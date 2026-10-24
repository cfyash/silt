package org.cfyash.silt.common.exceptions;

public class UnknownRecordTypeException extends SiltException {
  public UnknownRecordTypeException(String message) {
    super(message);
  }

  public UnknownRecordTypeException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnknownRecordTypeException(Throwable cause) {
    super(cause);
  }
}
