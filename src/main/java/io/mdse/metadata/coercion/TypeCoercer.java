package io.mdse.metadata.coercion;

import io.mdse.metadata.exception.CoercionException;
import io.mdse.metadata.schema.ColumnSchema;

/**
 * Functional interface for custom type coercers
 */
@FunctionalInterface
public interface TypeCoercer {
    
    /**
     * Coerce value to target type
     *
     * @param value the value to coerce
     * @param column column schema with additional hints (may be null)
     * @return coerced value
     * @throws CoercionException if conversion fails
     */
    Object coerce(Object value, ColumnSchema column) throws CoercionException;
}

