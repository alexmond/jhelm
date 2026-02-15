package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainNodeTest {

    @Test
    void testConstructorWithNode() {
        IdentifierNode baseNode = new IdentifierNode("user");
        ChainNode chainNode = new ChainNode(baseNode);

        assertEquals(baseNode, chainNode.getNode());
        assertEquals(0, chainNode.getFields().size());
    }

    @Test
    void testAppendSingleField() {
        IdentifierNode baseNode = new IdentifierNode("user");
        ChainNode chainNode = new ChainNode(baseNode);
        chainNode.append(".name");

        assertEquals(1, chainNode.getFields().size());
        assertEquals("name", chainNode.getFields().get(0));
    }

    @Test
    void testAppendMultipleFields() {
        IdentifierNode baseNode = new IdentifierNode("user");
        ChainNode chainNode = new ChainNode(baseNode);
        chainNode.append(".profile");
        chainNode.append(".email");

        assertEquals(2, chainNode.getFields().size());
        assertEquals("profile", chainNode.getFields().get(0));
        assertEquals("email", chainNode.getFields().get(1));
    }

    @Test
    void testAppendFieldWithoutDotThrowsException() {
        ChainNode chainNode = new ChainNode(new IdentifierNode("base"));
        assertThrows(IllegalArgumentException.class, () -> chainNode.append("field"));
    }

    @Test
    void testAppendEmptyFieldThrowsException() {
        ChainNode chainNode = new ChainNode(new IdentifierNode("base"));
        assertThrows(IllegalArgumentException.class, () -> chainNode.append("."));
    }

    @Test
    void testToStringWithIdentifierNode() {
        IdentifierNode baseNode = new IdentifierNode("user");
        ChainNode chainNode = new ChainNode(baseNode);
        chainNode.append(".name");
        chainNode.append(".first");

        assertEquals("user.name.first", chainNode.toString());
    }

    @Test
    void testToStringWithPipeNode() {
        PipeNode pipeNode = new PipeNode("test");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("getValue"));
        pipeNode.append(cmd);

        ChainNode chainNode = new ChainNode(pipeNode);
        chainNode.append(".field");

        String result = chainNode.toString();
        assertTrue(result.startsWith("("));
        assertTrue(result.contains(")"));
        assertTrue(result.endsWith(".field"));
    }

    @Test
    void testToStringWithoutFields() {
        IdentifierNode baseNode = new IdentifierNode("user");
        ChainNode chainNode = new ChainNode(baseNode);

        assertEquals("user", chainNode.toString());
    }
}
