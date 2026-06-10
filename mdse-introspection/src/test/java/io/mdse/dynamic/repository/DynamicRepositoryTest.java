package io.mdse.dynamic.repository;

import io.mdse.dynamic.exception.RecordNotFoundException;
import io.mdse.dynamic.model.DynamicRecord;
import io.mdse.metadata.introspection.JdbcSchemaIntrospector;
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

import org.mockito.junit.jupiter.MockitoExtension;
import org.zapodot.junit.db.annotations.EmbeddedDatabase;
import org.zapodot.junit.db.annotations.EmbeddedDatabaseTest;
import org.zapodot.junit.db.common.Engine;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EmbeddedDatabaseTest(
        engine = Engine.H2,
        initialSqlResources = "classpath:test-schema.sql"
)
@DisplayName("DynamicRepository Integration Tests")
class DynamicRepositoryTest {

    private DynamicRepository repository;
    private SchemaRegistry schemaRegistry;

    @BeforeEach
    void setUp(final @EmbeddedDatabase DataSource dataSource) {
        // Introspect the real database schema
        JdbcSchemaIntrospector introspector = new JdbcSchemaIntrospector(dataSource);
        schemaRegistry = new SchemaRegistry();
        for (TableSchema schema : introspector.introspectAll()) {
            schemaRegistry.register(schema);
        }

        // Create the repository with real services (coercion, validation, formatting)
        repository = new DynamicRepository(
                dataSource,
                schemaRegistry,
                new StandardCoercionService(),
                new StandardValidationService(),
                new StandardFormatterService()
        );
    }
    
    @Nested
    @DisplayName("Find by id tests")
    class FindById {
        
        @Test
        void shouldReturnRecordWhenFound() {
            Optional<DynamicRecord> result = repository.findById("customers", 1L);
            assertThat(result).isPresent();
            DynamicRecord record = result.get();
            assertThat(record.getValue("ID")).isEqualTo(1L);
            assertThat(record.getValue("NAME")).isEqualTo("John Doe");
            assertThat(record.getValue("EMAIL")).isEqualTo("john@example.com");
        }
        
        @Test
        void shouldReturnEmptyWhenNotFound() {
            Optional<DynamicRecord> result = repository.findById("customers", 999L);
            assertThat(result).isEmpty();
        }
        
        @Test
        void shouldThrowWhenTableHasNoPrimaryKey() {
            assertThatThrownBy(() -> repository.findById("no_pk_table", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no primary key");
        }
        
        @Test
        void shouldFindByCompositeKey() {
            // order_items has composite PK (order_id, "PRODUCT_ID")
            Map<String, Object> pk = Map.of("ORDER_ID", 1L, "PRODUCT_ID", 1L);
            Optional<DynamicRecord> result = repository.findById("order_items", pk);
            assertThat(result).isPresent();
            DynamicRecord record = result.get();
            assertThat(record.getValue("ORDER_ID")).isEqualTo(1L);
            assertThat(record.getValue("PRODUCT_ID")).isEqualTo(1L);
            assertThat(record.getValue("QUANTITY")).isEqualTo(2);
        }
        
        @Test
        void shouldThrowWhenMissingCompositeKeyValue() {
            Map<String, Object> incompletePk = Map.of("ORDER_ID", 1L);
            assertThatThrownBy(() -> repository.findById("order_items", incompletePk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Missing primary key value for column: PRODUCT_ID");
        }
    }
    
    @Nested
    @DisplayName("Find all tests")
    class FindAll {
        
        @Test
        void shouldReturnAllRecords() {
            List<DynamicRecord> records = repository.findAll("customers");
            assertThat(records).hasSize(2);
            assertThat(records.get(0).getValue("NAME")).isEqualTo("John Doe");
            assertThat(records.get(1).getValue("NAME")).isEqualTo("Jane Smith");
        }
        
        @Test
        void shouldSupportPagination() {
            List<DynamicRecord> firstPage = repository.findAll("products", 0, 2);
            assertThat(firstPage).hasSize(2);
            assertThat(firstPage.get(0).getValue("SKU")).isEqualTo("PROD-001");
            assertThat(firstPage.get(1).getValue("SKU")).isEqualTo("PROD-002");

            List<DynamicRecord> secondPage = repository.findAll("products", 1, 2);
            assertThat(secondPage).hasSize(1);
            assertThat(secondPage.get(0).getValue("SKU")).isEqualTo("PROD-003");
        }
    }
    
    @Nested
    @DisplayName("Find by columns tests")
    class FindByColumns {
        
        @Test
        void shouldFindBySingleColumn() {
            List<DynamicRecord> records = repository.findByColumn("customers", "EMAIL", "john@example.com");
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getValue("NAME")).isEqualTo("John Doe");
        }
        
        @Test
        void shouldFindByMultipleColumnsAnd() {
            Map<String, Object> criteria = Map.of("NAME", "Widget A", "active", true);
            List<DynamicRecord> records = repository.findByColumns("products", criteria);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getValue("SKU")).isEqualTo("PROD-001");
        }
    }
    
    @Nested
    @DisplayName("Count")
    class Count {
        
        @Test
        void shouldCountAll() {
            long count = repository.count("customers");
            assertThat(count).isEqualTo(2);
        }
        
        @Test
        void shouldCountWithCriteria() {
            long count = repository.count("products", Map.of("active", true));
            assertThat(count).isEqualTo(2);
        }
    }
    
    @Nested
    @DisplayName("Insert tests")
    class Insert {
        
        @Test
        void shouldInsertRecordAndReturnWithGeneratedKey() {
            Map<String, Object> values = new HashMap<>();
            values.put("NAME", "Alice Brown");
            values.put("EMAIL", "alice@example.com");
            
            DynamicRecord newCustomer = DynamicRecord.builder()
                    .tableName("customers")
                    .schemaName("PUBLIC")
                    .values(values)
                    .build();

            DynamicRecord inserted = repository.insert("customers", newCustomer);
            assertThat(inserted.getValue("ID")).isNotNull();
            assertThat(inserted.getValue("NAME")).isEqualTo("Alice Brown");
            assertThat(inserted.getValue("EMAIL")).isEqualTo("alice@example.com");

            // Verify it was actually inserted
            Optional<DynamicRecord> found = repository.findById("customers", inserted.getValue("ID"));
            assertThat(found).isPresent();
        }
        
        @Test
        void shouldInsertFromMap() {
            Map<String, Object> values = Map.of("SKU", "PROD-004", "NAME", "Widget D", "PRICE", 49.99, "ACTIVE", true);
            DynamicRecord inserted = repository.insert("products", values);
            assertThat(inserted.getValue("ID")).isNotNull();
            assertThat(inserted.getValue("SKU")).isEqualTo("PROD-004");
        }
    }
    
    @Nested
    @DisplayName("Update tests")
    class Update {
        
        @Test
        void shouldUpdateRecordBySimpleId() {
            Optional<DynamicRecord> before = repository.findById("products", 1L);
            assertThat(before).isPresent();
            assertThat(before.get().getValue("PRICE")).isEqualTo(new BigDecimal("19.99"));
            
            DynamicRecord toUpdate = before.get();  // full record
            toUpdate.setValue("PRICE", new BigDecimal("24.99"));
            
            repository.update("products", 1L, toUpdate);

            Optional<DynamicRecord> after = repository.findById("products", 1L);
            assertThat(after).isPresent();
            assertThat(after.get().getValue("PRICE")).isEqualTo(new BigDecimal("24.99"));
        }
        
        @Test
        void shouldThrowWhenNoRowsUpdated() {
            DynamicRecord record = new DynamicRecord();
            record.setTableName("products");
            record.setValue("SKU", "GHOST");
            record.setValue("NAME", "Ghost");
            record.setValue("PRICE", new BigDecimal("99.99"));
            
            assertThatThrownBy(() -> repository.update("products", 999L, record))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("No record found with id 999");
        }
        
        @Test
        void shouldUpdateOnlyModifiedFields() {
            Optional<DynamicRecord> before = repository.findById("customers", 1L);
            assertThat(before).isPresent();
            before.get().setValue("NAME", "Johnathan Doe");
            repository.updateModified("customers", 1L, before.get());

            Optional<DynamicRecord> after = repository.findById("customers", 1L);
            assertThat(after).isPresent();
            assertThat(after.get().getValue("NAME")).isEqualTo("Johnathan Doe");
            // other fields unchanged
            assertThat(after.get().getValue("EMAIL")).isEqualTo("john@example.com");
        }
    }
    
    @Nested
    @DisplayName("Delete tests")
    class Delete {
        
        @Test
        void shouldDeleteBySimpleId() {
            // Insert a temporary product first
            Map<String, Object> values = Map.of("SKU", "TO-DELETE", "NAME", "Temp", "PRICE", 9.99);
            DynamicRecord inserted = repository.insert("products", values);
            Long id = (Long) inserted.getValue("ID");

            repository.delete("products", id);
            Optional<DynamicRecord> deleted = repository.findById("products", id);
            assertThat(deleted).isEmpty();
        }
        
        @Test
        void shouldDeleteByCompositeKey() {
            // Delete an order_item (composite key)
            Map<String, Object> pk = Map.of("ORDER_ID", 1L, "PRODUCT_ID", 1L);
            repository.delete("order_items", pk);
            Optional<DynamicRecord> deleted = repository.findById("order_items", pk);
            assertThat(deleted).isEmpty();
        }
        
        @Test
        void shouldDeleteByColumns() {
            int deleted = repository.deleteByColumns("products", Map.of("active", false));
            assertThat(deleted).isEqualTo(1); // PROD-003 is inactive
        }
    }
    
    @Test
    void existsShouldReturnTrueWhenFound() {
        boolean exists = repository.exists("customers", 1L);
        assertThat(exists).isTrue();
    }

    @Test
    void existsShouldReturnFalseWhenNotFound() {
        boolean exists = repository.exists("customers", 999L);
        assertThat(exists).isFalse();
    }
}