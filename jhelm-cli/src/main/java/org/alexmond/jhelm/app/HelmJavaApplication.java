package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.app.plugin.HelmPluginDispatcher;
import org.alexmond.jhelm.app.plugin.HelmPluginParameterExceptionHandler;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IFactory;

/**
 * Spring Boot entry point for the {@code jhelm} command-line application.
 * <p>
 * Bootstraps the Spring context, then hands the command-line arguments to the Picocli
 * {@link JHelmCommand} for parsing and execution. The process exit code reported back to
 * the shell is the Picocli execution result.
 */
@Slf4j
@SpringBootApplication
public class HelmJavaApplication implements CommandLineRunner, ExitCodeGenerator {

	// The unmodified argument vector, captured in main(). Picocli parses this (so the
	// global flags are accepted anywhere), while Spring receives the vector with those
	// flags stripped.
	private static String[] originalArgs = {};

	private final IFactory factory;

	private final JHelmCommand jHelmCommand;

	private final HelmPluginDispatcher pluginDispatcher;

	private int exitCode;

	/**
	 * Creates the application runner.
	 * @param factory the Picocli factory used to instantiate Spring-managed commands
	 * @param jHelmCommand the root {@code jhelm} command
	 * @param pluginDispatcher dispatches unmatched subcommands to installed Helm plugins
	 */
	public HelmJavaApplication(IFactory factory, JHelmCommand jHelmCommand, HelmPluginDispatcher pluginDispatcher) {
		this.factory = factory;
		this.jHelmCommand = jHelmCommand;
		this.pluginDispatcher = pluginDispatcher;
	}

	/**
	 * Application entry point; runs the Spring context and exits with the command
	 * execution result.
	 * @param args the command-line arguments
	 */
	public static void main(String[] args) {
		originalArgs = args.clone();
		// Resolve Helm's global persistent flags before the context is built (they
		// configure eagerly-created beans), and hide them from Spring's own arg parsing.
		GlobalOptionsPreParser.Result parsed = GlobalOptionsPreParser.parse(args);
		parsed.systemProperties().forEach(System::setProperty);
		System.exit(SpringApplication.exit(SpringApplication.run(HelmJavaApplication.class, parsed.springArgs())));
	}

	/**
	 * Parses and executes the command line via Picocli, capturing the exit code.
	 * @param args the command-line arguments passed by Spring Boot
	 */
	@Override
	public void run(String... args) {
		CommandLine cmd = new CommandLine(jHelmCommand, factory);
		cmd.setUsageHelpWidth(120);
		cmd.setColorScheme(CommandLine.Help.defaultColorScheme(Ansi.AUTO));
		// Route an unmatched top-level subcommand to an installed Helm plugin (helm-diff,
		// helm-secrets, …) before falling back to the default "Unmatched argument" error.
		cmd.setParameterExceptionHandler(
				new HelmPluginParameterExceptionHandler(cmd.getParameterExceptionHandler(), pluginDispatcher));
		// Picocli parses the original vector so the global flags (stripped from the
		// Spring
		// args) are still accepted and shown in --help.
		exitCode = cmd.execute(originalArgs);
	}

	/**
	 * Returns the exit code produced by the last command execution.
	 * @return the process exit code
	 */
	@Override
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Logs the CLI's security posture at startup, overriding the core banner (which is
	 * written for the network adapters, where {@code FULL} without an API key means
	 * disabled). For the standalone CLI, {@code FULL} alone enables mutations — the local
	 * kubeconfig is the trust boundary, like {@code helm} — so this reports the posture
	 * the CLI actually enforces.
	 * @param policy the resolved security policy
	 * @return an application runner that logs the posture once
	 */
	@Bean
	public ApplicationRunner jhelmSecurityPostureLogger(JhelmSecurityPolicy policy) {
		return (args) -> {
			if (policy.mode() == JhelmAccessMode.FULL) {
				log.info(
						"jhelm security: FULL — mutating operations enabled (local kubeconfig is the trust boundary).");
			}
			else {
				log.info("jhelm security: READ_ONLY — mutating operations are disabled "
						+ "(set jhelm.security.mode=FULL to enable).");
			}
		};
	}

}
