package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;
import java.util.List;

@RequiredArgsConstructor
public class HistoryAction {

    private final KubeService kubeService;

    public List<Release> history(String name, String namespace) throws Exception {
        return kubeService.getReleaseHistory(name, namespace);
    }
}
