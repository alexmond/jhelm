package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

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

	public PullCommand(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	@Override
	public void run() {
		try {
			if (chart.startsWith("oci://")) {
				String fileName = deriveOciFileName(chart);
				repoManager.pullFromUrl(chart, dest, fileName);
			}
			else {
				String resolvedVersion = resolveVersion();
				if (resolvedVersion == null) {
					log.error("--version is required for repository chart pulls");
					return;
				}
				repoManager.pull(chart, null, resolvedVersion, dest);
			}
			log.info("Chart pulled to {}", dest);
		}
		catch (Exception ex) {
			log.error("Error pulling chart: {}", ex.getMessage());
		}
	}

	private String resolveVersion() {
		if (version != null) {
			return version;
		}
		// Support repo/chart:version syntax
		if (chart.contains(":")) {
			int colon = chart.lastIndexOf(':');
			String v = chart.substring(colon + 1);
			chart = chart.substring(0, colon);
			return v;
		}
		return null;
	}

	private String deriveOciFileName(String ociUrl) {
		String raw = ociUrl.substring(6);
		String[] parts = raw.split("/");
		String last = parts[parts.length - 1];
		String name = last.contains(":") ? last.substring(0, last.indexOf(':')) : last;
		String chartTag = last.contains(":") ? last.substring(last.indexOf(':') + 1) : "latest";
		return name + "-" + chartTag + ".tgz";
	}

}
