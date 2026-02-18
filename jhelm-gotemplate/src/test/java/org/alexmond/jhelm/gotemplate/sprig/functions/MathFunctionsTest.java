package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class MathFunctionsTest {

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ add 2 3 }}       | 5",
        "{{ add1 5 }}        | 6",
        "{{ sub 5 3 }}       | 2",
        "{{ mul 3 4 }}       | 12",
        "{{ div 10 2 }}      | 5",
        "{{ mod 10 3 }}      | 1",
        "{{ max 2 5 3 }}     | 5",
        "{{ min 2 5 3 }}     | 2",
        "{{ len \"hello\" }} | 5",
        "{{ int \"42\" }}    | 42",
        "{{ int64 \"123\" }} | 123",
        "{{ toString 42 }}   | 42",
        "{{ min 5 }}         | 5",
        "{{ max 5 }}         | 5",
        "{{ add }}           | 0",
    })
    void testExactMathFunction(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ ceil 3.2 }}      | 4",
        "{{ floor 3.8 }}     | 3",
        "{{ round 3.5 0 }}   | 4",
        "{{ addf 2.5 3.5 }}  | 6",
        "{{ mulf 2.5 4.0 }}  | 10",
        "{{ divf 10.0 2.0 }} | 5",
        "{{ float64 \"3.14\" }} | 3.14",
    })
    void testApproxMathFunction(String template, String expectedContains) throws IOException, TemplateException {
        assertTrue(exec(template).contains(expectedContains));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ $s := seq 1 3 }}{{ len $s }}              | 3",
        "{{ $u := until 3 }}{{ len $u }}              | 3",
        "{{ $u := untilStep 0 10 2 }}{{ len $u }}     | 5",
    })
    void testSequenceFunction(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }
}
