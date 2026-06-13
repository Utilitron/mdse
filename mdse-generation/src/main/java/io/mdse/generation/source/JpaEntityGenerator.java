package io.mdse.generation.source;

import io.mdse.generation.descriptor.EntityDescriptor;
import io.mdse.generation.descriptor.FieldDescriptor;
import io.mdse.generation.descriptor.RelationshipDescriptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates JPA/Hibernate annotated entity classes.
 *
 * Generated classes include:
 * - @Entity and @Table annotations
 * - @Id and @GeneratedValue for primary keys
 * - @Column annotations with constraints
 * - @ManyToOne and @OneToMany for relationships
 * - Standard getters/setters
 * - equals/hashCode based on primary key
 * - toString for debugging
 */
public class JpaEntityGenerator implements SourceGenerator {
    
    private final String generatorVersion = "1.0.0";
    
    @Override
    public boolean supports(GenerationTarget target) {
        return target == GenerationTarget.JPA;
    }
    
    @Override
    public GenerationTarget getTarget() {
        return GenerationTarget.JPA;
    }
    
    @Override
    public Map<String, String> generate(EntityDescriptor descriptor) {
        Map<String, String> sources = new LinkedHashMap<>();
        
        // Generate entity class
        String entitySource = generateEntityClass(descriptor);
        sources.put(descriptor.getFullyQualifiedName(), entitySource);
        
        // TODO: If composite primary key, generate @IdClass or @Embeddable
        // For now, skip composite key generation
        
        return sources;
    }
    
    private String generateEntityClass(EntityDescriptor descriptor) {
        StringBuilder source = new StringBuilder();
        
        // Package declaration
        source.append("package ").append(descriptor.getPackageName()).append(";\n\n");
        
        // Imports
        source.append(generateImports(descriptor));
        source.append("\n");
        
        // @Generated annotation (for fingerprinting)
        source.append(generateGeneratedAnnotation(descriptor));
        source.append("\n");
        
        // Class declaration with @Entity and @Table
        source.append(generateClassDeclaration(descriptor));
        source.append("\n");
        
        // Fields
        source.append(generateFields(descriptor));
        source.append("\n");
        
        // Constructors
        source.append(generateConstructors(descriptor));
        source.append("\n");
        
        // Getters and Setters
        source.append(generateAccessors(descriptor));
        source.append("\n");
        
        // equals and hashCode
        source.append(generateEqualsAndHashCode(descriptor));
        source.append("\n");
        
        // toString
        source.append(generateToString(descriptor));
        
        source.append("}\n");
        
        return source.toString();
    }
    
    private String generateImports(EntityDescriptor descriptor) {
        StringBuilder imports = new StringBuilder();
        
        // Jakarta Persistence imports
        imports.append("import jakarta.persistence.*;\n");
        
        // Jakarta Annotations for @Generated
        imports.append("import jakarta.annotation.Generated;\n");
        
        // Java imports
        if (descriptor.getFields().stream().anyMatch(f -> f.getType().startsWith("java.time"))) {
            imports.append("import java.time.*;\n");
        }
        
        if (descriptor.getFields().stream().anyMatch(f -> f.getType().equals("java.sql.Timestamp"))) {
            imports.append("import java.sql.Timestamp;\n");
        }
        
        if (descriptor.getFields().stream().anyMatch(f -> f.getType().contains("BigDecimal"))) {
            imports.append("import java.math.BigDecimal;\n");
        }
        
        if (descriptor.hasRelationships() && !descriptor.getOneToManyRelationships().isEmpty()) {
            imports.append("import java.util.ArrayList;\n");
            imports.append("import java.util.List;\n");
        }
        
        imports.append("import java.util.Objects;\n");
        
        return imports.toString();
    }
    
    private String generateGeneratedAnnotation(EntityDescriptor descriptor) {
        return String.format("""
            /**
             * Generated entity for table: %s
             * Schema hash: %s
             * DO NOT EDIT MANUALLY - changes will be lost on regeneration
             */
            @Generated(
                value = "%s",
                date = "%s",
                comments = "schemaHash=%s"
            )""",
                descriptor.getTableName(),
                descriptor.getSchemaHash(),
                this.getClass().getName(),
                Instant.now().toString(),
                descriptor.getSchemaHash()
        );
    }
    
    private String generateClassDeclaration(EntityDescriptor descriptor) {
        StringBuilder decl = new StringBuilder();
        
        decl.append("@Entity\n");
        
        // @Table with schema if present
        if (descriptor.getSchemaName() != null && !descriptor.getSchemaName().isBlank()) {
            decl.append(String.format("@Table(name = \"%s\", schema = \"%s\")\n",
                    descriptor.getTableName(), descriptor.getSchemaName()));
        } else {
            decl.append(String.format("@Table(name = \"%s\")\n", descriptor.getTableName()));
        }
        
        decl.append("public class ").append(descriptor.getClassName()).append(" {\n");
        
        return decl.toString();
    }
    
    private String generateFields(EntityDescriptor descriptor) {
        StringBuilder fields = new StringBuilder();
        
        // Regular fields
        for (FieldDescriptor field : descriptor.getFields()) {
            fields.append(generateField(field));
        }
        
        // Relationship fields
        for (RelationshipDescriptor rel : descriptor.getRelationships()) {
            fields.append(generateRelationshipField(rel));
        }
        
        return fields.toString();
    }
    
    private String generateField(FieldDescriptor field) {
        StringBuilder fieldDecl = new StringBuilder();
        
        fieldDecl.append("    ");
        
        // Primary key annotation
        if (field.isPrimaryKey()) {
            fieldDecl.append("@Id\n    ");
            
            if (field.isGenerated()) {
                String strategy = field.getGenerationStrategy();
                if ("IDENTITY".equals(strategy)) {
                    fieldDecl.append("@GeneratedValue(strategy = GenerationType.IDENTITY)\n    ");
                } else if ("SEQUENCE".equals(strategy)) {
                    fieldDecl.append("@GeneratedValue(strategy = GenerationType.SEQUENCE)\n    ");
                } else {
                    fieldDecl.append("@GeneratedValue(strategy = GenerationType.AUTO)\n    ");
                }
            }
        }
        
        // @Column annotation
        StringBuilder columnAnnotation = new StringBuilder("@Column(name = \"");
        columnAnnotation.append(field.getColumnName()).append("\"");
        
        if (!field.isNullable()) {
            columnAnnotation.append(", nullable = false");
        }
        
        if (field.isUnique()) {
            columnAnnotation.append(", unique = true");
        }
        
        if (field.getMaxLength() != null && field.getMaxLength() > 0) {
            columnAnnotation.append(", length = ").append(field.getMaxLength());
        }
        
        columnAnnotation.append(")");
        fieldDecl.append(columnAnnotation).append("\n    ");
        
        // Field declaration
        fieldDecl.append("private ")
                .append(field.getSimpleType())
                .append(" ")
                .append(field.getName())
                .append(";\n\n");
        
        return fieldDecl.toString();
    }
    
    private String generateRelationshipField(RelationshipDescriptor rel) {
        StringBuilder fieldDecl = new StringBuilder();
        
        fieldDecl.append("    ");
        
        // Relationship annotation
        if (rel.isManyToOne()) {
            fieldDecl.append("@ManyToOne");
            if (rel.getDeleteAction() != null) {
                // Add fetch type and cascade if needed
                fieldDecl.append("(fetch = FetchType.LAZY)");
            }
            fieldDecl.append("\n    ");
            
            // Join column
            if (!rel.getJoinColumns().isEmpty()) {
                String joinColumn = rel.getJoinColumns().get(0);
                fieldDecl.append(String.format("@JoinColumn(name = \"%s\")", joinColumn));
                fieldDecl.append("\n    ");
            }
        } else if (rel.isOneToMany()) {
            fieldDecl.append("@OneToMany");
            if (rel.getMappedBy() != null) {
                fieldDecl.append(String.format("(mappedBy = \"%s\")", rel.getMappedBy()));
            }
            fieldDecl.append("\n    ");
        }
        
        // Field declaration
        String type = rel.isOneToMany() || rel.isManyToMany()
                ? "List<" + rel.getTargetEntity() + ">"
                : rel.getTargetEntity();
        
        String initializer = rel.isOneToMany() || rel.isManyToMany()
                ? " = new ArrayList<>()"
                : "";
        
        fieldDecl.append("private ")
                .append(type)
                .append(" ")
                .append(rel.getName())
                .append(initializer)
                .append(";\n\n");
        
        return fieldDecl.toString();
    }
    
    private String generateConstructors(EntityDescriptor descriptor) {
        StringBuilder constructors = new StringBuilder();
        
        // Default no-args constructor (required by JPA)
        constructors.append("    public ").append(descriptor.getClassName()).append("() {\n");
        constructors.append("    }\n\n");
        
        // Constructor with required fields (non-null, non-generated)
        var requiredFields = descriptor.getRequiredFields();
        if (!requiredFields.isEmpty()) {
            constructors.append("    public ").append(descriptor.getClassName()).append("(");
            
            String params = requiredFields.stream()
                    .map(f -> f.getSimpleType() + " " + f.getName())
                    .collect(Collectors.joining(", "));
            
            constructors.append(params).append(") {\n");
            
            for (FieldDescriptor field : requiredFields) {
                constructors.append("        this.").append(field.getName())
                        .append(" = ").append(field.getName()).append(";\n");
            }
            
            constructors.append("    }\n");
        }
        
        return constructors.toString();
    }
    
    private String generateAccessors(EntityDescriptor descriptor) {
        StringBuilder accessors = new StringBuilder();
        
        // Getters and setters for fields
        for (FieldDescriptor field : descriptor.getFields()) {
            accessors.append(generateGetter(field));
            accessors.append(generateSetter(field));
        }
        
        // Getters and setters for relationships
        for (RelationshipDescriptor rel : descriptor.getRelationships()) {
            accessors.append(generateRelationshipGetter(rel));
            accessors.append(generateRelationshipSetter(rel));
        }
        
        return accessors.toString();
    }
    
    private String generateGetter(FieldDescriptor field) {
        return String.format("""
            public %s %s() {
                return %s;
            }
            
            """,
                field.getSimpleType(),
                field.getGetterName(),
                field.getName()
        );
    }
    
    private String generateSetter(FieldDescriptor field) {
        return String.format("""
            public void %s(%s %s) {
                this.%s = %s;
            }
            
            """,
                field.getSetterName(),
                field.getSimpleType(),
                field.getName(),
                field.getName(),
                field.getName()
        );
    }
    
    private String generateRelationshipGetter(RelationshipDescriptor rel) {
        String type = rel.getGenericDeclaration();
        String methodName = "get" + capitalize(rel.getName());
        
        return String.format("""
            public %s %s() {
                return %s;
            }
            
            """,
                type,
                methodName,
                rel.getName()
        );
    }
    
    private String generateRelationshipSetter(RelationshipDescriptor rel) {
        String type = rel.getGenericDeclaration();
        String methodName = "set" + capitalize(rel.getName());
        
        return String.format("""
            public void %s(%s %s) {
                this.%s = %s;
            }
            
            """,
                methodName,
                type,
                rel.getName(),
                rel.getName(),
                rel.getName()
        );
    }
    
    private String generateEqualsAndHashCode(EntityDescriptor descriptor) {
        var pkFields = descriptor.getPrimaryKeyFields();
        
        if (pkFields.isEmpty()) {
            // No PK - use identity comparison
            return """
                @Override
                public boolean equals(Object o) {
                    return this == o;
                }
                
                @Override
                public int hashCode() {
                    return System.identityHashCode(this);
                }
                
                """;
        }
        
        // Use PK fields for equals/hashCode
        StringBuilder equals = new StringBuilder();
        equals.append("    @Override\n");
        equals.append("    public boolean equals(Object o) {\n");
        equals.append("        if (this == o) return true;\n");
        equals.append("        if (o == null || getClass() != o.getClass()) return false;\n");
        equals.append("        ").append(descriptor.getClassName()).append(" that = (")
                .append(descriptor.getClassName()).append(") o;\n");
        equals.append("        return ");
        
        String comparison = pkFields.stream()
                .map(f -> "Objects.equals(" + f.getName() + ", that." + f.getName() + ")")
                .collect(Collectors.joining(" && "));
        
        equals.append(comparison).append(";\n");
        equals.append("    }\n\n");
        
        equals.append("    @Override\n");
        equals.append("    public int hashCode() {\n");
        equals.append("        return Objects.hash(");
        equals.append(pkFields.stream()
                .map(FieldDescriptor::getName)
                .collect(Collectors.joining(", ")));
        equals.append(");\n");
        equals.append("    }\n");
        
        return equals.toString();
    }
    
    private String generateToString(EntityDescriptor descriptor) {
        StringBuilder toString = new StringBuilder();
        toString.append("    @Override\n");
        toString.append("    public String toString() {\n");
        toString.append("        return \"").append(descriptor.getClassName()).append("{\" +\n");
        
        var fields = descriptor.getFields();
        for (int i = 0; i < fields.size(); i++) {
            FieldDescriptor field = fields.get(i);
            toString.append("                \"").append(field.getName())
                    .append("=\" + ").append(field.getName());
            
            if (i < fields.size() - 1) {
                toString.append(" + \", \" +\n");
            } else {
                toString.append(" +\n");
            }
        }
        
        toString.append("                '}';\n");
        toString.append("    }\n");
        
        return toString.toString();
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}

