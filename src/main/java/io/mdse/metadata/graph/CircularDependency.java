package io.mdse.metadata.graph;

import java.util.List;

/**
 * A circular dependency (cycle) found in the dependency graph.
 * The cycle is a list of table names.
 */
public record CircularDependency(List<DependencyEdge> edges) {
    
    public CircularDependency {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Cycle cannot be empty");
        }
    }
    
    public List<DependencyNode> nodes() {
        return edges.stream()
                .map(DependencyEdge::source)
                .distinct()
                .toList();
    }
    
    @Override
    public String toString() {
        return edges.stream()
                .map(e -> e.source().name())
                .reduce((a, b) -> a + " -> " + b)
                .orElse("");
    }
}

