package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class UpgradeAction {

    private final Engine engine;
    private final KubeService kubeService;

    public Release upgrade(Release currentRelease, Chart newChart, Map<String, Object> overrideValues) throws Exception {
        Map<String, Object> values = new HashMap<>(newChart.getValues());
        if (overrideValues != null) {
            values.putAll(overrideValues);
        }

        Release.ReleaseInfo info = Release.ReleaseInfo.builder()
                .firstDeployed(currentRelease.getInfo().getFirstDeployed())
                .lastDeployed(OffsetDateTime.now())
                .status("deployed")
                .description("Upgrade complete")
                .build();

        Release newRelease = Release.builder()
                .name(currentRelease.getName())
                .namespace(currentRelease.getNamespace())
                .version(currentRelease.getVersion() + 1)
                .chart(newChart)
                .info(info)
                .build();

        Map<String, Object> releaseData = new HashMap<>();
        releaseData.put("Name", newRelease.getName());
        releaseData.put("Namespace", newRelease.getNamespace());
        releaseData.put("Service", "Helm");
        releaseData.put("IsInstall", false);
        releaseData.put("IsUpgrade", true);

        String manifest = engine.render(newChart, values, releaseData);
        newRelease.setManifest(manifest);

        if (kubeService != null) {
            kubeService.apply(newRelease.getNamespace(), manifest);
            kubeService.storeRelease(newRelease);
        }

        return newRelease;
    }
}
