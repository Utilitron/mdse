package io.mdse.metadata.service;

import io.mdse.metadata.exception.ReflectionException;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Handles all reflection operations for entity mapping.
 * Stateless service - separates reflection concerns from schema metadata.
 *
 * This enables:
 * - Testing without entities
 * - Multiple reflection strategies
 * - Caching reflection results separately from schemas
 * - Supporting both static entities and dynamic records
 */
public interface ReflectionService {
    
    /**
     * Resolve Java Class from schema type name.
     *
     * @param javaTypeName fully qualified class name
     * @return resolved class, or empty if not found/loadable
     */
    Optional<Class<?>> resolveType(String javaTypeName);
    
    /**
     * Get field accessor for a column in an entity class.
     *
     * @param entityClass the entity class
     * @param fieldName the field name
     * @return the field, or empty if not found
     */
    Optional<Field> getField(Class<?> entityClass, String fieldName);
    
    /**
     * Get field accessor for a column using schema metadata.
     *
     * @param entityClass the entity class
     * @param column column schema with field name
     * @return the field, or empty if not found
     */
    default Optional<Field> getField(Class<?> entityClass, ColumnSchema column) {
        return getField(entityClass, column.getEffectiveFieldName());
    }
    
    /**
     * Read value from entity using column schema.
     *
     * @param entity the entity instance
     * @param column the column schema
     * @return the field value
     * @throws ReflectionException if field cannot be accessed
     */
    Object getValue(Object entity, ColumnSchema column) throws ReflectionException;
    
    /**
     * Write value to entity using column schema.
     *
     * @param entity the entity instance
     * @param column the column schema
     * @param value the value to set
     * @throws ReflectionException if field cannot be accessed
     */
    void setValue(Object entity, ColumnSchema column, Object value) throws ReflectionException;
    
    /**
     * Read all column values from entity into a map.
     *
     * @param entity the entity instance
     * @param schema the table schema
     * @return map of column name to value
     */
    java.util.Map<String, Object> extractValues(Object entity, TableSchema schema);
    
    /**
     * Populate entity from a map of values.
     *
     * @param entity the entity instance (already created)
     * @param schema the table schema
     * @param values map of column name to value
     * @throws ReflectionException if any field cannot be set
     */
    void populateEntity(Object entity, TableSchema schema, java.util.Map<String, Object> values);
    
    /**
     * Create instance of entity class using default constructor.
     *
     * @param entityClass the class to instantiate
     * @param <T> the entity type
     * @return new instance
     * @throws ReflectionException if instantiation fails
     */
    <T> T createInstance(Class<T> entityClass) throws ReflectionException;
    
    /**
     * Create and populate entity instance from values.
     *
     * @param entityClass the entity class
     * @param schema the table schema
     * @param values map of column name to value
     * @param <T> the entity type
     * @return populated entity instance
     * @throws ReflectionException if creation or population fails
     */
    default <T> T createAndPopulate(
            Class<T> entityClass,
            TableSchema schema,
            java.util.Map<String, Object> values) {
        T instance = createInstance(entityClass);
        populateEntity(instance, schema, values);
        return instance;
    }
    
    /**
     * Check if a class has a field matching the column.
     *
     * @param entityClass the entity class
     * @param column the column schema
     * @return true if field exists
     */
    default boolean hasField(Class<?> entityClass, ColumnSchema column) {
        return getField(entityClass, column).isPresent();
    }
}
