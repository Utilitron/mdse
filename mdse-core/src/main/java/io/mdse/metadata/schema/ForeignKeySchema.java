package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable foreign key relationship descriptor.
 * Pure structural metadata - no navigation or fetch behavior.
 */
@Value
@Builder(toBuilder = true)
public class ForeignKeySchema {
    
    UUID id;
    
    /**
     * Constraint name
     */
    String constraintName;
    
    /**
     * Source schema name
     */
    String sourceSchema;
    
    /**
     * Source table (the table with the foreign key)
     */
    String sourceTable;
    
    /**
     * Source columns (foreign key columns)
     * For composite keys: ["customer_id", "product_id"]
     */
    @Singular
    List<String> sourceColumns;
    
    /**
     * Referenced schema name
     */
    String referencedSchema;
    
    /**
     * Referenced table (the table being referenced)
     */
    String referencedTable;
    
    /**
     * Referenced columns (usually primary key columns)
     */
    @Singular
    List<String> referencedColumns;
    
    /**
     * Relationship cardinality
     */
    RelationshipCardinality cardinality;
    
    /**
     * ON DELETE rule (CASCADE, SET_NULL, RESTRICT, NO_ACTION)
     */
    ReferentialAction deleteRule;
    
    /**
     * ON UPDATE rule
     */
    ReferentialAction updateRule;
    
    /**
     * Whether this FK is deferrable (can be checked at commit time)
     */
    @Builder.Default
    boolean deferrable = false;
    
    /**
     * Whether this FK is initially deferred
     */
    @Builder.Default
    boolean initiallyDeferred = false;
    
    /**
     * Description of this relationship
     */
    String description;
    
    /**
     * Custom annotations for extensibility
     * Examples:
     * - "bidirectional": "true"
     * - "inverseProperty": "orders"
     * - "joinTable": "order_items"
     */
    @Singular
    Map<String, String> annotations;
    
    /**
     * Get fully qualified source table name
     */
    public String getQualifiedSourceTable() {
        return (sourceSchema != null ? sourceSchema + "." : "") + sourceTable;
    }
    
    /**
     * Get fully qualified referenced table name
     */
    public String getQualifiedReferencedTable() {
        return (referencedSchema != null ? referencedSchema + "." : "") + referencedTable;
    }
    
    /**
     * Check if this is a composite foreign key
     */
    public boolean isComposite() {
        return sourceColumns.size() > 1;
    }
    
    /**
     * Check if this is a self-referencing foreign key
     */
    public boolean isSelfReferencing() {
        return sourceTable.equalsIgnoreCase(referencedTable)
                && (sourceSchema == null || sourceSchema.equalsIgnoreCase(referencedSchema));
    }
    
    @Override
    public String toString() {
        return String.format("ForeignKeySchema[%s: %s.%s -> %s.%s]",
                constraintName,
                sourceTable,
                sourceColumns,
                referencedTable,
                referencedColumns);
    }
}

