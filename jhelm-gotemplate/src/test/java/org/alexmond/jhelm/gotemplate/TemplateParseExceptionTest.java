package org.alexmond.jhelm.gotemplate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateParseExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Parse error: unexpected token";
        TemplateParseException exception = new TemplateParseException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Parse error: unexpected token";
        Throwable cause = new IllegalArgumentException("Invalid syntax");
        TemplateParseException exception = new TemplateParseException(message, cause);

        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testInheritanceFromTemplateException() {
        TemplateParseException exception = new TemplateParseException("Test");
        assertTrue(exception instanceof TemplateException);
        assertTrue(exception instanceof Exception);
    }
}
