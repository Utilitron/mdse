package io.mdse.generation.compiler;

import io.mdse.generation.exception.CompilationException;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;

/**
 * Compilation provider using JDK's javax.tools.JavaCompiler.
 * Requires full JDK (not JRE).
 *
 * Fast and reliable for production use.
 */
public class JdkCompiler implements CompilationProvider {
    
    @Override
    public CompilationResult compile(Map<String, String> sources, ClassLoader parentClassLoader)
            throws CompilationException {
        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompilationException(
                    "Java compiler not available. " +
                            "This requires a full JDK installation, not just a JRE."
            );
        }
        
        // In-memory file manager
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(null, null, null)
        );
        
        // Create compilation units from sources
        List<JavaFileObject> compilationUnits = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            compilationUnits.add(new SourceFileObject(entry.getKey(), entry.getValue()));
        }
        
        // Compilation options
        List<String> options = Arrays.asList(
                "-g",           // Generate debug info
                "-parameters"   // Preserve parameter names
        );
        
        // Diagnostic collector
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        
        // Compile
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,               // Writer for additional output
                fileManager,        // File manager
                diagnostics,        // Diagnostic listener
                options,            // Compiler options
                null,               // Classes for annotation processing
                compilationUnits    // Compilation units
        );
        
        boolean success = task.call();
        
        // Build result
        CompilationResult.CompilationResultBuilder result = CompilationResult.builder()
                .success(success);
        
        // Add compiled bytecode
        for (Map.Entry<String, byte[]> entry : fileManager.getCompiledClasses().entrySet()) {
            result.classBytes(entry.getKey(), entry.getValue());
            result.compiledClass(entry.getKey());
        }
        
        // Add diagnostics
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            result.diagnostic(CompilationDiagnostic.builder()
                    .kind(mapDiagnosticKind(diagnostic.getKind()))
                    .message(diagnostic.getMessage(null))
                    .sourceFile(diagnostic.getSource() != null
                            ? diagnostic.getSource().getName()
                            : null)
                    .line(diagnostic.getLineNumber())
                    .column(diagnostic.getColumnNumber())
                    .build());
        }
        
        CompilationResult compilationResult = result.build();
        
        if (!success) {
            throw new CompilationException(
                    "Compilation failed with " + compilationResult.getErrors().size() + " error(s)",
                    compilationResult.getDiagnostics()
            );
        }
        
        return compilationResult;
    }
    
    @Override
    public boolean isAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }
    
    @Override
    public String getName() {
        return "JDK Compiler (javax.tools)";
    }
    
    private DiagnosticKind mapDiagnosticKind(Diagnostic.Kind kind) {
        return switch (kind) {
            case ERROR -> DiagnosticKind.ERROR;
            case WARNING -> DiagnosticKind.WARNING;
            case MANDATORY_WARNING -> DiagnosticKind.MANDATORY_WARNING;
            case NOTE -> DiagnosticKind.NOTE;
            case OTHER -> DiagnosticKind.OTHER;
        };
    }
    
    /**
     * In-memory Java source file
     */
    private static class SourceFileObject extends SimpleJavaFileObject {
        private final String source;
        
        public SourceFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + ".java"),
                    Kind.SOURCE);
            this.source = source;
        }
        
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
    
    /**
     * In-memory file manager that stores compiled classes in memory
     */
    private static class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        
        private final Map<String, byte[]> compiledClasses = new HashMap<>();
        
        public InMemoryFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }
        
        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) throws IOException {
            
            if (kind == JavaFileObject.Kind.CLASS) {
                return new ClassFileObject(className, compiledClasses);
            }
            
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }
        
        @Override
        public Iterable<Set<Location>> listLocationsForModules(Location location) throws IOException {
            try {
                return fileManager.listLocationsForModules(location);
            } catch (UnsupportedOperationException e) {
                return Collections.emptyList();
            }
        }
        
        @Override
        public Location getLocationForModule(Location location, String moduleName) throws IOException {
            try {
                return fileManager.getLocationForModule(location, moduleName);
            } catch (UnsupportedOperationException e) {
                return null;
            }
        }
        
        @Override
        public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
            try {
                return fileManager.getLocationForModule(location, fo);
            } catch (UnsupportedOperationException e) {
                return null;
            }
        }
        
        @Override
        public String inferModuleName(Location location) throws IOException {
            try {
                return fileManager.inferModuleName(location);
            } catch (UnsupportedOperationException e) {
                return null;
            }
        }
        
        @Override
        public boolean contains(Location location, FileObject fo) throws IOException {
            try {
                return fileManager.contains(location, fo);
            } catch (UnsupportedOperationException e) {
                return false;
            }
        }
        
        public Map<String, byte[]> getCompiledClasses() {
            return compiledClasses;
        }
    }
    
    /**
     * In-memory class file output
     */
    private static class ClassFileObject extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, byte[]> compiledClasses;
        private ByteArrayOutputStream outputStream;
        
        public ClassFileObject(String className, Map<String, byte[]> compiledClasses) {
            super(URI.create("bytes:///" + className.replace('.', '/') + ".class"),
                    Kind.CLASS);
            this.className = className;
            this.compiledClasses = compiledClasses;
        }
        
        @Override
        public OutputStream openOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    compiledClasses.put(className, toByteArray());
                }
            };
        }
    }
    
    /**
     * Forwarding file manager to extend standard file manager
     */
    private static class ForwardingJavaFileManager<M extends JavaFileManager>
            implements JavaFileManager {
        
        protected final M fileManager;
        
        public ForwardingJavaFileManager(M fileManager) {
            this.fileManager = fileManager;
        }
        
        @Override
        public ClassLoader getClassLoader(Location location) {
            return fileManager.getClassLoader(location);
        }
        
        @Override
        public Iterable<JavaFileObject> list(
                Location location,
                String packageName,
                Set<JavaFileObject.Kind> kinds,
                boolean recurse) throws IOException {
            return fileManager.list(location, packageName, kinds, recurse);
        }
        
        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            return fileManager.inferBinaryName(location, file);
        }
        
        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            return fileManager.isSameFile(a, b);
        }
        
        @Override
        public boolean handleOption(String current, Iterator<String> remaining) {
            return fileManager.handleOption(current, remaining);
        }
        
        @Override
        public boolean hasLocation(Location location) {
            return fileManager.hasLocation(location);
        }
        
        @Override
        public JavaFileObject getJavaFileForInput(
                Location location,
                String className,
                JavaFileObject.Kind kind) throws IOException {
            return fileManager.getJavaFileForInput(location, className, kind);
        }
        
        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) throws IOException {
            return fileManager.getJavaFileForOutput(location, className, kind, sibling);
        }
        
        @Override
        public FileObject getFileForInput(
                Location location,
                String packageName,
                String relativeName) throws IOException {
            return fileManager.getFileForInput(location, packageName, relativeName);
        }
        
        @Override
        public FileObject getFileForOutput(
                Location location,
                String packageName,
                String relativeName,
                FileObject sibling) throws IOException {
            return fileManager.getFileForOutput(location, packageName, relativeName, sibling);
        }
        
        @Override
        public void flush() throws IOException {
            fileManager.flush();
        }
        
        @Override
        public void close() throws IOException {
            fileManager.close();
        }
        
        @Override
        public int isSupportedOption(String option) {
            return fileManager.isSupportedOption(option);
        }
    }
}

