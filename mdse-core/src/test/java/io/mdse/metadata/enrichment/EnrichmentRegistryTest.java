package io.mdse.metadata.enrichment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EnrichmentRegistry Tests")
class EnrichmentRegistryTest {
    
    private EnrichmentRegistry registry;
    private UUID schemaId;
    
    @BeforeEach
    void setUp() {
        registry = new EnrichmentRegistry();
        schemaId = UUID.randomUUID();
    }
    
    @Test
    @DisplayName("Should register and retrieve enrichment")
    void testRegisterAndGet() {
        CacheMetadata cacheMetadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .ttl(Duration.ofMinutes(5))
                .build();
        
        registry.register(schemaId, CacheMetadata.class, cacheMetadata);
        
        var retrieved = registry.get(schemaId, CacheMetadata.class);
        
        assertThat(retrieved).isPresent().contains(cacheMetadata);
    }
    
    @Test
    @DisplayName("Should return empty for non-existent enrichment")
    void testGetNonExistent() {
        assertThat(registry.get(schemaId, CacheMetadata.class)).isEmpty();
        assertThat(registry.get(UUID.randomUUID(), CacheMetadata.class)).isEmpty();
    }
    
    @Test
    @DisplayName("Should check if enrichment exists")
    void testHasEnrichment() {
        assertThat(registry.has(schemaId, CacheMetadata.class)).isFalse();
        
        CacheMetadata metadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .build();
        
        registry.register(schemaId, CacheMetadata.class, metadata);
        
        assertThat(registry.has(schemaId, CacheMetadata.class)).isTrue();
    }
    
    @Test
    @DisplayName("Should store multiple enrichment types for same schema")
    void testMultipleEnrichmentTypes() {
        CacheMetadata cacheMetadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .build();
        
        FetchPlanMetadata fetchPlan = FetchPlanMetadata.builder()
                .tableSchemaId(schemaId)
                .maxDepth(3)
                .build();
        
        registry.register(schemaId, CacheMetadata.class, cacheMetadata);
        registry.register(schemaId, FetchPlanMetadata.class, fetchPlan);
        
        assertThat(registry.get(schemaId, CacheMetadata.class)).isPresent();
        assertThat(registry.get(schemaId, FetchPlanMetadata.class)).isPresent();
    }
    
    @Test
    @DisplayName("Should remove enrichment")
    void testRemoveEnrichment() {
        CacheMetadata metadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .build();
        
        registry.register(schemaId, CacheMetadata.class, metadata);
        assertThat(registry.get(schemaId, CacheMetadata.class)).isPresent();
        
        var removed = registry.remove(schemaId, CacheMetadata.class);
        
        assertThat(removed).isPresent().contains(metadata);
        assertThat(registry.get(schemaId, CacheMetadata.class)).isEmpty();
    }
    
    @Test
    @DisplayName("Should clear all enrichments for a schema")
    void testClearForSchema() {
        CacheMetadata cacheMetadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .build();
        
        FetchPlanMetadata fetchPlan = FetchPlanMetadata.builder()
                .tableSchemaId(schemaId)
                .maxDepth(3)
                .build();
        
        registry.register(schemaId, CacheMetadata.class, cacheMetadata);
        registry.register(schemaId, FetchPlanMetadata.class, fetchPlan);
        
        registry.clearForSchema(schemaId);
        
        assertThat(registry.get(schemaId, CacheMetadata.class)).isEmpty();
        assertThat(registry.get(schemaId, FetchPlanMetadata.class)).isEmpty();
    }
    
    @Test
    @DisplayName("Should clear all enrichments")
    void testClearAll() {
        UUID schema1 = UUID.randomUUID();
        UUID schema2 = UUID.randomUUID();
        
        registry.register(schema1, CacheMetadata.class,
                CacheMetadata.builder().tableSchemaId(schema1).build());
        registry.register(schema2, CacheMetadata.class,
                CacheMetadata.builder().tableSchemaId(schema2).build());
        
        registry.clearAll();
        
        assertThat(registry.get(schema1, CacheMetadata.class)).isEmpty();
        assertThat(registry.get(schema2, CacheMetadata.class)).isEmpty();
    }
    
    @Test
    @DisplayName("Should handle null parameters gracefully")
    void testNullParameters() {
        CacheMetadata metadata = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .build();
        
        // Null schema ID
        assertThatThrownBy(() -> registry.register(null, CacheMetadata.class, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema ID cannot be null");
        
        // Null enrichment type
        assertThatThrownBy(() -> registry.register(schemaId, null, metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enrichment type cannot be null");
        
        // Null enrichment
        assertThatThrownBy(() -> registry.register(schemaId, CacheMetadata.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enrichment cannot be null");
        
        // Get with null
        assertThat(registry.get(null, CacheMetadata.class)).isEmpty();
        assertThat(registry.get(schemaId, null)).isEmpty();
    }
    
    @Test
    @DisplayName("Should overwrite existing enrichment")
    void testOverwriteEnrichment() {
        CacheMetadata metadata1 = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(true)
                .ttl(Duration.ofMinutes(5))
                .build();
        
        CacheMetadata metadata2 = CacheMetadata.builder()
                .tableSchemaId(schemaId)
                .cacheable(false)
                .ttl(Duration.ofMinutes(10))
                .build();
        
        registry.register(schemaId, CacheMetadata.class, metadata1);
        registry.register(schemaId, CacheMetadata.class, metadata2);
        
        var retrieved = registry.get(schemaId, CacheMetadata.class);
        
        assertThat(retrieved).isPresent().contains(metadata2);
        assertThat(retrieved.get().isCacheable()).isFalse();
        assertThat(retrieved.get().getTtl()).isEqualTo(Duration.ofMinutes(10));
    }
    
    @Test
    @DisplayName("Should be thread-safe for concurrent access")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        UUID id = UUID.randomUUID();
                        
                        CacheMetadata metadata = CacheMetadata.builder()
                                .tableSchemaId(id)
                                .cacheable(true)
                                .ttl(Duration.ofMinutes(threadId + i))
                                .build();
                        
                        registry.register(id, CacheMetadata.class, metadata);
                        registry.get(id, CacheMetadata.class);
                        registry.has(id, CacheMetadata.class);
                        registry.remove(id, CacheMetadata.class);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertThat(completed).isTrue();
    }
}

