package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class InstallAction {

    private final Engine engine;
    private final KubeService kubeService;

    public Release install(Chart chart, String releaseName, String namespace, Map<String, Object> overrideValues, int version) throws Exception {
        Map<String, Object> values = new HashMap<>(chart.getValues());
        if (overrideValues != null) {
            values.putAll(overrideValues);
        }

        Release.ReleaseInfo info = Release.ReleaseInfo.builder()
                .firstDeployed(OffsetDateTime.now())
                .lastDeployed(OffsetDateTime.now())
                .status("deployed")
                .description("Install complete")
                .build();

        Release release = Release.builder()
                .name(releaseName)
                .namespace(namespace)
                .version(version)
                .chart(chart)
                .info(info)
                .build();

        Map<String, Object> releaseData = new HashMap<>();
        releaseData.put("Name", releaseName);
        releaseData.put("Namespace", namespace);
        releaseData.put("Service", "Helm");
        releaseData.put("IsInstall", true);
        releaseData.put("IsUpgrade", false);
        releaseData.put("Revision", release.getVersion());

        String manifest = engine.render(chart, values, releaseData);
        
        release.setManifest(manifest);

        if (kubeService != null) {
            kubeService.apply(namespace, manifest);
            kubeService.storeRelease(release);
        }
        
        return release;
    }
}
