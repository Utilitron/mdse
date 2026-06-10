package io.mdse.metadata.service.impl;

import io.mdse.metadata.exception.ReflectionException;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.service.ReflectionService;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard implementation of ReflectionService with aggressive caching.
 *
 * Thread-safe and optimized for repeated access to the same entity classes.
 * Caches:
 * - Class lookups by name
 * - Field lookups by class+fieldName
 */
public class StandardReflectionService implements ReflectionService {
    
    /**
     * Sentinel value for "class not found" cache entries
     */
    private static final Class<?> NOT_FOUND_CLASS = NotFoundSentinel.class;
    
    /**
     * Sentinel value for "field not found" cache entries
     */
    private static final Field NOT_FOUND_FIELD;
    
    static {
        try {
            NOT_FOUND_FIELD = NotFoundSentinel.class.getDeclaredField("sentinel");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    /**
     * Cache of resolved classes by fully qualified name
     */
    private final Map<String, Class<?>> typeCache = new ConcurrentHashMap<>();
    
    /**
     * Cache of fields by (entityClass, fieldName)
     */
    private final Map<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Class<?>> resolveType(String javaTypeName) {
        if (javaTypeName == null || javaTypeName.isBlank()) {
            return Optional.empty();
        }
        
        // Check cache first
        Class<?> cached = typeCache.get(javaTypeName);
        if (cached != null) {
            if (cached == NOT_FOUND_CLASS) {
                return Optional.empty();
            }
            return Optional.of(cached);
        }
        
        try {
            // Try to load class
            Class<?> clazz = Class.forName(javaTypeName);
            typeCache.put(javaTypeName, clazz);
            return Optional.of(clazz);
        } catch (ClassNotFoundException e) {
            // Not found - cache sentinel to avoid repeated lookups
            typeCache.put(javaTypeName, NOT_FOUND_CLASS);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<Field> getField(Class<?> entityClass, String fieldName) {
        if (entityClass == null || fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }
        
        FieldKey key = new FieldKey(entityClass, fieldName);
        
        // Check cache first
        Field cached = fieldCache.get(key);
        if (cached != null) {
            if (cached == NOT_FOUND_FIELD) {
                return Optional.empty();
            }
            return Optional.of(cached);
        }
        
        try {
            // Try to find field (including inherited fields)
            Field field = findFieldInHierarchy(entityClass, fieldName);
            field.setAccessible(true);
            fieldCache.put(key, field);
            return Optional.of(field);
        } catch (NoSuchFieldException e) {
            // Mark as not found with sentinel
            fieldCache.put(key, NOT_FOUND_FIELD);
            return Optional.empty();
        }
    }
    
    @Override
    public Object getValue(Object entity, ColumnSchema column) throws ReflectionException {
        if (entity == null) {
            throw new ReflectionException("Cannot get value from null entity");
        }
        
        Field field = resolveField(entity.getClass(), column);
        
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(
                    "Cannot read field: " + column.getEffectiveFieldName() +
                            " in class: " + entity.getClass().getName(), e);
        }
    }
    
    @Override
    public void setValue(Object entity, ColumnSchema column, Object value) throws ReflectionException {
        if (entity == null) {
            throw new ReflectionException("Cannot set value on null entity");
        }
        
        Field field = resolveField(entity.getClass(), column);
        
        try {
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(
                    "Cannot write field: " + column.getEffectiveFieldName() +
                            " in class: " + entity.getClass().getName(), e);
        } catch (IllegalArgumentException e) {
            throw new ReflectionException(
                    String.format("Cannot assign value of type %s to field %s of type %s",
                            value != null ? value.getClass().getName() : "null",
                            column.getEffectiveFieldName(),
                            field.getType().getName()), e);
        }
    }
    
    @Override
    public Map<String, Object> extractValues(Object entity, TableSchema schema) {
        Map<String, Object> values = new HashMap<>();
        
        for (ColumnSchema column : schema.getColumns()) {
            try {
                Object value = getValue(entity, column);
                values.put(column.getColumnName(), value);
            } catch (ReflectionException e) {
                // Skip columns that don't exist in entity
                // (supports hybrid mode with extra database columns)
            }
        }
        
        return values;
    }
    
    @Override
    public void populateEntity(Object entity, TableSchema schema, Map<String, Object> values) {
        for (ColumnSchema column : schema.getColumns()) {
            String columnName = column.getColumnName();
            
            if (!values.containsKey(columnName)) {
                continue;
            }
            
            Object value = values.get(columnName);
            
            try {
                setValue(entity, column, value);
            } catch (ReflectionException e) {
                // Skip columns that don't exist in entity
                // (supports hybrid mode with extra database columns)
            }
        }
    }
    
    @Override
    public <T> T createInstance(Class<T> entityClass) throws ReflectionException {
        if (entityClass == null) {
            throw new ReflectionException("Cannot create instance of null class");
        }
        
        try {
            return entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ReflectionException(
                    "Cannot create instance of: " + entityClass.getName() +
                            ". Class must have a no-args constructor.", e);
        }
    }
    
    /**
     * Resolve field from cache or throw exception if not found
     */
    private Field resolveField(Class<?> entityClass, ColumnSchema column) {
        return getField(entityClass, column)
                .orElseThrow(() -> new ReflectionException(
                        "Field not found: " + column.getEffectiveFieldName() +
                                " in class: " + entityClass.getName()));
    }
    
    /**
     * Find field in class hierarchy (including superclasses)
     */
    private Field findFieldInHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        
        throw new NoSuchFieldException(
                "Field '" + fieldName + "' not found in " + clazz.getName() + " or its superclasses");
    }
    
    /**
     * Cache key for field lookups
     */
    private static class FieldKey {
        private final Class<?> entityClass;
        private final String fieldName;
        
        FieldKey(Class<?> entityClass, String fieldName) {
            this.entityClass = entityClass;
            this.fieldName = fieldName;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldKey fieldKey = (FieldKey) o;
            return Objects.equals(entityClass, fieldKey.entityClass) &&
                    Objects.equals(fieldName, fieldKey.fieldName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(entityClass, fieldName);
        }
    }
    
    /**
     * Sentinel class for cache entries (to avoid null in ConcurrentHashMap)
     */
    private static class NotFoundSentinel {
        private static final String sentinel = "NOT_FOUND";
    }
}
