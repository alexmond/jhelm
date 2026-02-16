package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Functions;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SprigFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testTrunc() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "Hello World");
        execute("test", "{{ trunc 5 .text }}", data, writer);

        assertEquals("Hello", writer.toString());
    }

    @Test
    void testJoinWithList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "c"));
        execute("test", "{{ join \",\" .items }}", data, writer);

        assertEquals("a,b,c", writer.toString());
    }

    @Test
    void testRequired() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "test");
        execute("test", "{{ required \"value is required\" .value }}", data, writer);

        assertEquals("test", writer.toString());
    }

    @Test
    void testRequiredThrowsOnEmpty() {
        assertThrows(Exception.class, () -> {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("value", "");
            execute("test", "{{ required \"value is required\" .value }}", data, writer);
        });
    }

    @Test
    void testToJson() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello");
        execute("test", "{{ toJson .text }}", data, writer);

        assertEquals("\"hello\"", writer.toString());
    }

    @Test
    void testMustRegexReplaceAllLiteral() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello world");
        execute("test", "{{ mustRegexReplaceAllLiteral \"world\" \"universe\" .text }}", data, writer);

        assertEquals("hello universe", writer.toString());
    }

    @Test
    void testHtpasswd() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ htpasswd \"user\" \"pass\" }}", new HashMap<>(), writer);

        String result = writer.toString();
        assertTrue(result.startsWith("user:$2y$"));
    }

    @Test
    void testTuple() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $t := tuple \"a\" \"b\" \"c\" }}{{ index $t 0 }},{{ index $t 1 }},{{ index $t 2 }}", new HashMap<>(), writer);

        assertEquals("a,b,c", writer.toString());
    }

    @Test
    void testTupleWithRange() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ range tuple \"x\" \"y\" \"z\" }}{{ . }} {{ end }}", new HashMap<>(), writer);

        assertEquals("x y z ", writer.toString());
    }

    @Test
    void testFirst() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("apple", "banana", "cherry"));
        execute("test", "{{ first .items }}", data, writer);

        assertEquals("apple", writer.toString());
    }

    @Test
    void testFirstWithString() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello");
        execute("test", "{{ first .text }}", data, writer);

        assertEquals("h", writer.toString());
    }

    @Test
    void testUniq() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "a", "c", "b"));
        execute("test", "{{ uniq .items }}", data, writer);

        String result = writer.toString();
        // Result order might depend on implementation but should contain unique elements
        assertTrue(result.contains("a") && result.contains("b") && result.contains("c"));
    }

    @Test
    void testSortAlpha() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("cherry", "apple", "banana"));
        execute("test", "{{ range sortAlpha .items }}{{ . }} {{ end }}", data, writer);

        String result = writer.toString().trim();
        assertEquals("apple banana cherry", result);
    }

    @Test
    void testSet() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $dict := dict }}{{ $_ := set $dict \"key\" \"value\" }}{{ index $dict \"key\" }}", new HashMap<>(), writer);

        assertEquals("value", writer.toString());
    }

    @Test
    void testRandAlphaNum() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ randAlphaNum 10 }}", new HashMap<>(), writer);

        String result = writer.toString();
        assertEquals(10, result.length());
        assertTrue(result.matches("[A-Za-z0-9]+"));
    }

    @Test
    void testRandAlpha() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ randAlpha 8 }}", new HashMap<>(), writer);

        String result = writer.toString();
        assertEquals(8, result.length());
        assertTrue(result.matches("[A-Za-z]+"));
    }

    @Test
    void testGenCA() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $ca := genCA \"foo-ca\" 365 }}{{ $ca.Cert }}", new HashMap<>(), writer);

        String result = writer.toString();
        assertTrue(result.contains("BEGIN CERTIFICATE"));
    }

    @Test
    void testKindIs() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        execute("test", "{{ kindIs \"string\" .value }}", data, writer);

        assertEquals("true", writer.toString());
    }

    @Test
    void testTypeIs() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        execute("test", "{{ typeIs \"string\" .value }}", data, writer);

        assertEquals("true", writer.toString());
    }

    @Test
    void testHasKey() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "test");
        data.put("dict", dict);
        execute("test", "{{ hasKey .dict \"name\" }}", data, writer);

        assertEquals("true", writer.toString());
    }

    @Test
    void testGet() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "test");
        data.put("dict", dict);
        execute("test", "{{ get .dict \"name\" }}", data, writer);

        assertEquals("test", writer.toString());
    }

    @Test
    void testWithout() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "c"));
        execute("test", "{{ without .items \"b\" }}", data, writer);

        String result = writer.toString();
        assertTrue(result.contains("a") && result.contains("c") && !result.contains("\"b\""));
    }

    @Test
    void testSplitList() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "a,b,c");
        execute("test", "{{ splitList \",\" .text }}", data, writer);

        String result = writer.toString();
        assertTrue(result.contains("a") && result.contains("b") && result.contains("c"));
    }

    @Test
    void testUnset() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $dict := dict \"key1\" \"value1\" \"key2\" \"value2\" }}{{ $_ := unset $dict \"key1\" }}{{ hasKey $dict \"key1\" }}", new HashMap<>(), writer);

        assertEquals("false", writer.toString());
    }

    @Test
    void testSemver() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"v1.2.3\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}", new HashMap<>(), writer);

        assertEquals("1.2.3", writer.toString());
    }

    @Test
    void testAdd1() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ add1 5 }}", new HashMap<>(), writer);

        assertEquals("6", writer.toString());
    }

    @Test
    void testRandNumeric() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $r := randNumeric 10 }}{{ len $r }}", new HashMap<>(), writer);

        assertEquals("10", writer.toString());
    }

    @Test
    void testReverse() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $list := list \"a\" \"b\" \"c\" }}{{ range reverse $list }}{{ . }}{{ end }}", new HashMap<>(), writer);

        assertEquals("cba", writer.toString());
    }

    @Test
    void testIndent() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "line1\nline2");
        execute("test", "{{ indent 4 .text }}", data, writer);

        assertEquals("    line1\n    line2", writer.toString());
    }

    @Test
    void testRegexReplaceAll() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ regexReplaceAll \"[^a-z]\" \"hello123world\" \"-\" }}", new HashMap<>(), writer);

        assertEquals("hello---world", writer.toString());
    }

    @Test
    void testReplaceNewlines() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "line1\nline2\nline3");
        execute("test", "{{ replace \"\\n\" \",\" .text }}", data, writer);

        assertEquals("line1,line2,line3", writer.toString());
    }

    @Test
    void testGenSignedCert() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $cert := genSignedCert \"example.com\" nil nil 365 nil }}{{ hasKey $cert \"Cert\" }}", new HashMap<>(), writer);

        assertEquals("true", writer.toString());
    }

    @Test
    void testRandAscii() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $r := randAscii 15 }}{{ len $r }}", new HashMap<>(), writer);

        assertEquals("15", writer.toString());
    }
}
