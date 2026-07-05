package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.action.StatusAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm status RELEASE}, displaying the status of a named release and
 * optionally the readiness of its resources.
 */
@Component
@CommandLine.Command(name = "status", mixinStandardHelpOptions = true,
		description = "Display the status of the named release")
@Slf4j
public class StatusCommand implements Callable<Integer> {

	private final StatusAction statusAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--show-resources" }, description = "show resource readiness status")
	private boolean showResources;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table, json, or yaml")
	private String output;

	/**
	 * Creates the command.
	 * @param statusAction the action that retrieves release status
	 */
	public StatusCommand(StatusAction statusAction) {
		this.statusAction = statusAction;
	}

	private static List<Map<String, Object>> resourceRows(List<ResourceStatus> statuses) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (ResourceStatus rs : statuses) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("kind", rs.getKind());
			row.put("name", rs.getName());
			row.put("ready", rs.isReady());
			row.put("message", rs.getMessage());
			rows.add(row);
		}
		return rows;
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
			Optional<Release> releaseOpt = statusAction.status(name, namespace);
			if (releaseOpt.isEmpty()) {
				CliOutput.errPrintln(CliOutput.error("Error: release not found: " + name));
				return CommandLine.ExitCode.SOFTWARE;
			}

			Release r = releaseOpt.get();
			if (OutputFormat.isJson(output) || OutputFormat.isYaml(output)) {
				Map<String, Object> map = OutputFormat.release(r);
				if (showResources) {
					map.put("resources", resourceRows(statusAction.getResourceStatuses(r)));
				}
				if (OutputFormat.isJson(output)) {
					System.out.println(OutputFormat.json(map));
				}
				else {
					System.out.print(OutputFormat.yaml(map));
				}
				return CommandLine.ExitCode.OK;
			}

			CliOutput.println(CliOutput.bold("NAME:") + " " + r.getName());
			CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + r.getInfo().getLastDeployed());
			CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + r.getNamespace());
			CliOutput.println(CliOutput.bold("STATUS:") + " " + colorizeStatus(r.getInfo().getStatus().getValue()));
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
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error fetching status: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
