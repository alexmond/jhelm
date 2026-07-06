package org.alexmond.jhelm.app.command;

import lombok.Getter;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import picocli.CommandLine.Option;

/**
 * Reusable Picocli mixin for resolving a chart from a repository on {@code install} and
 * {@code upgrade}: the chart version, an ad-hoc {@code --repo} URL, and its
 * authentication/TLS settings — mirroring Helm's {@code install}/{@code upgrade} repo
 * flags (and matching {@code jhelm pull}).
 */
@Getter
public class RepoChartOptions {

	@Option(names = { "--version" },
			description = "chart version to fetch when the chart is a repository reference or used with --repo")
	private String version;

	@Option(names = { "--repo" },
			description = "chart repository URL to locate the named chart (Helm's --repo form; requires --version)")
	private String repo;

	@Option(names = { "--username" }, description = "chart repository username (with --repo)")
	private String username;

	@Option(names = { "--password" }, description = "chart repository password (with --repo)")
	private String password;

	@Option(names = { "--cert-file" }, description = "identity certificate (PEM) for client TLS auth")
	private String certFile;

	@Option(names = { "--key-file" }, description = "identity key (PEM) for client TLS auth")
	private String keyFile;

	@Option(names = { "--ca-file" }, description = "verify server certificate against this CA bundle (PEM)")
	private String caFile;

	@Option(names = { "--insecure-skip-tls-verify" },
			description = "skip TLS certificate verification for the chart repository")
	private boolean insecureSkipTlsVerify;

	@Option(names = { "--pass-credentials" },
			description = "send credentials to all domains, not just the repository host")
	private boolean passCredentials;

	/**
	 * @return {@code true} if an ad-hoc {@code --repo} URL was supplied
	 */
	public boolean hasRepo() {
		return this.repo != null && !this.repo.isBlank();
	}

	/**
	 * Builds a repository descriptor carrying the auth and TLS settings for a
	 * {@code --repo} pull.
	 * @return the repository descriptor
	 */
	public RepositoryConfig.Repository auth() {
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
