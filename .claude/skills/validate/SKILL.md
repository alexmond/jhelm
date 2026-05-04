---
name: validate
description: Auto-format, fix violations, and run Maven validate (PMD + Checkstyle) in one step
argument-hint: [module]
allowed-tools: Bash(./mvnw *), Bash(python3 *)
---

## Format → Fix → Validate

Quick code quality check. `$ARGUMENTS` is an optional module name.

### Step 1: Auto-format

```bash
./mvnw spring-javaformat:apply $( [ -n "$ARGUMENTS" ] && echo "-pl $ARGUMENTS" )
```

### Step 2: Fix common violations

```bash
python3 .claude/scripts/fix_fqn.py .
python3 .claude/scripts/fix_violations.py .
```

### Step 3: Validate (PMD + Checkstyle)

```bash
./mvnw validate $( [ -n "$ARGUMENTS" ] && echo "-pl $ARGUMENTS" ) 2>&1 | grep -E "^\[ERROR\]|violations|PMD Failure"
```

### Report

| Check | Result |
|-------|--------|
| Format | Applied / Already clean |
| FQN fix | N files fixed / Clean |
| Violations fix | N files fixed / Clean |
| PMD | 0 violations / N violations |
| Checkstyle | 0 violations / N violations |

If violations remain after auto-fix, list them and suggest manual fixes per the `/checkstyle` skill.
