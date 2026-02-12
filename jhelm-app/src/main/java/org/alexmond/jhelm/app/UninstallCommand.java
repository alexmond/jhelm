package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.UninstallAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "uninstall", description = "uninstall a release")
@Slf4j
public class UninstallCommand implements Runnable {

    private final UninstallAction uninstallAction;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    public UninstallCommand(UninstallAction uninstallAction) {
        this.uninstallAction = uninstallAction;
    }

    @Override
    public void run() {
        try {
            uninstallAction.uninstall(name, namespace);
            log.info("release \"{}\" uninstalled", name);
        } catch (Exception e) {
            log.error("Error uninstalling release: {}", e.getMessage());
        }
    }
}
