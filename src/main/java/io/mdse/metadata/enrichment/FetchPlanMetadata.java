package io.mdse.metadata.enrichment;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Query fetch planning metadata.
 * Enrichment overlay for controlling relationship loading strategies.
 *
 * This is operational metadata, NOT part of the schema domain.
 */
@Value
@Builder
public class FetchPlanMetadata {
    
    /**
     * Reference to the table schema this enriches
     */
    UUID tableSchemaId;
    
    /**
     * Fetch strategies for each relationship.
     * Key: relationship name (foreign key constraint name or field name)
     * Value: how to fetch related data
     */
    @Singular
    Map<String, FetchStrategy> relationshipStrategies;
    
    /**
     * Maximum depth for relationship traversal (prevents infinite loops)
     */
    @Builder.Default
    int maxDepth = 3;
    
    /**
     * Relationships to fetch eagerly (immediately with parent)
     */
    @Singular
    List<String> eagerRelationships;
    
    /**
     * Batch size for batched fetches
     */
    @Builder.Default
    int batchSize = 50;
}
