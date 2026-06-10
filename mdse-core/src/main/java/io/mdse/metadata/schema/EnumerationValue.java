package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable representation of an enumeration value.
 * Used for database enums or application-level enumerated types.
 */
@Value
@Builder
public class EnumerationValue {
    
    /**
     * The actual value stored in the database
     */
    String value;
    
    /**
     * Human-readable label (optional)
     * Falls back to value if not specified
     */
    String label;
    
    /**
     * Ordinal position (optional)
     */
    Integer ordinal;
    
    /**
     * Description of this enumeration value (optional)
     */
    String description;
    
    /**
     * Get effective label (or fallback to value)
     */
    public String getEffectiveLabel() {
        return label != null ? label : value;
    }
}

