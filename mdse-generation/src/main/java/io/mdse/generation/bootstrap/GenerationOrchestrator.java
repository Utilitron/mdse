package io.mdse.generation.bootstrap;

import io.mdse.generation.exception.CompilationException;
import io.mdse.generation.compiler.CompilationProvider;
import io.mdse.generation.compiler.CompilationResult;
import io.mdse.generation.compiler.JdkCompiler;
import io.mdse.generation.descriptor.EntityDescriptor;
import io.mdse.generation.loader.ClassLoaderRegistry;
import io.mdse.generation.loader.SessionClassLoader;
import io.mdse.generation.source.GenerationTarget;
import io.mdse.generation.source.JpaEntityGenerator;
import io.mdse.generation.source.SourceGenerator;
import io.mdse.metadata.graph.DependencyGraph;
import io.mdse.metadata.graph.DependencyGraphAnalyzer;
import io.mdse.metadata.introspection.JdbcSchemaIntrospector;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.TableSchema;

import java.time.Instant;
import java.util.*;

/**
 * Main orchestrator for runtime code generation.
 *
 * Workflow:
 * 1. Introspect database schema
 * 2. Build dependency graph and sort tables
 * 3. Convert to EntityDescriptors
 * 4. Generate source code
 * 5. Compile to bytecode
 * 6. Load into isolated classloader
 * 7. Return GenerationSession
 *
 * This class ties together all the generation layers.
 */
public class GenerationOrchestrator {
    
    private final JdbcSchemaIntrospector introspector;
    private final SchemaRegistry schemaRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final CompilationProvider compilationProvider;
    private final Map<GenerationTarget, SourceGenerator> generators;
    
    /**
     * Create with default components
     */
    public GenerationOrchestrator(
            JdbcSchemaIntrospector introspector,
            SchemaRegistry schemaRegistry) {
        this(introspector, schemaRegistry, new JdkCompiler(), new ClassLoaderRegistry());
    }
    
    /**
     * Create with custom compilation provider
     */
    public GenerationOrchestrator(
            JdbcSchemaIntrospector introspector,
            SchemaRegistry schemaRegistry,
            CompilationProvider compilationProvider,
            ClassLoaderRegistry classLoaderRegistry) {
        this.introspector = introspector;
        this.schemaRegistry = schemaRegistry;
        this.compilationProvider = compilationProvider;
        this.classLoaderRegistry = classLoaderRegistry;
        
        // Register default generators
        this.generators = new HashMap<>();
        this.generators.put(GenerationTarget.JPA, new JpaEntityGenerator());
    }
    
    /**
     * Register a custom generator
     */
    public void registerGenerator(SourceGenerator generator) {
        generators.put(generator.getTarget(), generator);
    }
    
    /**
     * Generate entities from all tables in the schema
     */
    public GenerationSession generateAll(GenerationConfig config)
            throws CompilationException {
        
        long startTime = System.currentTimeMillis();
        
        // 1. Introspect all tables
        Collection<TableSchema> schemas = introspector.introspectAll();
        
        // 2. Build dependency graph and calculate creation order
        DependencyGraph graph = DependencyGraph.from(schemas);
        List<String> creationOrder = DependencyGraphAnalyzer.calculateCreationOrder(graph);
        
        // 3. Convert to EntityDescriptors (in dependency order)
        List<EntityDescriptor> descriptors = new ArrayList<>();
        for (String tableName : creationOrder) {
            TableSchema schema = schemaRegistry.getRequired(tableName);
            EntityDescriptor descriptor = EntityDescriptor.from(
                    schema,
                    config.getPackageName()
            );
            descriptors.add(descriptor);
        }
        
        // 4. Generate source code
        Map<String, String> allSources = new LinkedHashMap<>();
        SourceGenerator generator = generators.get(config.getTarget());
        
        if (generator == null) {
            throw new IllegalStateException(
                    "No generator registered for target: " + config.getTarget()
            );
        }
        
        for (EntityDescriptor descriptor : descriptors) {
            Map<String, String> sources = generator.generate(descriptor);
            allSources.putAll(sources);
        }
        
        // 5. Compile
        long compilationStart = System.currentTimeMillis();
        CompilationResult compilationResult = compilationProvider.compile(
                allSources,
                Thread.currentThread().getContextClassLoader()
        );
        long compilationTime = System.currentTimeMillis() - compilationStart;
        
        // 6. Create session classloader
        String sessionId = UUID.randomUUID().toString();
        SessionClassLoader classLoader = new SessionClassLoader(
                sessionId,
                compilationResult.getClassBytesMap(),
                Thread.currentThread().getContextClassLoader()
        );
        
        // Register session
        classLoaderRegistry.register(classLoader);
        
        // 7. Build statistics
        long totalTime = System.currentTimeMillis() - startTime;
        
        GenerationStats stats = GenerationStats.builder()
                .totalTables(schemas.size())
                .generatedEntities(descriptors.size())
                .totalFields(descriptors.stream()
                        .mapToInt(d -> d.getFields().size())
                        .sum())
                .totalRelationships(descriptors.stream()
                        .mapToInt(d -> d.getRelationships().size())
                        .sum())
                .compilationTimeMs(compilationTime)
                .totalTimeMs(totalTime)
                .totalBytecodeSize(classLoader.getStats().getTotalBytecodeSize())
                .build();
        
        // 8. Return session
        return GenerationSession.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .classLoader(classLoader)
                .descriptors(descriptors)
                .sources(allSources)
                .stats(stats)
                .build();
    }
    
    /**
     * Generate entities for specific tables only
     */
    public GenerationSession generateTables(
            GenerationConfig config,
            Collection<String> tableNames)
            throws CompilationException {
        
        long startTime = System.currentTimeMillis();
        
        // Get schemas for requested tables
        List<TableSchema> schemas = new ArrayList<>();
        for (String tableName : tableNames) {
            schemas.add(schemaRegistry.getRequired(tableName));
        }
        
        // Build dependency graph with just these tables
        DependencyGraph graph = DependencyGraph.from(schemas);
        List<String> creationOrder = DependencyGraphAnalyzer.calculateCreationOrder(graph);
        
        // Convert to descriptors
        List<EntityDescriptor> descriptors = new ArrayList<>();
        for (String tableName : creationOrder) {
            TableSchema schema = schemaRegistry.getRequired(tableName);
            EntityDescriptor descriptor = EntityDescriptor.from(
                    schema,
                    config.getPackageName()
            );
            descriptors.add(descriptor);
        }
        
        // Generate and compile
        Map<String, String> allSources = new LinkedHashMap<>();
        SourceGenerator generator = generators.get(config.getTarget());
        
        for (EntityDescriptor descriptor : descriptors) {
            allSources.putAll(generator.generate(descriptor));
        }
        
        long compilationStart = System.currentTimeMillis();
        CompilationResult compilationResult = compilationProvider.compile(
                allSources,
                Thread.currentThread().getContextClassLoader()
        );
        long compilationTime = System.currentTimeMillis() - compilationStart;
        
        // Create session
        String sessionId = UUID.randomUUID().toString();
        SessionClassLoader classLoader = new SessionClassLoader(
                sessionId,
                compilationResult.getClassBytesMap(),
                Thread.currentThread().getContextClassLoader()
        );
        
        classLoaderRegistry.register(classLoader);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        GenerationStats stats = GenerationStats.builder()
                .totalTables(schemas.size())
                .generatedEntities(descriptors.size())
                .totalFields(descriptors.stream().mapToInt(d -> d.getFields().size()).sum())
                .totalRelationships(descriptors.stream().mapToInt(d -> d.getRelationships().size()).sum())
                .compilationTimeMs(compilationTime)
                .totalTimeMs(totalTime)
                .totalBytecodeSize(classLoader.getStats().getTotalBytecodeSize())
                .build();
        
        return GenerationSession.builder()
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .classLoader(classLoader)
                .descriptors(descriptors)
                .sources(allSources)
                .stats(stats)
                .build();
    }
    
    /**
     * Get a previously generated session
     */
    public Optional<SessionClassLoader> getSession(String sessionId) {
        return classLoaderRegistry.get(sessionId);
    }
    
    /**
     * Remove a session (allows GC)
     */
    public void removeSession(String sessionId) {
        classLoaderRegistry.remove(sessionId);
    }
    
    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return classLoaderRegistry.getActiveSessionCount();
    }
}


