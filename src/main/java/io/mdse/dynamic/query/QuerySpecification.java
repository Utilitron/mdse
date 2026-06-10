package io.mdse.dynamic.query;

import io.mdse.metadata.schema.SortDirection;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Query specification for dynamic search operations.
 * Holds filters, sorting, and pagination parameters.
 */
@Getter
@Setter
public class QuerySpecification {
    
    /**
     * List of filter conditions (combined with AND)
     */
    private final List<Filter> filters = new ArrayList<>();
    
    /**
     * List of sort orders
     */
    private final List<Sort> sorts = new ArrayList<>();
    
    /**
     * Page number (zero-based) for pagination
     */
    private Integer page;
    
    /**
     * Number of records per page
     */
    private Integer size;
    
    /**
     * A single filter condition: field + operator + value.
     * Example: new Filter("age", ">=", 18)
     */
    public record Filter(String field, String operator, Object value) {}
    
    /**
     * A sort instruction: field and direction (ASC/DESC).
     */
    public record Sort(String field, SortDirection direction) {}
    
}
