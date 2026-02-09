package org.alexmond.jhelm.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartMetadata {
    private String name;
    private String version;
    private String description;
    private String apiVersion; // e.g., "v2"
    private String type;       // e.g., "application"
    private String appVersion;
}
