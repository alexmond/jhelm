package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.model.Release;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Component
@CommandLine.Command(name = "history", mixinStandardHelpOptions = true, description = "Fetch release history")
@Slf4j
public class HistoryCommand implements Runnable {

	private final HistoryAction historyAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	public HistoryCommand(HistoryAction historyAction) {
		this.historyAction = historyAction;
	}

	@Override
	public void run() {
		try {
			List<Release> history = historyAction.history(name, namespace);
			CliOutput.printf("%-10s %-30s %-10s %-20s %-30s\n", CliOutput.bold("REVISION"), CliOutput.bold("UPDATED"),
					CliOutput.bold("STATUS"), CliOutput.bold("CHART"), CliOutput.bold("DESCRIPTION"));
			for (Release r : history) {
				String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
				CliOutput.printf("%-10d %-30s %-10s %-20s %-30s\n", r.getVersion(), r.getInfo().getLastDeployed(),
						r.getInfo().getStatus(), chartInfo, r.getInfo().getDescription());
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error fetching history: " + ex.getMessage()));
		}
	}

}
