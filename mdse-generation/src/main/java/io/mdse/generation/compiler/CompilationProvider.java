package io.mdse.generation.compiler;

import io.mdse.generation.exception.CompilationException;

import java.util.Map;

/**
 * Abstraction for Java source compilation.
 * Allows plugging different compilation backends (JDK, Janino, external javac).
 *
 * This decouples the generation layer from specific compiler implementations.
 */
public interface CompilationProvider {
    
    /**
     * Compile Java source files to bytecode.
     *
     * @param sources map of fully qualified class name to source code
     * @param parentClassLoader parent classloader for compilation classpath
     * @return compilation result with bytecode and diagnostics
     * @throws CompilationException if compilation fails
     */
    CompilationResult compile(Map<String, String> sources, ClassLoader parentClassLoader)
            throws CompilationException;
    
    /**
     * Check if this compiler is available in the current environment
     */
    boolean isAvailable();
    
    /**
     * Get human-readable name of this compiler
     */
    String getName();
}

