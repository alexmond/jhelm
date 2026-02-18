package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class DateFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testNow() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testDateFormatting() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | date \"2006-01-02\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testHtmlDate() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | htmlDate }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testUnixEpoch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | unixEpoch }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d+"));
    }

    @Test
    void testDateModify() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | dateModify \"24h\" | unixEpoch }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d+"));
    }

    @Test
    void testToDate() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ toDate \"2006-01-02\" \"2024-01-15\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testDateInZone() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ dateInZone \"2006-01-02\" now \"UTC\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testDurationRound() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ durationRound \"2h10m5s\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testMustToDate() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ mustToDate \"2006-01-02\" \"2024-12-25\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testHtmlDateInZone() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ htmlDateInZone now \"UTC\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void testDateWithCustomFormat() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | date \"2006\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{4}"));
    }

    @Test
    void testDateInZoneWithTimezone() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ dateInZone \"15:04:05\" now \"America/New_York\" }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testToDateWithDifferentFormat() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ toDate \"01/02/2006\" \"12/25/2024\" }}", new HashMap<>(), writer);
        assertFalse(writer.toString().isEmpty());
    }

    @Test
    void testDateModifyWithNegative() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | dateModify \"-24h\" | unixEpoch }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d+"));
    }

    @Test
    void testUnixEpochWithPipechain() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ now | dateModify \"1h\" | unixEpoch }}", new HashMap<>(), writer);
        assertTrue(writer.toString().matches("\\d+"));
    }
}
