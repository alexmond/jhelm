package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvCommandTest {

	@Test
	void testEnvPrintsKeyValues() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new CommandLine(new EnvCommand()).execute();
		}
		finally {
			System.setOut(original);
		}
		String out = bos.toString(StandardCharsets.UTF_8);
		assertTrue(out.contains("HELM_NAMESPACE="));
		assertTrue(out.contains("JHELM_VERSION="));
		assertTrue(out.contains("JAVA_VERSION="));
	}

}
