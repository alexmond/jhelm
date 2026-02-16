package org.alexmond.jhelm.core;

import org.alexmond.jhelm.core.ChartLock.LockDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChartLock.
 */
class ChartLockTest {

    @TempDir
    File tempDir;

    @Test
    void testWriteAndReadLockFile() throws IOException {
        LockDependency dep1 = LockDependency.builder()
                .name("postgresql")
                .version("12.1.5")
                .repository("https://charts.bitnami.com/bitnami")
                .build();

        LockDependency dep2 = LockDependency.builder()
                .name("redis")
                .version("17.3.14")
                .repository("https://charts.bitnami.com/bitnami")
                .build();

        ChartLock originalLock = ChartLock.builder()
                .dependencies(List.of(dep1, dep2))
                .digest("sha256:abcdef123456")
                .build();

        // Write lock file
        originalLock.toFile(tempDir);

        // Verify file was created
        File lockFile = new File(tempDir, "Chart.lock");
        assertTrue(lockFile.exists(), "Chart.lock file should be created");

        // Read lock file
        ChartLock readLock = ChartLock.fromFile(tempDir);

        assertNotNull(readLock);
        assertEquals(2, readLock.getDependencies().size());
        assertEquals("postgresql", readLock.getDependencies().get(0).getName());
        assertEquals("12.1.5", readLock.getDependencies().get(0).getVersion());
        assertEquals("redis", readLock.getDependencies().get(1).getName());
        assertEquals("17.3.14", readLock.getDependencies().get(1).getVersion());
        assertEquals("sha256:abcdef123456", readLock.getDigest());
        assertNotNull(readLock.getGenerated(), "Generated timestamp should be set");
    }

    @Test
    void testReadNonExistentLockFile() throws IOException {
        ChartLock lock = ChartLock.fromFile(tempDir);
        assertNull(lock, "Should return null when Chart.lock doesn't exist");
    }

    @Test
    void testGeneratedTimestamp() throws IOException {
        ChartLock lock = ChartLock.builder()
                .dependencies(List.of())
                .digest("sha256:test")
                .build();

        assertNull(lock.getGenerated(), "Generated should be null before writing");

        lock.toFile(tempDir);

        ChartLock readLock = ChartLock.fromFile(tempDir);
        assertNotNull(readLock.getGenerated(), "Generated timestamp should be set after writing");
    }
}
