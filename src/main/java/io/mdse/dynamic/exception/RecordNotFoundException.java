package io.mdse.dynamic.exception;

/**
 * Exception thrown when a record is not found
 */
public class RecordNotFoundException extends RuntimeException {
    public RecordNotFoundException(String message) {
        super(message);
    }
}
