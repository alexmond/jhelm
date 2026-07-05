package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.app.output.OutputFormat;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.model.Release;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm history RELEASE}, printing the revision history of a release.
 */
@Component
@CommandLine.Command(name = "history", mixinStandardHelpOptions = true, description = "Fetch release history")
@Slf4j
public class HistoryCommand implements Callable<Integer> {

	private final HistoryAction historyAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--max" }, defaultValue = "256",
			description = "maximum number of revisions to include (most recent first)")
	private int max;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table, json, or yaml")
	private String output;

	/**
	 * Creates the command.
	 * @param historyAction the action that retrieves release history
	 */
	public HistoryCommand(HistoryAction historyAction) {
		this.historyAction = historyAction;
	}

	/**
	 * Prints the release revision history as a table, or as {@code json}/{@code yaml}.
	 */
	@Override
	public Integer call() {
		try {
			List<Release> history = historyAction.history(name, namespace);
			if (max > 0 && history.size() > max) {
				history = history.subList(history.size() - max, history.size());
			}
			switch (output.toLowerCase(Locale.ROOT)) {
				case "json" -> System.out.println(OutputFormat.json(toRows(history)));
				case "yaml" -> System.out.print(OutputFormat.yaml(toRows(history)));
				default -> printTable(history);
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error fetching history: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private void printTable(List<Release> history) {
		CliOutput.printf("%-10s %-30s %-10s %-20s %-30s\n", CliOutput.bold("REVISION"), CliOutput.bold("UPDATED"),
				CliOutput.bold("STATUS"), CliOutput.bold("CHART"), CliOutput.bold("DESCRIPTION"));
		for (Release r : history) {
			String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
			CliOutput.printf("%-10d %-30s %-10s %-20s %-30s\n", r.getVersion(), r.getInfo().getLastDeployed(),
					r.getInfo().getStatus().getValue(), chartInfo, r.getInfo().getDescription());
		}
	}

	private static List<Map<String, Object>> toRows(List<Release> history) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Release r : history) {
			rows.add(OutputFormat.historyRow(r));
		}
		return rows;
	}

}
