package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNodeTest {

    @Test
    void testConstructorAndGetText() {
        TextNode node = new TextNode("Hello, World!");
        assertEquals("Hello, World!", node.getText());
    }

    @Test
    void testToString() {
        TextNode node = new TextNode("test");
        assertEquals("\"test\"", node.toString());
    }

    @Test
    void testEmptyText() {
        TextNode node = new TextNode("");
        assertEquals("", node.getText());
        assertEquals("\"\"", node.toString());
    }

    @Test
    void testMultiLineText() {
        String text = "Line 1\nLine 2\nLine 3";
        TextNode node = new TextNode(text);
        assertEquals(text, node.getText());
        assertEquals("\"" + text + "\"", node.toString());
    }

    @Test
    void testTextWithSpecialCharacters() {
        String text = "Special: \t\n\r";
        TextNode node = new TextNode(text);
        assertEquals(text, node.getText());
    }
}
