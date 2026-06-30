package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Implements {@code jhelm registry}, managing authentication to OCI registries via the
 * {@code login} and {@code logout} subcommands.
 */
@Component
@CommandLine.Command(name = "registry", mixinStandardHelpOptions = true,
		description = "Login to or logout from a registry",
		subcommands = { RegistryCommand.LoginCommand.class, RegistryCommand.LogoutCommand.class })
@Slf4j
public class RegistryCommand implements Runnable {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public RegistryCommand() {
	}

	/**
	 * Prints the usage help when {@code registry} is invoked without a subcommand.
	 */
	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	/** Implements {@code registry login}: authenticates to an OCI registry. */
	@Component
	@CommandLine.Command(name = "login", mixinStandardHelpOptions = true, description = "Login to a registry")
	@Slf4j
	public static class LoginCommand implements Runnable {

		private final RegistryManager registryManager;

		@CommandLine.Parameters(index = "0", description = "registry server")
		private String server;

		@CommandLine.Option(names = { "-u", "--username" }, description = "registry username", required = true)
		private String username;

		@CommandLine.Option(names = { "-p", "--password" }, description = "registry password")
		private String password;

		@CommandLine.Option(names = "--password-stdin", description = "take the password from stdin")
		private boolean passwordStdin;

		/**
		 * Creates the command.
		 * @param registryManager the manager that performs the registry login
		 */
		public LoginCommand(RegistryManager registryManager) {
			this.registryManager = registryManager;
		}

		@Override
		public void run() {
			try {
				registryManager.login(server, username, resolvePassword());
				CliOutput.println(CliOutput.success("Login Succeeded"));
			}
			catch (IllegalArgumentException ex) {
				CliOutput.errPrintln(CliOutput.error(ex.getMessage()));
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error logging in: " + ex.getMessage()));
			}
		}

		/**
		 * Resolves the password, keeping it off the command line where possible: from
		 * {@code --password-stdin} (piped), else {@code -p}, else an interactive hidden
		 * prompt. {@code --password-stdin} and {@code -p} are mutually exclusive.
		 * @return the resolved password
		 * @throws IOException if reading stdin fails
		 * @throws IllegalArgumentException if the options conflict or no password is
		 * given
		 */
		private String resolvePassword() throws IOException {
			if (passwordStdin) {
				if (password != null) {
					throw new IllegalArgumentException("--password and --password-stdin are mutually exclusive");
				}
				String fromStdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$",
						"");
				if (fromStdin.isEmpty()) {
					throw new IllegalArgumentException("No password supplied on stdin");
				}
				return fromStdin;
			}
			if (password != null) {
				return password;
			}
			Console console = System.console();
			if (console != null) {
				char[] entered = console.readPassword("Password: ");
				if (entered != null && entered.length > 0) {
					return new String(entered);
				}
			}
			throw new IllegalArgumentException("No password supplied; use -p, --password-stdin, or run interactively");
		}

	}

	/** Implements {@code registry logout}: removes stored credentials for a registry. */
	@Component
	@CommandLine.Command(name = "logout", mixinStandardHelpOptions = true, description = "Logout from a registry")
	@Slf4j
	public static class LogoutCommand implements Runnable {

		private final RegistryManager registryManager;

		@CommandLine.Parameters(index = "0", description = "registry server")
		private String server;

		/**
		 * Creates the command.
		 * @param registryManager the manager that performs the registry logout
		 */
		public LogoutCommand(RegistryManager registryManager) {
			this.registryManager = registryManager;
		}

		@Override
		public void run() {
			try {
				registryManager.logout(server);
				CliOutput.println(CliOutput.success("Logout Succeeded"));
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error logging out: " + ex.getMessage()));
			}
		}

	}

}
