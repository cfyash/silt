package org.cfyash.silt.entities;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DBLifecycleManager {
  private final AtomicBoolean writes;
  private final AtomicBoolean shutdown;

  public DBLifecycleManager() {
    this.writes = new AtomicBoolean(true);
    this.shutdown = new AtomicBoolean(false);
  }

  public void readOnly() {
    writes.set(false);
  }

  public void shutdown() {
    shutdown.set(true);
    writes.set(false);
  }

  public boolean acceptWrites() {
    return writes.get();
  }

  public boolean isShutdown() {
    return shutdown.get();
  }
}
