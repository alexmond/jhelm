package org.alexmond.jhelm.gotemplate.sprig.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.TemplateException;
import org.junit.jupiter.api.Test;

class PathFunctionsTest {

	private String eval(String text) throws IOException, TemplateException {
		GoTemplate template = new GoTemplate();
		template.parse("test", text);
		StringWriter writer = new StringWriter();
		template.execute("test", Map.of(), writer);
		return writer.toString();
	}

	@Test
	void testBase() throws IOException, TemplateException {
		assertEquals("file.txt", eval("{{ base \"path/to/file.txt\" }}"));
	}

	@Test
	void testBaseRootPath() throws IOException, TemplateException {
		assertEquals("file.txt", eval("{{ base \"/path/to/file.txt\" }}"));
	}

	@Test
	void testBaseNoSlash() throws IOException, TemplateException {
		assertEquals("file.txt", eval("{{ base \"file.txt\" }}"));
	}

	@Test
	void testBaseSlashOnly() throws IOException, TemplateException {
		assertEquals("/", eval("{{ base \"/\" }}"));
	}

	@Test
	void testDir() throws IOException, TemplateException {
		assertEquals("path/to", eval("{{ dir \"path/to/file.txt\" }}"));
	}

	@Test
	void testDirRoot() throws IOException, TemplateException {
		assertEquals("/", eval("{{ dir \"/file.txt\" }}"));
	}

	@Test
	void testDirNoSlash() throws IOException, TemplateException {
		assertEquals(".", eval("{{ dir \"file.txt\" }}"));
	}

	@Test
	void testExt() throws IOException, TemplateException {
		assertEquals(".txt", eval("{{ ext \"file.txt\" }}"));
	}

	@Test
	void testExtNone() throws IOException, TemplateException {
		assertEquals("", eval("{{ ext \"file\" }}"));
	}

	@Test
	void testClean() throws IOException, TemplateException {
		assertEquals("a/b/c", eval("{{ clean \"a//b/../b/c\" }}"));
	}

	@Test
	void testIsAbsTrue() throws IOException, TemplateException {
		assertEquals("true", eval("{{ isAbs \"/path\" }}"));
	}

	@Test
	void testIsAbsFalse() throws IOException, TemplateException {
		assertEquals("false", eval("{{ isAbs \"path\" }}"));
	}

}
