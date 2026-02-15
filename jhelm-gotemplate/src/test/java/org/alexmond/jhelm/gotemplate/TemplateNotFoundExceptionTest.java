package org.alexmond.jhelm.gotemplate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
