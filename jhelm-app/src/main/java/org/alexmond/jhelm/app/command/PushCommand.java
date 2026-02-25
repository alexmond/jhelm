package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "push", description = "push a chart to a remote OCI registry")
@Slf4j
public class PushCommand implements Runnable {

	private final RepoManager repoManager;

	@CommandLine.Parameters(index = "0", description = "path to chart archive (.tgz)")
	private String chart;

	@CommandLine.Parameters(index = "1", description = "OCI registry destination (oci://registry/repo/chart[:tag])")
	private String remote;

	public PushCommand(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	@Override
	public void run() {
		try {
			repoManager.pushOci(chart, remote);
			log.info("Pushed: {}", remote);
		}
		catch (Exception ex) {
			log.error("Error pushing chart: {}", ex.getMessage());
		}
	}

}
