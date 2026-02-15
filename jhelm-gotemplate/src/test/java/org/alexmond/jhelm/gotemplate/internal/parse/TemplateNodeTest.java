package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateNodeTest {

    @Test
    void testConstructorWithName() {
        TemplateNode node = new TemplateNode("myTemplate");
        assertEquals("myTemplate", node.getName());
        assertNull(node.getPipeNode());
    }

    @Test
    void testSetPipeNode() {
        TemplateNode node = new TemplateNode("template1");
        PipeNode pipeNode = new PipeNode("ctx");
        node.setPipeNode(pipeNode);

        assertNotNull(node.getPipeNode());
        assertEquals(pipeNode, node.getPipeNode());
    }

    @Test
    void testToStringWithoutPipeNode() {
        TemplateNode node = new TemplateNode("header");
        String result = node.toString();

        assertTrue(result.contains("{{template"));
        assertTrue(result.contains("\"header\""));
        assertTrue(result.contains("}}"));
        assertFalse(result.contains("null"));
    }

    @Test
    void testToStringWithPipeNode() {
        TemplateNode node = new TemplateNode("content");
        PipeNode pipeNode = new PipeNode("data");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("getData"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        String result = node.toString();
        assertTrue(result.contains("{{template"));
        assertTrue(result.contains("\"content\""));
        assertTrue(result.contains("getData"));
        assertTrue(result.contains("}}"));
    }

    @Test
    void testTemplateNameIsQuoted() {
        TemplateNode node = new TemplateNode("my-template");
        String result = node.toString();
        assertTrue(result.contains("\"my-template\""));
    }

    @Test
    void testGetName() {
        TemplateNode node = new TemplateNode("testTemplate");
        assertEquals("testTemplate", node.getName());
    }
}
