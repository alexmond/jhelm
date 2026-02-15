package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.TemplateAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "template", description = "locally render templates")
@Slf4j
public class TemplateCommand implements Runnable {

    private final TemplateAction templateAction;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Parameters(index = "1", description = "chart path")
    private String chartPath;

    public TemplateCommand(TemplateAction templateAction) {
        this.templateAction = templateAction;
    }

    @Override
    public void run() {
        try {
            String manifest = templateAction.render(chartPath, name, "default");
            log.info("\n{}", manifest);
        } catch (Exception e) {
            log.error("Error rendering template: {}", e.getMessage());
        }
    }
}
