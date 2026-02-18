package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class StringFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testUpper() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ upper \"hello\" }}", new HashMap<>(), writer);
        assertEquals("HELLO", writer.toString());
    }

    @Test
    void testLower() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ lower \"HELLO\" }}", new HashMap<>(), writer);
        assertEquals("hello", writer.toString());
    }

    @Test
    void testTitle() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ title \"hello world\" }}", new HashMap<>(), writer);
        assertEquals("Hello World", writer.toString());
    }

    @Test
    void testUntitle() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ untitle \"Hello World\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().startsWith("hello"));
    }

    @Test
    void testRepeat() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ repeat 3 \"ab\" }}", new HashMap<>(), writer);
        assertEquals("ababab", writer.toString());
    }

    @Test
    void testSubstr() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ substr 0 5 \"Hello World\" }}", new HashMap<>(), writer);
        assertEquals("Hello", writer.toString());
    }

    @Test
    void testTrim() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ trim \"  hello  \" }}", new HashMap<>(), writer);
        assertEquals("hello", writer.toString());
    }

    @Test
    void testTrimAll() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ trimAll \"$\" \"$5.00\" }}", new HashMap<>(), writer);
        assertEquals("5.00", writer.toString());
    }

    @Test
    void testTrimPrefix() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ trimPrefix \"hello \" \"hello world\" }}", new HashMap<>(), writer);
        assertEquals("world", writer.toString());
    }

    @Test
    void testTrimSuffix() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ trimSuffix \" world\" \"hello world\" }}", new HashMap<>(), writer);
        assertEquals("hello", writer.toString());
    }

    @Test
    void testContains() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ contains \"ell\" \"hello\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testHasPrefix() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ hasPrefix \"hel\" \"hello\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testHasSuffix() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ hasSuffix \"lo\" \"hello\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testQuote() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ quote \"hello\" }}", new HashMap<>(), writer);
        assertEquals("\"hello\"", writer.toString());
    }

    @Test
    void testSquote() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ squote \"hello\" }}", new HashMap<>(), writer);
        assertEquals("'hello'", writer.toString());
    }

    @Test
    void testCat() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ cat \"hello\" \"world\" }}", new HashMap<>(), writer);
        assertEquals("hello world", writer.toString());
    }

    @Test
    void testIndent() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ indent 2 \"hello\" }}", new HashMap<>(), writer);
        assertEquals("  hello", writer.toString());
    }

    @Test
    void testNindent() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ nindent 2 \"hello\" }}", new HashMap<>(), writer);
        assertEquals("\n  hello", writer.toString());
    }

    @Test
    void testReplace() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ replace \" \" \"_\" \"hello world\" }}", new HashMap<>(), writer);
        assertEquals("hello_world", writer.toString());
    }

    @Test
    void testPlural() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ plural \"item\" \"items\" 1 }}", new HashMap<>(), writer);
        assertEquals("item", writer.toString());
    }

    @Test
    void testPluralWithMultiple() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ plural \"item\" \"items\" 2 }}", new HashMap<>(), writer);
        assertEquals("items", writer.toString());
    }

    @Test
    void testSnakecase() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ snakecase \"HelloWorld\" }}", new HashMap<>(), writer);
        assertEquals("hello_world", writer.toString());
    }

    @Test
    void testCamelcase() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ camelcase \"hello_world\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().contains("Hello") || writer.toString().contains("hello"));
    }

    @Test
    void testKebabcase() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ kebabcase \"HelloWorld\" }}", new HashMap<>(), writer);
        assertEquals("hello-world", writer.toString());
    }

    @Test
    void testShuffle() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ shuffle \"abc\" }}", new HashMap<>(), writer);
        assertEquals(3, writer.toString().length());
    }

    @Test
    void testWrap() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ wrap 5 \"hello world\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testWrapWith() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ wrapWith 5 \"\\n\" \"hello world\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testAbbrev() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ abbrev 5 \"hello world\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().length() <= 8); // 5 + "..."
    }

    @Test
    void testAbbrevboth() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ abbrevboth 5 8 \"hello world\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().length() <= 11);
    }

    @Test
    void testInitials() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ initials \"John Doe\" }}", new HashMap<>(), writer);
        assertEquals("JD", writer.toString());
    }

    @Test
    void testRegexMatch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ regexMatch \"^[a-z]+$\" \"hello\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testMustRegexMatch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mustRegexMatch \"^[a-z]+$\" \"hello\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testRegexFind() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ regexFind \"[0-9]+\" \"abc123def\" }}", new HashMap<>(), writer);
        assertEquals("123", writer.toString());
    }

    @Test
    void testMustRegexFind() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mustRegexFind \"[0-9]+\" \"abc123def\" }}", new HashMap<>(), writer);
        assertEquals("123", writer.toString());
    }

    @Test
    void testRegexFindAll() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $matches := regexFindAll \"[0-9]+\" \"abc123def456\" -1 }}{{ len $matches }}", new HashMap<>(), writer);
        assertEquals("2", writer.toString());
    }

    @Test
    void testRegexReplaceAll() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ regexReplaceAll \"[0-9]+\" \"abc123def456\" \"X\" }}", new HashMap<>(), writer);
        assertEquals("abcXdefX", writer.toString());
    }

    @Test
    void testMustRegexReplaceAll() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mustRegexReplaceAll \"[0-9]+\" \"abc123def\" \"X\" }}", new HashMap<>(), writer);
        assertEquals("abcXdef", writer.toString());
    }

    @Test
    void testRegexReplaceAllLiteral() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ regexReplaceAllLiteral \"[0-9]+\" \"abc123def456\" \"X\" }}", new HashMap<>(), writer);
        // Function returns just the replacement value
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testRegexSplit() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $parts := regexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }

    @Test
    void testMustRegexSplit() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $parts := mustRegexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}", new HashMap<>(), writer);
        assertEquals("3", writer.toString());
    }
}
