package io.mdse.dynamic.repository;

import io.mdse.dynamic.exception.RecordNotFoundException;
import io.mdse.dynamic.exception.RepositoryException;
import io.mdse.dynamic.model.DynamicRecord;
import io.mdse.metadata.exception.ValidationException;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.*;
import io.mdse.metadata.service.impl.StandardCoercionService;
import io.mdse.metadata.service.impl.StandardFormatterService;
import io.mdse.metadata.service.impl.StandardValidationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicRepository Integration Tests")
class DynamicRepositoryTest {
    
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private Statement statement;
    @Mock
    private ResultSet resultSet;
    @Mock
    private ResultSet generatedKeys;
    
    private DynamicRepository repository;
    private SchemaRegistry schemaRegistry;
    
    private static final String CUSTOMERS = "customers";
    private static final String PRODUCTS = "products";
    private static final String ORDERS = "orders";
    private static final String ORDER_ITEMS = "order_items";
    private static final String NO_PK = "no_pk_table";
    
    @BeforeEach
    void setUp() throws Exception {
        schemaRegistry = new SchemaRegistry();
        
        registerCustomers();
        registerProducts();
        registerOrders();
        registerOrderItems();
        registerNoPkTable();
        
        repository = new DynamicRepository(
                dataSource,
                schemaRegistry,
                new StandardCoercionService(),
                new StandardValidationService(),
                new StandardFormatterService()
        );
        
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(connection.prepareStatement(anyString(), anyInt())).thenReturn(preparedStatement);
        lenient().when(connection.createStatement()).thenReturn(statement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        lenient().when(statement.executeQuery(anyString())).thenReturn(resultSet);
        lenient().when(preparedStatement.getGeneratedKeys()).thenReturn(generatedKeys);
        lenient().when(generatedKeys.next()).thenReturn(false);
    }
    
    private void registerCustomers() {
        TableSchema schema = TableSchema.builder()
                .tableName(CUSTOMERS)
                .schemaName("PUBLIC")
                .primaryKey(PrimaryKeySchema.builder()
                        .constraintName("pk_customers")
                        .columnName("ID")
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("NAME")
                        .javaTypeName("java.lang.String")
                        .sqlType(Types.VARCHAR)
                        .nullable(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("EMAIL")
                        .javaTypeName("java.lang.String")
                        .sqlType(Types.VARCHAR)
                        .nullable(false)
                        .build())
                .build();
        schemaRegistry.register(schema);
    }
    
    private void registerProducts() {
        TableSchema schema = TableSchema.builder()
                .tableName(PRODUCTS)
                .schemaName("PUBLIC")
                .primaryKey(PrimaryKeySchema.builder()
                        .constraintName("pk_products")
                        .columnName("ID")
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("SKU")
                        .javaTypeName("java.lang.String")
                        .sqlType(Types.VARCHAR)
                        .nullable(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("NAME")
                        .javaTypeName("java.lang.String")
                        .sqlType(Types.VARCHAR)
                        .nullable(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("PRICE")
                        .javaTypeName("java.math.BigDecimal")
                        .sqlType(Types.DECIMAL)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ACTIVE")
                        .javaTypeName("java.lang.Boolean")
                        .sqlType(Types.BOOLEAN)
                        .nullable(true)
                        .build())
                .build();
        schemaRegistry.register(schema);
    }
    
    private void registerOrders() {
        TableSchema schema = TableSchema.builder()
                .tableName(ORDERS)
                .schemaName("PUBLIC")
                .primaryKey(PrimaryKeySchema.builder()
                        .constraintName("pk_orders")
                        .columnName("ID")
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("CUSTOMER_ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .nullable(false)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ORDER_DATE")
                        .javaTypeName("java.time.LocalDateTime")
                        .sqlType(Types.TIMESTAMP)
                        .build())
                .build();
        schemaRegistry.register(schema);
    }
    
    private void registerOrderItems() {
        TableSchema schema = TableSchema.builder()
                .tableName(ORDER_ITEMS)
                .schemaName("PUBLIC")
                .primaryKey(PrimaryKeySchema.builder()
                        .constraintName("pk_order_items")
                        .columnName("ORDER_ID")
                        .columnName("PRODUCT_ID")
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("ORDER_ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("PRODUCT_ID")
                        .javaTypeName("java.lang.Long")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("QUANTITY")
                        .javaTypeName("java.lang.Integer")
                        .sqlType(Types.INTEGER)
                        .build())
                .build();
        schemaRegistry.register(schema);
    }
    
    private void registerNoPkTable() {
        TableSchema schema = TableSchema.builder()
                .tableName(NO_PK)
                .schemaName("PUBLIC")
                .column(ColumnSchema.builder()
                        .columnName("DUMMY")
                        .javaTypeName("java.lang.String")
                        .sqlType(Types.VARCHAR)
                        .build())
                .build();
        schemaRegistry.register(schema);
    }
    
    @Nested
    @DisplayName("Find by id tests")
    class FindById {
        
        @Test
        @DisplayName("returns record when present")
        void shouldReturnRecordWhenFound() throws Exception {
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(1L);
            when(resultSet.getObject("NAME")).thenReturn("John Doe");
            when(resultSet.getObject("EMAIL")).thenReturn("john@example.com");
            
            Optional<DynamicRecord> result = repository.findById(CUSTOMERS, 1L);
            assertThat(result).isPresent();
            DynamicRecord record = result.get();
            assertThat(record.getValue("ID")).isEqualTo(1L);
            assertThat(record.getValue("NAME")).isEqualTo("John Doe");
            assertThat(record.getValue("EMAIL")).isEqualTo("john@example.com");
        }
        
        @Test
        @DisplayName("returns empty when not found")
        void shouldReturnEmptyWhenNotFound() throws Exception {
            when(resultSet.next()).thenReturn(false);
            Optional<DynamicRecord> result = repository.findById(CUSTOMERS, 999L);
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("throws for table without PK")
        void shouldThrowWhenTableHasNoPrimaryKey() {
            assertThatThrownBy(() -> repository.findById(NO_PK, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no primary key");
        }
        
        @Test
        @DisplayName("throws for composite-key table")
        void shouldThrowForCompositeKeyTable() {
            assertThatThrownBy(() -> repository.findById(ORDER_ITEMS, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("composite");
        }

        @Test
        @DisplayName("finds by composite key")
        void shouldFindByCompositeKey() throws Exception {
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ORDER_ID")).thenReturn(1L);
            when(resultSet.getObject("PRODUCT_ID")).thenReturn(1L);
            when(resultSet.getObject("QUANTITY")).thenReturn(2);
            
            Map<String, Object> pk = Map.of("ORDER_ID", 1L, "PRODUCT_ID", 1L);
            Optional<DynamicRecord> result = repository.findById(ORDER_ITEMS, pk);
            assertThat(result).isPresent();
            DynamicRecord record = result.get();
            assertThat(record.getValue("ORDER_ID")).isEqualTo(1L);
            assertThat(record.getValue("PRODUCT_ID")).isEqualTo(1L);
            assertThat(record.getValue("QUANTITY")).isEqualTo(2);
        }
        
        @Test
        @DisplayName("?????????")
        void shouldThrowWhenMissingCompositeKeyValue() {
            Map<String, Object> incompletePk = Map.of("ORDER_ID", 1L);
            assertThatThrownBy(() -> repository.findById(ORDER_ITEMS, incompletePk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing primary key value for column: PRODUCT_ID");
        }
    }
    
    @Nested
    @DisplayName("Find all tests")
    class FindAll {
        
        @Test
        @DisplayName("?????????")
        void shouldReturnAllRecords() throws Exception {
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getObject("ID")).thenReturn(1L, 2L);
            when(resultSet.getObject("NAME")).thenReturn("John Doe", "Jane Smith");
            when(resultSet.getObject("EMAIL")).thenReturn("john@example.com", "jane@example.com");
            
            List<DynamicRecord> records = repository.findAll(CUSTOMERS);
            assertThat(records).hasSize(2);
            assertThat(records.get(0).getValue("NAME")).isEqualTo("John Doe");
            assertThat(records.get(1).getValue("NAME")).isEqualTo("Jane Smith");
        }
        
        @Test
        @DisplayName("?????????")
        void shouldSupportPagination() throws Exception {
            // first page
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getObject("ID")).thenReturn(1L, 2L);
            when(resultSet.getObject("SKU")).thenReturn("PROD-001", "PROD-002");
            when(resultSet.getObject("NAME")).thenReturn("Widget A", "Widget B");
            when(resultSet.getObject("PRICE")).thenReturn(new BigDecimal("19.99"), new BigDecimal("29.99"));
            when(resultSet.getObject("ACTIVE")).thenReturn(true, true);
            
            List<DynamicRecord> firstPage = repository.findAll(PRODUCTS, 0, 2);
            assertThat(firstPage).hasSize(2);

            // reset and stub second page
            reset(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(3L);
            when(resultSet.getObject("SKU")).thenReturn("PROD-003");
            when(resultSet.getObject("NAME")).thenReturn("Widget C");
            when(resultSet.getObject("PRICE")).thenReturn(new BigDecimal("39.99"));
            when(resultSet.getObject("ACTIVE")).thenReturn(false);
            
            List<DynamicRecord> secondPage = repository.findAll(PRODUCTS, 1, 2);
            assertThat(secondPage).hasSize(1);
        }
    }
    
    @Nested
    @DisplayName("Find by columns tests")
    class FindByColumns {
        
        @Test
        @DisplayName("?????????")
        void shouldFindBySingleColumn() throws Exception {
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(1L);
            when(resultSet.getObject("NAME")).thenReturn("John Doe");
            when(resultSet.getObject("EMAIL")).thenReturn("john@example.com");
            
            List<DynamicRecord> records = repository.findByColumn(CUSTOMERS, "EMAIL", "john@example.com");
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getValue("NAME")).isEqualTo("John Doe");
        }
        
        @Test
        @DisplayName("?????????")
        void shouldFindByMultipleColumnsAnd() throws Exception {
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(1L);
            when(resultSet.getObject("SKU")).thenReturn("PROD-001");
            when(resultSet.getObject("NAME")).thenReturn("Widget A");
            when(resultSet.getObject("PRICE")).thenReturn(new BigDecimal("9.99"));
            when(resultSet.getObject("ACTIVE")).thenReturn(true);
            
            Map<String, Object> criteria = Map.of("NAME", "Widget A", "active", true);
            List<DynamicRecord> records = repository.findByColumns(PRODUCTS, criteria);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getValue("SKU")).isEqualTo("PROD-001");
        }
    }
    
    @Nested
    @DisplayName("Count")
    class Count {
        
        @Test
        @DisplayName("?????????")
        void shouldCountAll() throws Exception {
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(2L);
            long count = repository.count(CUSTOMERS);
            assertThat(count).isEqualTo(2);
        }
        
        @Test
        @DisplayName("?????????")
        void shouldCountWithCriteria() throws Exception {
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(2L);
            long count = repository.count(PRODUCTS, Map.of("active", true));
            assertThat(count).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Insert tests")
    class Insert {
        
        @Test
        void shouldInsertRecordAndReturnWithGeneratedKey() throws Exception {
            Map<String, Object> values = new HashMap<>();
            values.put("NAME", "Alice Brown");
            values.put("EMAIL", "alice@example.com");
            
            DynamicRecord newCustomer = DynamicRecord.builder()
                    .tableName(CUSTOMERS)
                    .schemaName("PUBLIC")
                    .values(values)
                    .build();
            
            // mock generated key retrieval
            when(preparedStatement.executeUpdate()).thenReturn(1);
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getObject(1)).thenReturn(3L);  // auto-generated ID
            
            DynamicRecord inserted = repository.insert(CUSTOMERS, newCustomer);
            assertThat(inserted.getValue("ID")).isEqualTo(3L);
            assertThat(inserted.getValue("NAME")).isEqualTo("Alice Brown");
            assertThat(inserted.getValue("EMAIL")).isEqualTo("alice@example.com");
        }
        
        @Test
        void shouldInsertFromMap() throws Exception {
            Map<String, Object> values = Map.of("SKU", "PROD-004", "NAME", "Widget D", "PRICE", 49.99, "ACTIVE", true);
            when(preparedStatement.executeUpdate()).thenReturn(1);
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getObject(1)).thenReturn(4L);
            
            DynamicRecord inserted = repository.insert(PRODUCTS, values);
            assertThat(inserted.getValue("ID")).isEqualTo(4L);
            assertThat(inserted.getValue("SKU")).isEqualTo("PROD-004");
        }
    }
    
    @Nested
    @DisplayName("Update tests")
    class Update {
        
        @Test
        void shouldUpdateRecordBySimpleId() throws Exception {
            // stub the internal findById that update() triggers
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(1L);
            when(resultSet.getObject("SKU")).thenReturn("PROD-001");
            when(resultSet.getObject("NAME")).thenReturn("Widget");
            when(resultSet.getObject("PRICE")).thenReturn(new BigDecimal("19.99"));
            when(resultSet.getObject("ACTIVE")).thenReturn(true);
            when(preparedStatement.executeUpdate()).thenReturn(1);

            Optional<DynamicRecord> before = repository.findById(PRODUCTS, 1L);
            assertThat(before).isPresent();
            assertThat(before.get().getValue("PRICE")).isEqualTo(new BigDecimal("19.99"));
            
            DynamicRecord toUpdate = before.get();
            toUpdate.setValue("PRICE", new BigDecimal("24.99"));

            // reset resultSet mock because update() will trigger another executeQuery
            reset(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject("ID")).thenReturn(1L);
            when(resultSet.getObject("SKU")).thenReturn("PROD-001");
            when(resultSet.getObject("NAME")).thenReturn("Widget");
            when(resultSet.getObject("PRICE")).thenReturn(new BigDecimal("24.99"));
            when(resultSet.getObject("ACTIVE")).thenReturn(true);

            repository.update(PRODUCTS, 1L, toUpdate);
            
            Optional<DynamicRecord> after = repository.findById(PRODUCTS, 1L);
            assertThat(after).isPresent();
            assertThat(after.get().getValue("PRICE")).isEqualTo(new BigDecimal("24.99"));
        }
        
        @Test
        void shouldThrowWhenNoRowsUpdated() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(0);

            DynamicRecord record = new DynamicRecord();
            record.setTableName(PRODUCTS);
            record.setValue("SKU", "GHOST");
            record.setValue("NAME", "Ghost");
            record.setValue("PRICE", new BigDecimal("99.99"));
            
            assertThatThrownBy(() -> repository.update(PRODUCTS, 999L, record))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("No record found with id 999");
        }
        
        @Test
        void shouldUpdateOnlyModifiedFields() throws Exception {
            DynamicRecord record = new DynamicRecord();
            record.setTableName(CUSTOMERS);
            record.setValue("NAME", "Johnathan Doe");
            record.setValue("EMAIL", "johnathan@example.com");
            // setValue marks it modified internally
            when(preparedStatement.executeUpdate()).thenReturn(1);
            
            repository.updateModified(CUSTOMERS, 1L, record);
            verify(preparedStatement, atLeastOnce()).executeUpdate();
        }
    }
    
    @Nested
    @DisplayName("Delete tests")
    class Delete {
        
        @Test
        void shouldDeleteBySimpleId() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1);
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getObject(1)).thenReturn(5L);
            
            Map<String, Object> values = Map.of("SKU", "TO-DELETE", "NAME", "Temp", "PRICE", 9.99);
            DynamicRecord inserted = repository.insert(PRODUCTS, values);

            // mock successful delete (executeUpdate again)
            when(preparedStatement.executeUpdate()).thenReturn(1);
            repository.delete(PRODUCTS, inserted.getValue("ID"));
            // no exception thrown
        }
        
        @Test
        void shouldDeleteByCompositeKey() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1);
            Map<String, Object> pk = Map.of("ORDER_ID", 1L, "PRODUCT_ID", 1L);
            repository.delete(ORDER_ITEMS, pk);
        }
        
        @Test
        void shouldDeleteByColumns() throws Exception {
            when(preparedStatement.executeUpdate()).thenReturn(1); // deletes 1 row
            int deleted = repository.deleteByColumns(PRODUCTS, Map.of("active", false));
            assertThat(deleted).isEqualTo(1);
        }
    }
    
    @Test
    void existsShouldReturnTrueWhenFound() throws Exception {
        when(resultSet.next()).thenReturn(true);
        boolean exists = repository.exists(CUSTOMERS, 1L);
        assertThat(exists).isTrue();
    }
    
    @Test
    void existsShouldReturnFalseWhenNotFound() throws Exception {
        when(resultSet.next()).thenReturn(false);
        boolean exists = repository.exists(CUSTOMERS, 999L);
        assertThat(exists).isFalse();
    }
}