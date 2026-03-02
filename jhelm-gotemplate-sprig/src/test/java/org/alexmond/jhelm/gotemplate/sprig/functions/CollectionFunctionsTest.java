package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class CollectionFunctionsTest {

	private void execute(String name, String text, Object data, StringWriter writer)
			throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse(name, text);
		template.execute(name, data, writer);
	}

	private String exec(String template) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", template, new HashMap<>(), writer);
		return writer.toString();
	}

	private String execWithData(String template, Map<String, Object> data) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", template, data, writer);
		return writer.toString();
	}

	// Basic list operations - parameterized for regular and must* variants

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "first     | a", "mustFirst | a" })
	void testFirst(String func, String expected) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals(expected, execWithData("{{ " + func + " .items }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "last     | c", "mustLast | c" })
	void testLast(String func, String expected) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals(expected, execWithData("{{ " + func + " .items }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "rest     | b,c", "mustRest | b,c" })
	void testRest(String func, String expected) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals(expected, execWithData("{{ $r := " + func + " .items }}{{ join \",\" $r }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "initial     | a,b", "mustInitial | a,b" })
	void testInitial(String func, String expected) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals(expected, execWithData("{{ $i := " + func + " .items }}{{ join \",\" $i }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource({ "append, mustAppend" })
	void testAppend(String func1, String func2) throws IOException, TemplateException {
		for (String func : new String[] { func1, func2 }) {
			Map<String, Object> data = new HashMap<>();
			data.put("items", new ArrayList<>(Arrays.asList("a", "b")));
			assertEquals("a,b,c", execWithData("{{ $l := " + func + " .items \"c\" }}{{ join \",\" $l }}", data));
		}
	}

	@ParameterizedTest
	@CsvSource({ "prepend, mustPrepend" })
	void testPrepend(String func1, String func2) throws IOException, TemplateException {
		for (String func : new String[] { func1, func2 }) {
			Map<String, Object> data = new HashMap<>();
			data.put("items", new ArrayList<>(Arrays.asList("b", "c")));
			assertEquals("a,b,c", execWithData("{{ $l := " + func + " .items \"a\" }}{{ join \",\" $l }}", data));
		}
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "reverse     | c,b,a", "mustReverse | c,b,a" })
	void testReverse(String func, String expected) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals(expected, execWithData("{{ $r := " + func + " .items }}{{ join \",\" $r }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource({ "compact", "mustCompact" })
	void testCompact(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("items", Arrays.asList("a", "", "b", null, "c"));
		assertEquals("a,b,c", execWithData("{{ $c := " + func + " .items }}{{ join \",\" $c }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "has", "mustHas" })
	void testHas(String func) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		assertEquals("true", execWithData("{{ " + func + " \"b\" .items }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource({ "slice", "mustSlice" })
	void testSlice(String func) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c", "d", "e"));
		assertEquals("b,c",
				execWithData("{{ $s := " + func + " .items 1 3 }}{{ join \",\" $s }}", new HashMap<>(data)));
	}

	// Uniq tests (use contains since order may vary)

	@ParameterizedTest
	@CsvSource({ "uniq", "mustUniq" })
	void testUniq(String func) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "a", "c", "b"));
		String result = execWithData("{{ $u := " + func + " .items }}{{ join \",\" $u }}", new HashMap<>(data));
		assertTrue(result.contains("a"));
		assertTrue(result.contains("b"));
		assertTrue(result.contains("c"));
	}

	@ParameterizedTest
	@CsvSource({ "without", "mustWithout" })
	void testWithout(String func) throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("a", "b", "c"));
		String result = execWithData("{{ $w := " + func + " .items \"b\" }}{{ join \",\" $w }}", new HashMap<>(data));
		assertTrue(result.contains("a"));
		assertTrue(result.contains("c"));
	}

	// Dict operations - parameterized for regular and must* variants

	@ParameterizedTest
	@CsvSource({ "hasKey", "mustHasKey" })
	void testHasKey(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("key", "value");
		data.put("map", map);
		assertEquals("true", execWithData("{{ " + func + " .map \"key\" }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "keys", "mustKeys" })
	void testKeys(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("a", 1);
		map.put("b", 2);
		data.put("map", map);
		assertEquals("2", execWithData("{{ $k := " + func + " .map }}{{ len $k }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "values", "mustValues" })
	void testValues(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("a", 1);
		map.put("b", 2);
		data.put("map", map);
		assertEquals("2", execWithData("{{ $v := " + func + " .map }}{{ len $v }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "pick", "mustPick" })
	void testPick(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("a", 1);
		map.put("b", 2);
		map.put("c", 3);
		data.put("map", map);
		assertEquals("2", execWithData("{{ $p := " + func + " .map \"a\" \"c\" }}{{ len $p }}", data));
	}

	@ParameterizedTest
	@CsvSource({ "omit", "mustOmit" })
	void testOmit(String func) throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("a", 1);
		map.put("b", 2);
		map.put("c", 3);
		data.put("map", map);
		assertEquals("2", execWithData("{{ $o := " + func + " .map \"b\" }}{{ len $o }}", data));
	}

	// Merge and deepCopy - parameterized for regular and must* variants

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"b\" 2 }}{{ $m := merge $d1 $d2 }}{{ len $m }}                         | 2",
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"b\" 2 }}{{ $m := mustMerge $d1 $d2 }}{{ len $m }}                     | 2",
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"a\" 2 }}{{ $m := merge $d1 $d2 }}{{ get $m \"a\" }}                    | 1",
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"a\" 2 }}{{ $m := mustMerge $d1 $d2 }}{{ get $m \"a\" }}                | 1",
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"a\" 2 }}{{ $m := mergeOverwrite $d1 $d2 }}{{ get $m \"a\" }}          | 2",
			"{{ $d1 := dict \"a\" 1 }}{{ $d2 := dict \"a\" 2 }}{{ $m := mustMergeOverwrite $d1 $d2 }}{{ get $m \"a\" }}      | 2",
			"{{ $d := dict \"a\" 1 }}{{ $c := deepCopy $d }}{{ get $c \"a\" }}                                                | 1",
			"{{ $d := dict \"a\" 1 }}{{ $c := mustDeepCopy $d }}{{ get $c \"a\" }}                                            | 1" })
	void testMergeAndCopy(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testMergeDeepNested() throws IOException, TemplateException {
		// Deep merge: nested maps should be merged recursively, dst values preserved
		String template = """
				{{ $d1 := dict "outer" (dict "a" 1 "b" 2) }}\
				{{ $d2 := dict "outer" (dict "a" 99 "c" 3) }}\
				{{ $m := merge $d1 $d2 }}\
				{{ (get (get $m "outer") "a") }},{{ (get (get $m "outer") "b") }},{{ (get (get $m "outer") "c") }}""";
		assertEquals("1,2,3", exec(template));
	}

	@Test
	void testMergeOverwriteDeepNested() throws IOException, TemplateException {
		// mergeOverwrite: source values should overwrite destination values
		String template = """
				{{ $d1 := dict "outer" (dict "a" 1 "b" 2) }}\
				{{ $d2 := dict "outer" (dict "a" 99 "c" 3) }}\
				{{ $m := mergeOverwrite $d1 $d2 }}\
				{{ (get (get $m "outer") "a") }},{{ (get (get $m "outer") "b") }},{{ (get (get $m "outer") "c") }}""";
		assertEquals("99,2,3", exec(template));
	}

	@Test
	void testMergePipelineDestinationWins() throws IOException, TemplateException {
		// Simulates the Helm pattern: pipeline value | merge $dst
		// In this pattern, $dst is the first arg, pipeline value is the source
		String template = """
				{{ $dst := dict "version" "1.0" }}\
				{{ $src := dict "version" "2.0" "name" "test" }}\
				{{ $dst = merge $dst $src }}\
				{{ get $dst "version" }},{{ get $dst "name" }}""";
		assertEquals("1.0,test", exec(template));
	}

	@Test
	void testMergeMultipleSources() throws IOException, TemplateException {
		// merge with 3 args: dst wins over all sources, earlier sources win over later
		String template = """
				{{ $d1 := dict "a" 1 }}\
				{{ $d2 := dict "a" 2 "b" 20 }}\
				{{ $d3 := dict "a" 3 "b" 30 "c" 300 }}\
				{{ $m := merge $d1 $d2 $d3 }}\
				{{ get $m "a" }},{{ get $m "b" }},{{ get $m "c" }}""";
		assertEquals("1,20,300", exec(template));
	}

	@Test
	void testMergeOverwriteMultipleSources() throws IOException, TemplateException {
		// mergeOverwrite with 3 args: later sources overwrite earlier
		String template = """
				{{ $d1 := dict "a" 1 }}\
				{{ $d2 := dict "a" 2 "b" 20 }}\
				{{ $d3 := dict "a" 3 "b" 30 "c" 300 }}\
				{{ $m := mergeOverwrite $d1 $d2 $d3 }}\
				{{ get $m "a" }},{{ get $m "b" }},{{ get $m "c" }}""";
		assertEquals("3,30,300", exec(template));
	}

	@Test
	void testMergeEmptyDestination() throws IOException, TemplateException {
		// merge into empty dict should copy all source keys
		String template = """
				{{ $dst := dict }}\
				{{ $src := dict "a" 1 "b" 2 }}\
				{{ $m := merge $dst $src }}\
				{{ get $m "a" }},{{ get $m "b" }}""";
		assertEquals("1,2", exec(template));
	}

	@Test
	void testMergeMutatesDestination() throws IOException, TemplateException {
		// Go's merge mutates the destination in place
		String template = """
				{{ $d1 := dict "a" 1 }}\
				{{ $d2 := dict "b" 2 }}\
				{{ $_ := merge $d1 $d2 }}\
				{{ get $d1 "b" }}""";
		assertEquals("2", exec(template));
	}

	@Test
	void testMergeOverwritesNullValues() throws IOException, TemplateException {
		// In Go's sprig, merge treats nil/zero values in dst as absent
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> dst = new HashMap<>();
		dst.put("tag", null);
		data.put("dst", dst);
		data.put("src", Map.of("tag", "latest"));
		assertEquals("latest", execWithData("{{ $m := merge .dst .src }}{{ get $m \"tag\" }}", data));
	}

	@Test
	void testMergePreservesNonNullValues() throws IOException, TemplateException {
		// merge should NOT overwrite non-null dst values
		Map<String, Object> data = new HashMap<>();
		data.put("dst", new HashMap<>(Map.of("tag", "v1")));
		data.put("src", Map.of("tag", "v2"));
		assertEquals("v1", execWithData("{{ $m := merge .dst .src }}{{ get $m \"tag\" }}", data));
	}

	@Test
	void testMustMergeDeepNested() throws IOException, TemplateException {
		String template = """
				{{ $d1 := dict "outer" (dict "a" 1 "b" 2) }}\
				{{ $d2 := dict "outer" (dict "a" 99 "c" 3) }}\
				{{ $m := mustMerge $d1 $d2 }}\
				{{ (get (get $m "outer") "a") }},{{ (get (get $m "outer") "c") }}""";
		assertEquals("1,3", exec(template));
	}

	@Test
	void testMustMergeOverwriteDeepNested() throws IOException, TemplateException {
		String template = """
				{{ $d1 := dict "outer" (dict "a" 1 "b" 2) }}\
				{{ $d2 := dict "outer" (dict "a" 99 "c" 3) }}\
				{{ $m := mustMergeOverwrite $d1 $d2 }}\
				{{ (get (get $m "outer") "a") }},{{ (get (get $m "outer") "c") }}""";
		assertEquals("99,3", exec(template));
	}

	// Standalone tests for operations without must* variants

	@Test
	void testList() throws IOException, TemplateException {
		assertEquals("a,b,c", exec("{{ $l := list \"a\" \"b\" \"c\" }}{{ join \",\" $l }}"));
	}

	@Test
	void testConcat() throws IOException, TemplateException {
		assertEquals("a,b,c,d", exec(
				"{{ $l1 := list \"a\" \"b\" }}{{ $l2 := list \"c\" \"d\" }}{{ $c := concat $l1 $l2 }}{{ join \",\" $c }}"));
	}

	@Test
	void testDict() throws IOException, TemplateException {
		assertEquals("John", exec("{{ $d := dict \"name\" \"John\" \"age\" 30 }}{{ $d.name }}"));
	}

	@Test
	void testSortAlpha() throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", Arrays.asList("c", "a", "b"));
		assertEquals("a,b,c",
				execWithData("{{ $sorted := sortAlpha .items }}{{ join \",\" $sorted }}", new HashMap<>(data)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ $s := seq 1 5 }}{{ join \",\" $s }}                    | 1,2,3,4,5",
					"{{ $u := until 5 }}{{ join \",\" $u }}                    | 0,1,2,3,4",
					"{{ $u := untilStep 0 10 2 }}{{ join \",\" $u }}           | 0,2,4,6,8" })
	void testSequences(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testGet() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> map = new HashMap<>();
		map.put("key", "value");
		data.put("map", map);
		assertEquals("value", execWithData("{{ get .map \"key\" }}", data));
	}

	@Test
	void testSet() throws IOException, TemplateException {
		assertEquals("2", exec("{{ $d := dict \"a\" 1 }}{{ $d := set $d \"b\" 2 }}{{ get $d \"b\" }}"));
	}

	@Test
	void testUnset() throws IOException, TemplateException {
		assertEquals("false", exec("{{ $d := dict \"a\" 1 \"b\" 2 }}{{ $d := unset $d \"a\" }}{{ hasKey $d \"a\" }}"));
	}

	@Test
	void testDig() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> nested = new HashMap<>();
		nested.put("key", "value");
		Map<String, Object> map = new HashMap<>();
		map.put("nested", nested);
		data.put("map", map);
		assertEquals("value", execWithData("{{ dig \"nested\" \"key\" \"\" .map }}", data));
	}

	// Different input types (arrays, collections, strings)

	@ParameterizedTest
	@MethodSource("inputTypeTestCases")
	void testWithDifferentInputTypes(String func, Object input, String template, String expected, boolean exact)
			throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("items", input);
		String result = execWithData(template, data);
		if (exact) {
			assertEquals(expected, result);
		}
		else {
			assertFalse(result.isEmpty());
		}
	}

	static Stream<Arguments> inputTypeTestCases() {
		return Stream.of(Arguments.of("join", new String[] { "a", "b", "c" }, "{{ join \",\" .items }}", "a,b,c", true),
				Arguments.of("sortAlpha", new String[] { "c", "a", "b" },
						"{{ $s := sortAlpha .items }}{{ join \",\" $s }}", "a,b,c", true),
				Arguments.of("reverse", new String[] { "a", "b", "c" }, "{{ $r := reverse .items }}{{ join \",\" $r }}",
						"c,b,a", true),
				Arguments.of("last", new String[] { "a", "b", "c" }, "{{ last .items }}", "c", true),
				Arguments.of("first", new String[] { "x", "y", "z" }, "{{ first .items }}", "x", true),
				Arguments.of("first-string", "hello", "{{ first .items }}", "h", true),
				Arguments.of("reverse-string", "hello", "{{ reverse .items }}", "olleh", true),
				Arguments.of("first-set", new HashSet<>(Arrays.asList("x", "y")), "{{ first .items }}", null, false),
				Arguments.of("last-set", new HashSet<>(Arrays.asList("a", "b", "c")), "{{ last .items }}", null, false),
				Arguments.of("rest-set", new HashSet<>(Arrays.asList("a", "b", "c")),
						"{{ $r := rest .items }}{{ len $r }}", null, false),
				Arguments.of("initial-set", new HashSet<>(Arrays.asList("a", "b", "c")),
						"{{ $i := initial .items }}{{ len $i }}", null, false),
				Arguments.of("uniq-array", new String[] { "a", "b", "a", "c" },
						"{{ $u := uniq .items }}{{ join \",\" $u }}", null, false));
	}

	// Additional collection operations

	@Test
	void testTuple() throws IOException, TemplateException {
		assertEquals("a,b,c", exec("{{ $t := tuple \"a\" \"b\" \"c\" }}{{ join \",\" $t }}"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			// Reproduces cert-manager v1.20.0 pattern: ne (len .) 4
			// len returns int (Integer), literal 4 is long (Long) — eq/ne must handle
			// mixed
			// types
			"{{ $t := tuple \"a\" \"b\" \"c\" \"d\" }}{{ eq (len $t) 4 }} | true",
			"{{ $t := tuple \"a\" \"b\" \"c\" \"d\" }}{{ ne (len $t) 4 }} | false",
			"{{ $t := tuple \"a\" \"b\" }}{{ ne (len $t) 4 }}             | true" })
	void testTupleLenWithComparison(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "{{ $s := split \",\" \"a,b,c\" }}{{ $s._1 }}            | b",
					"{{ $s := split \",\" \"a,b,c\" }}{{ $s._0 }}              | a",
					"{{ $s := splitList \",\" \"a,b,c\" }}{{ len $s }}          | 3",
					"{{ $s := splitn \",\" 2 \"a,b,c\" }}{{ $s._0 }}           | a",
					"{{ $s := splitn \",\" 2 \"a,b,c\" }}{{ $s._1 }}           | b,c" })
	void testSplitFunctions(String template, String expected) throws IOException, TemplateException {
		assertEquals(expected, exec(template));
	}

	@Test
	void testPluck() throws IOException, TemplateException {
		assertEquals("2", exec(
				"{{ $d1 := dict \"name\" \"John\" \"age\" 30 }}{{ $d2 := dict \"name\" \"Jane\" \"age\" 25 }}{{ $p := pluck \"name\" $d1 $d2 }}{{ len $p }}"));
	}

	@Test
	void testDeepCopyList() throws IOException, TemplateException {
		assertEquals("2", exec("{{ $l := list \"a\" \"b\" }}{{ $c := deepCopy $l }}{{ len $c }}"));
	}

	// --- New functions: chunk, mustChunk, push, mustPush ---

	@Test
	void testChunk() throws IOException, TemplateException {
		assertEquals("3", exec("{{ $chunks := chunk 2 (list \"a\" \"b\" \"c\" \"d\" \"e\") }}{{ len $chunks }}"));
	}

	@Test
	void testChunkFirstElement() throws IOException, TemplateException {
		assertEquals("2", exec("{{ $chunks := chunk 2 (list \"a\" \"b\" \"c\") }}{{ len (index $chunks 0) }}"));
	}

	@Test
	void testChunkLastPartial() throws IOException, TemplateException {
		assertEquals("1", exec("{{ $chunks := chunk 2 (list \"a\" \"b\" \"c\") }}{{ len (index $chunks 1) }}"));
	}

	@Test
	void testMustChunk() throws IOException, TemplateException {
		assertEquals("3", exec("{{ $chunks := mustChunk 1 (list \"a\" \"b\" \"c\") }}{{ len $chunks }}"));
	}

	@Test
	void testPushAlias() throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", new ArrayList<>(Arrays.asList("a", "b")));
		assertEquals("3", execWithData("{{ $r := push .items \"c\" }}{{ len $r }}", data));
	}

	@Test
	void testMustPushAlias() throws IOException, TemplateException {
		Map<String, Object> data = Map.of("items", new ArrayList<>(Arrays.asList("a", "b")));
		assertEquals("3", execWithData("{{ $r := mustPush .items \"c\" }}{{ len $r }}", data));
	}

}
