package org.cfyash.silt.common.exceptions;

public class WALReadException extends SiltException {
  public WALReadException(String message) {
    super(message);
  }

  public WALReadException(String message, Throwable cause) {
    super(message, cause);
  }

  public WALReadException(Throwable cause) {
    super(cause);
  }
}
