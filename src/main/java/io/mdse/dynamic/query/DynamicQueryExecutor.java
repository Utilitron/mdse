package io.mdse.dynamic.query;

import io.mdse.dynamic.model.DynamicRecord;
import io.mdse.dynamic.repository.DynamicRepository;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.service.CoercionService;
import io.mdse.metadata.service.impl.StandardCoercionService;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes complex queries with dynamic filters, sorting, and pagination.
 * Used alongside DynamicRepository for advanced search scenarios.
 */
@Slf4j
public class DynamicQueryExecutor {
    
    private final DataSource dataSource;
    private final SchemaRegistry schemaRegistry;
    private final DynamicRepository repository;
    private final CoercionService coercionService;
    
    /**
     * Constructor without CoercionService (for backward compatibility)
     */
    public DynamicQueryExecutor(DataSource dataSource,
                                SchemaRegistry schemaRegistry,
                                DynamicRepository repository) {
        this(dataSource, schemaRegistry, repository, new StandardCoercionService());
    }
    
    /**
     * Full constructor with CoercionService
     */
    public DynamicQueryExecutor(DataSource dataSource,
                                SchemaRegistry schemaRegistry,
                                DynamicRepository repository,
                                CoercionService coercionService) {
        this.dataSource = dataSource;
        this.schemaRegistry = schemaRegistry;
        this.repository = repository;
        this.coercionService = coercionService;
    }
    
    /**
     * Search a table using the given query specification.
     * Builds WHERE, ORDER BY, and LIMIT/OFFSET clauses dynamically.
     */
    public List<DynamicRecord> search(String tableName, QuerySpecification spec) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        StringBuilder sql = new StringBuilder("SELECT * FROM " + schema.getQualifiedName());
        List<Object> params = new ArrayList<>();
        
        // WHERE with type coercion
        if (!spec.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < spec.getFilters().size(); i++) {
                if (i > 0) sql.append(" AND ");
                QuerySpecification.Filter filter = spec.getFilters().get(i);
                sql.append(filter.field()).append(" ").append(filter.operator()).append(" ?");
                
                // Coerce filter value to appropriate type based on column schema
                ColumnSchema column = schema.getColumn(filter.field());
                Object coercedValue = coercionService.coerce(filter.value(), column);
                params.add(coercedValue);
            }
        }
        
        // ORDER BY
        if (!spec.getSorts().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < spec.getSorts().size(); i++) {
                if (i > 0) sql.append(", ");
                QuerySpecification.Sort sort = spec.getSorts().get(i);
                sql.append(sort.field()).append(" ").append(sort.direction().name());
            }
        }
        
        // Pagination (LIMIT / OFFSET)
        if (spec.getPage() != null && spec.getSize() != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(spec.getSize());
            params.add(spec.getPage() * spec.getSize());
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<DynamicRecord> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(repository.mapRow(schema, rs));
                }
                return results;
            }
        } catch (SQLException e) {
            log.error("Query failed: {}", sql, e);
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }
    
    /**
     * Count matching rows using the same filters (ignores sorting and pagination).
     */
    public long count(String tableName, QuerySpecification spec) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + schema.getQualifiedName());
        List<Object> params = new ArrayList<>();
        
        if (!spec.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < spec.getFilters().size(); i++) {
                if (i > 0) sql.append(" AND ");
                QuerySpecification.Filter filter = spec.getFilters().get(i);
                sql.append(filter.field()).append(" ").append(filter.operator()).append(" ?");
                
                // Coerce filter value
                ColumnSchema column = schema.getColumn(filter.field());
                Object coercedValue = coercionService.coerce(filter.value(), column);
                params.add(coercedValue);
            }
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet resultSet = stmt.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Count query failed: " + sql, e);
        }
    }
}
