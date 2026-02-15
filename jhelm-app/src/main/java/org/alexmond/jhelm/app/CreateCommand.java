package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.CreateAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@CommandLine.Command(
        name = "create",
        description = "Create a new chart with the given name"
)
public class CreateCommand implements Runnable {

    private final CreateAction createAction;

    @CommandLine.Parameters(index = "0", description = "The chart name")
    private String name;

    @CommandLine.Option(names = {"-p", "--starter"}, description = "The name or absolute path to Helm starter scaffold")
    private String starter;

    public CreateCommand(CreateAction createAction) {
        this.createAction = createAction;
    }

    @Override
    public void run() {
        try {
            Path chartPath = Paths.get(name);
            createAction.create(chartPath);
            System.out.println("Creating " + name);
        } catch (IOException e) {
            log.error("Error creating chart: {}", e.getMessage());
            System.exit(1);
        }
    }
}
