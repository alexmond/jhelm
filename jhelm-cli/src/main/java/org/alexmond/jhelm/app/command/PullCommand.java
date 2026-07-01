package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm pull CHART}, downloading a chart from a repository or OCI
 * registry to a local directory. Supports Helm's {@code --repo} form for pulling directly
 * from a repository URL with inline credentials and TLS settings, without adding the repo
 * first.
 */
@Component
@CommandLine.Command(name = "pull", mixinStandardHelpOptions = true, description = "Download a chart from a repository")
@Slf4j
public class PullCommand implements Runnable {

	private final RepoManager repoManager;

	@CommandLine.Parameters(index = "0", description = "chart to pull: repo/chart, repo/chart:version, or oci://...")
	private String chart;

	@CommandLine.Option(names = { "--version" }, description = "chart version (required for repo charts)")
	private String version;

	@CommandLine.Option(names = { "--dest", "-d" }, defaultValue = ".", description = "destination directory")
	private String dest;

	@CommandLine.Option(names = { "--repo" },
			description = "chart repository URL to pull the named chart from directly (no prior repo add)")
	private String repo;

	@CommandLine.Option(names = { "--username" }, description = "chart repository username (with --repo)")
	private String username;

	@CommandLine.Option(names = { "--password" }, description = "chart repository password (with --repo)")
	private String password;

	@CommandLine.Option(names = { "--cert-file" }, description = "identity certificate (PEM) for client TLS auth")
	private String certFile;

	@CommandLine.Option(names = { "--key-file" }, description = "identity key (PEM) for client TLS auth")
	private String keyFile;

	@CommandLine.Option(names = { "--ca-file" }, description = "verify server certificate against this CA bundle (PEM)")
	private String caFile;

	@CommandLine.Option(names = { "--insecure-skip-tls-verify" },
			description = "skip TLS verification of the repository")
	private boolean insecureSkipTlsVerify;

	@CommandLine.Option(names = { "--pass-credentials" },
			description = "send credentials to all domains, not just the repository host")
	private boolean passCredentials;

	/**
	 * Creates the command.
	 * @param repoManager the repository manager that downloads the chart
	 */
	public PullCommand(RepoManager repoManager) {
		this.repoManager = repoManager;
	}

	@Override
	public void run() {
		try {
			if (this.repo != null && !this.repo.isBlank()) {
				repoManager.pullFromRepoUrl(this.repo, this.chart, this.version, this.dest, buildRepoAuth());
			}
			else {
				repoManager.pull(this.chart, this.version, this.dest);
			}
			CliOutput.println(CliOutput.success("Chart pulled to " + this.dest));
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error pulling chart: " + ex.getMessage()));
		}
	}

	private RepositoryConfig.Repository buildRepoAuth() {
		return RepositoryConfig.Repository.builder()
			.url(this.repo)
			.username(this.username)
			.password(this.password)
			.certFile(this.certFile)
			.keyFile(this.keyFile)
			.caFile(this.caFile)
			.insecureSkipTlsVerify(this.insecureSkipTlsVerify)
			.passCredentialsAll(this.passCredentials)
			.build();
	}

}
