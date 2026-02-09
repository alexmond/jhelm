package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UninstallAction {
    // In a real implementation, this would delete resources from the manifest
    public void uninstall(String releaseName, String namespace) {
        // ... deletion logic ...
    }
}
