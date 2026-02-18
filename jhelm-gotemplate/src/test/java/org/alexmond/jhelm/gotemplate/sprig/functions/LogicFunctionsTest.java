package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LogicFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    // Test 'default' function

    @Test
    void testDefaultWithTruthyValue() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "actual");
        execute("test", "{{ default \"fallback\" .value }}", data, writer);
        assertEquals("actual", writer.toString());
    }

    @Test
    void testDefaultWithFalsyValue() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "");
        execute("test", "{{ default \"fallback\" .value }}", data, writer);
        assertEquals("fallback", writer.toString());
    }

    @Test
    void testDefaultWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ default \"fallback\" .value }}", data, writer);
        assertEquals("fallback", writer.toString());
    }

    @Test
    void testDefaultWithZero() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 0);
        execute("test", "{{ default 42 .value }}", data, writer);
        assertEquals("42", writer.toString());
    }

    @Test
    void testDefaultWithNonZero() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 10);
        execute("test", "{{ default 42 .value }}", data, writer);
        assertEquals("10", writer.toString());
    }

    @Test
    void testDefaultWithFalse() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", false);
        execute("test", "{{ default true .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testDefaultWithEmptyList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Collections.emptyList());
        execute("test", "{{ $default := list \"a\" \"b\" }}{{ $result := default $default .value }}{{ len $result }}", data, writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testDefaultWithNonEmptyList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Arrays.asList("x", "y"));
        execute("test", "{{ $default := list \"a\" \"b\" }}{{ $result := default $default .value }}{{ len $result }}", data, writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testDefaultWithEmptyMap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Collections.emptyMap());
        execute("test", "{{ $default := dict \"key\" \"val\" }}{{ $result := default $default .value }}{{ len $result }}", data, writer);
        assertEquals("1", writer.toString());
    }

    // Test 'empty' function

    @Test
    void testEmptyWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithEmptyString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "");
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithNonEmptyString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "text");
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testEmptyWithZero() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 0);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithNonZero() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", 5);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testEmptyWithFalse() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", false);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithTrue() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", true);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testEmptyWithEmptyList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Collections.emptyList());
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithNonEmptyList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Arrays.asList("a", "b"));
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    @Test
    void testEmptyWithEmptyMap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", Collections.emptyMap());
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testEmptyWithNonEmptyMap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        data.put("value", map);
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());
    }

    // Test 'coalesce' function

    @Test
    void testCoalesceReturnsFirstTruthyValue() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ coalesce \"\" 0 \"first\" \"second\" }}", new HashMap<>(), writer);
        assertEquals("first", writer.toString());
    }

    @Test
    void testCoalesceWithAllFalsy() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ coalesce \"\" 0 false }}", new HashMap<>(), writer);
        assertEquals("", writer.toString());  // null is rendered as empty string
    }

    @Test
    void testCoalesceWithNullFirst() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("a", null);
        data.put("b", "value");
        execute("test", "{{ coalesce .a .b }}", data, writer);
        assertEquals("value", writer.toString());
    }

    @Test
    void testCoalesceWithNumbers() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("a", 0);
        data.put("b", 42);
        execute("test", "{{ coalesce .a .b }}", data, writer);
        assertEquals("42", writer.toString());
    }

    @Test
    void testCoalesceWithCollections() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("empty", Collections.emptyList());
        data.put("full", Arrays.asList("a", "b"));
        execute("test", "{{ $result := coalesce .empty .full }}{{ len $result }}", data, writer);
        assertEquals("2", writer.toString());
    }

    // Test 'ternary' function

    @Test
    void testTernaryWithTrue() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ ternary \"yes\" \"no\" true }}", new HashMap<>(), writer);
        assertEquals("yes", writer.toString());
    }

    @Test
    void testTernaryWithFalse() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ ternary \"yes\" \"no\" false }}", new HashMap<>(), writer);
        assertEquals("no", writer.toString());
    }

    @Test
    void testTernaryWithTruthyString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", "text");
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("yes", writer.toString());
    }

    @Test
    void testTernaryWithEmptyString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", "");
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("no", writer.toString());
    }

    @Test
    void testTernaryWithNonZeroNumber() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", 1);
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("yes", writer.toString());
    }

    @Test
    void testTernaryWithZero() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", 0);
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("no", writer.toString());
    }

    @Test
    void testTernaryWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", null);
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("no", writer.toString());
    }

    @Test
    void testTernaryWithNonEmptyCollection() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", Arrays.asList("a", "b"));
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("yes", writer.toString());
    }

    @Test
    void testTernaryWithEmptyCollection() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("condition", Collections.emptyList());
        execute("test", "{{ ternary \"yes\" \"no\" .condition }}", data, writer);
        assertEquals("no", writer.toString());
    }

    // Test 'fail' function

    @Test
    void testFailThrowsException() {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "Custom error message");
        assertThrows(TemplateException.class, () -> {
            StringWriter writer = new StringWriter();
            execute("test", "{{ fail .error }}", data, writer);
        });
    }

    @Test
    void testFailWithoutMessage() {
        assertThrows(TemplateException.class, () -> {
            StringWriter writer = new StringWriter();
            execute("test", "{{ fail }}", new HashMap<>(), writer);
        });
    }

    @Test
    void testFailWithLiteralMessage() {
        assertThrows(TemplateException.class, () -> {
            StringWriter writer = new StringWriter();
            execute("test", "{{ fail \"Something went wrong\" }}", new HashMap<>(), writer);
        });
    }

    // Test isTruthy with non-standard types

    @Test
    void testEmptyWithCustomObject() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", new Object());  // Custom object - should be truthy via default case
        execute("test", "{{ empty .value }}", data, writer);
        assertEquals("false", writer.toString());  // Custom objects are truthy
    }

    @Test
    void testDefaultWithCustomObject() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", new Object());  // Custom object - should be truthy via default case
        execute("test", "{{ default \"fallback\" .value }}", data, writer);
        // Custom object is truthy, so it should be returned (toString() called)
        assertFalse(writer.toString().isEmpty());
        assertNotEquals("fallback", writer.toString());
    }
}
