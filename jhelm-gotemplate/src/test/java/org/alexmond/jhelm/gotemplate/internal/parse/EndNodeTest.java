package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndNodeTest {

    @Test
    void testToString() {
        EndNode node = new EndNode();
        assertEquals("{{end}}", node.toString());
    }

    @Test
    void testImplementsNode() {
        EndNode node = new EndNode();
        assertTrue(node instanceof Node);
    }

    @Test
    void testMultipleInstances() {
        EndNode node1 = new EndNode();
        EndNode node2 = new EndNode();
        assertEquals(node1.toString(), node2.toString());
    }
}
