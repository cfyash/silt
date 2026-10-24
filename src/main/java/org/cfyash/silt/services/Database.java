package org.cfyash.silt.services;

import java.io.IOException;
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
  private final int syncLimit;

  public Database(String dir) throws IOException {
    Files.createDirectories(Path.of(dir));
    this(new WALFile(Path.of(dir, DatabaseConfig.WAL_FILENAME)), DatabaseConfig.LINGER_TIME_MS, DatabaseConfig.SYNC_LIMIT);
  }

  Database(WALFile walFile, final long lingerMS, int syncLimit) {
    this.syncLimit = syncLimit;
    this.acceptWrites = new AtomicBoolean(false);
    this.walRecordHandler = new WALRecordHandler(walFile);
    long latestToken = rebuildMemtable();
    this.tokenManager = new TokenManager(latestToken);

    // init locks
    this.rwLock = new ReentrantReadWriteLock();
    this.writeLock = rwLock.writeLock();
    this.readLock = rwLock.readLock();
    this.syncLock = new ReentrantLock();
    this.syncedCondition = syncLock.newCondition();

    // start accepting writes
    this.acceptWrites.set(true);
    this.shutdown = new AtomicBoolean(false);

    // start background flush thread
    Thread.ofVirtual().name("background-flush-thread").start(() -> backgroundFlush(lingerMS));
  }

  private long rebuildMemtable() {
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
    return maxToken;
  }

  public void write(byte[] key, byte[] value) {
    validateWrite(key, value);
    WALRecord record = writeToWAL(key, value);
    sync(record);
  }

  private WALRecord writeToWAL(byte[] key, byte[] value) {
    writeLock.lock();
    WALRecord record;
    try {
      validateDBMode();
      try {
        long token = tokenManager.incrementAndGet();
        long timestamp = Instant.now().toEpochMilli();
        record = walRecordHandler.encodeAndWrite(token, timestamp, key, value);
        tokenManager.setLastWrittenToken(token);
      } catch (Exception e) {
        log.error("WAL writer threw exception for key {}", key);
        readOnlyMode();
        throw new WALWriteException(e);
      }
    } finally {
      writeLock.unlock();
    }
    return record;
  }

  private void validateDBMode() {
    if (!acceptWrites.get()) {
      throw new ReadOnlyModeException("db in read only mode, not accepting writes");
    }
    if (shutdown.get()) {
      throw new DatabaseShutdownException("db not serving request, might be shutting down");
    }
  }

  private void validateWrite(byte[] key, byte[] value) {
    if (key.length > DatabaseConfig.KEY_LIMIT) {
      throw new RecordLimitException(
          "key size > "
              + DatabaseConfig.KEY_LIMIT
              + "; please ensure keys are withing prescribed limits");
    }
    if (key.length == 0) {
      throw new RecordLimitException(
          "key size == 0; please ensure keys are withing prescribed limits");
    }
    if (value.length > DatabaseConfig.VALUE_LIMIT) {
      throw new RecordLimitException(
          "value size > "
              + DatabaseConfig.VALUE_LIMIT
              + "; please ensure value are withing prescribed limits");
    }
  }

  private void sync(WALRecord record) {
    syncLock.lock();
    try {
      if (!acceptWrites.get()) {
        throw new WALSyncException("read only mode");
      }
      if (tokenManager.getPendingSyncs() >= this.syncLimit) {
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
      if (acceptWrites.get()) {
        readOnlyMode();
      }
      throw new WALSyncException("Exception while syncing, shifting to read only mode", e);
    } finally {
      syncLock.unlock();
    }
  }

  private void backgroundFlush(long lingerTimeMs) {
    syncLock.lock();
    try {
      while (!shutdown.get() && acceptWrites.get()) {
        boolean syncOccur = syncedCondition.await(lingerTimeMs, TimeUnit.MILLISECONDS);
        if (!syncOccur && tokenManager.getPendingSyncs() > 0) {
          performSync();
        }
      }
    } catch (Exception e) {
      log.error(
          "error while syncing in background flush thread at: {}", Instant.now().toEpochMilli(), e);
      if (acceptWrites.get()) {
        readOnlyMode();
      }
    } finally {
      if (!shutdown.get()) readOnlyMode();
      syncLock.unlock();
    }
  }

  private void performSync() {
    syncLock.lock();
    try {
      log.info("trying to sync at: {}", Instant.now().toEpochMilli());
      if (!acceptWrites.get()) {
        throw new WALSyncException("read only mode");
      }
      long syncToken = tokenManager.getLastWritten();
      walRecordHandler.syncToDisk();
      tokenManager.incrementLastSynced(syncToken);
      syncedCondition.signalAll();
    } finally {
      syncLock.unlock();
    }
  }

  private void readOnlyMode() {
    syncLock.lock();
    try {
      log.warn("shifting to read only mode at: {}", Instant.now().toEpochMilli());
      acceptWrites.set(false);
      syncedCondition.signalAll();
    } finally {
      syncLock.unlock();
    }
  }
}
