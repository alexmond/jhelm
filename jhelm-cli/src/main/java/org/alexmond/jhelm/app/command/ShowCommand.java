package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ShowAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm show}, displaying information about a chart via the
 * {@code chart}, {@code values}, {@code readme}, {@code crds}, and {@code all}
 * subcommands.
 */
@Component
@CommandLine.Command(name = "show", mixinStandardHelpOptions = true, description = "Show information about a chart",
		subcommands = { ShowCommand.ChartCommand.class, ShowCommand.ValuesCommand.class,
				ShowCommand.ReadmeCommand.class, ShowCommand.CrdsCommand.class, ShowCommand.AllCommand.class })
@Slf4j
public class ShowCommand implements Callable<Integer> {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public ShowCommand() {
	}

	/**
	 * Prints the usage help when {@code show} is invoked without a subcommand.
	 */
	@Override
	public Integer call() {
		CommandLine.usage(this, System.out);
		return CommandLine.ExitCode.OK;
	}

	/** Implements {@code show chart}: prints the chart's Chart.yaml. */
	@Component
	@CommandLine.Command(name = "chart", mixinStandardHelpOptions = true, description = "Show the chart's Chart.yaml")
	@Slf4j
	public static class ChartCommand implements Callable<Integer> {

		private final ShowAction showAction;

		@CommandLine.Parameters(index = "0", description = "chart path")
		String chartPath;

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 */
		public ChartCommand(ShowAction showAction) {
			this.showAction = showAction;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showChart(chartPath));
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

		@CommandLine.Parameters(index = "0", description = "chart path")
		String chartPath;

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 */
		public ValuesCommand(ShowAction showAction) {
			this.showAction = showAction;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showValues(chartPath));
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

		@CommandLine.Parameters(index = "0", description = "chart path")
		String chartPath;

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 */
		public ReadmeCommand(ShowAction showAction) {
			this.showAction = showAction;
		}

		@Override
		public Integer call() {
			try {
				String readme = showAction.showReadme(chartPath);
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

		@CommandLine.Parameters(index = "0", description = "chart path")
		String chartPath;

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 */
		public CrdsCommand(ShowAction showAction) {
			this.showAction = showAction;
		}

		@Override
		public Integer call() {
			try {
				String crds = showAction.showCrds(chartPath);
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

		@CommandLine.Parameters(index = "0", description = "chart path")
		String chartPath;

		/**
		 * Creates the command.
		 * @param showAction the action that reads chart contents
		 */
		public AllCommand(ShowAction showAction) {
			this.showAction = showAction;
		}

		@Override
		public Integer call() {
			try {
				System.out.println(showAction.showAll(chartPath));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing all: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

}
