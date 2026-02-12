package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Optional;

@Component
@CommandLine.Command(name = "rollback", description = "roll back a release to a previous revision")
@Slf4j
public class RollbackCommand implements Runnable {

    private final HelmKubeService helmKubeService;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Parameters(index = "1", description = "revision number")
    private int revision;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

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
                log.error("Error: revision {} not found for release {}", revision, name);
                return;
            }

            Optional<Release> currentReleaseOpt = history.stream().findFirst(); // History is sorted descending
            int nextRevision = currentReleaseOpt.map(r -> r.getVersion() + 1).orElse(1);

            Release targetRelease = targetReleaseOpt.get();
            Release newRelease = Release.builder()
                    .name(targetRelease.getName())
                    .namespace(targetRelease.getNamespace())
                    .version(nextRevision)
                    .chart(targetRelease.getChart())
                    .manifest(targetRelease.getManifest())
                    .info(Release.ReleaseInfo.builder()
                            .firstDeployed(targetRelease.getInfo().getFirstDeployed())
                            .lastDeployed(java.time.OffsetDateTime.now())
                            .status("deployed")
                            .description("Rollback to " + revision)
                            .build())
                    .build();

            helmKubeService.apply(namespace, newRelease.getManifest());
            helmKubeService.storeRelease(newRelease);

            log.info("Rollback was a success! Happy Helming!");
        } catch (Exception e) {
            log.error("Error during rollback: {}", e.getMessage());
        }
    }
}
