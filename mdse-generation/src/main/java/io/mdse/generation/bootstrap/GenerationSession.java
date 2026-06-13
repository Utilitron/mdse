package io.mdse.generation.bootstrap;

import io.mdse.generation.descriptor.EntityDescriptor;
import io.mdse.generation.loader.SessionClassLoader;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of a complete generation session.
 * Contains the classloader, descriptors, and metadata about the generation.
 */
@Value
@Builder
public class GenerationSession {
    
    /**
     * Unique session ID
     */
    String sessionId;
    
    /**
     * When this session was created
     */
    Instant createdAt;
    
    /**
     * Classloader with all generated classes
     */
    SessionClassLoader classLoader;
    
    /**
     * Entity descriptors that were generated
     */
    @Singular
    List<EntityDescriptor> descriptors;
    
    /**
     * Generated source code (for debugging or materialization)
     */
    @Singular("source")
    Map<String, String> sources;
    
    /**
     * Generation statistics
     */
    GenerationStats stats;
    
    /**
     * Load a generated class from this session
     */
    public <T> Class<T> loadClass(String className) throws ClassNotFoundException {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) classLoader.loadClass(className);
        return clazz;
    }
    
    /**
     * Find descriptor by class name
     */
    public Optional<EntityDescriptor> findDescriptor(String className) {
        return descriptors.stream()
                .filter(d -> d.getClassName().equals(className) ||
                        d.getFullyQualifiedName().equals(className))
                .findFirst();
    }
    
    /**
     * Find descriptor by table name
     */
    public Optional<EntityDescriptor> findDescriptorByTable(String tableName) {
        return descriptors.stream()
                .filter(d -> d.getTableName().equalsIgnoreCase(tableName))
                .findFirst();
    }
    
    /**
     * Get all generated class names
     */
    public List<String> getGeneratedClassNames() {
        return descriptors.stream()
                .map(EntityDescriptor::getFullyQualifiedName)
                .toList();
    }
}

