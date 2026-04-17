---
name: "code-reviewer"
description: "Code review automation for Java, TypeScript, JavaScript, Python, Go. Analyzes PRs for complexity and risk, checks code quality for SOLID violations and code smells, generates review reports. Use when reviewing pull requests, analyzing code quality, identifying issues, generating review checklists."
---

# Code Reviewer

Automated code review tools for analyzing pull requests, detecting code quality issues, and generating review reports.

Adapted from [alirezarezvani/claude-skills](https://github.com/alirezarezvani/claude-skills) with Java support added.

---

## Tools

### PR Analyzer

Analyzes git diff between branches to assess review complexity and identify risks.

```bash
python3 $CLAUDE_SKILL_DIR/scripts/pr_analyzer.py /path/to/repo
python3 $CLAUDE_SKILL_DIR/scripts/pr_analyzer.py . --base main --head feature-branch
python3 $CLAUDE_SKILL_DIR/scripts/pr_analyzer.py /path/to/repo --json
```

**Detects:** Hardcoded secrets, SQL injection patterns, debug statements, TODO/FIXME, System.out.println in Java

### Code Quality Checker

Analyzes source code for structural issues, code smells, and SOLID violations.

```bash
python3 $CLAUDE_SKILL_DIR/scripts/code_quality_checker.py /path/to/code
python3 $CLAUDE_SKILL_DIR/scripts/code_quality_checker.py . --language java
python3 $CLAUDE_SKILL_DIR/scripts/code_quality_checker.py /path/to/code --json
```

**Detects:** Long functions (>50 lines), large files (>500 lines), god classes (>20 methods), deep nesting (>4 levels), too many parameters (>5), high cyclomatic complexity

### Review Report Generator

Combines PR analysis and code quality findings into structured review reports.

```bash
python3 $CLAUDE_SKILL_DIR/scripts/review_report_generator.py /path/to/repo
python3 $CLAUDE_SKILL_DIR/scripts/review_report_generator.py . --format markdown --output review.md
```

**Verdicts:**

| Score | Verdict |
|-------|---------|
| 90+ with no high issues | Approve |
| 75+ with ≤2 high issues | Approve with suggestions |
| 50-74 | Request changes |
| <50 or critical issues | Block |

---

## Reference Guides

- `references/code_review_checklist.md` — Systematic checklists (pre-review, correctness, security, performance, maintainability, testing)
- `references/coding_standards.md` — Language-specific standards
- `references/common_antipatterns.md` — Antipattern catalog with examples and fixes

---

## jhelm-Specific Review Points

When reviewing jhelm code, also check:

- **Imports**: Never use inline fully-qualified names (PMD: `UnnecessaryFullyQualifiedName`)
- **Logging**: Use `@Slf4j` — no `System.out.println` except CLI output in jhelm-cli
- **Lombok**: Appropriate use of `@Getter/@Setter`, `@Data`, `@Builder`, `@Slf4j`
- **Java 21**: Text blocks, enhanced switch, streams, try-with-resources
- **Module boundaries**: No cross-module dependency violations (gotemplate ← core ← kube ← app)
- **Testing**: JUnit 5 Assertions (not AssertJ), prefer real data over mocks, `@TempDir`
- **Size limits**: Files <500 lines (warn) / <1000 (fail), methods <50 (warn) / <80 (fail)
- **Security**: Path traversal in chart extraction, injection in user inputs
