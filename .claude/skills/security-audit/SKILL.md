---
name: security-audit
description: Scan jhelm code for security vulnerabilities (OWASP patterns, path traversal, injection risks)
argument-hint: [module or file path]
allowed-tools: Bash(./mvnw *), Bash(grep *), Read, Glob, Grep
---

## Security Audit for jhelm

Scan the codebase (or specific module/file) for common security vulnerabilities.

### Scope

If `$ARGUMENTS` is provided, limit the scan to that module or path. Otherwise, scan all modules.

### Checks to Perform

#### 1. Path Traversal
Search for file path construction from untrusted input:
```
Grep for: new File(.*,.*getName|new File(.*,.*entry|Path.of(.*input|Paths.get(.*input
```
Verify each hit has canonical path validation or sanitization.

#### 2. Command Injection
Search for Runtime.exec, ProcessBuilder with user input:
```
Grep for: Runtime.getRuntime|ProcessBuilder|\.exec\(
```
Verify arguments are not constructed from user input without sanitization.

#### 3. YAML Deserialization
Search for unsafe YAML parsing:
```
Grep for: new Yaml\(\)|Yaml.load\(|ObjectMapper.*readValue.*untrusted
```
Verify SnakeYAML SafeConstructor is used, or Jackson is configured safely.

#### 4. XML External Entity (XXE)
Search for XML parsing without disabling external entities:
```
Grep for: DocumentBuilder|SAXParser|XMLReader|TransformerFactory
```

#### 5. Sensitive Data Exposure
Search for credentials, tokens, or keys in code:
```
Grep for: password|secret|token|apiKey|private.key (case-insensitive, exclude test files)
```
Verify no hardcoded credentials exist.

#### 6. Input Validation at Boundaries
Check REST endpoints and CLI argument handlers validate input:
- Chart names, repository URLs, file paths from user
- OCI registry responses
- Helm chart metadata (Chart.yaml fields)

### Report Format

| Severity | Location | Issue | Recommendation |
|----------|----------|-------|----------------|
| CRITICAL | file:line | Description | Fix suggestion |
| HIGH | file:line | Description | Fix suggestion |
| MEDIUM | file:line | Description | Fix suggestion |
| LOW | file:line | Description | Fix suggestion |

Report only confirmed findings, not theoretical risks. If clean, say "No vulnerabilities found."
