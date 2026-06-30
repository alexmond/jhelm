package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.RegistryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RegistryCommandTest {

	@Mock
	private RegistryManager registryManager;

	private RegistryCommand.LoginCommand loginCommand;

	private RegistryCommand.LogoutCommand logoutCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		loginCommand = new RegistryCommand.LoginCommand(registryManager);
		logoutCommand = new RegistryCommand.LogoutCommand(registryManager);
	}

	@Test
	void testLoginCommandSuccess() throws IOException {
		doNothing().when(registryManager).login(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass");
	}

	@Test
	void testLoginCommandWithError() throws IOException {
		doThrow(new IOException("Test error")).when(registryManager).login(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass");
	}

	@Test
	void testLoginReadsPasswordFromStdin() throws IOException {
		doNothing().when(registryManager).login(anyString(), anyString(), anyString());
		InputStream original = System.in;
		try {
			System.setIn(new ByteArrayInputStream("s3cret\n".getBytes(StandardCharsets.UTF_8)));
			CommandLine cmd = new CommandLine(loginCommand);
			cmd.execute("registry.example.com", "-u", "user", "--password-stdin");
		}
		finally {
			System.setIn(original);
		}
		// trailing newline is stripped; the secret never appears on the command line
		verify(registryManager).login(eq("registry.example.com"), eq("user"), eq("s3cret"));
	}

	@Test
	void testLoginRejectsPasswordAndStdinTogether() throws IOException {
		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass", "--password-stdin");
		verify(registryManager, never()).login(anyString(), anyString(), anyString());
	}

	@Test
	void testLoginWithNoPasswordFails() throws IOException {
		// no -p, no --password-stdin, and no console under test → clean failure, no login
		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user");
		verify(registryManager, never()).login(anyString(), anyString(), anyString());
	}

	@Test
	void testLogoutCommandSuccess() throws IOException {
		doNothing().when(registryManager).logout(anyString());

		CommandLine cmd = new CommandLine(logoutCommand);
		cmd.execute("registry.example.com");
	}

	@Test
	void testLogoutCommandWithError() throws IOException {
		doThrow(new IOException("Test error")).when(registryManager).logout(anyString());

		CommandLine cmd = new CommandLine(logoutCommand);
		cmd.execute("registry.example.com");
	}

	@Test
	void testRegistryCommandShowsUsage() {
		RegistryCommand registryCommand = new RegistryCommand();
		CommandLine cmd = new CommandLine(registryCommand);
		cmd.execute();
	}

}
