package org.alexmond.jhelm.app.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "search", mixinStandardHelpOptions = true, description = "Search for a keyword in charts",
		subcommands = { SearchHubCommand.class })
public class SearchCommand implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

}
