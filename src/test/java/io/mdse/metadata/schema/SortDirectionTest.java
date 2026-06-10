package io.mdse.metadata.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SortDirection enum
 */
@DisplayName("SortDirection Tests")
class SortDirectionTest {
    
    @Test
    @DisplayName("Should have ASC value")
    void testAscValue() {
        assertNotNull(SortDirection.ASC);
        assertEquals("ASC", SortDirection.ASC.name());
    }
    
    @Test
    @DisplayName("Should have DESC value")
    void testDescValue() {
        assertNotNull(SortDirection.DESC);
        assertEquals("DESC", SortDirection.DESC.name());
    }
    
    @Test
    @DisplayName("Should have exactly 2 values")
    void testEnumCount() {
        SortDirection[] values = SortDirection.values();
        assertEquals(2, values.length);
    }
    
    @Test
    @DisplayName("Should convert from string")
    void testValueOf() {
        assertEquals(SortDirection.ASC, SortDirection.valueOf("ASC"));
        assertEquals(SortDirection.DESC, SortDirection.valueOf("DESC"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid value")
    void testInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () ->
                SortDirection.valueOf("INVALID"));
    }
    
    @Test
    @DisplayName("Should be usable in switch statement")
    void testSwitchStatement() {
        String result = switch (SortDirection.ASC) {
            case ASC -> "ascending";
            case DESC -> "descending";
        };
        assertEquals("ascending", result);
        
        result = switch (SortDirection.DESC) {
            case ASC -> "ascending";
            case DESC -> "descending";
        };
        assertEquals("descending", result);
    }
    
    @Test
    @DisplayName("Should be comparable")
    void testComparable() {
        assertEquals(0, SortDirection.ASC.compareTo(SortDirection.ASC));
        assertEquals(0, SortDirection.DESC.compareTo(SortDirection.DESC));
        assertNotEquals(0, SortDirection.ASC.compareTo(SortDirection.DESC));
    }
}

