package io.mdse.metadata.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReferentialAction enum
 */
@DisplayName("ReferentialAction Tests")
class ReferentialActionTest {
    
    @Test
    @DisplayName("Should have CASCADE value")
    void testCascadeValue() {
        assertNotNull(ReferentialAction.CASCADE);
        assertEquals("CASCADE", ReferentialAction.CASCADE.name());
    }
    
    @Test
    @DisplayName("Should have SET_NULL value")
    void testSetNullValue() {
        assertNotNull(ReferentialAction.SET_NULL);
        assertEquals("SET_NULL", ReferentialAction.SET_NULL.name());
    }
    
    @Test
    @DisplayName("Should have SET_DEFAULT value")
    void testSetDefaultValue() {
        assertNotNull(ReferentialAction.SET_DEFAULT);
        assertEquals("SET_DEFAULT", ReferentialAction.SET_DEFAULT.name());
    }
    
    @Test
    @DisplayName("Should have RESTRICT value")
    void testRestrictValue() {
        assertNotNull(ReferentialAction.RESTRICT);
        assertEquals("RESTRICT", ReferentialAction.RESTRICT.name());
    }
    
    @Test
    @DisplayName("Should have NO_ACTION value")
    void testNoActionValue() {
        assertNotNull(ReferentialAction.NO_ACTION);
        assertEquals("NO_ACTION", ReferentialAction.NO_ACTION.name());
    }
    
    @Test
    @DisplayName("Should have exactly 5 values")
    void testEnumCount() {
        ReferentialAction[] values = ReferentialAction.values();
        assertEquals(5, values.length);
    }
    
    @Test
    @DisplayName("Should convert from string")
    void testValueOf() {
        assertEquals(ReferentialAction.CASCADE, ReferentialAction.valueOf("CASCADE"));
        assertEquals(ReferentialAction.SET_NULL, ReferentialAction.valueOf("SET_NULL"));
        assertEquals(ReferentialAction.SET_DEFAULT, ReferentialAction.valueOf("SET_DEFAULT"));
        assertEquals(ReferentialAction.RESTRICT, ReferentialAction.valueOf("RESTRICT"));
        assertEquals(ReferentialAction.NO_ACTION, ReferentialAction.valueOf("NO_ACTION"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid value")
    void testInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () ->
                ReferentialAction.valueOf("INVALID"));
    }
    
    @Test
    @DisplayName("Should be usable in switch statement")
    void testSwitchStatement() {
        String result = switch (ReferentialAction.CASCADE) {
            case CASCADE -> "cascade";
            case SET_NULL -> "set_null";
            case SET_DEFAULT -> "set_default";
            case RESTRICT -> "restrict";
            case NO_ACTION -> "no_action";
        };
        assertEquals("cascade", result);
    }
}

