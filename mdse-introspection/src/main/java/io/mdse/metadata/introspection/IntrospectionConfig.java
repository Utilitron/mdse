package io.mdse.metadata.introspection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for schema introspection behavior.
 * Controls what metadata is loaded and how.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntrospectionConfig {
    
    /**
     * Default configuration
     */
    public static final IntrospectionConfig DEFAULT = IntrospectionConfig.builder()
            .tableTypes(new String[]{"TABLE", "VIEW"})
            .loadIndexes(true)
            .loadForeignKeys(true)
            .loadUniqueConstraints(true)
            .estimateRowCounts(false)
            .includeSystemTables(false)
            .includeViews(true)
            .build();
    
    /**
     * Minimal configuration (fastest introspection)
     */
    public static final IntrospectionConfig MINIMAL = IntrospectionConfig.builder()
            .tableTypes(new String[]{"TABLE"})
            .loadIndexes(false)
            .loadForeignKeys(false)
            .loadUniqueConstraints(false)
            .estimateRowCounts(false)
            .includeSystemTables(false)
            .includeViews(false)
            .build();
    
    /**
     * Complete configuration (slowest but most thorough)
     */
    public static final IntrospectionConfig COMPLETE = IntrospectionConfig.builder()
            .tableTypes(new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"})
            .loadIndexes(true)
            .loadForeignKeys(true)
            .loadUniqueConstraints(true)
            .estimateRowCounts(true)
            .includeSystemTables(false)
            .includeViews(true)
            .build();
    
    /**
     * Table types to include (TABLE, VIEW, etc.)
     */
    @Builder.Default
    private String[] tableTypes = new String[]{"TABLE", "VIEW"};
    
    /**
     * Whether to load index metadata
     */
    @Builder.Default
    private boolean loadIndexes = true;
    
    /**
     * Whether to load foreign key relationships
     */
    @Builder.Default
    private boolean loadForeignKeys = true;
    
    /**
     * Whether to load unique constraints
     */
    @Builder.Default
    private boolean loadUniqueConstraints = true;
    
    /**
     * Whether to estimate row counts (can be slow on large tables)
     */
    @Builder.Default
    private boolean estimateRowCounts = false;
    
    /**
     * Whether to include system tables
     */
    @Builder.Default
    private boolean includeSystemTables = false;
    
    /**
     * Whether to include views
     */
    @Builder.Default
    private boolean includeViews = true;
    
    /**
     * Table name pattern (SQL LIKE pattern)
     */
    private String tableNamePattern;
    
    /**
     * Schema name pattern (SQL LIKE pattern)
     */
    private String schemaNamePattern;
    
    /**
     * Column name pattern for filtering columns
     */
    private String columnNamePattern;
    
    /**
     * Maximum number of tables to introspect (for testing/limiting)
     */
    private Integer maxTables;
}

