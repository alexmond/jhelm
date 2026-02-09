package org.alexmond.jhelm.core;

import lombok.*;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Template {
        private String name;
        private String data;
    }
}
