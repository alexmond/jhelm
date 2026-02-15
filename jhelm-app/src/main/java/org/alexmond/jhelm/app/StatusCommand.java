package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.core.StatusAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Optional;

@Component
@CommandLine.Command(name = "status", description = "display the status of the named release")
@Slf4j
public class StatusCommand implements Runnable {

    private final StatusAction statusAction;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    public StatusCommand(StatusAction statusAction) {
        this.statusAction = statusAction;
    }

    @Override
    public void run() {
        try {
            Optional<Release> releaseOpt = statusAction.status(name, namespace);
            if (releaseOpt.isEmpty()) {
                log.error("Error: release not found: {}", name);
                return;
            }

            Release r = releaseOpt.get();
            log.info("NAME: {}", r.getName());
            log.info("LAST DEPLOYED: {}", r.getInfo().getLastDeployed());
            log.info("NAMESPACE: {}", r.getNamespace());
            log.info("STATUS: {}", r.getInfo().getStatus());
            log.info("REVISION: {}", r.getVersion());
            log.info("\nMANIFEST:\n{}", r.getManifest());
        } catch (Exception e) {
            log.error("Error fetching status: {}", e.getMessage());
        }
    }
}
