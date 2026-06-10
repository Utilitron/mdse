package io.mdse.metadata.service.impl;

import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.exception.CoercionException;
import io.mdse.metadata.service.CoercionService;
import io.mdse.metadata.coercion.TypeCoercer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard implementation of CoercionService.
 *
 * Handles common type conversions:
 * - String to primitives
 * - String to temporal types
 * - Numeric conversions
 * - Database types to Java types
 */
public class StandardCoercionService implements CoercionService {
    
    private final Map<String, TypeCoercer> coercers = new ConcurrentHashMap<>();
    
    public StandardCoercionService() {
        registerBuiltInCoercers();
    }
    
    @Override
    public <T> T coerce(Object value, Class<T> targetType) throws CoercionException {
        if (value == null) {
            return null;
        }
        
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        
        // Try registered coercer
        TypeCoercer coercer = coercers.get(targetType.getName());
        if (coercer != null) {
            return targetType.cast(coercer.coerce(value, null));
        }
        
        // Fallback to basic conversions
        return performBasicCoercion(value, targetType);
    }
    
    @Override
    public Object coerce(Object value, ColumnSchema column) throws CoercionException {
        if (value == null) {
            return null;
        }
        
        if (column == null) {
            return value; // No type info, return as-is
        }
        
        String javaTypeName = column.getJavaTypeName();
        if (javaTypeName == null) {
            return value; // No type info, return as-is
        }
        
        // Try registered coercer
        TypeCoercer coercer = coercers.get(javaTypeName);
        if (coercer != null) {
            return coercer.coerce(value, column);
        }
        
        // Fallback to class-based coercion
        try {
            Class<?> targetType = Class.forName(javaTypeName);
            return coerce(value, targetType);
        } catch (ClassNotFoundException e) {
            throw new CoercionException("Unknown target type: " + javaTypeName, e);
        }
    }
    
    @Override
    public Map<String, Object> coerceAll(Map<String, Object> values, TableSchema schema)
            throws CoercionException {
        
        Map<String, Object> coerced = new HashMap<>();
        
        for (ColumnSchema column : schema.getColumns()) {
            String columnName = column.getColumnName();
            
            if (values.containsKey(columnName)) {
                Object rawValue = values.get(columnName);
                Object coercedValue = coerce(rawValue, column);
                coerced.put(columnName, coercedValue);
            }
        }
        
        return coerced;
    }
    
    @Override
    public boolean canCoerce(Object value, ColumnSchema column) {
        if (value == null) {
            return true;
        }
        
        try {
            coerce(value, column);
            return true;
        } catch (CoercionException e) {
            return false;
        }
    }
    
    @Override
    public void registerCoercer(String javaTypeName, TypeCoercer coercer) {
        coercers.put(javaTypeName, coercer);
    }
    
    private void registerBuiltInCoercers() {
        // String
        registerCoercer("java.lang.String", (value, column) -> String.valueOf(value));
        
        // Integers
        registerCoercer("java.lang.Integer", this::coerceToInteger);
        registerCoercer("int", this::coerceToInteger);
        
        // Longs
        registerCoercer("java.lang.Long", this::coerceToLong);
        registerCoercer("long", this::coerceToLong);
        
        // Doubles
        registerCoercer("java.lang.Double", this::coerceToDouble);
        registerCoercer("double", this::coerceToDouble);
        
        // Booleans
        registerCoercer("java.lang.Boolean", this::coerceToBoolean);
        registerCoercer("boolean", this::coerceToBoolean);
        
        // BigDecimal
        registerCoercer("java.math.BigDecimal", this::coerceToBigDecimal);
        
        // LocalDate
        registerCoercer("java.time.LocalDate", this::coerceToLocalDate);
        
        // LocalDateTime
        registerCoercer("java.time.LocalDateTime", this::coerceToLocalDateTime);
        
        // Timestamp
        registerCoercer("java.sql.Timestamp", this::coerceToTimestamp);
    }
    
    private Object coerceToInteger(Object value, ColumnSchema column) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                throw new CoercionException("Cannot convert '" + str + "' to Integer", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to Integer");
    }
    
    private Object coerceToLong(Object value, ColumnSchema column) {
        if (value instanceof Number num) {
            return num.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                throw new CoercionException("Cannot convert '" + str + "' to Long", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to Long");
    }
    
    private Object coerceToDouble(Object value, ColumnSchema column) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                throw new CoercionException("Cannot convert '" + str + "' to Double", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to Double");
    }
    
    private Object coerceToBoolean(Object value, ColumnSchema column) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number num) {
            return num.intValue() != 0;
        }
        if (value instanceof String str) {
            String lower = str.trim().toLowerCase();
            if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
                return true;
            }
            if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
                return false;
            }
            throw new CoercionException("Cannot convert '" + str + "' to Boolean");
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to Boolean");
    }
    
    private Object coerceToBigDecimal(Object value, ColumnSchema column) {
        if (value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        if (value instanceof String str) {
            try {
                return new BigDecimal(str.trim());
            } catch (NumberFormatException e) {
                throw new CoercionException("Cannot convert '" + str + "' to BigDecimal", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to BigDecimal");
    }
    
    private Object coerceToLocalDate(Object value, ColumnSchema column) {
        if (value instanceof LocalDate) {
            return value;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof String str) {
            try {
                return LocalDate.parse(str.trim());
            } catch (DateTimeParseException e) {
                throw new CoercionException("Cannot convert '" + str + "' to LocalDate", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to LocalDate");
    }
    
    private Object coerceToLocalDateTime(Object value, ColumnSchema column) {
        if (value instanceof LocalDateTime) {
            return value;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof String str) {
            try {
                return LocalDateTime.parse(str.trim());
            } catch (DateTimeParseException e) {
                throw new CoercionException("Cannot convert '" + str + "' to LocalDateTime", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to LocalDateTime");
    }
    
    private Object coerceToTimestamp(Object value, ColumnSchema column) {
        if (value instanceof Timestamp) {
            return value;
        }
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof String str) {
            try {
                return Timestamp.valueOf(str.trim());
            } catch (IllegalArgumentException e) {
                throw new CoercionException("Cannot convert '" + str + "' to Timestamp", e);
            }
        }
        throw new CoercionException("Cannot coerce " + value.getClass() + " to Timestamp");
    }
    
    @SuppressWarnings("unchecked")
    private <T> T performBasicCoercion(Object value, Class<T> targetType) throws CoercionException {
        // String conversion
        if (targetType == String.class) {
            return (T) String.valueOf(value);
        }
        
        // Already correct type
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        
        throw new CoercionException("No coercer registered for type: " + targetType.getName() + " (value type: " + value.getClass().getName() + ")"
        );
    }
}
