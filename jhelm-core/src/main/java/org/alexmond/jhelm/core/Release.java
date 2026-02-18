package org.alexmond.jhelm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Release {
    private String name;
    private String namespace;
    private int version;
    private Chart chart;
    private MapConfig config;
    private ReleaseInfo info;
    private String manifest;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleaseInfo {
        private OffsetDateTime firstDeployed;
        private OffsetDateTime lastDeployed;
        private OffsetDateTime deleted;
        private String description;
        private String status; // e.g., "deployed", "uninstalled"
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapConfig {
        private java.util.Map<String, Object> values;
    }
}
