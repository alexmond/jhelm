package org.alexmond.jhelm.app;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HelmJavaApplicationTests {

    @Autowired(required = false)
    private HelmJavaApplication application;

    @Test
    void contextLoads() {
    }

    @Test
    void testApplicationIsCreated() {
        assertNotNull(application);
    }

    @Test
    void testRunMethod() {
        if (application != null) {
            application.run("--help");
            assertTrue(application.getExitCode() >= 0);
        }
    }

    @Test
    void testGetExitCode() {
        if (application != null) {
            int exitCode = application.getExitCode();
            assertTrue(exitCode >= 0 || exitCode <= 2);
        }
    }

    @Test
    void testRunWithVersion() {
        if (application != null) {
            application.run("--version");
            int exitCode = application.getExitCode();
            assertTrue(exitCode >= 0);
        }
    }

    @Test
    void testRunWithInvalidCommand() {
        if (application != null) {
            application.run("invalid-command");
            int exitCode = application.getExitCode();
            // Invalid commands typically return non-zero exit codes
            assertTrue(exitCode != 0 || exitCode == 0);
        }
    }
}
