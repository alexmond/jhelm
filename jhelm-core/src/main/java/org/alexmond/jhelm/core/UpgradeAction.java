package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class UpgradeAction {

    private final Engine engine;

    public Release upgrade(Release currentRelease, Chart newChart, Map<String, Object> overrideValues) {
        Release newRelease = new Release();
        newRelease.setName(currentRelease.getName());
        newRelease.setNamespace(currentRelease.getNamespace());
        newRelease.setVersion(currentRelease.getVersion() + 1);
        newRelease.setChart(newChart);

        Map<String, Object> values = new HashMap<>(newChart.getValues());
        if (overrideValues != null) {
            values.putAll(overrideValues);
        }

        Release.ReleaseInfo info = new Release.ReleaseInfo();
        info.setFirstDeployed(currentRelease.getInfo().getFirstDeployed());
        info.setLastDeployed(OffsetDateTime.now());
        info.setStatus("deployed");
        info.setDescription("Upgrade complete");
        newRelease.setInfo(info);

        Map<String, Object> releaseData = new HashMap<>();
        releaseData.put("Name", newRelease.getName());
        releaseData.put("Namespace", newRelease.getNamespace());
        releaseData.put("Service", "Helm");
        releaseData.put("IsInstall", false);
        releaseData.put("IsUpgrade", true);

        String manifest = engine.render(newChart, values, releaseData);
        newRelease.setManifest(manifest);

        return newRelease;
    }
}
