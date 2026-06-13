package io.mdse.generation.compiler;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Result of a compilation operation
 */
@Value
@Builder
public class CompilationResult {
    
    /**
     * Map of fully qualified class name to bytecode
     */
    @Singular("classBytes")
    Map<String, byte[]> classBytesMap;
    
    /**
     * List of successfully compiled class names
     */
    @Singular
    List<String> compiledClasses;
    
    /**
     * Compilation diagnostics (warnings, errors, info)
     */
    @Singular
    List<CompilationDiagnostic> diagnostics;
    
    /**
     * Whether compilation was successful (no errors)
     */
    boolean success;
    
    /**
     * Get bytecode for a specific class
     */
    public byte[] getBytecode(String className) {
        return classBytesMap.get(className);
    }
    
    /**
     * Check if class was compiled
     */
    public boolean hasClass(String className) {
        return classBytesMap.containsKey(className);
    }
    
    /**
     * Get errors only
     */
    public List<CompilationDiagnostic> getErrors() {
        return diagnostics.stream()
                .filter(d -> d.getKind() == DiagnosticKind.ERROR)
                .toList();
    }
    
    /**
     * Get warnings only
     */
    public List<CompilationDiagnostic> getWarnings() {
        return diagnostics.stream()
                .filter(d -> d.getKind() == DiagnosticKind.WARNING)
                .toList();
    }
}

