package io.mdse.metadata.exception;

/**
 * Exception thrown when type coercion fails
 */
public class CoercionException extends RuntimeException {
    
    public CoercionException(String message) {
        super(message);
    }
    
    public CoercionException(String message, Throwable cause) {
        super(message, cause);
    }
}
