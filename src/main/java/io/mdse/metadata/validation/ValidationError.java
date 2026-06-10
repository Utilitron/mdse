package io.mdse.metadata.validation;

import lombok.Getter;

/**
 * Single validation error
 */
public record ValidationError(String fieldName, String message) {
    
    @Override
    public String toString() {
        return String.format("%s: %s", fieldName, message);
    }
    
}

