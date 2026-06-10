package io.mdse.dynamic.repository;

import io.mdse.dynamic.exception.RecordNotFoundException;
import io.mdse.dynamic.exception.RepositoryException;
import io.mdse.dynamic.model.DynamicRecord;
import io.mdse.metadata.exception.ValidationException;
import io.mdse.metadata.registry.SchemaRegistry;
import io.mdse.metadata.schema.ColumnSchema;
import io.mdse.metadata.schema.TableSchema;
import io.mdse.metadata.service.CoercionService;
import io.mdse.metadata.service.FormatterService;
import io.mdse.metadata.service.ValidationService;

import io.mdse.metadata.validation.ValidationResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.sql.*;
import java.util.*;

/**
 * Dynamic repository that works without concrete entity classes.
 * Uses immutable {@link TableSchema} and pure {@link DynamicRecord}.
 *
 * All database interactions are metadata-driven.
 */
@Slf4j
public class DynamicRepository {
    
    private final DataSource dataSource;
    private final SchemaRegistry schemaRegistry;
    
    @Getter
    private final CoercionService coercionService;
    @Getter
    private final ValidationService validationService;
    @Getter
    private final FormatterService formatterService;

    /**
     * Full constructor with all service dependencies
     */
    public DynamicRepository(
            DataSource dataSource,
            SchemaRegistry schemaRegistry,
            CoercionService coercionService,
            ValidationService validationService,
            FormatterService formatterService) {
        this.dataSource = dataSource;
        this.schemaRegistry = schemaRegistry;
        this.coercionService = coercionService;
        this.validationService = validationService;
        this.formatterService = formatterService;
    }

    /**
     * Convert current row of a ResultSet into a DynamicRecord
     */
    public DynamicRecord mapRow(TableSchema schema, ResultSet rs) throws SQLException {
        Map<String, Object> values = new HashMap<>();
        for (ColumnSchema column : schema.getColumns()) {
            String columnName = column.getColumnName();
            try {
                Object rawValue = rs.getObject(columnName);
                
                // Coerce to expected Java type
                Object coercedValue = coercionService.coerce(rawValue, column);
                values.put(columnName, coercedValue);
            } catch (SQLException e) {
                // Column not present in result set – skip
                log.debug("Column {} not found in result set for table {}", columnName, schema.getTableName());
            }
        }
        return DynamicRecord.builder()
                .tableName(schema.getTableName())
                .schemaName(schema.getSchemaName())
                .values(values)
                .tableSchema(schema)
                .build();
    }

    private DynamicRecord mapResultSetToRecord(ResultSet rs, TableSchema schema) throws SQLException {
        return mapRow(schema, rs);
    }

    private List<DynamicRecord> mapResultSetToRecords(ResultSet rs, TableSchema schema) throws SQLException {
        List<DynamicRecord> records = new ArrayList<>();
        while (rs.next()) {
            records.add(mapResultSetToRecord(rs, schema));
        }
        return records;
    }

    /**
     * Set parameters with type coercion for safety
     */
    private void setParameters(PreparedStatement stmt, List<Object> params, List<ColumnSchema> columns)
            throws SQLException {
        if (params.size() != columns.size()) {
            throw new IllegalArgumentException(
                "Parameter count mismatch: " + params.size() + " params, " + columns.size() + " columns");
        }
        
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            ColumnSchema column = columns.get(i);
            
            // Coerce to correct type before setting
            Object coercedValue = coercionService.coerce(value, column);
            stmt.setObject(i + 1, coercedValue);
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Find record by primary key
     */
    public Optional<DynamicRecord> findById(String tableName, Object id) {
        TableSchema schema = schemaRegistry.getRequired(tableName);

        if (!schema.hasPrimaryKey()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key");
        }
        if (schema.hasCompositePrimaryKey()) {
            throw new IllegalArgumentException("Use findById(tableName, Map<String, Object>) for composite keys");
        }

        String pkColumn = schema.getPrimaryKey().getColumnNames().get(0);
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", schema.getQualifiedName(), pkColumn);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Coerce ID to correct type
            ColumnSchema pkColumnSchema = schema.getColumn(pkColumn);
            Object coercedId = coercionService.coerce(id, pkColumnSchema);
            
            stmt.setObject(1, coercedId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRecord(rs, schema));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find record by id: " + id, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find record by composite primary key
     */
    public Optional<DynamicRecord> findById(String tableName, Map<String, Object> pkValues) {
        TableSchema schema = schemaRegistry.getRequired(tableName);

        if (!schema.hasPrimaryKey()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key");
        }
        
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<ColumnSchema> columns = new ArrayList<>();
        
        for (String pkColumn : schema.getPrimaryKey().getColumnNames()) {
            if (!pkValues.containsKey(pkColumn)) {
                throw new IllegalArgumentException("Missing primary key value for column: " + pkColumn);
            }
            whereClauses.add(pkColumn + " = ?");
            params.add(pkValues.get(pkColumn));
            
            ColumnSchema column = schema.getColumn(pkColumn);
            columns.add(column);
        }

        String sql = String.format("SELECT * FROM %s WHERE %s", schema.getQualifiedName(), String.join(" AND ", whereClauses));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params, columns);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRecord(rs, schema));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find record by composite key", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find all records in a table
     */
    public List<DynamicRecord> findAll(String tableName) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        String sql = "SELECT * FROM " + schema.getQualifiedName();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return mapResultSetToRecords(rs, schema);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all records", e);
        }
    }
    
    /**
     * Find records with pagination
     */
    public List<DynamicRecord> findAll(String tableName, int page, int size) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        int offset = page * size;
        // SQL limit/offset syntax – works for H2, PostgreSQL, MySQL, etc.
        String sql = String.format("SELECT * FROM %s LIMIT ? OFFSET ?", schema.getQualifiedName());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, size);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSetToRecords(rs, schema);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find records with pagination", e);
        }
    }
    
    /**
     * Find records where a single column equals a value
     */
    public List<DynamicRecord> findByColumn(String tableName, String columnName, Object value) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        ColumnSchema column = schema.getColumn(columnName);
        
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", schema.getQualifiedName(), columnName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Coerce value to correct type
            Object coercedValue = coercionService.coerce(value, column);
            stmt.setObject(1, coercedValue);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSetToRecords(rs, schema);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find records by column", e);
        }
    }
    
    /**
     * Find records matching multiple column criteria
     */
    public List<DynamicRecord> findByColumns(String tableName, Map<String, Object> criteria) {
        if (criteria.isEmpty()) {
            return findAll(tableName);
        }
        
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<ColumnSchema> columns = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : criteria.entrySet()) {
            String columnName = entry.getKey();
            whereClauses.add(columnName + " = ?");
            params.add(entry.getValue());
            
            ColumnSchema column = schema.getColumn(columnName);
            columns.add(column);
        }

        String sql = String.format("SELECT * FROM %s WHERE %s", schema.getQualifiedName(), String.join(" AND ", whereClauses));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params, columns);
            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSetToRecords(rs, schema);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find records by columns", e);
        }
    }
    
    /**
     * Count total records in a table
     */
    public long count(String tableName) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        String sql = "SELECT COUNT(*) FROM " + schema.getQualifiedName();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to count records", e);
        }
    }
    
    /**
     * Count records matching criteria
     */
    public long count(String tableName, Map<String, Object> criteria) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        if (criteria.isEmpty()) {
            return count(tableName);
        }
        
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : criteria.entrySet()) {
            whereClauses.add(entry.getKey() + " = ?");
            params.add(entry.getValue());
        }

        String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s", schema.getQualifiedName(), String.join(" AND ", whereClauses));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to count records with criteria", e);
        }
    }
    
    /**
     * Check if a record exists by primary key
     */
    public boolean exists(String tableName, Object id) {
        return findById(tableName, id).isPresent();
    }
    
    /**
     * Insert a new record (pure dynamic, from a DynamicRecord)
     */
    public DynamicRecord insert(String tableName, DynamicRecord record) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        // Validate record before insert
        ValidationResult validationResult = validationService.validateRecord(record.getValues(), schema);
        if (!validationResult.isValid()) {
            throw new ValidationException("Validation failed for table " + tableName + ": " + validationResult.getErrors());
        }
        
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<ColumnSchema> columnSchemas = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : record.getValues().entrySet()) {
            String columnName = entry.getKey();
            
            // Skip auto-generated columns
            ColumnSchema columnSchema = schema.getColumn(columnName);
            if (columnSchema != null && columnSchema.isAutoIncrement()) {
                continue;
            }
            
            columns.add(columnName);
            placeholders.add("?");
            values.add(entry.getValue());
            if (columnSchema != null) {
                columnSchemas.add(columnSchema);
            }
        }
        
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No columns to insert for table " + tableName);
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                schema.getQualifiedName(),
                String.join(", ", columns),
                String.join(", ", placeholders));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setParameters(stmt, values, columnSchemas);
            stmt.executeUpdate();
            
            // Handle auto-generated keys
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next() && schema.hasPrimaryKey()) {
                    List<String> pkColumns = schema.getPrimaryKey().getColumnNames();
                    if (pkColumns.size() == 1) {
                        String pkColumn = pkColumns.get(0);
                        Object generatedId = generatedKeys.getObject(1);
                        record.setValue(pkColumn, generatedId);
                    }
                }
            }
            
            record.resetChangeTracking();
            
            log.debug("Inserted record into {}: {}", tableName, record.getPrimaryKeyValues());
            
            return record;
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to insert record", e);
        }
    }
    
    /**
     * Insert a record from a map of values
     */
    public DynamicRecord insert(String tableName, Map<String, Object> values) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        DynamicRecord record = DynamicRecord.builder()
                .tableName(tableName)
                .schemaName(schema.getSchemaName())
                .values(new HashMap<>(values))
                .tableSchema(schema)
                .build();
        
        return insert(tableName, record);
    }
    
    /**
     * Update a record by simple primary key (full update)
     */
    public DynamicRecord update(String tableName, Object id, DynamicRecord record) throws ValidationException {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        if (!schema.hasPrimaryKey()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key");
        }
        
        if (schema.hasCompositePrimaryKey()) {
            throw new IllegalArgumentException("Use update(tableName, Map<String, Object>, DynamicRecord) for composite keys");
        }

        // Validate record before update
        ValidationResult validationResult = validationService.validateRecord(record.getValues(), schema);
        if (!validationResult.isValid()) {
            throw new ValidationException("Validation failed for table " + tableName + ": " + validationResult.getErrors());
        }

        String pkColumn = schema.getPrimaryKey().getColumnNames().get(0);
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<ColumnSchema> columns = new ArrayList<>();

        for (Map.Entry<String, Object> entry : record.getValues().entrySet()) {
            String column = entry.getKey();
            if (column.equals(pkColumn)) continue; // skip PK
            
            setClauses.add(column + " = ?");
            params.add(entry.getValue());
            
            ColumnSchema columnSchema = schema.getColumn(column);
            columns.add(columnSchema);
        }
        
        if (setClauses.isEmpty()) {
            log.warn("No columns to update for table {}", tableName);
            return record;
        }

        ColumnSchema pkColumnSchema = schema.getColumn(pkColumn);
        columns.add(pkColumnSchema);
        params.add(id);

        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                schema.getQualifiedName(),
                String.join(", ", setClauses),
                pkColumn);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params, columns);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RecordNotFoundException("No record found with id " + id + " in table " + tableName);
            }

            record.resetChangeTracking();
            
            log.debug("Updated record in {}: id={}", tableName, id);
            
            return record;
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update record", e);
        }
    }
    
    /**
     * Update only modified fields (uses change tracking)
     */
    public DynamicRecord updateModified(String tableName, Object id, DynamicRecord record) {
        if (!record.isModified()) {
            log.debug("Record not modified, skipping update");
            return record;
        }
        
        return update(tableName, id, record);
    }
    
    /**
     * Delete a record by primary key
     */
    public void delete(String tableName, Object id) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        if (!schema.hasPrimaryKey()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key");
        }
        if (schema.hasCompositePrimaryKey()) {
            throw new IllegalArgumentException("Use delete(tableName, Map<String, Object>) for composite keys");
        }

        String pkColumn = schema.getPrimaryKey().getColumnNames().get(0);
        String sql = String.format("DELETE FROM %s WHERE %s = ?", schema.getQualifiedName(), pkColumn);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            //  Coerce ID to correct type
            ColumnSchema pkColumnSchema = schema.getColumn(pkColumn);
            Object coercedId = coercionService.coerce(id, pkColumnSchema);
            
            stmt.setObject(1, coercedId);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RecordNotFoundException("No record found with id " + id + " in table " + tableName);
            }
            
            log.debug("Deleted record from {}: id={}", tableName, id);
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete record", e);
        }
    }
    
    /**
     * Delete a record by composite primary key
     */
    public void delete(String tableName, Map<String, Object> pkValues) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        if (!schema.hasPrimaryKey()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key");
        }
        
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<ColumnSchema> columns = new ArrayList<>();
        
        for (String pkColumn : schema.getPrimaryKey().getColumnNames()) {
            if (!pkValues.containsKey(pkColumn)) {
                throw new IllegalArgumentException("Missing primary key value for column: " + pkColumn);
            }
            whereClauses.add(pkColumn + " = ?");
            params.add(pkValues.get(pkColumn));
            
            ColumnSchema column = schema.getColumn(pkColumn);
            columns.add(column);
        }

        String sql = String.format("DELETE FROM %s WHERE %s", schema.getQualifiedName(), String.join(" AND ", whereClauses));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params, columns);
            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RecordNotFoundException("No record found with pk " + pkValues + " in table " + tableName);
            }
            
            log.debug("Deleted record from {}: pk={}", tableName, pkValues);
            
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete record", e);
        }
    }
    
    /**
     * Delete records matching criteria
     */
    public int deleteByColumns(String tableName, Map<String, Object> criteria) {
        TableSchema schema = schemaRegistry.getRequired(tableName);
        
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete all records without criteria");
        }
        
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<ColumnSchema> columns = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : criteria.entrySet()) {
            String columnName = entry.getKey();
            whereClauses.add(columnName + " = ?");
            params.add(entry.getValue());
            
            ColumnSchema column = schema.getColumn(columnName);
            columns.add(column);
        }

        String sql = String.format("DELETE FROM %s WHERE %s", schema.getQualifiedName(), String.join(" AND ", whereClauses));

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params, columns);
            int affected = stmt.executeUpdate();
            log.debug("Deleted {} records from {}", affected, tableName);
            return affected;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete records by columns", e);
        }
    }
}
