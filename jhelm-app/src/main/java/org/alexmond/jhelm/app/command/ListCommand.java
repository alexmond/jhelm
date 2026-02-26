package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.model.Release;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Locale;

@Component
@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "List releases")
@Slf4j
public class ListCommand implements Runnable {

	private final ListAction listAction;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

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
	public void run() {
		try {
			List<Release> releases = listAction.list(namespace);
			CliOutput.printf("%-20s %-10s %-10s %-30s\n", CliOutput.bold("NAME"), CliOutput.bold("REVISION"),
					CliOutput.bold("STATUS"), CliOutput.bold("CHART"));
			for (Release r : releases) {
				String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
				String status = colorizeStatus(r.getInfo().getStatus());
				CliOutput.printf("%-20s %-10d %-10s %-30s\n", r.getName(), r.getVersion(), status, chartInfo);
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error listing releases: " + ex.getMessage()));
		}
	}

}
