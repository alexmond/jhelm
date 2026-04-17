---
name: helm-upstream
description: Check latest Helm releases for issues and fixes applicable to jhelm
argument-hint: [number of releases to check, default 4]
allowed-tools: Bash(gh *), Read, Glob, Grep, WebSearch, WebFetch
---

## Check Upstream Helm Releases

Check the latest Helm releases for closed issues, security fixes, and bug fixes that may be applicable to jhelm.

### Steps

1. **Fetch latest releases** (default 4, or `$ARGUMENTS` if specified):
```bash
gh release list -R helm/helm --limit ${ARGUMENTS:-4}
```

2. **Get release notes** for each:
```bash
gh release view <tag> -R helm/helm
```

3. **Fetch closed issues** associated with these releases:
```bash
gh issue list -R helm/helm --state closed --limit 50 --json number,title,labels,closedAt,body
```

4. **Cross-reference with jhelm** — for each issue, determine if it applies:
   - **Template engine bugs** → check jhelm-gotemplate
   - **Values merging/coalescing** → check Engine.java, ValuesLoader.java
   - **Chart extraction/packaging** → check RepoManager.java, ChartLoader.java
   - **OCI registry** → check OciRegistryClient.java
   - **Security advisories** → always check
   - **CLI/plugin/Kubernetes-specific** → usually not applicable

5. **Categorize findings**:
   - **APPLICABLE — Action Required**: jhelm has the same vulnerability/bug
   - **APPLICABLE — Verify**: jhelm may be affected, needs investigation
   - **NOT APPLICABLE**: jhelm handles this differently or doesn't implement the feature

### Report Format

| Issue | Severity | Description | jhelm Status | Action |
|-------|----------|-------------|--------------|--------|
| # | Security/Bug/Feature | Summary | Affected/Safe/N/A | Create issue / No action |

For each APPLICABLE finding, suggest whether to create a GitHub issue.
