package org.cfyash.silt.common.exceptions;

public abstract class SiltException extends RuntimeException{

    public SiltException(String message) {
        super(message);
    }

    public SiltException(String message, Throwable cause) {
        super(message, cause);
    }

    public SiltException(Throwable cause) {
        super(cause);
    }
}
