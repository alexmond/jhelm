package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class InstallAction {

    private final Engine engine;

    public Release install(Chart chart, String releaseName, String namespace, Map<String, Object> overrideValues, int version) {
        Release release = new Release();
        release.setName(releaseName);
        release.setNamespace(namespace);
        release.setVersion(version);
        release.setChart(chart);
        
        Map<String, Object> values = new HashMap<>(chart.getValues());
        if (overrideValues != null) {
            values.putAll(overrideValues);
        }
        
        Release.ReleaseInfo info = new Release.ReleaseInfo();
        info.setFirstDeployed(OffsetDateTime.now());
        info.setLastDeployed(OffsetDateTime.now());
        info.setStatus("deployed");
        info.setDescription("Install complete");
        release.setInfo(info);

        Map<String, Object> releaseData = new HashMap<>();
        releaseData.put("Name", releaseName);
        releaseData.put("Namespace", namespace);
        releaseData.put("Service", "Helm");
        releaseData.put("IsInstall", true);
        releaseData.put("IsUpgrade", false);
        releaseData.put("Revision", release.getVersion());

        String manifest = engine.render(chart, values, releaseData);
        
        release.setManifest(manifest);
        
        return release;
    }
}
