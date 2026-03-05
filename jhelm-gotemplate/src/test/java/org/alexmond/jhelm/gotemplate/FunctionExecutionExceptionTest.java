package org.alexmond.jhelm.gotemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class FunctionExecutionExceptionTest {

	@Test
	void testMessageOnlyConstructor() {
		FunctionExecutionException ex = new FunctionExecutionException("something failed");
		assertEquals("something failed", ex.getMessage());
		assertNull(ex.getCause());
		assertNull(ex.getFunctionName());
	}

	@Test
	void testMessageAndCauseConstructor() {
		IllegalArgumentException cause = new IllegalArgumentException("bad arg");
		FunctionExecutionException ex = new FunctionExecutionException("something failed", cause);
		assertEquals("something failed", ex.getMessage());
		assertSame(cause, ex.getCause());
		assertNull(ex.getFunctionName());
	}

	@Test
	void testFunctionNameConstructor() {
		IllegalArgumentException cause = new IllegalArgumentException("bad arg");
		FunctionExecutionException ex = new FunctionExecutionException("mustToYaml", "input is null", cause);
		assertEquals("mustToYaml: input is null", ex.getMessage());
		assertEquals("mustToYaml", ex.getFunctionName());
		assertSame(cause, ex.getCause());
	}

	@Test
	void testFunctionNameConstructorWithNullCause() {
		FunctionExecutionException ex = new FunctionExecutionException("required", "value is required", null);
		assertEquals("required: value is required", ex.getMessage());
		assertEquals("required", ex.getFunctionName());
		assertNull(ex.getCause());
	}

}
