package io.mdse.metadata.introspection;

import io.mdse.metadata.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcSchemaIntrospector Tests")
class JdbcSchemaIntrospectorTest {
    
    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private DatabaseMetaData metaData;
    
    private JdbcSchemaIntrospector introspector;
    
    // ------------------------------------------------------------------------
    // Metadata record definitions
    // ------------------------------------------------------------------------
    
    private record ColumnDef(
            String name,
            int sqlType,
            int ordinalPosition,
            boolean nullable,
            String defaultValue,
            boolean autoIncrement,
            Integer columnSize,      // for VARCHAR precision
            Integer decimalDigits,   // for numeric scale
            String typeName,         // database type name (e.g., "int8", "varchar")
            String remarks,
            boolean generated
    ) {
        ColumnDef(String name, int sqlType, int ordinalPosition, boolean nullable, String defaultValue, boolean autoIncrement) {
            this(name, sqlType, ordinalPosition, nullable, defaultValue, autoIncrement, null, null, null, null, false);
        }
    }
    
    private record PrimaryKeyDef(String constraintName, List<String> columns) {
        PrimaryKeyDef(String... columns) {
            this("pk", List.of(columns));
        }
    }
    
    private record ForeignKeyDef(
            String name,
            String sourceColumn,
            String referencedTable,
            String referencedColumn,
            short deleteRule,
            short updateRule
    ) {}
    
    private record IndexDef(
            String name,
            boolean nonUnique,
            short type,
            String columnName,
            int ordinalPosition
    ) {}
    
    private record UniqueConstraintDef(
            String name,
            String columnName,
            int ordinalPosition
    ) {}
    
    // ------------------------------------------------------------------------
    // Reusable mockRows helper (centralises cursor logic)
    // ------------------------------------------------------------------------
    
    private AtomicInteger mockRows(ResultSet rs, List<Map<String, Object>> rows) throws Exception {
        AtomicInteger rowIndex = new AtomicInteger(-1);
        when(rs.next()).thenAnswer(inv -> {
            if (rowIndex.get() + 1 < rows.size()) {
                rowIndex.incrementAndGet();
                return true;
            }
            return false;
        });
        return rowIndex;
    }
    
    // ------------------------------------------------------------------------
    // Mock builders using records
    // ------------------------------------------------------------------------
    
    private void mockColumns(String schema, String table, ColumnDef... columnDefs) throws Exception {
        ResultSet columnsRs = mock(ResultSet.class);
        when(metaData.getColumns("testdb", schema, table, "%")).thenReturn(columnsRs);
        
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ColumnDef def : columnDefs) {
            Map<String, Object> row = new HashMap<>();
            row.put("COLUMN_NAME", def.name);
            row.put("DATA_TYPE", def.sqlType);
            row.put("ORDINAL_POSITION", def.ordinalPosition);
            row.put("NULLABLE", def.nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls);
            row.put("COLUMN_DEF", def.defaultValue);
            row.put("IS_AUTOINCREMENT", def.autoIncrement ? "YES" : "NO");
            row.put("COLUMN_SIZE", def.columnSize != null ? def.columnSize : 255);
            row.put("DECIMAL_DIGITS", def.decimalDigits != null ? def.decimalDigits : 0);
            row.put("TYPE_NAME", def.typeName != null ? def.typeName : "dummy");
            row.put("REMARKS", def.remarks);
            row.put("IS_GENERATEDCOLUMN", def.generated ? "YES" : "NO");
            rows.add(row);
        }
        
        AtomicInteger rowIndex = mockRows(columnsRs, rows);
        
        when(columnsRs.getString("COLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("COLUMN_NAME"));
        when(columnsRs.getInt("DATA_TYPE")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("DATA_TYPE"));
        when(columnsRs.getInt("ORDINAL_POSITION")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("ORDINAL_POSITION"));
        when(columnsRs.getInt("NULLABLE")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("NULLABLE"));
        when(columnsRs.getString("COLUMN_DEF")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("COLUMN_DEF"));
        when(columnsRs.getString("IS_AUTOINCREMENT")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("IS_AUTOINCREMENT"));
        when(columnsRs.getInt("COLUMN_SIZE")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("COLUMN_SIZE"));
        when(columnsRs.getInt("DECIMAL_DIGITS")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("DECIMAL_DIGITS"));
        when(columnsRs.getString("TYPE_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("TYPE_NAME"));
        when(columnsRs.getString("REMARKS")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("REMARKS"));
        when(columnsRs.getString("IS_GENERATEDCOLUMN")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("IS_GENERATEDCOLUMN"));
    }
    
    private void mockPrimaryKey(String schema, String table, PrimaryKeyDef pkDef) throws Exception {
        ResultSet pkRs = mock(ResultSet.class);
        when(metaData.getPrimaryKeys("testdb", schema, table)).thenReturn(pkRs);
        
        if (pkDef.columns().isEmpty()) {
            when(pkRs.next()).thenReturn(false);
            return;
        }
        
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < pkDef.columns().size(); i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("COLUMN_NAME", pkDef.columns().get(i));
            row.put("KEY_SEQ", i + 1);
            rows.add(row);
        }
        
        AtomicInteger rowIndex = mockRows(pkRs, rows);
        when(pkRs.getString("COLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("COLUMN_NAME"));
        when(pkRs.getInt("KEY_SEQ")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("KEY_SEQ"));
        when(pkRs.getString("PK_NAME")).thenReturn(pkDef.constraintName());
    }
    
    private void mockForeignKeys(String schema, String table, List<ForeignKeyDef> fkDefs) throws Exception {
        ResultSet fkRs = mock(ResultSet.class);
        when(metaData.getImportedKeys("testdb", schema, table)).thenReturn(fkRs);
        
        if (fkDefs.isEmpty()) {
            when(fkRs.next()).thenReturn(false);
            return;
        }
        
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ForeignKeyDef def : fkDefs) {
            Map<String, Object> row = new HashMap<>();
            row.put("FK_NAME", def.name);
            row.put("FKCOLUMN_NAME", def.sourceColumn);
            row.put("PKTABLE_NAME", def.referencedTable);
            row.put("PKCOLUMN_NAME", def.referencedColumn);
            row.put("DELETE_RULE", def.deleteRule);
            row.put("UPDATE_RULE", def.updateRule);
            rows.add(row);
        }
        
        AtomicInteger rowIndex = mockRows(fkRs, rows);
        when(fkRs.getString("FK_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("FK_NAME"));
        when(fkRs.getString("FKCOLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("FKCOLUMN_NAME"));
        when(fkRs.getString("PKTABLE_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("PKTABLE_NAME"));
        when(fkRs.getString("PKCOLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("PKCOLUMN_NAME"));
        when(fkRs.getShort("DELETE_RULE")).thenAnswer(inv -> (short) rows.get(rowIndex.get()).get("DELETE_RULE"));
        when(fkRs.getShort("UPDATE_RULE")).thenAnswer(inv -> (short) rows.get(rowIndex.get()).get("UPDATE_RULE"));
        when(fkRs.getString("FKTABLE_SCHEM")).thenReturn(schema);
        when(fkRs.getString("PKTABLE_SCHEM")).thenReturn(schema);
        when(fkRs.getString("FKTABLE_NAME")).thenReturn(table);
    }
    
    private void mockIndexes(String schema, String table, List<IndexDef> indexDefs) throws Exception {
        ResultSet idxRs = mock(ResultSet.class);
        when(metaData.getIndexInfo("testdb", schema, table, false, false)).thenReturn(idxRs);
        
        if (indexDefs.isEmpty()) {
            when(idxRs.next()).thenReturn(false);
            return;
        }
        
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IndexDef def : indexDefs) {
            Map<String, Object> row = new HashMap<>();
            row.put("INDEX_NAME", def.name);
            row.put("NON_UNIQUE", def.nonUnique);
            row.put("TYPE", def.type);
            row.put("COLUMN_NAME", def.columnName);
            row.put("ORDINAL_POSITION", def.ordinalPosition);
            rows.add(row);
        }
        
        AtomicInteger rowIndex = mockRows(idxRs, rows);
        when(idxRs.getString("INDEX_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("INDEX_NAME"));
        when(idxRs.getBoolean("NON_UNIQUE")).thenAnswer(inv -> (boolean) rows.get(rowIndex.get()).get("NON_UNIQUE"));
        when(idxRs.getShort("TYPE")).thenAnswer(inv -> (short) rows.get(rowIndex.get()).get("TYPE"));
        when(idxRs.getString("COLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("COLUMN_NAME"));
        when(idxRs.getInt("ORDINAL_POSITION")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("ORDINAL_POSITION"));
    }
    
    private void mockUniqueConstraints(String schema, String table, List<UniqueConstraintDef> ucDefs) throws Exception {
        ResultSet ucRs = mock(ResultSet.class);
        when(metaData.getIndexInfo("testdb", schema, table, true, false)).thenReturn(ucRs);
        
        if (ucDefs.isEmpty()) {
            when(ucRs.next()).thenReturn(false);
            return;
        }
        
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UniqueConstraintDef def : ucDefs) {
            Map<String, Object> row = new HashMap<>();
            row.put("INDEX_NAME", def.name);
            row.put("COLUMN_NAME", def.columnName);
            row.put("ORDINAL_POSITION", def.ordinalPosition);
            rows.add(row);
        }
        
        AtomicInteger rowIndex = mockRows(ucRs, rows);
        when(ucRs.getString("INDEX_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("INDEX_NAME"));
        when(ucRs.getString("COLUMN_NAME")).thenAnswer(inv -> (String) rows.get(rowIndex.get()).get("COLUMN_NAME"));
        when(ucRs.getInt("ORDINAL_POSITION")).thenAnswer(inv -> (int) rows.get(rowIndex.get()).get("ORDINAL_POSITION"));
        when(ucRs.getBoolean("NON_UNIQUE")).thenReturn(false);
    }
    
    private void mockTableExists(String schema, String table, boolean exists) throws Exception {
        ResultSet tableRs = mock(ResultSet.class);
        when(metaData.getTables(eq("testdb"), eq(schema), eq(table), isNull())).thenReturn(tableRs);
        when(tableRs.next()).thenReturn(exists);
        if (exists) {
            when(tableRs.getString("TABLE_TYPE")).thenReturn("TABLE");
            when(tableRs.getString("REMARKS")).thenReturn(null);
        }
    }
    
    // ------------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------------
    
    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(connection.getSchema()).thenReturn("public");
        introspector = new JdbcSchemaIntrospector(dataSource);
    }
    
    // ============================================================
    // Test classes
    // ============================================================
    
    @Nested
    @DisplayName("Table discovery tests")
    class TableDiscoveryTests {
        
        @Test
        @DisplayName("Should introspect all tables in default schema")
        void testIntrospectAll() throws Exception {
            String schema = "public";
            ResultSet tablesRs = mock(ResultSet.class);
            when(metaData.getTables(eq("testdb"), eq(schema), eq("%"), any(String[].class)))
                    .thenReturn(tablesRs);
            when(tablesRs.next()).thenReturn(true, true, false);
            when(tablesRs.getString("TABLE_NAME")).thenReturn("users", "orders");
            when(tablesRs.getString("TABLE_SCHEM")).thenReturn(schema, schema);
            
            JdbcSchemaIntrospector spy = spy(introspector);
            TableSchema usersSchema = mock(TableSchema.class);
            TableSchema ordersSchema = mock(TableSchema.class);
            doReturn(Optional.of(usersSchema)).when(spy).introspectTable(schema, "users");
            doReturn(Optional.of(ordersSchema)).when(spy).introspectTable(schema, "orders");
            
            Collection<TableSchema> tables = spy.introspectAll();
            assertThat(tables).hasSize(2).containsExactly(usersSchema, ordersSchema);
        }
        
        @Test
        @DisplayName("Should introspect specific schema")
        void testIntrospectSchema() throws Exception {
            String schema = "sales";
            ResultSet tablesRs = mock(ResultSet.class);
            when(metaData.getTables(eq("testdb"), eq(schema), eq("%"), any(String[].class)))
                    .thenReturn(tablesRs);
            when(tablesRs.next()).thenReturn(true, false);
            when(tablesRs.getString("TABLE_NAME")).thenReturn("customers");
            when(tablesRs.getString("TABLE_SCHEM")).thenReturn(schema);
            
            JdbcSchemaIntrospector spy = spy(introspector);
            TableSchema customersSchema = mock(TableSchema.class);
            doReturn(Optional.of(customersSchema)).when(spy).introspectTable(schema, "customers");
            
            Collection<TableSchema> tables = spy.introspectSchema(schema);
            assertThat(tables).hasSize(1).containsExactly(customersSchema);
        }
        
        @Test
        @DisplayName("Should return empty when table not found")
        void testIntrospectTableNotFound() throws Exception {
            mockTableExists("public", "unknown", false);
            Optional<TableSchema> result = introspector.introspectTable("unknown");
            assertThat(result).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("Column introspection tests")
    class ColumnIntrospectionTests {
        
        @Test
        @DisplayName("Should build column schema with all attributes")
        void testBuildColumnSchema() throws Exception {
            mockTableExists("public", "users", true);
            mockColumns("public", "users",
                    new ColumnDef("id", Types.BIGINT, 1, false, null, true,
                            19, 0, "int8", "Primary key", false));
            mockPrimaryKey("public", "users", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "users", List.of());
            mockIndexes("public", "users", List.of());
            mockUniqueConstraints("public", "users", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("users");
            assertThat(result).isPresent();
            TableSchema schema = result.get();
            assertThat(schema.getTableName()).isEqualTo("users");
            assertThat(schema.getColumns()).hasSize(1);
            ColumnSchema col = schema.getColumns().get(0);
            assertThat(col.getColumnName()).isEqualTo("id");
            assertThat(col.getSqlType()).isEqualTo(Types.BIGINT);
            assertThat(col.isAutoIncrement()).isTrue();
            assertThat(col.isNullable()).isFalse();
            assertThat(col.getDescription()).isEqualTo("Primary key");
            assertThat(col.getDbTypeName()).isEqualTo("int8");
            assertThat(col.getColumnSize()).isEqualTo(19);
            assertThat(col.getScale()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should recognise generated column")
        void testGeneratedColumn() throws Exception {
            mockTableExists("public", "users", true);
            mockColumns("public", "users",
                    new ColumnDef("full_name", Types.VARCHAR, 2, false, null, false,
                            255, null, "varchar", null, true));
            mockPrimaryKey("public", "users", new PrimaryKeyDef()); // no PK
            mockForeignKeys("public", "users", List.of());
            mockIndexes("public", "users", List.of());
            mockUniqueConstraints("public", "users", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("users");
            assertThat(result).isPresent();
            ColumnSchema col = result.get().getColumns().get(0);
            assertThat(col.isGenerated()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Primary key tests")
    class PrimaryKeyTests {
        
        @Test
        @DisplayName("Should load simple primary key")
        void testLoadSimplePrimaryKey() throws Exception {
            mockTableExists("public", "products", true);
            mockColumns("public", "products",
                    new ColumnDef("id", Types.BIGINT, 1, false, null, false),
                    new ColumnDef("sku", Types.VARCHAR, 2, false, null, false));
            mockPrimaryKey("public", "products", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "products", List.of());
            mockIndexes("public", "products", List.of());
            mockUniqueConstraints("public", "products", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("products");
            assertThat(result).isPresent();
            TableSchema schema = result.get();
            assertThat(schema.hasPrimaryKey()).isTrue();
            assertThat(schema.getPrimaryKey().getColumnNames()).containsExactly("id");
        }
        
        @Test
        @DisplayName("Should load composite primary key respecting KEY_SEQ")
        void testLoadCompositePrimaryKey() throws Exception {
            mockTableExists("public", "order_items", true);
            mockColumns("public", "order_items",
                    new ColumnDef("order_id", Types.BIGINT, 1, false, null, false),
                    new ColumnDef("item_id", Types.BIGINT, 2, false, null, false),
                    new ColumnDef("quantity", Types.INTEGER, 3, true, null, false));
            mockPrimaryKey("public", "order_items", new PrimaryKeyDef("order_id", "item_id"));
            mockForeignKeys("public", "order_items", List.of());
            mockIndexes("public", "order_items", List.of());
            mockUniqueConstraints("public", "order_items", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("order_items");
            assertThat(result).isPresent();
            TableSchema schema = result.get();
            assertThat(schema.hasPrimaryKey()).isTrue();
            assertThat(schema.hasCompositePrimaryKey()).isTrue();
            assertThat(schema.getPrimaryKey().getColumnNames()).containsExactly("order_id", "item_id");
        }
    }
    
    @Nested
    @DisplayName("Foreign key tests")
    class ForeignKeyTests {
        
        @Test
        @DisplayName("Should load foreign keys")
        void testLoadForeignKeys() throws Exception {
            mockTableExists("public", "orders", true);
            mockColumns("public", "orders",
                    new ColumnDef("id", Types.BIGINT, 1, false, null, false),
                    new ColumnDef("customer_id", Types.BIGINT, 2, true, null, false));
            mockPrimaryKey("public", "orders", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "orders", List.of(
                    new ForeignKeyDef("fk_order_customer", "customer_id", "customers", "id",
                            (short) DatabaseMetaData.importedKeyRestrict, (short) DatabaseMetaData.importedKeyCascade)
            ));
            mockIndexes("public", "orders", List.of());
            mockUniqueConstraints("public", "orders", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("orders");
            assertThat(result).isPresent();
            TableSchema schema = result.get();
            assertThat(schema.getForeignKeys()).hasSize(1);
            ForeignKeySchema fk = schema.getForeignKeys().get(0);
            assertThat(fk.getConstraintName()).isEqualTo("fk_order_customer");
            assertThat(fk.getSourceColumns()).containsExactly("customer_id");
            assertThat(fk.getReferencedTable()).isEqualTo("customers");
            assertThat(fk.getReferencedColumns()).containsExactly("id");
            assertThat(fk.getDeleteRule()).isEqualTo(ReferentialAction.RESTRICT);
            assertThat(fk.getUpdateRule()).isEqualTo(ReferentialAction.CASCADE);
        }
    }
    
    @Nested
    @DisplayName("Index tests")
    class IndexTests {
        
        @Test
        @DisplayName("Should load indexes with correct column ordering (by ORDINAL_POSITION)")
        void testLoadIndexesWithOrdering() throws Exception {
            mockTableExists("public", "users", true);
            mockColumns("public", "users",
                    new ColumnDef("id", Types.BIGINT, 1, false, null, false),
                    new ColumnDef("b", Types.VARCHAR, 2, false, null, false),
                    new ColumnDef("a", Types.VARCHAR, 3, false, null, false));
            mockPrimaryKey("public", "users", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "users", List.of());
            
            // Feed index rows in non‑ordinal order (b then a)
            List<IndexDef> indexDefs = List.of(
                    new IndexDef("idx_test", false, DatabaseMetaData.tableIndexClustered, "b", 2),
                    new IndexDef("idx_test", false, DatabaseMetaData.tableIndexClustered, "a", 1)
            );
            mockIndexes("public", "users", indexDefs);
            mockUniqueConstraints("public", "users", List.of());
            
            Optional<TableSchema> result = introspector.introspectTable("users");
            assertThat(result).isPresent();
            TableSchema schema = result.get();
            assertThat(schema.getIndexes()).hasSize(1);
            IndexSchema idx = schema.getIndexes().get(0);
            // Expect columns sorted by ORDINAL_POSITION: "a", "b"
            assertThat(idx.getColumnNames()).containsExactly("a", "b");
        }
    }
    
    @Nested
    @DisplayName("Configuration tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should skip index loading when configured")
        void testSkipIndexes() throws Exception {
            IntrospectionConfig config = IntrospectionConfig.builder()
                    .loadIndexes(false)
                    .loadForeignKeys(true)
                    .loadUniqueConstraints(true)
                    .build();
            
            JdbcSchemaIntrospector customIntrospector = new JdbcSchemaIntrospector(dataSource, config);
            
            mockTableExists("public", "users", true);
            mockColumns("public", "users",
                    new ColumnDef("id", Types.BIGINT, 1, false, null, false));
            mockPrimaryKey("public", "users", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "users", List.of());
            mockUniqueConstraints("public", "users", List.of());  // still loaded because loadUniqueConstraints=true
            
            Optional<TableSchema> result = customIntrospector.introspectTable("users");
            assertThat(result).isPresent();
            
            verify(metaData, never()).getIndexInfo(eq("testdb"), eq("public"), eq("users"), eq(false), anyBoolean());
        }
        
        @Test
        @DisplayName("Should skip row count estimation when disabled")
        void testSkipRowCount() throws Exception {
            IntrospectionConfig config = IntrospectionConfig.builder()
                    .estimateRowCounts(false)
                    .build();
            
            JdbcSchemaIntrospector customIntrospector = new JdbcSchemaIntrospector(dataSource, config);
            
            mockTableExists("public", "users", true);
            mockColumns("public", "users", new ColumnDef("id", Types.BIGINT, 1, false, null, false));
            mockPrimaryKey("public", "users", new PrimaryKeyDef("id"));
            mockForeignKeys("public", "users", List.of());
            mockIndexes("public", "users", List.of());
            mockUniqueConstraints("public", "users", List.of());
            
            customIntrospector.introspectTable("users");
            
            // The production code should never call connection.createStatement() or prepareStatement for COUNT(*)
            verify(connection, never()).createStatement();
            verify(connection, never()).prepareStatement(anyString());
        }
    }
}
