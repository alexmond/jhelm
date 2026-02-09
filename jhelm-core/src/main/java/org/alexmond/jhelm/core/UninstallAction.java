package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class UninstallAction {
    private final KubeService kubeService;

    public void uninstall(String releaseName, String namespace) throws Exception {
        Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
        if (releaseOpt.isEmpty()) {
            throw new RuntimeException("Release not found: " + releaseName);
        }

        Release release = releaseOpt.get();
        kubeService.delete(namespace, release.getManifest());
        kubeService.deleteReleaseHistory(releaseName, namespace);
    }
}
