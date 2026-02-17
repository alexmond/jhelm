package org.alexmond.jhelm.gotemplate;

import org.alexmond.jhelm.gotemplate.internal.parse.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class GoTemplateTest {

    @Test
    void testParseUnnamedTemplate() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("Hello {{ .name }}!");
        assertNotNull(template);
    }

    @Test
    void testParseFromReader() throws TemplateParseException, IOException {
        GoTemplate template = new GoTemplate();
        StringReader reader = new StringReader("Hello {{ .name }}!");
        template.parse("test", reader);
        assertNotNull(template);
    }

    @Test
    void testExecuteMainTemplate() throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello {{ .name }}!");

        StringWriter writer = new StringWriter();
        java.util.Map<String, Object> data = java.util.Map.of("name", "World");
        template.execute(data, writer);

        assertEquals("Hello World!", writer.toString());
    }

    @Test
    void testHasTemplate() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("exists", "Hello!");

        assertTrue(template.hasTemplate("exists"));
        assertFalse(template.hasTemplate("not-exists"));
    }

    @Test
    void testRootWithoutName() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello!");

        Node root = template.root();
        assertNotNull(root);
    }

    @ParameterizedTest
    @CsvSource({"main, Hello!", "secondary, Goodbye!"})
    void testRootWithName(String name, String content) throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello!");
        template.parse("secondary", "Goodbye!");

        Node root = template.root(name);
        assertNotNull(root);
    }

    @Test
    void testRootWithNonExistentName() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello!");

        Node root = template.root("nonexistent");
        assertNull(root);
    }

    @Test
    void testParseMultipleTemplates() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("first", "First template");
        template.parse("second", "Second template");

        assertTrue(template.hasTemplate("first"));
        assertTrue(template.hasTemplate("second"));
    }

    @Test
    void testExecuteNamedTemplate() throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse("greeting", "Hello {{ .name }}!");

        StringWriter writer = new StringWriter();
        java.util.Map<String, Object> data = java.util.Map.of("name", "Alice");
        template.execute("greeting", data, writer);

        assertEquals("Hello Alice!", writer.toString());
    }

    @Test
    void testParseAndExecuteUnnamedTemplate() throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse("{{ .value }}");

        StringWriter writer = new StringWriter();
        java.util.Map<String, Object> data = java.util.Map.of("value", "test");
        template.execute(data, writer);

        assertEquals("test", writer.toString());
    }

    @Test
    void testParseFromInputStream() throws IOException, TemplateParseException {
        GoTemplate template = new GoTemplate();
        String templateText = "Hello {{ .name }}!";
        java.io.InputStream inputStream = new java.io.ByteArrayInputStream(templateText.getBytes());

        template.parse("test", inputStream);
        assertTrue(template.hasTemplate("test"));
    }

    @Test
    void testExecuteNonExistentTemplateThrows() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello!");

        StringWriter writer = new StringWriter();
        assertThrows(TemplateNotFoundException.class,
                () -> template.execute("nonexistent", new java.util.HashMap<>(), writer));
    }

    @Test
    void testExecuteNullTemplateNameThrows() throws TemplateParseException {
        GoTemplate template = new GoTemplate();
        template.parse("main", "Hello!");

        StringWriter writer = new StringWriter();
        assertThrows(TemplateNotFoundException.class,
                () -> template.execute(null, new java.util.HashMap<>(), writer));
    }

    @Test
    void testParseInvalidTemplateThrows() {
        GoTemplate template = new GoTemplate();
        assertThrows(TemplateParseException.class,
                () -> template.parse("bad", "{{ .foo | nonExistentFunc }}"));
    }

    @Test
    void testParseMultipleTemplatesWithSameName() throws TemplateParseException, IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        // First parse sets the main template name
        template.parse("main", "First");

        // Second parse with different name - should not override the main template name
        template.parse("secondary", "Second");

        // Both templates should exist
        assertTrue(template.hasTemplate("main"));
        assertTrue(template.hasTemplate("secondary"));

        // Main template name should still be "main"
        StringWriter writer = new StringWriter();
        template.execute("main", new java.util.HashMap<>(), writer);
        assertEquals("First", writer.toString());
    }
}
