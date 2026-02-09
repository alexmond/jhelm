package org.alexmond.jhelm.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
        private boolean insecure_skip_tls_verify;
        private boolean pass_credentials_all;
    }
}
