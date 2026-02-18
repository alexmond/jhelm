---
name: jacoco
description: Check JaCoCo code coverage for jhelm modules
argument-hint: [module]
allowed-tools: Bash(./mvnw *), Bash(python3 *)
---

## Check JaCoCo Code Coverage

Run `verify` to generate coverage reports, then parse and display results.

### Minimum threshold: **80%** line coverage

### Check a specific module (e.g., `/jacoco jhelm-core`)
```bash
./mvnw verify -pl $ARGUMENTS -q
```

### Check all modules
```bash
./mvnw verify -q
```

### Parse coverage report
After running verify, parse the JaCoCo XML report to get coverage percentages:

```python
python3 -c "
import xml.etree.ElementTree as ET
import sys

modules = ['jhelm-gotemplate', 'jhelm-core', 'jhelm-app']
if '$ARGUMENTS':
    modules = ['$ARGUMENTS']

for module in modules:
    try:
        tree = ET.parse(f'{module}/target/site/jacoco/jacoco.xml')
        root = tree.getroot()
        for counter in root.findall('counter'):
            if counter.get('type') == 'LINE':
                missed = int(counter.get('missed'))
                covered = int(counter.get('covered'))
                total = missed + covered
                pct = covered / total * 100 if total > 0 else 0
                status = 'PASS' if pct >= 80 else 'FAIL'
                print(f'{module}: {covered}/{total} = {pct:.2f}% [{status}]')
    except FileNotFoundError:
        print(f'{module}: No coverage report found (run verify first)')
"
```

### Detailed class-level breakdown
To find classes with lowest coverage (useful for targeting improvements):

```python
python3 -c "
import xml.etree.ElementTree as ET

tree = ET.parse('$ARGUMENTS/target/site/jacoco/jacoco.xml')
root = tree.getroot()
results = []
for pkg in root.findall('.//package'):
    for cls in pkg.findall('class'):
        for counter in cls.findall('counter'):
            if counter.get('type') == 'LINE':
                missed = int(counter.get('missed'))
                covered = int(counter.get('covered'))
                total = missed + covered
                if total > 0:
                    pct = covered / total * 100
                    results.append((pct, missed, covered, total, cls.get('name')))
results.sort()
for pct, missed, covered, total, name in results[:20]:
    print(f'{pct:6.1f}% ({covered}/{total}) {name}')
"
```

### Coverage improvement strategy
When coverage is below 80%:
1. Start with classes at 0% coverage that have many lines (biggest impact)
2. Then target classes just below threshold with small gaps
3. Use `@ParameterizedTest` to efficiently cover multiple code paths
4. Use Mockito for external dependencies (KubeService, RepoManager, etc.)
5. Re-run verify after adding tests to confirm improvement
