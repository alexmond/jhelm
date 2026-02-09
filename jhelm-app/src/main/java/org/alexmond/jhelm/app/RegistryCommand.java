package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.RegistryManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;

@Component
@CommandLine.Command(name = "registry", description = "login to or logout from a registry",
        subcommands = {
                RegistryCommand.LoginCommand.class,
                RegistryCommand.LogoutCommand.class
        })
@Slf4j
public class RegistryCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Component
    @CommandLine.Command(name = "login", description = "login to a registry")
    @Slf4j
    public static class LoginCommand implements Runnable {
        private final RegistryManager registryManager;

        @CommandLine.Parameters(index = "0", description = "registry server")
        private String server;

        @CommandLine.Option(names = {"-u", "--username"}, description = "registry username", required = true)
        private String username;

        @CommandLine.Option(names = {"-p", "--password"}, description = "registry password", required = true)
        private String password;

        public LoginCommand(RegistryManager registryManager) {
            this.registryManager = registryManager;
        }

        @Override
        public void run() {
            try {
                registryManager.login(server, username, password);
                log.info("Login Succeeded");
            } catch (IOException e) {
                log.error("Error logging in: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "logout", description = "logout from a registry")
    @Slf4j
    public static class LogoutCommand implements Runnable {
        private final RegistryManager registryManager;

        @CommandLine.Parameters(index = "0", description = "registry server")
        private String server;

        public LogoutCommand(RegistryManager registryManager) {
            this.registryManager = registryManager;
        }

        @Override
        public void run() {
            try {
                registryManager.logout(server);
                log.info("Logout Succeeded");
            } catch (IOException e) {
                log.error("Error logging out: {}", e.getMessage());
            }
        }
    }
}
