package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Component
@CommandLine.Command(name = "list", description = "list releases")
public class ListCommand implements Runnable {

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;

    public ListCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            List<Release> releases = helmKubeService.listReleases(namespace);
            System.out.printf("%-20s %-10s %-10s %-30s\n", "NAME", "REVISION", "STATUS", "CHART");
            for (Release r : releases) {
                String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
                System.out.printf("%-20s %-10d %-10s %-30s\n", 
                    r.getName(), r.getVersion(), r.getInfo().getStatus(), chartInfo);
            }
        } catch (Exception e) {
            System.err.println("Error listing releases: " + e.getMessage());
        }
    }
}
