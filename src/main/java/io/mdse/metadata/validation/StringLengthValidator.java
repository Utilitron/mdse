package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Validates string length constraints
 */
public class StringLengthValidator implements ConstraintValidator {
    
    @Override
    public boolean supports(ColumnSchema column) {
        return column.isTextType() && column.getMaxLength() != null;
    }
    
    @Override
    public void validate(Object value, ColumnSchema column, ValidationResult.Builder result) {
        if (value == null) {
            return;
        }
        
        // Convert to string safely (coercion should have happened earlier, but be defensive)
        String str = (value instanceof String s) ? s : String.valueOf(value);
        
        if (column.getMaxLength() != null && str.length() > column.getMaxLength()) {
            result.addError(
                    column.getColumnName(),
                    String.format("Field '%s' exceeds maximum length of %d characters (actual: %d)",
                            column.getColumnName(), column.getMaxLength(), str.length())
            );
        }
    }
}

