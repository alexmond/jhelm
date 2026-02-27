package org.alexmond.jhelm.gotemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionsTest {

	// --- isTrue tests ---

	@Test
	void testIsTrueNull() {
		assertFalse(Functions.isTrue(null));
	}

	@Test
	void testIsTrueBoolean() {
		assertTrue(Functions.isTrue(true));
		assertFalse(Functions.isTrue(false));
	}

	@Test
	void testIsTrueString() {
		assertTrue(Functions.isTrue("hello"));
		assertFalse(Functions.isTrue(""));
	}

	@Test
	void testIsTrueNumber() {
		assertTrue(Functions.isTrue(1));
		assertTrue(Functions.isTrue(3.14));
		assertTrue(Functions.isTrue(-1));
		assertFalse(Functions.isTrue(0));
		assertFalse(Functions.isTrue(0.0));
	}

	@Test
	void testIsTrueCollection() {
		assertTrue(Functions.isTrue(List.of("a")));
		assertFalse(Functions.isTrue(Collections.emptyList()));
	}

	@Test
	void testIsTrueMap() {
		assertTrue(Functions.isTrue(Map.of("k", "v")));
		assertFalse(Functions.isTrue(Collections.emptyMap()));
	}

	@Test
	void testIsTrueArray() {
		assertTrue(Functions.isTrue(new int[] { 1, 2 }));
		assertFalse(Functions.isTrue(new int[] {}));
	}

	@Test
	void testIsTrueObject() {
		assertTrue(Functions.isTrue(new Object()));
	}

	// --- index function tests ---

	@Test
	void testIndexFromMap() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		Map<String, Object> map = Map.of("key", "value");
		assertEquals("value", index.invoke(new Object[] { map, "key" }));
	}

	@Test
	void testIndexFromMapMissingKey() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		Map<String, Object> map = Map.of("key", "value");
		assertNull(index.invoke(new Object[] { map, "missing" }));
	}

	@Test
	void testIndexFromList() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		List<String> list = List.of("a", "b", "c");
		assertEquals("b", index.invoke(new Object[] { list, 1 }));
	}

	@Test
	void testIndexFromArray() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		String[] arr = { "x", "y", "z" };
		assertEquals("y", index.invoke(new Object[] { arr, 1 }));
	}

	@Test
	void testIndexNullContainer() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		assertNull(index.invoke(new Object[] { null, 0 }));
	}

	@Test
	void testIndexInsufficientArgs() throws Exception {
		Function index = Functions.BUILTIN.get("index");
		assertNull(index.invoke(new Object[] { Map.of("a", 1) }));
	}

	// --- len function tests ---

	@Test
	void testLenString() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(5, len.invoke(new Object[] { "hello" }));
	}

	@Test
	void testLenEmptyString() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(0, len.invoke(new Object[] { "" }));
	}

	@Test
	void testLenList() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(3, len.invoke(new Object[] { List.of(1, 2, 3) }));
	}

	@Test
	void testLenMap() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(2, len.invoke(new Object[] { Map.of("a", 1, "b", 2) }));
	}

	@Test
	void testLenArray() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(4, len.invoke(new Object[] { new int[] { 1, 2, 3, 4 } }));
	}

	@Test
	void testLenNull() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(0, len.invoke(new Object[] { null }));
	}

	@Test
	void testLenNoArgs() throws Exception {
		Function len = Functions.BUILTIN.get("len");
		assertEquals(0, len.invoke(new Object[] {}));
	}

	// --- and / or / not tests ---

	@Test
	void testAndAllTrue() throws Exception {
		Function and = Functions.BUILTIN.get("and");
		assertEquals("last", and.invoke(new Object[] { true, "hello", "last" }));
	}

	@Test
	void testAndShortCircuit() throws Exception {
		Function and = Functions.BUILTIN.get("and");
		assertEquals(false, and.invoke(new Object[] { true, false, "never" }));
	}

	@Test
	void testAndEmpty() throws Exception {
		Function and = Functions.BUILTIN.get("and");
		assertEquals(false, and.invoke(new Object[] {}));
	}

	@Test
	void testOrFirstTrue() throws Exception {
		Function or = Functions.BUILTIN.get("or");
		assertEquals("hello", or.invoke(new Object[] { "hello", "world" }));
	}

	@Test
	void testOrAllFalse() throws Exception {
		Function or = Functions.BUILTIN.get("or");
		assertEquals("", or.invoke(new Object[] { false, null, "" }));
	}

	@Test
	void testOrEmpty() throws Exception {
		Function or = Functions.BUILTIN.get("or");
		assertEquals(false, or.invoke(new Object[] {}));
	}

	@Test
	void testNotTrue() throws Exception {
		Function not = Functions.BUILTIN.get("not");
		assertEquals(true, not.invoke(new Object[] { false }));
		assertEquals(true, not.invoke(new Object[] { null }));
		assertEquals(true, not.invoke(new Object[] { "" }));
		assertEquals(true, not.invoke(new Object[] { 0 }));
	}

	@Test
	void testNotFalse() throws Exception {
		Function not = Functions.BUILTIN.get("not");
		assertEquals(false, not.invoke(new Object[] { true }));
		assertEquals(false, not.invoke(new Object[] { "hello" }));
		assertEquals(false, not.invoke(new Object[] { 1 }));
	}

	// --- comparison tests ---

	@Test
	void testEqEqual() throws Exception {
		Function eq = Functions.BUILTIN.get("eq");
		assertEquals(true, eq.invoke(new Object[] { "a", "a" }));
		assertEquals(true, eq.invoke(new Object[] { 42, 42 }));
	}

	@Test
	void testEqNotEqual() throws Exception {
		Function eq = Functions.BUILTIN.get("eq");
		assertEquals(false, eq.invoke(new Object[] { "a", "b" }));
	}

	@Test
	void testEqMultipleArgs() throws Exception {
		Function eq = Functions.BUILTIN.get("eq");
		assertEquals(true, eq.invoke(new Object[] { "x", "x", "x" }));
		assertEquals(false, eq.invoke(new Object[] { "x", "x", "y" }));
	}

	@Test
	void testEqInsufficientArgs() throws Exception {
		Function eq = Functions.BUILTIN.get("eq");
		assertEquals(false, eq.invoke(new Object[] { "a" }));
	}

	@Test
	void testEqMixedNumericTypes() throws Exception {
		Function eq = Functions.BUILTIN.get("eq");
		// Integer vs Long — the exact case triggered by ne (len .) 4
		assertEquals(true, eq.invoke(new Object[] { Integer.valueOf(4), Long.valueOf(4L) }));
		assertEquals(true, eq.invoke(new Object[] { Long.valueOf(4L), Integer.valueOf(4) }));
		// Integer vs Double
		assertEquals(true, eq.invoke(new Object[] { Integer.valueOf(3), Double.valueOf(3.0) }));
		// Different values across types
		assertEquals(false, eq.invoke(new Object[] { Integer.valueOf(4), Long.valueOf(5L) }));
	}

	@Test
	void testNeNotEqual() throws Exception {
		Function ne = Functions.BUILTIN.get("ne");
		assertEquals(true, ne.invoke(new Object[] { "a", "b" }));
		assertEquals(false, ne.invoke(new Object[] { "a", "a" }));
	}

	@Test
	void testNeMixedNumericTypes() throws Exception {
		Function ne = Functions.BUILTIN.get("ne");
		// Integer vs Long — must return false (values are equal)
		assertEquals(false, ne.invoke(new Object[] { Integer.valueOf(4), Long.valueOf(4L) }));
		assertEquals(true, ne.invoke(new Object[] { Integer.valueOf(4), Long.valueOf(5L) }));
	}

	@Test
	void testLtNumbers() throws Exception {
		Function lt = Functions.BUILTIN.get("lt");
		assertEquals(true, lt.invoke(new Object[] { 1, 2 }));
		assertEquals(false, lt.invoke(new Object[] { 2, 1 }));
		assertEquals(false, lt.invoke(new Object[] { 1, 1 }));
	}

	@Test
	void testLtStrings() throws Exception {
		Function lt = Functions.BUILTIN.get("lt");
		assertEquals(true, lt.invoke(new Object[] { "a", "b" }));
		assertEquals(false, lt.invoke(new Object[] { "b", "a" }));
	}

	@Test
	void testLtNullHandling() throws Exception {
		Function lt = Functions.BUILTIN.get("lt");
		assertEquals(false, lt.invoke(new Object[] { null, 1 }));
		assertEquals(false, lt.invoke(new Object[] { 1, null }));
	}

	@Test
	void testLeNumbers() throws Exception {
		Function le = Functions.BUILTIN.get("le");
		assertEquals(true, le.invoke(new Object[] { 1, 2 }));
		assertEquals(true, le.invoke(new Object[] { 1, 1 }));
		assertEquals(false, le.invoke(new Object[] { 2, 1 }));
	}

	@Test
	void testGtNumbers() throws Exception {
		Function gt = Functions.BUILTIN.get("gt");
		assertEquals(true, gt.invoke(new Object[] { 2, 1 }));
		assertEquals(false, gt.invoke(new Object[] { 1, 2 }));
		assertEquals(false, gt.invoke(new Object[] { 1, 1 }));
	}

	@Test
	void testGeNumbers() throws Exception {
		Function ge = Functions.BUILTIN.get("ge");
		assertEquals(true, ge.invoke(new Object[] { 2, 1 }));
		assertEquals(true, ge.invoke(new Object[] { 1, 1 }));
		assertEquals(false, ge.invoke(new Object[] { 1, 2 }));
	}

	@Test
	void testComparisonInsufficientArgs() throws Exception {
		assertEquals(false, Functions.BUILTIN.get("lt").invoke(new Object[] { 1 }));
		assertEquals(false, Functions.BUILTIN.get("le").invoke(new Object[] {}));
		assertEquals(false, Functions.BUILTIN.get("gt").invoke(new Object[] { 1 }));
		assertEquals(false, Functions.BUILTIN.get("ge").invoke(new Object[] {}));
	}

	@Test
	void testComparisonNonComparable() throws Exception {
		Function lt = Functions.BUILTIN.get("lt");
		// Object is not Comparable
		assertEquals(false, lt.invoke(new Object[] { new Object(), new Object() }));
	}

	// --- print/printf/println tests ---

	@Test
	void testPrint() throws Exception {
		Function print = Functions.BUILTIN.get("print");
		assertEquals("abc", print.invoke(new Object[] { "a", "b", "c" }));
		assertEquals("", print.invoke(new Object[] {}));
	}

	@Test
	void testPrintf() throws Exception {
		Function printf = Functions.BUILTIN.get("printf");
		assertEquals("hello 42", printf.invoke(new Object[] { "%s %d", "hello", 42 }));
	}

	@Test
	void testPrintfGoVerbTranslation() throws Exception {
		Function printf = Functions.BUILTIN.get("printf");
		// %v -> %s
		assertEquals("test", printf.invoke(new Object[] { "%v", "test" }));
	}

	@Test
	void testPrintfEmpty() throws Exception {
		Function printf = Functions.BUILTIN.get("printf");
		assertEquals("", printf.invoke(new Object[] {}));
	}

	@Test
	void testPrintln() throws Exception {
		Function println = Functions.BUILTIN.get("println");
		assertEquals("a b c\n", println.invoke(new Object[] { "a", "b", "c" }));
	}

	// --- BUILTIN map verification ---

	@Test
	void testBuiltinContainsExpectedFunctions() {
		assertNotNull(Functions.BUILTIN.get("index"));
		assertNotNull(Functions.BUILTIN.get("len"));
		assertNotNull(Functions.BUILTIN.get("and"));
		assertNotNull(Functions.BUILTIN.get("or"));
		assertNotNull(Functions.BUILTIN.get("not"));
		assertNotNull(Functions.BUILTIN.get("eq"));
		assertNotNull(Functions.BUILTIN.get("ne"));
		assertNotNull(Functions.BUILTIN.get("lt"));
		assertNotNull(Functions.BUILTIN.get("le"));
		assertNotNull(Functions.BUILTIN.get("gt"));
		assertNotNull(Functions.BUILTIN.get("ge"));
		assertNotNull(Functions.BUILTIN.get("print"));
		assertNotNull(Functions.BUILTIN.get("printf"));
		assertNotNull(Functions.BUILTIN.get("println"));
	}

}
