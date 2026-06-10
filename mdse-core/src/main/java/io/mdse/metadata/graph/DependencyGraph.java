package io.mdse.metadata.graph;

import io.mdse.metadata.schema.ForeignKeySchema;
import io.mdse.metadata.schema.TableSchema;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A directed graph representing table dependencies based on foreign keys
 * Node A → Node B means table A depends on table B (A has a foreign key to B)
 */
@Getter
public final class DependencyGraph {
    
    private final Map<String, DependencyNode> nodes;
    private final Set<DependencyEdge> edges;
    
    private final Map<String, Set<DependencyEdge>> outgoing;
    private final Map<String, Set<DependencyEdge>> incoming;
    
    private final Set<ForeignKeySchema> unresolvedForeignKeys;
    
    private DependencyGraph(
            Map<String, DependencyNode> nodes,
            Set<DependencyEdge> edges, Set<ForeignKeySchema> unresolvedForeignKeys
    ) {
        this.nodes = Map.copyOf(nodes);
        this.edges = Set.copyOf(edges);
        
        this.outgoing = buildOutgoing(edges);
        this.incoming = buildIncoming(edges);
        this.unresolvedForeignKeys = Set.copyOf(unresolvedForeignKeys);;
    }
    
    public static DependencyGraph from(Collection<TableSchema> tables) {
        
        Map<String, DependencyNode> nodes = tables.stream()
                .map(DependencyNode::new)
                .collect(Collectors.toMap(
                        n -> n.table().getTableName(),
                        n -> n,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        
        Set<DependencyEdge> edges = new LinkedHashSet<>();
        Set<ForeignKeySchema> unresolved = new LinkedHashSet<>();
        
        for (TableSchema table : tables) {
            DependencyNode source = nodes.get(table.getTableName());
            for (ForeignKeySchema fk : table.getForeignKeys()) {
                DependencyNode target = nodes.get(fk.getReferencedTable());
                if (target == null) {
                    unresolved.add(fk);
                    continue;
                }
                
                edges.add(new DependencyEdge(
                        source,
                        target,
                        fk,
                        DependencyType.FOREIGN_KEY
                ));
            }
        }
        
        return new DependencyGraph(nodes, edges, unresolved);
    }
    
    public Collection<DependencyNode> nodes() {
        return nodes.values();
    }
    
    public Set<DependencyEdge> edges() {
        return edges;
    }
    
    public Optional<DependencyNode> node(String name) {
        return Optional.ofNullable(nodes.get(name));
    }
    
    public DependencyNode requireNode(String name) {
        return node(name).orElseThrow(() -> new NoSuchElementException("Node not found: " + name));
    }
    
    public boolean hasNode(String name) {
        return nodes.containsKey(name);
    }
    
    public int size() {
        return nodes.size();
    }
    
    public Set<DependencyEdge> outgoing(String node) {
        return outgoing.getOrDefault(node, Set.of());
    }
    
    public Set<DependencyEdge> incoming(String node) {
        return incoming.getOrDefault(node, Set.of());
    }
    
    public Set<DependencyNode> dependenciesOf(String node) {
        return outgoing(node).stream()
                .map(DependencyEdge::target)
                .collect(Collectors.toSet());
    }
    
    public Set<DependencyNode> dependentsOf(String node) {
        return incoming(node).stream()
                .map(DependencyEdge::source)
                .collect(Collectors.toSet());
    }
    
    private static Map<String, Set<DependencyEdge>> buildOutgoing(
            Set<DependencyEdge> edges
    ) {
        Map<String, Set<DependencyEdge>> map = new HashMap<>();
        
        for (DependencyEdge edge : edges) {
            map.computeIfAbsent(
                    edge.source().name(),
                    k -> new LinkedHashSet<>()
            ).add(edge);
        }
        
        return map;
    }
    
    private static Map<String, Set<DependencyEdge>> buildIncoming(
            Set<DependencyEdge> edges
    ) {
        Map<String, Set<DependencyEdge>> map = new HashMap<>();
        
        for (DependencyEdge edge : edges) {
            map.computeIfAbsent(
                    edge.target().name(),
                    k -> new LinkedHashSet<>()
            ).add(edge);
        }
        
        return map;
    }
}
