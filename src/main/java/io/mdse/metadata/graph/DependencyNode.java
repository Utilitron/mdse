package io.mdse.metadata.graph;

import io.mdse.metadata.schema.TableSchema;

import java.util.Objects;

/**
 * Immutable graph node representing a database structure.
 *
 * Nodes are intentionally lightweight and contain only
 * structural metadata identity. Relationships belong
 * to the graph via DependencyEdge.
 */
public final class DependencyNode {
    
    private final String name;
    private final TableSchema table;
    
    public DependencyNode(TableSchema table) {
        
        Objects.requireNonNull(table, "table cannot be null");
        
        this.name = table.getTableName();
        this.table = table;
    }
    
    /**
     * Table name.
     */
    public String name() {
        return name;
    }
    
    /**
     * Underlying schema metadata.
     */
    public TableSchema table() {
        return table;
    }
    
    /**
     * Convenience access to physical table name.
     */
    public String tableName() {
        return table.getTableName();
    }
    
    /**
     * Convenience access to schema name.
     */
    public String schemaName() {
        return table.getSchemaName();
    }
    
    /**
     * True if this represents a view.
     */
    public boolean isView() {
        return table.getTableType() != null
                && table.getTableType().name().contains("VIEW");
    }
    
    /**
     * True if this table has a composite primary key.
     */
    public boolean hasCompositeKey() {
        return table.hasCompositePrimaryKey();
    }
    
    /**
     * Estimated row count if available.
     */
    public long estimatedRowCount() {
        return table.getEstimatedRowCount() != null
                ? table.getEstimatedRowCount()
                : -1L;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    @Override
    public boolean equals(Object o) {
        
        if (this == o) {
            return true;
        }
        
        if (!(o instanceof DependencyNode other)) {
            return false;
        }
        
        return name.equalsIgnoreCase(other.name);
    }
    
    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }
}

