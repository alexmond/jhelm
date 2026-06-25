package org.alexmond.jhelm.app.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm search}, the parent command for chart search. Delegates to the
 * {@code hub} subcommand to search Artifact Hub.
 */
@Component
@CommandLine.Command(name = "search", mixinStandardHelpOptions = true, description = "Search for a keyword in charts",
		subcommands = { SearchHubCommand.class })
public class SearchCommand implements Runnable {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public SearchCommand() {
	}

	/**
	 * Prints the usage help when {@code search} is invoked without a subcommand.
	 */
	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

}
