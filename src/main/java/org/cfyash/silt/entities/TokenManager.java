package org.cfyash.silt.entities;

import org.cfyash.silt.common.exceptions.SyncTokenUpdateException;

public class TokenManager {
  private final Object lock;
  private volatile long currentToken;
  private volatile long lastSyncedToken;
  private volatile long lastWrittenToken;

  public TokenManager(long initToken) {
    this.currentToken = initToken;
    this.lastSyncedToken = initToken;
    this.lastWrittenToken = initToken;
    lock = new Object();
  }

  public long incrementAndGet() {
    synchronized (lock) {
      this.currentToken = this.currentToken + 1;
      return this.currentToken;
    }
  }

  public long getLastSynced() {
    return this.lastSyncedToken;
  }

  public void setLastWrittenToken(long token) {
    synchronized (lock) {
      lastWrittenToken = token;
    }
  }

  public long getLastWritten() {
    return this.lastWrittenToken;
  }

  public void incrementLastSynced(long token) {
    synchronized (lock) {
      if (this.lastSyncedToken > token) {
        throw new SyncTokenUpdateException("last sync token update rejected, value < currentSyncValue");
      }
      this.lastSyncedToken = token;
    }
  }

  public long getPendingSyncs() {
    synchronized (lock) {
      return this.currentToken - this.lastSyncedToken;
    }
  }
}
