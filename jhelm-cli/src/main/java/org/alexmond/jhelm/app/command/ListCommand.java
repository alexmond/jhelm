package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.util.ReleaseFilters;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

	@CommandLine.Option(names = { "-l", "--selector" },
			description = "filter by a label selector (key=value or key!=value, comma-separated, all ANDed)")
	private String selector;

	@CommandLine.Option(names = { "--filter" },
			description = "filter releases by a regular expression matched against the name")
	private String filter;

	@CommandLine.Option(names = { "--offset" }, defaultValue = "0",
			description = "next release index in the list to start from")
	private int offset;

	@CommandLine.Option(names = { "-m", "--max" }, defaultValue = "256",
			description = "maximum number of releases to fetch (0 = no limit)")
	private int max;

	@CommandLine.Option(names = { "-a", "--all" },
			description = "show all releases regardless of status, including uninstalled and superseded")
	private boolean all;

	@CommandLine.Option(names = { "--deployed" }, description = "show deployed releases")
	private boolean deployed;

	@CommandLine.Option(names = { "--failed" }, description = "show failed releases")
	private boolean failed;

	@CommandLine.Option(names = { "--pending" }, description = "show pending releases")
	private boolean pending;

	@CommandLine.Option(names = { "--uninstalled" }, description = "show uninstalled releases")
	private boolean uninstalled;

	@CommandLine.Option(names = { "--uninstalling" },
			description = "show releases that are currently being uninstalled")
	private boolean uninstalling;

	@CommandLine.Option(names = { "--superseded" }, description = "show superseded releases")
	private boolean superseded;

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
			List<Release> byStatus = ReleaseFilters.retainStatuses(listAction.list(namespace), resolveStatuses());
			List<Release> releases = ReleaseFilters.apply(byStatus, selector, filter, offset, max);
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

	// Resolves the statuses to include: null = every status (--all); otherwise the
	// flagged
	// statuses, or a default mask (all except uninstalled/superseded) when no status flag
	// is set.
	private Set<ReleaseStatus> resolveStatuses() {
		if (all) {
			return null;
		}
		EnumSet<ReleaseStatus> set = EnumSet.noneOf(ReleaseStatus.class);
		if (deployed) {
			set.add(ReleaseStatus.DEPLOYED);
		}
		if (failed) {
			set.add(ReleaseStatus.FAILED);
		}
		if (pending) {
			set.add(ReleaseStatus.PENDING_INSTALL);
			set.add(ReleaseStatus.PENDING_UPGRADE);
			set.add(ReleaseStatus.PENDING_ROLLBACK);
		}
		if (uninstalled) {
			set.add(ReleaseStatus.UNINSTALLED);
		}
		if (uninstalling) {
			set.add(ReleaseStatus.UNINSTALLING);
		}
		if (superseded) {
			set.add(ReleaseStatus.SUPERSEDED);
		}
		if (set.isEmpty()) {
			set = EnumSet.allOf(ReleaseStatus.class);
			set.remove(ReleaseStatus.UNINSTALLED);
			set.remove(ReleaseStatus.SUPERSEDED);
		}
		return set;
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
