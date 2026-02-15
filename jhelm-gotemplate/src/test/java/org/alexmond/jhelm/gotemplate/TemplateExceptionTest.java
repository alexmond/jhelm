package org.alexmond.jhelm.gotemplate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Template error occurred";
        TemplateException exception = new TemplateException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Template error occurred";
        Throwable cause = new RuntimeException("Root cause");
        TemplateException exception = new TemplateException(message, cause);

        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testInheritanceFromException() {
        TemplateException exception = new TemplateException("Test");
        assertTrue(exception instanceof Exception);
    }
}
