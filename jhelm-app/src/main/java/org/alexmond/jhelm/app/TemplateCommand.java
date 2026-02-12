package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Chart;
import org.alexmond.jhelm.core.ChartLoader;
import org.alexmond.jhelm.core.Engine;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Component
@CommandLine.Command(name = "template", description = "locally render templates")
@Slf4j
public class TemplateCommand implements Runnable {

    private final Engine engine;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Parameters(index = "1", description = "chart path")
    private String chartPath;

    public TemplateCommand(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void run() {
        try {
            ChartLoader loader = new ChartLoader();
            Chart chart = loader.load(new File(chartPath));

            Map<String, Object> releaseData = new HashMap<>();
            releaseData.put("Name", name);
            releaseData.put("Namespace", "default");
            releaseData.put("Service", "Helm");
            releaseData.put("IsInstall", true);
            releaseData.put("IsUpgrade", false);
            releaseData.put("Revision", 1);

            String manifest = engine.render(chart, chart.getValues(), releaseData);
            log.info("\n{}", manifest);
        } catch (Exception e) {
            log.error("Error rendering template: {}", e.getMessage());
        }
    }
}
