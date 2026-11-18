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
# 1.36.0 (2026-11-04, 출시 때 다시 정함 — 1.37-인계 278줄): 1.35.0 APK 10.35MB에서 이미 넘었고, 1.36(스튜디오·장터·FCM)으로
# APK 11.73MB, AAB 11.82MB가 됐어요. 늘어난 몫은 거의 dex라 지금 크기 + 약 7%로 잡고, 1.37에서 줄일 곳을 찾아요.
BUDGETS = {".apk": 12_500_000, ".aab": 12_500_000}
# 2.0.0 [RVR-19] (검토 TEST-20 ②) The watch's AAB has its own: under the phone's 12.5 MB it could double unnoticed. 1.39.0's was
# 1.78 MB without BUNDLE-METADATA (3.83 MB whole), so about 20 % more. A file with 'wear' in its name is the watch's.
WATCH_BUDGET = 2_200_000
NOT_SHIPPED = "BUNDLE-METADATA/"


def budget_of(path):
    """The budget for [path]: the watch's for a wear file, else by its kind (.apk, .aab); None for anything else."""
    if "wear" in os.path.basename(path).lower():
        return WATCH_BUDGET
    return BUDGETS.get(os.path.splitext(path)[1])


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
        budget = budget_of(f)
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
