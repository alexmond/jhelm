package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm pull CHART}, downloading a chart from a repository or OCI
 * registry to a local directory.
 */
@Component
@CommandLine.Command(name = "pull", mixinStandardHelpOptions = true, description = "Download a chart from a repository")
@Slf4j
public class PullCommand implements Runnable {

	private final RepoManager repoManager;

	@CommandLine.Parameters(index = "0", description = "chart to pull: repo/chart, repo/chart:version, or oci://...")
	private String chart;

	@CommandLine.Option(names = { "--version" }, description = "chart version (required for repo charts)")
	private String version;

	@CommandLine.Option(names = { "--dest", "-d" }, defaultValue = ".", description = "destination directory")
	private String dest;

	/**
	 * Creates the command.
	 * @param repoManager the repository manager that downloads the chart
	 */
	public PullCommand(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	@Override
	public void run() {
		try {
			repoManager.pull(chart, version, dest);
			CliOutput.println(CliOutput.success("Chart pulled to " + dest));
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error pulling chart: " + ex.getMessage()));
		}
	}

}
