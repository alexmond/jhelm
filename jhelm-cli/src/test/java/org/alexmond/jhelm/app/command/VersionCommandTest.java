package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.alexmond.jhelm.app.VersionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCommandTest {

	private static VersionCommand versionCommand() {
		Properties props = new Properties();
		props.setProperty("version", "9.9.9-test");
		return new VersionCommand(new VersionInfo(new BuildProperties(props)));
	}

	private String run(String... args) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new CommandLine(versionCommand()).execute(args);
		}
		finally {
			System.setOut(original);
		}
		return bos.toString(StandardCharsets.UTF_8);
	}

	@Test
	void testVersionStringIsPrefixed() {
		assertTrue(versionCommand().versionString().startsWith("v"), "version should be v-prefixed");
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
