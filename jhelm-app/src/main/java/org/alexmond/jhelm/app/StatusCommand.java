package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Optional;

@Component
@CommandLine.Command(name = "status", description = "display the status of the named release")
public class StatusCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;

    public StatusCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            Optional<Release> releaseOpt = helmKubeService.getRelease(name, namespace);
            if (releaseOpt.isEmpty()) {
                System.err.println("Error: release not found: " + name);
                return;
            }

            Release r = releaseOpt.get();
            System.out.println("NAME: " + r.getName());
            System.out.println("LAST DEPLOYED: " + r.getInfo().getLastDeployed());
            System.out.println("NAMESPACE: " + r.getNamespace());
            System.out.println("STATUS: " + r.getInfo().getStatus());
            System.out.println("REVISION: " + r.getVersion());
            System.out.println("\nMANIFEST:");
            System.out.println(r.getManifest());
        } catch (Exception e) {
            System.err.println("Error fetching status: " + e.getMessage());
        }
    }
}
