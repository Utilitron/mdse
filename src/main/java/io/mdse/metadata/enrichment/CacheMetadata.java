package io.mdse.metadata.enrichment;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Runtime caching strategy metadata.
 * Enrichment overlay for table schemas - completely separate from schema structure.
 *
 * This is NOT part of the core schema domain - it's operational metadata.
 */
@Value
@Builder
public class CacheMetadata {
    
    /**
     * Reference to the table schema this enriches
     */
    UUID tableSchemaId;
    
    /**
     * Whether this table's data should be cached
     */
    @Builder.Default
    boolean cacheable = true;
    
    /**
     * Time-to-live for cached entries
     */
    Duration ttl;
    
    /**
     * Caching strategy
     */
    CacheStrategy strategy;
    
    /**
     * Maximum number of entries to cache
     */
    Integer maxEntries;
    
    /**
     * Events/operations that should invalidate cache
     */
    @Singular
    List<String> invalidationTriggers;
    
    /**
     * Whether to cache null/empty results
     */
    @Builder.Default
    boolean cacheNulls = false;
}

