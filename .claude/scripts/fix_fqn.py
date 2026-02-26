#!/usr/bin/env python3
"""
Fix fully qualified class names (FQN) used inline in Java source files.
Replaces inline FQN usages with simple class names and adds missing imports.

Usage: python3 fix_fqn.py [root_dir]
Default root_dir: current working directory

Saved at: .claude/scripts/fix_fqn.py
"""

import os
import re
import sys

# FQN patterns to fix: (fqn_pattern, simple_name, import_line)
# Each entry: (regex to match inline usage, replacement, full import statement)
FQN_RULES = [
    # java.util - common collections (often already imported)
    ("java.util.Map", "Map", "import java.util.Map;"),
    ("java.util.List", "List", "import java.util.List;"),
    ("java.util.Set", "Set", "import java.util.Set;"),
    ("java.util.Collection", "Collection", "import java.util.Collection;"),
    ("java.util.ArrayList", "ArrayList", "import java.util.ArrayList;"),
    ("java.util.HashMap", "HashMap", "import java.util.HashMap;"),
    ("java.util.LinkedHashMap", "LinkedHashMap", "import java.util.LinkedHashMap;"),
    ("java.util.HashSet", "HashSet", "import java.util.HashSet;"),
    ("java.util.Arrays", "Arrays", "import java.util.Arrays;"),
    ("java.util.Collections", "Collections", "import java.util.Collections;"),
    ("java.util.Optional", "Optional", "import java.util.Optional;"),
    ("java.util.Objects", "Objects", "import java.util.Objects;"),
    ("java.util.Iterator", "Iterator", "import java.util.Iterator;"),
    ("java.util.Locale.ROOT", "Locale.ROOT", "import java.util.Locale;"),
    ("java.util.Locale", "Locale", "import java.util.Locale;"),
    ("java.util.Base64", "Base64", "import java.util.Base64;"),
    ("java.util.regex.Pattern", "Pattern", "import java.util.regex.Pattern;"),
    ("java.util.concurrent.TimeUnit", "TimeUnit", "import java.util.concurrent.TimeUnit;"),
    ("java.util.concurrent.CompletionException", "CompletionException", "import java.util.concurrent.CompletionException;"),
    # java.nio
    ("java.nio.charset.StandardCharsets", "StandardCharsets", "import java.nio.charset.StandardCharsets;"),
    # java.io
    ("java.io.ByteArrayInputStream", "ByteArrayInputStream", "import java.io.ByteArrayInputStream;"),
    ("java.io.InputStream", "InputStream", "import java.io.InputStream;"),
    ("java.io.Writer", "Writer", "import java.io.Writer;"),
    # java.net
    ("java.net.SocketException", "SocketException", "import java.net.SocketException;"),
    ("java.net.ConnectException", "ConnectException", "import java.net.ConnectException;"),
    ("java.net.URL", "URL", "import java.net.URL;"),
    # java.time
    ("java.time.Duration", "Duration", "import java.time.Duration;"),
    ("java.time.OffsetDateTime", "OffsetDateTime", "import java.time.OffsetDateTime;"),
    # java.security
    ("java.security.SecureRandom", "SecureRandom", "import java.security.SecureRandom;"),
    # java.lang.reflect
    ("java.lang.reflect.Array", "Array", "import java.lang.reflect.Array;"),
    # javax.crypto
    ("javax.crypto.spec.SecretKeySpec", "SecretKeySpec", "import javax.crypto.spec.SecretKeySpec;"),
    ("javax.crypto.Mac", "Mac", "import javax.crypto.Mac;"),
    # org.apache
    ("org.apache.commons.codec.binary.Hex", "Hex", "import org.apache.commons.codec.binary.Hex;"),
    ("org.apache.hc.core5.http.Header", "Header", "import org.apache.hc.core5.http.Header;"),
    # org.springframework.retry
    ("org.springframework.retry.RetryListener", "RetryListener", "import org.springframework.retry.RetryListener;"),
    ("org.springframework.retry.RetryContext", "RetryContext", "import org.springframework.retry.RetryContext;"),
    ("org.springframework.retry.RetryCallback", "RetryCallback", "import org.springframework.retry.RetryCallback;"),
    # org.junit / org.mockito (annotations and static calls)
    ("org.junit.jupiter.api.AfterEach", "AfterEach", "import org.junit.jupiter.api.AfterEach;"),
    ("org.mockito.Mockito.never", "Mockito.never", "import org.mockito.Mockito;"),
]


def find_java_files(root_dir):
    """Find all .java files under src/ directories."""
    java_files = []
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if f.endswith(".java") and "/src/" in os.path.join(dirpath, f):
                java_files.append(os.path.join(dirpath, f))
    return java_files


def get_import_section_end(lines):
    """Find the line index after the last import statement."""
    last_import = -1
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("import "):
            last_import = i
    return last_import


def has_import(lines, import_stmt):
    """Check if an import already exists."""
    clean = import_stmt.strip().rstrip(";")
    for line in lines:
        if line.strip().rstrip(";") == clean:
            return True
    return False


def is_in_import_line(line):
    """Check if a line is an import statement."""
    return line.strip().startswith("import ")


def is_in_string_literal(line, pos):
    """Rough check if position is inside a string literal."""
    in_string = False
    i = 0
    while i < pos and i < len(line):
        if line[i] == '"' and (i == 0 or line[i-1] != '\\'):
            in_string = not in_string
        i += 1
    return in_string


def is_in_comment(line, pos):
    """Rough check if position is inside a line comment."""
    comment_start = line.find("//")
    if comment_start >= 0 and pos > comment_start:
        return True
    return False


def process_file(filepath):
    """Process a single Java file, replacing FQN with imports."""
    with open(filepath, "r") as f:
        content = f.read()

    lines = content.split("\n")
    imports_to_add = set()
    changes_made = False

    for fqn, simple, import_line in FQN_RULES:
        # Skip if FQN not present in file at all
        if fqn not in content:
            continue

        new_lines = []
        for line in lines:
            if is_in_import_line(line):
                new_lines.append(line)
                continue

            if fqn not in line:
                new_lines.append(line)
                continue

            # Check each occurrence
            new_line = line
            idx = 0
            while True:
                pos = new_line.find(fqn, idx)
                if pos < 0:
                    break
                # Skip if in string literal or comment
                if is_in_string_literal(new_line, pos) or is_in_comment(new_line, pos):
                    idx = pos + len(fqn)
                    continue

                # Replace FQN with simple name
                new_line = new_line[:pos] + simple + new_line[pos + len(fqn):]
                idx = pos + len(simple)
                imports_to_add.add(import_line)
                changes_made = True

            new_lines.append(new_line)

        lines = new_lines

    if not changes_made:
        return False

    # Add missing imports
    imports_actually_needed = set()
    for imp in imports_to_add:
        if not has_import(lines, imp):
            imports_actually_needed.add(imp)

    if imports_actually_needed:
        last_import_idx = get_import_section_end(lines)
        if last_import_idx >= 0:
            # Insert after last import
            for imp in sorted(imports_actually_needed):
                last_import_idx += 1
                lines.insert(last_import_idx, imp)
        else:
            # No imports yet - find package line and add after it
            for i, line in enumerate(lines):
                if line.strip().startswith("package "):
                    lines.insert(i + 1, "")
                    for j, imp in enumerate(sorted(imports_actually_needed)):
                        lines.insert(i + 2 + j, imp)
                    break

    with open(filepath, "w") as f:
        f.write("\n".join(lines))

    return True


def main():
    root_dir = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    java_files = find_java_files(root_dir)
    total_fixed = 0

    for filepath in java_files:
        if process_file(filepath):
            rel = os.path.relpath(filepath, root_dir)
            print(f"  Fixed: {rel}")
            total_fixed += 1

    print(f"\nDone. Fixed {total_fixed} files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
