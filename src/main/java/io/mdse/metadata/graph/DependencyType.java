package io.mdse.metadata.graph;

/**
 * Classification of dependency relationships.
 *
 * These describe why one node depends on another.
 */
public enum DependencyType {
    
    /**
     * Standard database foreign key relationship.
     */
    FOREIGN_KEY,
    
    /**
     * Entity inheritance relationship.
     *
     * Example:
     * OrderEntity -> BaseEntity
     */
    INHERITANCE,
    
    /**
     * Generated/runtime dependency.
     *
     * Example:
     * Generated DTO depends on source entity metadata.
     */
    GENERATED,
    
    /**
     * Virtual relationship not enforced physically.
     *
     * Example:
     * Derived query relationship.
     */
    VIRTUAL,
    
    /**
     * Weak/non-enforced logical dependency.
     *
     * Example:
     * Reference by business key.
     */
    SOFT
}
