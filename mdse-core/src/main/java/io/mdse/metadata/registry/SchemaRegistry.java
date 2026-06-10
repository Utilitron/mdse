package io.mdse.metadata.registry;

import io.mdse.metadata.exception.MetadataNotFoundException;
import io.mdse.metadata.schema.TableSchema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for immutable table schemas.
 * Indexes by both simple table name and qualified name (case‑insensitive).
 */
@RequiredArgsConstructor
@Slf4j
public class SchemaRegistry {
    
    private final Map<String, TableSchema> byTableName = new ConcurrentHashMap<>();
    private final Map<String, TableSchema> byQualifiedName = new ConcurrentHashMap<>();
    
    /**
     * Register a table schema.
     * Overwrites any existing schema with the same table name or qualified name.
     */
    public void register(TableSchema schema) {
        String tableKey = schema.getTableName().toLowerCase();
        String qualifiedKey = schema.getQualifiedName().toLowerCase();
        byTableName.put(tableKey, schema);
        byQualifiedName.put(qualifiedKey, schema);
        log.debug("Registered schema: {}", schema.getQualifiedName());
    }
    
    /**
     * Remove a schema by name (simple or qualified).
     *
     * @return true if a schema was removed
     */
    public boolean unregister(String name) {
        String key = name.toLowerCase();
        // Try table name first
        TableSchema removed = byTableName.remove(key);
        if (removed != null) {
            // Also remove by its qualified name
            byQualifiedName.remove(removed.getQualifiedName().toLowerCase());
            log.debug("Unregistered schema: {}", removed.getQualifiedName());
            return true;
        }
        // Try qualified name
        removed = byQualifiedName.remove(key);
        if (removed != null) {
            byTableName.remove(removed.getTableName().toLowerCase());
            log.debug("Unregistered schema: {}", removed.getQualifiedName());
            return true;
        }
        return false;
    }
    
    /**
     * Retrieve a schema by name (simple or qualified). Case‑insensitive.
     */
    public Optional<TableSchema> get(String name) {
        String key = name.toLowerCase();
        TableSchema schema = byTableName.get(key);
        if (schema == null) {
            schema = byQualifiedName.get(key);
        }
        return Optional.ofNullable(schema);
    }
    
    /**
     * Retrieve a schema or throw {@link MetadataNotFoundException}.
     */
    public TableSchema getRequired(String name) {
        return get(name).orElseThrow(() -> new MetadataNotFoundException("Table schema not found: " + name));
    }
    
    /**
     * Returns all registered schemas (by simple table name).
     * Returns an immutable collection to prevent modification.
     */
    public Collection<TableSchema> getAll() {
        return Collections.unmodifiableCollection(byTableName.values());
    }
    
    /**
     * Remove all schemas from the registry.
     */
    public void clear() {
        byTableName.clear();
        byQualifiedName.clear();
    }
    
}
