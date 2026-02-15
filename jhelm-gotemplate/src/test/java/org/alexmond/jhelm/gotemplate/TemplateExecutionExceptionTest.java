package org.alexmond.jhelm.gotemplate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateExecutionExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Execution error: variable not found";
        TemplateExecutionException exception = new TemplateExecutionException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Execution error: variable not found";
        Throwable cause = new NullPointerException("Null value");
        TemplateExecutionException exception = new TemplateExecutionException(message, cause);

        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testInheritanceFromTemplateException() {
        TemplateExecutionException exception = new TemplateExecutionException("Test");
        assertTrue(exception instanceof TemplateException);
        assertTrue(exception instanceof Exception);
    }
}
