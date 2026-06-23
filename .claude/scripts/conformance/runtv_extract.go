// Command runtv_extract ports Sprig's runt/runtv(...) test cases into base64 TSV
// conformance fixtures for SprigConformanceTest.
//
// Sprig's test helpers are:
//
//	func runt(tpl, expect string) error            // render tpl with NO data
//	func runtv(tpl, expect string, vars any) error // render tpl with vars as "."
//
// A runtv case carries a live Go `vars` value as the template's "." context, so it
// cannot be evaluated by static text parsing the way runt tables can. This tool instead
// lets the Go compiler do the evaluation:
//
//  1. Parse each Sprig *_test.go with go/ast; find runt/runtv calls and resolve a `tpl`
//     identifier back to the string literal it was last assigned in the same function.
//  2. Generate a throwaway Go module that embeds each (tpl, vars) pair verbatim, imports
//     the real github.com/Masterminds/sprig funcmap, renders the template over vars and
//     json.Marshal's vars.
//  3. If a vars expression references a test-local type/fixture/selector it won't compile;
//     the compiler error names the line, the tool drops that one case and regenerates
//     (compiler-as-oracle — no hand-rolled type checker, no guessing).
//  4. Run the module and write <out>/sprig_<cat>_runtv_cases.tsv, one line per case:
//     base64(tpl) \t base64(rendered-output) \t base64(json(vars))
//
// The third column (JSON data) is what makes these portable: the Java SprigConformanceTest
// parses it into a Map and supplies it as the render root, so gotmpl4j renders the exact
// same template over the exact same data Sprig did, and the rendered output is ground truth.
//
// Usage:
//
//	go run runtv_extract.go -out ../../jhelm-gotemplate-sprig/src/test/resources/conformance \
//	    /tmp/sprig_dict_test.go /tmp/sprig_defaults_test.go ...
//
// Flags:
//
//	-out string    directory to write the *_runtv_cases.tsv files (default ".")
//	-sprig string  Sprig module version for the generated module (default "v3.3.0")
//	-runt          also emit runt() (no-data) cases with an empty {} JSON column
//	-keep          keep the generated temp module dir (for debugging)
package main

import (
	"bufio"
	"flag"
	"fmt"
	"go/ast"
	"go/parser"
	"go/printer"
	"go/token"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

type rawCase struct {
	tpl     string // resolved template text (already unquoted)
	varsSrc string // verbatim Go source of the vars expression; "" for runt (no data)
}

func main() {
	out := flag.String("out", ".", "directory to write *_runtv_cases.tsv files")
	sprigVer := flag.String("sprig", "v3.3.0", "Sprig module version for the generated module")
	includeRunt := flag.Bool("runt", false, "also emit runt() (no-data) cases")
	keep := flag.Bool("keep", false, "keep the generated temp module dir")
	flag.Parse()
	if flag.NArg() == 0 {
		fmt.Fprintln(os.Stderr, "usage: go run runtv_extract.go -out <dir> <sprig_*_test.go> ...")
		os.Exit(2)
	}
	if err := os.MkdirAll(*out, 0o755); err != nil {
		fatal(err)
	}
	for _, file := range flag.Args() {
		cat := categoryOf(file)
		cases, err := extractFile(file, *includeRunt)
		if err != nil {
			fmt.Fprintf(os.Stderr, "%s: parse error: %v\n", file, err)
			continue
		}
		if len(cases) == 0 {
			fmt.Fprintf(os.Stderr, "%s: no runtv cases found\n", filepath.Base(file))
			continue
		}
		rows, dropped, err := renderCases(cases, *sprigVer, *keep)
		if err != nil {
			fmt.Fprintf(os.Stderr, "%s: render error: %v\n", filepath.Base(file), err)
			continue
		}
		dst := filepath.Join(*out, "sprig_"+cat+"_runtv_cases.tsv")
		if err := os.WriteFile(dst, []byte(strings.Join(rows, "\n")+"\n"), 0o644); err != nil {
			fatal(err)
		}
		fmt.Printf("%-22s %3d cases -> %s  (%d non-portable dropped)\n",
			filepath.Base(file), len(rows), dst, dropped)
	}
}

// categoryOf turns "/tmp/sprig_dict_test.go" into "dict".
func categoryOf(path string) string {
	base := strings.TrimSuffix(filepath.Base(path), ".go")
	base = strings.TrimPrefix(base, "sprig_")
	base = strings.TrimSuffix(base, "_test")
	return base
}

// extractFile parses one Sprig test file and returns its runtv cases (and, when
// includeRunt is set, its runt cases too) with `tpl` identifiers resolved to literals.
func extractFile(path string, includeRunt bool) ([]rawCase, error) {
	fset := token.NewFileSet()
	f, err := parser.ParseFile(fset, path, nil, 0)
	if err != nil {
		return nil, err
	}
	var cases []rawCase
	for _, decl := range f.Decls {
		fn, ok := decl.(*ast.FuncDecl)
		if !ok || fn.Body == nil {
			continue
		}
		// Track the most recent string-literal value bound to each simple identifier,
		// in source order, so `tpl = ...` then `runt(tpl, ...)` resolves correctly.
		strVars := map[string]string{}
		ast.Inspect(fn.Body, func(n ast.Node) bool {
			switch node := n.(type) {
			case *ast.AssignStmt:
				if len(node.Lhs) == 1 && len(node.Rhs) == 1 {
					if id, ok := node.Lhs[0].(*ast.Ident); ok {
						if s, ok := stringLit(node.Rhs[0]); ok {
							strVars[id.Name] = s
						} else {
							delete(strVars, id.Name) // reassigned to a non-literal
						}
					}
				}
			case *ast.CallExpr:
				name := calleeName(node.Fun)
				switch {
				case name == "runtv" && len(node.Args) == 3:
					if tpl, ok := resolveTpl(node.Args[0], strVars); ok {
						cases = append(cases, rawCase{tpl: tpl, varsSrc: exprSource(fset, node.Args[2])})
					}
				case includeRunt && name == "runt" && len(node.Args) == 2:
					if tpl, ok := resolveTpl(node.Args[0], strVars); ok {
						cases = append(cases, rawCase{tpl: tpl, varsSrc: ""})
					}
				}
			}
			return true
		})
	}
	return cases, nil
}

// resolveTpl yields the template text for a runt/runtv first argument, whether it is an
// inline string literal or an identifier previously assigned one.
func resolveTpl(arg ast.Expr, strVars map[string]string) (string, bool) {
	if s, ok := stringLit(arg); ok {
		return s, true
	}
	if id, ok := arg.(*ast.Ident); ok {
		if s, ok := strVars[id.Name]; ok {
			return s, true
		}
	}
	return "", false
}

// stringLit unquotes a basic string literal expression (raw or interpreted).
func stringLit(e ast.Expr) (string, bool) {
	lit, ok := e.(*ast.BasicLit)
	if !ok || lit.Kind != token.STRING {
		return "", false
	}
	s, err := strconv.Unquote(lit.Value)
	if err != nil {
		return "", false
	}
	return s, true
}

// calleeName returns the function name of a call, handling both bare `runtv(...)` and
// the (rare) `pkg.runtv(...)` form.
func calleeName(fun ast.Expr) string {
	switch f := fun.(type) {
	case *ast.Ident:
		return f.Name
	case *ast.SelectorExpr:
		return f.Sel.Name
	}
	return ""
}

// exprSource renders an AST expression back to Go source text (single-spaced).
func exprSource(fset *token.FileSet, n ast.Node) string {
	var sb strings.Builder
	cfg := printer.Config{Mode: printer.UseSpaces, Tabwidth: 1}
	if err := cfg.Fprint(&sb, fset, n); err != nil {
		return ""
	}
	// Collapse to a single line so each case occupies one source line in the generated
	// module — this keeps the compiler-error -> case-index mapping exact.
	return strings.Join(strings.Fields(sb.String()), " ")
}

var buildErrLine = regexp.MustCompile(`main\.go:(\d+):`)

// renderCases generates a throwaway Go module for the given cases, drops any whose vars
// won't compile, then runs it and returns the TSV rows plus the dropped count.
func renderCases(cases []rawCase, sprigVer string, keep bool) ([]string, int, error) {
	dir, err := os.MkdirTemp("", "runtvgen")
	if err != nil {
		return nil, 0, err
	}
	if keep {
		fmt.Fprintf(os.Stderr, "generated module: %s\n", dir)
	} else {
		defer os.RemoveAll(dir)
	}
	if err := writeModule(dir, sprigVer); err != nil {
		return nil, 0, err
	}

	bad := map[int]bool{}
	for {
		src, lineOf := generateSource(cases, bad)
		if err := os.WriteFile(filepath.Join(dir, "main.go"), []byte(src), 0o644); err != nil {
			return nil, 0, err
		}
		out, err := runGo(dir, "build", "-o", os.DevNull, ".")
		if err == nil {
			break
		}
		// Map each compiler-error line back to the case that owns it and drop those.
		newlyBad := false
		for _, m := range buildErrLine.FindAllStringSubmatch(out, -1) {
			ln, _ := strconv.Atoi(m[1])
			if idx := caseForLine(lineOf, ln); idx >= 0 && !bad[idx] {
				bad[idx] = true
				newlyBad = true
			}
		}
		if !newlyBad {
			return nil, 0, fmt.Errorf("build failed and no offending case could be isolated:\n%s", out)
		}
	}

	out, err := runGo(dir, "run", ".")
	if err != nil {
		return nil, 0, fmt.Errorf("run failed: %v\n%s", err, out)
	}
	var rows []string
	sc := bufio.NewScanner(strings.NewReader(out))
	sc.Buffer(make([]byte, 1024*1024), 8*1024*1024)
	for sc.Scan() {
		if line := sc.Text(); line != "" {
			rows = append(rows, line)
		}
	}
	return rows, len(bad), nil
}

// caseForLine returns the index of the case whose generated literal spans source line ln,
// i.e. the greatest case-start line <= ln.
func caseForLine(lineOf []int, ln int) int {
	idx := -1
	for i, start := range lineOf {
		if start <= ln {
			idx = i
		} else {
			break
		}
	}
	return idx
}

// generateSource emits the throwaway program and returns, for each still-live case, the
// 1-based source line on which its struct literal begins (for error attribution).
func generateSource(cases []rawCase, bad map[int]bool) (string, []int) {
	var b strings.Builder
	emit := func(s string) { b.WriteString(s) }
	emit(genHeader)
	var lineOf []int
	line := strings.Count(genHeader, "\n") + 1
	for i, c := range cases {
		if bad[i] {
			continue
		}
		vars := c.varsSrc
		if vars == "" {
			vars = "map[string]any{}" // runt: empty data
		}
		entry := fmt.Sprintf("\t{tpl: %s, vars: %s},\n", strconv.Quote(c.tpl), vars)
		lineOf = append(lineOf, line)
		emit(entry)
		line += strings.Count(entry, "\n")
	}
	emit(genFooter)
	return b.String(), lineOf
}

const genHeader = `package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"text/template"

	"github.com/Masterminds/sprig/v3"
)

type entry struct {
	tpl  string
	vars any
}

var cases = []entry{
`

const genFooter = `}

func main() {
	fm := sprig.TxtFuncMap()
	enc := base64.StdEncoding
	for i, c := range cases {
		t, err := template.New("test").Funcs(fm).Parse(c.tpl)
		if err != nil {
			fmt.Fprintf(os.Stderr, "skip %d (parse): %v\n", i, err)
			continue
		}
		var buf bytes.Buffer
		if err := t.Execute(&buf, c.vars); err != nil {
			fmt.Fprintf(os.Stderr, "skip %d (exec): %v\n", i, err)
			continue
		}
		jv, err := json.Marshal(c.vars)
		if err != nil {
			fmt.Fprintf(os.Stderr, "skip %d (json): %v\n", i, err)
			continue
		}
		fmt.Printf("%s\t%s\t%s\n",
			enc.EncodeToString([]byte(c.tpl)),
			enc.EncodeToString(buf.Bytes()),
			enc.EncodeToString(jv))
	}
}
`

// writeModule lays down a minimal module that requires Sprig, then fetches it.
func writeModule(dir, sprigVer string) error {
	gomod := "module runtvgen\n\ngo 1.21\n"
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte(gomod), 0o644); err != nil {
		return err
	}
	// A stub main so `go get` has a buildable package to resolve against.
	stub := "package main\n\nimport _ \"github.com/Masterminds/sprig/v3\"\n\nfunc main() {}\n"
	if err := os.WriteFile(filepath.Join(dir, "main.go"), []byte(stub), 0o644); err != nil {
		return err
	}
	if out, err := runGo(dir, "get", "github.com/Masterminds/sprig/v3@"+sprigVer); err != nil {
		return fmt.Errorf("go get sprig %s: %v\n%s", sprigVer, err, out)
	}
	return nil
}

// runGo runs `go <args...>` in dir and returns combined output.
func runGo(dir string, args ...string) (string, error) {
	cmd := exec.Command("go", args...)
	cmd.Dir = dir
	cmd.Env = append(os.Environ(), "GOFLAGS=-mod=mod")
	out, err := cmd.CombinedOutput()
	return string(out), err
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "error:", err)
	os.Exit(1)
}
