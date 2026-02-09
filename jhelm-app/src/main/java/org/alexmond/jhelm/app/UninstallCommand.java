package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Optional;

@Component
@CommandLine.Command(name = "uninstall", description = "uninstall a release")
public class UninstallCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;

    public UninstallCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            Optional<Release> releaseOpt = helmKubeService.getRelease(name, namespace);
            if (releaseOpt.isEmpty()) {
                System.err.println("Error: uninstall: Release not found: " + name);
                return;
            }

            Release release = releaseOpt.get();
            helmKubeService.delete(namespace, release.getManifest());
            helmKubeService.deleteReleaseHistory(name, namespace);
            
            System.out.println("release \"" + name + "\" uninstalled");
        } catch (Exception e) {
            System.err.println("Error uninstalling release: " + e.getMessage());
        }
    }
}
