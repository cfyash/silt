package org.cfyash.silt.services;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.WALSyncException;
import org.cfyash.silt.entities.DBLifecycleManager;
import org.cfyash.silt.entities.TokenManager;
import org.cfyash.silt.handlers.WALRecordHandler;
import org.cfyash.silt.models.WALRecord;

@Slf4j
public class SyncCoordinator implements AutoCloseable {

  private final ReentrantLock lock;
  private final Condition condition;
  private final DBLifecycleManager lifecycleManager;
  private final TokenManager tokenManager;
  private final WALRecordHandler walRecordHandler;
  private final Thread bgWorker;

  private final int syncLimit;

  public SyncCoordinator(
      DBLifecycleManager lifecycleManager,
      TokenManager tokenManager,
      WALRecordHandler walRecordHandler,
      int syncLimit,
      long lingerMs) {
    lock = new ReentrantLock();
    condition = lock.newCondition();
    this.lifecycleManager = lifecycleManager;
    this.tokenManager = tokenManager;
    this.walRecordHandler = walRecordHandler;
    this.syncLimit = syncLimit;

    this.bgWorker = Thread.ofVirtual().name("background-flush-thread").start(() -> backgroundFlush(lingerMs));
  }

  private void backgroundFlush(long lingerTimeMs) {
    lock.lock();
    try {
      while (lifecycleManager.acceptWrites() && !lifecycleManager.isShutdown()) {
        boolean syncOccur = condition.await(lingerTimeMs, TimeUnit.MILLISECONDS);
        if (!syncOccur && tokenManager.getPendingSyncs() > 0) {
          performSync();
        }
      }
    } catch (InterruptedException e){
      log.error("interrupted bg worker",e);
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      log.error(
          "error while syncing in background flush thread at: {}", Instant.now().toEpochMilli(), e);
      if (lifecycleManager.acceptWrites()) {
        coordinateToReadOnly();
      }
    } finally {
      if (!lifecycleManager.isShutdown()) coordinateToReadOnly();
      lock.unlock();
    }
  }

  private void performSync() {
    lock.lock();
    try {
      log.info("trying to sync at: {}", Instant.now().toEpochMilli());
      if (!lifecycleManager.acceptWrites()) {
        throw new WALSyncException("read only mode");
      }
      long syncToken = tokenManager.getLastWritten();
      walRecordHandler.syncToDisk();
      tokenManager.incrementLastSynced(syncToken);
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public void coordinate(WALRecord record) {
    lock.lock();
    try {
      if (!lifecycleManager.acceptWrites()) {
        throw new WALSyncException("read only mode");
      }
      if (record.token() <= tokenManager.getLastSynced()) {
        return;
      }
      if (tokenManager.getPendingSyncs() >= this.syncLimit) {
        performSync();
      } else {
        while (record.token() > tokenManager.getLastSynced()) {
          condition.awaitUninterruptibly();
          if (!lifecycleManager.acceptWrites()) {
            throw new WALSyncException("Exception while syncing,db in read only mode");
          }
        }
      }
    } catch (Exception e) {
      if (lifecycleManager.acceptWrites()) {
        coordinateToReadOnly();
      }
      throw new WALSyncException("Exception while syncing, shifting to read only mode", e);
    } finally {
      lock.unlock();
    }
  }

  public void coordinateToReadOnly() {
    lock.lock();
    try {
      log.warn("shifting to read only mode at: {}", Instant.now().toEpochMilli());
      lifecycleManager.readOnly();
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  ReentrantLock getLock() {
    return this.lock;
  }

  Condition getCondition() {
    return this.condition;
  }

  @Override
  public void close() throws Exception {
    lifecycleManager.shutdown();
    this.bgWorker.interrupt();
  }
}
