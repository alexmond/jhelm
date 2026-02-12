package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Component
@CommandLine.Command(name = "history", description = "fetch release history")
@Slf4j
public class HistoryCommand implements Runnable {

    private final HelmKubeService helmKubeService;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    public HistoryCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            List<Release> history = helmKubeService.getReleaseHistory(name, namespace);
            System.out.printf("%-10s %-30s %-10s %-20s %-30s\n", "REVISION", "UPDATED", "STATUS", "CHART", "DESCRIPTION");
            for (Release r : history) {
                String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
                System.out.printf("%-10d %-30s %-10s %-20s %-30s\n",
                        r.getVersion(), r.getInfo().getLastDeployed(), r.getInfo().getStatus(), chartInfo, r.getInfo().getDescription());
            }
        } catch (Exception e) {
            log.error("Error fetching history: {}", e.getMessage());
        }
    }
}
