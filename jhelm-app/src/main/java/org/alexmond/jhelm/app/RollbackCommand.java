package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.RollbackAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "rollback", description = "roll back a release to a previous revision")
@Slf4j
public class RollbackCommand implements Runnable {

    private final RollbackAction rollbackAction;
    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;
    @CommandLine.Parameters(index = "1", description = "revision number")
    private int revision;
    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    public RollbackCommand(RollbackAction rollbackAction) {
        this.rollbackAction = rollbackAction;
    }

    @Override
    public void run() {
        try {
            rollbackAction.rollback(name, namespace, revision);
            log.info("Rollback was a success! Happy Helming!");
        } catch (Exception e) {
            log.error("Error during rollback: {}", e.getMessage());
        }
    }
}
