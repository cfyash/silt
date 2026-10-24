package org.cfyash.silt.common.exceptions;

public class SyncTokenUpdateException extends SiltException {
  public SyncTokenUpdateException(String message) {
    super(message);
  }

  public SyncTokenUpdateException(String message, Throwable cause) {
    super(message, cause);
  }

  public SyncTokenUpdateException(Throwable cause) {
    super(cause);
  }
}
