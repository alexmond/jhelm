package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implements {@code jhelm create NAME}, scaffolding a new chart directory with the given
 * name (optionally from a starter scaffold).
 */
@Slf4j
@Component
@CommandLine.Command(name = "create", mixinStandardHelpOptions = true,
		description = "Create a new chart with the given name")
public class CreateCommand implements Runnable {

	private final CreateAction createAction;

	@CommandLine.Parameters(index = "0", description = "The chart name")
	private String name;

	@CommandLine.Option(names = { "-p", "--starter" },
			description = "The name or absolute path to Helm starter scaffold")
	private String starter;

	/**
	 * Creates the command.
	 * @param createAction the action that scaffolds the new chart
	 */
	public CreateCommand(CreateAction createAction) {
		this.createAction = createAction;
	}

	/**
	 * Scaffolds the new chart and reports the result.
	 */
	@Override
	public void run() {
		try {
			Path chartPath = Paths.get(name);
			createAction.create(chartPath);
			CliOutput.println(CliOutput.success("Creating " + name));
		}
		catch (JhelmException ex) {
			CliOutput.errPrintln(CliOutput.error("Error creating chart: " + ex.getMessage()));
			System.exit(1);
		}
	}

}
