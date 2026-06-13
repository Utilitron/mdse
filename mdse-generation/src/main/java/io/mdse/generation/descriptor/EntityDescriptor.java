package io.mdse.generation.descriptor;

import io.mdse.metadata.schema.ForeignKeySchema;
import io.mdse.metadata.schema.ReferentialAction;
import io.mdse.metadata.schema.RelationshipCardinality;
import io.mdse.metadata.schema.TableSchema;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete entity descriptor for code generation.
 * Generator-neutral representation of a table as a Java entity.
 *
 * This is the single source of truth for code generators.
 * All generators (JPA, jOOQ, MyBatis, etc.) consume this.
 */
@Value
@Builder(toBuilder = true)
public class EntityDescriptor {
    
    /**
     * Package name for generated class
     * Example: "io.mdse.generated.entities"
     */
    String packageName;
    
    /**
     * Simple class name (no package)
     * Example: "User", "OrderItem"
     */
    String className;
    
    /**
     * Fully qualified class name
     * Example: "io.mdse.generated.entities.User"
     */
    String fullyQualifiedName;
    
    /**
     * Database table name
     */
    String tableName;
    
    /**
     * Database schema name
     */
    String schemaName;
    
    /**
     * Database catalog name
     */
    String catalogName;
    
    /**
     * All fields in this entity
     */
    @Singular
    List<FieldDescriptor> fields;
    
    /**
     * All relationships (foreign keys)
     */
    @Singular
    List<RelationshipDescriptor> relationships;
    
    /**
     * SHA-256 hash of the source schema
     * Used for drift detection and regeneration decisions
     */
    String schemaHash;
    
    /**
     * Table description/comment
     */
    String description;
    
    /**
     * Source table schema
     */
    TableSchema sourceSchema;
    
    /**
     * Get primary key fields
     */
    public List<FieldDescriptor> getPrimaryKeyFields() {
        return fields.stream()
                .filter(FieldDescriptor::isPrimaryKey)
                .toList();
    }
    
    /**
     * Get non-primary key fields
     */
    public List<FieldDescriptor> getNonKeyFields() {
        return fields.stream()
                .filter(f -> !f.isPrimaryKey())
                .toList();
    }
    
    /**
     * Get generated fields (auto-increment, sequences)
     */
    public List<FieldDescriptor> getGeneratedFields() {
        return fields.stream()
                .filter(FieldDescriptor::isGenerated)
                .toList();
    }
    
    /**
     * Get required (non-nullable) fields
     */
    public List<FieldDescriptor> getRequiredFields() {
        return fields.stream()
                .filter(f -> !f.isNullable() && !f.isGenerated())
                .toList();
    }
    
    /**
     * Get ManyToOne relationships (belongs-to)
     */
    public List<RelationshipDescriptor> getManyToOneRelationships() {
        return relationships.stream()
                .filter(RelationshipDescriptor::isManyToOne)
                .toList();
    }
    
    /**
     * Get OneToMany relationships (has-many)
     */
    public List<RelationshipDescriptor> getOneToManyRelationships() {
        return relationships.stream()
                .filter(RelationshipDescriptor::isOneToMany)
                .toList();
    }
    
    /**
     * Check if this entity has a composite primary key
     */
    public boolean hasCompositePrimaryKey() {
        return getPrimaryKeyFields().size() > 1;
    }
    
    /**
     * Check if this entity has any relationships
     */
    public boolean hasRelationships() {
        return !relationships.isEmpty();
    }
    
    /**
     * Get all imports needed for this entity
     */
    public List<String> getRequiredImports() {
        return fields.stream()
                .map(FieldDescriptor::getType)
                .filter(type -> type.contains(".") && !type.startsWith("java.lang"))
                .distinct()
                .sorted()
                .toList();
    }
    
    /**
     * Build EntityDescriptor from TableSchema
     */
    public static EntityDescriptor from(TableSchema schema, String packageName) {
        String className = toClassName(schema.getTableName());
        
        return EntityDescriptor.builder()
                .packageName(packageName)
                .className(className)
                .fullyQualifiedName(packageName + "." + className)
                .tableName(schema.getTableName())
                .schemaName(schema.getSchemaName())
                .catalogName(schema.getCatalogName())
                .fields(schema.getColumns().stream()
                        .map(FieldDescriptor::from)
                        .toList())
                .relationships(schema.getForeignKeys() != null
                        ? schema.getForeignKeys().stream()
                        .map(fk -> buildRelationship(fk, packageName))
                        .toList()
                        : List.of())
                .schemaHash(computeSchemaHash(schema))
                .description(schema.getDescription())
                .sourceSchema(schema)
                .build();
    }
    
    /**
     * Convert table name to Java class name
     * Examples:
     * - "users" -> "User"
     * - "order_items" -> "OrderItem"
     * - "product_categories" -> "ProductCategory"
     */
    private static String toClassName(String tableName) {
        // Remove trailing 's' for plural tables (heuristic)
        String singular = tableName.endsWith("s") && tableName.length() > 1
                ? tableName.substring(0, tableName.length() - 1)
                : tableName;
        
        // Convert to PascalCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : singular.toCharArray()) {
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
    
    /**
     * Build relationship descriptor from foreign key
     */
    private static RelationshipDescriptor buildRelationship(
            ForeignKeySchema fk, String packageName) {
        
        String targetClassName = toClassName(fk.getReferencedTable());
        String fieldName = toFieldName(fk.getReferencedTable());
        
        return RelationshipDescriptor.builder()
                .name(fieldName)
                .targetEntity(targetClassName)
                .targetEntityFqn(packageName + "." + targetClassName)
                .cardinality(RelationshipCardinality.MANY_TO_ONE) // Most common
                .bidirectional(false) // Will be determined by graph analysis
                .joinColumns(fk.getSourceColumns())
                .referencedColumns(fk.getReferencedColumns())
                .deleteAction(mapReferentialAction(fk.getDeleteRule()))
                .updateAction(mapReferentialAction(fk.getUpdateRule()))
                .owningSide(true)
                .sourceForeignKey(fk)
                .build();
    }
    
    private static String toFieldName(String tableName) {
        String className = toClassName(tableName);
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
    
    private static ReferentialAction mapReferentialAction(ReferentialAction action) {
        if (action == null) {
            return ReferentialAction.NO_ACTION;
        }
        
        return switch (action) {
            case CASCADE -> ReferentialAction.CASCADE;
            case SET_NULL -> ReferentialAction.SET_NULL;
            case SET_DEFAULT -> ReferentialAction.SET_DEFAULT;
            case RESTRICT -> ReferentialAction.RESTRICT;
            case NO_ACTION -> ReferentialAction.NO_ACTION;
        };
    }
    
    /**
     * Compute SHA-256 hash of schema for drift detection
     */
    private static String computeSchemaHash(TableSchema schema) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Include table structure in hash
            StringBuilder hashInput = new StringBuilder();
            hashInput.append(schema.getTableName()).append("|");
            
            // Columns (name, type, constraints)
            for (var column : schema.getColumns()) {
                hashInput.append(column.getColumnName()).append(":")
                        .append(column.getSqlType()).append(":")
                        .append(column.isNullable()).append(":")
                        .append(column.isPrimaryKey()).append("|");
            }
            
            // Foreign keys
            if (schema.getForeignKeys() != null) {
                for (var fk : schema.getForeignKeys()) {
                    hashInput.append(fk.getConstraintName()).append(":")
                            .append(fk.getReferencedTable()).append("|");
                }
            }
            
            byte[] hash = digest.digest(hashInput.toString().getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

