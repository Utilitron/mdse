package io.mdse.metadata.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TableType enum
 */
@DisplayName("TableType Tests")
class TableTypeTest {
    
    @Test
    @DisplayName("Should have TABLE value")
    void testTableValue() {
        assertNotNull(TableType.TABLE);
        assertEquals("TABLE", TableType.TABLE.name());
    }
    
    @Test
    @DisplayName("Should have VIEW value")
    void testViewValue() {
        assertNotNull(TableType.VIEW);
        assertEquals("VIEW", TableType.VIEW.name());
    }
    
    @Test
    @DisplayName("Should have MATERIALIZED_VIEW value")
    void testMaterializedViewValue() {
        assertNotNull(TableType.MATERIALIZED_VIEW);
        assertEquals("MATERIALIZED_VIEW", TableType.MATERIALIZED_VIEW.name());
    }
    
    @Test
    @DisplayName("Should have SYSTEM_TABLE value")
    void testSystemTableValue() {
        assertNotNull(TableType.SYSTEM_TABLE);
        assertEquals("SYSTEM_TABLE", TableType.SYSTEM_TABLE.name());
    }
    
    @Test
    @DisplayName("Should have TEMPORARY_TABLE value")
    void testTemporaryTableValue() {
        assertNotNull(TableType.TEMPORARY_TABLE);
        assertEquals("TEMPORARY_TABLE", TableType.TEMPORARY_TABLE.name());
    }
    
    @Test
    @DisplayName("Should have GLOBAL_TEMPORARY value")
    void testGlobalTemporaryValue() {
        assertNotNull(TableType.GLOBAL_TEMPORARY);
        assertEquals("GLOBAL_TEMPORARY", TableType.GLOBAL_TEMPORARY.name());
    }
    
    @Test
    @DisplayName("Should have LOCAL_TEMPORARY value")
    void testLocalTemporaryValue() {
        assertNotNull(TableType.LOCAL_TEMPORARY);
        assertEquals("LOCAL_TEMPORARY", TableType.LOCAL_TEMPORARY.name());
    }
    
    @Test
    @DisplayName("Should have EXTERNAL_TABLE value")
    void testExternalTableValue() {
        assertNotNull(TableType.EXTERNAL_TABLE);
        assertEquals("EXTERNAL_TABLE", TableType.EXTERNAL_TABLE.name());
    }
    
    @Test
    @DisplayName("Should have exactly 8 values")
    void testEnumCount() {
        TableType[] values = TableType.values();
        assertEquals(8, values.length);
    }
    
    @Test
    @DisplayName("Should convert from string")
    void testValueOf() {
        assertEquals(TableType.TABLE, TableType.valueOf("TABLE"));
        assertEquals(TableType.VIEW, TableType.valueOf("VIEW"));
        assertEquals(TableType.MATERIALIZED_VIEW, TableType.valueOf("MATERIALIZED_VIEW"));
        assertEquals(TableType.SYSTEM_TABLE, TableType.valueOf("SYSTEM_TABLE"));
        assertEquals(TableType.TEMPORARY_TABLE, TableType.valueOf("TEMPORARY_TABLE"));
        assertEquals(TableType.GLOBAL_TEMPORARY, TableType.valueOf("GLOBAL_TEMPORARY"));
        assertEquals(TableType.LOCAL_TEMPORARY, TableType.valueOf("LOCAL_TEMPORARY"));
        assertEquals(TableType.EXTERNAL_TABLE, TableType.valueOf("EXTERNAL_TABLE"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid value")
    void testInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () ->
                TableType.valueOf("INVALID"));
    }
    
    @Test
    @DisplayName("Should be usable in switch statement")
    void testSwitchStatement() {
        String result = switch (TableType.TABLE) {
            case TABLE -> "table";
            case VIEW -> "view";
            case MATERIALIZED_VIEW -> "materialized_view";
            case SYSTEM_TABLE -> "system_table";
            case TEMPORARY_TABLE -> "temporary_table";
            case GLOBAL_TEMPORARY -> "global_temporary";
            case LOCAL_TEMPORARY -> "local_temporary";
            case EXTERNAL_TABLE -> "external_table";
        };
        assertEquals("table", result);
    }
}
