package io.mdse.metadata.exception;

/**
 * Exception thrown when metadata is not found
 */
public class MetadataNotFoundException extends RuntimeException {
    public MetadataNotFoundException(String message) {
        super(message);
    }
}
