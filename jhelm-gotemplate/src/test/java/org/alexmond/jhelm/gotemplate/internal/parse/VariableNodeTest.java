package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VariableNodeTest {

    @Test
    void testSingleIdentifier() {
        VariableNode node = new VariableNode("user");
        String[] identifiers = node.getIdentifiers();
        assertEquals(1, identifiers.length);
        assertEquals("user", identifiers[0]);
        assertEquals("user", node.toString());
    }

    @Test
    void testMultipleIdentifiers() {
        VariableNode node = new VariableNode("user.name");
        String[] identifiers = node.getIdentifiers();
        assertEquals(2, identifiers.length);
        assertEquals("user", identifiers[0]);
        assertEquals("name", identifiers[1]);
        assertEquals("user.name", node.toString());
    }

    @Test
    void testGetIdentifierByIndex() {
        VariableNode node = new VariableNode("a.b.c");
        assertEquals("a", node.getIdentifier(0));
        assertEquals("b", node.getIdentifier(1));
        assertEquals("c", node.getIdentifier(2));
    }

    @Test
    void testDeeplyNestedIdentifiers() {
        VariableNode node = new VariableNode("user.profile.address.city");
        String[] identifiers = node.getIdentifiers();
        assertEquals(4, identifiers.length);
        assertEquals("user.profile.address.city", node.toString());
    }

    @Test
    void testToStringReconstructsOriginal() {
        String original = "config.database.host";
        VariableNode node = new VariableNode(original);
        assertEquals(original, node.toString());
    }
}
