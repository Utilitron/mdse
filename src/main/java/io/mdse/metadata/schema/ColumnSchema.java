package io.mdse.metadata.schema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable structural description of a database column.
 * Pure value object - contains only schema information, no runtime behavior.
 *
 * This is the foundation of the metadata-driven architecture.
 * All operational concerns (validation, formatting, reflection) are handled
 * by separate services that operate on these immutable schemas.
 */
@Value
@Builder(toBuilder = true)
public class ColumnSchema {
    
    /**
     * Unique identifier for this column schema
     */
    UUID id;
    
    /**
     * Physical column name in the database
     */
    String columnName;
    
    /**
     * Java field name (if mapping to entity class)
     * Falls back to columnName if not specified
     */
    String fieldName;
    
    /**
     * SQL data type (from java.sql.Types)
     */
    int sqlType;
    
    /**
     * Database type name (VARCHAR, INTEGER, TIMESTAMP, etc.)
     */
    String dbTypeName;
    
    /**
     * Column size/length (for character and binary types)
     */
    Integer columnSize;
    
    /**
     * Decimal precision (for numeric types)
     */
    Integer precision;
    
    /**
     * Decimal scale (for numeric types)
     */
    Integer scale;
    
    /**
     * Ordinal position in the table (1-based)
     */
    int ordinalPosition;
    
    /**
     * Fully qualified Java type name (String representation)
     * Example: "java.lang.String", "java.time.LocalDateTime"
     *
     * We use String instead of Class<?> to keep schema serializable
     * and avoid classloader dependencies.
     */
    String javaTypeName;
    
    /**
     * Whether this column accepts NULL values
     */
    boolean nullable;
    
    /**
     * Whether this column is part of the primary key
     */
    boolean primaryKey;
    
    /**
     * Whether this column has a unique constraint
     */
    boolean unique;
    
    /**
     * Whether this column is auto-increment/generated
     */
    boolean autoIncrement;
    
    /**
     * Whether this column is generated (computed/virtual)
     */
    boolean generated;
    
    /**
     * Generation strategy (IDENTITY, SEQUENCE, UUID, etc.)
     */
    String generationStrategy;
    
    /**
     * Default value expression
     */
    String defaultValue;
    
    /**
     * Check constraint expression (if any)
     */
    String checkConstraint;
    
    /**
     * Maximum numeric value (inclusive) for numeric columns
     */
    Long maxValue;
    
    /**
     * Minimum numeric value (inclusive) for numeric columns
     */
    Long minValue;
    
    /**
     * Maximum string length (for text columns)
     */
    Integer maxLength;
    
    /**
     * Validation pattern (regex) for text columns
     */
    String validationPattern;
    
    // ============================================================
    // ENUMERATION SUPPORT
    // ============================================================
    
    /**
     * Allowed enumeration values (if this column represents an enum)
     * For database enums or application-level enumerations
     */
    @Singular
    List<EnumerationValue> allowedValues;
    
    /**
     * Column description/comment from database
     */
    String description;
    
    /**
     * Whether this column should be persisted in the database
     * (false for transient/calculated fields)
     */
    @Builder.Default
    boolean persistent = true;
    
    /**
     * Custom annotations for extensibility
     * Use for format hints, application-specific metadata, etc.
     *
     * Examples:
     * - "format": "yyyy-MM-dd"
     * - "precision": "2"
     * - "currency": "USD"
     */
    @Singular
    Map<String, String> annotations;
    
    /**
     * Get effective field name (or fallback to column name)
     */
    public String getEffectiveFieldName() {
        return fieldName != null ? fieldName : columnName;
    }
    
    /**
     * Check if this column is required (NOT NULL with no default)
     */
    public boolean isRequired() {
        return !nullable && !autoIncrement && defaultValue == null;
    }
    
    /**
     * Check if this is a text/string column
     */
    public boolean isTextType() {
        return sqlType == java.sql.Types.VARCHAR
                || sqlType == java.sql.Types.CHAR
                || sqlType == java.sql.Types.LONGVARCHAR
                || sqlType == java.sql.Types.CLOB;
    }
    
    /**
     * Check if this is a numeric column
     */
    public boolean isNumericType() {
        return sqlType == java.sql.Types.INTEGER
                || sqlType == java.sql.Types.BIGINT
                || sqlType == java.sql.Types.SMALLINT
                || sqlType == java.sql.Types.TINYINT
                || sqlType == java.sql.Types.DECIMAL
                || sqlType == java.sql.Types.NUMERIC
                || sqlType == java.sql.Types.FLOAT
                || sqlType == java.sql.Types.DOUBLE
                || sqlType == java.sql.Types.REAL;
    }
    
    /**
     * Check if this is a date/time column
     */
    public boolean isTemporalType() {
        return sqlType == java.sql.Types.DATE
                || sqlType == java.sql.Types.TIME
                || sqlType == java.sql.Types.TIMESTAMP
                || sqlType == java.sql.Types.TIME_WITH_TIMEZONE
                || sqlType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
    }
    
    /**
     * Check if this is a boolean column
     */
    public boolean isBooleanType() {
        return sqlType == java.sql.Types.BOOLEAN
                || sqlType == java.sql.Types.BIT;
    }
    
    /**
     * Check if this is a binary column
     */
    public boolean isBinaryType() {
        return sqlType == java.sql.Types.BINARY
                || sqlType == java.sql.Types.VARBINARY
                || sqlType == java.sql.Types.LONGVARBINARY
                || sqlType == java.sql.Types.BLOB;
    }
    
    /**
     * Structural validation - ensures this schema is well-formed
     */
    public boolean isStructurallyValid() {
        return columnName != null
                && !columnName.isBlank()
                && sqlType != 0
                && ordinalPosition > 0;
    }
    
    @Override
    public String toString() {
        return String.format("ColumnSchema[%s (%s), type=%s, nullable=%s, pk=%s]",
                columnName,
                dbTypeName,
                javaTypeName != null ? javaTypeName : "unknown",
                nullable,
                primaryKey);
    }
}
