#!/bin/bash
# PreToolUse hook: block dangerous operations
# Prevents accidental force-push, hard reset, and destructive file operations

set -euo pipefail

INPUT=$(cat)
TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || echo "")
COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null || echo "")

if [[ "$TOOL_NAME" != "Bash" ]]; then
    exit 0
fi

# Block force-push
if echo "$COMMAND" | grep -qE 'git\s+push\s+.*--force|git\s+push\s+-f\b'; then
    echo "BLOCKED: Force-push detected. Use regular push or ask the user first."
    exit 2
fi

# Block hard reset
if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
    echo "BLOCKED: git reset --hard detected. This discards uncommitted work."
    exit 2
fi

# Block rm -rf on project root or home
if echo "$COMMAND" | grep -qE 'rm\s+-rf\s+(/|~|\$HOME|\./)?\s*$'; then
    echo "BLOCKED: Dangerous rm -rf on broad path."
    exit 2
fi

exit 0
