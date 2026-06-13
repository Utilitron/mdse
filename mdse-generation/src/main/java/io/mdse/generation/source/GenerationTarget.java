package io.mdse.generation.source;

/**
 * Supported generation targets
 */
public enum GenerationTarget {
    /**
     * JPA/Hibernate entities with annotations
     */
    JPA,
    
    /**
     * Plain Java POJOs (no annotations)
     */
    POJO,
    
    /**
     * jOOQ records and table constants
     */
    JOOQ,
    
    /**
     * MyBatis mapper interfaces
     */
    MYBATIS,
    
    /**
     * Spring Data JDBC entities
     */
    SPRING_DATA_JDBC,
    
    /**
     * Micronaut Data entities
     */
    MICRONAUT_DATA
}

