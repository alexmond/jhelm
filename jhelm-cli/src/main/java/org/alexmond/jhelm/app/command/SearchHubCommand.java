package org.alexmond.jhelm.app.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm search hub KEYWORD}, searching Artifact Hub for charts matching
 * a keyword and printing the results as a table.
 */
@Component
@CommandLine.Command(name = "hub", mixinStandardHelpOptions = true, description = "Search for charts in Artifact Hub")
public class SearchHubCommand implements Callable<Integer> {

	private final SearchHubAction searchHubAction;

	@CommandLine.Parameters(index = "0", description = "search keyword")
	private String keyword;

	@CommandLine.Option(names = { "--max-col-width" }, defaultValue = "50",
			description = "maximum column width for output table")
	private int maxColWidth;

	@CommandLine.Option(names = { "--list-repo-url" }, defaultValue = "false",
			description = "print charts repository URL")
	private boolean listRepoUrl;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table, json, or yaml")
	private String output;

	/**
	 * Creates the command.
	 * @param searchHubAction the action that queries Artifact Hub
	 */
	public SearchHubCommand(SearchHubAction searchHubAction) {
		this.searchHubAction = searchHubAction;
	}

	@Override
	public Integer call() {
		try {
			List<SearchHubAction.HubResult> results = searchHubAction.search(keyword, 25);
			switch (output.toLowerCase(Locale.ROOT)) {
				case "json" -> System.out.println(OutputFormat.json(toRows(results)));
				case "yaml" -> System.out.print(OutputFormat.yaml(toRows(results)));
				default -> {
					if (results.isEmpty()) {
						CliOutput.println("No results found for \"" + keyword + "\"");
					}
					else {
						printTable(results);
					}
				}
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	private void printTable(List<SearchHubAction.HubResult> results) {
		CliOutput.println(String.format("%-40s\t%-8s\t%-12s\t%s", "URL", "VERSION", "APP VERSION", "DESCRIPTION"));
		for (SearchHubAction.HubResult r : results) {
			String description = truncate(r.getDescription(), maxColWidth);
			// --list-repo-url prints the chart's repository URL; the default prints the
			// Artifact Hub package URL (matching `helm search hub [--list-repo-url]`).
			String url = listRepoUrl ? r.getRepoUrl() : r.getUrl();
			CliOutput
				.println(String.format("%-40s\t%-8s\t%-12s\t%s", url, r.getVersion(), r.getAppVersion(), description));
		}
	}

	// Mirrors the fields Helm emits for `helm search hub -o json/yaml`.
	private static List<Map<String, Object>> toRows(List<SearchHubAction.HubResult> results) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (SearchHubAction.HubResult r : results) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("url", r.getUrl());
			row.put("version", r.getVersion());
			row.put("app_version", r.getAppVersion());
			row.put("description", r.getDescription());
			row.put("repository", r.getRepoUrl());
			rows.add(row);
		}
		return rows;
	}

	private String truncate(String value, int maxWidth) {
		if (value == null) {
			return "";
		}
		if (value.length() <= maxWidth) {
			return value;
		}
		return value.substring(0, maxWidth - 3) + "...";
	}

}
