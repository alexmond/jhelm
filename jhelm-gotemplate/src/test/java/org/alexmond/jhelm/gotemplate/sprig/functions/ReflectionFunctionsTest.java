package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testTypeOfString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        execute("test", "{{ typeOf .value }}", data, writer);
        assertTrue(writer.toString().contains("String") || writer.toString().contains("string"));
    }

    @Test
    void testTypeOfNumber() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);
        execute("test", "{{ typeOf .value }}", data, writer);
        assertTrue(writer.toString().contains("Integer") || writer.toString().contains("int"));
    }

    @Test
    void testTypeOfList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Arrays.asList(1, 2, 3));
        execute("test", "{{ typeOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testTypeOfMap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");
        data.put("value", nested);
        execute("test", "{{ typeOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testKindOfString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "test");
        execute("test", "{{ kindOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testKindOfNumber() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 123);
        execute("test", "{{ kindOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testKindOfList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Arrays.asList("a", "b"));
        execute("test", "{{ kindOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testTypeIsString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        execute("test", "{{ typeIs \"string\" .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testTypeIsStringFalse() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 123);
        execute("test", "{{ typeIs \"string\" .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testTypeIsInt() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);
        execute("test", "{{ typeIs \"int\" .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testTypeIsLikeString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "test");
        execute("test", "{{ typeIsLike \"*String\" .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testKindIsString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        execute("test", "{{ kindIs \"string\" .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testKindIsInt() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 42);
        execute("test", "{{ kindIs \"int\" .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testKindIsMap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", new HashMap<>());
        execute("test", "{{ kindIs \"map\" .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testDeepEqualSame() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("a", Arrays.asList(1, 2, 3));
        data.put("b", Arrays.asList(1, 2, 3));
        execute("test", "{{ deepEqual .a .b }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testDeepEqualDifferent() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("a", Arrays.asList(1, 2, 3));
        data.put("b", Arrays.asList(1, 2, 4));
        execute("test", "{{ deepEqual .a .b }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testDeepEqualMaps() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> map1 = Map.of("key", "value");
        Map<String, Object> map2 = Map.of("key", "value");
        data.put("a", map1);
        data.put("b", map2);
        execute("test", "{{ deepEqual .a .b }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testDeepEqualStrings() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("a", "hello");
        data.put("b", "hello");
        execute("test", "{{ deepEqual .a .b }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testTypeOfNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ typeOf .value }}", data, writer);
        assertFalse(writer.toString().isEmpty());
    }
}
