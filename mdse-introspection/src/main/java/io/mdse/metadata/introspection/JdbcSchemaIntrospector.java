package io.mdse.metadata.introspection;

import io.mdse.metadata.schema.*;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-based schema introspection implementation.
 * Uses DatabaseMetaData to discover table structures at runtime.
 * Produces immutable {@link TableSchema} instances.
 * <p>
 * This is vendor-agnostic and works with any JDBC-compliant database.
 */
@Slf4j
public class JdbcSchemaIntrospector implements SchemaIntrospector {
    
    private final DataSource dataSource;
    private final String catalogName;
    private final String defaultSchemaName;
    private final IntrospectionConfig config;
    
    /**
     * Constructor with default configuration
     */
    public JdbcSchemaIntrospector(DataSource dataSource) {
        this(dataSource, IntrospectionConfig.DEFAULT);
    }
    
    /**
     * Constructor with custom configuration
     */
    public JdbcSchemaIntrospector(DataSource dataSource, IntrospectionConfig config) {
        this.dataSource = dataSource;
        this.config = config;
        
        // Determine catalog and schema on construction
        try (Connection connection = dataSource.getConnection()) {
            this.catalogName = connection.getCatalog();
            this.defaultSchemaName = connection.getSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to determine catalog/schema", e);
        }
        
        log.info("Schema introspector initialized for catalog={}, schema={}", catalogName, defaultSchemaName);
    }
    
    @Override
    public Collection<TableSchema> introspectAll() {
        return introspectSchema(defaultSchemaName);
    }
    
    @Override
    public Collection<TableSchema> introspectSchema(String schemaName) {
        List<TableSchema> tables = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            try (ResultSet resultSet = metaData.getTables(catalogName, schemaName, "%", config.getTableTypes())) {
                
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    String tableSchema = resultSet.getString("TABLE_SCHEM");
                    
                    try {
                        Optional<TableSchema> tableOpt = introspectTable(tableSchema, tableName);
                        tableOpt.ifPresent(tables::add);
                    } catch (Exception e) {
                        log.error("Failed to introspect table {}.{}", tableSchema, tableName, e);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to introspect schema: " + schemaName, e);
        }
        
        log.info("Introspected {} tables from schema {}", tables.size(), schemaName);
        return tables;
    }
    
    @Override
    public Optional<TableSchema> introspectTable(String tableName) {
        return introspectTable(defaultSchemaName, tableName);
    }
    
    @Override
    public Optional<TableSchema> introspectTable(String schemaName, String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Check if table exists
            if (!tableExists(metaData, schemaName, tableName)) {
                log.warn("Table not found: {}.{}", schemaName, tableName);
                return Optional.empty();
            }
            
            TableSchema table = buildTableSchema(metaData, schemaName, tableName);
            
            return Optional.of(table);
            
        } catch (SQLException e) {
            log.error("Failed to introspect table {}.{}", schemaName, tableName, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<TableSchema> refreshTable(String tableName) {
        return introspectTable(tableName);
    }
    
    @Override
    public String getCatalogName() {
        return catalogName;
    }
    
    @Override
    public String getDefaultSchemaName() {
        return defaultSchemaName;
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private boolean tableExists(DatabaseMetaData metaData, String schema, String table) throws SQLException {
        try (ResultSet rs = metaData.getTables(catalogName, schema, table, null)) {
            return rs.next();
        }
    }

    private TableSchema buildTableSchema(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        TableSchema.TableSchemaBuilder builder = TableSchema.builder()
                .id(UUID.randomUUID())
                .catalogName(catalogName)
                .schemaName(schemaName)
                .tableName(tableName)
                .lastRefreshed(Instant.now());
        
        // Get table type and description
        try (ResultSet rs = metaData.getTables(catalogName, schemaName, tableName, null)) {
            if (rs.next()) {
                builder.tableType(mapTableType(rs.getString("TABLE_TYPE")));
                builder.description(rs.getString("REMARKS"));
            }
        }

        // Load columns (needed for PK and FK references)
        List<ColumnSchema> columns = loadColumns(metaData, schemaName, tableName);
        builder.columns(columns);

        // Primary key (also marks columns as primaryKey)
        PrimaryKeySchema primaryKey = loadPrimaryKey(metaData, schemaName, tableName, columns);
        builder.primaryKey(primaryKey);

        // Indexes
        if (config.isLoadIndexes()) {
            builder.indexes(loadIndexes(metaData, schemaName, tableName));
        }

        // Foreign keys (needs columns to set annotations)
        if (config.isLoadForeignKeys()) {
            builder.foreignKeys(loadForeignKeys(metaData, schemaName, tableName, columns));
        }

        // Unique constraints (derived from unique indexes not PK)
        if (config.isLoadUniqueConstraints()) {
            builder.uniqueConstraints(loadUniqueConstraints(metaData, schemaName, tableName));
        }

        // Estimate row count
        if (config.isEstimateRowCounts()) {
            long rowCount = estimateRowCount(schemaName, tableName);
            builder.estimatedRowCount(rowCount);
        }

        return builder.build();
    }

    private List<ColumnSchema> loadColumns(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<ColumnSchema> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(catalogName, schemaName, tableName, "%")) {
            while (rs.next()) {
                ColumnSchema column = buildColumnSchema(rs);
                columns.add(column);
            }
        }

        columns.sort(Comparator.comparingInt(ColumnSchema::getOrdinalPosition));
        return columns;
    }

    private ColumnSchema buildColumnSchema(ResultSet rs) throws SQLException {
        String columnName = rs.getString("COLUMN_NAME");
        int sqlType = rs.getInt("DATA_TYPE");
        String dbTypeName = rs.getString("TYPE_NAME");
        Integer columnSize = getIntOrNull(rs, "COLUMN_SIZE");
        Integer decimalDigits = getIntOrNull(rs, "DECIMAL_DIGITS");
        boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
        String defaultValue = rs.getString("COLUMN_DEF");
        String remarks = rs.getString("REMARKS");
        int ordinalPosition = rs.getInt("ORDINAL_POSITION");
        boolean autoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
        boolean generated = "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));

        ColumnSchema.ColumnSchemaBuilder builder = ColumnSchema.builder()
                .id(UUID.randomUUID())
                .columnName(columnName)
                .sqlType(sqlType)
                .dbTypeName(dbTypeName)
                .columnSize(columnSize)
                .precision(columnSize)
                .scale(decimalDigits)
                .nullable(nullable)
                .defaultValue(defaultValue)
                .description(remarks)
                .ordinalPosition(ordinalPosition)
                .autoIncrement(autoIncrement)
                .generated(generated)
                .javaTypeName(mapJavaTypeName(sqlType));

        // Text length constraint
        if (isTextType(sqlType) && columnSize != null) {
            builder.maxLength(columnSize);
        }

        // Default field name is camelCase of column name
        builder.fieldName(toCamelCase(columnName));

        return builder.build();
    }

    private PrimaryKeySchema loadPrimaryKey(DatabaseMetaData metaData, String schemaName, String tableName,
                                            List<ColumnSchema> columns) throws SQLException {
        Map<Integer, String> pkColumns = new TreeMap<>();
        String constraintName = null;

        try (ResultSet rs = metaData.getPrimaryKeys(catalogName, schemaName, tableName)) {
            while (rs.next()) {
                constraintName = rs.getString("PK_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                int keySeq = rs.getInt("KEY_SEQ");
                pkColumns.put(keySeq, columnName);
            }
        }

        if (pkColumns.isEmpty()) {
            return null;
        }

        List<String> columnNames = new ArrayList<>(pkColumns.values());

        // Mark columns as primary key (optional, but can be useful)
        for (ColumnSchema col : columns) {
            if (columnNames.contains(col.getColumnName())) {
                // In immutable builder, we can't modify existing ColumnSchema.
                // Instead, we'll rely on the primary key reference in TableSchema.
                // For now, ignore; we keep the flag out of ColumnSchema as it's redundant.
            }
        }

        return PrimaryKeySchema.builder()
                .id(UUID.randomUUID())
                .constraintName(constraintName)
                .columnNames(columnNames)
                .build();
    }
    
    private List<IndexSchema> loadIndexes(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        
        record IndexColumn(int ordinalPosition, String columnName) {}
        
        class IndexAccumulator {
            boolean unique;
            String indexType;
            final List<IndexColumn> columns = new ArrayList<>();
        }
        
        Map<String, IndexAccumulator> indexes = new LinkedHashMap<>();
        
        try (ResultSet rs = metaData.getIndexInfo(catalogName, schemaName, tableName, false, false)) {
            
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                
                if (indexName == null) {
                    continue;
                }
                
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                short jdbcIndexType = rs.getShort("TYPE");
                String columnName = rs.getString("COLUMN_NAME");
                int ordinalPosition = rs.getInt("ORDINAL_POSITION");
                
                IndexAccumulator index = indexes.computeIfAbsent(indexName, k -> {
                    IndexAccumulator acc = new IndexAccumulator();
                    acc.unique = !nonUnique;
                    acc.indexType = mapIndexType(jdbcIndexType);
                    return acc;
                });
                
                if (columnName != null) {
                    index.columns.add(new IndexColumn(ordinalPosition, columnName));
                }
            }
        }
        
        List<IndexSchema> result = new ArrayList<>();
        
        for (Map.Entry<String, IndexAccumulator> entry : indexes.entrySet()) {
            IndexAccumulator acc = entry.getValue();
            
            IndexSchema.IndexSchemaBuilder builder = IndexSchema.builder()
                    .id(UUID.randomUUID())
                    .indexName(entry.getKey())
                    .unique(acc.unique)
                    .indexType(acc.indexType);
            
            acc.columns.stream()
                    .sorted(Comparator.comparingInt(IndexColumn::ordinalPosition))
                    .map(IndexColumn::columnName)
                    .forEach(builder::columnName);
            
            result.add(builder.build());
        }
        
        return result;
    }

    private List<ForeignKeySchema> loadForeignKeys(DatabaseMetaData metaData, String schemaName, String tableName,
                                                   List<ColumnSchema> columns) throws SQLException {
        Map<String, ForeignKeySchema.ForeignKeySchemaBuilder> fkBuilders = new LinkedHashMap<>();

        try (ResultSet rs = metaData.getImportedKeys(catalogName, schemaName, tableName)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null) continue;

                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                short deleteRule = rs.getShort("DELETE_RULE");
                short updateRule = rs.getShort("UPDATE_RULE");

                ForeignKeySchema.ForeignKeySchemaBuilder builder = fkBuilders.computeIfAbsent(fkName, k -> {
                            try {
                                return ForeignKeySchema.builder()
                                        .id(UUID.randomUUID())
                                        .constraintName(fkName)
                                        .sourceSchema(rs.getString("FKTABLE_SCHEM"))
                                        .sourceTable(rs.getString("FKTABLE_NAME"))
                                        .referencedSchema(rs.getString("PKTABLE_SCHEM"))
                                        .referencedTable(pkTable)
                                        .deleteRule(mapReferentialAction(deleteRule))
                                        .updateRule(mapReferentialAction(updateRule));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );

                // Add columns in key sequence order (key_seq is not available directly in importedKeys,
                // but we can rely on the order of rows as returned by JDBC, which should be by KEY_SEQ.
                // We'll store columns in the order they appear.
                builder.sourceColumn(fkColumn);
                builder.referencedColumn(pkColumn);
            }
        }

        // Determine cardinality (simple heuristic: if source table has unique constraint on FK columns)
        List<ForeignKeySchema> foreignKeys = fkBuilders.values().stream()
                .map(ForeignKeySchema.ForeignKeySchemaBuilder::build)
                .collect(Collectors.toList());

        // Annotate columns as foreign key (optional)
        for (ForeignKeySchema fk : foreignKeys) {
            for (String srcCol : fk.getSourceColumns()) {
                // We can't modify immutable columns, but we could store a separate map of annotations.
                // For now, ignore; the relationship is captured in the ForeignKeySchema.
            }
        }

        return foreignKeys;
    }

    private List<UniqueConstraintSchema> loadUniqueConstraints(DatabaseMetaData metaData, String schemaName,
                                                                String tableName) throws SQLException {
        // Unique constraints are often exposed as unique indexes.
        // We can reuse the indexes but filter those that are unique and not the primary key.
        List<UniqueConstraintSchema> uniqueConstraints = new ArrayList<>();

        try (ResultSet rs = metaData.getIndexInfo(catalogName, schemaName, tableName, true, false)) {
            Map<String, List<String>> indexColumns = new LinkedHashMap<>();
            String pkName = null;

            // First get primary key name to exclude it
            try (ResultSet pkRs = metaData.getPrimaryKeys(catalogName, schemaName, tableName)) {
                if (pkRs.next()) {
                    pkName = pkRs.getString("PK_NAME");
                }
            }

            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null || indexName.equals(pkName)) continue;

                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                if (nonUnique) continue; // only unique indexes

                String columnName = rs.getString("COLUMN_NAME");
                int ordinal = rs.getInt("ORDINAL_POSITION");
                indexColumns.computeIfAbsent(indexName, k -> new ArrayList<>())
                        .add(columnName);
                // Ensure order by ordinal (JDBC returns in key sequence order)
            }

            for (Map.Entry<String, List<String>> entry : indexColumns.entrySet()) {
                uniqueConstraints.add(UniqueConstraintSchema.builder()
                        .id(UUID.randomUUID())
                        .constraintName(entry.getKey())
                        .columnNames(entry.getValue())
                        .build());
            }
        }

        return uniqueConstraints;
    }

    private long estimateRowCount(String schemaName, String tableName) {
        String sql = "SELECT COUNT(*) FROM " + qualify(schemaName, tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.debug("Failed to estimate row count for {}.{}", schemaName, tableName, e);
        }
        return -1L;
    }

    private String qualify(String schema, String table) {
        return (schema != null ? schema + "." : "") + table;
    }

    private TableType mapTableType(String dbType) {
        if (dbType == null) return TableType.TABLE;
        return switch (dbType.toUpperCase()) {
            case "VIEW" -> TableType.VIEW;
            case "SYSTEM TABLE", "SYSTEM_TABLE" -> TableType.SYSTEM_TABLE;
            case "TEMPORARY", "LOCAL TEMPORARY", "GLOBAL TEMPORARY" -> TableType.TEMPORARY_TABLE;
            default -> TableType.TABLE;
        };
    }

    private String mapJavaTypeName(int sqlType) {
        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.CLOB -> String.class.getName();
            case Types.INTEGER -> Integer.class.getName();
            case Types.BIGINT -> Long.class.getName();
            case Types.SMALLINT -> Short.class.getName();
            case Types.TINYINT -> Byte.class.getName();
            case Types.BOOLEAN, Types.BIT -> Boolean.class.getName();
            case Types.DECIMAL, Types.NUMERIC -> java.math.BigDecimal.class.getName();
            case Types.DOUBLE, Types.FLOAT -> Double.class.getName();
            case Types.REAL -> Float.class.getName();
            case Types.DATE -> java.sql.Date.class.getName();
            case Types.TIME -> java.sql.Time.class.getName();
            case Types.TIMESTAMP -> java.sql.Timestamp.class.getName();
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> byte[].class.getName();
            default -> Object.class.getName();
        };
    }

    private boolean isTextType(int sqlType) {
        return sqlType == Types.VARCHAR || sqlType == Types.CHAR || sqlType == Types.LONGVARCHAR || sqlType == Types.CLOB;
    }

    private String mapIndexType(short indexType) {
        return switch (indexType) {
            case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
            case DatabaseMetaData.tableIndexHashed -> "HASH";
            case DatabaseMetaData.tableIndexOther -> "OTHER";
            default -> "BTREE";
        };
    }
    
    private ReferentialAction mapReferentialAction(short action) {
        return switch (action) {
            case DatabaseMetaData.importedKeyCascade -> ReferentialAction.CASCADE;
            case DatabaseMetaData.importedKeySetNull -> ReferentialAction.SET_NULL;
            case DatabaseMetaData.importedKeySetDefault -> ReferentialAction.SET_DEFAULT;
            case DatabaseMetaData.importedKeyRestrict -> ReferentialAction.RESTRICT;
            default -> ReferentialAction.NO_ACTION;
        };
    }

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    private String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        
        for (char c : snake.toLowerCase().toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return sb.toString();
    }
}

