package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    @Test
    void testBase64Encode() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b64enc \"hello\" }}", new HashMap<>(), writer);
        assertEquals("aGVsbG8=", writer.toString());
    }

    @Test
    void testBase64Decode() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b64dec \"aGVsbG8=\" }}", new HashMap<>(), writer);
        assertEquals("hello", writer.toString());
    }

    @Test
    void testBase64RoundTrip() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ \"test data\" | b64enc | b64dec }}", new HashMap<>(), writer);
        assertEquals("test data", writer.toString());
    }

    @Test
    void testBase32Encode() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b32enc \"hello\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testBase32Decode() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        String encoded = new StringWriter() {{
            execute("test", "{{ b32enc \"hello\" }}", new HashMap<>(), this);
        }}.toString();

        StringWriter decoder = new StringWriter();
        execute("test", "{{ b32dec \"" + encoded + "\" }}", new HashMap<>(), decoder);
        assertEquals("hello", decoder.toString());
    }

    @Test
    void testSha1Sum() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha1sum \"hello\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
        assertEquals(40, writer.toString().length());
    }

    @Test
    void testSha256Sum() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha256sum \"hello\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
        assertEquals(64, writer.toString().length());
    }

    @Test
    void testSha512Sum() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha512sum \"hello\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
        assertEquals(128, writer.toString().length());
    }

    @Test
    void testAdler32Sum() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ adler32sum \"hello\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
        assertTrue(writer.toString().matches("\\d+"));
    }

    // Edge case tests

    @Test
    void testBase64EncodeWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ b64enc .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase64DecodeWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ b64dec .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase64DecodeInvalidInput() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b64dec \"not-valid-base64!!!\" }}", new HashMap<>(), writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase32EncodeWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ b32enc .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase32DecodeWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ b32dec .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase32DecodeInvalidInput() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b32dec \"@@@invalid@@@\" }}", new HashMap<>(), writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testSha1SumWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ sha1sum .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testSha256SumWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ sha256sum .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testSha512SumWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ sha512sum .value }}", data, writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testAdler32SumWithNull() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        HashMap<String, Object> data = new HashMap<>();
        data.put("value", null);
        execute("test", "{{ adler32sum .value }}", data, writer);
        assertEquals("0", writer.toString());
    }

    @Test
    void testBase64EncodeEmpty() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b64enc \"\" }}", new HashMap<>(), writer);
        assertEquals("", writer.toString());
    }

    @Test
    void testBase32EncodeEmpty() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ b32enc \"\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().isEmpty() || writer.toString().matches("[A-Z2-7=]+"));
    }

    @Test
    void testSha1SumConsistency() throws IOException, TemplateException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        execute("test", "{{ sha1sum \"test\" }}", new HashMap<>(), writer1);
        execute("test", "{{ sha1sum \"test\" }}", new HashMap<>(), writer2);
        assertEquals(writer1.toString(), writer2.toString());
    }

    @Test
    void testSha256SumConsistency() throws IOException, TemplateException {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        execute("test", "{{ sha256sum \"test\" }}", new HashMap<>(), writer1);
        execute("test", "{{ sha256sum \"test\" }}", new HashMap<>(), writer2);
        assertEquals(writer1.toString(), writer2.toString());
    }

    // Additional tests to reach edge cases

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg", "x"})
    void testBase32EncodeWithVariousLengths(String input) throws IOException, TemplateException {
        // Test inputs of different lengths to trigger different padding scenarios
        StringWriter writer = new StringWriter();
        execute("test", "{{ b32enc \"" + input + "\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "Hello World!", "12345", "special!@#$%"})
    void testSha1SumWithVariousInputs(String input) throws IOException, TemplateException {
        // Test with different input types to exercise lambda code paths
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha1sum \"" + input + "\" }}", new HashMap<>(), writer);
        // All should produce valid hex strings or empty for empty input
        assertTrue(writer.toString().isEmpty() || writer.toString().matches("[0-9a-f]{40}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "Hello World!", "12345", "unicode: \u00A9 \u00AE"})
    void testSha256SumWithVariousInputs(String input) throws IOException, TemplateException {
        // Test with different input types to exercise lambda code paths
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha256sum \"" + input + "\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().isEmpty() || writer.toString().matches("[0-9a-f]{64}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "test", "long input string with many characters", "123"})
    void testSha512SumWithVariousInputs(String input) throws IOException, TemplateException {
        // Test with different input types to exercise lambda code paths
        StringWriter writer = new StringWriter();
        execute("test", "{{ sha512sum \"" + input + "\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().isEmpty() || writer.toString().matches("[0-9a-f]{128}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "xy", "xyz", "test"})
    void testBase32RoundTripWithVariousInputs(String input) throws IOException, TemplateException {
        // Test round trip with different lengths to ensure encodeBase32 edge cases work
        StringWriter encoder = new StringWriter();
        execute("test", "{{ b32enc \"" + input + "\" }}", new HashMap<>(), encoder);

        StringWriter decoder = new StringWriter();
        execute("test", "{{ b32dec \"" + encoder.toString() + "\" }}", new HashMap<>(), decoder);

        assertEquals(input, decoder.toString());
    }

    @Test
    void testHashFunctionsWithEmptyString() throws IOException, TemplateException {
        // Ensure hash functions handle empty strings correctly (produce valid hash)
        StringWriter sha1 = new StringWriter();
        execute("test", "{{ sha1sum \"\" }}", new HashMap<>(), sha1);
        assertTrue(sha1.toString().matches("[0-9a-f]{40}"));  // Empty string has valid hash

        StringWriter sha256 = new StringWriter();
        execute("test", "{{ sha256sum \"\" }}", new HashMap<>(), sha256);
        assertTrue(sha256.toString().matches("[0-9a-f]{64}"));

        StringWriter sha512 = new StringWriter();
        execute("test", "{{ sha512sum \"\" }}", new HashMap<>(), sha512);
        assertTrue(sha512.toString().matches("[0-9a-f]{128}"));
    }
}
