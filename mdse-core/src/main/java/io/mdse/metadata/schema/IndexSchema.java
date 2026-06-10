package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * Immutable index descriptor
 */
@Value
@Builder
public class IndexSchema {
    
    UUID id;
    
    /**
     * Index name
     */
    String indexName;
    
    /**
     * Column names in the index
     */
    @Singular
    List<String> columnNames;
    
    /**
     * Whether this is a unique index
     */
    boolean unique;
    
    /**
     * Index type (BTREE, HASH, GIN, GIST, etc.)
     */
    String indexType;
    
    /**
     * Whether this is a partial index (has WHERE clause)
     */
    boolean partial;
    
    /**
     * Partial index predicate/condition
     */
    String predicate;
    
    /**
     * Check if this is a composite index
     */
    public boolean isComposite() {
        return columnNames.size() > 1;
    }
}
