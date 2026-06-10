package io.mdse.metadata.service.impl;

import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.service.FormatterService;
import io.mdse.metadata.formatting.ValueFormatter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard implementation of FormatterService with caching
 *
 * Built-in formatters for:
 * - Temporal types (LocalDate, LocalDateTime)
 * - Numeric types (BigDecimal, Integer, Long, Double)
 * - Boolean types
 * - String types
 */
public class StandardFormatterService implements FormatterService {
    
    private final Map<String, ValueFormatter> formatters = new ConcurrentHashMap<>();
    private final Map<FormatterKey, DateTimeFormatter> dateTimeFormatters = new ConcurrentHashMap<>();
    private final Map<FormatterKey, DecimalFormat> decimalFormatters = new ConcurrentHashMap<>();
    
    public StandardFormatterService() {
        registerBuiltInFormatters();
    }
    
    @Override
    public String format(Object value, ColumnSchema column) {
        if (value == null) {
            return "";
        }
        
        String javaTypeName = column.getJavaTypeName();
        if (javaTypeName == null) {
            return String.valueOf(value);
        }
        
        ValueFormatter formatter = formatters.get(javaTypeName);
        if (formatter != null) {
            return formatter.format(value, column);
        }
        
        // Fallback to toString
        return String.valueOf(value);
    }
    
    @Override
    public Object parse(String input, ColumnSchema column) throws ParseException {
        if (input == null || input.isBlank()) {
            return null;
        }
        
        String javaTypeName = column.getJavaTypeName();
        if (javaTypeName == null) {
            return input;
        }
        
        // Delegate to CoercionService for parsing
        // This is primarily a formatting service
        return input;
    }
    
    @Override
    public void registerFormatter(String javaTypeName, ValueFormatter formatter) {
        formatters.put(javaTypeName, formatter);
    }
    
    private void registerBuiltInFormatters() {
        // String
        registerFormatter("java.lang.String", (value, column) -> String.valueOf(value));
        
        // LocalDate
        registerFormatter("java.time.LocalDate", this::formatLocalDate);
        
        // LocalDateTime
        registerFormatter("java.time.LocalDateTime", this::formatLocalDateTime);
        
        // BigDecimal
        registerFormatter("java.math.BigDecimal", this::formatBigDecimal);
        
        // Integer
        registerFormatter("java.lang.Integer", (value, column) -> String.valueOf(value));
        registerFormatter("int", (value, column) -> String.valueOf(value));
        
        // Long
        registerFormatter("java.lang.Long", (value, column) -> String.valueOf(value));
        registerFormatter("long", (value, column) -> String.valueOf(value));
        
        // Double
        registerFormatter("java.lang.Double", this::formatDouble);
        registerFormatter("double", this::formatDouble);
        
        // Boolean
        registerFormatter("java.lang.Boolean", (value, column) -> String.valueOf(value));
        registerFormatter("boolean", (value, column) -> String.valueOf(value));
    }
    
    private String formatLocalDate(Object value, ColumnSchema column) {
        if (!(value instanceof LocalDate date)) {
            return String.valueOf(value);
        }
        
        String pattern = column.getAnnotations().get("format");
        if (pattern == null || pattern.isBlank()) {
            return date.toString();
        }
        
        try {
            DateTimeFormatter formatter = getDateTimeFormatter(pattern);
            return date.format(formatter);
        } catch (DateTimeException e) {
            // Pattern incompatible with LocalDate
            return date.toString();
        }
    }
    
    private String formatLocalDateTime(Object value, ColumnSchema column) {
        if (!(value instanceof LocalDateTime dateTime)) {
            return String.valueOf(value);
        }
        
        String pattern = column.getAnnotations().get("format");
        if (pattern == null || pattern.isBlank()) {
            return dateTime.toString(); // ISO format
        }
        
        DateTimeFormatter formatter = getDateTimeFormatter(pattern);
        return dateTime.format(formatter);
    }
    
    private String formatBigDecimal(Object value, ColumnSchema column) {
        if (!(value instanceof BigDecimal decimal)) {
            return String.valueOf(value);
        }
        
        String pattern = column.getAnnotations().get("format");
        if (pattern == null || pattern.isBlank()) {
            return decimal.toPlainString();
        }
        
        DecimalFormat formatter = getDecimalFormatter(pattern);
        return formatter.format(decimal);
    }
    
    private String formatDouble(Object value, ColumnSchema column) {
        if (!(value instanceof Double dbl)) {
            return String.valueOf(value);
        }
        
        String pattern = column.getAnnotations().get("format");
        if (pattern == null || pattern.isBlank()) {
            return String.valueOf(dbl);
        }
        
        DecimalFormat formatter = getDecimalFormatter(pattern);
        return formatter.format(dbl);
    }
    
    private DateTimeFormatter getDateTimeFormatter(String pattern) {
        FormatterKey key = new FormatterKey("datetime", pattern);
        return dateTimeFormatters.computeIfAbsent(key, k -> {
            try {
                return DateTimeFormatter.ofPattern(pattern);
            } catch (IllegalArgumentException e) {
                // Invalid pattern, fallback to ISO
                return DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            }
        });
    }
    
    private DecimalFormat getDecimalFormatter(String pattern) {
        FormatterKey key = new FormatterKey("decimal", pattern);
        return decimalFormatters.computeIfAbsent(key, k -> {
            if (!(pattern.contains("0") || pattern.contains("#"))) {
                return new DecimalFormat("#,##0.00");
            }
            try {
                return new DecimalFormat(pattern);
            } catch (IllegalArgumentException e) {
                // Invalid pattern, fallback to default
                return new DecimalFormat("#,##0.00");
            }
        });
    }
    
    /**
         * Cache key for formatters
         */
        private record FormatterKey(String type, String pattern) {
        
        @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                FormatterKey that = (FormatterKey) o;
                return type.equals(that.type) && pattern.equals(that.pattern);
            }
        
    }
}
