package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListNodeTest {

    @Test
    void testAppendNode() {
        ListNode listNode = new ListNode();
        TextNode textNode = new TextNode("Hello");
        listNode.append(textNode);

        assertEquals(textNode, listNode.getLast());
    }

    @Test
    void testAppendMultipleNodes() {
        ListNode listNode = new ListNode();
        TextNode node1 = new TextNode("First");
        TextNode node2 = new TextNode("Second");
        TextNode node3 = new TextNode("Third");

        listNode.append(node1);
        listNode.append(node2);
        listNode.append(node3);

        assertEquals(node3, listNode.getLast());
    }

    @Test
    void testGetLastOnEmptyList() {
        ListNode listNode = new ListNode();
        assertNull(listNode.getLast());
    }

    @Test
    void testRemoveLast() {
        ListNode listNode = new ListNode();
        TextNode node1 = new TextNode("First");
        TextNode node2 = new TextNode("Second");

        listNode.append(node1);
        listNode.append(node2);
        assertEquals(node2, listNode.getLast());

        listNode.removeLast();
        assertEquals(node1, listNode.getLast());
    }

    @Test
    void testRemoveLastOnEmptyList() {
        ListNode listNode = new ListNode();
        assertDoesNotThrow(() -> listNode.removeLast());
        assertNull(listNode.getLast());
    }

    @Test
    void testIterator() {
        ListNode listNode = new ListNode();
        TextNode node1 = new TextNode("First");
        TextNode node2 = new TextNode("Second");

        listNode.append(node1);
        listNode.append(node2);

        List<Node> nodes = new ArrayList<>();
        for (Node node : listNode) {
            nodes.add(node);
        }

        assertEquals(2, nodes.size());
        assertEquals(node1, nodes.get(0));
        assertEquals(node2, nodes.get(1));
    }

    @Test
    void testToString() {
        ListNode listNode = new ListNode();
        listNode.append(new TextNode("Hello"));
        listNode.append(new TextNode(" "));
        listNode.append(new TextNode("World"));

        assertEquals("\"Hello\"\" \"\"World\"", listNode.toString());
    }

    @Test
    void testToStringEmptyList() {
        ListNode listNode = new ListNode();
        assertEquals("", listNode.toString());
    }
}
