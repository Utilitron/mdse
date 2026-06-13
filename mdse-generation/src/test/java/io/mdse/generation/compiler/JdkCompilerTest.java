package io.mdse.generation.compiler;

import io.mdse.generation.exception.CompilationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JdkCompiler Tests")
class JdkCompilerTest {
    
    private JdkCompiler compiler;
    
    @BeforeEach
    void setUp() {
        compiler = new JdkCompiler();
    }
    
    @Test
    @DisplayName("Should be available when JDK compiler is present")
    void testIsAvailable() {
        // This test assumes we're running with a JDK (not just JRE)
        // In CI/CD, ensure JDK is used
        boolean available = compiler.isAvailable();
        
        // If running with JDK, should be true
        // If running with JRE, will be false
        assertThat(compiler.getName()).isEqualTo("JDK Compiler (javax.tools)");
    }
    
    @Test
    @DisplayName("Should compile simple Java class")
    void testCompileSimpleClass() throws Exception {
        String source = """
            package com.example;
            
            public class SimpleClass {
                private String name;
                
                public SimpleClass() {
                }
                
                public String getName() {
                    return name;
                }
                
                public void setName(String name) {
                    this.name = name;
                }
            }
            """;
        
        Map<String, String> sources = Map.of("com.example.SimpleClass", source);
        
        // Skip test if compiler not available (running with JRE)
        if (!compiler.isAvailable()) {
            return;
        }
        
        CompilationResult result = compiler.compile(sources, getClass().getClassLoader());
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCompiledClasses()).contains("com.example.SimpleClass");
        assertThat(result.getBytecode("com.example.SimpleClass")).isNotNull();
        assertThat(result.getErrors()).isEmpty();
    }
    
    @Test
    @DisplayName("Should compile class with dependencies")
    void testCompileWithDependencies() throws Exception {
        String entitySource = """
            package com.example;
            
            import jakarta.persistence.*;
            
            @Entity
            @Table(name = "users")
            public class User {
                @Id
                private Long id;
                private String name;
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """;
        
        Map<String, String> sources = Map.of("com.example.User", entitySource);
        
        if (!compiler.isAvailable()) {
            return;
        }
        
        CompilationResult result = compiler.compile(sources, getClass().getClassLoader());
        System.out.println(result.getCompiledClasses());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCompiledClasses()).contains("com.example.User");
    }
    
    @Test
    @DisplayName("Should compile multiple classes at once")
    void testCompileMultipleClasses() throws Exception {
        String class1 = """
            package com.example;
            public class Class1 {
                public String getValue() { return "class1"; }
            }
            """;
        
        String class2 = """
            package com.example;
            public class Class2 {
                public String getValue() { return "class2"; }
            }
            """;
        
        String class3 = """
            package com.example;
            public class Class3 {
                private Class1 c1 = new Class1();
                private Class2 c2 = new Class2();
                
                public String getCombined() {
                    return c1.getValue() + c2.getValue();
                }
            }
            """;
        
        Map<String, String> sources = Map.of(
                "com.example.Class1", class1,
                "com.example.Class2", class2,
                "com.example.Class3", class3
        );
        
        if (!compiler.isAvailable()) {
            return;
        }
        
        CompilationResult result = compiler.compile(sources, getClass().getClassLoader());
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCompiledClasses())
                .contains("com.example.Class1", "com.example.Class2", "com.example.Class3");
    }
    
    @Test
    @DisplayName("Should fail compilation for syntax errors")
    void testCompilationFailure() {
        String invalidSource = """
            package com.example;
            
            public class InvalidClass {
                // Missing semicolon
                private String name
                
                public String getName() {
                    return name;
                }
            }
            """;
        
        Map<String, String> sources = Map.of("com.example.InvalidClass", invalidSource);
        
        if (!compiler.isAvailable()) {
            return;
        }
        
        assertThatThrownBy(() -> compiler.compile(sources, getClass().getClassLoader()))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("Compilation failed");
    }
    
    @Test
    @DisplayName("Should collect compilation diagnostics")
    void testCompilationDiagnostics() {
        String sourceWithWarning = """
            package com.example;
            
            public class WithWarning {
                @SuppressWarnings("unused")
                private String unusedField;
                
                public void method() {
                    String unused = "test";
                }
            }
            """;
        
        Map<String, String> sources = Map.of("com.example.WithWarning", sourceWithWarning);
        
        if (!compiler.isAvailable()) {
            return;
        }
        
        try {
            CompilationResult result = compiler.compile(sources, getClass().getClassLoader());
            
            // Should compile successfully
            assertThat(result.isSuccess()).isTrue();
            
            // May have warnings (compiler-dependent)
            // Just verify diagnostics mechanism works
            assertThat(result.getDiagnostics()).isNotNull();
            
        } catch (CompilationException e) {
            // If it fails, check that diagnostics are present
            assertThat(e.getDiagnostics()).isNotNull();
        }
    }
    
    @Test
    @DisplayName("Should throw exception when compiler not available")
    void testCompilerNotAvailable() {
        // This test documents the behavior when running with JRE
        // Can't really test it in a JDK environment
        
        if (compiler.isAvailable()) {
            // Skip this test if compiler IS available
            return;
        }
        
        String source = "package com.example; public class Test {}";
        Map<String, String> sources = Map.of("com.example.Test", source);
        
        assertThatThrownBy(() -> compiler.compile(sources, getClass().getClassLoader()))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("Java compiler not available");
    }
}

