package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.core.UninstallAction;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Optional;

@Component
@CommandLine.Command(name = "uninstall", description = "uninstall a release")
@Slf4j
public class UninstallCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final UninstallAction uninstallAction;

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
