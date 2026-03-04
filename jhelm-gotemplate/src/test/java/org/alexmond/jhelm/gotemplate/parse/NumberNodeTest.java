package org.alexmond.jhelm.gotemplate.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.alexmond.jhelm.gotemplate.util.Complex;
import org.junit.jupiter.api.Test;

class NumberNodeTest {

	@Test
	void testConstructorAndGetText() {
		NumberNode node = new NumberNode("42");
		assertEquals("42", node.getText());
		assertEquals("42", node.toString());
	}

	@Test
	void testSetIntValue() {
		NumberNode node = new NumberNode("42");
		node.setIsInt(true);
		node.setIntValue(42L);

		assertTrue(node.isInt());
		assertEquals(42L, node.getIntValue());
	}

	@Test
	void testSetFloatValue() {
		NumberNode node = new NumberNode("3.14");
		node.setIsFloat(true);
		node.setFloatValue(3.14);

		assertTrue(node.isFloat());
		assertEquals(3.14, node.getFloatValue(), 0.0001);
	}

	@Test
	void testSetComplexValue() {
		NumberNode node = new NumberNode("3+4i");
		Complex complex = new Complex(3.0, 4.0);
		node.setIsComplex(true);
		node.setComplex(complex);

		assertTrue(node.isComplex());
		assertEquals(complex, node.getComplexValue());
	}

	@Test
	void testMultipleSetters() {
		NumberNode node = new NumberNode("123");
		node.setIsInt(true);
		node.setIntValue(123L);

		assertTrue(node.isInt());
		assertFalse(node.isFloat());
		assertFalse(node.isComplex());
	}

	@Test
	void testNegativeNumber() {
		NumberNode node = new NumberNode("-42");
		assertEquals("-42", node.getText());
	}

	@Test
	void testDecimalNumber() {
		NumberNode node = new NumberNode("3.14159");
		assertEquals("3.14159", node.getText());
	}

}
