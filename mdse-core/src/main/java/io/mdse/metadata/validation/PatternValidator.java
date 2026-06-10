package io.mdse.metadata.validation;

import io.mdse.metadata.schema.ColumnSchema;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates pattern (regex) constraints
 */
public class PatternValidator implements ConstraintValidator {
    
    private final Map<String, Pattern> patternCache = new HashMap<>();
    
    @Override
    public boolean supports(ColumnSchema column) {
        return column.getValidationPattern() != null && !column.getValidationPattern().isBlank();
    }
    
    @Override
    public void validate(Object value, ColumnSchema column, ValidationResult.Builder result) {
        if (value == null) {
            return;
        }
        
        String strValue = String.valueOf(value);
        Pattern pattern = getPattern(column.getValidationPattern());
        
        if (pattern != null && !pattern.matcher(strValue).matches()) {
            result.addError(
                    column.getColumnName(),
                    String.format("Field '%s' does not match required pattern",
                            column.getColumnName())
            );
        }
    }
    
    private Pattern getPattern(String regex) {
        return patternCache.computeIfAbsent(regex, r -> {
            try {
                return Pattern.compile(r);
            } catch (PatternSyntaxException e) {
                // Invalid regex in schema - log warning and skip validation
                return null;
            }
        });
    }
}

