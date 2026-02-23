package org.alexmond.jhelm.gotemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemplateNotFoundExceptionTest {

	@Test
	void testConstructorWithMessage() {
		String message = "Template 'user-profile' not found";
		TemplateNotFoundException exception = new TemplateNotFoundException(message);

		assertEquals(message, exception.getMessage());
		assertNull(exception.getCause());
	}

	@Test
	void testConstructorWithMessageAndCause() {
		String message = "Template 'user-profile' not found";
		Throwable cause = new IllegalStateException("Template registry empty");
		TemplateNotFoundException exception = new TemplateNotFoundException(message, cause);

		assertEquals(message, exception.getMessage());
		assertSame(cause, exception.getCause());
	}

	@Test
	void testInheritanceFromTemplateException() {
		TemplateNotFoundException exception = new TemplateNotFoundException("Test");
		assertTrue(exception instanceof TemplateException);
		assertTrue(exception instanceof Exception);
	}

}
