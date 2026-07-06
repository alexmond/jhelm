package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm show}, displaying information about a chart via the
 * {@code chart}, {@code values}, {@code readme}, {@code crds}, and {@code all}
 * subcommands. Each subcommand accepts a local chart path or, via the shared repo options
 * ({@code --version}, {@code --repo}, {@code --username}/{@code --password}, TLS flags),
 * a chart pulled from a repository without a prior {@code pull}.
 */
@Component
@CommandLine.Command(name = "show", mixinStandardHelpOptions = true, description = "Show information about a chart",
		subcommands = { ShowCommand.ChartCommand.class, ShowCommand.ValuesCommand.class,
				ShowCommand.ReadmeCommand.class, ShowCommand.CrdsCommand.class, ShowCommand.AllCommand.class })
@Slf4j
public class ShowCommand implements Callable<Integer> {

	@CommandLine.Spec
	private CommandLine.Model.CommandSpec spec;

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public ShowCommand() {
	}

	/**
	 * Prints the usage help when {@code show} is invoked without a subcommand. Renders
	 * via the already-built command line so the subcommands (which carry {@code @Mixin}
	 * options and have no no-arg constructor) are not re-instantiated by the default
	 * factory.
	 */
	@Override
	public Integer call() {
		if (spec != null) {
			spec.commandLine().usage(System.out);
		}
		return CommandLine.ExitCode.OK;
	}

	// Resolves the chart from a local path, a registered repo/chart ref, or an ad-hoc
	// --repo URL (with the mixin's credentials/TLS), reusing the install/upgrade
	// resolver.
	private static Chart resolve(ChartResolver chartResolver, String chartPath, RepoChartOptions repoOptions)
			throws IOException {
		return chartResolver.resolveFromRepo(chartPath, repoOptions.getVersion(), repoOptions.getRepo(),
				repoOptions.hasRepo() ? repoOptions.auth() : null, false, null, ValuesProfiles.none());
	}

	/** Implements {@code show chart}: prints the chart's Chart.yaml. */
	@Component
	@CommandLine.Command(name = "chart", mixinStandardHelpOptions = true, description = "Show the chart's Chart.yaml")
	@Slf4j
	public static class ChartCommand implements Callable<Integer> {

		private final ShowAction showAction;

		private final ChartResolver chartResolver;

		@CommandLine.Parameters(index = "0", description = "chart path or repo/chart reference")
		String chartPath;

		@CommandLine.Mixin
		private final RepoChartOptions repoOptions = new RepoChartOptions();

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 * @param chartResolver resolves the chart source (local path or repository)
		 */
		public ChartCommand(ShowAction showAction, ChartResolver chartResolver) {
			this.showAction = showAction;
			this.chartResolver = chartResolver;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showChart(resolve(chartResolver, chartPath, repoOptions)));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing chart: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code show values}: prints the chart's values.yaml. */
	@Component
	@CommandLine.Command(name = "values", mixinStandardHelpOptions = true, description = "Show the chart's values.yaml")
	@Slf4j
	public static class ValuesCommand implements Callable<Integer> {

		private final ShowAction showAction;

		private final ChartResolver chartResolver;

		@CommandLine.Parameters(index = "0", description = "chart path or repo/chart reference")
		String chartPath;

		@CommandLine.Mixin
		private final RepoChartOptions repoOptions = new RepoChartOptions();

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 * @param chartResolver resolves the chart source (local path or repository)
		 */
		public ValuesCommand(ShowAction showAction, ChartResolver chartResolver) {
			this.showAction = showAction;
			this.chartResolver = chartResolver;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showValues(resolve(chartResolver, chartPath, repoOptions)));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing values: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code show readme}: prints the chart's README. */
	@Component
	@CommandLine.Command(name = "readme", mixinStandardHelpOptions = true, description = "Show the chart's README")
	@Slf4j
	public static class ReadmeCommand implements Callable<Integer> {

		private final ShowAction showAction;

		private final ChartResolver chartResolver;

		@CommandLine.Parameters(index = "0", description = "chart path or repo/chart reference")
		String chartPath;

		@CommandLine.Mixin
		private final RepoChartOptions repoOptions = new RepoChartOptions();

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 * @param chartResolver resolves the chart source (local path or repository)
		 */
		public ReadmeCommand(ShowAction showAction, ChartResolver chartResolver) {
			this.showAction = showAction;
			this.chartResolver = chartResolver;
		}

		@Override
		public Integer call() {
			try {
				String readme = showAction.showReadme(resolve(chartResolver, chartPath, repoOptions));
				if (readme.isEmpty()) {
					System.out.println("No README found in chart");
					return CommandLine.ExitCode.OK;
				}
				System.out.println(readme);
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing readme: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code show crds}: prints the chart's Custom Resource Definitions. */
	@Component
	@CommandLine.Command(name = "crds", mixinStandardHelpOptions = true,
			description = "Show the chart's Custom Resource Definitions")
	@Slf4j
	public static class CrdsCommand implements Callable<Integer> {

		private final ShowAction showAction;

		private final ChartResolver chartResolver;

		@CommandLine.Parameters(index = "0", description = "chart path or repo/chart reference")
		String chartPath;

		@CommandLine.Mixin
		private final RepoChartOptions repoOptions = new RepoChartOptions();

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 * @param chartResolver resolves the chart source (local path or repository)
		 */
		public CrdsCommand(ShowAction showAction, ChartResolver chartResolver) {
			this.showAction = showAction;
			this.chartResolver = chartResolver;
		}

		@Override
		public Integer call() {
			try {
				String crds = showAction.showCrds(resolve(chartResolver, chartPath, repoOptions));
				if (crds.isEmpty()) {
					System.out.println("No CRDs found in chart");
					return CommandLine.ExitCode.OK;
				}
				System.out.println(crds);
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing crds: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/**
	 * Implements {@code show all}: prints the chart's combined Chart.yaml, values, and
	 * README.
	 */
	@Component
	@CommandLine.Command(name = "all", mixinStandardHelpOptions = true,
			description = "Show all information about the chart")
	@Slf4j
	public static class AllCommand implements Callable<Integer> {

		private final ShowAction showAction;

		private final ChartResolver chartResolver;

		@CommandLine.Parameters(index = "0", description = "chart path or repo/chart reference")
		String chartPath;

		@CommandLine.Mixin
		private final RepoChartOptions repoOptions = new RepoChartOptions();

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 * @param chartResolver resolves the chart source (local path or repository)
		 */
		public AllCommand(ShowAction showAction, ChartResolver chartResolver) {
			this.showAction = showAction;
			this.chartResolver = chartResolver;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showAll(resolve(chartResolver, chartPath, repoOptions)));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing all: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

}
