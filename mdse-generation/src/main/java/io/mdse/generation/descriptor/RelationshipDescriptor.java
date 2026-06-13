package io.mdse.generation.descriptor;

import io.mdse.metadata.schema.ForeignKeySchema;
import io.mdse.metadata.schema.ReferentialAction;
import io.mdse.metadata.schema.RelationshipCardinality;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * Generator-neutral relationship descriptor.
 * Represents a foreign key relationship for code generation.
 */
@Value
@Builder(toBuilder = true)
public class RelationshipDescriptor {
    
    /**
     * Java field name for this relationship
     * Example: "orders", "customer", "addresses"
     */
    String name;
    
    /**
     * Target entity simple class name
     * Example: "Order", "Customer"
     */
    String targetEntity;
    
    /**
     * Fully qualified target entity class name
     * Example: "io.mdse.generated.entities.Order"
     */
    String targetEntityFqn;
    
    /**
     * Relationship cardinality
     */
    RelationshipCardinality cardinality;
    
    /**
     * Whether this relationship is bidirectional
     */
    boolean bidirectional;
    
    /**
     * Field name on the inverse side (for bidirectional)
     * Example: if Order has "customer", Customer's inverse is "orders"
     */
    String mappedBy;
    
    /**
     * Join column names (source side)
     */
    @Singular
    List<String> joinColumns;
    
    /**
     * Referenced column names (target side)
     */
    @Singular
    List<String> referencedColumns;
    
    /**
     * ON DELETE action
     */
    ReferentialAction deleteAction;
    
    /**
     * ON UPDATE action
     */
    ReferentialAction updateAction;
    
    /**
     * Whether this is the owning side of the relationship
     */
    boolean owningSide;
    
    /**
     * Source foreign key schema
     */
    ForeignKeySchema sourceForeignKey;
    
    /**
     * Check if this is a many-to-one relationship
     */
    public boolean isManyToOne() {
        return cardinality == RelationshipCardinality.MANY_TO_ONE;
    }
    
    /**
     * Check if this is a one-to-many relationship
     */
    public boolean isOneToMany() {
        return cardinality == RelationshipCardinality.ONE_TO_MANY;
    }
    
    /**
     * Check if this is a one-to-one relationship
     */
    public boolean isOneToOne() {
        return cardinality == RelationshipCardinality.ONE_TO_ONE;
    }
    
    /**
     * Check if this is a many-to-many relationship
     */
    public boolean isManyToMany() {
        return cardinality == RelationshipCardinality.MANY_TO_MANY;
    }
    
    /**
     * Get collection type for *-to-many relationships
     */
    public String getCollectionType() {
        if (isOneToMany() || isManyToMany()) {
            return "java.util.List";
        }
        return null;
    }
    
    /**
     * Get generic type declaration for collections
     * Example: "List<Order>"
     */
    public String getGenericDeclaration() {
        if (isOneToMany() || isManyToMany()) {
            return "List<" + targetEntity + ">";
        }
        return targetEntity;
    }
}


