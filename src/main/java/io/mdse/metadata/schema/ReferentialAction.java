package io.mdse.metadata.schema;

/**
 * Referential action for foreign keys
 */
public enum ReferentialAction {
    CASCADE,
    SET_NULL,
    SET_DEFAULT,
    RESTRICT,
    NO_ACTION
}

