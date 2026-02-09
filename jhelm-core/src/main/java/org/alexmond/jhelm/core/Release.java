package org.alexmond.jhelm.core;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Release {
    private String name;
    private String namespace;
    private int version;
    private Chart chart;
    private MapConfig config;
    private ReleaseInfo info;
    private String manifest;

    @Data
    public static class ReleaseInfo {
        private OffsetDateTime firstDeployed;
        private OffsetDateTime lastDeployed;
        private OffsetDateTime deleted;
        private String description;
        private String status; // e.g., "deployed", "uninstalled"
    }

    @Data
    public static class MapConfig {
        private java.util.Map<String, Object> values;
    }
}
