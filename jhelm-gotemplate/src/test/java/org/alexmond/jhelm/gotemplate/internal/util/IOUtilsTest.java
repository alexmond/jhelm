package org.alexmond.jhelm.gotemplate.internal.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class IOUtilsTest {

    @Test
    void testReadEmptyString() throws IOException {
        StringReader reader = new StringReader("");
        String result = IOUtils.read(reader);
        assertEquals("", result);
    }

    @Test
    void testReadSimpleString() throws IOException {
        String input = "Hello, World!";
        StringReader reader = new StringReader(input);
        String result = IOUtils.read(reader);
        assertEquals(input, result);
    }

    @Test
    void testReadMultiLineString() throws IOException {
        String input = "Line 1\nLine 2\nLine 3";
        StringReader reader = new StringReader(input);
        String result = IOUtils.read(reader);
        assertEquals(input, result);
    }

    @Test
    void testReadLargeString() throws IOException {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            input.append("This is line ").append(i).append("\n");
        }
        StringReader reader = new StringReader(input.toString());
        String result = IOUtils.read(reader);
        assertEquals(input.toString(), result);
    }

    @Test
    void testReadWithSpecialCharacters() throws IOException {
        String input = "Special chars: \t\n\r\b\f !@#$%^&*()";
        StringReader reader = new StringReader(input);
        String result = IOUtils.read(reader);
        assertEquals(input, result);
    }

    @Test
    void testReadUnicodeCharacters() throws IOException {
        String input = "Unicode: \u0041 \u4E2D \u6587 \uD83D\uDE00";
        StringReader reader = new StringReader(input);
        String result = IOUtils.read(reader);
        assertEquals(input, result);
    }
}
