package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Functional interface for custom column validators
 */
@FunctionalInterface
public interface ColumnValidator {
    
    /**
     * Validate a value
     *
     * @param value the value to validate
     * @param column the column context
     * @return validation error if invalid, null if valid
     */
    ValidationError validate(Object value, ColumnSchema column);
}

