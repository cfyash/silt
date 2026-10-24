package org.cfyash.silt.services;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.DatabaseShutdownException;
import org.cfyash.silt.common.exceptions.ReadOnlyModeException;
import org.cfyash.silt.common.exceptions.RecordLimitException;
import org.cfyash.silt.common.exceptions.WALWriteException;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.DBLifecycleManager;
import org.cfyash.silt.entities.TokenManager;
import org.cfyash.silt.handlers.MemtableHandler;
import org.cfyash.silt.handlers.WALRecordHandler;
import org.cfyash.silt.models.MemKey;
import org.cfyash.silt.models.MemValue;
import org.cfyash.silt.models.RecordType;
import org.cfyash.silt.models.WALRecord;

@Slf4j
public class WritePipeline implements AutoCloseable{

  private final ReentrantLock lock;
  private final SyncCoordinator syncCoordinator;
  private final WALRecordHandler walRecordHandler;
  private final MemtableHandler memtableHandler;
  private final TokenManager tokenManager;
  private final DBLifecycleManager lifecycleManager;

  public WritePipeline(
      SyncCoordinator syncCoordinator,
      WALRecordHandler walRecordHandler,
      MemtableHandler memtableHandler,
      TokenManager tokenManager,
      DBLifecycleManager lifeCycleManager) {
    this.walRecordHandler = walRecordHandler;
    this.memtableHandler = memtableHandler;
    this.tokenManager = tokenManager;
    this.lifecycleManager = lifeCycleManager;
    this.lock = new ReentrantLock();
    this.syncCoordinator = syncCoordinator;
  }

  void write(byte[] key, byte[] value) {
    validateWrite(key, value);
    WALRecord record = writeToWAL(key, value, RecordType.PUT);
    try {
      syncCoordinator.coordinate(record);
      putIntoMemtable(record);
    } finally {
      memtableHandler.unregister(record.token());
    }
  }

  private void putIntoMemtable(@NonNull WALRecord record) {
    memtableHandler.put(
        MemKey.builder().key(record.key()).token(record.token()).build(),
        MemValue.builder()
            .value(record.value())
            .isDelete(record.type() == RecordType.DELETE)
            .build());
  }

  void delete(byte[] key) {
    validateDelete(key);
    WALRecord record = writeToWAL(key, new byte[0], RecordType.DELETE);
    try {
      syncCoordinator.coordinate(record);
      putIntoMemtable(record);
    } finally {
      memtableHandler.unregister(record.token());
    }
  }

  private void validateDelete(byte[] key) {
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
  }

  private WALRecord writeToWAL(byte[] key, byte[] value, RecordType type) {
    lock.lock();
    WALRecord record;
    try {
      validateDBMode();
      try {
        long token = tokenManager.incrementAndGet();
        long timestamp = Instant.now().toEpochMilli();
        record = walRecordHandler.encodeAndWrite(token, timestamp, key, value, type);
        tokenManager.setLastWrittenToken(token);
        memtableHandler.registerOnActive(token);
      } catch (Exception e) {
        log.error("WAL writer threw exception for key {}", key);
        syncCoordinator.coordinateToReadOnly();
        throw new WALWriteException(e);
      }
    } finally {
      lock.unlock();
    }
    return record;
  }

  private void validateDBMode() {
    if (!lifecycleManager.acceptWrites()) {
      throw new ReadOnlyModeException("db in read only mode, not accepting writes");
    }
    if (lifecycleManager.isShutdown()) {
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

  @Override
  public void close() throws Exception {
    walRecordHandler.close();
  }
}
