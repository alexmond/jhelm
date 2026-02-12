package org.alexmond.jhelm.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootApplication(scanBasePackages = "org.alexmond.jhelm")
public class HelmJavaApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final JHelmCommand jHelmCommand;
    private int exitCode;

    public HelmJavaApplication(IFactory factory, JHelmCommand jHelmCommand) {
        this.factory = factory;
        this.jHelmCommand = jHelmCommand;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(HelmJavaApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(jHelmCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
