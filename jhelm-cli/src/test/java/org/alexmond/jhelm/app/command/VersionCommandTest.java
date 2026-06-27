package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCommandTest {

	private String run(String... args) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new CommandLine(new VersionCommand()).execute(args);
		}
		finally {
			System.setOut(original);
		}
		return bos.toString(StandardCharsets.UTF_8);
	}

	@Test
	void testVersionStringIsPrefixed() {
		assertTrue(VersionCommand.versionString().startsWith("v"), "version should be v-prefixed");
	}

	@Test
	void testLongOutputIncludesJava() {
		String out = run();
		assertTrue(out.contains("jhelm version"));
		assertTrue(out.contains("Java"));
	}

	@Test
	void testShortOutputIsVersionOnly() {
		String out = run("--short").trim();
		assertTrue(out.startsWith("v"));
		assertFalse(out.contains("jhelm version"));
	}

}
