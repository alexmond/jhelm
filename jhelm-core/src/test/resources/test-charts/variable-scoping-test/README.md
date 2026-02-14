# Variable Scoping Test Chart

This is a comprehensive test chart designed to verify variable scoping and template processing functionality in JHelm.

## Purpose

This chart tests critical template features including:

1. **Variable Assignment** - `{{- $var := value }}`
2. **Variable Access in If Conditions** - `{{- if $var }}`
3. **Variable Scoping in Range Loops** - Access to outer variables inside `range`
4. **Nested Range Loops** - Multiple levels of range iteration with variable access
5. **Conditional Logic** - `if`, `or`, `and`, `not` with various data types
6. **With Blocks** - Variable access inside `with` contexts
7. **Subchart Integration** - Value isolation and global value propagation

## Bug History

### Bug #1: isTrue() Method Not Handling Maps (Fixed)
**Issue**: The `Executor.isTrue()` method only checked Boolean and CharSequence types, returning `false` for all other types including Maps, Collections, and complex objects.

**Symptom**: Template conditions like `{{- if .Values.scrapeConfigs }}` would always evaluate to `false` even when the map existed and was non-empty.

**Root Cause**: `Executor.java` had its own private `isTrue()` method that was incomplete:
```java
// BUGGY CODE
private boolean isTrue(Object value) {
    if (value instanceof Boolean) return (Boolean) value;
    if (value instanceof CharSequence) return ((CharSequence) value).length() != 0;
    return false;  // ← BUG: Always returns false for Maps!
}
```

**Fix**: Changed Executor to delegate to `Functions.isTrue()` which correctly handles all types:
```java
// FIXED CODE
private boolean isTrue(Object value) {
    return Functions.isTrue(value);
}
```

**Impact**: This was a critical bug that prevented charts like prometheus-community/prometheus from rendering correctly. The chart's `scrape_configs` section was completely missing because the condition `{{- if or $root.Values.scrapeConfigs ... }}` always evaluated to false.

## Test Coverage

### Main Chart Tests (`configmap.yaml`)

- **test-root-variable.txt**: Basic variable assignment and access
- **test-range-variable.txt**: Variable access inside range loop
- **test-nested-range.txt**: Nested range loops with outer variable access
- **test-if-map.txt**: If condition directly on map value
- **test-if-assigned-variable.txt**: If condition on assigned variable
- **test-if-list.txt**: If condition on list value
- **test-or-condition.txt**: OR logic with multiple maps
- **test-and-condition.txt**: AND logic with boolean and map
- **test-not-empty.txt**: NOT and empty() function combination
- **test-complex-variable.txt**: Complex nested access with filtering
- **test-with-variable.txt**: Variable access inside with block
- **test-range-index-value.txt**: Range with index and global value access

### Subchart Tests (`charts/subchart/templates/configmap.yaml`)

- **subchart-values.txt**: Access to subchart's own values
- **global-values.txt**: Access to global values from parent chart
- **subchart-variable-scoping.txt**: Variable scoping within subchart
- **subchart-isolation.txt**: Verification that parent values are isolated

## Usage

Run the test:
```bash
mvn test -Dtest=VariableScopingTest
```

The test verifies that all conditions evaluate correctly and that the rendered manifest contains the expected output markers.

## Extending This Chart

To add new test cases:

1. Add a new data entry to `templates/configmap.yaml`
2. Add corresponding assertions in `VariableScopingTest.java`
3. Document the new test case in this README

## Related Issues

- **prometheus-community/prometheus chart**: Missing scrape_configs section (9 resource differences → 2 after fix)
- **Variable scoping bugs**: This chart serves as regression prevention for future variable-related issues
