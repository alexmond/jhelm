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

class StringFunctionsTest {

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
        "{{ upper \"hello\" }}                          | HELLO",
        "{{ lower \"HELLO\" }}                          | hello",
        "{{ title \"hello world\" }}                    | Hello World",
        "{{ repeat 3 \"ab\" }}                          | ababab",
        "{{ substr 0 5 \"Hello World\" }}               | Hello",
        "{{ trim \"  hello  \" }}                       | hello",
        "{{ trimAll \"$\" \"$5.00\" }}                  | 5.00",
        "{{ trimPrefix \"hello \" \"hello world\" }}    | world",
        "{{ trimSuffix \" world\" \"hello world\" }}    | hello",
        "{{ contains \"ell\" \"hello\" }}               | true",
        "{{ hasPrefix \"hel\" \"hello\" }}              | true",
        "{{ hasSuffix \"lo\" \"hello\" }}               | true",
        "{{ cat \"hello\" \"world\" }}                  | hello world",
        "{{ replace \" \" \"_\" \"hello world\" }}      | hello_world",
        "{{ snakecase \"HelloWorld\" }}                  | hello_world",
        "{{ kebabcase \"HelloWorld\" }}                  | hello-world",
        "{{ initials \"John Doe\" }}                    | JD",
        "{{ plural \"item\" \"items\" 1 }}              | item",
        "{{ plural \"item\" \"items\" 2 }}              | items",
    })
    void testStringFunction(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    @Test
    void testIndent() throws IOException, TemplateException {
        assertEquals("  hello", exec("{{ indent 2 \"hello\" }}"));
    }

    @Test
    void testUntitle() throws IOException, TemplateException {
        assertTrue(exec("{{ untitle \"Hello World\" }}").startsWith("hello"));
    }

    @Test
    void testNindent() throws IOException, TemplateException {
        assertEquals("\n  hello", exec("{{ nindent 2 \"hello\" }}"));
    }

    @Test
    void testQuote() throws IOException, TemplateException {
        assertEquals("\"hello\"", exec("{{ quote \"hello\" }}"));
    }

    @Test
    void testSquote() throws IOException, TemplateException {
        assertEquals("'hello'", exec("{{ squote \"hello\" }}"));
    }

    @Test
    void testCamelcase() throws IOException, TemplateException {
        String result = exec("{{ camelcase \"hello_world\" }}");
        assertTrue(result.contains("Hello") || result.contains("hello"));
    }

    @Test
    void testShuffle() throws IOException, TemplateException {
        assertEquals(3, exec("{{ shuffle \"abc\" }}").length());
    }

    @Test
    void testWrap() throws IOException, TemplateException {
        assertFalse(exec("{{ wrap 5 \"hello world\" }}").isEmpty());
    }

    @Test
    void testWrapWith() throws IOException, TemplateException {
        assertFalse(exec("{{ wrapWith 5 \"\\n\" \"hello world\" }}").isEmpty());
    }

    @Test
    void testAbbrev() throws IOException, TemplateException {
        assertTrue(exec("{{ abbrev 5 \"hello world\" }}").length() <= 8);
    }

    @Test
    void testAbbrevboth() throws IOException, TemplateException {
        assertTrue(exec("{{ abbrevboth 5 8 \"hello world\" }}").length() <= 11);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ regexMatch \"^[a-z]+$\" \"hello\" }}       | true",
        "{{ mustRegexMatch \"^[a-z]+$\" \"hello\" }}   | true",
        "{{ regexFind \"[0-9]+\" \"abc123def\" }}       | 123",
        "{{ mustRegexFind \"[0-9]+\" \"abc123def\" }}   | 123",
        "{{ regexReplaceAll \"[0-9]+\" \"abc123def456\" \"X\" }} | abcXdefX",
        "{{ mustRegexReplaceAll \"[0-9]+\" \"abc123def\" \"X\" }} | abcXdef",
    })
    void testRegexFunction(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ $matches := regexFindAll \"[0-9]+\" \"abc123def456\" -1 }}{{ len $matches }} | 2",
        "{{ $parts := regexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}                | 3",
        "{{ $parts := mustRegexSplit \"[,;]\" \"a,b;c\" -1 }}{{ len $parts }}            | 3",
    })
    void testRegexCollectionFunction(String template, String expected) throws IOException, TemplateException {
        assertEquals(expected, exec(template));
    }

    @Test
    void testRegexReplaceAllLiteral() throws IOException, TemplateException {
        assertFalse(exec("{{ regexReplaceAllLiteral \"[0-9]+\" \"abc123def456\" \"X\" }}").isEmpty());
    }
}
