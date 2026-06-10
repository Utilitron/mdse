package io.mdse.metadata.formatting;

import io.mdse.metadata.schema.ColumnSchema;

/**
 * Functional interface for custom value formatters
 */
@FunctionalInterface
public interface ValueFormatter {
    
    /**
     * Format value to string
     */
    String format(Object value, ColumnSchema column);
}
