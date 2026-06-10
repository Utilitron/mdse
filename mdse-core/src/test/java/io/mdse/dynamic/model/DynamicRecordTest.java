package io.mdse.dynamic.model;

import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.PrimaryKeySchema;
import io.mdse.metadata.schema.TableSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicRecord Tests")
class DynamicRecordTest {

    private TableSchema tableSchema;
    private DynamicRecord record;
    
    @BeforeEach
    void setUp() {
        // Build immutable schema
        ColumnSchema idCol = ColumnSchema.builder()
                .columnName("id")
                .fieldName("id")
                .sqlType(Types.BIGINT)
                .javaTypeName(Long.class.getName())
                .nullable(false)
                .ordinalPosition(1)
                .build();

        ColumnSchema nameCol = ColumnSchema.builder()
                .columnName("name")
                .fieldName("name")
                .sqlType(Types.VARCHAR)
                .javaTypeName(String.class.getName())
                .maxLength(255)
                .nullable(false)
                .ordinalPosition(2)
                .build();

        ColumnSchema ageCol = ColumnSchema.builder()
                .columnName("age")
                .fieldName("age")
                .sqlType(Types.INTEGER)
                .javaTypeName(Integer.class.getName())
                .nullable(true)
                .ordinalPosition(3)
                .build();

        PrimaryKeySchema pk = PrimaryKeySchema.builder()
                .constraintName("users_pkey")
                .columnName("id")
                .build();

        tableSchema = TableSchema.builder()
                .schemaName("public")
                .tableName("users")
                .column(idCol)
                .column(nameCol)
                .column(ageCol)
                .primaryKey(pk)
                .build();

        // Create record with values
        Map<String, Object> values = new HashMap<>();
        values.put("id", 1L);
        values.put("name", "John Doe");
        values.put("age", 30);
        
        record = DynamicRecord.builder()
                .tableName("users")
                .schemaName("public")
                .values(values)
                .tableSchema(tableSchema)
                .build();
        
        record.resetChangeTracking();
    }
    
    @Test
    @DisplayName("Should create record with values")
    void testCreateRecord() {
        assertEquals("users", record.getTableName());
        assertEquals("public", record.getSchemaName());
        assertEquals(3, record.getValues().size());
    }
    
    @Test
    @DisplayName("Should get value by column name")
    void testGetValue() {
        assertEquals(1L, record.getValue("id"));
        assertEquals("John Doe", record.getValue("name"));
        assertEquals(30, record.getValue("age"));
        assertNull(record.getValue("nonexistent"));
    }
    
    @Test
    @DisplayName("Should get typed value")
    void testGetTypedValue() {
        Long id = record.getValue("id", Long.class);
        assertEquals(1L, id);
        
        String name = record.getValue("name", String.class);
        assertEquals("John Doe", name);
        
        Integer age = record.getValue("age", Integer.class);
        assertEquals(30, age);
    }
    
    @Test
    @DisplayName("Should throw exception for type mismatch")
    void testGetTypedValueMismatch() {
        assertThrows(ClassCastException.class, () -> 
                record.getValue("name", Integer.class));
    }
    
    @Test
    @DisplayName("Should get optional typed value")
    void testGetOptionalValue() {
        Optional<Long> id = record.getOptionalValue("id", Long.class);
        assertTrue(id.isPresent());
        assertEquals(1L, id.get());
        
        Optional<String> nonexistent = record.getOptionalValue("nonexistent", String.class);
        assertFalse(nonexistent.isPresent());
        
        Optional<Integer> typeMismatch = record.getOptionalValue("name", Integer.class);
        assertFalse(typeMismatch.isPresent());
    }
    
    @Test
    @DisplayName("Should set value and track change")
    void testSetValue() {
        assertFalse(record.isModified());
        
        record.setValue("name", "Jane Doe");
        
        assertTrue(record.isModified());
        assertEquals("Jane Doe", record.getValue("name"));
    }
    
    @Test
    @DisplayName("Should check if value exists")
    void testHasValue() {
        assertTrue(record.hasValue("id"));
        assertTrue(record.hasValue("name"));
        assertFalse(record.hasValue("nonexistent"));
    }
    
    @Test
    @DisplayName("Should remove value")
    void testRemoveValue() {
        Object removed = record.removeValue("age");
        
        assertEquals(30, removed);
        assertFalse(record.hasValue("age"));
        assertTrue(record.isModified());
    }
    
    @Test
    @DisplayName("Should get qualified table name")
    void testQualifiedTableName() {
        assertEquals("public.users", record.getQualifiedTableName());
        
        record.setSchemaName(null);
        assertEquals("users", record.getQualifiedTableName());
    }
    
    @Test
    @DisplayName("Should get single primary key value")
    void testGetSinglePrimaryKeyValue() {
        Object pk = record.getSinglePrimaryKeyValue();
        assertEquals(1L, pk);
    }
    
    @Test
    @DisplayName("Should set single primary key value")
    void testSetSinglePrimaryKeyValue() {
        record.setSinglePrimaryKeyValue(99L);
        assertEquals(99L, record.getValue("id"));
    }
    
    @Test
    @DisplayName("Should get composite primary key values")
    void testGetCompositePrimaryKeyValues() {
        // Create a composite PK table
        PrimaryKeySchema compositePk = PrimaryKeySchema.builder()
                .columnName("order_id")
                .columnName("item_id")
                .build();

        TableSchema orderItemsSchema = TableSchema.builder()
                .tableName("order_items")
                .primaryKey(compositePk)
                .build();

        DynamicRecord rec = DynamicRecord.builder()
                .tableName("order_items")
                .tableSchema(orderItemsSchema)
                .build();

        rec.setValue("order_id", 100);
        rec.setValue("item_id", 200);

        Map<String, Object> pkValues = rec.getPrimaryKeyValues();

        assertEquals(2, pkValues.size());
        assertEquals(100, pkValues.get("order_id"));
        assertEquals(200, pkValues.get("item_id"));
    }
    
    @Test
    @DisplayName("Should throw for single PK on composite key")
    void testSinglePkOnCompositeKey() {
        PrimaryKeySchema compositePk = PrimaryKeySchema.builder()
                .columnName("order_id")
                .columnName("item_id")
                .build();

        TableSchema orderItems = TableSchema.builder()
                .tableName("order_items")
                .primaryKey(compositePk)
                .build();

        DynamicRecord rec = DynamicRecord.builder()
                .tableSchema(orderItems)
                .build();

        assertThrows(IllegalStateException.class, rec::getSinglePrimaryKeyValue);
        assertThrows(IllegalStateException.class, () -> rec.setSinglePrimaryKeyValue(99));
    }
    
    @Test
    @DisplayName("Should track modifications correctly")
    void testChangeTracking() {
        assertFalse(record.isModified());

        record.setValue("name", "Jane Doe");
        
        assertTrue(record.isModified());
        
        Map<String, Object> modified = record.getModifiedValues();
        assertEquals(1, modified.size());
        assertTrue(modified.containsKey("name"));
        assertEquals("Jane Doe", modified.get("name"));
    }
    
    @Test
    @DisplayName("Should reset change tracking")
    void testResetChangeTracking() {
        record.setValue("name", "Jane Doe");
        assertTrue(record.isModified());
        
        record.resetChangeTracking();
        
        assertFalse(record.isModified());
        assertTrue(record.getModifiedValues().isEmpty());
    }
    
    @Test
    @DisplayName("Should discard changes")
    void testDiscardChanges() {
        String originalName = record.getValue("name", String.class);
        
        record.setValue("name", "Jane Doe");
        assertEquals("Jane Doe", record.getValue("name"));
        
        record.discardChanges();
        
        assertEquals(originalName, record.getValue("name"));
        assertFalse(record.isModified());
    }
    
    @Test
    @DisplayName("Should set multiple values at once")
    void testSetValues() {
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", "Jane Smith");
        newValues.put("age", 25);
        
        record.setValues(newValues);
        
        assertEquals("Jane Smith", record.getValue("name"));
        assertEquals(25, record.getValue("age"));
        assertTrue(record.isModified());
    }
    
    @Test
    @DisplayName("Should get all column names")
    void testGetColumnNames() {
        Set<String> columnNames = record.getColumnNames();
        
        assertEquals(3, columnNames.size());
        assertTrue(columnNames.contains("id"));
        assertTrue(columnNames.contains("name"));
        assertTrue(columnNames.contains("age"));
    }
    
    @Test
    @DisplayName("Should get copy of all values")
    void testGetAllValues() {
        Map<String, Object> allValues = record.getAllValues();
        
        assertEquals(3, allValues.size());
        
        // Verify it's a copy
        allValues.put("new_column", "value");
        assertFalse(record.hasValue("new_column"));
    }
    
    @Test
    @DisplayName("Should clear all values")
    void testClear() {
        record.clear();
        
        assertTrue(record.getValues().isEmpty());
        assertTrue(record.isModified());
    }
    
    @Test
    @DisplayName("Should have schema reference")
    void testHasSchema() {
        assertTrue(record.hasSchema());

        DynamicRecord noSchema = DynamicRecord.builder().build();
        assertFalse(noSchema.hasSchema());
    }
    
    @Test
    @DisplayName("Should produce meaningful toString")
    void testToString() {
        String str = record.toString();
        assertNotNull(str);
        assertTrue(str.contains("public.users"));
        assertTrue(str.contains("modified=false"));
    }
}

