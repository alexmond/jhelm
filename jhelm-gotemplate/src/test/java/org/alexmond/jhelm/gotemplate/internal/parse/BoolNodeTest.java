package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoolNodeTest {

    @Test
    void testTrueValue() {
        BoolNode node = new BoolNode("true");
        assertTrue(node.isValue());
        assertEquals("true", node.toString());
    }

    @Test
    void testFalseValue() {
        BoolNode node = new BoolNode("false");
        assertFalse(node.isValue());
        assertEquals("false", node.toString());
    }

    @Test
    void testCaseInsensitive() {
        assertTrue(new BoolNode("TRUE").isValue());
        assertTrue(new BoolNode("True").isValue());
        assertFalse(new BoolNode("FALSE").isValue());
        assertFalse(new BoolNode("False").isValue());
    }

    @Test
    void testInvalidBoolean() {
        BoolNode node = new BoolNode("invalid");
        assertFalse(node.isValue());
    }

    @Test
    void testEmptyString() {
        BoolNode node = new BoolNode("");
        assertFalse(node.isValue());
    }
}
