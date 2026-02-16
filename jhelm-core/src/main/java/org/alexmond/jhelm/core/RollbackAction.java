package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class RollbackAction {

    private final KubeService kubeService;

    public void rollback(String name, String namespace, int revision) throws Exception {
        List<Release> history = kubeService.getReleaseHistory(name, namespace);
        Optional<Release> targetReleaseOpt = history.stream()
                .filter(r -> r.getVersion() == revision)
                .findFirst();

        if (targetReleaseOpt.isEmpty()) {
            throw new RuntimeException("revision " + revision + " not found for release " + name);
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
                        .lastDeployed(OffsetDateTime.now())
                        .status("deployed")
                        .description("Rollback to " + revision)
                        .build())
                .build();

        kubeService.apply(namespace, newRelease.getManifest());
        kubeService.storeRelease(newRelease);
    }
}
