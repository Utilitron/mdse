package io.mdse.metadata.graph;

import io.mdse.metadata.schema.ForeignKeySchema;
import io.mdse.metadata.schema.ReferentialAction;
import io.mdse.metadata.schema.RelationshipCardinality;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable directed relationship between two dependency nodes.
 * <p>
 * Source -> Target means:
 * Source depends on Target.
 */
public record DependencyEdge(DependencyNode source, DependencyNode target, ForeignKeySchema foreignKey, DependencyType type) {
    
    public DependencyEdge(
            DependencyNode source,
            DependencyNode target,
            ForeignKeySchema foreignKey,
            DependencyType type
    ) {
        
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.foreignKey = Objects.requireNonNull(foreignKey);
        this.type = Objects.requireNonNull(type);
    }
    
    /**
     * Source node.
     * <p>
     * The dependent table.
     */
    @Override
    public DependencyNode source() {
        return source;
    }
    
    /**
     * Target node.
     * <p>
     * The referenced table.
     */
    @Override
    public DependencyNode target() {
        return target;
    }
    
    /**
     * Relationship metadata.
     */
    @Override
    public ForeignKeySchema foreignKey() {
        return foreignKey;
    }
    
    /**
     * Dependency category.
     */
    @Override
    public DependencyType type() {
        return type;
    }
    
    /**
     * FK constraint name.
     */
    public String constraintName() {
        return foreignKey.getConstraintName();
    }
    
    /**
     * Source FK columns.
     */
    public List<String> sourceColumns() {
        return foreignKey.getSourceColumns();
    }
    
    /**
     * Referenced PK/UK columns.
     */
    public List<String> referencedColumns() {
        return foreignKey.getReferencedColumns();
    }
    
    /**
     * Cardinality semantics.
     */
    public RelationshipCardinality cardinality() {
        return foreignKey.getCardinality();
    }
    
    /**
     * True if delete cascades.
     */
    public boolean cascadesDelete() {
        return foreignKey.getDeleteRule() == ReferentialAction.CASCADE;
    }
    
    /**
     * True if updates cascade.
     */
    public boolean cascadesUpdate() {
        return foreignKey.getUpdateRule() == ReferentialAction.CASCADE;
    }
    
    /**
     * True if edge references same table.
     */
    public boolean selfReferencing() {
        return source.equals(target);
    }
    
    /**
     * True if edge is optional.
     */
    public boolean nullableRelationship() {
        return sourceColumns().stream()
                .map(source.table()::findColumn)
                .flatMap(Optional::stream)
                .anyMatch(column -> !column.isRequired());
    }
    
    @Override
    public String toString() {
        return source.name()
                + " -> "
                + target.name()
                + " ["
                + constraintName()
                + "]";
    }
    
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        
        if (!(object instanceof DependencyEdge other)) {
            return false;
        }
        
        return constraintName().equalsIgnoreCase(other.constraintName())
                && source.equals(other.source)
                && target.equals(other.target);
    }
    
    @Override
    public int hashCode() {
        
        return Objects.hash(
                source,
                target,
                constraintName().toLowerCase()
        );
    }
}

