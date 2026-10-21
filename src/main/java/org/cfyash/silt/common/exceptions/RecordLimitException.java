package org.cfyash.silt.common.exceptions;

public class RecordLimitException extends SiltException {
  public RecordLimitException(String message) {
    super(message);
  }

  public RecordLimitException(String message, Throwable cause) {
    super(message, cause);
  }

  public RecordLimitException(Throwable cause) {
    super(cause);
  }
}
