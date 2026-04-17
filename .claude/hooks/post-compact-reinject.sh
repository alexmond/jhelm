#!/bin/bash
# PostCompact hook: re-inject critical project context after context compaction
cat <<'CONTEXT'
## jhelm Project Quick Reference (re-injected after compaction)

**Modules:** jhelm-gotemplate → jhelm-core → jhelm-kube → jhelm-app
**Build:** `./mvnw clean install` | **Test:** `./mvnw test -pl <module>`
**Format:** `./mvnw spring-javaformat:apply` | **FQN fix:** `python3 .claude/scripts/fix_fqn.py .`
**Validate:** `./mvnw validate` (runs PMD + Checkstyle — both fail the build)

**Style:** Tabs, imports (never inline FQN), @Slf4j for logging, Lombok annotations
**Testing:** JUnit 5 Assertions, prefer real data over mocks, @TempDir for temp files
**Coverage:** JaCoCo minimum 80%
**Git:** Skill-only changes → commit to main; Source changes → feature branch + PR via /push
**Labels:** bug/enhancement + phase:1-5 + priority:P1/P2
CONTEXT
