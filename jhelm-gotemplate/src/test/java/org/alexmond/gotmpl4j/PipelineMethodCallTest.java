package org.alexmond.gotmpl4j;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a method call on a value receives the upstream pipeline value as its final
 * argument — Go's {@code arg | .obj.Method} calls {@code Method(arg)}. This is the
 * pattern Helm's {@code .Files.Get} relies on (e.g. {@code $path | $.Files.Get}); without
 * it the argument is dropped and the call returns empty.
 */
class PipelineMethodCallTest {

	private String render(String body, Object data) throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("t", body);
		StringWriter writer = new StringWriter();
		template.execute("t", data, writer);
		return writer.toString();
	}

	private Map<String, Object> data() {
		return Map.of("files", new FileBag(Map.of("a.txt", "content-a")));
	}

	@Test
	void testDirectMethodCallWithArgument() throws IOException, TemplateException {
		assertEquals("content-a", render("{{ .files.Get \"a.txt\" }}", data()));
	}

	@Test
	void testPipedMethodCallOnField() throws IOException, TemplateException {
		assertEquals("content-a", render("{{ \"a.txt\" | .files.Get }}", data()));
	}

	@Test
	void testPipedMethodCallOnRootVariable() throws IOException, TemplateException {
		// The exact shape used by grafana/k8s-monitoring: `$path | $.Files.Get`.
		assertEquals("content-a", render("{{ \"a.txt\" | $.files.Get }}", data()));
	}

	/**
	 * Minimal stand-in for Helm's {@code .Files} object: a behavioural map-like accessor.
	 */
	public static class FileBag {

		private final Map<String, String> files;

		FileBag(Map<String, String> files) {
			this.files = files;
		}

		public String Get(String name) {
			return files.getOrDefault(name, "");
		}

	}

}
