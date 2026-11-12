#!/usr/bin/env python3
"""[QA-05] The slowest tests of a Gradle run, as a Markdown table (for $GITHUB_STEP_SUMMARY).

Reads the JUnit XML files Gradle writes (build/test-results/**/*.xml) and prints the classes with their
total time and the N slowest single tests, so a test that grows slower shows before the job times out.
With --json it also writes the numbers for the CI trend ([QA-06]).

2.0.0 [RVR-12] (검토 U60-03): the table starts with '건너뜀 N개' and the names of the skipped tests (an assumeTrue that
turned false, an @Ignore), so a test that quietly stopped running shows on the first line.

Usage: python3 tools/slow_tests.py <results dir> [<results dir> …] [--top 20] [--json out.json]
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def read(dirs):
    classes, cases, skipped = {}, [], []
    for d in dirs:
        for f in glob.glob(os.path.join(d, "**", "*.xml"), recursive=True):
            try:
                root = ET.parse(f).getroot()
            except ET.ParseError:
                continue
            for suite in ([root] if root.tag == "testsuite" else root.iter("testsuite")):
                name = suite.get("name", "?")
                c = classes.setdefault(name, [0.0, 0, 0])
                c[0] += float(suite.get("time", "0") or 0)
                c[1] += int(suite.get("tests", "0") or 0)
                c[2] += int(suite.get("failures", "0") or 0) + int(suite.get("errors", "0") or 0)
                for case in suite.iter("testcase"):
                    cls = (case.get("classname") or name).rsplit(".", 1)[-1]
                    cases.append((float(case.get("time", "0") or 0), cls, case.get("name", "?")))
                    s = case.find("skipped")
                    if s is not None:
                        skipped.append((cls, case.get("name", "?"), (s.get("message") or "").strip().splitlines()[:1]))
    return classes, cases, skipped


def skipped_lines(skipped, top):
    """[RVR-12] '건너뜀 N개' and up to [top] names, each with the first line of its reason."""
    if not skipped:
        return ["**건너뜀 0개**\n"]
    out = [f"**건너뜀 {len(skipped)}개** (assumeTrue가 거짓이거나 @Ignore: 이 시험들은 돌지 않았어요)\n"]
    for cls, name, why in sorted(skipped)[:top]:
        out.append(f"- {cls}.{name}" + (f": {why[0][:120]}" if why and why[0] else ""))
    if len(skipped) > top:
        out.append(f"- … {len(skipped) - top}개 더")
    return out + [""]


def table(classes, cases, top, skipped=()):
    total = sum(c[0] for c in classes.values())
    count = sum(c[1] for c in classes.values())
    out = skipped_lines(list(skipped), top) + [f"**{count} tests in {len(classes)} classes, {total / 60:.1f} min of test time** (forks run side by side)\n",
           "| class | tests | min | failed |", "| --- | ---: | ---: | ---: |"]
    for name, (t, n, fail) in sorted(classes.items(), key=lambda kv: -kv[1][0])[:top]:
        out.append(f"| {name.rsplit('.', 1)[-1]} | {n} | {t / 60:.1f} | {fail} |")
    out += ["", f"| slowest {top} tests | class | s |", "| --- | --- | ---: |"]
    for t, cls, name in sorted(cases, reverse=True)[:top]:
        out.append(f"| {name} | {cls} | {t:.1f} |")
    return "\n".join(out)


def main(argv):
    top, out_json, dirs = 20, None, []
    i = 0
    while i < len(argv):
        if argv[i] in ("--top", "--json"):
            if argv[i] == "--top":
                top = int(argv[i + 1])
            else:
                out_json = argv[i + 1]
            i += 2
        else:
            dirs.append(argv[i])
            i += 1
    if not dirs:
        print(__doc__)
        return 2
    classes, cases, skipped = read(dirs)
    print(table(classes, cases, top, skipped))
    if out_json:
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump({
                "tests": sum(c[1] for c in classes.values()),
                "seconds": round(sum(c[0] for c in classes.values()), 1),
                "skipped": [f"{c}.{n}" for c, n, _ in sorted(skipped)],
                "classes": {k.rsplit(".", 1)[-1]: round(v[0], 1) for k, v in classes.items()},
                "slowest": [{"test": n, "class": c, "s": round(t, 1)} for t, c, n in sorted(cases, reverse=True)[:top]],
            }, f, ensure_ascii=False, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
