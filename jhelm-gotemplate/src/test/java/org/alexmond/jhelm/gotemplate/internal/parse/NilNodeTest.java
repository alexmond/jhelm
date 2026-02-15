package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NilNodeTest {

    @Test
    void testToString() {
        NilNode node = new NilNode();
        assertEquals("nil", node.toString());
    }

    @Test
    void testImplementsNode() {
        NilNode node = new NilNode();
        assertTrue(node instanceof Node);
    }

    @Test
    void testMultipleInstances() {
        NilNode node1 = new NilNode();
        NilNode node2 = new NilNode();
        assertEquals(node1.toString(), node2.toString());
    }
}
