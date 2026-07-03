package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Implements {@code jhelm repo}, managing chart repositories via the {@code add},
 * {@code list}, {@code remove}, {@code update}, and {@code search} subcommands.
 */
@Component
@CommandLine.Command(name = "repo", mixinStandardHelpOptions = true, description = "Manage chart repositories",
		subcommands = { RepoCommand.AddCommand.class, RepoCommand.ListCommand.class, RepoCommand.RemoveCommand.class,
				RepoCommand.UpdateCommand.class, RepoCommand.SearchCommand.class })
@Slf4j
public class RepoCommand implements Callable<Integer> {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public RepoCommand() {
	}

	/**
	 * Prints the usage help when {@code repo} is invoked without a subcommand.
	 */
	@Override
	public Integer call() {
		CommandLine.usage(this, System.out);
		return CommandLine.ExitCode.OK;
	}

	/** Implements {@code repo add}: registers a chart repository by name and URL. */
	@Component
	@CommandLine.Command(name = "add", mixinStandardHelpOptions = true, description = "Add a chart repository")
	@Slf4j
	public static class AddCommand implements Callable<Integer> {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "repository name")
		private String name;

		@CommandLine.Parameters(index = "1", description = "repository url")
		private String url;

		/**
		 * Creates the command.
		 * @param repoManager the repository manager that stores the repository
		 */
		public AddCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public Integer call() {
			try {
				repoManager.addRepo(name, url);
				CliOutput.println(CliOutput.success("\"" + name + "\" has been added to your repositories"));
				return CommandLine.ExitCode.OK;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error adding repository: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code repo list}: prints the configured chart repositories. */
	@Component
	@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "List chart repositories")
	@Slf4j
	public static class ListCommand implements Callable<Integer> {

		private final RepoManager repoManager;

		/**
		 * Creates the command.
		 * @param repoManager the repository manager that supplies the configured
		 * repositories
		 */
		public ListCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public Integer call() {
			try {
				RepositoryConfig config = repoManager.loadConfig();
				CliOutput.printf("%-20s\t%-50s\n", CliOutput.bold("NAME"), CliOutput.bold("URL"));
				for (RepositoryConfig.Repository repo : config.getRepositories()) {
					CliOutput.printf("%-20s\t%-50s\n", repo.getName(), repo.getUrl());
				}
				return CommandLine.ExitCode.OK;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error listing repositories: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code repo remove}: deletes a chart repository by name. */
	@Component
	@CommandLine.Command(name = "remove", mixinStandardHelpOptions = true,
			description = "Remove one or more chart repositories")
	@Slf4j
	public static class RemoveCommand implements Callable<Integer> {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "repository name")
		private String name;

		/**
		 * Creates the command.
		 * @param repoManager the repository manager that removes the repository
		 */
		public RemoveCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public Integer call() {
			try {
				repoManager.removeRepo(name);
				CliOutput.println(CliOutput.success("\"" + name + "\" has been removed from your repositories"));
				return CommandLine.ExitCode.OK;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error removing repository: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/**
	 * Implements {@code repo update}: refreshes the cached index for one or more chart
	 * repositories (all of them when no name is given), matching
	 * {@code helm repo update}.
	 */
	@Component
	@CommandLine.Command(name = "update", aliases = { "up" }, mixinStandardHelpOptions = true,
			description = "Update the local cache of one or more chart repositories (default: all)")
	@Slf4j
	public static class UpdateCommand implements Callable<Integer> {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", arity = "0..*", description = "repositories to update (default: all)")
		private List<String> names;

		/**
		 * Creates the command.
		 * @param repoManager the repository manager that refreshes the repo indexes
		 */
		public UpdateCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public Integer call() {
			try {
				CliOutput.println("Hang tight while we grab the latest from your chart repositories...");
				if (names == null || names.isEmpty()) {
					repoManager.updateAll();
				}
				else {
					for (String name : names) {
						repoManager.updateRepo(name);
					}
				}
				CliOutput.println(CliOutput.success("Update Complete. Happy Helming!"));
				return CommandLine.ExitCode.OK;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Error updating repositories: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code repo search}: searches the added repositories for a chart. */
	@Component
	@CommandLine.Command(name = "search", mixinStandardHelpOptions = true,
			description = "Search the added repositories for a chart")
	@Slf4j
	public static class SearchCommand implements Callable<Integer> {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "chart query in the form repo/chart")
		private String query;

		@CommandLine.Option(names = { "--versions" }, description = "show all versions")
		private boolean showAllVersions;

		/**
		 * Creates the command.
		 * @param repoManager the repository manager that resolves chart versions
		 */
		public SearchCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public Integer call() {
			try {
				if (query == null || !query.contains("/")) {
					CliOutput.errPrintln(CliOutput.error("Please specify chart as repo/chart (e.g., bitnami/ghost)"));
					return CommandLine.ExitCode.SOFTWARE;
				}
				String repo = query.substring(0, query.indexOf('/'));
				String chart = query.substring(query.indexOf('/') + 1);

				var versions = repoManager.getChartVersions(repo, chart);
				if (versions.isEmpty()) {
					CliOutput.println(CliOutput.warn("No results found for " + query
							+ ". Make sure the repo is added (jhelm repo add) and contains the chart."));
					return CommandLine.ExitCode.OK;
				}

				CliOutput.printf("%-40s %-15s %-15s %-s\n", CliOutput.bold("NAME"), CliOutput.bold("CHART VERSION"),
						CliOutput.bold("APP VERSION"), CliOutput.bold("DESCRIPTION"));
				if (showAllVersions) {
					for (var v : versions) {
						CliOutput.printf("%-40s %-15s %-15s %-s\n", v.getName(), nv(v.getChartVersion()),
								nv(v.getAppVersion()), nv(v.getDescription()));
					}
				}
				else {
					var v = versions.get(0);
					CliOutput.printf("%-40s %-15s %-15s %-s\n", v.getName(), nv(v.getChartVersion()),
							nv(v.getAppVersion()), nv(v.getDescription()));
				}
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error searching repositories: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

		private String nv(String s) {
			return (s != null) ? s : "";
		}

	}

}
