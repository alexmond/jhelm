package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

class ReflectionFunctionsTest {

	private void execute(String name, String text, Object data, StringWriter writer)
			throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse(name, text);
		template.execute(name, data, writer);
	}

	private String eval(String text, Map<String, Object> data) throws IOException, TemplateException {
		StringWriter writer = new StringWriter();
		execute("test", text, data, writer);
		return writer.toString();
	}

	// ========== typeOf ==========

	@Test
	void testTypeOfString() throws IOException, TemplateException {
		assertEquals("string", eval("{{ typeOf .value }}", Map.of("value", "hello")));
	}

	@Test
	void testTypeOfInt() throws IOException, TemplateException {
		assertEquals("int", eval("{{ typeOf .value }}", Map.of("value", 42)));
	}

	@Test
	void testTypeOfLong() throws IOException, TemplateException {
		assertEquals("int64", eval("{{ typeOf .value }}", Map.of("value", 42L)));
	}

	@Test
	void testTypeOfDouble() throws IOException, TemplateException {
		assertEquals("float64", eval("{{ typeOf .value }}", Map.of("value", 3.14)));
	}

	@Test
	void testTypeOfFloat() throws IOException, TemplateException {
		assertEquals("float32", eval("{{ typeOf .value }}", Map.of("value", 3.14f)));
	}

	@Test
	void testTypeOfBool() throws IOException, TemplateException {
		assertEquals("bool", eval("{{ typeOf .value }}", Map.of("value", true)));
	}

	@Test
	void testTypeOfList() throws IOException, TemplateException {
		assertEquals("[]interface {}", eval("{{ typeOf .value }}", Map.of("value", Arrays.asList(1, 2, 3))));
	}

	@Test
	void testTypeOfMap() throws IOException, TemplateException {
		Map<String, Object> nested = new HashMap<>();
		nested.put("key", "value");
		assertEquals("map[string]interface {}", eval("{{ typeOf .value }}", Map.of("value", nested)));
	}

	@Test
	void testTypeOfNull() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("value", null);
		assertEquals("<nil>", eval("{{ typeOf .value }}", data));
	}

	@Test
	void testTypeOfBigInteger() throws IOException, TemplateException {
		assertEquals("int64", eval("{{ typeOf .value }}", Map.of("value", BigInteger.valueOf(999))));
	}

	@Test
	void testTypeOfBigDecimal() throws IOException, TemplateException {
		assertEquals("float64", eval("{{ typeOf .value }}", Map.of("value", BigDecimal.valueOf(1.5))));
	}

	@Test
	void testTypeOfShort() throws IOException, TemplateException {
		assertEquals("int", eval("{{ typeOf .value }}", Map.of("value", (short) 5)));
	}

	// ========== kindOf ==========

	@Test
	void testKindOfString() throws IOException, TemplateException {
		assertEquals("string", eval("{{ kindOf .value }}", Map.of("value", "test")));
	}

	@Test
	void testKindOfInt() throws IOException, TemplateException {
		// Go YAML unmarshals all numbers as float64; kindOf maps all numeric types to
		// float64
		assertEquals("float64", eval("{{ kindOf .value }}", Map.of("value", 123)));
	}

	@Test
	void testKindOfFloat64() throws IOException, TemplateException {
		assertEquals("float64", eval("{{ kindOf .value }}", Map.of("value", 1.5)));
	}

	@Test
	void testKindOfBool() throws IOException, TemplateException {
		assertEquals("bool", eval("{{ kindOf .value }}", Map.of("value", false)));
	}

	@Test
	void testKindOfMap() throws IOException, TemplateException {
		assertEquals("map", eval("{{ kindOf .value }}", Map.of("value", new HashMap<>())));
	}

	@Test
	void testKindOfSlice() throws IOException, TemplateException {
		assertEquals("slice", eval("{{ kindOf .value }}", Map.of("value", Arrays.asList("a", "b"))));
	}

	// ========== typeIs ==========

	@Test
	void testTypeIsString() throws IOException, TemplateException {
		assertEquals("true", eval("{{ typeIs \"string\" .value }}", Map.of("value", "hello")));
	}

	@Test
	void testTypeIsStringFalse() throws IOException, TemplateException {
		assertEquals("false", eval("{{ typeIs \"string\" .value }}", Map.of("value", 123)));
	}

	@Test
	void testTypeIsInt() throws IOException, TemplateException {
		assertEquals("true", eval("{{ typeIs \"int\" .value }}", Map.of("value", 42)));
	}

	@Test
	void testTypeIsFloat64() throws IOException, TemplateException {
		assertEquals("true", eval("{{ typeIs \"float64\" .value }}", Map.of("value", 3.14)));
	}

	@Test
	void testTypeIsMap() throws IOException, TemplateException {
		assertEquals("true", eval("{{ typeIs \"map[string]interface {}\" .value }}", Map.of("value", new HashMap<>())));
	}

	// ========== typeIsLike ==========

	@Test
	void testTypeIsLikeString() throws IOException, TemplateException {
		assertEquals("true", eval("{{ typeIsLike \"string\" .value }}", Map.of("value", "test")));
	}

	@Test
	void testTypeIsLikePointer() throws IOException, TemplateException {
		// In Go, typeIsLike checks target == type || "*"+target == type
		// Since Java has no pointers, this should match the exact type
		assertEquals("false", eval("{{ typeIsLike \"*string\" .value }}", Map.of("value", "test")));
	}

	// ========== kindIs ==========

	@Test
	void testKindIsString() throws IOException, TemplateException {
		assertEquals("true", eval("{{ kindIs \"string\" .value }}", Map.of("value", "hello")));
	}

	@Test
	void testKindIsInt() throws IOException, TemplateException {
		// Go YAML unmarshals all numbers as float64, so kindIs "int" returns false
		assertEquals("false", eval("{{ kindIs \"int\" .value }}", Map.of("value", 42)));
	}

	@Test
	void testKindIsFloat64() throws IOException, TemplateException {
		assertEquals("true", eval("{{ kindIs \"float64\" .value }}", Map.of("value", 3.14)));
	}

	@Test
	void testKindIsMap() throws IOException, TemplateException {
		assertEquals("true", eval("{{ kindIs \"map\" .value }}", Map.of("value", new HashMap<>())));
	}

	@Test
	void testKindIsSlice() throws IOException, TemplateException {
		assertEquals("true", eval("{{ kindIs \"slice\" .value }}", Map.of("value", Arrays.asList(1))));
	}

	@Test
	void testKindIsFloat64ForInteger() throws IOException, TemplateException {
		// Go YAML unmarshals 15 as float64 — kindIs "float64" must match Java Integer
		assertEquals("true", eval("{{ kindIs \"float64\" .value }}", Map.of("value", 15)));
	}

	@Test
	void testKindIsFloat64ForLong() throws IOException, TemplateException {
		assertEquals("true", eval("{{ kindIs \"float64\" .value }}", Map.of("value", 42L)));
	}

	@Test
	void testKindIsNumberAlias() throws IOException, TemplateException {
		// "number" is a JHelm alias that matches any numeric kind
		assertEquals("true", eval("{{ kindIs \"number\" .value }}", Map.of("value", 42)));
		assertEquals("true", eval("{{ kindIs \"number\" .value }}", Map.of("value", 3.14)));
	}

	@Test
	void testKindIsListAlias() throws IOException, TemplateException {
		// "list" is a JHelm alias that matches "slice"
		assertEquals("true", eval("{{ kindIs \"list\" .value }}", Map.of("value", Arrays.asList("a"))));
	}

	// ========== deepEqual ==========

	@Test
	void testDeepEqualSame() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("a", Arrays.asList(1, 2, 3));
		data.put("b", Arrays.asList(1, 2, 3));
		assertEquals("true", eval("{{ deepEqual .a .b }}", data));
	}

	@Test
	void testDeepEqualDifferent() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("a", Arrays.asList(1, 2, 3));
		data.put("b", Arrays.asList(1, 2, 4));
		assertEquals("false", eval("{{ deepEqual .a .b }}", data));
	}

	@Test
	void testDeepEqualMaps() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("a", Map.of("key", "value"));
		data.put("b", Map.of("key", "value"));
		assertEquals("true", eval("{{ deepEqual .a .b }}", data));
	}

	@Test
	void testDeepEqualStrings() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("a", "hello");
		data.put("b", "hello");
		assertEquals("true", eval("{{ deepEqual .a .b }}", data));
	}

	// ========== Vault chart pattern: typeOf eq "string" ==========

	@Test
	void testVaultTypeOfStringComparison() throws IOException, TemplateException {
		// Reproduces the exact pattern vault uses: {{ $tp := typeOf $config }} {{ if eq
		// $tp "string" }}
		Map<String, Object> data = new HashMap<>();
		data.put("config", "listener \"tcp\" {\n  address = \"0.0.0.0:8200\"\n}");
		assertEquals("yes",
				eval("{{ $tp := typeOf .config }}{{ if eq $tp \"string\" }}yes{{ else }}no{{ end }}", data));
	}

	@Test
	void testVaultTypeOfMapComparison() throws IOException, TemplateException {
		Map<String, Object> data = new HashMap<>();
		data.put("config", Map.of("listener", Map.of("tcp", Map.of("address", "0.0.0.0:8200"))));
		assertEquals("no", eval("{{ $tp := typeOf .config }}{{ if eq $tp \"string\" }}yes{{ else }}no{{ end }}", data));
	}

}
