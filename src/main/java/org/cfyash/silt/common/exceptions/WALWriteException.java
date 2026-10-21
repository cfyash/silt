package org.cfyash.silt.common.exceptions;

public class WALWriteException extends SiltException {
  public WALWriteException(String message) {
    super(message);
  }

  public WALWriteException(String message, Throwable cause) {
    super(message, cause);
  }

  public WALWriteException(Throwable cause) {
    super(cause);
  }
}
