package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepoManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetChartVersionsFromRealRepo() throws IOException {
        RepoManager repoManager = new RepoManager();
        repoManager.setInsecureSkipTlsVerify(true);
        
        // We use a real repo for integration-like test
        repoManager.addRepo("bitnami-test", "https://charts.bitnami.com/bitnami");
        
        List<RepoManager.ChartVersion> versions = repoManager.getChartVersions("bitnami-test", "nginx");
        
        assertNotNull(versions);
        assertFalse(versions.isEmpty());
        
        RepoManager.ChartVersion latest = versions.get(0);
        assertEquals("bitnami-test/nginx", latest.getName());
        assertNotNull(latest.getChartVersion());
        assertNotNull(latest.getAppVersion());
        assertNotNull(latest.getDescription());
        
        // Cleanup repo if it was added to real config (RepoManager currently uses fixed paths in home dir)
        // Note: RepoManager constructor uses hardcoded paths, which is not ideal for unit tests.
        // For now, we'll just verify the logic.
    }

    @Test
    void testRepoNotFound() {
        RepoManager repoManager = new RepoManager();
        assertThrows(IOException.class, () -> repoManager.getChartVersions("non-existent", "nginx"));
    }
}
