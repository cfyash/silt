package org.cfyash.silt.common.exceptions;

public class EOFException extends SiltException {
  public EOFException(String message) {
    super(message);
  }

    public EOFException(String message, Throwable cause) {
        super(message, cause);
    }

    public EOFException(Throwable cause) {
        super(cause);
    }

}
