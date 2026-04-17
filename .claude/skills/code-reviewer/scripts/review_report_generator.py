#!/usr/bin/env python3
"""Review report generator that combines PR analysis and code quality checks.

Runs pr_analyzer.py and code_quality_checker.py, combines their findings,
calculates a score (0-100) and verdict, and generates a prioritized report.
"""

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional

SCRIPT_DIR = Path(__file__).parent.resolve()

VERDICTS = {
	"approve": {"min_score": 80, "label": "APPROVE", "description": "Code looks good, no significant issues."},
	"approve_with_suggestions": {"min_score": 60, "label": "APPROVE WITH SUGGESTIONS", "description": "Generally acceptable, but some improvements recommended."},
	"request_changes": {"min_score": 30, "label": "REQUEST CHANGES", "description": "Issues found that should be addressed before merging."},
	"block": {"min_score": 0, "label": "BLOCK", "description": "Critical issues detected. Do not merge."},
}


@dataclass
class ActionItem:
	priority: int  # 1=highest
	category: str
	message: str
	file: Optional[str] = None
	line: Optional[int] = None
	severity: str = "warning"


@dataclass
class ReviewReport:
	score: int = 100
	verdict: str = "approve"
	pr_analysis: dict = field(default_factory=dict)
	quality_analysis: dict = field(default_factory=dict)
	action_items: list = field(default_factory=list)
	timestamp: str = ""


def run_script(script_name, args=None):
	"""Run a sibling script and capture JSON output."""
	script_path = SCRIPT_DIR / script_name
	cmd = [sys.executable, str(script_path), "--json"]
	if args:
		cmd.extend(args)

	try:
		result = subprocess.run(
			cmd,
			capture_output=True,
			text=True,
			timeout=120,
		)
		if result.returncode != 0:
			print(f"Warning: {script_name} exited with code {result.returncode}", file=sys.stderr)
			if result.stderr:
				print(f"  stderr: {result.stderr.strip()}", file=sys.stderr)
			return {}
		return json.loads(result.stdout) if result.stdout.strip() else {}
	except subprocess.TimeoutExpired:
		print(f"Warning: {script_name} timed out", file=sys.stderr)
		return {}
	except json.JSONDecodeError as e:
		print(f"Warning: {script_name} produced invalid JSON: {e}", file=sys.stderr)
		return {}
	except FileNotFoundError:
		print(f"Error: {script_name} not found at {script_path}", file=sys.stderr)
		return {}


def calculate_score(pr_data, quality_data):
	"""Calculate overall review score (0-100) from PR and quality analyses."""
	score = 100
	deductions = []

	# --- PR Analysis deductions ---

	# Complexity penalty (max -20)
	complexity = pr_data.get("complexity_score", 1)
	if complexity >= 8:
		penalty = 20
	elif complexity >= 6:
		penalty = 15
	elif complexity >= 4:
		penalty = 10
	elif complexity >= 3:
		penalty = 5
	else:
		penalty = 0
	if penalty:
		deductions.append(("high_complexity", penalty))

	# Risky findings penalties
	findings = pr_data.get("risky_findings", [])
	critical_count = sum(1 for f in findings if f.get("severity") == "critical")
	error_count = sum(1 for f in findings if f.get("severity") == "error")
	warning_count = sum(1 for f in findings if f.get("severity") == "warning")

	if critical_count:
		deductions.append(("critical_findings", min(critical_count * 15, 40)))
	if error_count:
		deductions.append(("error_findings", min(error_count * 8, 20)))
	if warning_count:
		deductions.append(("warning_findings", min(warning_count * 3, 15)))

	# Large PR penalty (max -10)
	total_changes = pr_data.get("total_additions", 0) + pr_data.get("total_deletions", 0)
	if total_changes > 1000:
		deductions.append(("large_pr", 10))
	elif total_changes > 500:
		deductions.append(("large_pr", 5))

	# --- Quality Analysis deductions ---

	summary = quality_data.get("summary", {})
	total_smells = summary.get("total_smells", 0)
	total_violations = summary.get("total_violations", 0)

	if total_smells > 10:
		deductions.append(("many_code_smells", min(total_smells * 2, 20)))
	elif total_smells > 5:
		deductions.append(("code_smells", min(total_smells, 10)))
	elif total_smells > 0:
		deductions.append(("minor_smells", min(total_smells, 5)))

	if total_violations > 5:
		deductions.append(("solid_violations", min(total_violations * 3, 15)))
	elif total_violations > 0:
		deductions.append(("solid_violations", min(total_violations * 2, 10)))

	# Per-file quality issues (check for error-severity smells)
	for file_data in quality_data.get("files", []):
		for smell in file_data.get("smells", []):
			if smell.get("severity") == "error":
				deductions.append(("error_smell_" + file_data["path"], 5))

	# Apply deductions
	total_deduction = sum(d[1] for d in deductions)
	score = max(0, score - total_deduction)

	return score


def determine_verdict(score):
	"""Determine review verdict based on score."""
	if score >= 80:
		return "approve"
	elif score >= 60:
		return "approve_with_suggestions"
	elif score >= 30:
		return "request_changes"
	else:
		return "block"


def generate_action_items(pr_data, quality_data):
	"""Generate prioritized action items from analysis results."""
	items = []
	priority = 1

	# Critical risky findings first
	for finding in pr_data.get("risky_findings", []):
		if finding.get("severity") == "critical":
			items.append(ActionItem(
				priority=priority,
				category="security",
				message=finding["message"],
				file=finding.get("file"),
				line=finding.get("line_number"),
				severity="critical",
			))
			priority += 1

	# Error-level risky findings
	for finding in pr_data.get("risky_findings", []):
		if finding.get("severity") == "error":
			items.append(ActionItem(
				priority=priority,
				category="bug_risk",
				message=finding["message"],
				file=finding.get("file"),
				line=finding.get("line_number"),
				severity="error",
			))
			priority += 1

	# Error-level code smells
	for file_data in quality_data.get("files", []):
		for smell in file_data.get("smells", []):
			if smell.get("severity") == "error":
				items.append(ActionItem(
					priority=priority,
					category="code_quality",
					message=smell["message"],
					file=smell.get("file"),
					line=smell.get("line"),
					severity="error",
				))
				priority += 1

	# SOLID violations
	for file_data in quality_data.get("files", []):
		for violation in file_data.get("solid_violations", []):
			items.append(ActionItem(
				priority=priority,
				category="design",
				message=violation["message"],
				file=violation.get("file"),
				severity="warning",
			))
			priority += 1

	# Warning-level code smells
	for file_data in quality_data.get("files", []):
		for smell in file_data.get("smells", []):
			if smell.get("severity") == "warning":
				items.append(ActionItem(
					priority=priority,
					category="code_quality",
					message=smell["message"],
					file=smell.get("file"),
					line=smell.get("line"),
					severity="warning",
				))
				priority += 1

	# Warning-level risky findings
	for finding in pr_data.get("risky_findings", []):
		if finding.get("severity") == "warning":
			items.append(ActionItem(
				priority=priority,
				category="cleanup",
				message=finding["message"],
				file=finding.get("file"),
				line=finding.get("line_number"),
				severity="warning",
			))
			priority += 1

	# Info-level findings last
	for finding in pr_data.get("risky_findings", []):
		if finding.get("severity") == "info":
			items.append(ActionItem(
				priority=priority,
				category="info",
				message=finding["message"],
				file=finding.get("file"),
				line=finding.get("line_number"),
				severity="info",
			))
			priority += 1

	return items


def build_report(pr_data, quality_data):
	"""Build a complete review report."""
	report = ReviewReport()
	report.timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
	report.pr_analysis = pr_data
	report.quality_analysis = quality_data
	report.score = calculate_score(pr_data, quality_data)
	report.verdict = determine_verdict(report.score)
	report.action_items = generate_action_items(pr_data, quality_data)
	return report


def report_to_dict(report):
	"""Convert ReviewReport to a dictionary."""
	return {
		"timestamp": report.timestamp,
		"score": report.score,
		"verdict": report.verdict,
		"verdict_label": VERDICTS[report.verdict]["label"],
		"verdict_description": VERDICTS[report.verdict]["description"],
		"action_items": [
			{
				"priority": item.priority,
				"category": item.category,
				"severity": item.severity,
				"message": item.message,
				"file": item.file,
				"line": item.line,
			}
			for item in report.action_items
		],
		"pr_analysis": report.pr_analysis,
		"quality_analysis": report.quality_analysis,
	}


def format_text(report):
	"""Format report as plain text."""
	lines = []
	verdict_info = VERDICTS[report.verdict]

	lines.append("=" * 70)
	lines.append("CODE REVIEW REPORT")
	lines.append("=" * 70)
	lines.append(f"  Generated: {report.timestamp}")
	lines.append(f"  Score:     {report.score}/100")
	lines.append(f"  Verdict:   {verdict_info['label']}")
	lines.append(f"             {verdict_info['description']}")
	lines.append("")

	# PR summary
	pr = report.pr_analysis
	if pr:
		lines.append("--- PR Summary ---")
		lines.append(f"  Files changed:    {pr.get('total_files', 0)}")
		lines.append(f"  Additions:        +{pr.get('total_additions', 0)}")
		lines.append(f"  Deletions:        -{pr.get('total_deletions', 0)}")
		lines.append(f"  Complexity:       {pr.get('complexity_score', 'N/A')}/10")
		lines.append("")

	# Quality summary
	qs = report.quality_analysis.get("summary", {})
	if qs:
		lines.append("--- Code Quality Summary ---")
		lines.append(f"  Files analyzed:   {qs.get('total_files', 0)}")
		lines.append(f"  Total lines:      {qs.get('total_lines', 0)}")
		lines.append(f"  Functions:        {qs.get('total_functions', 0)}")
		lines.append(f"  Code smells:      {qs.get('total_smells', 0)}")
		lines.append(f"  SOLID issues:     {qs.get('total_violations', 0)}")
		lines.append("")

	# Action items
	if report.action_items:
		lines.append("--- Action Items (prioritized) ---")
		for item in report.action_items:
			severity_icon = {
				"critical": "[!!!]",
				"error": "[!!]",
				"warning": "[!]",
				"info": "[i]",
			}.get(item.severity, "[?]")

			loc = ""
			if item.file:
				loc = f" ({item.file}"
				if item.line:
					loc += f":{item.line}"
				loc += ")"

			lines.append(f"  {item.priority}. {severity_icon} [{item.category}] {item.message}{loc}")
		lines.append("")
	else:
		lines.append("--- No action items ---")
		lines.append("")

	# Review order
	review_order = pr.get("review_order", []) if pr else []
	if review_order:
		lines.append("--- Suggested Review Order ---")
		for i, path in enumerate(review_order, 1):
			lines.append(f"  {i}. {path}")
		lines.append("")

	lines.append("=" * 70)
	return "\n".join(lines)


def format_markdown(report):
	"""Format report as Markdown."""
	lines = []
	verdict_info = VERDICTS[report.verdict]

	lines.append("# Code Review Report")
	lines.append("")
	lines.append(f"**Generated:** {report.timestamp}")
	lines.append(f"**Score:** {report.score}/100")
	lines.append(f"**Verdict:** {verdict_info['label']}")
	lines.append(f"> {verdict_info['description']}")
	lines.append("")

	# PR summary
	pr = report.pr_analysis
	if pr:
		lines.append("## PR Summary")
		lines.append("")
		lines.append(f"| Metric | Value |")
		lines.append(f"|--------|-------|")
		lines.append(f"| Files changed | {pr.get('total_files', 0)} |")
		lines.append(f"| Additions | +{pr.get('total_additions', 0)} |")
		lines.append(f"| Deletions | -{pr.get('total_deletions', 0)} |")
		lines.append(f"| Complexity | {pr.get('complexity_score', 'N/A')}/10 |")
		lines.append("")

	# Quality summary
	qs = report.quality_analysis.get("summary", {})
	if qs:
		lines.append("## Code Quality")
		lines.append("")
		lines.append(f"| Metric | Value |")
		lines.append(f"|--------|-------|")
		lines.append(f"| Files analyzed | {qs.get('total_files', 0)} |")
		lines.append(f"| Total lines | {qs.get('total_lines', 0)} |")
		lines.append(f"| Functions | {qs.get('total_functions', 0)} |")
		lines.append(f"| Code smells | {qs.get('total_smells', 0)} |")
		lines.append(f"| SOLID issues | {qs.get('total_violations', 0)} |")
		lines.append("")

	# Action items
	if report.action_items:
		lines.append("## Action Items")
		lines.append("")
		for item in report.action_items:
			severity_badge = {
				"critical": "**CRITICAL**",
				"error": "**ERROR**",
				"warning": "WARNING",
				"info": "INFO",
			}.get(item.severity, item.severity)

			loc = ""
			if item.file:
				loc = f" `{item.file}"
				if item.line:
					loc += f":{item.line}"
				loc += "`"

			lines.append(f"{item.priority}. [{severity_badge}] [{item.category}] {item.message}{loc}")
		lines.append("")

	# Review order
	review_order = pr.get("review_order", []) if pr else []
	if review_order:
		lines.append("## Suggested Review Order")
		lines.append("")
		for i, path in enumerate(review_order, 1):
			lines.append(f"{i}. `{path}`")
		lines.append("")

	return "\n".join(lines)


def main():
	parser = argparse.ArgumentParser(
		description="Generate a combined code review report from PR analysis and code quality checks."
	)
	parser.add_argument(
		"--base",
		default="main",
		help="Base branch for PR analysis (default: main)",
	)
	parser.add_argument(
		"--head",
		default="HEAD",
		help="Head ref for PR analysis (default: HEAD)",
	)
	parser.add_argument(
		"--paths",
		nargs="*",
		default=["."],
		help="Paths to analyze for code quality (default: current directory)",
	)
	parser.add_argument(
		"--language",
		choices=["java", "python", "typescript", "javascript", "go"],
		help="Filter code quality analysis to a specific language",
	)
	parser.add_argument(
		"--format",
		choices=["text", "markdown", "json"],
		default="text",
		help="Output format (default: text)",
	)
	parser.add_argument(
		"--pr-analysis",
		dest="pr_analysis_file",
		help="Path to pre-computed PR analysis JSON file (skip running pr_analyzer.py)",
	)
	parser.add_argument(
		"--quality-analysis",
		dest="quality_analysis_file",
		help="Path to pre-computed quality analysis JSON file (skip running code_quality_checker.py)",
	)
	parser.add_argument(
		"--cwd",
		default=None,
		help="Working directory for git commands",
	)

	args = parser.parse_args()

	# Get or load PR analysis
	if args.pr_analysis_file:
		try:
			with open(args.pr_analysis_file, "r") as f:
				pr_data = json.load(f)
		except (OSError, json.JSONDecodeError) as e:
			print(f"Error loading PR analysis from {args.pr_analysis_file}: {e}", file=sys.stderr)
			pr_data = {}
	else:
		pr_args = ["--base", args.base, "--head", args.head]
		if args.cwd:
			pr_args.extend(["--cwd", args.cwd])
		pr_data = run_script("pr_analyzer.py", pr_args)

	# Get or load quality analysis
	if args.quality_analysis_file:
		try:
			with open(args.quality_analysis_file, "r") as f:
				quality_data = json.load(f)
		except (OSError, json.JSONDecodeError) as e:
			print(f"Error loading quality analysis from {args.quality_analysis_file}: {e}", file=sys.stderr)
			quality_data = {}
	else:
		quality_args = list(args.paths)
		if args.language:
			quality_args.extend(["--language", args.language])
		quality_data = run_script("code_quality_checker.py", quality_args)

	# Build report
	report = build_report(pr_data, quality_data)

	# Output
	if args.format == "json":
		print(json.dumps(report_to_dict(report), indent=2))
	elif args.format == "markdown":
		print(format_markdown(report))
	else:
		print(format_text(report))


if __name__ == "__main__":
	main()
