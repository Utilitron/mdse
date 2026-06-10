package io.mdse.metadata.enrichment;

/**
 * Relationship fetch strategies
 */
public enum FetchStrategy {
    /**
     * Fetch on first access (N+1 queries possible)
     */
    LAZY,
    
    /**
     * Fetch immediately with parent (JOIN)
     */
    EAGER,
    
    /**
     * Batch multiple lazy fetches into single query
     */
    BATCH,
    
    /**
     * Use subselect to fetch related entities
     */
    SUBSELECT
}

