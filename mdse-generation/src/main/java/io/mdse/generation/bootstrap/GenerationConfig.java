package io.mdse.generation.bootstrap;

import io.mdse.generation.source.GenerationTarget;

/**
 * Configuration for code generation
 */
public class GenerationConfig {
    private final String packageName;
    private final GenerationTarget target;
    
    public GenerationConfig(String packageName, GenerationTarget target) {
        this.packageName = packageName;
        this.target = target;
    }
    
    public static GenerationConfig jpa(String packageName) {
        return new GenerationConfig(packageName, GenerationTarget.JPA);
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public GenerationTarget getTarget() {
        return target;
    }
}
