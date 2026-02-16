package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SprigFunctionsTestRunner {
    public static void main(String[] args) {
        SprigFunctionsTestRunner runner = new SprigFunctionsTestRunner();

        System.out.println("Testing Sprig Functions...\n");

        runner.testTrunc();
        runner.testJoin();
        runner.testFirst();
        runner.testUniq();
        runner.testSortAlpha();
        runner.testSet();
        runner.testRandAlphaNum();
        runner.testRandAlpha();

        System.out.println("\nAll manual tests completed!");
    }

    private void execute(String name, String text, Object data, java.io.Writer writer) throws Exception {
        GoTemplate template = new GoTemplate();
        template.parse(name, text);
        template.execute(name, data, writer);
    }

    private void testTrunc() {
        try {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("text", "Hello World");
            execute("test", "{{ trunc 5 .text }}", data, writer);
            System.out.println("testTrunc: " + (writer.toString().equals("Hello") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testTrunc: FAIL - " + e.getMessage());
        }
    }

    private void testJoin() {
        try {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("a", "b", "c"));
            execute("test", "{{ join \",\" .items }}", data, writer);
            System.out.println("testJoin: " + (writer.toString().equals("a,b,c") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testJoin: FAIL - " + e.getMessage());
        }
    }

    private void testFirst() {
        try {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("apple", "banana", "cherry"));
            execute("test", "{{ first .items }}", data, writer);
            System.out.println("testFirst: " + (writer.toString().equals("apple") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testFirst: FAIL - " + e.getMessage());
        }
    }

    private void testUniq() {
        try {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("a", "b", "a", "c", "b"));
            execute("test", "{{ uniq .items }}", data, writer);
            String result = writer.toString();
            boolean pass = result.contains("a") && result.contains("b") && result.contains("c");
            System.out.println("testUniq: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testUniq: FAIL - " + e.getMessage());
        }
    }

    private void testSortAlpha() {
        try {
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("cherry", "apple", "banana"));
            execute("test", "{{ sortAlpha .items }}", data, writer);
            String result = writer.toString();
            boolean pass = result.contains("apple") && result.contains("banana") && result.contains("cherry");
            System.out.println("testSortAlpha: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testSortAlpha: FAIL - " + e.getMessage());
        }
    }

    private void testSet() {
        try {
            StringWriter writer = new StringWriter();
            execute("test", "{{ $dict := dict }}{{ set $dict \"key\" \"value\" }}{{ index $dict \"key\" }}", new HashMap<>(), writer);
            System.out.println("testSet: " + (writer.toString().equals("value") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testSet: FAIL - " + e.getMessage());
        }
    }

    private void testRandAlphaNum() {
        try {
            StringWriter writer = new StringWriter();
            execute("test", "{{ randAlphaNum 10 }}", new HashMap<>(), writer);
            String result = writer.toString();
            boolean pass = result.length() == 10 && result.matches("[A-Za-z0-9]+");
            System.out.println("testRandAlphaNum: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testRandAlphaNum: FAIL - " + e.getMessage());
        }
    }

    private void testRandAlpha() {
        try {
            StringWriter writer = new StringWriter();
            execute("test", "{{ randAlpha 8 }}", new HashMap<>(), writer);
            String result = writer.toString();
            boolean pass = result.length() == 8 && result.matches("[A-Za-z]+");
            System.out.println("testRandAlpha: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testRandAlpha: FAIL - " + e.getMessage());
        }
    }
}
