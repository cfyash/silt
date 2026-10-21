package org.cfyash.silt.common.exceptions;

public class NegativeOffsetException extends SiltException{
    public NegativeOffsetException(String message) {
        super(message);
    }

    public NegativeOffsetException(String message, Throwable cause) {
        super(message, cause);
    }

    public NegativeOffsetException(Throwable cause) {
        super(cause);
    }

}
