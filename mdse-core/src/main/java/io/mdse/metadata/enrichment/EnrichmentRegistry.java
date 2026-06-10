package io.mdse.metadata.enrichment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for metadata enrichments.
 * Allows attaching multiple enrichment types to schemas without modifying the schemas.
 *
 * This enables:
 * - Separation of concerns (schema vs runtime metadata)
 * - Context-specific metadata (cache, fetch plans, etc.)
 * - Extensibility without schema changes
 */
public class EnrichmentRegistry {
    
    private final Map<EnrichmentKey, Object> enrichments = new ConcurrentHashMap<>();
    
    /**
     * Register an enrichment for a schema.
     */
    public <T> void register(UUID schemaId, Class<T> enrichmentType, T enrichment) {
        if (schemaId == null) {
            throw new IllegalArgumentException("Schema ID cannot be null");
        }
        if (enrichmentType == null) {
            throw new IllegalArgumentException("Enrichment type cannot be null");
        }
        if (enrichment == null) {
            throw new IllegalArgumentException("Enrichment cannot be null");
        }
        
        EnrichmentKey key = new EnrichmentKey(schemaId, enrichmentType);
        enrichments.put(key, enrichment);
    }
    
    /**
     * Get an enrichment for a schema.
     */
    public <T> Optional<T> get(UUID schemaId, Class<T> enrichmentType) {
        if (schemaId == null || enrichmentType == null) {
            return Optional.empty();
        }
        
        EnrichmentKey key = new EnrichmentKey(schemaId, enrichmentType);
        Object enrichment = enrichments.get(key);
        
        if (enrichment != null && enrichmentType.isInstance(enrichment)) {
            return Optional.of(enrichmentType.cast(enrichment));
        }
        
        return Optional.empty();
    }
    
    /**
     * Check if an enrichment exists.
     */
    public boolean has(UUID schemaId, Class<?> enrichmentType) {
        return get(schemaId, enrichmentType).isPresent();
    }
    
    /**
     * Remove an enrichment.
     */
    public <T> Optional<T> remove(UUID schemaId, Class<T> enrichmentType) {
        if (schemaId == null || enrichmentType == null) {
            return Optional.empty();
        }
        
        EnrichmentKey key = new EnrichmentKey(schemaId, enrichmentType);
        Object removed = enrichments.remove(key);
        
        if (removed != null && enrichmentType.isInstance(removed)) {
            return Optional.of(enrichmentType.cast(removed));
        }
        
        return Optional.empty();
    }
    
    /**
     * Clear all enrichments for a schema.
     */
    public void clearForSchema(UUID schemaId) {
        enrichments.keySet().removeIf(key -> key.schemaId.equals(schemaId));
    }
    
    /**
     * Clear all enrichments.
     */
    public void clearAll() {
        enrichments.clear();
    }
    
    /**
     * Cache key for enrichments
     */
    private static class EnrichmentKey {
        private final UUID schemaId;
        private final Class<?> enrichmentType;
        
        EnrichmentKey(UUID schemaId, Class<?> enrichmentType) {
            this.schemaId = schemaId;
            this.enrichmentType = enrichmentType;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnrichmentKey that = (EnrichmentKey) o;
            return Objects.equals(schemaId, that.schemaId) && Objects.equals(enrichmentType, that.enrichmentType);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(schemaId, enrichmentType);
        }
    }
}
