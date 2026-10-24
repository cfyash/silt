package org.cfyash.silt.common.exceptions;

public class MemtableInsertException extends SiltException {
  public MemtableInsertException(String message) {
    super(message);
  }

  public MemtableInsertException(String message, Throwable cause) {
    super(message, cause);
  }

  public MemtableInsertException(Throwable cause) {
    super(cause);
  }
}
