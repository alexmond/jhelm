package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;
import java.util.Optional;

@RequiredArgsConstructor
public class StatusAction {

    private final KubeService kubeService;

    public Optional<Release> status(String name, String namespace) throws Exception {
        return kubeService.getRelease(name, namespace);
    }
}
