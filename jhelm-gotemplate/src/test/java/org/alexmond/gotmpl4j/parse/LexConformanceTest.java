package org.alexmond.gotmpl4j.parse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the lexer, ported from Go text/template/parse lex_test.go
 * {@code lexTests}. Each case lexes a template and asserts the token sequence (type and,
 * for content tokens, value) matches Go's. Error cases (Go emits a final
 * {@code itemError}) only require gotmpl4j to signal an error — a thrown exception or an
 * ERROR token — since the exact message wording differs.
 *
 * <p>
 * The custom-delimiter cases (Go's {@code lexDelimTests}, {@code $$}/{@code @@}) are not
 * included here; they need the lexer built with custom delimiters.
 */
class LexConformanceTest {

	@Test
	void lexConformance() throws Exception {
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				LexConformanceTest.class.getResourceAsStream("/conformance/lex_cases.tsv"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String name = p[0];
				String input = new String(Base64.getDecoder().decode(p[1]), StandardCharsets.UTF_8);
				List<String[]> expected = parseExpected(p[2]);
				total++;
				boolean expectsError = !expected.isEmpty() && "ERROR".equals(expected.get(expected.size() - 1)[0]);
				String result = check(input, expected, expectsError);
				if (result != null) {
					failures.add("[" + name + "] " + result);
				}
			}
		}
		int count = total;
		assertTrue(count > 30, "expected the lexer table; got only " + count + " cases");
		assertTrue(failures.isEmpty(), () -> "lexer divergences from Go lexTests (" + failures.size() + "/" + count
				+ "):\n" + String.join("\n", failures));
	}

	private String check(String input, List<String[]> expected, boolean expectsError) {
		List<Token> tokens;
		try {
			tokens = new Lexer(input, true).getTokens();
		}
		catch (RuntimeException ex) {
			// A thrown lex error satisfies an error case; otherwise it is a divergence.
			return expectsError ? null : "threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
		}
		if (expectsError) {
			boolean signalled = tokens.stream().anyMatch((t) -> t.type() == TokenType.ERROR);
			return signalled ? null : "expected an error but lexed cleanly: " + render(tokens);
		}
		// Clean case: compare the type+value sequence exactly.
		if (tokens.size() != expected.size()) {
			return "want " + renderExpected(expected) + "got " + render(tokens);
		}
		for (int i = 0; i < expected.size(); i++) {
			if (!expected.get(i)[0].equals(tokens.get(i).type().name())
					|| !expected.get(i)[1].equals(tokens.get(i).value())) {
				return "want " + renderExpected(expected) + "got " + render(tokens);
			}
		}
		return null;
	}

	private static List<String[]> parseExpected(String field) {
		List<String[]> out = new ArrayList<>();
		// Tokens are space-separated; each is "TYPE,base64(value)" or a bare "ERROR".
		for (String tok : field.split(" ")) {
			int sep = tok.indexOf(',');
			if (sep < 0) {
				out.add(new String[] { tok, "ERROR".equals(tok) ? null : "" });
			}
			else {
				String val = new String(Base64.getDecoder().decode(tok.substring(sep + 1)), StandardCharsets.UTF_8);
				out.add(new String[] { tok.substring(0, sep), val });
			}
		}
		return out;
	}

	private static String render(List<Token> tokens) {
		StringBuilder sb = new StringBuilder();
		for (Token t : tokens) {
			sb.append(t.type()).append(":[").append(t.value()).append("] ");
		}
		return sb.toString();
	}

	private static String renderExpected(List<String[]> expected) {
		StringBuilder sb = new StringBuilder();
		for (String[] e : expected) {
			sb.append(e[0]).append(":[").append(e[1]).append("] ");
		}
		return sb.toString();
	}

}
