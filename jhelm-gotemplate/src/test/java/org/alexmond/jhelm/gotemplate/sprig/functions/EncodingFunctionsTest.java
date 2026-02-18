package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class EncodingFunctionsTest {

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

    private String execWithData(String template, HashMap<String, Object> data) throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", template, data, writer);
        return writer.toString();
    }

    // Base64 tests

    @Test
    void testBase64Encode() throws IOException, TemplateException {
        assertEquals("aGVsbG8=", exec("{{ b64enc \"hello\" }}"));
    }

    @Test
    void testBase64Decode() throws IOException, TemplateException {
        assertEquals("hello", exec("{{ b64dec \"aGVsbG8=\" }}"));
    }

    @Test
    void testBase64RoundTrip() throws IOException, TemplateException {
        assertEquals("test data", exec("{{ \"test data\" | b64enc | b64dec }}"));
    }

    // Base32 tests

    @Test
    void testBase32Encode() throws IOException, TemplateException {
        assertFalse(exec("{{ b32enc \"hello\" }}").isEmpty());
    }

    @Test
    void testBase32Decode() throws IOException, TemplateException {
        String encoded = exec("{{ b32enc \"hello\" }}");
        StringWriter decoder = new StringWriter();
        execute("test", "{{ b32dec \"" + encoded + "\" }}", new HashMap<>(), decoder);
        assertEquals("hello", decoder.toString());
    }

    // Hash function tests with expected lengths

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "sha1sum   | 40",
        "sha256sum | 64",
        "sha512sum | 128",
    })
    void testHashFunctionLength(String func, int expectedLength) throws IOException, TemplateException {
        String result = exec("{{ " + func + " \"hello\" }}");
        assertFalse(result.isEmpty());
        assertEquals(expectedLength, result.length());
    }

    @Test
    void testAdler32Sum() throws IOException, TemplateException {
        String result = exec("{{ adler32sum \"hello\" }}");
        assertFalse(result.isEmpty());
        assertTrue(result.matches("\\d+"));
    }

    // Null handling tests

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{{ b64enc .value }}    | ",
        "{{ b64dec .value }}    | ",
        "{{ b32enc .value }}    | ",
        "{{ b32dec .value }}    | ",
        "{{ sha1sum .value }}   | ",
        "{{ sha256sum .value }} | ",
        "{{ sha512sum .value }} | ",
    })
    void testFunctionWithNull(String template, String expected) throws IOException, TemplateException {
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        assertEquals(expected == null ? "" : expected, execWithData(template, data));
    }

    @Test
    void testAdler32SumWithNull() throws IOException, TemplateException {
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        assertEquals("0", execWithData("{{ adler32sum .value }}", data));
    }

    // Invalid input tests

    @Test
    void testBase64DecodeInvalidInput() throws IOException, TemplateException {
        assertEquals("", exec("{{ b64dec \"not-valid-base64!!!\" }}"));
    }

    @Test
    void testBase32DecodeInvalidInput() throws IOException, TemplateException {
        assertEquals("", exec("{{ b32dec \"@@@invalid@@@\" }}"));
    }

    // Empty string tests

    @Test
    void testBase64EncodeEmpty() throws IOException, TemplateException {
        assertEquals("", exec("{{ b64enc \"\" }}"));
    }

    @Test
    void testBase32EncodeEmpty() throws IOException, TemplateException {
        String result = exec("{{ b32enc \"\" }}");
        assertTrue(result.isEmpty() || result.matches("[A-Z2-7=]+"));
    }

    // Consistency tests

    @ParameterizedTest
    @CsvSource({"sha1sum", "sha256sum"})
    void testHashConsistency(String func) throws IOException, TemplateException {
        String result1 = exec("{{ " + func + " \"test\" }}");
        String result2 = exec("{{ " + func + " \"test\" }}");
        assertEquals(result1, result2);
    }

    // Various input length tests

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg", "x"})
    void testBase32EncodeWithVariousLengths(String input) throws IOException, TemplateException {
        assertFalse(exec("{{ b32enc \"" + input + "\" }}").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "Hello World!", "12345", "special!@#$%"})
    void testSha1SumWithVariousInputs(String input) throws IOException, TemplateException {
        String result = exec("{{ sha1sum \"" + input + "\" }}");
        assertTrue(result.isEmpty() || result.matches("[0-9a-f]{40}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "Hello World!", "12345", "unicode: \u00A9 \u00AE"})
    void testSha256SumWithVariousInputs(String input) throws IOException, TemplateException {
        String result = exec("{{ sha256sum \"" + input + "\" }}");
        assertTrue(result.isEmpty() || result.matches("[0-9a-f]{64}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "long input string with many characters", "123"})
    void testSha512SumWithVariousInputs(String input) throws IOException, TemplateException {
        String result = exec("{{ sha512sum \"" + input + "\" }}");
        assertTrue(result.isEmpty() || result.matches("[0-9a-f]{128}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "xy", "xyz", "test"})
    void testBase32RoundTripWithVariousInputs(String input) throws IOException, TemplateException {
        String encoded = exec("{{ b32enc \"" + input + "\" }}");
        StringWriter decoder = new StringWriter();
        execute("test", "{{ b32dec \"" + encoded + "\" }}", new HashMap<>(), decoder);
        assertEquals(input, decoder.toString());
    }

    @Test
    void testHashFunctionsWithEmptyString() throws IOException, TemplateException {
        assertTrue(exec("{{ sha1sum \"\" }}").matches("[0-9a-f]{40}"));
        assertTrue(exec("{{ sha256sum \"\" }}").matches("[0-9a-f]{64}"));
        assertTrue(exec("{{ sha512sum \"\" }}").matches("[0-9a-f]{128}"));
    }
}
