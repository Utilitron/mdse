package io.mdse.metadata.enrichment;

/**
 * Cache strategy options
 */
public enum CacheStrategy {
    /**
     * Read from cache, fetch on miss, populate cache
     */
    READ_THROUGH,
    
    /**
     * Write to cache and database synchronously
     */
    WRITE_THROUGH,
    
    /**
     * Write to cache immediately, database asynchronously
     */
    WRITE_BEHIND,
    
    /**
     * Cache aside - application manages cache
     */
    CACHE_ASIDE,
    
    /**
     * Refresh cache periodically
     */
    REFRESH_AHEAD
}

