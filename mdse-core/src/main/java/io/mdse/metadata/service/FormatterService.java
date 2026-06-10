package io.mdse.metadata.service;

import io.mdse.metadata.formatting.ValueFormatter;
import io.mdse.metadata.schema.ColumnSchema;

import java.text.ParseException;

/**
 * Formats and parses values according to schema type and format hints
 * Stateless service - separates formatting logic from schema metadata
 */
public interface FormatterService {
    
    /**
     * Format value for display
     *
     * @param value the value to format
     * @param column column schema with type and format information
     * @return formatted string representation
     */
    String format(Object value, ColumnSchema column);
    
    /**
     * Parse string to typed value
     *
     * @param input the string to parse
     * @param column column schema with type information
     * @return parsed value
     * @throws ParseException if parsing fails
     */
    Object parse(String input, ColumnSchema column) throws ParseException;
    
    /**
     * Register custom formatter for a type
     *
     * @param javaTypeName fully qualified type name
     * @param formatter the formatter implementation
     */
    void registerFormatter(String javaTypeName, ValueFormatter formatter);
}
