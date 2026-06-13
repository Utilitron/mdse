package io.mdse.generation.loader;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing generation session classloaders.
 * Uses weak references to allow garbage collection when sessions are no longer needed.
 *
 * This prevents memory leaks from abandoned sessions while still allowing
 * lookup of active sessions.
 */
public class ClassLoaderRegistry {
    
    private final Map<String, WeakReference<SessionClassLoader>> sessions =
            new ConcurrentHashMap<>();
    
    /**
     * Register a session classloader
     */
    public void register(SessionClassLoader classLoader) {
        sessions.put(classLoader.getSessionId(), new WeakReference<>(classLoader));
    }
    
    /**
     * Get a session classloader by ID
     */
    public Optional<SessionClassLoader> get(String sessionId) {
        WeakReference<SessionClassLoader> ref = sessions.get(sessionId);
        if (ref == null) {
            return Optional.empty();
        }
        
        SessionClassLoader loader = ref.get();
        if (loader == null) {
            // GC'd - remove from registry
            sessions.remove(sessionId);
            return Optional.empty();
        }
        
        return Optional.of(loader);
    }
    
    /**
     * Remove a session from the registry
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
    
    /**
     * Clear all sessions (for testing or shutdown)
     */
    public void clear() {
        sessions.clear();
    }
    
    /**
     * Get count of active sessions
     */
    public int getActiveSessionCount() {
        // Clean up GC'd references
        sessions.entrySet().removeIf(entry -> entry.getValue().get() == null);
        return sessions.size();
    }
    
    /**
     * Get all active session IDs
     */
    public java.util.Set<String> getActiveSessions() {
        // Clean up GC'd references
        sessions.entrySet().removeIf(entry -> entry.getValue().get() == null);
        return sessions.keySet();
    }
}

