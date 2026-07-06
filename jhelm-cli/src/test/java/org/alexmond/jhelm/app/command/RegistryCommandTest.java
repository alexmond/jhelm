package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.RegistryLoginOptions;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RegistryCommandTest {

	@Mock
	private RepoManager repoManager;

	@Mock
	private RegistryManager registryManager;

	private RegistryCommand.LoginCommand loginCommand;

	private RegistryCommand.LogoutCommand logoutCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		loginCommand = new RegistryCommand.LoginCommand(repoManager);
		logoutCommand = new RegistryCommand.LogoutCommand(registryManager);
	}

	@Test
	void testLoginCommandSuccess() throws IOException {
		doNothing().when(repoManager).registryLogin(anyString(), anyString(), anyString(), any());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass");

		verify(repoManager).registryLogin(eq("registry.example.com"), eq("user"), eq("pass"), any());
	}

	@Test
	void testLoginCommandWithError() throws IOException {
		doThrow(new IOException("Test error")).when(repoManager)
			.registryLogin(anyString(), anyString(), anyString(), any());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass");
	}

	@Test
	void testLoginHandlesSsrfRejectionCleanly() throws IOException {
		// the SSRF guard throws SecurityException for a disallowed host — it must surface
		// as
		// a clean error, not an uncaught exception
		doThrow(new SecurityException("blocked host")).when(repoManager)
			.registryLogin(anyString(), anyString(), anyString(), any());

		CommandLine cmd = new CommandLine(loginCommand);
		int code = cmd.execute("registry.example.com", "-u", "user", "-p", "pass");

		assertEquals(CommandLine.ExitCode.SOFTWARE, code);
	}

	@Test
	void testLoginReadsPasswordFromStdin() throws IOException {
		doNothing().when(repoManager).registryLogin(anyString(), anyString(), anyString(), any());
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
		verify(repoManager).registryLogin(eq("registry.example.com"), eq("user"), eq("s3cret"), any());
	}

	@Test
	void testLoginPassesTlsOptionsWithoutPersisting() throws IOException {
		doNothing().when(repoManager).registryLogin(anyString(), anyString(), anyString(), any());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass", "--insecure", "--plain-http", "--ca-file",
				"/tmp/ca.pem", "--cert-file", "/tmp/client.crt", "--key-file", "/tmp/client.key");

		ArgumentCaptor<RegistryLoginOptions> captor = ArgumentCaptor.forClass(RegistryLoginOptions.class);
		verify(repoManager).registryLogin(eq("registry.example.com"), eq("user"), eq("pass"), captor.capture());
		RegistryLoginOptions opts = captor.getValue();
		assertTrue(opts.insecureSkipTlsVerify());
		assertTrue(opts.plainHttp());
		assertEquals("/tmp/ca.pem", opts.caFile());
		assertEquals("/tmp/client.crt", opts.certFile());
		assertEquals("/tmp/client.key", opts.keyFile());
	}

	@Test
	void testLoginDefaultsHaveNoTlsOverrides() throws IOException {
		doNothing().when(repoManager).registryLogin(anyString(), anyString(), anyString(), any());

		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass");

		ArgumentCaptor<RegistryLoginOptions> captor = ArgumentCaptor.forClass(RegistryLoginOptions.class);
		verify(repoManager).registryLogin(anyString(), anyString(), anyString(), captor.capture());
		RegistryLoginOptions opts = captor.getValue();
		assertFalse(opts.insecureSkipTlsVerify());
		assertFalse(opts.plainHttp());
	}

	@Test
	void testLoginRejectsPasswordAndStdinTogether() throws IOException {
		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user", "-p", "pass", "--password-stdin");
		verify(repoManager, never()).registryLogin(anyString(), anyString(), anyString(), any());
	}

	@Test
	void testLoginWithNoPasswordFails() throws IOException {
		// no -p, no --password-stdin, and no console under test → clean failure, no login
		CommandLine cmd = new CommandLine(loginCommand);
		cmd.execute("registry.example.com", "-u", "user");
		verify(repoManager, never()).registryLogin(anyString(), anyString(), anyString(), any());
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
