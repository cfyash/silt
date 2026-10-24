package org.cfyash.silt.services;

import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.DatabaseShutdownException;
import org.cfyash.silt.entities.DBLifecycleManager;
import org.cfyash.silt.handlers.MemtableHandler;
import org.cfyash.silt.models.MemKey;
import org.cfyash.silt.models.MemValue;

@Slf4j
public class ReadPipeline {

  private final MemtableHandler memtableHandler;
  private final DBLifecycleManager lifecycleManager;

  public ReadPipeline(MemtableHandler memtableHandler, DBLifecycleManager lifecycleManager) {
    this.memtableHandler = memtableHandler;
    this.lifecycleManager = lifecycleManager;
  }

  public byte[] get(byte[] key) {
    if (lifecycleManager.isShutdown()) {
      throw new DatabaseShutdownException("database shutting down...");
    }
    MemValue v = memtableHandler.get(MemKey.builder().key(key).token(Long.MAX_VALUE).build());
    if (v == null || v.isDelete()) {
      return null;
    }
    return v.value();
  }
}
