#!/usr/bin/env python3
"""Extract Masterminds/sprig Go test cases (the `tpl := ...; runt(tpl, expect)` and inline
`runt(`...`, expect)` patterns) into a base64 TSV for gotmpl4j-sprig conformance.

Usage: sprig_table.py <sprig_test.go> <out.tsv>

Only no-variable cases are extracted: `runt(...)` (and `runtv(..., emptyMap)`); cases
with non-empty `runtv(..., vars)` are skipped (they need a Go data structure). Each output
row is `base64(template)\\tbase64(expected)`.
"""
import sys, re, base64

GOLIT = r'`[^`]*`|"(?:[^"\\]|\\.)*"'


def godec(seg):
    if seg.startswith('`'):
        return seg[1:-1]
    s, out, i = seg[1:-1], [], 0
    simple = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\', "'": "'"}
    while i < len(s):
        c = s[i]
        if c == '\\':
            n = s[i + 1]
            if n in simple:
                out.append(simple[n]); i += 2
            elif n == 'x':
                out.append(chr(int(s[i + 2:i + 4], 16))); i += 4
            elif n == 'u':
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6
            else:
                out.append(n); i += 2
        else:
            out.append(c); i += 1
    return ''.join(out)


def main():
    src = open(sys.argv[1]).read()
    # Walk assignments and runt/runtv calls in source order, keeping a running view of the
    # most recent value of each variable (tpl is reassigned per test).
    assign = re.compile(r'(\w+)\s*:?=\s*(' + GOLIT + r')\s*$', re.M)
    call = re.compile(r'\brunt(v)?\(\s*(\w+|' + GOLIT + r')\s*,\s*(' + GOLIT + r')\s*(,\s*([^)]*))?\)')
    events = []
    for m in assign.finditer(src):
        events.append((m.start(), 'a', m.group(1), godec(m.group(2))))
    for m in call.finditer(src):
        events.append((m.start(), 'c', m.group(1), m.group(2), m.group(3), m.group(5)))
    events.sort(key=lambda e: e[0])
    vars = {}
    rows = []
    seen = set()
    for e in events:
        if e[1] == 'a':
            vars[e[2]] = e[3]
        else:
            is_v, arg, expect, varsarg = e[2], e[3], e[4], e[5]
            if is_v and varsarg and 'map[string]string{}' not in varsarg.replace(' ', '') \
                    and varsarg.strip() != 'nil':
                continue  # needs real data
            tpl = vars[arg] if arg in vars else (godec(arg) if arg[0] in '`"' else None)
            if tpl is None:
                continue
            key = (tpl, godec(expect))
            if key in seen:
                continue
            seen.add(key)
            rows.append(key)
    with open(sys.argv[2], 'w') as f:
        for tpl, expect in rows:
            f.write(base64.b64encode(tpl.encode()).decode() + '\t'
                    + base64.b64encode(expect.encode()).decode() + '\n')
    print(f'wrote {len(rows)} no-var cases -> {sys.argv[2]}')


if __name__ == '__main__':
    main()
