package org.alexmond.jhelm.app.command;

import java.util.List;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "hub", mixinStandardHelpOptions = true, description = "Search for charts in Artifact Hub")
public class SearchHubCommand implements Runnable {

	private final SearchHubAction searchHubAction;

	@CommandLine.Parameters(index = "0", description = "search keyword")
	private String keyword;

	@CommandLine.Option(names = { "--max-col-width" }, defaultValue = "50",
			description = "maximum column width for output table")
	private int maxColWidth;

	@CommandLine.Option(names = { "--list-repo-url" }, defaultValue = "false",
			description = "print charts repository URL")
	private boolean listRepoUrl;

	public SearchHubCommand(SearchHubAction searchHubAction) {
		this.searchHubAction = searchHubAction;
	}

	@Override
	public void run() {
		try {
			List<SearchHubAction.HubResult> results = searchHubAction.search(keyword, 25);
			if (results.isEmpty()) {
				CliOutput.println("No results found for \"" + keyword + "\"");
				return;
			}
			printTable(results);
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error: " + ex.getMessage()));
		}
	}

	private void printTable(List<SearchHubAction.HubResult> results) {
		if (listRepoUrl) {
			CliOutput.println(String.format("%-40s\t%-8s\t%-12s\t%s", "URL", "VERSION", "APP VERSION", "DESCRIPTION"));
		}
		else {
			CliOutput.println(String.format("%-40s\t%-8s\t%-12s\t%s", "URL", "VERSION", "APP VERSION", "DESCRIPTION"));
		}
		for (SearchHubAction.HubResult r : results) {
			String description = truncate(r.getDescription(), maxColWidth);
			if (listRepoUrl) {
				CliOutput.println(String.format("%-40s\t%-8s\t%-12s\t%s", r.getUrl(), r.getVersion(), r.getAppVersion(),
						description));
			}
			else {
				CliOutput.println(String.format("%-40s\t%-8s\t%-12s\t%s", r.getUrl(), r.getVersion(), r.getAppVersion(),
						description));
			}
		}
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
