#!/bin/bash
# PostToolUse hook: auto-format Java files after Edit/Write
# Reads tool_name and file_path from stdin JSON

set -euo pipefail

INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || echo "")
FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null || echo "")

# Only run for Edit/Write on Java files
if [[ "$TOOL_NAME" != "Edit" && "$TOOL_NAME" != "Write" ]]; then
    exit 0
fi

if [[ "$FILE_PATH" != *.java ]]; then
    exit 0
fi

cd /Users/alex.mondshain/claude/jhelm

# Determine which module the file belongs to
MODULE=""
case "$FILE_PATH" in
    *jhelm-gotemplate-sprig*) MODULE="jhelm-gotemplate-sprig" ;;
    *jhelm-gotemplate-helm*) MODULE="jhelm-gotemplate-helm" ;;
    *jhelm-gotemplate*) MODULE="jhelm-gotemplate" ;;
    *jhelm-core*) MODULE="jhelm-core" ;;
    *jhelm-kube*) MODULE="jhelm-kube" ;;
    *jhelm-cli*) MODULE="jhelm-cli" ;;
    *jhelm-plugin*) MODULE="jhelm-plugin" ;;
esac

if [[ -n "$MODULE" ]]; then
    # Run formatter on the specific module only (faster)
    ./mvnw spring-javaformat:apply -pl "$MODULE" -q 2>/dev/null || true
    # Fix fully qualified names
    python3 .claude/scripts/fix_fqn.py . 2>/dev/null || true
fi
