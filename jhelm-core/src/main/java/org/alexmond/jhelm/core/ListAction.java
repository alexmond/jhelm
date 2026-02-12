package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ListAction {
    private final KubeService kubeService;

    public List<Release> list(String namespace) throws Exception {
        return kubeService.listReleases(namespace);
    }
}
