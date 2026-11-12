#!/usr/bin/env python3
"""[QA-05] The slowest tests of a Gradle run, as a Markdown table (for $GITHUB_STEP_SUMMARY).

Reads the JUnit XML files Gradle writes (build/test-results/**/*.xml) and prints the classes with their
total time and the N slowest single tests, so a test that grows slower shows before the job times out.
With --json it also writes the numbers for the CI trend ([QA-06]).

2.0.0 [RVR-12] (검토 U60-03): the table starts with '건너뜀 N개' and the names of the skipped tests (an assumeTrue that
turned false, an @Ignore), so a test that quietly stopped running shows on the first line.

2.0.0 [U60-01] (검토 2026-11-12) With --base <json> (the same job's --json from the base branch's last run, .github/workflows/android.yml),
the table starts with the failures sorted: '이 PR에서 처음 깨짐' (passed on the base), '기준에서도 깨짐' (failing there too: a date or
something outside the code, not this change) and '기준 결과 없음'. The --json now keeps the failed tests with their file:line.

Usage: python3 tools/slow_tests.py <results dir> [<results dir> …] [--top 20] [--json out.json] [--base base.json]
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import date_watch  # noqa: E402


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


def failed_lines(failed, base, has_base):
    """[U60-01] The failures first, each said against the base branch's last run."""
    if not failed:
        return []
    said = dict(date_watch.against(base if has_base else None, [f["name"] for f in failed]))
    at = {f["name"]: f["at"] for f in failed}
    groups = [("이 PR에서 처음 깨짐", "기준에서는 통과"), ("기준에서도 깨짐(날짜·바깥 탓일 수 있어요, 이 바뀜 때문이 아니에요)", "기준에서도 깨짐"),
              ("새 시험이거나 기준 결과 없음", None)]
    out = [f"**실패 {len(failed)}개**" + ("" if has_base else " (기준 브랜치 결과를 못 받아 견주지 않았어요)")]
    for title, key in groups:
        names = [n for n, how in said.items() if (how == key if key else how not in ("기준에서는 통과", "기준에서도 깨짐"))]
        if names:
            out.append(f"- {title} {len(names)}개: " + ", ".join(f"`{n}`" + (f" ({at[n]})" if at[n] else "") for n in names[:15])
                       + (f" … {len(names) - 15}개 더" if len(names) > 15 else ""))
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
    top, out_json, base_json, dirs = 20, None, None, []
    i = 0
    while i < len(argv):
        if argv[i] in ("--top", "--json", "--base"):
            if argv[i] == "--top":
                top = int(argv[i + 1])
            elif argv[i] == "--json":
                out_json = argv[i + 1]
            else:
                base_json = argv[i + 1]
            i += 2
        else:
            dirs.append(argv[i])
            i += 1
    if not dirs:
        print(__doc__)
        return 2
    classes, cases, skipped = read(dirs)
    failed = date_watch.read(dirs)["failed"]
    base = date_watch.load(base_json) if base_json else None
    lines = failed_lines(failed, base, base is not None)
    if lines:
        print("\n".join(lines))
    print(table(classes, cases, top, skipped))
    if out_json:
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump({
                "tests": sum(c[1] for c in classes.values()),
                "seconds": round(sum(c[0] for c in classes.values()), 1),
                "skipped": [f"{c}.{n}" for c, n, _ in sorted(skipped)],
                "failed": failed,
                "sha": os.environ.get("GITHUB_SHA", ""),
                "classes": {k.rsplit(".", 1)[-1]: round(v[0], 1) for k, v in classes.items()},
                "slowest": [{"test": n, "class": c, "s": round(t, 1)} for t, c, n in sorted(cases, reverse=True)[:top]],
            }, f, ensure_ascii=False, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
