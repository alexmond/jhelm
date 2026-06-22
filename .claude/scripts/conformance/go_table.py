#!/usr/bin/env python3
"""Extract a Go test table of string-literal rows into a base64 TSV for the gotmpl4j
conformance suites, so the extraction runs as an allowlisted command instead of an
ad-hoc heredoc.

Usage:
  go_table.py <go_test_file> <TestFuncName> <out.tsv> [--func NAME] [--fields 2|3]

  --fields 2  rows look like `{ INLIT, WANTLIT }`            (default)
  --fields 3  rows look like `{ NAMELIT, INLIT, WANTLIT }`   (in = 2nd, want = 3rd)
  --func      prepend a function-name column (for dispatching tests); omit for none

Each output row is `[func\\t]base64(in)\\tbase64(want)`. Go string literals (both
interpreted "..." and raw `...`) are decoded, including \\x \\u \\U escapes.
"""
import sys, re, base64, argparse

GOLIT = r'(`[^`]*`|"(?:[^"\\]|\\.)*")'


def godec(seg):
    if seg.startswith('`'):
        return seg[1:-1]
    s, out, i = seg[1:-1], [], 0
    simple = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\', "'": "'",
              'a': '\a', 'b': '\b', 'f': '\f', 'v': '\v', '0': '\x00'}
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
            elif n == 'U':
                out.append(chr(int(s[i + 2:i + 10], 16))); i += 10
            else:
                out.append(n); i += 2
        else:
            out.append(c); i += 1
    return ''.join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('go_file')
    ap.add_argument('test_func')
    ap.add_argument('out_tsv')
    ap.add_argument('--func', default=None)
    ap.add_argument('--fields', type=int, default=2)
    ap.add_argument('--single', nargs=2, metavar=('IN_VAR', 'WANT_VAR'), default=None,
                    help='extract one row from `IN_VAR := (...)` and `WANT_VAR := (...)`')
    a = ap.parse_args()

    src = open(a.go_file).read()
    start = src.index('func ' + a.test_func)
    body = src[start:src.index('\n}\n', start)]

    pre = (a.func + '\t') if a.func else ''
    if a.single:
        invar, wantvar = a.single
        iblk = re.search(invar + r' := \((.*?)\)\s*\n', body, re.S).group(1)
        wblk = re.search(wantvar + r' := \((.*?)\)\s*\n', body, re.S).group(1)
        ival = ''.join(godec(m.group(0)) for m in re.finditer(GOLIT, iblk))
        wval = ''.join(godec(m.group(0)) for m in re.finditer(GOLIT, wblk))
        with open(a.out_tsv, 'w') as f:
            f.write(pre + base64.b64encode(ival.encode()).decode() + '\t'
                    + base64.b64encode(wval.encode()).decode() + '\n')
        print(f'wrote 1 single-case row from {a.test_func} -> {a.out_tsv}')
        return

    if a.fields == 3:
        pat = re.compile(r'\{\s*' + GOLIT + r',\s*' + GOLIT + r',\s*' + GOLIT + r',?\s*\}', re.S)
        pairs = [(godec(m.group(2)), godec(m.group(3))) for m in pat.finditer(body)]
    else:
        pat = re.compile(r'\{\s*' + GOLIT + r',\s*' + GOLIT + r',?\s*\}', re.S)
        pairs = [(godec(m.group(1)), godec(m.group(2))) for m in pat.finditer(body)]

    with open(a.out_tsv, 'w') as f:
        for i, w in pairs:
            f.write(pre + base64.b64encode(i.encode()).decode() + '\t'
                    + base64.b64encode(w.encode()).decode() + '\n')
    print(f'wrote {len(pairs)} rows from {a.test_func} -> {a.out_tsv}')


if __name__ == '__main__':
    main()
