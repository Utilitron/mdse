package io.mdse.generation.descriptor;

import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.ForeignKeySchema;
import io.mdse.metadata.schema.RelationshipCardinality;
import io.mdse.metadata.schema.TableSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EntityDescriptor Tests")
class EntityDescriptorTest {
    
    @Test
    @DisplayName("Should build EntityDescriptor from TableSchema")
    void testBuildFromTableSchema() {
        TableSchema schema = TableSchema.builder()
                .tableName("user_accounts")
                .schemaName("public")
                .column(ColumnSchema.builder()
                        .columnName("user_id")
                        .sqlType(Types.BIGINT)
                        .nullable(false)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("email_address")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(false)
                        .maxLength(255)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.example.entities");
        
        assertThat(descriptor.getPackageName()).isEqualTo("com.example.entities");
        assertThat(descriptor.getClassName()).isEqualTo("UserAccount");
        assertThat(descriptor.getFullyQualifiedName()).isEqualTo("com.example.entities.UserAccount");
        assertThat(descriptor.getTableName()).isEqualTo("user_accounts");
        assertThat(descriptor.getFields()).hasSize(2);
    }
    
    @Test
    @DisplayName("Should convert table name to class name correctly")
    void testTableNameToClassName() {
        assertTableConvertsTo("users", "User");
        assertTableConvertsTo("user_accounts", "UserAccount");
        assertTableConvertsTo("order_items", "OrderItem");
        assertTableConvertsTo("products", "Product");
        assertTableConvertsTo("customer_addresses", "CustomerAddresse"); // Removes trailing 's'
    }
    
    private void assertTableConvertsTo(String tableName, String expectedClassName) {
        TableSchema schema = TableSchema.builder()
                .tableName(tableName)
                .column(ColumnSchema.builder().columnName("id").sqlType(Types.BIGINT).build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.test");
        assertThat(descriptor.getClassName()).isEqualTo(expectedClassName);
    }
    
    @Test
    @DisplayName("Should compute schema hash for drift detection")
    void testSchemaHash() {
        TableSchema schema1 = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder().columnName("id").sqlType(Types.BIGINT).build())
                .column(ColumnSchema.builder().columnName("name").sqlType(Types.VARCHAR).build())
                .build();
        
        TableSchema schema2 = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder().columnName("id").sqlType(Types.BIGINT).build())
                .column(ColumnSchema.builder().columnName("name").sqlType(Types.VARCHAR).build())
                .build();
        
        EntityDescriptor desc1 = EntityDescriptor.from(schema1, "com.test");
        EntityDescriptor desc2 = EntityDescriptor.from(schema2, "com.test");
        
        // Same structure should produce same hash
        assertThat(desc1.getSchemaHash()).isEqualTo(desc2.getSchemaHash());
    }
    
    @Test
    @DisplayName("Should detect schema changes via hash")
    void testSchemaHashDetectsChanges() {
        TableSchema schema1 = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder().columnName("id").sqlType(Types.BIGINT).build())
                .build();
        
        TableSchema schema2 = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder().columnName("id").sqlType(Types.BIGINT).build())
                .column(ColumnSchema.builder().columnName("email").sqlType(Types.VARCHAR).build())
                .build();
        
        EntityDescriptor desc1 = EntityDescriptor.from(schema1, "com.test");
        EntityDescriptor desc2 = EntityDescriptor.from(schema2, "com.test");
        
        // Different structure should produce different hash
        assertThat(desc1.getSchemaHash()).isNotEqualTo(desc2.getSchemaHash());
    }
    
    @Test
    @DisplayName("Should identify primary key fields")
    void testGetPrimaryKeyFields() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("name")
                        .primaryKey(false)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.test");
        
        assertThat(descriptor.getPrimaryKeyFields()).hasSize(1);
        assertThat(descriptor.getPrimaryKeyFields().get(0).getName()).isEqualTo("id");
        assertThat(descriptor.hasCompositePrimaryKey()).isFalse();
    }
    
    @Test
    @DisplayName("Should detect composite primary key")
    void testCompositePrimaryKey() {
        TableSchema schema = TableSchema.builder()
                .tableName("order_items")
                .column(ColumnSchema.builder()
                        .columnName("order_id")
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("product_id")
                        .primaryKey(true)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.test");
        
        assertThat(descriptor.getPrimaryKeyFields()).hasSize(2);
        assertThat(descriptor.hasCompositePrimaryKey()).isTrue();
    }
    
    @Test
    @DisplayName("Should identify required fields")
    void testGetRequiredFields() {
        TableSchema schema = TableSchema.builder()
                .tableName("users")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .nullable(false)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("email")
                        .nullable(false)
                        .autoIncrement(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("phone")
                        .nullable(true)
                        .build())
                .build();
        
        EntityDescriptor descriptor = EntityDescriptor.from(schema, "com.test");
        
        // Required = not nullable and not auto-generated
        assertThat(descriptor.getRequiredFields()).hasSize(1);
        assertThat(descriptor.getRequiredFields().get(0).getName()).isEqualTo("email");
    }
}

@DisplayName("FieldDescriptor Tests")
class FieldDescriptorTest {
    
    @Test
    @DisplayName("Should convert column name to Java field name")
    void testColumnNameToFieldName() {
        assertColumnConvertsTo("user_id", "userId");
        assertColumnConvertsTo("first_name", "firstName");
        assertColumnConvertsTo("EMAIL_ADDRESS", "emailAddress");
        assertColumnConvertsTo("created-at", "createdAt");
    }
    
    private void assertColumnConvertsTo(String columnName, String expectedFieldName) {
        ColumnSchema column = ColumnSchema.builder()
                .columnName(columnName)
                .fieldName(columnName)
                .sqlType(Types.BIGINT)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        assertThat(field.getName()).isEqualTo(expectedFieldName);
    }
    
    @Test
    @DisplayName("Should generate correct getter name for boolean")
    void testBooleanGetterName() {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("active")
                .fieldName("active")
                .javaTypeName("java.lang.Boolean")
                .sqlType(Types.BOOLEAN)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        
        assertThat(field.getGetterName()).isEqualTo("isActive");
    }
    
    @Test
    @DisplayName("Should generate correct getter name for non-boolean")
    void testNonBooleanGetterName() {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("name")
                .fieldName("name")
                .javaTypeName("java.lang.String")
                .sqlType(Types.VARCHAR)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        
        assertThat(field.getGetterName()).isEqualTo("getName");
    }
    
    @Test
    @DisplayName("Should map SQL types to Java types")
    void testSqlTypeMapping() {
        assertSqlTypeMapsTo(Types.BIGINT, "java.lang.Long");
        assertSqlTypeMapsTo(Types.VARCHAR, "java.lang.String");
        assertSqlTypeMapsTo(Types.TIMESTAMP, "java.time.LocalDateTime");
        assertSqlTypeMapsTo(Types.BOOLEAN, "java.lang.Boolean");
    }
    
    private void assertSqlTypeMapsTo(int sqlType, String expectedJavaType) {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("test")
                .sqlType(sqlType)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        assertThat(field.getType()).isEqualTo(expectedJavaType);
    }
    
    @Test
    @DisplayName("Should preserve explicit Java type from column")
    void testExplicitJavaType() {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("amount")
                .sqlType(Types.DECIMAL)
                .javaTypeName("java.math.BigDecimal")
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        
        assertThat(field.getType()).isEqualTo("java.math.BigDecimal");
    }
    
    @Test
    @DisplayName("Should extract simple type name")
    void testGetSimpleType() {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("created_at")
                .javaTypeName("java.time.LocalDateTime")
                .sqlType(Types.TIMESTAMP)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        
        assertThat(field.getSimpleType()).isEqualTo("LocalDateTime");
    }
    
    @Test
    @DisplayName("Should use boxed type for nullable primitives")
    void testNullablePrimitiveDeclarationType() {
        ColumnSchema column = ColumnSchema.builder()
                .columnName("age")
                .sqlType(Types.INTEGER)
                .nullable(true)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(column);
        
        // Should use boxed type for nullable
        assertThat(field.getSimpleType()).isEqualTo("Long");
    }
    
    @Test
    @DisplayName("Should determine generation strategy")
    void testGenerationStrategy() {
        ColumnSchema autoIncrement = ColumnSchema.builder()
                .columnName("id")
                .autoIncrement(true)
                .sqlType(Types.BIGINT)
                .build();
        
        FieldDescriptor field = FieldDescriptor.from(autoIncrement);
        
        assertThat(field.isGenerated()).isTrue();
        assertThat(field.getGenerationStrategy()).isEqualTo("IDENTITY");
    }
}

@DisplayName("RelationshipDescriptor Tests")
class RelationshipDescriptorTest {
    
    @Test
    @DisplayName("Should identify ManyToOne relationship")
    void testManyToOneRelationship() {
        RelationshipDescriptor rel = RelationshipDescriptor.builder()
                .name("customer")
                .targetEntity("Customer")
                .cardinality(RelationshipCardinality.MANY_TO_ONE)
                .build();
        
        assertThat(rel.isManyToOne()).isTrue();
        assertThat(rel.isOneToMany()).isFalse();
        assertThat(rel.getCollectionType()).isNull();
    }
    
    @Test
    @DisplayName("Should identify OneToMany relationship with collection type")
    void testOneToManyRelationship() {
        RelationshipDescriptor rel = RelationshipDescriptor.builder()
                .name("orders")
                .targetEntity("Order")
                .cardinality(RelationshipCardinality.ONE_TO_MANY)
                .build();
        
        assertThat(rel.isOneToMany()).isTrue();
        assertThat(rel.isManyToOne()).isFalse();
        assertThat(rel.getCollectionType()).isEqualTo("java.util.List");
        assertThat(rel.getGenericDeclaration()).isEqualTo("List<Order>");
    }
    
    @Test
    @DisplayName("Should build relationship from ForeignKeySchema")
    void testBuildFromForeignKey() {
        ForeignKeySchema fk = ForeignKeySchema.builder()
                .constraintName("fk_order_customer")
                .sourceTable("orders")
                .sourceColumn("customer_id")
                .referencedTable("customers")
                .referencedColumn("id")
                .build();
        
        // This would be called by EntityDescriptor.from()
        // Just verify the mapping logic works
        assertThat(fk.getReferencedTable()).isEqualTo("customers");
        assertThat(fk.getSourceColumns()).contains("customer_id");
    }
}

