package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Immutable unique constraint descriptor
 */
@Value
@Builder
public class UniqueConstraintSchema {
    
    UUID id;
    
    /**
     * Constraint name
     */
    String constraintName;
    
    /**
     * Column names in the unique constraint
     */
    @Singular
    List<String> columnNames;
    
    /**
     * Whether this is a composite unique constraint
     */
    public boolean isComposite() {
        return columnNames.size() > 1;
    }
}

