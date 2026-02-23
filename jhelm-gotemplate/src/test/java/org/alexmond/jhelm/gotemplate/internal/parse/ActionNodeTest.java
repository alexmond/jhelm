package org.alexmond.jhelm.gotemplate.internal.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionNodeTest {

	@Test
	void testActionNodeWithNullPipe() {
		ActionNode node = new ActionNode();
		assertNull(node.getPipeNode());
		assertEquals("{{}}", node.toString());
	}

	@Test
	void testActionNodeWithPipe() {
		ActionNode node = new ActionNode();
		PipeNode pipeNode = new PipeNode("test");
		CommandNode commandNode = new CommandNode();
		commandNode.append(new IdentifierNode("print"));
		pipeNode.append(commandNode);
		node.setPipeNode(pipeNode);

		assertNotNull(node.getPipeNode());
		assertEquals(pipeNode, node.getPipeNode());
		assertTrue(node.toString().contains("{{"));
		assertTrue(node.toString().contains("}}"));
	}

	@Test
	void testSetPipeNode() {
		ActionNode node = new ActionNode();
		PipeNode pipeNode = new PipeNode("ctx");
		node.setPipeNode(pipeNode);
		assertSame(pipeNode, node.getPipeNode());
	}

}
