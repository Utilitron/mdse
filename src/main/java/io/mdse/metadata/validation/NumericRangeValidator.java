package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Validates numeric range constraints
 */
public class NumericRangeValidator implements ConstraintValidator {
    
    @Override
    public boolean supports(ColumnSchema column) {
        return column.isNumericType()
                && (column.getMinValue() != null || column.getMaxValue() != null);
    }
    
    @Override
    public void validate(Object value, ColumnSchema column, ValidationResult.Builder result) {
        if (value == null) {
            return; // Nullability handled by NullabilityValidator
        }
        
        if (!(value instanceof Number num)) {
            result.addError(
                    column.getColumnName(),
                    "Expected numeric value, got: " + value.getClass().getSimpleName()
            );
            return;
        }
        
        long numValue = num.longValue();
        
        if (column.getMinValue() != null && numValue < column.getMinValue()) {
            result.addError(
                    column.getColumnName(),
                    String.format("Value %d is below minimum %d", numValue, column.getMinValue())
            );
        }
        
        if (column.getMaxValue() != null && numValue > column.getMaxValue()) {
            result.addError(
                    column.getColumnName(),
                    String.format("Value %d exceeds maximum %d", numValue, column.getMaxValue())
            );
        }
    }
}

