package io.mdse.metadata.service.impl;


import io.mdse.metadata.validation.ValidationError;
import io.mdse.metadata.validation.ValidationResult;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.service.ValidationService;
import io.mdse.metadata.validation.*;

import java.util.*;

/**
 * Standard implementation of ValidationService with built-in validators.
 *
 * Built-in validators:
 * - Nullability (required fields)
 * - Numeric range (min/max)
 * - String length
 * - Pattern matching (regex)
 * - Enumeration values
 */
public class StandardValidationService implements ValidationService {
    
    private final List<ConstraintValidator> validators = new ArrayList<>();
    
    public StandardValidationService() {
        // Register built-in validators
        validators.add(new NullabilityValidator());
        validators.add(new NumericRangeValidator());
        validators.add(new StringLengthValidator());
        validators.add(new PatternValidator());
        validators.add(new EnumerationValidator());
    }
    
    @Override
    public ValidationResult validateValue(Object value, ColumnSchema column) {
        return validateValue(value, column, Collections.emptyList());
    }
    
    @Override
    public ValidationResult validateValue(
            Object value,
            ColumnSchema column,
            List<ColumnValidator> customValidators) {
        
        if (column == null) {
            return ValidationResult.success();
        }
        
        ValidationResult.Builder result = ValidationResult.builder();
        
        // Run built-in validators
        for (ConstraintValidator validator : validators) {
            if (validator.supports(column)) {
                validator.validate(value, column, result);
            }
        }
        
        // Run custom validators
        for (ColumnValidator customValidator : customValidators) {
            ValidationError error = customValidator.validate(value, column);
            if (error != null) {
                result.addError(error);
            }
        }
        
        return result.build();
    }
    
    @Override
    public ValidationResult validateRecord(Map<String, Object> values, TableSchema schema) {
        ValidationResult.Builder result = ValidationResult.builder();
        
        // Validate each column
        for (ColumnSchema column : schema.getColumns()) {
            String columnName = column.getColumnName();
            Object value = values.get(columnName);
            
            ValidationResult columnResult = validateValue(value, column);
            result.addErrors(columnResult.getErrors());
        }
        
        // Table‑level validation rules
        if (schema.getValidationRules() != null) {
            for (TableValidationRule rule : schema.getValidationRules()) {
                if ("CHECK".equals(rule.getType()) && rule.getExpression() != null) {
                    if (!evaluateCheckExpression(rule.getExpression(), values)) {
                        result.addError(new ValidationError(null, rule.getErrorMessage()));
                    }
                }
            }
        }
        
        return result.build();
    }
    
    private boolean evaluateCheckExpression(String expression, Map<String, Object> values) {
        // Very simple evaluator for expressions like "field1 < field2"
        // Supports: <, >, <=, >=, ==, !=
        String[] parts = expression.trim().split("\\s+");
        if (parts.length == 3) {
            String leftField = parts[0];
            String operator = parts[1];
            String rightField = parts[2];
            Object leftVal = values.get(leftField);
            Object rightVal = values.get(rightField);
            if (leftVal instanceof Comparable && rightVal instanceof Comparable) {
                @SuppressWarnings("unchecked")
                int cmp = ((Comparable) leftVal).compareTo(rightVal);
                return switch (operator) {
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "==" -> cmp == 0;
                    case "!=" -> cmp != 0;
                    default -> true; // unknown operator – ignore
                };
            }
        }
        // If expression cannot be parsed, assume valid (or log warning)
        return true;
    }
    
    @Override
    public void registerValidator(ConstraintValidator validator) {
        validators.add(validator);
    }
}
