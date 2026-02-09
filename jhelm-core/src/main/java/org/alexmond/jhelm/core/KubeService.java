package org.alexmond.jhelm.core;

import java.util.List;
import java.util.Optional;

public interface KubeService {
    void storeRelease(Release release) throws Exception;
    Optional<Release> getRelease(String name, String namespace) throws Exception;
    List<Release> listReleases(String namespace) throws Exception;
    List<Release> getReleaseHistory(String name, String namespace) throws Exception;
    void deleteReleaseHistory(String name, String namespace) throws Exception;
    void apply(String namespace, String yamlContent) throws Exception;
    void delete(String namespace, String yamlContent) throws Exception;
}
