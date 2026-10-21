package org.cfyash.silt.common.exceptions;

public class UnexpectedEOFException extends SiltException {
  public UnexpectedEOFException(String message) {
    super(message);
  }

  public UnexpectedEOFException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnexpectedEOFException(Throwable cause) {
    super(cause);
  }
}
