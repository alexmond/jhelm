package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.RepoManager.ChartMatch;
import org.alexmond.jhelm.core.util.ChartVersions;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm outdated CHART}, reporting the latest version of a chart across
 * every configured repository and — when a {@code --current} version is supplied —
 * whether an upgrade is available.
 *
 * <p>
 * This is a repository-only command: it consults {@link RepoManager} and never needs a
 * Kubernetes cluster. Version precedence uses
 * {@link ChartVersions#compare(String, String)} so the "upgrade available?" decision
 * matches how jhelm selects the latest chart.
 */
@Component
@CommandLine.Command(name = "outdated", mixinStandardHelpOptions = true,
		description = "Report the latest available version of a chart across the configured repositories, "
				+ "and whether an upgrade is available")
@Slf4j
public class OutdatedCommand implements Callable<Integer> {

	private final RepoManager repoManager;

	@CommandLine.Parameters(index = "0", description = "chart name (short name, without a repo/ prefix)")
	private String chart;

	@CommandLine.Option(names = { "-c", "--current" },
			description = "current chart version to check for an available upgrade")
	private String current;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table, json, or yaml")
	private String output;

	/**
	 * Creates the command.
	 * @param repoManager the repository manager that resolves cross-repo chart versions
	 */
	public OutdatedCommand(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	@Override
	public Integer call() {
		try {
			Optional<ChartMatch> latest = repoManager.latestOf(chart);
			ChartMatch match = latest.orElse(null);
			switch (output.toLowerCase(Locale.ROOT)) {
				case "json" -> System.out.println(OutputFormat.json(toRow(match)));
				case "yaml" -> System.out.print(OutputFormat.yaml(toRow(match)));
				default -> {
					if (match == null) {
						CliOutput.println(CliOutput.warn("No versions of \"" + chart
								+ "\" found in the configured repositories. Make sure a repo containing it is added "
								+ "(jhelm repo add) and updated (jhelm repo update)."));
					}
					else {
						printTable(match);
					}
				}
			}
			return CommandLine.ExitCode.OK;
		}
		catch (IOException ex) {
			CliOutput.errPrintln(CliOutput.error("Error looking up chart versions: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private void printTable(ChartMatch match) {
		String latestVersion = match.version().getChartVersion();
		CliOutput.printf("%-30s %-16s %-16s %s%n", CliOutput.bold("NAME"), CliOutput.bold("LATEST VERSION"),
				CliOutput.bold("APP VERSION"), CliOutput.bold("REPOSITORY"));
		CliOutput.printf("%-30s %-16s %-16s %s%n", chart, nv(latestVersion), nv(match.version().getAppVersion()),
				match.repoName());

		if (current != null && !current.isBlank()) {
			if (ChartVersions.compare(current, latestVersion) < 0) {
				CliOutput.println(CliOutput
					.success("Upgrade available: " + current + " -> " + latestVersion + " (" + match.repoName() + ")"));
			}
			else {
				CliOutput.println(CliOutput.info("\"" + chart + "\" is up to date (" + current + ")"));
			}
		}
	}

	// Mirrors the fields the table shows, with an upgrade verdict when --current is set.
	private Map<String, Object> toRow(ChartMatch match) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", chart);
		if (match == null) {
			row.put("found", false);
			return row;
		}
		String latestVersion = match.version().getChartVersion();
		row.put("found", true);
		row.put("latest_version", latestVersion);
		row.put("app_version", match.version().getAppVersion());
		row.put("repository", match.repoName());
		if (current != null && !current.isBlank()) {
			row.put("current_version", current);
			row.put("upgrade_available", ChartVersions.compare(current, latestVersion) < 0);
		}
		return row;
	}

	private String nv(String s) {
		return (s != null) ? s : "";
	}

}
