package org.cfyash.silt.common.exceptions;

public class ChecksumValidationException extends SiltException {
  public ChecksumValidationException(String message) {
    super(message);
  }

  public ChecksumValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public ChecksumValidationException(Throwable cause) {
    super(cause);
  }
}
