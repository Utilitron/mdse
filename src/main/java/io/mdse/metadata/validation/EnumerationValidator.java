package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.EnumerationValue;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates enumeration constraints
 */
public class EnumerationValidator implements ConstraintValidator {
    
    @Override
    public boolean supports(ColumnSchema column) {
        return column.getAllowedValues() != null && !column.getAllowedValues().isEmpty();
    }
    
    @Override
    public void validate(Object value, ColumnSchema column, ValidationResult.Builder result) {
        if (value == null) {
            return;
        }
        
        String strValue = String.valueOf(value);
        List<EnumerationValue> allowedValues = column.getAllowedValues();
        
        boolean valid = allowedValues.stream()
                .anyMatch(enumVal -> enumVal.getValue().equals(strValue));
        
        if (!valid) {
            String allowedStr = allowedValues.stream()
                    .map(EnumerationValue::getValue)
                    .collect(Collectors.joining(", "));
            
            result.addError(
                    column.getColumnName(),
                    String.format("Value '%s' is not allowed. Allowed values: %s",
                            strValue, allowedStr)
            );
        }
    }
}

