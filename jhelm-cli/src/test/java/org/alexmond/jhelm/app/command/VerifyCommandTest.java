package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.alexmond.jhelm.core.action.VerifyAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class VerifyCommandTest {

	@Mock
	private VerifyAction verifyAction;

	private VerifyCommand verifyCommand;

	private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	private final PrintStream originalErr = System.err;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		verifyCommand = new VerifyCommand(verifyAction);
		System.setOut(new PrintStream(outStream));
		System.setErr(new PrintStream(errStream));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	@Test
	void testVerifySuccess() throws Exception {
		doNothing().when(verifyAction).verify(anyString(), anyString());

		CommandLine cmd = new CommandLine(verifyCommand);
		cmd.execute("chart-1.0.0.tgz");

		verify(verifyAction).verify(eq("chart-1.0.0.tgz"), anyString());
		assertTrue(outStream.toString().contains("Verification succeeded"));
	}

	@Test
	void testVerifyWithKeyring() throws Exception {
		doNothing().when(verifyAction).verify(anyString(), anyString());

		CommandLine cmd = new CommandLine(verifyCommand);
		cmd.execute("chart-1.0.0.tgz", "--keyring", "/path/to/pubring.gpg");

		verify(verifyAction).verify("chart-1.0.0.tgz", "/path/to/pubring.gpg");
	}

	@Test
	void testVerifyFailure() throws Exception {
		doThrow(new RuntimeException("signature mismatch")).when(verifyAction).verify(anyString(), anyString());

		CommandLine cmd = new CommandLine(verifyCommand);
		cmd.execute("chart-1.0.0.tgz");

		assertTrue(errStream.toString().contains("Error"));
		assertTrue(errStream.toString().contains("signature mismatch"));
	}

	@Test
	void testVerifyDefaultKeyring() throws Exception {
		doNothing().when(verifyAction).verify(anyString(), anyString());

		CommandLine cmd = new CommandLine(verifyCommand);
		cmd.execute("chart-1.0.0.tgz");

		String expectedKeyring = System.getProperty("user.home") + "/.gnupg/pubring.gpg";
		verify(verifyAction).verify("chart-1.0.0.tgz", expectedKeyring);
	}

}
