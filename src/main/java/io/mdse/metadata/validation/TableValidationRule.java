package io.mdse.metadata.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Validation rule for table-level constraints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableValidationRule {
    
    private UUID id;
    
    /**
     * Rule name/identifier
     */
    private String name;
    
    /**
     * Rule type (CHECK, CUSTOM, etc.)
     */
    private String type;
    
    /**
     * SQL expression for check constraint
     */
    private String expression;
    
    /**
     * Error message when validation fails
     */
    private String errorMessage;
    
    /**
     * Whether this rule is enforced at database level
     */
    private boolean databaseEnforced;
}

