package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for registering a new chart repository.
 */
@Data
@Schema(description = "Request to add a chart repository")
public class RepoAddRequest {

	@Schema(description = "Repository name", example = "bitnami", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "name is required")
	private String name;

	@Schema(description = "Repository URL", example = "https://charts.bitnami.com/bitnami",
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "url is required")
	private String url;

	@Schema(description = "Chart repository username")
	private String username;

	@Schema(description = "Chart repository password")
	private String password;

	@Schema(description = "Identity certificate (PEM) for client TLS auth")
	private String certFile;

	@Schema(description = "Identity key (PEM) for client TLS auth")
	private String keyFile;

	@Schema(description = "Verify the server certificate against this CA bundle (PEM)")
	private String caFile;

	@Schema(description = "Skip TLS verification of the repository", defaultValue = "false")
	private boolean insecureSkipTlsVerify;

	@Schema(description = "Send credentials to all domains, not just the repository host", defaultValue = "false")
	private boolean passCredentials;

	@Schema(description = "Replace the repository if it already exists (default: fail)", defaultValue = "false")
	private boolean forceUpdate;

	@Schema(description = "Do not fetch the repository index after adding", defaultValue = "false")
	private boolean noUpdate;

}
