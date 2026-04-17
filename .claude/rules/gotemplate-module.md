---
globs: jhelm-gotemplate/src/**/*.java
---

# Go Template Module Rules

This module is the pure Go template engine. It must NOT depend on Helm or Sprig.

- No imports from `org.alexmond.jhelm.gotemplate.sprig` or `org.alexmond.jhelm.gotemplate.helm`
- No imports from `org.alexmond.jhelm.core`
- Functions here are Go builtins only (defined in `Functions.GO_BUILTINS`)
- Template functions are registered via ServiceLoader — Sprig (priority 100) and Helm (priority 200) are in separate modules
- Parser node changes: any feature handling FieldNode must also handle ChainNode
