package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.action.StatusAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@CommandLine.Command(name = "status", mixinStandardHelpOptions = true,
		description = "Display the status of the named release")
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
	public void run() {
		try {
			Optional<Release> releaseOpt = statusAction.status(name, namespace);
			if (releaseOpt.isEmpty()) {
				CliOutput.errPrintln(CliOutput.error("Error: release not found: " + name));
				return;
			}

			Release r = releaseOpt.get();
			CliOutput.println(CliOutput.bold("NAME:") + " " + r.getName());
			CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + r.getInfo().getLastDeployed());
			CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + r.getNamespace());
			CliOutput.println(CliOutput.bold("STATUS:") + " " + colorizeStatus(r.getInfo().getStatus()));
			CliOutput.println(CliOutput.bold("REVISION:") + " " + r.getVersion());

			if (showResources) {
				List<ResourceStatus> statuses = statusAction.getResourceStatuses(r);
				if (statuses.isEmpty()) {
					CliOutput.println("\n" + CliOutput.bold("RESOURCES:") + "\n  (none)");
				}
				else {
					CliOutput.println("\n" + CliOutput.bold("RESOURCES:"));
					for (ResourceStatus rs : statuses) {
						String readyMark = rs.isReady() ? CliOutput.success("\u2713") : CliOutput.error("\u2717");
						CliOutput.println(
								"  " + readyMark + " " + rs.getKind() + "/" + rs.getName() + ": " + rs.getMessage());
					}
				}
			}

			CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + r.getManifest());
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error fetching status: " + ex.getMessage()));
		}
	}

}
