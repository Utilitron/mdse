package io.mdse.metadata.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RelationshipCardinality enum
 */
@DisplayName("RelationshipCardinality Tests")
class RelationshipCardinalityTest {
    
    @Test
    @DisplayName("Should have ONE_TO_ONE value")
    void testOneToOneValue() {
        assertNotNull(RelationshipCardinality.ONE_TO_ONE);
        assertEquals("ONE_TO_ONE", RelationshipCardinality.ONE_TO_ONE.name());
    }
    
    @Test
    @DisplayName("Should have ONE_TO_MANY value")
    void testOneToManyValue() {
        assertNotNull(RelationshipCardinality.ONE_TO_MANY);
        assertEquals("ONE_TO_MANY", RelationshipCardinality.ONE_TO_MANY.name());
    }
    
    @Test
    @DisplayName("Should have MANY_TO_ONE value")
    void testManyToOneValue() {
        assertNotNull(RelationshipCardinality.MANY_TO_ONE);
        assertEquals("MANY_TO_ONE", RelationshipCardinality.MANY_TO_ONE.name());
    }
    
    @Test
    @DisplayName("Should have MANY_TO_MANY value")
    void testManyToManyValue() {
        assertNotNull(RelationshipCardinality.MANY_TO_MANY);
        assertEquals("MANY_TO_MANY", RelationshipCardinality.MANY_TO_MANY.name());
    }
    
    @Test
    @DisplayName("Should have exactly 4 values")
    void testEnumCount() {
        RelationshipCardinality[] values = RelationshipCardinality.values();
        assertEquals(4, values.length);
    }
    
    @Test
    @DisplayName("Should convert from string")
    void testValueOf() {
        assertEquals(RelationshipCardinality.ONE_TO_ONE,
                RelationshipCardinality.valueOf("ONE_TO_ONE"));
        assertEquals(RelationshipCardinality.ONE_TO_MANY,
                RelationshipCardinality.valueOf("ONE_TO_MANY"));
        assertEquals(RelationshipCardinality.MANY_TO_ONE,
                RelationshipCardinality.valueOf("MANY_TO_ONE"));
        assertEquals(RelationshipCardinality.MANY_TO_MANY,
                RelationshipCardinality.valueOf("MANY_TO_MANY"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid value")
    void testInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () ->
                RelationshipCardinality.valueOf("INVALID"));
    }
    
    @Test
    @DisplayName("Should be usable in switch statement")
    void testSwitchStatement() {
        String result = switch (RelationshipCardinality.ONE_TO_MANY) {
            case ONE_TO_ONE -> "1:1";
            case ONE_TO_MANY -> "1:N";
            case MANY_TO_ONE -> "N:1";
            case MANY_TO_MANY -> "N:N";
        };
        assertEquals("1:N", result);
    }
}
