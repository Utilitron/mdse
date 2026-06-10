package io.mdse.metadata.graph;

import io.mdse.metadata.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DependencyGraphAnalyzer Tests (Immutable Graph)")
class DependencyGraphAnalyzerTest {
    
    private Map<String, TableSchema> normalTables;
    private DependencyGraph normalGraph;
    
    @BeforeEach
    void setUp() {
        normalTables = new LinkedHashMap<>();
        setupNormalTables();
        normalGraph = DependencyGraph.from(normalTables.values());
    }
    
    private void setupNormalTables() {
        // Minimal id column for each table
        ColumnSchema idCol = ColumnSchema.builder()
                .columnName("id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(1)
                .build();
        
        // customers (no FK)
        normalTables.put("customers", TableSchema.builder()
                .tableName("customers")
                .schemaName("public")
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .column(idCol)
                .build());
        
        // products (no FK)
        normalTables.put("products", TableSchema.builder()
                .tableName("products")
                .schemaName("public")
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .column(idCol)
                .build());
        
        // orders depends on customers
        ColumnSchema customerIdCol = ColumnSchema.builder()
                .columnName("customer_id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(2)
                .build();
        
        ForeignKeySchema orderCustomerFk = ForeignKeySchema.builder()
                .constraintName("fk_order_customer")
                .sourceTable("orders")
                .sourceColumn("customer_id")
                .referencedTable("customers")
                .referencedColumn("id")
                .deleteRule(ReferentialAction.RESTRICT)
                .build();
        
        normalTables.put("orders", TableSchema.builder()
                .tableName("orders")
                .schemaName("public")
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .column(idCol)
                .column(customerIdCol)
                .foreignKey(orderCustomerFk)
                .build());
        
        // order_items depends on orders and products
        ColumnSchema orderIdCol = ColumnSchema.builder()
                .columnName("order_id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(1)
                .build();
        ColumnSchema productIdCol = ColumnSchema.builder()
                .columnName("product_id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(2)
                .build();
        
        ForeignKeySchema itemOrderFk = ForeignKeySchema.builder()
                .constraintName("fk_order_item_order")
                .sourceTable("order_items")
                .sourceColumn("order_id")
                .referencedTable("orders")
                .referencedColumn("id")
                .deleteRule(ReferentialAction.CASCADE)
                .build();
        
        ForeignKeySchema itemProductFk = ForeignKeySchema.builder()
                .constraintName("fk_order_item_product")
                .sourceTable("order_items")
                .sourceColumn("product_id")
                .referencedTable("products")
                .referencedColumn("id")
                .deleteRule(ReferentialAction.RESTRICT)
                .build();
        
        normalTables.put("order_items", TableSchema.builder()
                .tableName("order_items")
                .schemaName("public")
                .primaryKey(PrimaryKeySchema.builder()
                        .columnName("order_id")
                        .columnName("product_id")
                        .build())
                .column(orderIdCol)
                .column(productIdCol)
                .foreignKey(itemOrderFk)
                .foreignKey(itemProductFk)
                .build());
    }
    
    // Helper: convert Set<DependencyNode> to Set<String> by node name
    private Set<String> names(Set<DependencyNode> nodes) {
        return nodes.stream().map(DependencyNode::name).collect(Collectors.toSet());
    }
    
    // Helper: convert List<DependencyNode> to List<String> by node name
    private List<String> names(List<DependencyNode> nodes) {
        return nodes.stream().map(DependencyNode::name).toList();
    }
    
    @Nested
    @DisplayName("Graph Building")
    class GraphBuilding {
        
        @Test
        @DisplayName("Should build graph with correct nodes")
        void shouldBuildGraphWithCorrectNodes() {
            assertEquals(4, normalGraph.getNodes().size());
            assertTrue(normalGraph.hasNode("customers"));
            assertTrue(normalGraph.hasNode("orders"));
            assertTrue(normalGraph.hasNode("order_items"));
            assertTrue(normalGraph.hasNode("products"));
        }
        
        @Test
        @DisplayName("Should correctly identify dependencies (outgoing edges)")
        void shouldIdentifyDependencies() {
            Set<String> ordersDeps = names(normalGraph.dependenciesOf("orders"));
            assertEquals(Set.of("customers"), ordersDeps);
            
            Set<String> itemsDeps = names(normalGraph.dependenciesOf("order_items"));
            assertEquals(Set.of("orders", "products"), itemsDeps);
            
            assertTrue(normalGraph.dependenciesOf("customers").isEmpty());
        }
        
        @Test
        @DisplayName("Should correctly identify dependents (incoming edges)")
        void shouldIdentifyDependents() {
            Set<String> customersDeps = names(normalGraph.dependentsOf("customers"));
            assertEquals(Set.of("orders"), customersDeps);
            
            Set<String> ordersDeps = names(normalGraph.dependentsOf("orders"));
            assertEquals(Set.of("order_items"), ordersDeps);
            
            assertTrue(normalGraph.dependentsOf("order_items").isEmpty());
        }
        
        @Test
        @DisplayName("Should handle self-referencing table")
        void shouldHandleSelfReferencingTable() {
            ColumnSchema idCol = ColumnSchema.builder()
                    .columnName("id")
                    .sqlType(Types.BIGINT)
                    .ordinalPosition(1)
                    .build();
            ColumnSchema managerIdCol = ColumnSchema.builder()
                    .columnName("manager_id")
                    .sqlType(Types.BIGINT)
                    .ordinalPosition(2)
                    .build();
            
            ForeignKeySchema selfFk = ForeignKeySchema.builder()
                    .constraintName("fk_emp_manager")
                    .sourceTable("employees")
                    .sourceColumn("manager_id")
                    .referencedTable("employees")
                    .referencedColumn("id")
                    .build();
            
            TableSchema employees = TableSchema.builder()
                    .tableName("employees")
                    .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                    .column(idCol)
                    .column(managerIdCol)
                    .foreignKey(selfFk)
                    .build();
            
            DependencyGraph graph = DependencyGraph.from(List.of(employees));
            assertTrue(graph.hasNode("employees"));
            
            Set<String> deps = names(graph.dependenciesOf("employees"));
            assertTrue(deps.contains("employees"));
            Set<String> dependents = names(graph.dependentsOf("employees"));
            assertTrue(dependents.contains("employees"));
        }
        
        @Test
        @DisplayName("Should handle empty table collection")
        void shouldHandleEmptyTables() {
            DependencyGraph graph = DependencyGraph.from(Collections.emptyList());
            assertNotNull(graph);
            assertTrue(graph.getNodes().isEmpty());
        }
        
        @Test
        @DisplayName("Should handle single table with no dependencies")
        void shouldHandleSingleTableNoDependencies() {
            ColumnSchema idCol = ColumnSchema.builder()
                    .columnName("id")
                    .sqlType(Types.BIGINT)
                    .ordinalPosition(1)
                    .build();
            
            TableSchema single = TableSchema.builder()
                    .tableName("single")
                    .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                    .column(idCol)
                    .build();
            
            DependencyGraph graph = DependencyGraph.from(List.of(single));
            assertEquals(1, graph.getNodes().size());
            assertTrue(graph.dependenciesOf("single").isEmpty());
            assertTrue(graph.dependentsOf("single").isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Topological Sort")
    class TopologicalSort {
        
        @Test
        @DisplayName("Should topologically sort acyclic graph")
        void shouldTopologicalSort() {
            List<String> sorted = DependencyGraphAnalyzer.topologicalSort(normalGraph);
            assertEquals(4, sorted.size());
            
            int customersIdx = sorted.indexOf("customers");
            int productsIdx = sorted.indexOf("products");
            int ordersIdx = sorted.indexOf("orders");
            int itemsIdx = sorted.indexOf("order_items");
            
            assertTrue(customersIdx < ordersIdx);
            assertTrue(ordersIdx < itemsIdx);
            assertTrue(productsIdx < itemsIdx);
        }
        
        @Test
        @DisplayName("Should throw exception for topological sort when cycle exists")
        void shouldThrowOnTopologicalSortWithCycle() {
            DependencyGraph cyclic = buildTwoNodeCycleGraph();
            assertThrows(IllegalStateException.class,
                    () -> DependencyGraphAnalyzer.topologicalSort(cyclic));
        }
    }
    
    @Nested
    @DisplayName("Cycle Detection")
    class CycleDetection {
        
        @Test
        @DisplayName("Should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            DependencyGraph graph = buildThreeNodeCycleGraph();
            List<CircularDependency> cycles = DependencyGraphAnalyzer.detectCircularDependencies(graph);
            assertFalse(cycles.isEmpty());
            
            // Verify that at least one cycle contains table_a (as a node)
            boolean found = cycles.stream()
                    .flatMap(c -> c.nodes().stream())
                    .anyMatch(n -> n.name().equals("table_a"));
            assertTrue(found);
        }
        
        @Test
        @DisplayName("Should not detect cycles in acyclic graph")
        void shouldNotDetectCycles() {
            List<CircularDependency> cycles = DependencyGraphAnalyzer.detectCircularDependencies(normalGraph);
            assertTrue(cycles.isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Dependency Depth")
    class DependencyDepth {
        
        @Test
        @DisplayName("Should calculate correct dependency depths")
        void shouldCalculateDependencyDepth() {
            Map<String, Integer> depths = DependencyGraphAnalyzer.calculateDependencyDepth(normalGraph);
            
            
            System.out.println("depths " + depths);
            
            assertEquals(0, depths.get("customers"));
            assertEquals(0, depths.get("products"));
            assertEquals(1, depths.get("orders"));
            assertEquals(2, depths.get("order_items"));
        }
        
        @Test
        @DisplayName("Should give depth 0 for isolated node")
        void shouldGiveDepthZeroForIsolatedNode() {
            ColumnSchema idCol = ColumnSchema.builder()
                    .columnName("id")
                    .sqlType(Types.BIGINT)
                    .ordinalPosition(1)
                    .build();
            
            TableSchema isolated = TableSchema.builder()
                    .tableName("isolated")
                    .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                    .column(idCol)
                    .build();
            
            DependencyGraph graph = DependencyGraph.from(List.of(isolated));
            Map<String, Integer> depths = DependencyGraphAnalyzer.calculateDependencyDepth(graph);
            assertEquals(0, depths.get("isolated"));
        }
    }
    
    @Nested
    @DisplayName("Transitive Dependencies")
    class TransitiveDependencies {
        
        @Test
        @DisplayName("Should find all transitive dependencies of a table")
        void shouldFindAllDependencies() {
            Set<String> orderItemDeps = DependencyGraphAnalyzer.findAllDependencies(normalGraph, "order_items");
            assertTrue(orderItemDeps.contains("orders"));
            assertTrue(orderItemDeps.contains("products"));
            assertTrue(orderItemDeps.contains("customers"));
            assertFalse(orderItemDeps.contains("order_items"));
        }
        
        @Test
        @DisplayName("Should find all transitive dependents of a table")
        void shouldFindAllDependents() {
            Set<String> customerDependents = DependencyGraphAnalyzer.findAllDependents(normalGraph, "customers");
            assertTrue(customerDependents.contains("orders"));
            assertTrue(customerDependents.contains("order_items"));
            assertFalse(customerDependents.contains("products"));
        }
    }
    
    @Nested
    @DisplayName("Root / Leaf Tables")
    class RootLeafTables {
        
        @Test
        @DisplayName("Should find root tables (no dependencies)")
        void shouldFindRootTables() {
            Set<String> roots = DependencyGraphAnalyzer.findRootTables(normalGraph);
            assertEquals(2, roots.size());
            assertTrue(roots.contains("customers"));
            assertTrue(roots.contains("products"));
        }
        
        @Test
        @DisplayName("Should find leaf tables (no dependents)")
        void shouldFindLeafTables() {
            Set<String> leaves = DependencyGraphAnalyzer.findLeafTables(normalGraph);
            assertEquals(1, leaves.size());
            assertTrue(leaves.contains("order_items"));
        }
    }
    
    @Nested
    @DisplayName("Creation / Deletion Orders")
    class CreationDeletion {
        
        @Test
        @DisplayName("Should calculate creation order (topological)")
        void shouldCalculateCreationOrder() {
            List<String> creationOrder = DependencyGraphAnalyzer.calculateCreationOrder(normalGraph);
            int customersIdx = creationOrder.indexOf("customers");
            int ordersIdx = creationOrder.indexOf("orders");
            int itemsIdx = creationOrder.indexOf("order_items");
            assertTrue(customersIdx < ordersIdx);
            assertTrue(ordersIdx < itemsIdx);
        }
        
        @Test
        @DisplayName("Should calculate deletion order (reverse topological)")
        void shouldCalculateDeletionOrder() {
            List<String> deletionOrder = DependencyGraphAnalyzer.calculateDeletionOrder(normalGraph);
            int customersIdx = deletionOrder.indexOf("customers");
            int ordersIdx = deletionOrder.indexOf("orders");
            int itemsIdx = deletionOrder.indexOf("order_items");
            assertTrue(itemsIdx < ordersIdx);
            assertTrue(ordersIdx < customersIdx);
        }
    }
    
    @Nested
    @DisplayName("Impact Analysis")
    class ImpactAnalysis {
        
        @Test
        @DisplayName("Should analyze impact of table modification")
        void shouldAnalyzeImpact() {
            Set<String> impacted = DependencyGraphAnalyzer.analyzeImpact(normalGraph, "customers");
            assertTrue(impacted.contains("orders"));
            assertTrue(impacted.contains("order_items"));
            assertFalse(impacted.contains("products"));
        }
    }
    
    @Nested
    @DisplayName("Strongly Connected Components")
    class StronglyConnectedComponents {
        
        @Test
        @DisplayName("Should find strongly connected components")
        void shouldFindSCCs() {
            TableSchema tableA = createTableWithFk("table_a", "table_b");
            TableSchema tableB = createTableWithFk("table_b", "table_c");
            TableSchema tableC = createTableWithFk("table_c", "table_a");
            TableSchema tableD = createTableWithFk("table_d", "table_a");
            
            DependencyGraph graph = DependencyGraph.from(List.of(tableA, tableB, tableC, tableD));
            List<Set<DependencyNode>> components = DependencyGraphAnalyzer.findStronglyConnectedComponents(graph);
            
            boolean hasCycleComponent = components.stream()
                    .map(comp -> comp.stream().map(DependencyNode::name).collect(Collectors.toSet()))
                    .anyMatch(comp -> comp.size() == 3 &&
                            comp.contains("table_a") &&
                            comp.contains("table_b") &&
                            comp.contains("table_c"));
            assertTrue(hasCycleComponent);
            
            boolean hasDComponent = components.stream()
                    .map(comp -> comp.stream().map(DependencyNode::name).collect(Collectors.toSet()))
                    .anyMatch(comp -> comp.size() == 1 && comp.contains("table_d"));
            assertTrue(hasDComponent);
        }
    }
    
    @Nested
    @DisplayName("Graph Validation")
    class GraphValidation {
        
        @Test
        @DisplayName("Should validate a consistent graph")
        void shouldValidateConsistentGraph() {
            assertTrue(DependencyGraphAnalyzer.validateGraph(normalGraph));
        }
        
        @Test
        @DisplayName("Should detect invalid graph with dangling reference")
        void shouldDetectInvalidGraph() {
            TableSchema tableA = createTableWithFk("table_a", "table_b");
            TableSchema tableB = createTableWithFk("table_b", "table_c");
            TableSchema tableX = createTableWithFk("table_x", "nonexistent");
            DependencyGraph graph = DependencyGraph.from(List.of(tableA, tableB, tableX));
            assertFalse(DependencyGraphAnalyzer.validateGraph(graph));
        }
    }
    
    // --------------------------------------------------------------------
    // Helper methods to create cyclic graphs
    // --------------------------------------------------------------------
    
    private DependencyGraph buildTwoNodeCycleGraph() {
        TableSchema tableA = createTableWithFk("table_a", "table_b");
        TableSchema tableB = createTableWithFk("table_b", "table_a");
        return DependencyGraph.from(List.of(tableA, tableB));
    }
    
    private DependencyGraph buildThreeNodeCycleGraph() {
        TableSchema tableA = createTableWithFk("table_a", "table_b");
        TableSchema tableB = createTableWithFk("table_b", "table_c");
        TableSchema tableC = createTableWithFk("table_c", "table_a");
        return DependencyGraph.from(List.of(tableA, tableB, tableC));
    }
    
    private TableSchema createTableWithFk(String tableName, String referencedTable) {
        ColumnSchema idCol = ColumnSchema.builder()
                .columnName("id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(1)
                .build();
        ColumnSchema refIdCol = ColumnSchema.builder()
                .columnName("ref_id")
                .sqlType(Types.BIGINT)
                .ordinalPosition(2)
                .build();
        
        ForeignKeySchema fk = ForeignKeySchema.builder()
                .constraintName("fk_" + tableName + "_to_" + referencedTable)
                .sourceTable(tableName)
                .sourceColumn("ref_id")
                .referencedTable(referencedTable)
                .referencedColumn("id")
                .deleteRule(ReferentialAction.RESTRICT)
                .build();
        
        return TableSchema.builder()
                .tableName(tableName)
                .primaryKey(PrimaryKeySchema.builder().columnName("id").build())
                .column(idCol)
                .column(refIdCol)
                .foreignKey(fk)
                .build();
    }
}
