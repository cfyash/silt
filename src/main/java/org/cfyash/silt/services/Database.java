package org.cfyash.silt.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.*;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.TokenManager;
import org.cfyash.silt.entities.WALFile;
import org.cfyash.silt.handlers.WALRecordHandler;
import org.cfyash.silt.models.WALRecord;

@Slf4j
public class Database {
  private final WALRecordHandler walRecordHandler;
  private final TokenManager tokenManager;
  private final ReentrantReadWriteLock rwLock;
  private final ReentrantLock syncLock;
  private final Condition syncedCondition;
  private final Lock readLock;
  private final Lock writeLock;
  private final AtomicBoolean shutdown;
  private final AtomicBoolean acceptWrites;

  public Database(String dir) throws IOException {
    this.shutdown = new AtomicBoolean(false);
    this.acceptWrites = new AtomicBoolean(false);
    Files.createDirectories(Path.of(dir));
    this.walRecordHandler =
        new WALRecordHandler(new WALFile(Path.of(dir, DatabaseConfig.WAL_FILENAME)));
    long offset = 0;
    WALRecord record;
    long maxToken = 0;
    try {
      while (walRecordHandler.hasNext(offset)) {
        record = walRecordHandler.decodeNext(offset);
        offset += record.size();
        maxToken = Math.max(record.token(), maxToken);
      }
    } catch (NegativeOffsetException e) {
      throw new WALReadException("unable to built in memory memtable");
    } catch (ChecksumValidationException
        | EOFException
        | RecordLimitException
        | UnexpectedEOFException e) {
      log.warn("error while reading wal file, might be a torn record, truncating WAL...");
      this.walRecordHandler.truncateWAL(offset);
    }
    this.tokenManager = new TokenManager(maxToken + 1);
    this.rwLock = new ReentrantReadWriteLock();
    this.writeLock = rwLock.writeLock();
    this.readLock = rwLock.readLock();
    this.syncLock = new ReentrantLock();
    this.syncedCondition = syncLock.newCondition();
    this.acceptWrites.set(true);
    Thread.startVirtualThread(this::backgroundFlush);
  }

  public void write(String key, byte[] value) {
    if (!acceptWrites.get()) {
      throw new ReadOnlyModeException("db in read only mode, not accepting writes");
    }
    if (shutdown.get()) {
      throw new DatabaseShutdownException("db not serving request, might be shutting down");
    }
    writeLock.lock();
    WALRecord record;
    try {
      long token = tokenManager.incrementAndGet();
      long timestamp = Instant.now().toEpochMilli();
      record =
          walRecordHandler.encodeAndWrite(
              token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
      tokenManager.setLastWrittenToken(token);
    } catch (Exception e) {
      acceptWrites.set(false);
      throw new WALWriteException(e);
    } finally {
      writeLock.unlock();
    }
    sync(record);
  }

  private void sync(WALRecord record) {
    syncLock.lock();
    try {
      if (!acceptWrites.get()) {
        throw new WALSyncException("read only mode");
      }
      if (tokenManager.getPendingSyncs() >= DatabaseConfig.SYNC_LIMIT) {
        performSync();
      } else {
        while (record.token() > tokenManager.getLastSynced()) {
          syncedCondition.awaitUninterruptibly();
          if (!acceptWrites.get()) {
            throw new WALSyncException("Exception while syncing, shifting to read only mode");
          }
        }
      }
    } catch (Exception e) {
      this.acceptWrites.set(false);
      syncedCondition.signalAll();
      throw new WALSyncException("Exception while syncing, shifting to read only mode", e);
    } finally {
      syncLock.unlock();
    }
  }

  private void backgroundFlush() {
    syncLock.lock();
    try {
      while (!shutdown.get() && acceptWrites.get()) {
        boolean syncOccur =
            syncedCondition.await(DatabaseConfig.LINGER_TIME_MS, TimeUnit.MILLISECONDS);
        if (!syncOccur && tokenManager.getPendingSyncs() > 0) {
          performSync();
        }
      }
    } catch (Exception e) {
      this.acceptWrites.set(false);
      syncedCondition.signalAll();
      Thread.currentThread().interrupt();
    } finally {
      syncLock.unlock();
    }
  }

  private void performSync() {
    if (!acceptWrites.get()) {
      throw new WALSyncException("read only mode");
    }
    long syncToken = tokenManager.getLastWritten();
    walRecordHandler.syncToDisk();
    tokenManager.incrementLastSynced(syncToken);
    syncedCondition.signalAll();
  }
}
