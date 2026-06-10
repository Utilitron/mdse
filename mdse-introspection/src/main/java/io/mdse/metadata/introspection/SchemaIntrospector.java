package io.mdse.metadata.introspection;

import io.mdse.metadata.schema.TableSchema;

import java.util.Collection;
import java.util.Optional;

/**
 * Service responsible for introspecting database schema at runtime
 * This is the bridge between the physical database and the immutable metadata model
 */
public interface SchemaIntrospector {
    
    /**
     * Introspect all tables in the database
     *
     * @return collection of immutable table schemas
     */
    Collection<TableSchema> introspectAll();
    
    /**
     * Introspect all tables in a specific schema
     *
     * @param schemaName the schema name
     * @return collection of immutable table schemas
     */
    Collection<TableSchema> introspectSchema(String schemaName);
    
    /**
     * Introspect a specific table in the default schema
     *
     * @param tableName the table name
     * @return optional containing the table schema if found
     */
    Optional<TableSchema> introspectTable(String tableName);
    
    /**
     * Introspect a specific table in a specific schema
     *
     * @param schemaName the schema name
     * @param tableName  the table name
     * @return optional containing the table schema if found
     */
    Optional<TableSchema> introspectTable(String schemaName, String tableName);
    
    /**
     * Refresh metadata for a table that may have changed
     *
     * @param tableName the table name
     * @return optional containing the refreshed schema
     */
    Optional<TableSchema> refreshTable(String tableName);
    
    /**
     * Get the catalog name being introspected
     *
     * @return catalog name (may be null if not applicable)
     */
    String getCatalogName();
    
    /**
     * Get the default schema name
     *
     * @return default schema name (may be null if not applicable)
     */
    String getDefaultSchemaName();
}

