package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class GetAction {

    private final KubeService kubeService;

    public Optional<Release> getRelease(String name, String namespace) throws Exception {
        return kubeService.getRelease(name, namespace);
    }

    public Optional<Release> getReleaseByRevision(String name, String namespace, int revision) throws Exception {
        List<Release> history = kubeService.getReleaseHistory(name, namespace);
        return history.stream()
                .filter(r -> r.getVersion() == revision)
                .findFirst();
    }

    public String getValues(Release release, boolean all) throws Exception {
        if (all && release.getChart() != null && release.getChart().getValues() != null) {
            Map<String, Object> merged = new LinkedHashMap<>(release.getChart().getValues());
            if (release.getConfig() != null && release.getConfig().getValues() != null) {
                merged.putAll(release.getConfig().getValues());
            }
            return toYaml(merged);
        }
        if (release.getConfig() == null || release.getConfig().getValues() == null
                || release.getConfig().getValues().isEmpty()) {
            return "{}";
        }
        return toYaml(release.getConfig().getValues());
    }

    public String getManifest(Release release) {
        return release.getManifest() != null ? release.getManifest() : "";
    }

    public String getNotes(Release release) {
        if (release.getInfo() != null && release.getInfo().getNotes() != null) {
            return release.getInfo().getNotes();
        }
        return "";
    }

    public String getHooks(Release release) {
        if (release.getManifest() == null || release.getManifest().isEmpty()) {
            return "";
        }
        String[] docs = release.getManifest().split("---");
        StringBuilder hooks = new StringBuilder();
        for (String doc : docs) {
            if (doc.trim().isEmpty()) {
                continue;
            }
            if (doc.contains("helm.sh/hook")) {
                if (!hooks.isEmpty()) {
                    hooks.append("---\n");
                }
                hooks.append(doc.trim()).append("\n");
            }
        }
        return hooks.toString();
    }

    public Map<String, Object> getMetadata(Release release) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", release.getName());
        metadata.put("namespace", release.getNamespace());
        metadata.put("revision", release.getVersion());
        if (release.getChart() != null && release.getChart().getMetadata() != null) {
            ChartMetadata cm = release.getChart().getMetadata();
            metadata.put("chart", cm.getName() + "-" + cm.getVersion());
            metadata.put("appVersion", cm.getAppVersion());
        }
        if (release.getInfo() != null) {
            metadata.put("status", release.getInfo().getStatus());
            metadata.put("deployedAt", release.getInfo().getLastDeployed());
        }
        return metadata;
    }

    public String getAll(Release release, boolean allValues) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append("MANIFEST:\n");
        sb.append(getManifest(release));
        if (!getManifest(release).endsWith("\n")) {
            sb.append("\n");
        }

        String hooks = getHooks(release);
        if (!hooks.isEmpty()) {
            sb.append("\nHOOKS:\n");
            sb.append(hooks);
        }

        String notes = getNotes(release);
        if (!notes.isEmpty()) {
            sb.append("\nNOTES:\n");
            sb.append(notes);
            if (!notes.endsWith("\n")) {
                sb.append("\n");
            }
        }

        sb.append("\nVALUES:\n");
        sb.append(getValues(release, allValues));

        return sb.toString();
    }

    public String toYaml(Object obj) throws Exception {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        return yamlMapper.writeValueAsString(obj);
    }

    public String toJson(Object obj) throws Exception {
        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return jsonMapper.writeValueAsString(obj);
    }
}
