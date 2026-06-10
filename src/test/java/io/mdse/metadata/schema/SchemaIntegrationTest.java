package io.mdse.metadata.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for immutable schema classes.
 * Verifies structural integrity, navigation, and basic derived logic.
 */
@DisplayName("Schema Integration Tests")
class SchemaIntegrationTest {
    
    @Nested
    @DisplayName("Complete Table Schema Integration")
    class CompleteTableSchemaTests {
        
        @Test
        @DisplayName("Should build complete table with all relationships")
        void testCompleteTableStructure() {
            // Create columns
            ColumnSchema idCol = ColumnSchema.builder()
                    .columnName("id")
                    .sqlType(Types.BIGINT)
                    .javaTypeName(Long.class.getName())
                    .nullable(false)
                    .ordinalPosition(1)
                    .autoIncrement(true)
                    .generationStrategy("IDENTITY")
                    .build();
            
            ColumnSchema nameCol = ColumnSchema.builder()
                    .columnName("name")
                    .sqlType(Types.VARCHAR)
                    .javaTypeName(String.class.getName())
                    .nullable(false)
                    .columnSize(255)
                    .maxLength(255)
                    .ordinalPosition(2)
                    .build();
            
            ColumnSchema accountIdCol = ColumnSchema.builder()
                    .columnName("account_id")
                    .sqlType(Types.BIGINT)
                    .javaTypeName(Long.class.getName())
                    .nullable(false)
                    .ordinalPosition(3)
                    .build();
            
            // Create primary key
            PrimaryKeySchema pk = PrimaryKeySchema.builder()
                    .constraintName("pk_users")
                    .columnName("id")
                    .build();
            
            // Create foreign key
            ForeignKeySchema fk = ForeignKeySchema.builder()
                    .constraintName("fk_user_account")
                    .sourceTable("users")
                    .sourceColumn("account_id")
                    .referencedTable("accounts")
                    .referencedColumn("id")
                    .cardinality(RelationshipCardinality.MANY_TO_ONE)
                    .deleteRule(ReferentialAction.CASCADE)
                    .updateRule(ReferentialAction.CASCADE)
                    .build();
            
            // Create index
            IndexSchema idx = IndexSchema.builder()
                    .indexName("idx_users_name")
                    .columnName("name")
                    .unique(false)
                    .indexType("BTREE")
                    .build();
            
            // Create unique constraint
            UniqueConstraintSchema uc = UniqueConstraintSchema.builder()
                    .constraintName("uk_name")
                    .columnName("name")
                    .build();
            
            // Build complete table
            TableSchema table = TableSchema.builder()
                    .id(UUID.randomUUID())
                    .schemaName("public")
                    .tableName("users")
                    .tableType(TableType.TABLE)
                    .column(idCol)
                    .column(nameCol)
                    .column(accountIdCol)
                    .primaryKey(pk)
                    .foreignKey(fk)
                    .index(idx)
                    .uniqueConstraint(uc)
                    .description("User accounts")
                    .lastRefreshed(Instant.now())
                    .annotation("auditable", "true")
                    .build();
            
            // Verify structure
            assertNotNull(table);
            assertEquals("users", table.getTableName());
            assertEquals(3, table.getColumns().size());
            assertTrue(table.hasPrimaryKey());
            assertEquals(1, table.getForeignKeys().size());
            assertEquals(1, table.getIndexes().size());
            assertEquals(1, table.getUniqueConstraints().size());
            
            // Verify navigation
            assertTrue(table.findColumn("account_id").isPresent());
            assertTrue(table.findForeignKey("fk_user_account").isPresent());
            assertTrue(table.findIndex("idx_users_name").isPresent());
            assertTrue(table.findUniqueConstraint("uk_name").isPresent());
        }
        
        @Test
        @DisplayName("Should handle composite primary key with foreign keys")
        void testCompositeKeysIntegration() {
            // Composite PK columns
            ColumnSchema orderId = ColumnSchema.builder()
                    .columnName("order_id")
                    .sqlType(Types.BIGINT)
                    .nullable(false)
                    .ordinalPosition(1)
                    .build();
            
            ColumnSchema itemId = ColumnSchema.builder()
                    .columnName("item_id")
                    .sqlType(Types.BIGINT)
                    .nullable(false)
                    .ordinalPosition(2)
                    .build();
            
            // Composite primary key
            PrimaryKeySchema compositePk = PrimaryKeySchema.builder()
                    .constraintName("pk_order_items")
                    .columnName("order_id")
                    .columnName("item_id")
                    .build();
            
            // Foreign key referencing part of composite key
            ForeignKeySchema fk = ForeignKeySchema.builder()
                    .constraintName("fk_order_item_order")
                    .sourceTable("order_items")
                    .sourceColumn("order_id")
                    .referencedTable("orders")
                    .referencedColumn("id")
                    .cardinality(RelationshipCardinality.MANY_TO_ONE)
                    .build();
            
            TableSchema table = TableSchema.builder()
                    .tableName("order_items")
                    .column(orderId)
                    .column(itemId)
                    .primaryKey(compositePk)
                    .foreignKey(fk)
                    .build();
            
            assertTrue(table.hasCompositePrimaryKey());
            assertEquals(2, table.getPrimaryKey().getColumnNames().size());
            assertTrue(table.getPrimaryKey().getColumnNames().contains("order_id"));
            assertTrue(table.getPrimaryKey().getColumnNames().contains("item_id"));
        }
    }
    
    @Nested
    @DisplayName("Relationship Chain Tests")
    class RelationshipChainTests {
        
        @Test
        @DisplayName("Should handle bidirectional one-to-many/many-to-one pair via annotations")
        void testBidirectionalOneToManyPair() {
            // In the schema layer, bidirectionality is expressed through foreign key annotations
            // We can store inverse property in ForeignKeySchema.annotations
            
            ForeignKeySchema orderCustomer = ForeignKeySchema.builder()
                    .constraintName("fk_order_customer")
                    .sourceTable("orders")
                    .sourceColumn("customer_id")
                    .referencedTable("customers")
                    .referencedColumn("id")
                    .cardinality(RelationshipCardinality.MANY_TO_ONE)
                    .annotation("inverseProperty", "orders")
                    .build();
            
            assertTrue(orderCustomer.getAnnotations().containsKey("inverseProperty"));
            assertEquals("orders", orderCustomer.getAnnotations().get("inverseProperty"));
        }
        
        @Test
        @DisplayName("Should handle many-to-many with join table")
        void testManyToManyWithJoinTable() {
            // Many-to-many is represented by a foreign key schema with a join table annotation
            ForeignKeySchema userRole = ForeignKeySchema.builder()
                    .constraintName("fk_user_role")
                    .sourceTable("user_roles")
                    .sourceColumn("user_id")
                    .referencedTable("users")
                    .referencedColumn("id")
                    .cardinality(RelationshipCardinality.MANY_TO_MANY)
                    .annotation("joinTable", "user_roles")
                    .annotation("inverseJoinColumn", "role_id")
                    .build();
            
            assertEquals(RelationshipCardinality.MANY_TO_MANY, userRole.getCardinality());
            assertEquals("user_roles", userRole.getAnnotations().get("joinTable"));
        }
    }
    
    @Nested
    @DisplayName("Structural Validation Scenarios")
    class StructuralValidationTests {
        
        @Test
        @DisplayName("Should validate entire table structure")
        void testValidateCompleteTable() {
            ColumnSchema column = ColumnSchema.builder()
                    .columnName("id")
                    .sqlType(Types.BIGINT)
                    .ordinalPosition(1)
                    .build();
            
            PrimaryKeySchema pk = PrimaryKeySchema.builder()
                    .columnName("id")
                    .build();
            
            TableSchema validTable = TableSchema.builder()
                    .tableName("test")
                    .column(column)
                    .primaryKey(pk)
                    .build();
            
            assertTrue(validTable.isStructurallyValid());
            
            // Invalid table - no table name
            TableSchema invalidTable = TableSchema.builder()
                    .column(column)
                    .build();
            
            assertFalse(invalidTable.isStructurallyValid());
        }
        
        @Test
        @DisplayName("Should validate foreign key referential integrity")
        void testForeignKeyValidation() {
            // Valid FK (same number of columns on both sides)
            ForeignKeySchema validFk = ForeignKeySchema.builder()
                    .sourceTable("orders")
                    .sourceColumn("customer_id")
                    .referencedTable("customers")
                    .referencedColumn("id")
                    .build();
            // All FK schemas are structurally valid if they have non‑empty source/referenced columns
            assertEquals(1, validFk.getSourceColumns().size());
            assertEquals(1, validFk.getReferencedColumns().size());
            
            // Composite FK with matching counts
            ForeignKeySchema compositeFk = ForeignKeySchema.builder()
                    .sourceTable("order_items")
                    .sourceColumn("order_id")
                    .sourceColumn("product_id")
                    .referencedTable("orders")
                    .referencedColumn("id")
                    .referencedColumn("product_id")
                    .build();
            assertEquals(2, compositeFk.getSourceColumns().size());
            assertEquals(2, compositeFk.getReferencedColumns().size());
            assertTrue(compositeFk.isComposite());
        }
    }
    
    @Nested
    @DisplayName("Qualified Name Tests")
    class QualifiedNameTests {
        
        @Test
        @DisplayName("Should generate qualified name correctly")
        void testQualifiedName() {
            TableSchema noCatalogNoSchema = TableSchema.builder()
                    .tableName("users")
                    .build();
            assertEquals("users", noCatalogNoSchema.getQualifiedName());
            
            TableSchema withSchema = TableSchema.builder()
                    .schemaName("public")
                    .tableName("users")
                    .build();
            assertEquals("public.users", withSchema.getQualifiedName());
            
            TableSchema withCatalogAndSchema = TableSchema.builder()
                    .catalogName("mydb")
                    .schemaName("public")
                    .tableName("users")
                    .build();
            assertEquals("mydb.public.users", withCatalogAndSchema.getQualifiedName());
        }
    }
    
    @Nested
    @DisplayName("Column Derived Methods Tests")
    class ColumnDerivedMethodsTests {
        
        @Test
        @DisplayName("Should correctly identify required column")
        void testRequiredColumn() {
            ColumnSchema required = ColumnSchema.builder()
                    .nullable(false)
                    .autoIncrement(false)
                    .defaultValue(null)
                    .build();
            assertTrue(required.isRequired());
            
            ColumnSchema notRequired = ColumnSchema.builder()
                    .nullable(true)
                    .build();
            assertFalse(notRequired.isRequired());
            
            ColumnSchema autoInc = ColumnSchema.builder()
                    .nullable(false)
                    .autoIncrement(true)
                    .build();
            assertFalse(autoInc.isRequired());
            
            ColumnSchema withDefault = ColumnSchema.builder()
                    .nullable(false)
                    .defaultValue("0")
                    .build();
            assertFalse(withDefault.isRequired());
        }
        
        @Test
        @DisplayName("Should identify text, numeric, temporal types")
        void testTypeIdentification() {
            ColumnSchema varchar = ColumnSchema.builder()
                    .sqlType(Types.VARCHAR)
                    .build();
            assertTrue(varchar.isTextType());
            
            ColumnSchema integer = ColumnSchema.builder()
                    .sqlType(Types.INTEGER)
                    .build();
            assertTrue(integer.isNumericType());
            
            ColumnSchema timestamp = ColumnSchema.builder()
                    .sqlType(Types.TIMESTAMP)
                    .build();
            assertTrue(timestamp.isTemporalType());
        }
    }
    
    @Nested
    @DisplayName("Find Methods Tests")
    class FindMethodsTests {
        
        @Test
        @DisplayName("Should find column case‑insensitively")
        void testFindColumnCaseInsensitive() {
            ColumnSchema col = ColumnSchema.builder()
                    .columnName("UserId")
                    .build();
            
            TableSchema table = TableSchema.builder()
                    .tableName("test")
                    .column(col)
                    .build();
            
            assertTrue(table.findColumn("userid").isPresent());
            assertTrue(table.findColumn("USERID").isPresent());
            assertTrue(table.findColumn("UserId").isPresent());
            assertFalse(table.findColumn("none").isPresent());
        }
        
        @Test
        @DisplayName("Should find foreign key by name case‑insensitively")
        void testFindForeignKeyCaseInsensitive() {
            ForeignKeySchema fk = ForeignKeySchema.builder()
                    .constraintName("FK_ORDER_CUSTOMER")
                    .sourceTable("orders")
                    .sourceColumn("customer_id")
                    .referencedTable("customers")
                    .referencedColumn("id")
                    .build();
            
            TableSchema table = TableSchema.builder()
                    .tableName("orders")
                    .foreignKey(fk)
                    .build();
            
            assertTrue(table.findForeignKey("fk_order_customer").isPresent());
            assertTrue(table.findForeignKey("FK_ORDER_CUSTOMER").isPresent());
            assertFalse(table.findForeignKey("none").isPresent());
        }
    }
}

