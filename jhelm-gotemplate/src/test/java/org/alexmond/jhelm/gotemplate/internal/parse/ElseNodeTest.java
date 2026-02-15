package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElseNodeTest {

    @Test
    void testToString() {
        ElseNode node = new ElseNode();
        assertEquals("{{else}}", node.toString());
    }

    @Test
    void testImplementsNode() {
        ElseNode node = new ElseNode();
        assertTrue(node instanceof Node);
    }

    @Test
    void testMultipleInstances() {
        ElseNode node1 = new ElseNode();
        ElseNode node2 = new ElseNode();
        assertEquals(node1.toString(), node2.toString());
    }
}
