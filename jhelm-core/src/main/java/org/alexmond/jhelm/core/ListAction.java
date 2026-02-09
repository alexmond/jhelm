package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class ListAction {
    // In a real implementation, this would read from a Release storage (e.g. ConfigMaps or Secrets)
    // For now, it's just a stub.
    public List<Release> list(String namespace) {
        return Collections.emptyList();
    }
}
