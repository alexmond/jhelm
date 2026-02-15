package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierNodeTest {

    @Test
    void testConstructorAndGetIdentifier() {
        IdentifierNode node = new IdentifierNode("myVariable");
        assertEquals("myVariable", node.getIdentifier());
    }

    @Test
    void testToString() {
        IdentifierNode node = new IdentifierNode("userName");
        assertEquals("userName", node.toString());
    }

    @Test
    void testSingleCharacterIdentifier() {
        IdentifierNode node = new IdentifierNode("x");
        assertEquals("x", node.getIdentifier());
    }

    @Test
    void testLongIdentifier() {
        String identifier = "veryLongVariableNameForTesting";
        IdentifierNode node = new IdentifierNode(identifier);
        assertEquals(identifier, node.getIdentifier());
    }

    @Test
    void testIdentifierWithNumbers() {
        IdentifierNode node = new IdentifierNode("var123");
        assertEquals("var123", node.getIdentifier());
    }

    @Test
    void testIdentifierWithUnderscores() {
        IdentifierNode node = new IdentifierNode("my_variable_name");
        assertEquals("my_variable_name", node.getIdentifier());
    }
}
