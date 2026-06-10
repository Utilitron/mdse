package io.mdse.metadata.validation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a validation operation
 */
@Getter
public class ValidationResult {
    
    /**
     * -- GETTER --
     *  Get all validation errors
     */
    private final List<ValidationError> errors;
    
    private ValidationResult(List<ValidationError> errors) {
        this.errors = Collections.unmodifiableList(errors);
    }
    
    /**
     * Create a successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult(Collections.emptyList());
    }
    
    /**
     * Create a failed validation result with errors
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(errors);
    }
    
    /**
     * Create a failed validation result with a single error
     */
    public static ValidationResult failure(ValidationError error) {
        return new ValidationResult(Collections.singletonList(error));
    }
    
    /**
     * Check if validation passed
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    /**
     * Check if validation failed
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Get errors for a specific field
     */
    public List<ValidationError> getErrorsFor(String fieldName) {
        return errors.stream()
                .filter(e -> e.fieldName().equals(fieldName))
                .toList();
    }
    
    /**
     * Combine with another validation result
     */
    public ValidationResult merge(ValidationResult other) {
        List<ValidationError> combined = new ArrayList<>(this.errors);
        combined.addAll(other.errors);
        return new ValidationResult(combined);
    }
    
    /**
     * Builder for constructing validation results incrementally
     */
    public static ValidationResult.Builder builder() {
        return new ValidationResult.Builder();
    }
    
    public static class Builder {
        private final List<ValidationError> errors = new ArrayList<>();
        
        public ValidationResult.Builder addError(String fieldName, String message) {
            errors.add(new ValidationError(fieldName, message));
            return this;
        }
        
        public ValidationResult.Builder addError(ValidationError error) {
            errors.add(error);
            return this;
        }
        
        public ValidationResult.Builder addErrors(List<ValidationError> errors) {
            this.errors.addAll(errors);
            return this;
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public ValidationResult build() {
            return errors.isEmpty() ? success() : failure(errors);
        }
    }
}
