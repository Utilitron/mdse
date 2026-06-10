package io.mdse.metadata.schema;

import io.mdse.metadata.validation.TableValidationRule;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable structural description of a database table.
 * Pure value object with relationships defined declaratively.
 *
 * This is the cornerstone of the metadata-driven architecture, enabling:
 * - Dynamic CRUD operations without code generation
 * - Runtime schema discovery
 * - Relationship traversal
 * - Query generation
 */
@Value
@Builder(toBuilder = true)
public class TableSchema {
    
    /**
     * Unique identifier for this metadata instance
     */
    UUID id;
    
    /**
     * Database catalog name (optional)
     */
    String catalogName;
    
    /**
     * Database schema name (e.g., "public", "dbo")
     */
    String schemaName;
    
    /**
     * Physical table name in the database
     */
    String tableName;
    
    /**
     * Table type: TABLE, VIEW, MATERIALIZED_VIEW, SYSTEM_TABLE
     */
    TableType tableType;
    
    /**
     * All columns in this table, ordered by ordinal position
     */
    @Singular
    List<ColumnSchema> columns;
    
    /**
     * Primary key definition (simple or composite)
     */
    PrimaryKeySchema primaryKey;
    
    /**
     * Unique constraints (excluding primary key)
     */
    @Singular
    List<UniqueConstraintSchema> uniqueConstraints;
    
    /**
     * Indexes defined on this table
     */
    @Singular
    List<IndexSchema> indexes;
    
    /**
     * Foreign key relationships where this table is the source
     */
    @Singular
    List<ForeignKeySchema> foreignKeys;
    
    /**
     * Table comment/description from database
     */
    String description;
    
    /**
     * Estimated row count (from database statistics)
     */
    Long estimatedRowCount;
    
    /**
     * Timestamp when metadata was last refreshed
     */
    Instant lastRefreshed;
    
    /**
     * Custom annotations for extensibility
     * Examples:
     * - "auditable": "true"
     * - "softDelete": "deleted_at"
     * - "partition": "by_month"
     */
    @Singular
    Map<String, String> annotations;
    
    /**
     * Validation rules
     */
    @Singular
    List<TableValidationRule> validationRules;
    
    /**
     * Get fully qualified table name: catalog.schema.tableName
     */
    public String getQualifiedName() {
        return Stream.of(catalogName, schemaName, tableName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }
    
    /**
     * Check if this table has a primary key
     */
    public boolean hasPrimaryKey() {
        return primaryKey != null && !primaryKey.getColumnNames().isEmpty();
    }
    
    /**
     * Check if this table has a composite primary key
     */
    public boolean hasCompositePrimaryKey() {
        return hasPrimaryKey() && primaryKey.getColumnNames().size() > 1;
    }
    
    /**
     * Find column by name (case-insensitive)
     */
    public Optional<ColumnSchema> findColumn(String columnName) {
        return columns.stream()
                .filter(col -> col.getColumnName().equalsIgnoreCase(columnName))
                .findFirst();
    }
    
    /**
     * Get column by name or throw exception
     */
    public ColumnSchema getColumn(String columnName) {
        return findColumn(columnName)
                .orElseThrow(() -> new IllegalArgumentException("Column not found: " + columnName + " in table " + tableName));
    }
    
    /**
     * Get all primary key columns
     */
    public List<ColumnSchema> getPrimaryKeyColumns() {
        if (!hasPrimaryKey()) {
            return Collections.emptyList();
        }
        
        return primaryKey.getColumnNames().stream()
                .map(this::getColumn)
                .toList();
    }
    
    /**
     * Get all required (NOT NULL) columns
     */
    public List<ColumnSchema> getRequiredColumns() {
        return columns.stream()
                .filter(ColumnSchema::isRequired)
                .toList();
    }
    
    /**
     * Get all columns with default values
     */
    public List<ColumnSchema> getColumnsWithDefaults() {
        return columns.stream()
                .filter(col -> col.getDefaultValue() != null)
                .toList();
    }
    
    /**
     * Get all auto-generated columns
     */
    public List<ColumnSchema> getGeneratedColumns() {
        return columns.stream()
                .filter(col -> col.isAutoIncrement() || col.isGenerated())
                .toList();
    }
    
    /**
     * Find foreign key by constraint name
     */
    public Optional<ForeignKeySchema> findForeignKey(String constraintName) {
        return foreignKeys.stream()
                .filter(fk -> fk.getConstraintName().equalsIgnoreCase(constraintName))
                .findFirst();
    }
    
    /**
     * Find all foreign keys referencing a specific table
     */
    public List<ForeignKeySchema> findForeignKeysTo(String targetTable) {
        return foreignKeys.stream()
                .filter(fk -> fk.getReferencedTable().equalsIgnoreCase(targetTable))
                .toList();
    }
    
    /**
     * Get all tables this table depends on (via foreign keys)
     */
    public Set<String> getDependencies() {
        return foreignKeys.stream()
                .map(ForeignKeySchema::getReferencedTable)
                .collect(Collectors.toSet());
    }
    
    /**
     * Find index by name
     */
    public Optional<IndexSchema> findIndex(String indexName) {
        return indexes.stream()
                .filter(idx -> idx.getIndexName().equalsIgnoreCase(indexName))
                .findFirst();
    }
    
    /**
     * Find indexes covering a specific column
     */
    public List<IndexSchema> findIndexesCovering(String columnName) {
        return indexes.stream()
                .filter(idx -> idx.getColumnNames().stream()
                        .anyMatch(col -> col.equalsIgnoreCase(columnName)))
                .toList();
    }
    
    /**
     * Get all unique indexes
     */
    public List<IndexSchema> getUniqueIndexes() {
        return indexes.stream()
                .filter(IndexSchema::isUnique)
                .toList();
    }
    
    /**
     * Find a unique constraint by its name (case-insensitive)
     */
    public Optional<UniqueConstraintSchema> findUniqueConstraint(String constraintName) {
        return uniqueConstraints.stream()
                .filter(uc -> uc.getConstraintName().equalsIgnoreCase(constraintName))
                .findFirst();
    }
    
    /**
     * Basic structural validation.
     * A table is valid if it has a non-blank name and at least one column.
     */
    public boolean isStructurallyValid() {
        return tableName != null
                && !tableName.isBlank()
                && columns != null
                && !columns.isEmpty()
                && columns.stream().allMatch(ColumnSchema::isStructurallyValid);
    }
    
    @Override
    public String toString() {
        return String.format("TableSchema[%s, columns=%d, pk=%s]",
                getQualifiedName(),
                columns.size(),
                hasPrimaryKey() ? primaryKey.getColumnNames() : "none");
    }
}
