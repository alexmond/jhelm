package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeNodeTest {

    @Test
    void testRangeNodeExtendsBranchNode() {
        RangeNode node = new RangeNode();
        assertTrue(node instanceof BranchNode);
        assertTrue(node instanceof Node);
    }

    @Test
    void testRangeNodeWithPipeNode() {
        RangeNode node = new RangeNode();
        PipeNode pipeNode = new PipeNode("range");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("items"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        assertNotNull(node.getPipeNode());
        assertEquals("range", node.getPipeNode().getContext());
    }

    @Test
    void testRangeNodeWithListNodes() {
        RangeNode node = new RangeNode();
        PipeNode pipeNode = new PipeNode("range");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("collection"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        ListNode rangeList = new ListNode();
        rangeList.append(new TextNode("Item: "));
        node.setIfListNode(rangeList);

        assertNotNull(node.getIfListNode());
    }

    @Test
    void testRangeNodeToString() {
        RangeNode node = new RangeNode();
        PipeNode pipeNode = new PipeNode("range");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("data"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        String result = node.toString();
        assertTrue(result.contains("{{range"));
        assertTrue(result.contains("{{end}}"));
    }
}
