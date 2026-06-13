package io.mdse.generation.exception;

import io.mdse.generation.compiler.CompilationDiagnostic;
import io.mdse.generation.compiler.DiagnosticKind;

import java.util.List;

/**
 * Exception thrown when compilation fails
 */
public class CompilationException extends Exception {
    
    private final List<CompilationDiagnostic> diagnostics;
    
    public CompilationException(String message) {
        super(message);
        this.diagnostics = List.of();
    }
    
    public CompilationException(String message, List<CompilationDiagnostic> diagnostics) {
        super(message + "\n" + formatDiagnostics(diagnostics));
        this.diagnostics = diagnostics;
    }
    
    public CompilationException(String message, Throwable cause) {
        super(message, cause);
        this.diagnostics = List.of();
    }
    
    public List<CompilationDiagnostic> getDiagnostics() {
        return diagnostics;
    }
    
    private static String formatDiagnostics(List<CompilationDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (CompilationDiagnostic diag : diagnostics) {
            if (diag.getKind() == DiagnosticKind.ERROR) {
                sb.append("\n  ").append(diag.getKind()).append(": ");
                sb.append(diag.getMessage());
                if (diag.getSourceFile() != null) {
                    sb.append(" (").append(diag.getSourceFile());
                    if (diag.getLine() > 0) {
                        sb.append(":").append(diag.getLine());
                    }
                    sb.append(")");
                }
            }
        }
        return sb.toString();
    }
}

