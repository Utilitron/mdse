package io.mdse.generation.compiler;

import lombok.Builder;
import lombok.Value;

/**
 * Single compilation diagnostic (error, warning, or info)
 */
@Value
@Builder
public class CompilationDiagnostic {
    DiagnosticKind kind;
    String message;
    String sourceFile;
    long line;
    long column;
}

