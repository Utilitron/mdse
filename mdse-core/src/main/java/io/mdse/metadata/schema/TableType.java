package io.mdse.metadata.schema;

/**
 * Type of database table
 */
public enum TableType {
    TABLE,
    VIEW,
    MATERIALIZED_VIEW,
    SYSTEM_TABLE,
    TEMPORARY_TABLE,
    GLOBAL_TEMPORARY,
    LOCAL_TEMPORARY,
    EXTERNAL_TABLE
}
