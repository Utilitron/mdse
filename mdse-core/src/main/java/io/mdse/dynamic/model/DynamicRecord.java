package io.mdse.dynamic.model;

import io.mdse.metadata.schema.PrimaryKeySchema;
import io.mdse.metadata.schema.TableSchema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * Represents a single row of data from any table.
 * This is the runtime data container for the metadata-driven system.
 *
 * Pure dynamic mode only – hybrid mode (entity backing) is handled by separate services
 * (e.g., EntityMapper) that use ReflectionService.
 *
 * Change tracking is provided for efficient updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicRecord {
    
    /**
     * Table name
     */
    private String tableName;
    
    /**
     * Schema name
     */
    private String schemaName;
    
    /**
     * Immutable schema of the table (structural description)
     */
    private TableSchema tableSchema;

    /**
     * Column values (column name → value)
     */
    @Builder.Default
    private Map<String, Object> values = new HashMap<>();
    
    /**
     * Whether this record has been modified since last reset
     */
    @Getter
    private transient boolean modified;
    
    /**
     * Original values snapshot for change tracking
     */
    private transient Map<String, Object> originalValues;

    /**
     * Get a value by column name
     */
    public Object getValue(String columnName) {
        return values.get(columnName);
    }
    
    /**
     * Set a value by column name, tracking the change
     */
    public void setValue(String columnName, Object value) {
        if (originalValues == null) {
            originalValues = new HashMap<>(values);
        }
        values.put(columnName, value);
        modified = true;
    }
    
    /**
     * Get typed value (convenience, no coercion)
     *
     * @throws ClassCastException if the value is not of the requested type
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String columnName, Class<T> type) {
        Object value = getValue(columnName);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new ClassCastException(String.format(
                "Cannot cast %s to %s for column %s",
                value.getClass().getName(), type.getName(), columnName));
    }
    
    /**
     * Get optional typed value
     */
    public <T> Optional<T> getOptionalValue(String columnName, Class<T> type) {
        try {
            return Optional.ofNullable(getValue(columnName, type));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if column has a non‑null value in this record
     */
    public boolean hasValue(String columnName) {
        return values.containsKey(columnName) && values.get(columnName) != null;
    }
    
    /**
     * Remove a value from the record
     */
    public Object removeValue(String columnName) {
        if (originalValues == null) {
            originalValues = new HashMap<>(values);
        }
        modified = true;
        return values.remove(columnName);
    }

    /**
     * Get primary key values as a map (column name → value)
     */
    public Map<String, Object> getPrimaryKeyValues() {
        if (tableSchema == null || !tableSchema.hasPrimaryKey()) {
            return Map.of();
        }

        PrimaryKeySchema pk = tableSchema.getPrimaryKey();
        Map<String, Object> pkValues = new HashMap<>();
        for (String pkColumn : pk.getColumnNames()) {
            Object value = values.get(pkColumn);
            if (value != null) {
                pkValues.put(pkColumn, value);
            }
        }
        return pkValues;
    }
    
    /**
     * Get single primary key value (for simple keys)
     *
     * @throws IllegalStateException if no primary key or composite key
     */
    public Object getSinglePrimaryKeyValue() {
        if (tableSchema == null || !tableSchema.hasPrimaryKey()) {
            throw new IllegalStateException("Table has no primary key");
        }
        if (tableSchema.hasCompositePrimaryKey()) {
            throw new IllegalStateException("Cannot get single primary key value from composite key");
        }
        String pkColumn = tableSchema.getPrimaryKey().getColumnNames().get(0);
        return values.get(pkColumn);
    }
    
    /**
     * Set single primary key value (for simple keys)
     *
     * @throws IllegalStateException if no primary key or composite key
     */
    public void setSinglePrimaryKeyValue(Object value) {
        if (tableSchema == null || !tableSchema.hasPrimaryKey()) {
            throw new IllegalStateException("Table has no primary key");
        }
        if (tableSchema.hasCompositePrimaryKey()) {
            throw new IllegalStateException("Cannot set single primary key value on composite key");
        }
        String pkColumn = tableSchema.getPrimaryKey().getColumnNames().get(0);
        setValue(pkColumn, value);
    }

    /**
     * Set multiple values at once.
     */
    public void setValues(Map<String, Object> newValues) {
        if (originalValues == null) {
            originalValues = new HashMap<>(values);
        }
        values.putAll(newValues);
        modified = true;
    }
    
    /**
     * Get all column names that currently have values
     */
    public Set<String> getColumnNames() {
        return values.keySet();
    }
    
    /**
     * Get copy of all values
     */
    public Map<String, Object> getAllValues() {
        return new HashMap<>(values);
    }
    
    /**
     * Clear all values
     */
    public void clear() {
        if (originalValues == null) {
            originalValues = new HashMap<>(values);
        }
        values.clear();
        modified = true;
    }
    
    /**
     * Get only the columns that have been modified since last reset
     */
    public Map<String, Object> getModifiedValues() {
        if (originalValues == null) {
            return Map.of();
        }
        Map<String, Object> modifiedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = originalValues.get(key);
            if (!Objects.equals(oldVal, newVal)) {
                modifiedMap.put(key, newVal);
            }
        }
        return modifiedMap;
    }

    /**
     * Reset change tracking (mark as unmodified, capture current values as original)
     */
    public void resetChangeTracking() {
        this.modified = false;
        this.originalValues = new HashMap<>(values);
    }

    /**
     * Discard all changes and revert to the original values.
     */
    public void discardChanges() {
        if (originalValues != null) {
            this.values = new HashMap<>(originalValues);
            this.modified = false;
        }
    }

    /**
     * Get fully qualified table name (schema.table).
     */
    public String getQualifiedTableName() {
        if (schemaName != null && !schemaName.isBlank()) {
            return schemaName + "." + tableName;
        }
        return tableName;
    }
    
    /**
     * Check if this record has a valid schema reference
     */
    public boolean hasSchema() {
        return tableSchema != null;
    }
    
    @Override
    public String toString() {
        return String.format("DynamicRecord[%s, values=%d, pk=%s, modified=%s]",
                getQualifiedTableName(), values.size(), getPrimaryKeyValues(), modified);
    }
}
