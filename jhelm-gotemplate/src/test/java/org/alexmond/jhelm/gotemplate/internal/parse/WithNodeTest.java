package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WithNodeTest {

    @Test
    void testWithNodeExtendsBranchNode() {
        WithNode node = new WithNode();
        assertTrue(node instanceof BranchNode);
        assertTrue(node instanceof Node);
    }

    @Test
    void testWithNodeWithPipeNode() {
        WithNode node = new WithNode();
        PipeNode pipeNode = new PipeNode("with");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("user"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        assertNotNull(node.getPipeNode());
        assertEquals("with", node.getPipeNode().getContext());
    }

    @Test
    void testWithNodeWithListNodes() {
        WithNode node = new WithNode();
        PipeNode pipeNode = new PipeNode("with");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("data"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        ListNode withList = new ListNode();
        withList.append(new TextNode("Data exists"));
        node.setIfListNode(withList);

        ListNode elseList = new ListNode();
        elseList.append(new TextNode("No data"));
        node.setElseListNode(elseList);

        assertNotNull(node.getIfListNode());
        assertNotNull(node.getElseListNode());
    }

    @Test
    void testWithNodeToString() {
        WithNode node = new WithNode();
        PipeNode pipeNode = new PipeNode("with");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("config"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        String result = node.toString();
        assertTrue(result.contains("{{with"));
        assertTrue(result.contains("{{end}}"));
    }
}
