package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Template;

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

    private void testTrunc() {
        try {
            Template template = new Template("test");
            template.parse("{{ trunc 5 .text }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("text", "Hello World");
            template.execute(writer, data);
            System.out.println("testTrunc: " + (writer.toString().equals("Hello") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testTrunc: FAIL - " + e.getMessage());
        }
    }

    private void testJoin() {
        try {
            Template template = new Template("test");
            template.parse("{{ join \",\" .items }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("a", "b", "c"));
            template.execute(writer, data);
            System.out.println("testJoin: " + (writer.toString().equals("a,b,c") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testJoin: FAIL - " + e.getMessage());
        }
    }

    private void testFirst() {
        try {
            Template template = new Template("test");
            template.parse("{{ first .items }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("apple", "banana", "cherry"));
            template.execute(writer, data);
            System.out.println("testFirst: " + (writer.toString().equals("apple") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testFirst: FAIL - " + e.getMessage());
        }
    }

    private void testUniq() {
        try {
            Template template = new Template("test");
            template.parse("{{ uniq .items }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("a", "b", "a", "c", "b"));
            template.execute(writer, data);
            String result = writer.toString();
            boolean pass = result.contains("a") && result.contains("b") && result.contains("c");
            System.out.println("testUniq: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testUniq: FAIL - " + e.getMessage());
        }
    }

    private void testSortAlpha() {
        try {
            Template template = new Template("test");
            template.parse("{{ sortAlpha .items }}");
            StringWriter writer = new StringWriter();
            Map<String, Object> data = new HashMap<>();
            data.put("items", Arrays.asList("cherry", "apple", "banana"));
            template.execute(writer, data);
            String result = writer.toString();
            boolean pass = result.contains("apple") && result.contains("banana") && result.contains("cherry");
            System.out.println("testSortAlpha: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testSortAlpha: FAIL - " + e.getMessage());
        }
    }

    private void testSet() {
        try {
            Template template = new Template("test");
            template.parse("{{ $dict := dict }}{{ set $dict \"key\" \"value\" }}{{ index $dict \"key\" }}");
            StringWriter writer = new StringWriter();
            template.execute(writer, new HashMap<>());
            System.out.println("testSet: " + (writer.toString().equals("value") ? "PASS" : "FAIL - got: " + writer.toString()));
        } catch (Exception e) {
            System.out.println("testSet: FAIL - " + e.getMessage());
        }
    }

    private void testRandAlphaNum() {
        try {
            Template template = new Template("test");
            template.parse("{{ randAlphaNum 10 }}");
            StringWriter writer = new StringWriter();
            template.execute(writer, new HashMap<>());
            String result = writer.toString();
            boolean pass = result.length() == 10 && result.matches("[A-Za-z0-9]+");
            System.out.println("testRandAlphaNum: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testRandAlphaNum: FAIL - " + e.getMessage());
        }
    }

    private void testRandAlpha() {
        try {
            Template template = new Template("test");
            template.parse("{{ randAlpha 8 }}");
            StringWriter writer = new StringWriter();
            template.execute(writer, new HashMap<>());
            String result = writer.toString();
            boolean pass = result.length() == 8 && result.matches("[A-Za-z]+");
            System.out.println("testRandAlpha: " + (pass ? "PASS" : "FAIL - got: " + result));
        } catch (Exception e) {
            System.out.println("testRandAlpha: FAIL - " + e.getMessage());
        }
    }
}
