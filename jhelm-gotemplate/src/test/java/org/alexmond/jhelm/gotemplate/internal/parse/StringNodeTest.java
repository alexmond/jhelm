package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringNodeTest {

    @Test
    void testDoubleQuotedString() {
        StringNode node = new StringNode("\"hello\"");
        assertEquals("\"hello\"", node.getOrigin());
        assertEquals("hello", node.getText());
        assertEquals("\"hello\"", node.toString());
    }

    @Test
    void testSingleQuotedString() {
        StringNode node = new StringNode("'hello'");
        assertEquals("'hello'", node.getOrigin());
        assertEquals("hello", node.getText());
    }

    @Test
    void testBacktickString() {
        StringNode node = new StringNode("`hello`");
        assertEquals("`hello`", node.getOrigin());
        assertEquals("hello", node.getText());
    }

    @Test
    void testStringWithEscapeSequences() {
        StringNode node = new StringNode("\"hello\\nworld\"");
        assertEquals("\"hello\\nworld\"", node.getOrigin());
        assertEquals("hello\nworld", node.getText());
    }

    @Test
    void testEmptyString() {
        StringNode node = new StringNode("\"\"");
        assertEquals("\"\"", node.getOrigin());
        assertEquals("", node.getText());
    }

    @Test
    void testUnquotedString() {
        StringNode node = new StringNode("hello");
        assertEquals("hello", node.getOrigin());
        assertEquals("hello", node.getText());
    }
}
