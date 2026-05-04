---
name: checkstyle
description: Check and fix Checkstyle violations in jhelm modules
argument-hint: [module]
allowed-tools: Bash(./mvnw *), Bash(python3 *)
---

## Check and Fix Checkstyle Violations

### Configuration

- **Plugin**: `maven-checkstyle-plugin` 3.6.0 with `spring-javaformat-checkstyle` 0.0.43
- **Suppressions**: `checkstyle-suppressions.xml` in project root
- **Violations fail the build** at `validate` phase (before compile)

### Step 1: Check violations

**All modules**:
```bash
./mvnw validate 2>&1 | grep -E "violations|ERROR.*\.java"
```

**Specific module** (e.g., `/checkstyle jhelm-core`):
```bash
./mvnw validate -pl $ARGUMENTS 2>&1 | grep "^\[ERROR\]"
```

### Step 2: Auto-format first

Always run `spring-javaformat:apply` before checking violations — it fixes indentation/spacing automatically:
```bash
./mvnw spring-javaformat:apply
```

### Step 3: Fix remaining violations with Python script

Run the persistent fix script:

```bash
python3 .claude/scripts/fix_violations.py .
```

This fixes: SpringCatch (`e` → `ex`), NeedBraces, SpringLambda (parens + block collapse), SpringTernary, and AvoidStarImport expansion.

### Step 4: Handle special violations manually

| Violation | Fix |
|-----------|-----|
| `InnerTypeLast` | Move inner classes AFTER all methods |
| `SpringHideUtilityClassConstructor` | Add `private Constructor() {}` AND make class `final` |
| `FinalClass` | Add `final` to class declaration |
| `NestedIfDepth > 3` | Extract helper method or add to `checkstyle-suppressions.xml` |
| `SpringMethodVisibility` (interface impl) | Add suppression to `checkstyle-suppressions.xml` |
| `AnnotationUseStyle` trailing comma | Remove `,` before `})` in `@CsvSource` etc. |

### Step 5: Re-format and validate

```bash
./mvnw spring-javaformat:apply && ./mvnw validate
```

### Critical Pitfalls

1. **Lambda parens vs switch patterns**: `case Boolean b -> b` — do NOT add parens to switch pattern variables. Check if preceded by uppercase type name or `case` keyword.
2. **Lambda parens vs switch default**: `default -> value` — do NOT add parens. `default` is a Java keyword.
3. **Ternary in lambda body**: `(args) -> condition ? a : b` — the `->` arrow is a boundary; the condition starts AFTER `->`. Do NOT wrap the lambda params in the ternary condition.
4. **`==` vs `=` in find_condition_start**: When scanning backward for ternary condition start, `==` at position `i` has `line[i-1]='='` — skip it. AND `line[i+1]='='` also means it's `==`, skip.
5. **Star import for `removeIf(r ->`)**: `r` is preceded by `(` from the method call, NOT from lambda parens. Check if char AFTER identifier is `)` to detect already-parenthesized.
6. **Missing imports after star import expansion**: Manually check for `Base64`, `BufferedInputStream`, `VersionInfo`, etc. that may not be in the predefined class lists.

### Suppressions file (`checkstyle-suppressions.xml`)

Located at project root. Current suppressions:
- `SpringHeader`, `SpringTestFileName`, `JavadocPackage` — project conventions differ
- `RegexpSinglelineJava` — JUnit 5 assertions used instead of AssertJ
- `SpringImportOrder` — handled by `spring-javaformat:apply`
- `RequireThis` — not enforced in this project
- All `Javadoc*` and `SpringJavadoc` — not required
- `SpringMethodVisibility` for `KpsComparisonTest.java` — TrustManager interface requires public
- `NestedIfDepth` for `RepoManager.java` — intentional deep YAML parsing
