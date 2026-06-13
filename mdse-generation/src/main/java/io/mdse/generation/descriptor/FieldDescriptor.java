package io.mdse.generation.descriptor;

import io.mdse.metadata.schema.ColumnSchema;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Generator-neutral field descriptor.
 * Enriches ColumnSchema with naming conventions and generation hints.
 *
 * This is the bridge between database metadata and code generation.
 */
@Value
@Builder(toBuilder = true)
public class FieldDescriptor {
    
    /**
     * Java field name (camelCase)
     * Example: "userId", "firstName", "createdAt"
     */
    String name;
    
    /**
     * Fully qualified Java type
     * Example: "java.lang.Long", "java.time.LocalDateTime"
     */
    String type;
    
    /**
     * Boxed type for primitives (null if already boxed)
     * Example: "Integer" for int, null for String
     */
    String boxedType;
    
    /**
     * Database column name
     */
    String columnName;
    
    /**
     * Whether this field can be null
     */
    boolean nullable;
    
    /**
     * Whether this is a primary key field
     */
    boolean primaryKey;
    
    /**
     * Whether this field is auto-generated (sequence, identity, UUID)
     */
    boolean generated;
    
    /**
     * Generation strategy: IDENTITY, SEQUENCE, UUID, AUTO
     */
    String generationStrategy;
    
    /**
     * Whether this field is unique
     */
    boolean unique;
    
    /**
     * Maximum string length (for VARCHAR columns)
     */
    Integer maxLength;
    
    /**
     * Minimum numeric value
     */
    Long minValue;
    
    /**
     * Maximum numeric value
     */
    Long maxValue;
    
    /**
     * Validation regex pattern
     */
    String validationPattern;
    
    /**
     * Allowed enumeration values
     */
    @Singular
    List<String> allowedValues;
    
    /**
     * Default value expression
     */
    String defaultValue;
    
    /**
     * Column comment/description
     */
    String description;
    
    /**
     * Source column schema (for reference)
     */
    ColumnSchema sourceColumn;
    
    /**
     * Check if this field is a primitive type
     */
    public boolean isPrimitive() {
        return type.equals("int") || type.equals("long")
                || type.equals("double") || type.equals("float")
                || type.equals("boolean") || type.equals("byte")
                || type.equals("short") || type.equals("char");
    }
    
    /**
     * Get the type to use in declarations (boxed for nullables)
     */
    public String getDeclarationType() {
        if (nullable && isPrimitive()) {
            return boxedType != null ? boxedType : type;
        }
        return type;
    }
    
    /**
     * Get simple type name (without package)
     */
    public String getSimpleType() {
        String declType = getDeclarationType();
        int lastDot = declType.lastIndexOf('.');
        return lastDot >= 0 ? declType.substring(lastDot + 1) : declType;
    }
    
    /**
     * Get getter method name
     */
    public String getGetterName() {
        if (type.equals("boolean") || type.equals("java.lang.Boolean")) {
            return "is" + capitalize(name);
        }
        return "get" + capitalize(name);
    }
    
    /**
     * Get setter method name
     */
    public String getSetterName() {
        return "set" + capitalize(name);
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * Build from ColumnSchema
     */
    public static FieldDescriptor from(ColumnSchema column) {
        return FieldDescriptor.builder()
                .name(toJavaFieldName(column.getEffectiveFieldName()))
                .type(mapJavaType(column))
                .boxedType(getBoxedType(column))
                .columnName(column.getColumnName())
                .nullable(column.isNullable())
                .primaryKey(column.isPrimaryKey())
                .generated(column.isAutoIncrement() || column.isGenerated())
                .generationStrategy(determineGenerationStrategy(column))
                .unique(column.isUnique())
                .maxLength(column.getMaxLength())
                .minValue(column.getMinValue())
                .maxValue(column.getMaxValue())
                .validationPattern(column.getValidationPattern())
                .allowedValues(column.getAllowedValues() != null
                        ? column.getAllowedValues().stream()
                        .map(io.mdse.metadata.schema.EnumerationValue::getValue)
                        .toList()
                        : null)
                .defaultValue(column.getDefaultValue())
                .description(column.getDescription())
                .sourceColumn(column)
                .build();
    }
    
    private static String toJavaFieldName(String fieldName) {
        // Convert snake_case or kebab-case to camelCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        
        for (char c : fieldName.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
    
    private static String mapJavaType(ColumnSchema column) {
        String javaTypeName = column.getJavaTypeName();
        if (javaTypeName != null && !javaTypeName.isBlank()) {
            return javaTypeName;
        }
        
        // Fallback based on SQL type
        if (column.isNumericType()) {
            if (column.getPrecision() != null && column.getPrecision() > 0) {
                return "java.math.BigDecimal";
            }
            return "java.lang.Long";
        }
        
        if (column.isTextType()) {
            return "java.lang.String";
        }
        
        if (column.isTemporalType()) {
            return "java.time.LocalDateTime";
        }
        
        if (column.isBooleanType()) {
            return "java.lang.Boolean";
        }
        
        return "java.lang.Object";
    }
    
    private static String getBoxedType(ColumnSchema column) {
        String type = mapJavaType(column);
        
        return switch (type) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "char" -> "Character";
            default -> null;
        };
    }
    
    private static String determineGenerationStrategy(ColumnSchema column) {
        if (!column.isAutoIncrement() && !column.isGenerated()) {
            return null;
        }
        
        String strategy = column.getGenerationStrategy();
        if (strategy != null && !strategy.isBlank()) {
            return strategy.toUpperCase();
        }
        
        // Infer from type and constraints
        if (column.isAutoIncrement()) {
            return "IDENTITY";
        }
        
        return "AUTO";
    }
}

