package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.ListAction;
import org.alexmond.jhelm.core.Release;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Component
@CommandLine.Command(name = "list", description = "list releases")
@Slf4j
public class ListCommand implements Runnable {

    private final ListAction listAction;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    public ListCommand(ListAction listAction) {
        this.listAction = listAction;
    }

    @Override
    public void run() {
        try {
            List<Release> releases = listAction.list(namespace);
            System.out.printf("%-20s %-10s %-10s %-30s\n", "NAME", "REVISION", "STATUS", "CHART");
            for (Release r : releases) {
                String chartInfo = r.getChart().getMetadata().getName() + "-" + r.getChart().getMetadata().getVersion();
                System.out.printf("%-20s %-10d %-10s %-30s\n",
                        r.getName(), r.getVersion(), r.getInfo().getStatus(), chartInfo);
            }
        } catch (Exception e) {
            log.error("Error listing releases: {}", e.getMessage());
        }
    }
}
