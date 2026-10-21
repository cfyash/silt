package org.cfyash.silt.common.exceptions;

public class ReadOnlyModeException extends SiltException {
  public ReadOnlyModeException(String message) {
    super(message);
  }

  public ReadOnlyModeException(String message, Throwable cause) {
    super(message, cause);
  }

  public ReadOnlyModeException(Throwable cause) {
    super(cause);
  }
}
