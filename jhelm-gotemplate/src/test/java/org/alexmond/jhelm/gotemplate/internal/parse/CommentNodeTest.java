package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentNodeTest {

    @Test
    void testConstructorAndGetComment() {
        CommentNode node = new CommentNode("This is a comment");
        assertEquals("This is a comment", node.getComment());
    }

    @Test
    void testToString() {
        CommentNode node = new CommentNode("TODO: implement this");
        assertEquals("TODO: implement this", node.toString());
    }

    @Test
    void testEmptyComment() {
        CommentNode node = new CommentNode("");
        assertEquals("", node.getComment());
        assertEquals("", node.toString());
    }

    @Test
    void testMultiLineComment() {
        String comment = "Line 1\nLine 2\nLine 3";
        CommentNode node = new CommentNode(comment);
        assertEquals(comment, node.getComment());
    }

    @Test
    void testCommentWithSpecialCharacters() {
        String comment = "Comment with /* special */ chars";
        CommentNode node = new CommentNode(comment);
        assertEquals(comment, node.getComment());
    }
}
