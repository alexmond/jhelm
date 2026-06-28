package org.alexmond.jhelm.core.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryConfig {

	private String apiVersion;

	private String generated;

	private List<Repository> repositories;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Repository {

		private String name;

		private String url;

		private String username;

		private String password;

		private String certFile;

		private String keyFile;

		private String caFile;

		@JsonProperty("insecure_skip_tls_verify")
		private boolean insecureSkipTlsVerify;

		@JsonProperty("pass_credentials_all")
		private boolean passCredentialsAll;

	}

}
