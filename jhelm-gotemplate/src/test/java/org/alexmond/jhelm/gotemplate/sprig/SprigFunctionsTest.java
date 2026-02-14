package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Template;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SprigFunctionsTest {

    @Test
    void testTrunc() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ trunc 5 .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "Hello World");
        template.execute(writer, data);

        assertEquals("Hello", writer.toString());
    }

    @Test
    void testJoinWithList() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ join \",\" .items }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "c"));
        template.execute(writer, data);

        assertEquals("a,b,c", writer.toString());
    }

    @Test
    void testRequired() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ required \"value is required\" .value }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "test");
        template.execute(writer, data);

        assertEquals("test", writer.toString());
    }

    @Test
    void testRequiredThrowsOnEmpty() {
        Template template = new Template("test");
        assertThrows(Exception.class, () -> {
            template.parse("{{ required \"value is required\" .value }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("value", "");
            template.execute(writer, data);
        });
    }

    @Test
    void testToJson() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ toJson .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello");
        template.execute(writer, data);

        assertEquals("\"hello\"", writer.toString());
    }

    @Test
    void testMustRegexReplaceAllLiteral() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ mustRegexReplaceAllLiteral \"world\" \"universe\" .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello world");
        template.execute(writer, data);

        assertEquals("hello universe", writer.toString());
    }

    @Test
    void testHtpasswd() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ htpasswd \"user\" \"pass\" }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        String result = writer.toString();
        assertTrue(result.startsWith("user:$2y$"));
    }

    @Test
    void testTuple() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $t := tuple \"a\" \"b\" \"c\" }}{{ index $t 0 }},{{ index $t 1 }},{{ index $t 2 }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("a,b,c", writer.toString());
    }

    @Test
    void testTupleWithRange() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ range tuple \"x\" \"y\" \"z\" }}{{ . }} {{ end }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("x y z ", writer.toString());
    }

    @Test
    void testFirst() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ first .items }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("apple", "banana", "cherry"));
        template.execute(writer, data);

        assertEquals("apple", writer.toString());
    }

    @Test
    void testFirstWithString() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ first .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "hello");
        template.execute(writer, data);

        assertEquals("h", writer.toString());
    }

    @Test
    void testUniq() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ uniq .items }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "a", "c", "b"));
        template.execute(writer, data);

        String result = writer.toString();
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testSortAlpha() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ range sortAlpha .items }}{{ . }} {{ end }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("cherry", "apple", "banana"));
        template.execute(writer, data);

        String result = writer.toString().trim();
        // Should be sorted: apple banana cherry
        assertEquals("apple banana cherry", result);
    }

    @Test
    void testSet() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $dict := dict }}{{ $_ := set $dict \"key\" \"value\" }}{{ index $dict \"key\" }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("value", writer.toString());
    }

    @Test
    void testRandAlphaNum() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ randAlphaNum 10 }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        String result = writer.toString();
        assertEquals(10, result.length());
        assertTrue(result.matches("[A-Za-z0-9]+"));
    }

    @Test
    void testRandAlpha() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ randAlpha 8 }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        String result = writer.toString();
        assertEquals(8, result.length());
        assertTrue(result.matches("[A-Za-z]+"));
    }

    @Test
    void testGenCA() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $ca := genCA \"test\" 365 }}{{ $ca.Cert }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        String result = writer.toString();
        assertTrue(result.contains("BEGIN CERTIFICATE"));
    }

    @Test
    void testKindIs() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ kindIs \"string\" .value }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        template.execute(writer, data);

        assertEquals("true", writer.toString());
    }

    @Test
    void testTypeIs() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ typeIs \"string\" .value }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("value", "hello");
        template.execute(writer, data);

        assertEquals("true", writer.toString());
    }

    @Test
    void testHasKey() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ hasKey .dict \"name\" }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "test");
        data.put("dict", dict);
        template.execute(writer, data);

        assertEquals("true", writer.toString());
    }

    @Test
    void testGet() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ get .dict \"name\" }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> dict = new HashMap<>();
        dict.put("name", "test");
        data.put("dict", dict);
        template.execute(writer, data);

        assertEquals("test", writer.toString());
    }

    @Test
    void testWithout() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ without .items \"b\" }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "c"));
        template.execute(writer, data);

        String result = writer.toString();
        assertTrue(result.contains("a"));
        assertTrue(result.contains("c"));
        assertFalse(result.contains("\"b\""));
    }

    @Test
    void testSplitList() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ splitList \",\" .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "a,b,c");
        template.execute(writer, data);

        String result = writer.toString();
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testUnset() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $dict := dict \"key1\" \"value1\" \"key2\" \"value2\" }}{{ $_ := unset $dict \"key1\" }}{{ hasKey $dict \"key1\" }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("false", writer.toString());
    }

    @Test
    void testSemver() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $v := semver \"v1.2.3\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("1.2.3", writer.toString());
    }

    @Test
    void testAdd1() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ add1 5 }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("6", writer.toString());
    }

    @Test
    void testRandNumeric() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $r := randNumeric 10 }}{{ len $r }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("10", writer.toString());
    }

    @Test
    void testReverse() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $list := list \"a\" \"b\" \"c\" }}{{ range reverse $list }}{{ . }}{{ end }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("cba", writer.toString());
    }

    @Test
    void testIndent() throws IOException, TemplateException {
        Template template = new Template("test");
        // Test with data containing actual newlines
        template.parse("{{ indent 4 .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "line1\nline2");  // Actual newline character in Java string
        template.execute(writer, data);

        String result = writer.toString();
        assertEquals("    line1\n    line2", result);
    }

    @Test
    void testRegexReplaceAll() throws IOException, TemplateException {
        Template template = new Template("test");
        // Test Sprig signature: regexReplaceAll pattern text replacement
        template.parse("{{ regexReplaceAll \"[^a-z]\" \"hello123world\" \"-\" }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        // Should replace all non-lowercase letters with hyphens
        assertEquals("hello---world", writer.toString());
    }

    @Test
    void testReplaceNewlines() throws IOException, TemplateException {
        Template template = new Template("test");
        // Test with data that has actual newlines
        template.parse("{{ replace \"\\n\" \",\" .text }}");

        StringWriter writer = new StringWriter();
        Map<String, Object> data = new HashMap<>();
        data.put("text", "line1\nline2\nline3");
        template.execute(writer, data);

        // Should replace actual newlines with commas
        assertEquals("line1,line2,line3", writer.toString());
    }

    @Test
    void testGenSignedCert() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $cert := genSignedCert \"example.com\" nil nil 365 nil }}{{ hasKey $cert \"Cert\" }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("true", writer.toString());
    }

    @Test
    void testRandAscii() throws IOException, TemplateException {
        Template template = new Template("test");
        template.parse("{{ $r := randAscii 15 }}{{ len $r }}");

        StringWriter writer = new StringWriter();
        template.execute(writer, new HashMap<>());

        assertEquals("15", writer.toString());
    }
}
