package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Plugin interface for constraint validation strategies
 */
public interface ConstraintValidator {
    
    /**
     * Check if this validator supports the given column schema
     */
    boolean supports(ColumnSchema column);
    
    /**
     * Validate value against column constraints
     */
    void validate(Object value, ColumnSchema column, ValidationResult.Builder result);
}

