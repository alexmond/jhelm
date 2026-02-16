package org.alexmond.jhelm.app;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "jhelm", mixinStandardHelpOptions = true, version = "jhelm 0.0.1",
        description = "A Spring Boot based implementation of Helm-like functionality in Java.",
        subcommands = {
                CreateCommand.class,
                RepoCommand.class,
                RegistryCommand.class,
                TemplateCommand.class,
                InstallCommand.class,
                UpgradeCommand.class,
                UninstallCommand.class,
                ListCommand.class,
                HistoryCommand.class,
                StatusCommand.class,
                RollbackCommand.class,
                ShowCommand.class,
                DependencyCommand.class
        })
public class JHelmCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
