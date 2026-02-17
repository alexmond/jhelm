package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

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

    @Test
    void testAdd() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ add 2 3 }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testAdd1() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ add1 5 }}", new HashMap<>(), writer);
        assertEquals("6", writer.toString());
    }

    @Test
    void testSub() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ sub 5 3 }}", new HashMap<>(), writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testMul() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mul 3 4 }}", new HashMap<>(), writer);
        assertEquals("12", writer.toString());
    }

    @Test
    void testDiv() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ div 10 2 }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testMod() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mod 10 3 }}", new HashMap<>(), writer);
        assertEquals("1", writer.toString());
    }

    @Test
    void testMax() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ max 2 5 3 }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testMin() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ min 2 5 3 }}", new HashMap<>(), writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testCeil() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ ceil 3.2 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("4"));
    }

    @Test
    void testFloor() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ floor 3.8 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("3"));
    }

    @Test
    void testRound() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ round 3.5 0 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("4"));
    }

    @Test
    void testAddf() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ addf 2.5 3.5 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("6"));
    }

    @Test
    void testMulf() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mulf 2.5 4.0 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("10"));
    }

    @Test
    void testDivf() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ divf 10.0 2.0 }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("5"));
    }

    @Test
    void testLenFunc() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ len \"hello\" }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testSeq() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $s := seq 1 3 }}{{ len $s }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }

    @Test
    void testUntil() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $u := until 3 }}{{ len $u }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }

    @Test
    void testUntilStep() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $u := untilStep 0 10 2 }}{{ len $u }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testToInt() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ int \"42\" }}", new HashMap<>(), writer);
        assertEquals("42", writer.toString());
    }

    @Test
    void testToInt64() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ int64 \"123\" }}", new HashMap<>(), writer);
        assertEquals("123", writer.toString());
    }

    @Test
    void testToFloat64() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ float64 \"3.14\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("3.14"));
    }

    @Test
    void testToString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ toString 42 }}", new HashMap<>(), writer);
        assertEquals("42", writer.toString());
    }

    @Test
    void testEdgeCaseMin() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ min 5 }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testEdgeCaseMax() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ max 5 }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testAddEmptyArgs() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ add }}", new HashMap<>(), writer);
        assertEquals("0", writer.toString());
    }
}

