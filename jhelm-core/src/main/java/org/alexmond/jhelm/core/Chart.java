package org.alexmond.jhelm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chart {
    private ChartMetadata metadata;
    @Builder.Default
    private List<Template> templates = new ArrayList<>();
    private Map<String, Object> values;
    @Builder.Default
    private List<Chart> dependencies = new ArrayList<>();
    private String readme;
    @Builder.Default
    private List<Crd> crds = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Template {
        private String name;
        private String data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Crd {
        private String name;
        private String data;
    }
}
