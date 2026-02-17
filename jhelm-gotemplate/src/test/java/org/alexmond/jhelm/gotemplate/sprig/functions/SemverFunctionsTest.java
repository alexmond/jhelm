package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemverFunctionsTest {

    private void execute(String name, String text, Object data, StringWriter writer) throws IOException, TemplateException {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    @Test
    void testSemverParsing() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"1.2.3\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}", new HashMap<>(), writer);
        assertEquals("1.2.3", writer.toString());
    }

    @Test
    void testSemverWithVPrefix() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"v2.5.7\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}", new HashMap<>(), writer);
        assertEquals("2.5.7", writer.toString());
    }

    @Test
    void testSemverWithPrerelease() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"1.2.3-alpha.1\" }}{{ $v.Major }}", new HashMap<>(), writer);
        assertEquals("1", writer.toString());
    }

    @Test
    void testSemverPatch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"1.2.5\" }}{{ $v.Patch }}", new HashMap<>(), writer);
        assertEquals("5", writer.toString());
    }

    @Test
    void testSemverCompareEqual() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"=1.2.3\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareGreaterThan() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">1.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareLessThan() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"<2.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareRange() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">=1.0.0 <2.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareVersion() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">=1.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    // Additional basic tests
    @Test
    void testSemverInvalidFormat() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"invalid\" }}{{ $v.Major }}", new HashMap<>(), writer);
        assertEquals("0", writer.toString());
    }

    @Test
    void testSemverMinorOnly() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"1.2\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}", new HashMap<>(), writer);
        assertEquals("1.2.0", writer.toString());
    }

    @Test
    void testSemverMajorOnly() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ $v := semver \"5\" }}{{ $v.Major }}.{{ $v.Minor }}.{{ $v.Patch }}", new HashMap<>(), writer);
        assertEquals("5.0.0", writer.toString());
    }

    @Test
    void testSemverCompareNotEqual() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"!=1.2.3\" \"1.2.4\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareLessOrEqual() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"<=2.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareGreaterOrEqual() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">=1.0.0\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareTildeOperator() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"~1.2.3\" \"1.2.5\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareCaretOperator() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"^1.2.3\" \"1.5.0\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareCaretZeroMajor() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"^0.2.3\" \"0.2.5\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareCaretZeroMinor() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"^0.0.3\" \"0.0.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareOrOperator() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"=1.0.0 || =2.0.0\" \"2.0.0\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareNoOperator() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"1.2.3\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareWithPrerelease() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"<1.2.3\" \"1.2.3-alpha\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverComparePrereleaseLexical() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"<1.2.3-beta\" \"1.2.3-alpha\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareExactVersion() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"=1.2.3\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareLessOrEqualMatch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \"<=1.2.3\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareGreaterOrEqualMatch() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">=1.2.3\" \"1.2.3\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareMultipleConstraintsPass() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">1.0.0 <3.0.0\" \"1.5.0\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }

    @Test
    void testSemverCompareRangeBoundary() throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        execute("test", "{{ semverCompare \">1.0.0 <=2.0.0\" \"2.0.0\" }}", new HashMap<>(), writer);
        assertEquals("true", writer.toString());
    }
}
