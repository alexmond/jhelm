package org.alexmond.jhelm.gotemplate.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DotNodeTest {

	@Test
	void testToString() {
		DotNode node = new DotNode();
		assertEquals(".", node.toString());
	}

	@Test
	void testImplementsNode() {
		DotNode node = new DotNode();
		assertTrue(node instanceof Node);
	}

	@Test
	void testMultipleInstances() {
		DotNode node1 = new DotNode();
		DotNode node2 = new DotNode();
		assertEquals(node1.toString(), node2.toString());
	}

}
