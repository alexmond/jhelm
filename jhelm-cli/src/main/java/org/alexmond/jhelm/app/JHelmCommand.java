package org.alexmond.jhelm.app;

import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.command.CreateCommand;
import org.alexmond.jhelm.app.command.DependencyCommand;
import org.alexmond.jhelm.app.command.GetCommand;
import org.alexmond.jhelm.app.command.HistoryCommand;
import org.alexmond.jhelm.app.command.InstallCommand;
import org.alexmond.jhelm.app.command.LintCommand;
import org.alexmond.jhelm.app.command.ListCommand;
import org.alexmond.jhelm.app.command.PackageCommand;
import org.alexmond.jhelm.app.command.PluginCommand;
import org.alexmond.jhelm.app.command.PullCommand;
import org.alexmond.jhelm.app.command.PushCommand;
import org.alexmond.jhelm.app.command.RegistryCommand;
import org.alexmond.jhelm.app.command.RepoCommand;
import org.alexmond.jhelm.app.command.RollbackCommand;
import org.alexmond.jhelm.app.command.SearchCommand;
import org.alexmond.jhelm.app.command.ShowCommand;
import org.alexmond.jhelm.app.command.StatusCommand;
import org.alexmond.jhelm.app.command.TemplateCommand;
import org.alexmond.jhelm.app.command.TestCommand;
import org.alexmond.jhelm.app.command.EnvCommand;
import org.alexmond.jhelm.app.command.UninstallCommand;
import org.alexmond.jhelm.app.command.VersionCommand;
import org.alexmond.jhelm.app.command.UpgradeCommand;
import org.alexmond.jhelm.app.command.VerifyCommand;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.springframework.boot.ResourceBanner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Root {@code jhelm} command. Defines global options and wires up every top-level
 * subcommand (install, upgrade, template, repo, and so on). Running it with no subcommand
 * prints the usage help.
 */
@Slf4j
@Component
@CommandLine.Command(name = "jhelm", mixinStandardHelpOptions = true, version = "jhelm 0.0.1",
		header = { "", "The Java Kubernetes package manager", "" },
		description = { "%nCommon actions for jhelm:%n", "  - jhelm search:    search for charts in repositories",
				"  - jhelm pull:      download a chart to your local directory",
				"  - jhelm install:   install a chart into a Kubernetes cluster",
				"  - jhelm list:      list releases of charts", "" },
		synopsisHeading = "%nUsage: ", commandListHeading = "%nAvailable Commands:%n", optionListHeading = "%nFlags:%n",
		footer = { "", "Use \"jhelm [command] --help\" for more information about a command." },
		subcommands = { CreateCommand.class, TemplateCommand.class, InstallCommand.class, UpgradeCommand.class,
				UninstallCommand.class, ListCommand.class, StatusCommand.class, HistoryCommand.class,
				RollbackCommand.class, ShowCommand.class, GetCommand.class, TestCommand.class, DependencyCommand.class,
				PullCommand.class, PushCommand.class, PackageCommand.class, VerifyCommand.class, LintCommand.class,
				RepoCommand.class, RegistryCommand.class, PluginCommand.class, SearchCommand.class,
				VersionCommand.class, EnvCommand.class })
public class JHelmCommand implements Callable<Integer> {

	private final Environment environment;

	/**
	 * Creates the command.
	 * @param environment the Spring environment, used to resolve banner placeholders
	 */
	public JHelmCommand(Environment environment) {
		this.environment = environment;
	}

	/**
	 * Toggles colored CLI output. Bound to the inherited {@code --no-color} option so it
	 * applies to every subcommand.
	 * @param noColor {@code true} to disable ANSI colors in output
	 */
	@CommandLine.Option(names = { "--no-color" }, description = "Disable colored output",
			scope = CommandLine.ScopeType.INHERIT)
	public void setNoColor(boolean noColor) {
		if (noColor) {
			CliOutput.setEnabled(false);
		}
	}

	/**
	 * Prints the jhelm banner (to stderr, so piped stdout stays clean) followed by the
	 * usage help when {@code jhelm} is invoked without a subcommand.
	 */
	@Override
	public Integer call() {
		printBanner();
		CommandLine.usage(this, System.out);
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Prints the {@code banner.txt} brand banner to stderr. Spring's automatic stdout
	 * banner is disabled (see {@code application.properties}); this keeps the banner out
	 * of command output while still branding the bare {@code jhelm} invocation. The
	 * banner is purely cosmetic, so any failure is swallowed rather than aborting.
	 */
	private void printBanner() {
		try {
			new ResourceBanner(new ClassPathResource("banner.txt")).printBanner(this.environment, JHelmCommand.class,
					System.err);
		}
		catch (RuntimeException ex) {
			// Banner is cosmetic; never let it break the command.
			log.debug("Skipping banner: {}", ex.getMessage());
		}
	}

}
