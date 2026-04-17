package org.alexmond.jhelm.app.output;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliOutputTest {

	private final PrintStream originalOut = System.out;

	private final PrintStream originalErr = System.err;

	private ByteArrayOutputStream outContent;

	private ByteArrayOutputStream errContent;

	@BeforeEach
	void setUp() {
		outContent = new ByteArrayOutputStream();
		errContent = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outContent));
		System.setErr(new PrintStream(errContent));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		System.setErr(originalErr);
		CliOutput.setEnabled(true);
	}

	@Test
	void testSuccessContainsAnsiWhenEnabled() {
		CliOutput.setEnabled(true);
		String result = CliOutput.success("done");
		assertTrue(result.contains("\u001B["), "Should contain ANSI escape code");
		assertTrue(result.contains("done"), "Should contain original text");
	}

	@Test
	void testErrorContainsAnsiWhenEnabled() {
		CliOutput.setEnabled(true);
		String result = CliOutput.error("fail");
		assertTrue(result.contains("\u001B["), "Should contain ANSI escape code");
		assertTrue(result.contains("fail"), "Should contain original text");
	}

	@Test
	void testWarnContainsAnsiWhenEnabled() {
		CliOutput.setEnabled(true);
		String result = CliOutput.warn("caution");
		assertTrue(result.contains("\u001B["), "Should contain ANSI escape code");
		assertTrue(result.contains("caution"), "Should contain original text");
	}

	@Test
	void testInfoContainsAnsiWhenEnabled() {
		CliOutput.setEnabled(true);
		String result = CliOutput.info("note");
		assertTrue(result.contains("\u001B["), "Should contain ANSI escape code");
		assertTrue(result.contains("note"), "Should contain original text");
	}

	@Test
	void testBoldContainsAnsiWhenEnabled() {
		CliOutput.setEnabled(true);
		String result = CliOutput.bold("header");
		assertTrue(result.contains("\u001B["), "Should contain ANSI escape code");
		assertTrue(result.contains("header"), "Should contain original text");
	}

	@Test
	void testHeaderDelegatesToBold() {
		CliOutput.setEnabled(true);
		String header = CliOutput.header("title");
		String bold = CliOutput.bold("title");
		assertEquals(bold, header);
	}

	@Test
	void testSuccessPlainWhenDisabled() {
		CliOutput.setEnabled(false);
		String result = CliOutput.success("done");
		assertEquals("done", result);
		assertFalse(result.contains("\u001B["), "Should not contain ANSI codes");
	}

	@Test
	void testErrorPlainWhenDisabled() {
		CliOutput.setEnabled(false);
		String result = CliOutput.error("fail");
		assertEquals("fail", result);
	}

	@Test
	void testWarnPlainWhenDisabled() {
		CliOutput.setEnabled(false);
		String result = CliOutput.warn("caution");
		assertEquals("caution", result);
	}

	@Test
	void testInfoPlainWhenDisabled() {
		CliOutput.setEnabled(false);
		String result = CliOutput.info("note");
		assertEquals("note", result);
	}

	@Test
	void testBoldPlainWhenDisabled() {
		CliOutput.setEnabled(false);
		String result = CliOutput.bold("header");
		assertEquals("header", result);
	}

	@Test
	void testPrintlnWritesToStdout() {
		CliOutput.println("hello");
		assertEquals("hello\n", outContent.toString());
	}

	@Test
	void testPrintfWritesToStdout() {
		CliOutput.printf("%-5s %d\n", "test", 42);
		assertEquals("test  42\n", outContent.toString());
	}

	@Test
	void testErrPrintlnWritesToStderr() {
		CliOutput.errPrintln("error msg");
		assertEquals("error msg\n", errContent.toString());
	}

	@Test
	void testSetEnabledToggle() {
		CliOutput.setEnabled(false);
		assertFalse(CliOutput.enabled());

		CliOutput.setEnabled(true);
		assertTrue(CliOutput.enabled());
	}

}
