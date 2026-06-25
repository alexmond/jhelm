package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ShowAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

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
public class ShowCommand implements Runnable {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public ShowCommand() {
	}

	/**
	 * Prints the usage help when {@code show} is invoked without a subcommand.
	 */
	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	/** Implements {@code show chart}: prints the chart's Chart.yaml. */
	@Component
	@CommandLine.Command(name = "chart", mixinStandardHelpOptions = true, description = "Show the chart's Chart.yaml")
	@Slf4j
	public static class ChartCommand implements Runnable {

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
		public void run() {
			try {
				System.out.println(showAction.showChart(chartPath));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing chart: " + ex.getMessage()));
			}
		}

	}

	/** Implements {@code show values}: prints the chart's values.yaml. */
	@Component
	@CommandLine.Command(name = "values", mixinStandardHelpOptions = true, description = "Show the chart's values.yaml")
	@Slf4j
	public static class ValuesCommand implements Runnable {

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
		public void run() {
			try {
				System.out.println(showAction.showValues(chartPath));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing values: " + ex.getMessage()));
			}
		}

	}

	/** Implements {@code show readme}: prints the chart's README. */
	@Component
	@CommandLine.Command(name = "readme", mixinStandardHelpOptions = true, description = "Show the chart's README")
	@Slf4j
	public static class ReadmeCommand implements Runnable {

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
		public void run() {
			try {
				String readme = showAction.showReadme(chartPath);
				if (readme.isEmpty()) {
					System.out.println("No README found in chart");
					return;
				}
				System.out.println(readme);
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing readme: " + ex.getMessage()));
			}
		}

	}

	/** Implements {@code show crds}: prints the chart's Custom Resource Definitions. */
	@Component
	@CommandLine.Command(name = "crds", mixinStandardHelpOptions = true,
			description = "Show the chart's Custom Resource Definitions")
	@Slf4j
	public static class CrdsCommand implements Runnable {

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
		public void run() {
			try {
				String crds = showAction.showCrds(chartPath);
				if (crds.isEmpty()) {
					System.out.println("No CRDs found in chart");
					return;
				}
				System.out.println(crds);
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing crds: " + ex.getMessage()));
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
	public static class AllCommand implements Runnable {

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
		public void run() {
			try {
				System.out.println(showAction.showAll(chartPath));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error showing all: " + ex.getMessage()));
			}
		}

	}

}
