package io.mdse.generation.loader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated classloader for a single generation session.
 * Each generation cycle gets its own SessionClassLoader.
 *
 * This prevents LinkageError and metaspace leaks from repeated regeneration.
 *
 * CRITICAL: Never reuse a SessionClassLoader after regeneration.
 * Always create a new instance and discard the old one.
 */
public class SessionClassLoader extends ClassLoader {
    
    private final Map<String, byte[]> classBytecode;
    private final Map<String, Class<?>> loadedClasses;
    private final String sessionId;
    
    /**
     * Create a new session classloader
     *
     * @param sessionId unique identifier for this session
     * @param classBytecode map of class name to bytecode
     * @param parent parent classloader
     */
    public SessionClassLoader(
            String sessionId,
            Map<String, byte[]> classBytecode,
            ClassLoader parent) {
        super(parent);
        this.sessionId = sessionId;
        this.classBytecode = new ConcurrentHashMap<>(classBytecode);
        this.loadedClasses = new ConcurrentHashMap<>();
    }
    
    /**
     * Get session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Get all class names in this session
     */
    public java.util.Set<String> getClassNames() {
        return classBytecode.keySet();
    }
    
    /**
     * Check if this session contains a class
     */
    public boolean hasClass(String className) {
        return classBytecode.containsKey(className);
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Check if already loaded in this session
        Class<?> loaded = loadedClasses.get(name);
        if (loaded != null) {
            return loaded;
        }
        
        // Get bytecode
        byte[] bytecode = classBytecode.get(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(
                    "Class not found in session " + sessionId + ": " + name
            );
        }
        
        // Define class
        Class<?> clazz = defineClass(name, bytecode, 0, bytecode.length);
        loadedClasses.put(name, clazz);
        
        return clazz;
    }
    
    /**
     * Load all classes in this session eagerly.
     * Useful for validation and ensuring no compilation issues.
     */
    public void loadAllClasses() throws ClassNotFoundException {
        for (String className : classBytecode.keySet()) {
            loadClass(className);
        }
    }
    
    /**
     * Get statistics about this session
     */
    public SessionStats getStats() {
        return new SessionStats(
                sessionId,
                classBytecode.size(),
                loadedClasses.size(),
                calculateTotalBytecodeSize()
        );
    }
    
    private long calculateTotalBytecodeSize() {
        return classBytecode.values().stream()
                .mapToLong(bytes -> bytes.length)
                .sum();
    }
    
    @Override
    public String toString() {
        return String.format("SessionClassLoader[session=%s, classes=%d, loaded=%d]",
                sessionId, classBytecode.size(), loadedClasses.size());
    }
    
    /**
     * Statistics about a classloader session
     */
    public static class SessionStats {
        private final String sessionId;
        private final int totalClasses;
        private final int loadedClasses;
        private final long totalBytecodeSize;
        
        public SessionStats(String sessionId, int totalClasses,
                            int loadedClasses, long totalBytecodeSize) {
            this.sessionId = sessionId;
            this.totalClasses = totalClasses;
            this.loadedClasses = loadedClasses;
            this.totalBytecodeSize = totalBytecodeSize;
        }
        
        public String getSessionId() { return sessionId; }
        public int getTotalClasses() { return totalClasses; }
        public int getLoadedClasses() { return loadedClasses; }
        public long getTotalBytecodeSize() { return totalBytecodeSize; }
        
        @Override
        public String toString() {
            return String.format(
                    "SessionStats[session=%s, total=%d, loaded=%d, size=%d bytes]",
                    sessionId, totalClasses, loadedClasses, totalBytecodeSize
            );
        }
    }
}

