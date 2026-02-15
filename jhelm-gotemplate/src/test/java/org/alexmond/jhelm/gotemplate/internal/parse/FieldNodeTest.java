package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldNodeTest {

    @Test
    void testSingleField() {
        FieldNode node = new FieldNode(".Name");
        String[] identifiers = node.getIdentifiers();
        assertEquals(1, identifiers.length);
        assertEquals("Name", identifiers[0]);
        assertEquals(".Name", node.toString());
    }

    @Test
    void testNestedFields() {
        FieldNode node = new FieldNode(".User.Name");
        String[] identifiers = node.getIdentifiers();
        assertEquals(2, identifiers.length);
        assertEquals("User", identifiers[0]);
        assertEquals("Name", identifiers[1]);
        assertEquals(".User.Name", node.toString());
    }

    @Test
    void testDeeplyNestedFields() {
        FieldNode node = new FieldNode(".Data.User.Profile.Name");
        String[] identifiers = node.getIdentifiers();
        assertEquals(4, identifiers.length);
        assertEquals("Data", identifiers[0]);
        assertEquals("User", identifiers[1]);
        assertEquals("Profile", identifiers[2]);
        assertEquals("Name", identifiers[3]);
        assertEquals(".Data.User.Profile.Name", node.toString());
    }

    @Test
    void testEmptyField() {
        FieldNode node = new FieldNode(".");
        String[] identifiers = node.getIdentifiers();
        assertEquals(1, identifiers.length);
        assertEquals("", identifiers[0]);
    }

    @Test
    void testToStringReconstructsOriginal() {
        String original = ".User.Address.City";
        FieldNode node = new FieldNode(original);
        assertEquals(original, node.toString());
    }
}
