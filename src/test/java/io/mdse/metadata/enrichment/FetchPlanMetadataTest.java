package io.mdse.metadata.enrichment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FetchPlanMetadata Tests")
public class FetchPlanMetadataTest {
    
    @Test
    @DisplayName("Should build fetch plan metadata")
    void testBuildFetchPlan() {
        UUID schemaId = UUID.randomUUID();
        
        FetchPlanMetadata metadata = FetchPlanMetadata.builder()
                .tableSchemaId(schemaId)
                .maxDepth(3)
                .relationshipStrategy("customer", FetchStrategy.EAGER)
                .relationshipStrategy("items", FetchStrategy.BATCH)
                .relationshipStrategy("payments", FetchStrategy.LAZY)
                .eagerRelationship("customer")
                .batchSize(50)
                .build();
        
        assertThat(metadata.getTableSchemaId()).isEqualTo(schemaId);
        assertThat(metadata.getMaxDepth()).isEqualTo(3);
        assertThat(metadata.getRelationshipStrategies())
                .containsEntry("customer", FetchStrategy.EAGER)
                .containsEntry("items", FetchStrategy.BATCH)
                .containsEntry("payments", FetchStrategy.LAZY);
        assertThat(metadata.getEagerRelationships()).contains("customer");
        assertThat(metadata.getBatchSize()).isEqualTo(50);
    }
    
    @Test
    @DisplayName("Should have default max depth")
    void testDefaultMaxDepth() {
        UUID schemaId = UUID.randomUUID();
        
        FetchPlanMetadata metadata = FetchPlanMetadata.builder()
                .tableSchemaId(schemaId)
                .build();
        
        assertThat(metadata.getMaxDepth()).isEqualTo(3); // Default
        assertThat(metadata.getBatchSize()).isEqualTo(50); // Default
    }
}
