package io.mdse.generation.source;

import io.mdse.generation.descriptor.EntityDescriptor;

import java.util.Map;

/**
 * Pluggable source code generator interface.
 * Implementations generate Java source code for different targets (JPA, jOOQ, plain POJOs, etc.)
 *
 * This abstraction allows swapping persistence frameworks without changing
 * the introspection or descriptor layers.
 */
public interface SourceGenerator {
    
    /**
     * Check if this generator supports the given target
     */
    boolean supports(GenerationTarget target);
    
    /**
     * Generate source code for an entity.
     *
     * @param descriptor the entity descriptor
     * @return map of fully qualified class name to source code
     */
    Map<String, String> generate(EntityDescriptor descriptor);
    
    /**
     * Get the target this generator produces
     */
    GenerationTarget getTarget();
    
    /**
     * Get human-readable name of this generator
     */
    default String getName() {
        return getTarget().name() + " Generator";
    }
}

