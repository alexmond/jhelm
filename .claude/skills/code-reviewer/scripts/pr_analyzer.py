#!/usr/bin/env python3
"""PR diff analyzer that categorizes changed files by risk level and detects risky patterns.

Uses git diff to analyze changes between branches, assigns risk levels,
detects dangerous patterns, and suggests review order.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

# File risk categories based on path patterns
SECURITY_CRITICAL_PATTERNS = [
	r"auth", r"security", r"crypto", r"password", r"secret",
	r"token", r"credential", r"permission", r"acl", r"rbac",
	r"certificate", r"keystore", r"truststore", r"pgp", r"gpg",
]

CORE_INFRASTRUCTURE_PATTERNS = [
	r"pom\.xml$", r"build\.gradle", r"package\.json$", r"go\.mod$",
	r"Dockerfile", r"docker-compose", r"\.github/workflows",
	r"\.ci/", r"Makefile", r"settings\.xml",
	r"application\.ya?ml$", r"application\.properties$",
	r"src/main/resources/META-INF",
]

TEST_DOC_PATTERNS = [
	r"src/test/", r"__tests__/", r"_test\.go$", r"\.test\.",
	r"\.spec\.", r"Test\.java$", r"Tests\.java$",
	r"\.md$", r"\.adoc$", r"\.rst$", r"docs?/", r"README",
	r"CHANGELOG", r"LICENSE",
]

# Risky patterns to detect in added lines
RISKY_PATTERNS = {
	"hardcoded_secret": {
		"pattern": re.compile(
			r"""(?:password|secret|token|api_key|apikey|api[-_]?secret|private[-_]?key)\s*[=:]\s*["'][^"']{4,}["']""",
			re.IGNORECASE,
		),
		"severity": "critical",
		"message": "Possible hardcoded secret or credential",
	},
	"sql_injection": {
		"pattern": re.compile(
			r"""(?:execute|query|prepare)\s*\(\s*["'].*\+|String\.format\s*\(\s*["'](?:SELECT|INSERT|UPDATE|DELETE|DROP)""",
			re.IGNORECASE,
		),
		"severity": "critical",
		"message": "Potential SQL injection - string concatenation in query",
	},
	"system_out_println": {
		"pattern": re.compile(r"System\.(out|err)\.(println|print|printf)\s*\("),
		"severity": "warning",
		"message": "System.out/err usage - use a logging framework instead",
	},
	"debugger_statement": {
		"pattern": re.compile(r"\bdebugger\b|\.pdb\.set_trace\(\)|breakpoint\(\)"),
		"severity": "error",
		"message": "Debugger statement left in code",
	},
	"todo_fixme": {
		"pattern": re.compile(r"\b(TODO|FIXME|HACK|XXX|WORKAROUND)\b", re.IGNORECASE),
		"severity": "info",
		"message": "TODO/FIXME comment found",
	},
	"console_log": {
		"pattern": re.compile(r"\bconsole\.(log|debug|info|warn|error)\s*\("),
		"severity": "warning",
		"message": "Console logging left in code",
	},
	"eval_usage": {
		"pattern": re.compile(r"\beval\s*\("),
		"severity": "critical",
		"message": "eval() usage detected - potential code injection risk",
	},
	"disabled_test": {
		"pattern": re.compile(r"@Disabled|@Ignore|\.skip\(|xit\(|xdescribe\("),
		"severity": "warning",
		"message": "Disabled/skipped test detected",
	},
	"exception_swallowing": {
		"pattern": re.compile(r"catch\s*\([^)]*\)\s*\{\s*\}"),
		"severity": "warning",
		"message": "Empty catch block - exception may be swallowed",
	},
}


@dataclass
class ChangedFile:
	path: str
	status: str  # A=added, M=modified, D=deleted, R=renamed
	additions: int = 0
	deletions: int = 0
	risk_category: str = "business_logic"
	risk_level: int = 1  # 1-5
	risky_findings: list = field(default_factory=list)


@dataclass
class RiskyFinding:
	pattern_name: str
	severity: str
	message: str
	line_content: str
	file: str
	line_number: Optional[int] = None


@dataclass
class PRAnalysis:
	base: str
	head: str
	total_files: int = 0
	total_additions: int = 0
	total_deletions: int = 0
	files: list = field(default_factory=list)
	risky_findings: list = field(default_factory=list)
	complexity_score: int = 1
	review_order: list = field(default_factory=list)


def run_git(args, cwd=None):
	"""Run a git command and return stdout."""
	cmd = ["git"] + args
	try:
		result = subprocess.run(
			cmd,
			capture_output=True,
			text=True,
			cwd=cwd,
			timeout=30,
		)
		if result.returncode != 0:
			print(f"Warning: git command failed: {' '.join(cmd)}", file=sys.stderr)
			print(f"  stderr: {result.stderr.strip()}", file=sys.stderr)
			return ""
		return result.stdout
	except (subprocess.TimeoutExpired, FileNotFoundError) as e:
		print(f"Error running git: {e}", file=sys.stderr)
		return ""


def categorize_file(filepath):
	"""Categorize a file by risk level."""
	path_lower = filepath.lower()

	# Check security-critical
	for pattern in SECURITY_CRITICAL_PATTERNS:
		if re.search(pattern, path_lower):
			return "security_critical", 5

	# Check core infrastructure
	for pattern in CORE_INFRASTRUCTURE_PATTERNS:
		if re.search(pattern, filepath):
			return "core_infrastructure", 4

	# Check tests/docs
	for pattern in TEST_DOC_PATTERNS:
		if re.search(pattern, filepath):
			return "tests_docs", 1

	# Default: business logic
	return "business_logic", 3


def parse_diff_stat(base, head, cwd=None):
	"""Parse git diff --numstat to get per-file addition/deletion counts."""
	output = run_git(["diff", "--numstat", f"{base}...{head}"], cwd=cwd)
	stats = {}
	for line in output.strip().splitlines():
		if not line.strip():
			continue
		parts = line.split("\t")
		if len(parts) >= 3:
			adds = int(parts[0]) if parts[0] != "-" else 0
			dels = int(parts[1]) if parts[1] != "-" else 0
			filepath = parts[2]
			# Handle renames: old => new
			if " => " in filepath:
				filepath = filepath.split(" => ")[-1].rstrip("}")
				# Handle {old => new} format
				if "{" in parts[2]:
					prefix = parts[2].split("{")[0]
					suffix = filepath
					filepath = prefix + suffix
			stats[filepath] = (adds, dels)
	return stats


def get_changed_files(base, head, cwd=None):
	"""Get list of changed files between base and head."""
	output = run_git(["diff", "--name-status", f"{base}...{head}"], cwd=cwd)
	files = []
	stats = parse_diff_stat(base, head, cwd=cwd)

	for line in output.strip().splitlines():
		if not line.strip():
			continue
		parts = line.split("\t")
		if len(parts) < 2:
			continue

		status = parts[0][0]  # First char: A, M, D, R
		filepath = parts[-1]  # Last part is the new name for renames

		category, risk = categorize_file(filepath)
		adds, dels = stats.get(filepath, (0, 0))

		files.append(ChangedFile(
			path=filepath,
			status=status,
			additions=adds,
			deletions=dels,
			risk_category=category,
			risk_level=risk,
		))

	return files


def scan_added_lines(base, head, cwd=None):
	"""Scan added lines in the diff for risky patterns."""
	output = run_git(["diff", "-U0", f"{base}...{head}"], cwd=cwd)
	findings = []
	current_file = None
	current_line_num = 0

	for line in output.splitlines():
		# Track current file
		if line.startswith("+++ b/"):
			current_file = line[6:]
			continue

		# Track line numbers from hunk headers
		hunk_match = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", line)
		if hunk_match:
			current_line_num = int(hunk_match.group(1))
			continue

		# Only check added lines (not hunk headers or context)
		if line.startswith("+") and not line.startswith("+++"):
			added_content = line[1:]  # Remove the leading +

			for name, info in RISKY_PATTERNS.items():
				if info["pattern"].search(added_content):
					findings.append(RiskyFinding(
						pattern_name=name,
						severity=info["severity"],
						message=info["message"],
						line_content=added_content.strip(),
						file=current_file or "<unknown>",
						line_number=current_line_num,
					))

			current_line_num += 1

	return findings


def calculate_complexity_score(analysis):
	"""Calculate overall PR complexity score (1-10)."""
	score = 1

	# File count factor
	if analysis.total_files > 20:
		score += 3
	elif analysis.total_files > 10:
		score += 2
	elif analysis.total_files > 5:
		score += 1

	# Change size factor
	total_changes = analysis.total_additions + analysis.total_deletions
	if total_changes > 1000:
		score += 3
	elif total_changes > 500:
		score += 2
	elif total_changes > 100:
		score += 1

	# Risk factor
	critical_files = sum(1 for f in analysis.files if f.risk_category == "security_critical")
	infra_files = sum(1 for f in analysis.files if f.risk_category == "core_infrastructure")
	if critical_files > 0:
		score += 2
	if infra_files > 0:
		score += 1

	# Risky findings factor
	critical_findings = sum(1 for f in analysis.risky_findings if f.severity == "critical")
	if critical_findings > 0:
		score += 1

	return min(score, 10)


def determine_review_order(files):
	"""Sort files by review priority (high-risk first)."""
	def sort_key(f):
		# Higher risk_level = review first, then by change size
		return (-f.risk_level, -(f.additions + f.deletions))

	return sorted(files, key=sort_key)


def analyze_pr(base="main", head="HEAD", cwd=None):
	"""Run full PR analysis."""
	analysis = PRAnalysis(base=base, head=head)

	# Get changed files
	analysis.files = get_changed_files(base, head, cwd=cwd)
	analysis.total_files = len(analysis.files)
	analysis.total_additions = sum(f.additions for f in analysis.files)
	analysis.total_deletions = sum(f.deletions for f in analysis.files)

	# Scan for risky patterns
	analysis.risky_findings = scan_added_lines(base, head, cwd=cwd)

	# Attach findings to files
	findings_by_file = {}
	for finding in analysis.risky_findings:
		findings_by_file.setdefault(finding.file, []).append(finding)
	for f in analysis.files:
		f.risky_findings = findings_by_file.get(f.path, [])
		# Boost risk level if file has critical findings
		if any(r.severity == "critical" for r in f.risky_findings):
			f.risk_level = max(f.risk_level, 4)

	# Calculate complexity
	analysis.complexity_score = calculate_complexity_score(analysis)

	# Determine review order
	analysis.review_order = [f.path for f in determine_review_order(analysis.files)]

	return analysis


def analysis_to_dict(analysis):
	"""Convert PRAnalysis to a dictionary."""
	return {
		"base": analysis.base,
		"head": analysis.head,
		"total_files": analysis.total_files,
		"total_additions": analysis.total_additions,
		"total_deletions": analysis.total_deletions,
		"complexity_score": analysis.complexity_score,
		"review_order": analysis.review_order,
		"files": [
			{
				"path": f.path,
				"status": f.status,
				"additions": f.additions,
				"deletions": f.deletions,
				"risk_category": f.risk_category,
				"risk_level": f.risk_level,
				"risky_findings": [
					{
						"pattern": r.pattern_name,
						"severity": r.severity,
						"message": r.message,
						"line_content": r.line_content,
						"line_number": r.line_number,
					}
					for r in f.risky_findings
				],
			}
			for f in analysis.files
		],
		"risky_findings": [
			{
				"pattern": r.pattern_name,
				"severity": r.severity,
				"message": r.message,
				"line_content": r.line_content,
				"file": r.file,
				"line_number": r.line_number,
			}
			for r in analysis.risky_findings
		],
	}


def print_report(analysis):
	"""Print a human-readable PR analysis report."""
	print("=" * 70)
	print("PR ANALYSIS REPORT")
	print("=" * 70)
	print()
	print(f"  Base: {analysis.base}")
	print(f"  Head: {analysis.head}")
	print(f"  Files changed: {analysis.total_files}")
	print(f"  Additions: +{analysis.total_additions}")
	print(f"  Deletions: -{analysis.total_deletions}")
	print(f"  Complexity score: {analysis.complexity_score}/10")
	print()

	# Group files by category
	categories = {}
	for f in analysis.files:
		categories.setdefault(f.risk_category, []).append(f)

	category_labels = {
		"security_critical": "SECURITY-CRITICAL",
		"core_infrastructure": "CORE INFRASTRUCTURE",
		"business_logic": "BUSINESS LOGIC",
		"tests_docs": "TESTS / DOCS",
	}

	print("--- Files by Risk Category ---")
	for cat in ["security_critical", "core_infrastructure", "business_logic", "tests_docs"]:
		files = categories.get(cat, [])
		if not files:
			continue
		label = category_labels.get(cat, cat)
		print(f"\n  [{label}]")
		for f in files:
			status_label = {"A": "added", "M": "modified", "D": "deleted", "R": "renamed"}.get(f.status, f.status)
			print(f"    {f.path} ({status_label}, +{f.additions}/-{f.deletions})")

	print()

	# Risky findings
	if analysis.risky_findings:
		print("--- Risky Patterns Detected ---")
		for finding in analysis.risky_findings:
			severity_icon = {
				"critical": "[!!!]",
				"error": "[!!]",
				"warning": "[!]",
				"info": "[i]",
			}.get(finding.severity, "[?]")
			print(f"  {severity_icon} {finding.message}")
			print(f"       File: {finding.file}:{finding.line_number}")
			print(f"       Code: {finding.line_content[:80]}")
			print()
	else:
		print("--- No risky patterns detected ---")
		print()

	# Review order
	print("--- Suggested Review Order ---")
	for i, path in enumerate(analysis.review_order, 1):
		print(f"  {i}. {path}")
	print()


def main():
	parser = argparse.ArgumentParser(
		description="Analyze a PR diff for risk, complexity, and risky patterns."
	)
	parser.add_argument(
		"--base",
		default="main",
		help="Base branch to compare against (default: main)",
	)
	parser.add_argument(
		"--head",
		default="HEAD",
		help="Head ref to analyze (default: HEAD)",
	)
	parser.add_argument(
		"--cwd",
		default=None,
		help="Working directory for git commands",
	)
	parser.add_argument(
		"--json",
		action="store_true",
		help="Output results as JSON",
	)

	args = parser.parse_args()

	analysis = analyze_pr(base=args.base, head=args.head, cwd=args.cwd)

	if args.json:
		print(json.dumps(analysis_to_dict(analysis), indent=2))
	else:
		print_report(analysis)


if __name__ == "__main__":
	main()
