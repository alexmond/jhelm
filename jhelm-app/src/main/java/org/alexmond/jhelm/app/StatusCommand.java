package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.action.StatusAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Optional;

@Component
@CommandLine.Command(name = "status", description = "display the status of the named release")
@Slf4j
public class StatusCommand implements Runnable {

	private final StatusAction statusAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--show-resources" }, description = "show resource readiness status")
	private boolean showResources;

	public StatusCommand(StatusAction statusAction) {
		this.statusAction = statusAction;
	}

	@Override
	public void run() {
		try {
			Optional<Release> releaseOpt = statusAction.status(name, namespace);
			if (releaseOpt.isEmpty()) {
				log.error("Error: release not found: {}", name);
				return;
			}

			Release r = releaseOpt.get();
			log.info("NAME: {}", r.getName());
			log.info("LAST DEPLOYED: {}", r.getInfo().getLastDeployed());
			log.info("NAMESPACE: {}", r.getNamespace());
			log.info("STATUS: {}", r.getInfo().getStatus());
			log.info("REVISION: {}", r.getVersion());

			if (showResources) {
				List<ResourceStatus> statuses = statusAction.getResourceStatuses(r);
				if (statuses.isEmpty()) {
					log.info("\nRESOURCES:\n  (none)");
				}
				else {
					log.info("\nRESOURCES:");
					for (ResourceStatus rs : statuses) {
						String readyMark = rs.isReady() ? "✓" : "✗";
						log.info("  {} {}/{}: {}", readyMark, rs.getKind(), rs.getName(), rs.getMessage());
					}
				}
			}

			log.info("\nMANIFEST:\n{}", r.getManifest());
		}
		catch (Exception ex) {
			log.error("Error fetching status: {}", ex.getMessage());
		}
	}

}
