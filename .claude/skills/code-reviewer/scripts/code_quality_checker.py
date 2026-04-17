#!/usr/bin/env python3
"""Code quality analyzer with support for Java, Python, TypeScript, JavaScript, and Go.

Detects code smells, SOLID violations, and quality issues in source files.
Outputs human-readable reports or JSON.
"""

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

LANGUAGE_EXTENSIONS = {
	"java": [".java"],
	"python": [".py"],
	"typescript": [".ts", ".tsx"],
	"javascript": [".js", ".jsx"],
	"go": [".go"],
}

# Reverse mapping: extension -> language
EXT_TO_LANGUAGE = {}
for lang, exts in LANGUAGE_EXTENSIONS.items():
	for ext in exts:
		EXT_TO_LANGUAGE[ext] = lang

# Function detection patterns per language
FUNCTION_PATTERNS = {
	"java": re.compile(
		r"(public|private|protected|static|\s)+([\w<>\[\]]+)\s+(\w+)\s*\("
	),
	"python": re.compile(r"^\s*def\s+(\w+)\s*\("),
	"typescript": re.compile(
		r"(?:export\s+)?(?:async\s+)?(?:function\s+(\w+)|(\w+)\s*(?:=|:)\s*(?:async\s+)?\()"
	),
	"javascript": re.compile(
		r"(?:export\s+)?(?:async\s+)?(?:function\s+(\w+)|(\w+)\s*(?:=|:)\s*(?:async\s+)?\()"
	),
	"go": re.compile(r"^func\s+(?:\(\w+\s+\*?\w+\)\s+)?(\w+)\s*\("),
}

# Class detection patterns
CLASS_PATTERNS = {
	"java": re.compile(r"class\s+(\w+)"),
	"python": re.compile(r"^\s*class\s+(\w+)"),
	"typescript": re.compile(r"(?:export\s+)?class\s+(\w+)"),
	"javascript": re.compile(r"(?:export\s+)?class\s+(\w+)"),
	"go": re.compile(r"^type\s+(\w+)\s+struct\s*\{"),
}

# Thresholds
MAX_FUNCTION_LINES = 50
MAX_FILE_LINES = 500
MAX_METHODS_PER_CLASS = 20
MAX_NESTING_DEPTH = 4
MAX_PARAMETERS = 5
MAX_CYCLOMATIC_COMPLEXITY = 10


@dataclass
class FunctionInfo:
	name: str
	start_line: int
	end_line: int
	line_count: int
	param_count: int
	nesting_depth: int
	cyclomatic_complexity: int


@dataclass
class ClassInfo:
	name: str
	start_line: int
	method_count: int


@dataclass
class CodeSmell:
	smell_type: str
	severity: str  # "warning", "error"
	message: str
	file: str
	line: Optional[int] = None
	function: Optional[str] = None


@dataclass
class FileAnalysis:
	path: str
	language: str
	line_count: int
	functions: list = field(default_factory=list)
	classes: list = field(default_factory=list)
	smells: list = field(default_factory=list)
	solid_violations: list = field(default_factory=list)


def detect_language(filepath):
	"""Detect language from file extension."""
	ext = Path(filepath).suffix.lower()
	return EXT_TO_LANGUAGE.get(ext)


def count_parameters(line, language):
	"""Count parameters in a function signature line."""
	# Find the parameter list between parens
	paren_start = line.find("(")
	if paren_start == -1:
		return 0

	depth = 0
	param_text = []
	for ch in line[paren_start:]:
		if ch == "(":
			depth += 1
			if depth == 1:
				continue
		elif ch == ")":
			depth -= 1
			if depth == 0:
				break
		if depth == 1:
			param_text.append(ch)

	params_str = "".join(param_text).strip()
	if not params_str:
		return 0

	# Split by commas, accounting for generics
	params = []
	depth = 0
	current = []
	for ch in params_str:
		if ch in "<([":
			depth += 1
		elif ch in ">)]":
			depth -= 1
		elif ch == "," and depth == 0:
			params.append("".join(current).strip())
			current = []
			continue
		current.append(ch)
	if current:
		params.append("".join(current).strip())

	# Filter out empty params
	return len([p for p in params if p.strip()])


def measure_nesting_depth(lines):
	"""Measure maximum nesting depth in a block of code."""
	max_depth = 0
	current_depth = 0
	for line in lines:
		stripped = line.strip()
		# Count opening braces/blocks
		current_depth += stripped.count("{") - stripped.count("}")
		max_depth = max(max_depth, current_depth)
	return max_depth


def calculate_cyclomatic_complexity(lines, language):
	"""Calculate cyclomatic complexity for a function body."""
	complexity = 1  # Base complexity

	branch_keywords = {
		"java": [r"\bif\b", r"\belse\s+if\b", r"\bfor\b", r"\bwhile\b",
				 r"\bcase\b", r"\bcatch\b", r"\b\?\?", r"&&", r"\|\|",
				 r"\?(?!=)"],
		"python": [r"\bif\b", r"\belif\b", r"\bfor\b", r"\bwhile\b",
				   r"\bexcept\b", r"\band\b", r"\bor\b"],
		"typescript": [r"\bif\b", r"\belse\s+if\b", r"\bfor\b", r"\bwhile\b",
					   r"\bcase\b", r"\bcatch\b", r"&&", r"\|\|", r"\?\?",
					   r"\?(?!=)"],
		"javascript": [r"\bif\b", r"\belse\s+if\b", r"\bfor\b", r"\bwhile\b",
					   r"\bcase\b", r"\bcatch\b", r"&&", r"\|\|", r"\?\?",
					   r"\?(?!=)"],
		"go": [r"\bif\b", r"\belse\s+if\b", r"\bfor\b", r"\bcase\b",
			   r"&&", r"\|\|"],
	}

	patterns = branch_keywords.get(language, branch_keywords["java"])
	for line in lines:
		stripped = line.strip()
		# Skip comments
		if stripped.startswith("//") or stripped.startswith("#") or stripped.startswith("*"):
			continue
		for pattern in patterns:
			complexity += len(re.findall(pattern, stripped))

	return complexity


def find_function_end(lines, start_idx, language):
	"""Find the end of a function body (brace-based or indent-based)."""
	if language == "python":
		# Indent-based
		if start_idx + 1 >= len(lines):
			return start_idx
		# Find the indentation of the def line
		def_indent = len(lines[start_idx]) - len(lines[start_idx].lstrip())
		end = start_idx + 1
		while end < len(lines):
			line = lines[end]
			if line.strip() == "":
				end += 1
				continue
			current_indent = len(line) - len(line.lstrip())
			if current_indent <= def_indent:
				break
			end += 1
		return end - 1
	else:
		# Brace-based (Java, TS, JS, Go)
		depth = 0
		found_open = False
		for i in range(start_idx, len(lines)):
			for ch in lines[i]:
				if ch == "{":
					depth += 1
					found_open = True
				elif ch == "}":
					depth -= 1
			if found_open and depth <= 0:
				return i
		return len(lines) - 1


def extract_functions(lines, language):
	"""Extract function information from source lines."""
	pattern = FUNCTION_PATTERNS.get(language)
	if not pattern:
		return []

	functions = []
	i = 0
	while i < len(lines):
		match = pattern.search(lines[i])
		if match:
			# Get function name
			if language == "java":
				name = match.group(3)
			elif language in ("typescript", "javascript"):
				name = match.group(1) or match.group(2)
			else:
				name = match.group(1)

			if not name:
				i += 1
				continue

			start_line = i
			end_line = find_function_end(lines, i, language)
			body_lines = lines[start_line:end_line + 1]
			line_count = end_line - start_line + 1
			param_count = count_parameters(lines[i], language)
			nesting = measure_nesting_depth(body_lines)
			complexity = calculate_cyclomatic_complexity(body_lines, language)

			functions.append(FunctionInfo(
				name=name,
				start_line=start_line + 1,  # 1-based
				end_line=end_line + 1,
				line_count=line_count,
				param_count=param_count,
				nesting_depth=nesting,
				cyclomatic_complexity=complexity,
			))
			i = end_line + 1
		else:
			i += 1

	return functions


def extract_classes(lines, language):
	"""Extract class information from source lines."""
	pattern = CLASS_PATTERNS.get(language)
	if not pattern:
		return []

	classes = []
	for i, line in enumerate(lines):
		match = pattern.search(line)
		if match:
			name = match.group(1)
			# Count methods in this class
			class_end = find_function_end(lines, i, language)
			class_body = lines[i:class_end + 1]
			func_pattern = FUNCTION_PATTERNS.get(language)
			method_count = 0
			if func_pattern:
				for body_line in class_body:
					if func_pattern.search(body_line):
						method_count += 1

			classes.append(ClassInfo(
				name=name,
				start_line=i + 1,
				method_count=method_count,
			))

	return classes


def check_code_smells(analysis):
	"""Check for code smells in the analyzed file."""
	smells = []

	# Large file
	if analysis.line_count > MAX_FILE_LINES:
		smells.append(CodeSmell(
			smell_type="large_file",
			severity="warning",
			message=f"File has {analysis.line_count} lines (threshold: {MAX_FILE_LINES})",
			file=analysis.path,
		))

	for func in analysis.functions:
		# Long function
		if func.line_count > MAX_FUNCTION_LINES:
			smells.append(CodeSmell(
				smell_type="long_function",
				severity="warning",
				message=f"Function '{func.name}' has {func.line_count} lines (threshold: {MAX_FUNCTION_LINES})",
				file=analysis.path,
				line=func.start_line,
				function=func.name,
			))

		# Deep nesting
		if func.nesting_depth > MAX_NESTING_DEPTH:
			smells.append(CodeSmell(
				smell_type="deep_nesting",
				severity="warning",
				message=f"Function '{func.name}' has nesting depth {func.nesting_depth} (threshold: {MAX_NESTING_DEPTH})",
				file=analysis.path,
				line=func.start_line,
				function=func.name,
			))

		# Too many parameters
		if func.param_count > MAX_PARAMETERS:
			smells.append(CodeSmell(
				smell_type="too_many_parameters",
				severity="warning",
				message=f"Function '{func.name}' has {func.param_count} parameters (threshold: {MAX_PARAMETERS})",
				file=analysis.path,
				line=func.start_line,
				function=func.name,
			))

		# High cyclomatic complexity
		if func.cyclomatic_complexity > MAX_CYCLOMATIC_COMPLEXITY:
			smells.append(CodeSmell(
				smell_type="high_complexity",
				severity="error",
				message=f"Function '{func.name}' has cyclomatic complexity {func.cyclomatic_complexity} (threshold: {MAX_CYCLOMATIC_COMPLEXITY})",
				file=analysis.path,
				line=func.start_line,
				function=func.name,
			))

	for cls in analysis.classes:
		# God class
		if cls.method_count > MAX_METHODS_PER_CLASS:
			smells.append(CodeSmell(
				smell_type="god_class",
				severity="warning",
				message=f"Class '{cls.name}' has {cls.method_count} methods (threshold: {MAX_METHODS_PER_CLASS})",
				file=analysis.path,
				line=cls.start_line,
			))

	return smells


def check_solid_violations(lines, language, filepath):
	"""Check for potential SOLID principle violations."""
	violations = []

	content = "\n".join(lines)

	# Single Responsibility: file has too many classes
	class_pattern = CLASS_PATTERNS.get(language)
	if class_pattern:
		class_matches = class_pattern.findall(content)
		# For Java, multiple top-level classes in one file
		if language == "java" and len(class_matches) > 2:
			violations.append(CodeSmell(
				smell_type="srp_violation",
				severity="warning",
				message=f"File contains {len(class_matches)} classes - may violate Single Responsibility Principle",
				file=filepath,
			))

	# Dependency Inversion: direct instantiation of many concrete classes
	if language == "java":
		new_count = len(re.findall(r"\bnew\s+\w+\s*\(", content))
		if new_count > 15:
			violations.append(CodeSmell(
				smell_type="dip_violation",
				severity="warning",
				message=f"File has {new_count} direct instantiations - consider dependency injection",
				file=filepath,
			))

	# Interface Segregation: very large interfaces
	if language == "java":
		interface_match = re.search(r"interface\s+(\w+)", content)
		if interface_match:
			func_pattern = FUNCTION_PATTERNS.get(language)
			if func_pattern:
				method_count = len(func_pattern.findall(content))
				if method_count > 10:
					violations.append(CodeSmell(
						smell_type="isp_violation",
						severity="warning",
						message=f"Interface '{interface_match.group(1)}' has {method_count} methods - consider splitting",
						file=filepath,
					))

	# Open/Closed: excessive use of instanceof / type checking
	if language == "java":
		instanceof_count = len(re.findall(r"\binstanceof\b", content))
		if instanceof_count > 5:
			violations.append(CodeSmell(
				smell_type="ocp_violation",
				severity="warning",
				message=f"File has {instanceof_count} instanceof checks - consider polymorphism",
				file=filepath,
			))

	return violations


def analyze_file(filepath):
	"""Analyze a single source file."""
	language = detect_language(filepath)
	if not language:
		return None

	try:
		with open(filepath, "r", encoding="utf-8", errors="replace") as f:
			content = f.read()
	except (OSError, IOError):
		return None

	lines = content.splitlines()

	analysis = FileAnalysis(
		path=filepath,
		language=language,
		line_count=len(lines),
	)

	analysis.functions = extract_functions(lines, language)
	analysis.classes = extract_classes(lines, language)
	analysis.smells = check_code_smells(analysis)
	analysis.solid_violations = check_solid_violations(lines, language, filepath)

	return analysis


def collect_files(paths, language_filter=None):
	"""Collect all source files from given paths."""
	files = []
	allowed_exts = set()
	if language_filter:
		allowed_exts = set(LANGUAGE_EXTENSIONS.get(language_filter, []))
	else:
		for exts in LANGUAGE_EXTENSIONS.values():
			allowed_exts.update(exts)

	for path in paths:
		p = Path(path)
		if p.is_file():
			if p.suffix.lower() in allowed_exts:
				files.append(str(p))
		elif p.is_dir():
			for root, dirs, filenames in os.walk(p):
				# Skip hidden dirs and common non-source dirs
				dirs[:] = [
					d for d in dirs
					if not d.startswith(".")
					and d not in ("node_modules", "target", "build", "dist", "vendor", "__pycache__")
				]
				for fname in filenames:
					fpath = os.path.join(root, fname)
					if Path(fpath).suffix.lower() in allowed_exts:
						files.append(fpath)

	return sorted(files)


def analysis_to_dict(analysis):
	"""Convert FileAnalysis to a dictionary."""
	return {
		"path": analysis.path,
		"language": analysis.language,
		"line_count": analysis.line_count,
		"functions": [
			{
				"name": f.name,
				"start_line": f.start_line,
				"end_line": f.end_line,
				"line_count": f.line_count,
				"param_count": f.param_count,
				"nesting_depth": f.nesting_depth,
				"cyclomatic_complexity": f.cyclomatic_complexity,
			}
			for f in analysis.functions
		],
		"classes": [
			{
				"name": c.name,
				"start_line": c.start_line,
				"method_count": c.method_count,
			}
			for c in analysis.classes
		],
		"smells": [
			{
				"type": s.smell_type,
				"severity": s.severity,
				"message": s.message,
				"file": s.file,
				"line": s.line,
				"function": s.function,
			}
			for s in analysis.smells
		],
		"solid_violations": [
			{
				"type": v.smell_type,
				"severity": v.severity,
				"message": v.message,
				"file": v.file,
			}
			for v in analysis.solid_violations
		],
	}


def print_report(analyses):
	"""Print a human-readable report."""
	total_smells = 0
	total_violations = 0
	total_files = len(analyses)

	print("=" * 70)
	print("CODE QUALITY REPORT")
	print("=" * 70)
	print()

	for analysis in analyses:
		smells = analysis.smells + analysis.solid_violations
		if not smells:
			continue

		print(f"--- {analysis.path} ({analysis.language}, {analysis.line_count} lines) ---")
		print(f"    Functions: {len(analysis.functions)}, Classes: {len(analysis.classes)}")
		print()

		if analysis.smells:
			print("  Code Smells:")
			for smell in analysis.smells:
				icon = "[!]" if smell.severity == "error" else "[~]"
				loc = f" (line {smell.line})" if smell.line else ""
				print(f"    {icon} {smell.message}{loc}")
			total_smells += len(analysis.smells)

		if analysis.solid_violations:
			print("  SOLID Violations:")
			for v in analysis.solid_violations:
				print(f"    [~] {v.message}")
			total_violations += len(analysis.solid_violations)

		print()

	print("=" * 70)
	print("SUMMARY")
	print("=" * 70)
	print(f"  Files analyzed: {total_files}")
	print(f"  Code smells:    {total_smells}")
	print(f"  SOLID issues:   {total_violations}")
	total_issues = total_smells + total_violations
	if total_issues == 0:
		print("  Result: CLEAN - No issues found")
	elif total_issues < 5:
		print("  Result: GOOD - Minor issues detected")
	elif total_issues < 15:
		print("  Result: FAIR - Several issues to address")
	else:
		print("  Result: NEEDS ATTENTION - Many issues detected")
	print()


def main():
	parser = argparse.ArgumentParser(
		description="Analyze code quality for Java, Python, TypeScript, JavaScript, and Go files."
	)
	parser.add_argument(
		"paths",
		nargs="*",
		default=["."],
		help="Files or directories to analyze (default: current directory)",
	)
	parser.add_argument(
		"--language",
		choices=["java", "python", "typescript", "javascript", "go"],
		help="Filter to a specific language",
	)
	parser.add_argument(
		"--json",
		action="store_true",
		help="Output results as JSON",
	)

	args = parser.parse_args()

	files = collect_files(args.paths, language_filter=args.language)
	if not files:
		if args.json:
			print(json.dumps({"files": [], "summary": {"total_files": 0, "total_smells": 0, "total_violations": 0}}))
		else:
			print("No matching source files found.")
		sys.exit(0)

	analyses = []
	for filepath in files:
		result = analyze_file(filepath)
		if result:
			analyses.append(result)

	if args.json:
		output = {
			"files": [analysis_to_dict(a) for a in analyses],
			"summary": {
				"total_files": len(analyses),
				"total_smells": sum(len(a.smells) for a in analyses),
				"total_violations": sum(len(a.solid_violations) for a in analyses),
				"total_functions": sum(len(a.functions) for a in analyses),
				"total_classes": sum(len(a.classes) for a in analyses),
				"total_lines": sum(a.line_count for a in analyses),
			},
		}
		print(json.dumps(output, indent=2))
	else:
		print_report(analyses)


if __name__ == "__main__":
	main()
