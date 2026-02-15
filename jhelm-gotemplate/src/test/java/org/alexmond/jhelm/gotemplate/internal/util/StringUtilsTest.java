package org.alexmond.jhelm.gotemplate.internal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void testQuote() {
        assertEquals("\"hello\"", StringUtils.quote("hello"));
        assertEquals("\"\"", StringUtils.quote(""));
        assertEquals("\"hello world\"", StringUtils.quote("hello world"));
    }

    @Test
    void testUnquoteDoubleQuotes() {
        assertEquals("hello", StringUtils.unquote("\"hello\""));
        assertEquals("", StringUtils.unquote("\"\""));
        assertEquals("hello world", StringUtils.unquote("\"hello world\""));
    }

    @Test
    void testUnquoteSingleQuotes() {
        assertEquals("hello", StringUtils.unquote("'hello'"));
        assertEquals("", StringUtils.unquote("''"));
        assertEquals("hello world", StringUtils.unquote("'hello world'"));
    }

    @Test
    void testUnquoteBackticks() {
        assertEquals("hello", StringUtils.unquote("`hello`"));
        assertEquals("", StringUtils.unquote("``"));
        assertEquals("hello world", StringUtils.unquote("`hello world`"));
    }

    @Test
    void testUnquoteBackticksWithCarriageReturn() {
        assertEquals("hello\nworld", StringUtils.unquote("`hello\r\nworld`"));
        assertEquals("test", StringUtils.unquote("`test\r`"));
    }

    @Test
    void testUnquoteNotQuoted() {
        assertEquals("hello", StringUtils.unquote("hello"));
        assertEquals("h", StringUtils.unquote("h"));
        assertEquals("", StringUtils.unquote(""));
    }

    @Test
    void testUnquoteMismatchedQuotes() {
        assertEquals("\"hello'", StringUtils.unquote("\"hello'"));
        assertEquals("'hello\"", StringUtils.unquote("'hello\""));
    }

    @Test
    void testUnquoteWithEscapeSequences() {
        assertEquals("hello\nworld", StringUtils.unquote("\"hello\\nworld\""));
        assertEquals("hello\tworld", StringUtils.unquote("\"hello\\tworld\""));
        assertEquals("hello\\world", StringUtils.unquote("\"hello\\\\world\""));
        assertEquals("hello\"world", StringUtils.unquote("\"hello\\\"world\""));
        assertEquals("hello'world", StringUtils.unquote("\"hello\\'world\""));
        assertEquals("hello\rworld", StringUtils.unquote("\"hello\\rworld\""));
        assertEquals("hello\bworld", StringUtils.unquote("\"hello\\bworld\""));
        assertEquals("hello\fworld", StringUtils.unquote("\"hello\\fworld\""));
    }

    @Test
    void testUnquoteWithMultipleEscapeSequences() {
        assertEquals("line1\nline2\tcolumn", StringUtils.unquote("\"line1\\nline2\\tcolumn\""));
        assertEquals("path\\to\\file", StringUtils.unquote("\"path\\\\to\\\\file\""));
    }

    @Test
    void testUnquoteWithUnknownEscapeSequence() {
        assertEquals("hello\\xworld", StringUtils.unquote("\"hello\\xworld\""));
    }

    @Test
    void testUnquoteNoEscapesFastPath() {
        assertEquals("simple text", StringUtils.unquote("\"simple text\""));
        assertEquals("no escapes here", StringUtils.unquote("'no escapes here'"));
    }

    @Test
    void testUnquoteTrailingBackslash() {
        assertEquals("test\\", StringUtils.unquote("\"test\\\\\""));
    }

    @Test
    void testUnquoteSingleCharacter() {
        assertEquals("", StringUtils.unquote("\"\""));
        assertEquals("a", StringUtils.unquote("\"a\""));
    }
}
