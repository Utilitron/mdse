package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Validates nullability constraints
 */
public class NullabilityValidator implements ConstraintValidator {
    
    @Override
    public boolean supports(ColumnSchema column) {
        return column.isRequired();
    }
    
    @Override
    public void validate(Object value, ColumnSchema column, ValidationResult.Builder result) {
        if (column.isRequired() && value == null) {
            result.addError(
                    column.getColumnName(),
                    "Field is required: " + column.getColumnName()
            );
        }
    }
}

