package org.alexmond.jhelm.gotemplate.helm.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversionFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testFromJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $data := fromJson \"{\\\"name\\\":\\\"John\\\"}\" }}{{ $data.name }}", new HashMap<>(), writer);
        assertEquals("John", writer.toString());
    }

    @Test
    void testMustFromJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $data := mustFromJson \"{\\\"age\\\":30}\" }}{{ $data.age }}", new HashMap<>(), writer);
        assertEquals("30", writer.toString());
    }

    @Test
    void testFromJsonArray() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $arr := fromJsonArray \"[1,2,3]\" }}{{ len $arr }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }

    @Test
    void testMustFromJsonArray() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $arr := mustFromJsonArray \"[\\\"a\\\",\\\"b\\\"]\" }}{{ len $arr }}", new HashMap<>(), writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testToJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        execute("test", "{{ toJson .name }}", data, writer);
        assertTrue(writer.toString().contains("Alice"));
    }

    @Test
    void testMustToJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);
        execute("test", "{{ mustToJson .value }}", data, writer);
        assertTrue(writer.toString().contains("42"));
    }

    @Test
    void testToPrettyJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> obj = Map.of("key", "value");
        data.put("obj", obj);
        execute("test", "{{ toPrettyJson .obj }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testMustToPrettyJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> obj = Map.of("a", 1, "b", 2);
        data.put("obj", obj);
        execute("test", "{{ mustToPrettyJson .obj }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testToRawJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello");
        execute("test", "{{ toRawJson .text }}", data, writer);
        assertTrue(writer.toString().contains("hello"));
    }

    @Test
    void testMustToRawJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("num", 123);
        execute("test", "{{ mustToRawJson .num }}", data, writer);
        assertTrue(writer.toString().contains("123"));
    }

    @Test
    void testFromYaml() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $data := fromYaml \"name: Bob\" }}{{ $data.name }}", new HashMap<>(), writer);
        assertEquals("Bob", writer.toString());
    }

    @Test
    void testMustFromYaml() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $data := mustFromYaml \"count: 5\" }}{{ $data.count }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testFromYamlArray() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $arr := fromYamlArray \"- a\\n- b\\n- c\" }}{{ len $arr }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }

    @Test
    void testMustFromYamlArray() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $arr := mustFromYamlArray \"- x\\n- y\" }}{{ len $arr }}", new HashMap<>(), writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testToYaml() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> obj = Map.of("key", "value");
        data.put("obj", obj);
        execute("test", "{{ toYaml .obj }}", data, writer);
        assertTrue(writer.toString().contains("key"));
        assertTrue(writer.toString().contains("value"));
    }

    @Test
    void testMustToYaml() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> obj = Map.of("name", "test");
        data.put("obj", obj);
        execute("test", "{{ mustToYaml .obj }}", data, writer);
        assertTrue(writer.toString().contains("name"));
    }
}
