package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.RepoManager;
import org.alexmond.jhelm.core.RepositoryConfig;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;

@Component
@CommandLine.Command(name = "repo", description = "manage a chart's repositories",
        subcommands = {
                RepoCommand.AddCommand.class,
                RepoCommand.ListCommand.class,
                RepoCommand.RemoveCommand.class,
                RepoCommand.SearchCommand.class
        })
@Slf4j
public class RepoCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Component
    @CommandLine.Command(name = "add", description = "add a chart repository")
    @Slf4j
    public static class AddCommand implements Runnable {
        private final RepoManager repoManager;

        @CommandLine.Parameters(index = "0", description = "repository name")
        private String name;

        @CommandLine.Parameters(index = "1", description = "repository url")
        private String url;

        public AddCommand(RepoManager repoManager) {
            this.repoManager = repoManager;
        }

        @Override
        public void run() {
            try {
                repoManager.addRepo(name, url);
                log.info("\"{}\" has been added to your repositories", name);
            } catch (IOException e) {
                log.error("Error adding repository: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "list", description = "list chart repositories")
    @Slf4j
    public static class ListCommand implements Runnable {
        private final RepoManager repoManager;

        public ListCommand(RepoManager repoManager) {
            this.repoManager = repoManager;
        }

        @Override
        public void run() {
            try {
                RepositoryConfig config = repoManager.loadConfig();
                System.out.printf("%-20s\t%-50s\n", "NAME", "URL");
                for (RepositoryConfig.Repository repo : config.getRepositories()) {
                    System.out.printf("%-20s\t%-50s\n", repo.getName(), repo.getUrl());
                }
            } catch (IOException e) {
                log.error("Error listing repositories: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "remove", description = "remove one or more chart repositories")
    @Slf4j
    public static class RemoveCommand implements Runnable {
        private final RepoManager repoManager;

        @CommandLine.Parameters(index = "0", description = "repository name")
        private String name;

        public RemoveCommand(RepoManager repoManager) {
            this.repoManager = repoManager;
        }

        @Override
        public void run() {
            try {
                repoManager.removeRepo(name);
                log.info("\"{}\" has been removed from your repositories", name);
            } catch (IOException e) {
                log.error("Error removing repository: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "search", description = "search the added repositories for a chart (supports repo/chart); use --versions to show all versions like Helm)")
    @Slf4j
    public static class SearchCommand implements Runnable {
        private final RepoManager repoManager;

        @CommandLine.Parameters(index = "0", description = "chart query in the form repo/chart")
        private String query;

        @CommandLine.Option(names = {"--versions"}, description = "show all versions")
        private boolean showAllVersions;

        public SearchCommand(RepoManager repoManager) {
            this.repoManager = repoManager;
        }

        @Override
        public void run() {
            try {
                if (query == null || !query.contains("/")) {
                    log.error("Please specify chart as repo/chart (e.g., bitnami/ghost)");
                    return;
                }
                String repo = query.substring(0, query.indexOf('/'));
                String chart = query.substring(query.indexOf('/') + 1);

                var versions = repoManager.getChartVersions(repo, chart);
                if (versions.isEmpty()) {
                    log.info("No results found for {}. Make sure the repo is added (jhelm repo add) and contains the chart.", query);
                    return;
                }

                System.out.printf("%-40s %-15s %-15s %-s\n", "NAME", "CHART VERSION", "APP VERSION", "DESCRIPTION");
                if (showAllVersions) {
                    for (var v : versions) {
                        System.out.printf("%-40s %-15s %-15s %-s\n", v.getName(), nv(v.getChartVersion()), nv(v.getAppVersion()), nv(v.getDescription()));
                    }
                } else {
                    var v = versions.get(0);
                    System.out.printf("%-40s %-15s %-15s %-s\n", v.getName(), nv(v.getChartVersion()), nv(v.getAppVersion()), nv(v.getDescription()));
                }
            } catch (Exception e) {
                log.error("Error searching repositories: {}", e.getMessage());
            }
        }

        private String nv(String s) { return s == null ? "" : s; }
    }
}
