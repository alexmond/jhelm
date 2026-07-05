package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm list}, printing the releases in a namespace with their
 * revision, status, and chart. Supports {@code -o table|json|yaml} for machine-readable
 * output, matching {@code helm list -o}.
 */
@Component
@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "List releases")
@Slf4j
public class ListCommand implements Callable<Integer> {

	private final ListAction listAction;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table, json, or yaml")
	private String output;

	/**
	 * Creates the command.
	 * @param listAction the action that lists releases
	 */
	public ListCommand(ListAction listAction) {
		this.listAction = listAction;
	}

	private static String colorizeStatus(String status) {
		if (status == null) {
			return "";
		}
		return switch (status.toLowerCase(Locale.ROOT)) {
			case "deployed" -> CliOutput.success(status);
			case "failed" -> CliOutput.error(status);
			case "pending-install", "pending-upgrade", "pending-rollback" -> CliOutput.warn(status);
			default -> status;
		};
	}

	@Override
	public Integer call() {
		try {
			List<Release> releases = listAction.list(namespace);
			switch (output.toLowerCase(Locale.ROOT)) {
				case "json" -> System.out.println(OutputFormat.json(toRows(releases)));
				case "yaml" -> System.out.print(OutputFormat.yaml(toRows(releases)));
				default -> printTable(releases);
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error listing releases: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private void printTable(List<Release> releases) {
		CliOutput.printf("%-20s %-10s %-10s %-30s\n", CliOutput.bold("NAME"), CliOutput.bold("REVISION"),
				CliOutput.bold("STATUS"), CliOutput.bold("CHART"));
		for (Release r : releases) {
			String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
			String status = colorizeStatus(r.getInfo().getStatus().getValue());
			CliOutput.printf("%-20s %-10d %-10s %-30s\n", r.getName(), r.getVersion(), status, chartInfo);
		}
	}

	// Mirrors the fields Helm emits for `helm list -o json/yaml` (snake_case keys).
	private List<Map<String, Object>> toRows(List<Release> releases) {
		return releases.stream().map(OutputFormat::listRow).toList();
	}

}
