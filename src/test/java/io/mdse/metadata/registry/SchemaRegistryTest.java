package io.mdse.metadata.registry;

import io.mdse.metadata.exception.MetadataNotFoundException;
import io.mdse.metadata.schema.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SchemaRegistry.
 */
@DisplayName("SchemaRegistry Tests")
class SchemaRegistryTest {
    
    private SchemaRegistry registry;
    private TableSchema usersSchema;
    private TableSchema ordersSchema;
    private TableSchema productsSchema;
    
    @BeforeEach
    void setUp() {
        registry = new SchemaRegistry();
        setupTestSchemas();
    }
    
    private void setupTestSchemas() {
        // Minimal valid column for each table
        ColumnSchema idColumn = ColumnSchema.builder()
                .columnName("id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(1)
                .build();
        
        // Users table
        usersSchema = TableSchema.builder()
                .id(UUID.randomUUID())
                .schemaName("public")
                .tableName("users")
                .tableType(TableType.TABLE)
                .column(idColumn)
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .build();
        
        // Orders table with FK to users
        ColumnSchema orderIdCol = ColumnSchema.builder()
                .columnName("id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(1)
                .build();
        ColumnSchema userIdCol = ColumnSchema.builder()
                .columnName("user_id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(2)
                .build();
        
        ForeignKeySchema orderUserFk = ForeignKeySchema.builder()
                .constraintName("fk_order_user")
                .sourceTable("orders")
                .sourceColumn("user_id")
                .referencedTable("users")
                .referencedColumn("id")
                .build();
        
        ordersSchema = TableSchema.builder()
                .id(UUID.randomUUID())
                .schemaName("public")
                .tableName("orders")
                .tableType(TableType.TABLE)
                .column(orderIdCol)
                .column(userIdCol)
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .foreignKey(orderUserFk)
                .build();
        
        // Products table
        productsSchema = TableSchema.builder()
                .id(UUID.randomUUID())
                .schemaName("public")
                .tableName("products")
                .tableType(TableType.TABLE)
                .column(idColumn)
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .build();
    }
    
    @Test
    @DisplayName("Should register and retrieve schema by table name")
    void testRegisterAndGet() {
        registry.register(usersSchema);
        
        Optional<TableSchema> found = registry.get("users");
        assertTrue(found.isPresent());
        assertEquals("users", found.get().getTableName());
    }
    
    @Test
    @DisplayName("Should retrieve schema by qualified name")
    void testGetByQualifiedName() {
        registry.register(usersSchema);
        
        Optional<TableSchema> found = registry.get("public.users");
        assertTrue(found.isPresent());
        assertEquals("users", found.get().getTableName());
    }
    
    @Test
    @DisplayName("Should retrieve schema case‑insensitively")
    void testGetCaseInsensitive() {
        registry.register(usersSchema);
        
        Optional<TableSchema> found = registry.get("USERS");
        assertTrue(found.isPresent());
        assertEquals("users", found.get().getTableName());
        
        found = registry.get("PUBLIC.USERS");
        assertTrue(found.isPresent());
    }
    
    @Test
    @DisplayName("Should return empty for non‑existent schema")
    void testGetNonExistent() {
        Optional<TableSchema> found = registry.get("nonexistent");
        assertFalse(found.isPresent());
    }
    
    @Test
    @DisplayName("Should get required schema or throw")
    void testGetRequired() {
        registry.register(usersSchema);
        
        TableSchema schema = registry.getRequired("users");
        assertNotNull(schema);
        assertEquals("users", schema.getTableName());
        
        assertThrows(MetadataNotFoundException.class, () ->
                registry.getRequired("nonexistent"));
    }
    
    @Test
    @DisplayName("Should register multiple schemas")
    void testRegisterMultiple() {
        registry.register(usersSchema);
        registry.register(ordersSchema);
        registry.register(productsSchema);
        
        assertEquals(3, registry.getAll().size());
        assertTrue(registry.get("users").isPresent());
        assertTrue(registry.get("orders").isPresent());
        assertTrue(registry.get("products").isPresent());
    }
    
    @Test
    @DisplayName("Should overwrite schema when registering with same name")
    void testRegisterOverwrite() {
        registry.register(usersSchema);
        
        TableSchema updated = usersSchema.toBuilder()
                .description("Updated description")
                .build();
        registry.register(updated);
        
        TableSchema retrieved = registry.getRequired("users");
        assertEquals("Updated description", retrieved.getDescription());
    }
    
    @Test
    @DisplayName("Should unregister schema")
    void testUnregister() {
        registry.register(usersSchema);
        assertTrue(registry.get("users").isPresent());
        
        registry.unregister("users");
        assertFalse(registry.get("users").isPresent());
    }
    
    @Test
    @DisplayName("Should unregister by qualified name")
    void testUnregisterByQualifiedName() {
        registry.register(usersSchema);
        assertTrue(registry.get("public.users").isPresent());
        
        registry.unregister("public.users");
        assertFalse(registry.get("public.users").isPresent());
    }
    
    @Test
    @DisplayName("Should clear all schemas")
    void testClear() {
        registry.register(usersSchema);
        registry.register(ordersSchema);
        assertEquals(2, registry.getAll().size());
        
        registry.clear();
        assertEquals(0, registry.getAll().size());
        assertTrue(registry.getAll().isEmpty());
    }
    
    @Test
    @DisplayName("Should get all schemas as unmodifiable collection")
    void testGetAll() {
        registry.register(usersSchema);
        registry.register(ordersSchema);
        
        Collection<TableSchema> all = registry.getAll();
        assertEquals(2, all.size());
        
        // Verify immutability (should throw)
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
    }
    
    @Test
    @DisplayName("Should handle concurrent registrations")
    void testConcurrentRegistrations() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    TableSchema schema = TableSchema.builder()
                            .tableName("concurrent_" + idx)
                            .column(ColumnSchema.builder()
                                    .columnName("id")
                                    .sqlType(Types.BIGINT)
                                    .ordinalPosition(1)
                                    .build())
                            .build();
                    registry.register(schema);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        assertEquals(threadCount, successCount.get());
        assertEquals(threadCount, registry.getAll().size());
    }
}

