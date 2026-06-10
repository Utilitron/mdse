package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Immutable primary key constraint descriptor
 */
@Value
@Builder
public class PrimaryKeySchema {
    
    UUID id;
    
    /**
     * Constraint name
     */
    String constraintName;
    
    /**
     * Column names that make up the primary key
     */
    @Singular
    List<String> columnNames;
    
    /**
     * Check if this is a composite primary key
     */
    public boolean isComposite() {
        return columnNames.size() > 1;
    }
}
