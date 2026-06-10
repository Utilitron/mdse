package io.mdse.metadata.service;

import io.mdse.metadata.validation.ValidationResult;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.validation.ColumnValidator;
import io.mdse.metadata.validation.ConstraintValidator;

import java.util.*;

/**
 * Validates data against schema constraints.
 * Stateless service - separates validation logic from schema metadata.
 *
 * Extensible through validator plugins.
 */
public interface ValidationService {
    
    /**
     * Validate single value against column constraints.
     *
     * @param value the value to validate
     * @param column the column schema with constraints
     * @return validation result with any errors
     */
    ValidationResult validateValue(Object value, ColumnSchema column);
    
    /**
     * Validate entire record against table constraints.
     * Includes column-level and cross-column validations.
     *
     * @param values map of column name to value
     * @param schema the table schema
     * @return validation result with any errors
     */
    ValidationResult validateRecord(Map<String, Object> values, TableSchema schema);
    
    /**
     * Validate with custom validators in addition to schema constraints.
     *
     * @param value the value to validate
     * @param column the column schema
     * @param customValidators additional validators to apply
     * @return validation result
     */
    ValidationResult validateValue(
            Object value,
            ColumnSchema column,
            List<ColumnValidator> customValidators
    );
    
    /**
     * Register a custom validator for a specific constraint type.
     * Allows extending validation beyond built-in constraints.
     *
     * @param validator the validator to register
     */
    void registerValidator(ConstraintValidator validator);
}
