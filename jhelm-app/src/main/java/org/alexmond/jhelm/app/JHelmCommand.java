package org.alexmond.jhelm.app;

import org.alexmond.jhelm.app.command.CreateCommand;
import org.alexmond.jhelm.app.command.DependencyCommand;
import org.alexmond.jhelm.app.command.GetCommand;
import org.alexmond.jhelm.app.command.HistoryCommand;
import org.alexmond.jhelm.app.command.InstallCommand;
import org.alexmond.jhelm.app.command.LintCommand;
import org.alexmond.jhelm.app.command.ListCommand;
import org.alexmond.jhelm.app.command.PackageCommand;
import org.alexmond.jhelm.app.command.PluginCommand;
import org.alexmond.jhelm.app.command.PullCommand;
import org.alexmond.jhelm.app.command.PushCommand;
import org.alexmond.jhelm.app.command.RegistryCommand;
import org.alexmond.jhelm.app.command.RepoCommand;
import org.alexmond.jhelm.app.command.RollbackCommand;
import org.alexmond.jhelm.app.command.ShowCommand;
import org.alexmond.jhelm.app.command.StatusCommand;
import org.alexmond.jhelm.app.command.TemplateCommand;
import org.alexmond.jhelm.app.command.TestCommand;
import org.alexmond.jhelm.app.command.UninstallCommand;
import org.alexmond.jhelm.app.command.UpgradeCommand;
import org.alexmond.jhelm.app.command.VerifyCommand;
import org.alexmond.jhelm.app.output.CliOutput;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "jhelm", mixinStandardHelpOptions = true, version = "jhelm 0.0.1",
		header = { "", "The Java Kubernetes package manager", "" },
		description = { "%nCommon actions for jhelm:%n", "  - jhelm search:    search for charts in repositories",
				"  - jhelm pull:      download a chart to your local directory",
				"  - jhelm install:   install a chart into a Kubernetes cluster",
				"  - jhelm list:      list releases of charts", "" },
		synopsisHeading = "%nUsage: ", commandListHeading = "%nAvailable Commands:%n", optionListHeading = "%nFlags:%n",
		footer = { "", "Use \"jhelm [command] --help\" for more information about a command." },
		subcommands = { CreateCommand.class, TemplateCommand.class, InstallCommand.class, UpgradeCommand.class,
				UninstallCommand.class, ListCommand.class, StatusCommand.class, HistoryCommand.class,
				RollbackCommand.class, ShowCommand.class, GetCommand.class, TestCommand.class, DependencyCommand.class,
				PullCommand.class, PushCommand.class, PackageCommand.class, VerifyCommand.class, LintCommand.class,
				RepoCommand.class, RegistryCommand.class, PluginCommand.class })
public class JHelmCommand implements Runnable {

	@CommandLine.Option(names = { "--no-color" }, description = "Disable colored output",
			scope = CommandLine.ScopeType.INHERIT)
	public void setNoColor(boolean noColor) {
		if (noColor) {
			CliOutput.setEnabled(false);
		}
	}

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

}
