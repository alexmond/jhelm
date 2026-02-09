package org.alexmond.jhelm.core;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class Chart {
    private ChartMetadata metadata;
    private List<Template> templates = new ArrayList<>();
    private Map<String, Object> values;
    private List<Chart> dependencies = new ArrayList<>();

    @Data
    public static class Template {
        private String name;
        private String data;
    }
}
