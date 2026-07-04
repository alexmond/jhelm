package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.alexmond.jhelm.core.config.JhelmEncryptProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@code jhelm encrypt} / {@code jhelm decrypt} through the real Picocli
 * command line: round-trip, {@code --key} override, and the missing-key error.
 */
class EncryptDecryptCommandTest {

	private final PrintStream originalOut = System.out;

	private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

	@AfterEach
	void restoreOut() {
		System.setOut(this.originalOut);
	}

	private String run(Object command, String... args) {
		this.captured.reset();
		System.setOut(new PrintStream(this.captured, true, StandardCharsets.UTF_8));
		int exit = new CommandLine(command).execute(args);
		System.setOut(this.originalOut);
		assertEquals(CommandLine.ExitCode.OK, exit, "command should succeed");
		return this.captured.toString(StandardCharsets.UTF_8).strip();
	}

	private static JhelmEncryptProperties propsWithKey(String key) {
		JhelmEncryptProperties props = new JhelmEncryptProperties();
		props.setKey(key);
		return props;
	}

	@Test
	void testEncryptThenDecryptRoundTrip() {
		JhelmEncryptProperties props = propsWithKey("cli-key");
		String token = run(new EncryptCommand(props), "hunter2");
		assertTrue(token.startsWith("{cipher}"), "encrypt emits a {cipher} token");
		assertNotEquals("hunter2", token, "the token is not the plaintext");

		String plain = run(new DecryptCommand(props), token);
		assertEquals("hunter2", plain, "decrypt returns the original plaintext");
	}

	@Test
	void testKeyFlagOverridesProperty() {
		// Encrypt with the --key flag (no configured key), decrypt with the same flag.
		String token = run(new EncryptCommand(new JhelmEncryptProperties()), "s", "--key", "flag-key");
		String plain = run(new DecryptCommand(new JhelmEncryptProperties()), token, "--key", "flag-key");
		assertEquals("s", plain);
	}

	@Test
	void testMissingKeyFails() {
		int exit = new CommandLine(new EncryptCommand(new JhelmEncryptProperties())).execute("value");
		assertEquals(CommandLine.ExitCode.SOFTWARE, exit, "no key configured or passed -> failure exit code");
	}

}
