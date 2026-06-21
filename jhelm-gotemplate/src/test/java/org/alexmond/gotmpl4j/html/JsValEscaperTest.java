package org.alexmond.gotmpl4j.html;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsValEscaperTest {

	@Test
	void jsonMarshalStrings() {
		assertEquals("\"foo\"", JsonMarshal.marshal("foo"));
		assertEquals("\"a\\\"b\"", JsonMarshal.marshal("a\"b"));
		// <, >, & are escaped like Go's encoding/json (escapeHTML default on).
		assertEquals("\"\\u003cb\\u003e \\u0026 x\"", JsonMarshal.marshal("<b> & x"));
		assertEquals("\"tab\\tend\"", JsonMarshal.marshal("tab\tend"));
	}

	@Test
	void jsonMarshalNumbersAndBools() {
		assertEquals("42", JsonMarshal.marshal(42L));
		assertEquals("3.5", JsonMarshal.marshal(3.5));
		// Whole floats print without a decimal point.
		assertEquals("8080", JsonMarshal.marshal(8080.0));
		assertEquals("0.0001", JsonMarshal.marshal(0.0001));
		// Very small / very large floats use exponent form.
		assertEquals("1e-07".replace("e-07", "e-7"), JsonMarshal.marshal(1e-7));
		assertEquals("1e+21", JsonMarshal.marshal(1e21));
		assertEquals("true", JsonMarshal.marshal(Boolean.TRUE));
		assertEquals("null", JsonMarshal.marshal(null));
	}

	@Test
	void jsonMarshalMapSortsKeys() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("b", 2L);
		m.put("a", 1L);
		assertEquals("{\"a\":1,\"b\":2}", JsonMarshal.marshal(m));
	}

	@Test
	void jsonMarshalList() {
		assertEquals("[1,2,3]", JsonMarshal.marshal(List.of(1L, 2L, 3L)));
		assertEquals("[\"x\",\"y\"]", JsonMarshal.marshal(List.of("x", "y")));
	}

	@Test
	void jsValEscaperWrapsAndPads() {
		// A JSON string boundary is not an identifier part, so no padding.
		assertEquals("\"foo\"", JsEscapers.jsValEscaper("foo"));
		// A number's digits are identifier parts, so it is padded so it cannot merge with
		// a
		// neighbouring keyword.
		assertEquals(" 42 ", JsEscapers.jsValEscaper(42L));
		assertEquals(" true ", JsEscapers.jsValEscaper(Boolean.TRUE));
		assertEquals(" null ", JsEscapers.jsValEscaper((Object) null));
		assertEquals("\"\\u003cb\\u003e\"", JsEscapers.jsValEscaper("<b>"));
	}

	@Test
	void jsValEscaperHonoursSafeJs() {
		// template.JS is emitted verbatim; template.JSStr is wrapped in quotes.
		assertEquals("x + 1", JsEscapers.jsValEscaper(SafeContent.js("x + 1")));
		assertEquals("\"abc\"", JsEscapers.jsValEscaper(SafeContent.jsStr("abc")));
	}

}
