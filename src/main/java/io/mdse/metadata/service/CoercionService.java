package io.mdse.metadata.service;


import io.mdse.metadata.coercion.TypeCoercer;
import io.mdse.metadata.exception.CoercionException;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;

/**
 * Type coercion and conversion between representations
 * Stateless service - separates type conversion from schema metadata
 * 
 * Handles conversion from:
 * - String representations (HTTP params, CSV files)
 * - Database types (JDBC ResultSet values)
 * - Generic Object values
 * 
 * To the target Java types defined in schema.
 */
public interface CoercionService {
    
    /**
     * Coerce value to target type
     * 
     * @param value the raw value
     * @param targetType the target class
     * @param <T> target type
     * @return coerced value
     * @throws CoercionException if conversion fails
     */
    <T> T coerce(Object value, Class<T> targetType) throws CoercionException;
    
    /**
     * Coerce value according to column schema
     * Uses javaTypeName from schema to determine target type
     * 
     * @param value the raw value
     * @param column column schema with type information
     * @return coerced value
     * @throws CoercionException if conversion fails
     */
    Object coerce(Object value, ColumnSchema column) throws CoercionException;
    
    /**
     * Coerce all values in a map according to table schema
     * 
     * @param values map of column name to raw value
     * @param schema table schema
     * @return map of column name to coerced value
     * @throws CoercionException if any conversion fails
     */
    java.util.Map<String, Object> coerceAll(
        java.util.Map<String, Object> values,
        TableSchema schema
    ) throws CoercionException;
    
    /**
     * Check if coercion is possible without attempting it
     * 
     * @param value the value to check
     * @param column target column schema
     * @return true if coercion should succeed
     */
    boolean canCoerce(Object value, ColumnSchema column);
    
    /**
     * Register a custom coercer for a specific type
     * 
     * @param javaTypeName fully qualified type name
     * @param coercer the coercer implementation
     */
    void registerCoercer(String javaTypeName, TypeCoercer coercer);
}
