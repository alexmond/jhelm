package org.alexmond.jhelm.core;

import com.vdurmont.semver4j.Requirement;
import com.vdurmont.semver4j.Semver;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.ChartLock.LockDependency;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves chart dependencies based on version constraints.
 * <p>
 * This service handles:
 * <ul>
 *   <li>Version constraint matching using semver</li>
 *   <li>Conditional dependency evaluation</li>
 *   <li>Dependency tag filtering</li>
 *   <li>Chart.lock generation</li>
 * </ul>
 */
@Slf4j
public class DependencyResolver {
    private final RepoManager repoManager;

    public DependencyResolver(RepoManager repoManager) {
        this.repoManager = repoManager;
    }

    /**
     * Resolves all dependencies from Chart.yaml and returns a ChartLock with exact versions.
     *
     * @param metadata the chart metadata containing dependencies
     * @param values the chart values for evaluating conditions
     * @param enabledTags list of enabled tags for tag-based filtering
     * @return a ChartLock with resolved exact versions
     * @throws IOException if dependency resolution fails
     */
    public ChartLock resolveDependencies(ChartMetadata metadata, Map<String, Object> values, List<String> enabledTags) throws IOException {
        if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
            String digest = generateDigest(new ArrayList<>());
            return ChartLock.builder()
                    .dependencies(new ArrayList<>())
                    .digest(digest)
                    .build();
        }

        List<LockDependency> lockDependencies = new ArrayList<>();

        for (Dependency dep : metadata.getDependencies()) {
            // Check if dependency should be included based on conditions and tags
            if (!shouldIncludeDependency(dep, values, enabledTags)) {
                log.info("Skipping dependency {} due to condition/tag evaluation", dep.getName());
                continue;
            }

            // Resolve the dependency version
            LockDependency lockDep = resolveDependency(dep);
            lockDependencies.add(lockDep);
        }

        // Generate digest
        String digest = generateDigest(lockDependencies);

        return ChartLock.builder()
                .dependencies(lockDependencies)
                .digest(digest)
                .build();
    }

    /**
     * Resolves a single dependency to an exact version.
     *
     * @param dependency the dependency to resolve
     * @return a LockDependency with the resolved exact version
     * @throws IOException if the dependency cannot be resolved
     */
    private LockDependency resolveDependency(Dependency dependency) throws IOException {
        String repoName = dependency.getRepository();
        String chartName = dependency.getName();
        String versionConstraint = dependency.getVersion();

        // Handle repository aliases (strip @ prefix)
        if (repoName != null && repoName.startsWith("@")) {
            repoName = repoName.substring(1);
        }

        // Handle OCI and file:// repositories
        if (repoName != null && (repoName.startsWith("oci://") || repoName.startsWith("file://"))) {
            // For OCI and file repos, we trust the version as-is since we can't query for versions
            return LockDependency.builder()
                    .name(chartName)
                    .version(versionConstraint)
                    .repository(repoName)
                    .build();
        }

        // Get available versions from the repository
        List<RepoManager.ChartVersion> availableVersions = repoManager.getChartVersions(repoName, chartName);

        if (availableVersions.isEmpty()) {
            throw new IOException("No versions found for " + chartName + " in repository " + repoName);
        }

        // Find the best matching version
        String resolvedVersion = findBestMatchingVersion(versionConstraint, availableVersions);

        if (resolvedVersion == null) {
            throw new IOException("No version of " + chartName + " satisfies constraint: " + versionConstraint);
        }

        log.info("Resolved dependency {}: {} -> {}", chartName, versionConstraint, resolvedVersion);

        return LockDependency.builder()
                .name(chartName)
                .version(resolvedVersion)
                .repository(dependency.getRepository())
                .build();
    }

    /**
     * Finds the best matching version from available versions based on the constraint.
     *
     * @param constraint the version constraint (e.g., "^1.2.3", "~1.2.0", ">=1.0.0 <2.0.0")
     * @param availableVersions list of available versions
     * @return the best matching version, or {@code null} if none match
     */
    private String findBestMatchingVersion(String constraint, List<RepoManager.ChartVersion> availableVersions) {
        try {
            Requirement requirement = Requirement.buildNPM(constraint);

            // Find all matching versions
            List<Semver> matchingVersions = new ArrayList<>();
            for (RepoManager.ChartVersion cv : availableVersions) {
                try {
                    Semver semver = new Semver(cv.getChartVersion(), Semver.SemverType.NPM);
                    if (requirement.isSatisfiedBy(semver)) {
                        matchingVersions.add(semver);
                    }
                } catch (Exception e) {
                    log.debug("Skipping non-semver version: {}", cv.getChartVersion());
                }
            }

            // Return the highest matching version
            if (!matchingVersions.isEmpty()) {
                matchingVersions.sort(Semver::compareTo);
                return matchingVersions.get(matchingVersions.size() - 1).getValue();
            }
        } catch (Exception e) {
            log.warn("Failed to parse version constraint '{}': {}. Trying exact match.", constraint, e.getMessage());

            // Fallback: try exact match
            for (RepoManager.ChartVersion cv : availableVersions) {
                if (cv.getChartVersion().equals(constraint)) {
                    return cv.getChartVersion();
                }
            }
        }

        return null;
    }

    /**
     * Checks if a dependency should be included based on its condition and tags.
     *
     * @param dependency the dependency to check
     * @param values the chart values for condition evaluation
     * @param enabledTags list of enabled tags
     * @return {@code true} if the dependency should be included
     */
    private boolean shouldIncludeDependency(Dependency dependency, Map<String, Object> values, List<String> enabledTags) {
        // Check condition
        if (dependency.getCondition() != null && !dependency.getCondition().isEmpty()) {
            boolean conditionMet = evaluateCondition(dependency.getCondition(), values);
            if (!conditionMet) {
                return false;
            }
        }

        // Check tags
        if (dependency.getTags() != null && !dependency.getTags().isEmpty() && enabledTags != null) {
            boolean tagMatched = false;
            for (String tag : dependency.getTags()) {
                if (enabledTags.contains(tag)) {
                    tagMatched = true;
                    break;
                }
            }
            if (!tagMatched) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluates a condition expression against chart values.
     * <p>
     * Example: "postgresql.enabled" returns {@code true} if values.postgresql.enabled == true
     *
     * @param condition the condition expression (dot-separated path)
     * @param values the chart values map
     * @return {@code true} if the condition evaluates to true
     */
    private boolean evaluateCondition(String condition, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        String[] parts = condition.split("\\.");
        Object current = values;

        for (String part : parts) {
            if (!(current instanceof Map)) {
                return false;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return false;
            }
        }

        // Convert to boolean
        if (current instanceof Boolean) {
            return (Boolean) current;
        }
        if (current instanceof String) {
            return Boolean.parseBoolean((String) current);
        }

        return false;
    }

    /**
     * Generates a SHA256 digest of the dependencies for integrity checking.
     *
     * @param dependencies the list of lock dependencies
     * @return the SHA256 digest as a hex string
     */
    private String generateDigest(List<LockDependency> dependencies) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (LockDependency dep : dependencies) {
                digest.update((dep.getName() + dep.getVersion() + dep.getRepository()).getBytes());
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return "sha256:" + hexString;
        } catch (Exception e) {
            log.warn("Failed to generate digest: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Downloads and extracts dependencies to the charts/ directory.
     *
     * @param chartDir the chart directory
     * @param lockDependencies the locked dependencies to download
     * @throws IOException if download or extraction fails
     */
    public void downloadDependencies(File chartDir, List<LockDependency> lockDependencies) throws IOException {
        File chartsDir = new File(chartDir, "charts");
        chartsDir.mkdirs();

        for (LockDependency dep : lockDependencies) {
            log.info("Downloading dependency {}-{} from {}", dep.getName(), dep.getVersion(), dep.getRepository());

            String repoName = dep.getRepository();

            // Handle repository aliases
            if (repoName != null && repoName.startsWith("@")) {
                repoName = repoName.substring(1);
            }

            // Download the chart
            repoManager.pull(dep.getName(), repoName, dep.getVersion(), chartsDir.getAbsolutePath());
        }
    }
}
