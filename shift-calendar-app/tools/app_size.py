#!/usr/bin/env python3
"""[PERF-04] How big the release files are, what makes them big, and whether they stay inside the budget.

Prints a Markdown table (for $GITHUB_STEP_SUMMARY) with each file's size and its largest entries, and
fails (exit code 1) when a file is over its budget. The budgets are the 1.23.0 sizes plus about 20 %:
a larger file is a decision (write it here), not an accident.

Usage: python3 tools/app_size.py <file.apk|file.aab> … [--top 15] [--json out.json]
"""
import json
import os
import sys
import zipfile

# Bytes. Measured 2026-10-23 on 1.23 (OCR model no longer bundled, Korean only): APK 7.75 MB, AAB 19.4 MB.
# 1.30.0: an AAB is measured without BUNDLE-METADATA. That folder is the R8 name map and build notes Play keeps
# for crash reports; it never reaches a phone, and the map alone grew past the old 23.5 MB budget while the APK
# stayed inside. Without it the 1.30.0 AAB is 9.31 MB, so its budget is 11 MB.
BUDGETS = {".apk": 9_500_000, ".aab": 11_000_000}
NOT_SHIPPED = "BUNDLE-METADATA/"


def entries(path):
    with zipfile.ZipFile(path) as z:
        return sorted(((i.compress_size, i.file_size, i.filename) for i in z.infolist()), reverse=True)


def counted(path):
    """The bytes the budget looks at: the whole file, minus what Play keeps to itself in an AAB."""
    size = os.path.getsize(path)
    if path.endswith(".aab"):
        size -= sum(packed for packed, _, name in entries(path) if name.startswith(NOT_SHIPPED))
    return size


def main(argv):
    top, out_json, files = 15, None, []
    i = 0
    while i < len(argv):
        if argv[i] == "--top":
            top = int(argv[i + 1])
            i += 2
        elif argv[i] == "--json":
            out_json = argv[i + 1]
            i += 2
        else:
            files.append(argv[i])
            i += 1
    if not files:
        print(__doc__)
        return 2
    over, report = [], {}
    for f in files:
        size, size_counted = os.path.getsize(f), counted(f)
        budget = BUDGETS.get(os.path.splitext(f)[1])
        report[os.path.basename(f)] = size
        mark = "" if budget is None or size_counted <= budget else " **over budget**"
        shipped = f", {size_counted / 1e6:.2f} MB without {NOT_SHIPPED}" if size_counted != size else ""
        print(f"### {os.path.basename(f)}: {size / 1e6:.2f} MB{shipped}" + (f" (budget {budget / 1e6:.1f} MB)" if budget else "") + mark)
        print("\n| entry | packed KB | raw KB |\n| --- | ---: | ---: |")
        for packed, raw, name in entries(f)[:top]:
            print(f"| {name} | {packed / 1e3:.0f} | {raw / 1e3:.0f} |")
        print()
        if mark:
            over.append(f"{f} {size_counted} > {budget}")
    if out_json:
        with open(out_json, "w", encoding="utf-8") as out:
            json.dump(report, out, indent=1)
    for o in over:
        print("over budget:", o, file=sys.stderr)
    return 1 if over else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
