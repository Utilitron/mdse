package io.mdse.metadata.graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides various analysis algorithms on an immutable {@link DependencyGraph}.
 * All methods are static and self-contained.
 */
public final class DependencyGraphAnalyzer {
    
    private DependencyGraphAnalyzer() {} // prevent instantiation
    
    /**
     * Topological sort using Kahn's algorithm.
     * @return list of node names in dependency order (roots first)
     * @throws IllegalStateException if the graph contains a cycle
     */
    public static List<String> topologicalSort(DependencyGraph graph) {
        Map<String, Integer> outDegree = new HashMap<>();
        for (DependencyNode node : graph.getNodes().values()) {
            outDegree.put(node.name(), graph.outgoing(node.name()).size());
        }
        
        Queue<String> zeroOutDegree = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : outDegree.entrySet()) {
            if (entry.getValue() == 0) zeroOutDegree.add(entry.getKey());
        }
        
        List<String> sorted = new ArrayList<>();
        while (!zeroOutDegree.isEmpty()) {
            String current = zeroOutDegree.poll();
            sorted.add(current);
            
            // For each node that depends on current (incoming edges)
            for (DependencyEdge edge : graph.incoming(current)) {
                String source = edge.source().name();
                int deg = outDegree.get(source) - 1;
                outDegree.put(source, deg);
                if (deg == 0) zeroOutDegree.add(source);
            }
        }
        
        if (sorted.size() != graph.getNodes().size()) {
            throw new IllegalStateException("Graph contains a cycle – topological sort not possible.");
        }
        return sorted;
    }
    
    /**
     * Detect all circular dependencies (cycles) in the graph.
     */
    public static List<CircularDependency> detectCircularDependencies(DependencyGraph graph) {
        List<CircularDependency> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> onStack = new LinkedHashSet<>();
        Map<String, String> parent = new HashMap<>();
        
        for (String node : graph.getNodes().keySet()) {
            if (!visited.contains(node)) {
                dfsCycleDetect(node, graph, visited, onStack, parent, cycles);
            }
        }
        return cycles;
    }
    
    private static void dfsCycleDetect(String current, DependencyGraph graph,
                                       Set<String> visited, Set<String> onStack,
                                       Map<String, String> parent,
                                       List<CircularDependency> cycles) {
        visited.add(current);
        onStack.add(current);
        
        for (DependencyEdge edge : graph.outgoing(current)) {
            String target = edge.target().name();
            parent.put(target, current);
            if (!visited.contains(target)) {
                dfsCycleDetect(target, graph, visited, onStack, parent, cycles);
            } else if (onStack.contains(target)) {
                // Reconstruct cycle from parent pointers
                List<DependencyNode> cycleNodes = new ArrayList<>();
                String node = current;
                while (!node.equals(target)) {
                    cycleNodes.add(graph.getNodes().get(node));
                    node = parent.get(node);
                }
                cycleNodes.add(graph.getNodes().get(target));
                Collections.reverse(cycleNodes);
                
                // Collect edges that form the cycle
                List<DependencyEdge> cycleEdges = new ArrayList<>();
                for (int i = 0; i < cycleNodes.size(); i++) {
                    DependencyNode from = cycleNodes.get(i);
                    DependencyNode to = cycleNodes.get((i + 1) % cycleNodes.size());
                    graph.outgoing(from.name()).stream()
                            .filter(e -> e.target().equals(to))
                            .findFirst()
                            .ifPresent(cycleEdges::add);
                }
                cycles.add(new CircularDependency(cycleEdges));
            }
        }
        onStack.remove(current);
    }
    
    /**
     * Calculate the dependency depth of each node.
     * Depth = length of the longest path from any root (no dependencies) to that node.
     */
    public static Map<String, Integer> calculateDependencyDepth(DependencyGraph graph) {
        Map<String, Integer> depths = new HashMap<>();
        Set<String> roots = findRootTables(graph);
        
        Queue<String> queue = new ArrayDeque<>();
        for (String root : roots) {
            depths.put(root, 0);
            queue.add(root);
        }
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);
            for (DependencyEdge edge : graph.incoming(current)) {  // CHANGED from outgoing
                String source = edge.source().name();
                int newDepth = currentDepth + 1;
                if (!depths.containsKey(source) || newDepth > depths.get(source)) {
                    depths.put(source, newDepth);
                    queue.add(source);
                }
            }
        }
        
        for (String node : graph.getNodes().keySet()) {
            depths.putIfAbsent(node, 0);
        }
        return depths;
    }
    
    /**
     * Find all tables that the given table depends on (directly or transitively).
     */
    public static Set<String> findAllDependencies(DependencyGraph graph, String tableName) {
        Set<String> visited = new LinkedHashSet<>();
        dfsDependencies(graph, tableName, visited);
        visited.remove(tableName);
        return visited;
    }
    
    private static void dfsDependencies(DependencyGraph graph, String current, Set<String> visited) {
        if (!visited.add(current)) return;
        for (DependencyEdge edge : graph.outgoing(current)) {
            dfsDependencies(graph, edge.target().name(), visited);
        }
    }
    
    /**
     * Find all tables that depend on the given table (directly or transitively).
     */
    public static Set<String> findAllDependents(DependencyGraph graph, String tableName) {
        Set<String> visited = new LinkedHashSet<>();
        dfsDependents(graph, tableName, visited);
        visited.remove(tableName);
        return visited;
    }
    
    private static void dfsDependents(DependencyGraph graph, String current, Set<String> visited) {
        if (!visited.add(current)) return;
        for (DependencyEdge edge : graph.incoming(current)) {
            dfsDependents(graph, edge.source().name(), visited);
        }
    }
    
    /**
     * Tables that have no dependencies (roots).
     */
    public static Set<String> findRootTables(DependencyGraph graph) {
        return graph.getNodes().values().stream()
                .map(DependencyNode::name)
                .filter(name -> graph.outgoing(name).isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    
    /**
     * Tables that have no dependents (leaves).
     */
    public static Set<String> findLeafTables(DependencyGraph graph) {
        return graph.getNodes().values().stream()
                .map(DependencyNode::name)
                .filter(name -> graph.incoming(name).isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    
    /**
     * Safe deletion order: reverse topological sort.
     */
    public static List<String> calculateDeletionOrder(DependencyGraph graph) {
        List<String> order = new ArrayList<>(topologicalSort(graph));
        Collections.reverse(order);
        return order;
    }
    
    /**
     * Safe creation order: topological sort.
     */
    public static List<String> calculateCreationOrder(DependencyGraph graph) {
        return topologicalSort(graph);
    }
    
    /**
     * Analyze impact of modifying a table: all tables that directly or transitively depend on it.
     */
    public static Set<String> analyzeImpact(DependencyGraph graph, String tableName) {
        return findAllDependents(graph, tableName);
    }
    
    /**
     * Find strongly connected components using Tarjan's algorithm.
     * Returns a list of sets of DependencyNode.
     */
    public static List<Set<DependencyNode>> findStronglyConnectedComponents(DependencyGraph graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Map<String, Boolean> onStack = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        List<Set<DependencyNode>> sccs = new ArrayList<>();
        int[] currentIndex = {0};
        
        for (String node : graph.getNodes().keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, lowLink, onStack, stack, sccs, currentIndex);
            }
        }
        return sccs;
    }
    
    private static void strongConnect(String v, DependencyGraph graph,
                                      Map<String, Integer> index, Map<String, Integer> lowLink,
                                      Map<String, Boolean> onStack, Deque<String> stack,
                                      List<Set<DependencyNode>> sccs, int[] currentIndex) {
        index.put(v, currentIndex[0]);
        lowLink.put(v, currentIndex[0]);
        currentIndex[0]++;
        stack.push(v);
        onStack.put(v, true);
        
        for (DependencyEdge edge : graph.outgoing(v)) {
            String w = edge.target().name();
            if (!index.containsKey(w)) {
                strongConnect(w, graph, index, lowLink, onStack, stack, sccs, currentIndex);
                lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
            } else if (onStack.getOrDefault(w, false)) {
                lowLink.put(v, Math.min(lowLink.get(v), index.get(w)));
            }
        }
        
        if (lowLink.get(v).equals(index.get(v))) {
            Set<DependencyNode> scc = new LinkedHashSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.put(w, false);
                scc.add(graph.getNodes().get(w));
            } while (!w.equals(v));
            sccs.add(scc);
        }
    }
    
    /**
     * Validate that all dependency references point to existing nodes in the graph.
     */
    public static boolean validateGraph(DependencyGraph graph) {
        // Check all edges reference existing nodes
        for (DependencyEdge edge : graph.getEdges()) {
            if (!graph.getNodes().containsKey(edge.source().name()) ||
                !graph.getNodes().containsKey(edge.target().name())) {
                return false;
            }
        }
        // Check there are no unresolved foreign keys
        return graph.getUnresolvedForeignKeys().isEmpty();
    }
}
