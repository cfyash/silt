package org.cfyash.silt.common.exceptions;

public class WALSyncException extends SiltException {
  public WALSyncException(String message) {
    super(message);
  }

  public WALSyncException(String message, Throwable cause) {
    super(message, cause);
  }

  public WALSyncException(Throwable cause) {
    super(cause);
  }
}
