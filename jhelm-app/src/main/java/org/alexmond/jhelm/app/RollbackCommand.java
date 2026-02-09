package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Optional;

@Component
@CommandLine.Command(name = "rollback", description = "roll back a release to a previous revision")
public class RollbackCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Parameters(index = "1", description = "revision number")
    private int revision;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;

    public RollbackCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            List<Release> history = helmKubeService.getReleaseHistory(name, namespace);
            Optional<Release> targetReleaseOpt = history.stream()
                .filter(r -> r.getVersion() == revision)
                .findFirst();

            if (targetReleaseOpt.isEmpty()) {
                System.err.println("Error: revision " + revision + " not found for release " + name);
                return;
            }

            Optional<Release> currentReleaseOpt = history.stream().findFirst(); // History is sorted descending
            int nextRevision = currentReleaseOpt.map(r -> r.getVersion() + 1).orElse(1);

            Release targetRelease = targetReleaseOpt.get();
            Release newRelease = new Release();
            newRelease.setName(targetRelease.getName());
            newRelease.setNamespace(targetRelease.getNamespace());
            newRelease.setVersion(nextRevision);
            newRelease.setChart(targetRelease.getChart());
            newRelease.setManifest(targetRelease.getManifest());
            
            Release.ReleaseInfo info = new Release.ReleaseInfo();
            info.setFirstDeployed(targetRelease.getInfo().getFirstDeployed());
            info.setLastDeployed(java.time.OffsetDateTime.now());
            info.setStatus("deployed");
            info.setDescription("Rollback to " + revision);
            newRelease.setInfo(info);

            helmKubeService.apply(namespace, newRelease.getManifest());
            helmKubeService.storeRelease(newRelease);
            
            System.out.println("Rollback was a success! Happy Helming!");
        } catch (Exception e) {
            System.err.println("Error during rollback: " + e.getMessage());
        }
    }
}
