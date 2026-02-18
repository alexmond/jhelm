package org.alexmond.jhelm.gotemplate.helm.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    private String exec(String template) throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", template, new HashMap<>(), writer);
        return writer.toString();
    }

    private String execWithData(String template, Map<String, Object> data) throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", template, data, writer);
        return writer.toString();
    }

    // JSON parsing tests

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ $data := fromJson \"{\\\"name\\\":\\\"John\\\"}\" }}{{ $data.name }}             | John",
        "{{ $data := mustFromJson \"{\\\"age\\\":30}\" }}{{ $data.age }}                     | 30",
        "{{ $arr := fromJsonArray \"[1,2,3]\" }}{{ len $arr }}                                | 3",
        "{{ $arr := mustFromJsonArray \"[\\\"a\\\",\\\"b\\\"]\" }}{{ len $arr }}              | 2",
    })
    void testJsonParsing(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    // JSON serialization tests

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "toJson       | name  | Alice | Alice",
        "mustToJson   | value | 42    | 42",
        "toRawJson    | text  | hello | hello",
        "mustToRawJson| num   | 123   | 123",
    })
    void testJsonSerialization(String func, String key, Object value, String expectedContains) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value instanceof String s && s.matches("\\d+") ? Integer.parseInt(s) : value);
        String result = execWithData("{{ " + func + " ." + key + " }}", data);
        assertTrue(result.contains(expectedContains.toString()));
    }

    @Test
    void testToPrettyJson() throws IOException, TemplateException {
        Map<String, Object> data = Map.of("obj", Map.of("key", "value"));
        assertFalse(execWithData("{{ toPrettyJson .obj }}", new HashMap<>(data)).isEmpty());
    }

    @Test
    void testMustToPrettyJson() throws IOException, TemplateException {
        Map<String, Object> data = Map.of("obj", Map.of("a", 1, "b", 2));
        assertFalse(execWithData("{{ mustToPrettyJson .obj }}", new HashMap<>(data)).isEmpty());
    }

    // YAML parsing tests

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ $data := fromYaml \"name: Bob\" }}{{ $data.name }}                  | Bob",
        "{{ $data := mustFromYaml \"count: 5\" }}{{ $data.count }}              | 5",
        "{{ $arr := fromYamlArray \"- a\\n- b\\n- c\" }}{{ len $arr }}          | 3",
        "{{ $arr := mustFromYamlArray \"- x\\n- y\" }}{{ len $arr }}            | 2",
    })
    void testYamlParsing(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    // YAML serialization tests

    @ParameterizedTest
    @CsvSource({"toYaml, name", "mustToYaml, name"})
    void testYamlSerialization(String func, String expectedContains) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("obj", Map.of("name", "test"));
        String result = execWithData("{{ " + func + " .obj }}", data);
        assertTrue(result.contains(expectedContains));
    }
}
