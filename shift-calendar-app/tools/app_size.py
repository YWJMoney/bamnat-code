#!/usr/bin/env python3
"""[PERF-04] How big the release files are, what makes them big, and whether they stay inside the budget.

Prints a Markdown table (for $GITHUB_STEP_SUMMARY) with each file's size and its largest entries, and
fails (exit code 1) when a file is over its budget. The budgets are the 1.23.0 sizes plus about 20 %:
a larger file is a decision (write it here), not an accident.

2.0.0 [RVL-60] (검토 DATA-21) Over the budget is a warning (said in bold, exit 0); over it by more than a tenth
(HARD) fails. Before, every release build since 1.38 stopped at 'Size' (APK 13.41 MB against 12.5 MB) before
its files were uploaded, and a check that is always red says nothing. With --mapping (R8's mapping.txt of the
same build) the app's dex code is also added up by package, so what grew is a number, not a guess.

Usage: python3 tools/app_size.py <file.apk|file.aab> … [--top 15] [--json out.json] [--mapping mapping.txt]
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
# 2.0.0 [RVL-60] Over the budget by more than this share fails; up to it, a warning.
HARD = 1.10


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


def uleb(b, i):
    """An unsigned LEB128 at [i]: (value, next index)."""
    out, shift = 0, 0
    while True:
        x = b[i]
        i += 1
        out |= (x & 0x7F) << shift
        if x < 0x80:
            return out, i
        shift += 7


def dex_code(b):
    """2.0.0 [RVL-60] Bytes of code per class in one dex file: {"a.b.C": bytes}, from each method's code item (header, instructions,
    try entries). Fields, strings and annotations are shared and not counted, so the sum is under the file's size."""
    u32 = lambda o: int.from_bytes(b[o:o + 4], "little")
    u16 = lambda o: int.from_bytes(b[o:o + 2], "little")
    strings_off, types_off = u32(0x3C), u32(0x44)
    classes_size, classes_off = u32(0x60), u32(0x64)

    def string(idx):
        o = u32(strings_off + 4 * idx)
        _, o = uleb(b, o)
        end = b.index(0, o)
        return b[o:end].decode("utf-8", "replace")

    out = {}
    for k in range(classes_size):
        d = classes_off + 32 * k
        name = string(u32(types_off + 4 * u32(d)))
        name = name[1:-1].replace("/", ".") if name.startswith("L") else name
        data = u32(d + 24)
        size = 0
        if data:
            i = data
            sf, i = uleb(b, i)
            inf, i = uleb(b, i)
            dm, i = uleb(b, i)
            vm, i = uleb(b, i)
            for _ in range(2 * (sf + inf)):
                _, i = uleb(b, i)
            for _ in range(dm + vm):
                _, i = uleb(b, i)
                _, i = uleb(b, i)
                code, i = uleb(b, i)
                if code:
                    size += 16 + 2 * u32(code + 12) + 8 * u16(code + 6)
        out[name] = out.get(name, 0) + size
    return out


def renamed(mapping_text):
    """R8's mapping, renamed class -> original class (class lines only, so a 300 MB file reads in seconds)."""
    out = {}
    for line in mapping_text.splitlines():
        if line and not line[0].isspace() and not line.startswith("#") and line.endswith(":") and " -> " in line:
            orig, obf = line[:-1].split(" -> ", 1)
            out[obf] = orig
    return out


def package(cls):
    """Where a class is counted: our own by area (com.bamnat.app.ui), others by their library (com.google.android, kotlinx.coroutines)."""
    parts = cls.split(".")[:-1]
    if not parts:
        return "(no package)"
    if cls.startswith(("com.bamnat.app.ui.", "com.google.android.")):
        keep = 5  # the screens by area (ui.recipe, ui.money), Google's by service (gms.ads, gms.measurement)
    elif cls.startswith("com.bamnat."):
        keep = 4
    else:
        keep = 3 if parts[0] in ("com", "org", "io", "net") else 2
    return ".".join(parts[:keep])


def by_package(path, names, top=12):
    """The app's dex code added up by package (originals through [names]), largest first: [(package, bytes)]."""
    sums = {}
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith(".dex") and "/" not in n.replace("base/dex/", ""):
                for cls, size in dex_code(z.read(n)).items():
                    p = package(names.get(cls, cls))
                    sums[p] = sums.get(p, 0) + size
    return sorted(sums.items(), key=lambda kv: -kv[1])[:top]


def main(argv):
    top, out_json, files, mapping = 15, None, [], None
    i = 0
    while i < len(argv):
        if argv[i] == "--top":
            top = int(argv[i + 1])
            i += 2
        elif argv[i] == "--json":
            out_json = argv[i + 1]
            i += 2
        elif argv[i] == "--mapping":
            mapping = argv[i + 1]
            i += 2
        else:
            files.append(argv[i])
            i += 1
    if not files:
        print(__doc__)
        return 2
    over, warned, report = [], [], {}
    for f in files:
        size, size_counted = os.path.getsize(f), counted(f)
        budget = budget_of(f)
        report[os.path.basename(f)] = size
        hard = budget is not None and size_counted > budget * HARD
        soft = budget is not None and size_counted > budget
        mark = " **over budget by more than 10 %**" if hard else " **over budget** (a warning: decide the budget)" if soft else ""
        shipped = f", {size_counted / 1e6:.2f} MB without {NOT_SHIPPED}" if size_counted != size else ""
        print(f"### {os.path.basename(f)}: {size / 1e6:.2f} MB{shipped}" + (f" (budget {budget / 1e6:.1f} MB)" if budget else "") + mark)
        print("\n| entry | packed KB | raw KB |\n| --- | ---: | ---: |")
        for packed, raw, name in entries(f)[:top]:
            print(f"| {name} | {packed / 1e3:.0f} | {raw / 1e3:.0f} |")
        print()
        if mapping and f.endswith(".apk") and "wear" not in os.path.basename(f).lower():
            with open(mapping, encoding="utf-8") as m:
                names = renamed(m.read())
            print(f"#### dex code by package (top 12, through {os.path.basename(mapping)})\n\n| package | KB |\n| --- | ---: |")
            for p, n in by_package(f, names):
                print(f"| {p} | {n / 1e3:.0f} |")
            print()
        if hard:
            over.append(f"{f} {size_counted} > {budget} × {HARD}")
        elif soft:
            warned.append(f"{f} {size_counted} > {budget}")
    if out_json:
        with open(out_json, "w", encoding="utf-8") as out:
            json.dump(report, out, indent=1)
    for w in warned:
        print("over budget (warning):", w, file=sys.stderr)
    for o in over:
        print("over budget:", o, file=sys.stderr)
    return 1 if over else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
