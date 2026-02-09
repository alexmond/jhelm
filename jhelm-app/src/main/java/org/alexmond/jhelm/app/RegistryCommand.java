package org.alexmond.jhelm.app;

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
public class RegistryCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Component
    @CommandLine.Command(name = "login", description = "login to a registry")
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
                System.out.println("Login Succeeded");
            } catch (IOException e) {
                System.err.println("Error logging in: " + e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "logout", description = "logout from a registry")
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
                System.out.println("Logout Succeeded");
            } catch (IOException e) {
                System.err.println("Error logging out: " + e.getMessage());
            }
        }
    }
}
