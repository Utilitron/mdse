package io.mdse.generation.bootstrap;

import io.mdse.generation.compiler.JdkCompiler;
import io.mdse.generation.descriptor.EntityDescriptor;
import io.mdse.generation.loader.ClassLoaderRegistry;
import io.mdse.metadata.introspection.JdbcSchemaIntrospector;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import org.junit.jupiter.api.*;

import org.zapodot.junit.db.annotations.EmbeddedDatabase;
import org.zapodot.junit.db.annotations.EmbeddedDatabaseTest;
import org.zapodot.junit.db.common.Engine;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@EmbeddedDatabaseTest(
        engine = Engine.H2,
        initialSqlResources = "classpath:test-schema.sql"
)
@DisplayName("End-to-End Generation Integration Tests")
class GenerationIntegrationTest {
    
    private EmbeddedDatabase database;
    private JdbcSchemaIntrospector introspector;
    private SchemaRegistry schemaRegistry;
    private GenerationOrchestrator orchestrator;
    
    @BeforeEach
    void setUp(final @EmbeddedDatabase DataSource dataSource) {
        
        introspector = new JdbcSchemaIntrospector(dataSource);
        schemaRegistry = new SchemaRegistry();
        
        Collection<TableSchema> schemas = introspector.introspectAll();
        for (TableSchema schema : schemas) {
            schemaRegistry.register(schema);
        }
        
        // Check if JDK compiler is available
        JdkCompiler compiler = new JdkCompiler();
        if (!compiler.isAvailable()) {
            Assumptions.assumeTrue(false, "JDK compiler not available - skipping integration tests");
        }
        
        orchestrator = new GenerationOrchestrator(
                introspector,
                schemaRegistry,
                compiler,
                new ClassLoaderRegistry()
        );
    }
    
    @Test
    @DisplayName("Should generate entities from simple table")
    void testGenerateFromSimpleTable() throws Exception {
        // Create a simple table schema
        TableSchema schema = TableSchema.builder()
                .tableName("simple_table")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .javaTypeName("java.lang.Long")
                        .nullable(false)
                        .primaryKey(true)
                        .autoIncrement(true)
                        .build())
                .column(ColumnSchema.builder()
                        .columnName("name")
                        .sqlType(Types.VARCHAR)
                        .javaTypeName("java.lang.String")
                        .nullable(false)
                        .maxLength(100)
                        .build())
                .build();
        
        schemaRegistry.register(schema);
        
        // Generate
        GenerationConfig config = GenerationConfig.jpa("com.test.entities");
        GenerationSession session = orchestrator.generateTables(
                config,
                List.of("simple_table")
        );
        
        // Verify session
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.getDescriptors()).hasSize(1);
        assertThat(session.getGeneratedClassNames()).contains("com.test.entities.SimpleTable");
        
        // Verify statistics
        assertThat(session.getStats().getGeneratedEntities()).isEqualTo(1);
        assertThat(session.getStats().getTotalFields()).isEqualTo(2);
        assertThat(session.getStats().getCompilationTimeMs()).isGreaterThan(0);
        
        // Load the generated class
        Class<?> generatedClass = session.loadClass("com.test.entities.SimpleTable");
        assertThat(generatedClass).isNotNull();
        assertThat(generatedClass.getName()).isEqualTo("com.test.entities.SimpleTable");
        
        // Verify it has expected methods
        assertThat(generatedClass.getDeclaredMethod("getId")).isNotNull();
        assertThat(generatedClass.getDeclaredMethod("setId", Long.class)).isNotNull();
        assertThat(generatedClass.getDeclaredMethod("getName")).isNotNull();
        assertThat(generatedClass.getDeclaredMethod("setName", String.class)).isNotNull();
        
        // Create an instance
        Object instance = generatedClass.getDeclaredConstructor().newInstance();
        assertThat(instance).isNotNull();
    }
    
    @Test
    @DisplayName("Should generate entities with relationships")
    void testGenerateWithRelationships() throws Exception {
        // Generate all entities
        GenerationConfig config = GenerationConfig.jpa("com.test.entities");
        GenerationSession session = orchestrator.generateAll(config);
        
        assertThat(session.getDescriptors()).hasSize(4);
        assertThat(session.getGeneratedClassNames()).contains(
                "com.test.entities.Customers",
                "com.test.entities.Orders",
                "com.test.entities.Products",
                "com.test.entities.OrderItems"
        );
        
        // Verify relationships
        EntityDescriptor orderDescriptor = session.findDescriptor("Orders").orElseThrow();
        assertThat(orderDescriptor.hasRelationships()).isTrue();
        assertThat(orderDescriptor.getRelationships()).hasSizeGreaterThan(0);
    }
    
    @Test
    @DisplayName("Should handle multiple generation sessions independently")
    void testMultipleSessions() throws Exception {
        TableSchema schema = TableSchema.builder()
                .tableName("test_table")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .build();
        
        schemaRegistry.register(schema);
        
        GenerationConfig config = GenerationConfig.jpa("com.test.entities");
        
        // Generate first session
        GenerationSession session1 = orchestrator.generateTables(
                config,
                List.of("test_table")
        );
        
        // Generate second session
        GenerationSession session2 = orchestrator.generateTables(
                config,
                List.of("test_table")
        );
        
        // Sessions should be independent
        assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
        assertThat(session1.getClassLoader()).isNotSameAs(session2.getClassLoader());
        
        // Both should have loaded the same logical class but in different classloaders
        Class<?> class1 = session1.loadClass("com.test.entities.TestTable");
        Class<?> class2 = session2.loadClass("com.test.entities.TestTable");
        
        assertThat(class1.getName()).isEqualTo(class2.getName());
        // But they should be different Class objects (different classloaders)
        assertThat(class1).isNotSameAs(class2);
    }
    
    @Test
    @DisplayName("Should track active sessions")
    void testSessionTracking() throws Exception {
        TableSchema schema = TableSchema.builder()
                .tableName("test")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .build();
        
        schemaRegistry.register(schema);
        
        int initialCount = orchestrator.getActiveSessionCount();
        
        GenerationConfig config = GenerationConfig.jpa("com.test");
        GenerationSession session = orchestrator.generateTables(config, List.of("test"));
        
        assertThat(orchestrator.getActiveSessionCount()).isEqualTo(initialCount + 1);
        
        // Remove session
        orchestrator.removeSession(session.getSessionId());
        
        assertThat(orchestrator.getActiveSessionCount()).isEqualTo(initialCount);
    }
    
    @Test
    @DisplayName("Should find descriptor by table name")
    void testFindDescriptorByTable() throws Exception {
        GenerationConfig config = GenerationConfig.jpa("com.test");
        GenerationSession session = orchestrator.generateAll(config);
        
        EntityDescriptor descriptor = session.findDescriptorByTable("products").orElseThrow();
        
        assertThat(descriptor.getTableName()).isEqualTo("PRODUCTS");
        assertThat(descriptor.getClassName()).isEqualTo("Products");
    }
    
    @Test
    @DisplayName("Should generate consistent schema hash")
    void testSchemaHashConsistency() throws Exception {
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
                        .build())
                .build();
        
        schemaRegistry.register(schema);
        
        GenerationConfig config = GenerationConfig.jpa("com.test");
        
        // Generate twice
        GenerationSession session1 = orchestrator.generateTables(config, List.of("users"));
        GenerationSession session2 = orchestrator.generateTables(config, List.of("users"));
        
        String hash1 = session1.getDescriptors().get(0).getSchemaHash();
        String hash2 = session2.getDescriptors().get(0).getSchemaHash();
        
        // Same schema should produce same hash
        assertThat(hash1).isEqualTo(hash2);
    }
    
    @Test
    @DisplayName("Should include source code in session for debugging")
    void testSourceCodeAvailable() throws Exception {
        TableSchema schema = TableSchema.builder()
                .tableName("debug_table")
                .column(ColumnSchema.builder()
                        .columnName("id")
                        .sqlType(Types.BIGINT)
                        .primaryKey(true)
                        .build())
                .build();
        
        schemaRegistry.register(schema);
        
        GenerationConfig config = GenerationConfig.jpa("com.test");
        GenerationSession session = orchestrator.generateTables(config, List.of("debug_table"));
        
        assertThat(session.getSources()).isNotEmpty();
        assertThat(session.getSources()).containsKey("com.test.DebugTable");
        
        String source = session.getSources().get("com.test.DebugTable");
        assertThat(source)
                .contains("package com.test;")
                .contains("public class DebugTable")
                .contains("@Entity");
    }
}

