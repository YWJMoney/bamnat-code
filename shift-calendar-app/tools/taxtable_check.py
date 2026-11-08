#!/usr/bin/env python3
"""[P3-ACC] Checks remote/v1/taxtable.json cell by cell against the official 근로소득 간이세액표 PDF.

The PDF is 소득세법 시행령 [별표 2] from 국가법령정보센터 (law.go.kr › 소득세법 시행령 › 별표·서식 › 별표 2 › PDF).
Turn it into text with poppler's `pdftotext -layout`, then:

  pdftotext -layout 별표2.pdf /tmp/b2.txt
  python3 tools/taxtable_check.py /tmp/b2.txt            # default table: ../remote/v1/taxtable.json, the first table

Prints the official revision line, the row counts, missing/extra rows and every cell that differs, and the
10,000천원 row; exits 1 on any difference. 2026-11-05: 646 rows × 11 columns, 0 differences (개정 2026. 2. 27.).
"""
import json
import pathlib
import re
import sys

CELL = r'(?:[\d,]+|-)'
ROW = re.compile(r'^\s*([\d,]+)\s+([\d,]+)\s+(' + CELL + r'(?:\s+' + CELL + r'){10})\s*$')
TOP = re.compile(r'^\s*10,000천원\s+((?:[\d,]+\s+){10}[\d,]+)\s*$')


def num(v):
    return 0 if v == '-' else int(v.replace(',', ''))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    text = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8').splitlines()
    table_path = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(__file__).resolve().parents[2] / 'remote/v1/taxtable.json'
    index = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    head = next((l.strip() for l in text if '별표 2' in l), '?')
    official, top = {}, None
    for line in text:
        m = ROW.match(line)
        if m:
            official[(num(m.group(1)), num(m.group(2)))] = [num(v) for v in m.group(3).split()]
            continue
        t = TOP.match(line)
        if t:
            top = [num(v) for v in t.group(1).split()]
    table = json.loads(table_path.read_text(encoding='utf-8'))['data']['tables'][index]
    app = {(r[0], r[1]): list(r[2:]) for r in table['rows']}
    print(head)
    print(f"official rows {len(official)}, app rows {len(app)} ({table['from']}, {table['source']})")
    bad = 0
    for k in sorted(set(official) - set(app)):
        print('missing in app', k); bad += 1
    for k in sorted(set(app) - set(official)):
        print('extra in app', k); bad += 1
    for k in sorted(set(official) & set(app)):
        for i, (o, a) in enumerate(zip(official[k], app[k])):
            if o != a:
                print(f'{k[0]}~{k[1]}천원 가족 {i + 1}명: 원문 {o:,} 앱 {a:,}'); bad += 1
    if top is None:
        print('10,000천원 row not found in the text'); bad += 1
    elif top != list(table['top']):
        print('10,000천원 row differs', top, table['top']); bad += 1
    print('differences', bad)
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
