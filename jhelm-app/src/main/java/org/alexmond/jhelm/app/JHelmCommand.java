package org.alexmond.jhelm.app;

import org.alexmond.jhelm.app.command.CreateCommand;
import org.alexmond.jhelm.app.command.DependencyCommand;
import org.alexmond.jhelm.app.command.GetCommand;
import org.alexmond.jhelm.app.command.HistoryCommand;
import org.alexmond.jhelm.app.command.InstallCommand;
import org.alexmond.jhelm.app.command.ListCommand;
import org.alexmond.jhelm.app.command.PullCommand;
import org.alexmond.jhelm.app.command.PushCommand;
import org.alexmond.jhelm.app.command.RegistryCommand;
import org.alexmond.jhelm.app.command.RepoCommand;
import org.alexmond.jhelm.app.command.RollbackCommand;
import org.alexmond.jhelm.app.command.ShowCommand;
import org.alexmond.jhelm.app.command.StatusCommand;
import org.alexmond.jhelm.app.command.TemplateCommand;
import org.alexmond.jhelm.app.command.UninstallCommand;
import org.alexmond.jhelm.app.command.UpgradeCommand;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "jhelm", mixinStandardHelpOptions = true, version = "jhelm 0.0.1",
		description = "A Spring Boot based implementation of Helm-like functionality in Java.",
		subcommands = { CreateCommand.class, RepoCommand.class, RegistryCommand.class, TemplateCommand.class,
				InstallCommand.class, UpgradeCommand.class, UninstallCommand.class, ListCommand.class,
				HistoryCommand.class, StatusCommand.class, RollbackCommand.class, ShowCommand.class, GetCommand.class,
				DependencyCommand.class, PullCommand.class, PushCommand.class })
public class JHelmCommand implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

}
