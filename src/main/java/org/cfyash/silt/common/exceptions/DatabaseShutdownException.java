package org.cfyash.silt.common.exceptions;

public class DatabaseShutdownException extends SiltException {
  public DatabaseShutdownException(String message) {
    super(message);
  }

  public DatabaseShutdownException(String message, Throwable cause) {
    super(message, cause);
  }

  public DatabaseShutdownException(Throwable cause) {
    super(cause);
  }
}
