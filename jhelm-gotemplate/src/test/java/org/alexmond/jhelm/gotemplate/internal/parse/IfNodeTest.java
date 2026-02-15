package org.alexmond.jhelm.gotemplate.internal.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IfNodeTest {

    @Test
    void testIfNodeExtendsBranchNode() {
        IfNode node = new IfNode();
        assertTrue(node instanceof BranchNode);
        assertTrue(node instanceof Node);
    }

    @Test
    void testIfNodeWithCondition() {
        IfNode node = new IfNode();
        PipeNode pipeNode = new PipeNode("if");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("isValid"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        assertNotNull(node.getPipeNode());
        assertEquals("if", node.getPipeNode().getContext());
    }

    @Test
    void testIfNodeWithIfList() {
        IfNode node = new IfNode();
        PipeNode pipeNode = new PipeNode("if");
        CommandNode cmd = new CommandNode();
        cmd.append(new BoolNode("true"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        ListNode ifList = new ListNode();
        ifList.append(new TextNode("True branch"));
        node.setIfListNode(ifList);

        assertNotNull(node.getIfListNode());
    }

    @Test
    void testIfNodeWithElseList() {
        IfNode node = new IfNode();
        PipeNode pipeNode = new PipeNode("if");
        CommandNode cmd = new CommandNode();
        cmd.append(new BoolNode("false"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        ListNode elseList = new ListNode();
        elseList.append(new TextNode("False branch"));
        node.setElseListNode(elseList);

        assertNotNull(node.getElseListNode());
    }

    @Test
    void testIfNodeToString() {
        IfNode node = new IfNode();
        PipeNode pipeNode = new PipeNode("if");
        CommandNode cmd = new CommandNode();
        cmd.append(new IdentifierNode("test"));
        pipeNode.append(cmd);
        node.setPipeNode(pipeNode);

        String result = node.toString();
        assertTrue(result.contains("{{if"));
        assertTrue(result.contains("{{end}}"));
    }
}
