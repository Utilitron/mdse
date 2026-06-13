package io.mdse.generation.source;

import io.mdse.generation.descriptor.EntityDescriptor;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JpaEntityGenerator Tests")
class JpaEntityGeneratorTest {
    
    private JpaEntityGenerator generator;
    
    @BeforeEach
    void setUp() {
        generator = new JpaEntityGenerator();
    }
    
    @Test
    @DisplayName("Should support JPA target")
    void testSupportsJpaTarget() {
        assertThat(generator.supports(GenerationTarget.JPA)).isTrue();
        assertThat(generator.supports(GenerationTarget.POJO)).isFalse();
        assertThat(generator.getTarget()).isEqualTo(GenerationTarget.JPA);
    }
    
    @Test
    @DisplayName("Should generate entity class with basic structure")
    void testGenerateBasicEntity() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .schemaName("public")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .nullable(false)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("email")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(false)
                        .maxLength(255)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example.entities");
        
        Map<String, String> sources = generator.generate(descriptor);
        
        assertThat(sources).hasSize(1);
        assertThat(sources).containsKey("com.example.entities.User");
        
        String source = sources.get("com.example.entities.User");
        
        // Check package
        assertThat(source).contains("package com.example.entities;");
        
        // Check imports
        assertThat(source).contains("import jakarta.persistence.*;");
        
        // Check @Entity annotation
        assertThat(source).contains("@Entity");
        
        // Check @Table annotation
        assertThat(source).contains("@Table(name = \"users\"");
        assertThat(source).contains("schema = \"public\"");
        
        // Check class declaration
        assertThat(source).contains("public class User {");
        
        // Check @Generated annotation with schema hash
        assertThat(source).contains("@Generated");
        assertThat(source).contains("schemaHash=");
    }
    
    @Test
    @DisplayName("Should generate @Id and @GeneratedValue for primary key")
    void testGeneratePrimaryKeyAnnotations() {
        TableSchema schema = TableSchema.builder()
                .tableName("products")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .primaryKey(true)
                        .autoIncrement(true)
                        .generationStrategy("IDENTITY")
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        assertThat(source).contains("@Id");
        assertThat(source).contains("@GeneratedValue(strategy = GenerationType.IDENTITY)");
        assertThat(source).contains("private Long id;");
    }
    
    @Test
    @DisplayName("Should generate @Column with constraints")
    void testGenerateColumnAnnotations() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("email")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(false)
                        .unique(true)
                        .maxLength(255)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        assertThat(source).contains("@Column(name = \"email\"");
        assertThat(source).contains("nullable = false");
        assertThat(source).contains("unique = true");
        assertThat(source).contains("length = 255");
    }
    
    @Test
    @DisplayName("Should generate getters and setters")
    void testGenerateAccessors() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("name")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        // Getters
        assertThat(source).contains("public Long getId() {");
        assertThat(source).contains("return id;");
        assertThat(source).contains("public String getName() {");
        assertThat(source).contains("return name;");
        
        // Setters
        assertThat(source).contains("public void setId(Long id) {");
        assertThat(source).contains("this.id = id;");
        assertThat(source).contains("public void setName(String name) {");
        assertThat(source).contains("this.name = name;");
    }
    
    @Test
    @DisplayName("Should generate no-args constructor")
    void testGenerateNoArgsConstructor() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        assertThat(source).contains("public User() {");
    }
    
    @Test
    @DisplayName("Should generate constructor with required fields")
    void testGenerateRequiredFieldsConstructor() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("email")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("phone")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(true)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        // Should have constructor with email (required, non-generated)
        assertThat(source).contains("public User(String email) {");
        assertThat(source).contains("this.email = email;");
        
        // Should not include id (auto-generated) or phone (nullable)
        assertThat(source).doesNotContain("public User(Long id");
    }
    
    @Test
    @DisplayName("Should generate equals and hashCode based on primary key")
    void testGenerateEqualsHashCode() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("name")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        assertThat(source).contains("@Override");
        assertThat(source).contains("public boolean equals(Object o) {");
        assertThat(source).contains("Objects.equals(id, that.id)");
        
        assertThat(source).contains("public int hashCode() {");
        assertThat(source).contains("Objects.hash(id)");
    }
    
    @Test
    @DisplayName("Should generate toString method")
    void testGenerateToString() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("name")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        assertThat(source).contains("public String toString() {");
        assertThat(source).contains("\"User{\"");
        assertThat(source).contains("\"id=\" + id");
        assertThat(source).contains("\"name=\" + name");
    }
    
    @Test
    @DisplayName("Should include required imports")
    void testImports() {
        TableSchema schema = TableSchema.builder()
                .tableName("orders")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("created_at")
                        .sqlType(Types.TIMESTAMP)
                        .javaTypeName("java.time.LocalDateTime")
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("amount")
                        .sqlType(Types.DECIMAL)
                        .javaTypeName("java.math.BigDecimal")
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example");
        String source = generator.generate(descriptor).values().iterator().next();
        
        // JPA imports
        assertThat(source).contains("import jakarta.persistence.*;");
        
        // Java time imports
        assertThat(source).contains("import java.time.*;");
        
        // Math imports
        assertThat(source).contains("import java.math.BigDecimal;");
        
        // Utility imports
        assertThat(source).contains("import java.util.Objects;");
    }
}

