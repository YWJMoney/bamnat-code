#!/usr/bin/env python3
"""[QA-06] 330: 43 key screenshots kept as small fingerprints, so a change nobody meant shows in CI.

A fingerprint is the shot shrunk to 32x48 grey pixels (1.5 KB of hex), not the picture itself: no PNGs in git,
and anti-aliasing or a moved clock digit stays under the threshold while a moved card, a missing button or a
different colour scheme does not.

  python3 tools/shot_hashes.py check  app/build/screenshots   # CI: compare the shots this run made
  python3 tools/shot_hashes.py update app/build/screenshots   # after a change that was meant: write new ones

`check` compares only the shots present (CI makes them in two shards) and fails when one differs by more than
THRESHOLD on average (0~255) or when none of the list was found. Since 1.24 (QA-08) every shot is drawn on a fixed
day and time (ScreenshotBase, 2026-10-14 10:30 KST), so shots built around today's date or the hour are in the list
too; random numbers still are not.

2.0.0 [RVR-11] (검토 TEST-12 ②③·U60-04) a comparison is never lost without a word:
- a key shot whose name no test takes any more (renamed or removed) fails, read from the test sources;
- a name two tests take (one would draw over the other's file) fails;
- with --results <JUnit XML folder>, a key shot whose test class ran in this shard but left no picture fails
  ('비교 못 함'), and the summary lists the key shots of the other shard as not this shard's.

  python3 tools/shot_hashes.py check app/build/screenshots --results app/build/test-results/testDebugUnitTest
  python3 tools/shot_hashes.py names          # only the source checks (no pictures needed)

2.0.0 [RVR-39] (검토 TEST-12 ①·I60b-03) every shot, not just the 43, and a '어제와 다른 장' report:
- `prints` keeps, for every PNG of a run, the exact pixels' sha1 (any change at all, a letter or one colour), a 32x48 grey
  print and an 8x12 colour print (how big and what kind of change). 1,260 shots are about 5 MB of JSON, kept as a CI artifact,
  not in git, so no golden file has to be redrawn for it.
- `diff` compares two runs' prints: shots that differ by even one pixel are listed biggest first with a kind
  (배치: things moved, 색: colours, 작은 바뀜: a few letters or dots), plus new and gone shots, and with --sheet a picture of
  the most changed ones (today's shot beside yesterday's grey print and a heat map of where).

  python3 tools/shot_hashes.py prints app/build/screenshots --out build/shot-prints.json
  python3 tools/shot_hashes.py diff <어제 shot-prints.json> build/shot-prints.json [--shots app/build/screenshots --sheet build/shot-diff.png]
"""
import hashlib
import glob
import json
import os
import re
import sys

try:
    from PIL import Image
except ImportError:  # `names` needs no pictures
    Image = None

GOLDEN = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "golden", "shots.json")
TESTS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "java")
SIZE = (32, 48)
THRESHOLD = 6.0

# 43 screens across the app. 23_widget_today, store_1_calendar (오늘의 흐름 changes with the hour) and 352_folded_teams
# (오늘 N월 N일, the 14 days from today) came back with the fixed clock of 1.24 (QA-08).
SHOTS = [
    "2_folded_day_sheet", "3_folded_stats", "4_folded_settings", "8_unfolded_settings", "9_folded_memos_calendar",
    "13_folded_shift_types", "14_folded_pattern", "24_widget_week", "27_folded_pay_result",
    "28_unfolded_pay_result", "51_folded_money", "53_unfolded_money", "61_folded_setup_style",
    "76_folded_extra_kinds", "82_folded_backup_password", "117_folded_company_detail",
    "134_folded_received_calendar", "140_folded_settings_alarm", "164_phone_calendar_huge_text",
    "190_folded_company_board", "213_folded_calendars_list", "230_folded_ledger_summary", "250_folded_ledger_dark",
    "297_folded_business_calendar", "327_folded_settings_hub", "334_folded_help_home",
    "369_phone_year", "376_phone_pro_launched", "385_phone_pay_report", "430_folded_rest_rules",
    "460_folded_leave", "468_folded_leave_dark", "482_folded_money_customize", "494_folded_settings_backup_photos",
    "520_folded_recommended", "528_phone_settings_seven_groups", "530_folded_settings_privacy_about", "570_j_components_folded",
    "695_n_profile_folded", "store_tablet_year", "23_widget_today", "store_1_calendar", "352_folded_teams",
]


def fingerprint(path):
    im = Image.open(path).convert("L").resize(SIZE, Image.BILINEAR)
    return im.tobytes().hex()


def shot_names(root=None):
    """[RVR-11] Every literal shoot("…") name in the app's tests: name -> [(test class, file)], from the sources. Names a
    test passes another way (the widgets' Triple("23_widget_today", …)) are kept under ("quoted", name) for the key shots."""
    out = {}
    for f in sorted(glob.glob(os.path.join(root or TESTS, "**", "*.kt"), recursive=True)):
        with open(f, encoding="utf-8") as fh:
            text = fh.read()
        cls = os.path.splitext(os.path.basename(f))[0]
        pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
        fq = f"{pkg.group(1)}.{cls}" if pkg else cls
        for m in re.finditer(r'\bshoot\(\s*"([^"$]+)"', text):
            out.setdefault(m.group(1), []).append((fq, os.path.basename(f)))
        for m in re.finditer(r'"([A-Za-z0-9_]+)"', text):
            out.setdefault(("quoted", m.group(1)), []).append((fq, os.path.basename(f)))
    return out


def classes_of(names, name):
    return {w[0] for w in names.get(name, []) + names.get(("quoted", name), [])}


def source_problems(names, golden_names):
    bad = []
    for name in sorted(golden_names):
        if not classes_of(names, name):
            bad.append(f"no test takes '{name}' any more (renamed or removed?): rename it in SHOTS and run update, or drop it")
    for name, where in sorted(((k, v) for k, v in names.items() if isinstance(k, str)), key=lambda kv: kv[0]):
        if len(where) > 1:
            bad.append(f"'{name}' is taken by {len(where)} tests ({', '.join(sorted({w[1] for w in where}))}): one draws over the other")
    return bad


def ran_classes(results):
    """Test classes with a JUnit XML in [results] (the ones this shard ran)."""
    out = set()
    for f in glob.glob(os.path.join(results, "**", "TEST-*.xml"), recursive=True):
        out.add(os.path.basename(f)[len("TEST-"):-len(".xml")])
    return out


def distance(a, b):
    x, y = bytes.fromhex(a), bytes.fromhex(b)
    return sum(abs(p - q) for p, q in zip(x, y)) / len(x)


CSIZE = (8, 12)


def prints(folder):
    """[RVR-39] name -> {px: sha1 of the RGB pixels, g: 32x48 grey hex, c: 8x12 colour hex} for every PNG in folder."""
    out = {}
    for n in sorted(os.listdir(folder)):
        if not n.endswith(".png"):
            continue
        im = Image.open(os.path.join(folder, n)).convert("RGB")
        out[n[:-4]] = {"px": hashlib.sha1(im.tobytes() + f"{im.size}".encode()).hexdigest(),
                       "g": im.convert("L").resize(SIZE, Image.BILINEAR).tobytes().hex(),
                       "c": im.resize(CSIZE, Image.BILINEAR).tobytes().hex()}
    return out


def edges(g):
    """Where the 32x48 grey print has an edge (a neighbour differs by more than 12)."""
    x, w, h = bytes.fromhex(g), SIZE[0], SIZE[1]
    out = set()
    for j in range(h):
        for i in range(w):
            p = x[j * w + i]
            if (i + 1 < w and abs(p - x[j * w + i + 1]) > 12) or (j + 1 < h and abs(p - x[(j + 1) * w + i]) > 12):
                out.add(j * w + i)
    return out


def kind(grey, colour, old_g=None, new_g=None):
    """작은 바뀜: a few letters or dots; 색: the same edges in other colours (a colour, the dark theme); 배치: things moved."""
    if grey < 1.0 and colour < 2.0:
        return "작은 바뀜"
    if old_g is not None:
        a, b = edges(old_g), edges(new_g)
        if not a and not b or (a | b and len(a & b) / len(a | b) >= 0.8):
            return "색"
    return "배치"


def compare_prints(old, new):
    """[RVR-39] -> (changed [(name, grey, colour, kind)] biggest first, new names, gone names, same count)."""
    changed, same = [], 0
    for name in sorted(set(old) & set(new)):
        a, b = old[name], new[name]
        if a["px"] == b["px"]:
            same += 1
            continue
        g, c = distance(a["g"], b["g"]), distance(a["c"], b["c"])
        changed.append((name, g, c, kind(g, c, a["g"], b["g"])))
    changed.sort(key=lambda x: (-(x[1] + x[2]), x[0]))
    return changed, sorted(set(new) - set(old)), sorted(set(old) - set(new)), same


def sheet(changed, old, folder, out, top=24):
    """Today's shot | yesterday's grey print | where it changed, for the most changed shots (one row each)."""
    rows = [c for c in changed if os.path.exists(os.path.join(folder, c[0] + ".png"))][:top]
    if not rows:
        return 0
    W, H = 120, 180
    canvas = Image.new("RGB", (3 * W + 40, len(rows) * (H + 24) + 10), "white")
    from PIL import ImageDraw
    draw = ImageDraw.Draw(canvas)
    for i, (name, g, c, k) in enumerate(rows):
        y = 10 + i * (H + 24)
        now = Image.open(os.path.join(folder, name + ".png")).convert("RGB")
        was = Image.frombytes("L", SIZE, bytes.fromhex(old[name]["g"])).resize((W, H), Image.NEAREST)
        heat = Image.frombytes("L", SIZE, bytes(min(255, abs(p - q) * 4) for p, q in zip(
            now.convert("L").resize(SIZE, Image.BILINEAR).tobytes(), bytes.fromhex(old[name]["g"])))).resize((W, H), Image.NEAREST)
        canvas.paste(now.resize((W, H)), (10, y + 18))
        canvas.paste(was.convert("RGB"), (20 + W, y + 18))
        canvas.paste(Image.merge("RGB", (heat, Image.new("L", (W, H)), Image.new("L", (W, H)))), (30 + 2 * W, y + 18))
        draw.text((10, y + 2), f"{name}  {k}  {g:.1f}/{c:.1f}", fill="black")
    canvas.save(out)
    return len(rows)


def diff_main(argv):
    folder = sheet_out = None
    if "--shots" in argv:
        i = argv.index("--shots"); folder = argv[i + 1]; argv = argv[:i] + argv[i + 2:]
    if "--sheet" in argv:
        i = argv.index("--sheet"); sheet_out = argv[i + 1]; argv = argv[:i] + argv[i + 2:]
    if len(argv) != 2:
        print(__doc__)
        return 2
    old, new = (json.load(open(f, encoding="utf-8")) for f in argv)
    changed, added, gone, same = compare_prints(old, new)
    print(f"### 어제와 다른 화면 사진: {len(changed)}장 (같음 {same}, 새로 {len(added)}, 없어짐 {len(gone)})\n")
    if changed:
        print("| 사진 | 무엇이 | 회색 | 색 |\n| --- | --- | ---: | ---: |")
        for name, g, c, k in changed:
            print(f"| {name} | {k} | {g:.2f} | {c:.2f} |")
    for title, names in (("새로 생긴 사진", added), ("없어진 사진", gone)):
        if names:
            print(f"\n{title} {len(names)}장: " + ", ".join(names[:40]) + (" …" if len(names) > 40 else ""))
    if sheet_out and folder and changed:
        n = sheet(changed, old, folder, sheet_out)
        print(f"\n가장 많이 바뀐 {n}장을 나란히(오늘 · 어제 회색 · 바뀐 곳): 결과 묶음 shot-diff")
    return 0


def main(argv):
    if argv[:1] == ["prints"]:
        if len(argv) != 4 or argv[2] != "--out":
            print(__doc__)
            return 2
        p = prints(argv[1])
        os.makedirs(os.path.dirname(os.path.abspath(argv[3])), exist_ok=True)
        with open(argv[3], "w", encoding="utf-8") as fh:
            json.dump(p, fh, sort_keys=True)
        print(f"{len(p)}장의 지문을 {argv[3]}에 적었어요")
        return 0 if p else 1
    if argv[:1] == ["diff"]:
        return diff_main(argv[1:])
    results = None
    if "--results" in argv:
        i = argv.index("--results")
        results = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if argv[:1] == ["names"]:
        bad = source_problems(shot_names(), SHOTS)
        for b in bad:
            print(b, file=sys.stderr)
        print(f"{len(SHOTS)} key shots, all taken by one test each" if not bad else f"{len(bad)} problem(s)")
        return 1 if bad else 0
    if len(argv) != 2 or argv[0] not in ("check", "update"):
        print(__doc__)
        return 2
    mode, folder = argv
    if not os.path.isdir(folder):
        print(f"no screenshots folder at {folder}: the screenshot tests did not run (or Gradle took them from the "
              "build cache without their output folder — app/build.gradle.kts declares it as an output)")
        return 1
    present = {n[:-4]: os.path.join(folder, n) for n in os.listdir(folder) if n.endswith(".png")}
    if mode == "update":
        golden = {}
        if os.path.exists(GOLDEN):
            golden = json.load(open(GOLDEN, encoding="utf-8"))
        for name in SHOTS:
            if name in present:
                golden[name] = fingerprint(present[name])
        golden = {k: golden[k] for k in SHOTS if k in golden}
        os.makedirs(os.path.dirname(GOLDEN), exist_ok=True)
        with open(GOLDEN, "w", encoding="utf-8") as out:
            json.dump(golden, out, indent=0, sort_keys=True)
            out.write("\n")
        missing = [s for s in SHOTS if s not in golden]
        print(f"wrote {len(golden)} fingerprints" + (f"; not found: {missing}" if missing else ""))
        return 0
    with open(GOLDEN, encoding="utf-8") as fh:
        golden = json.load(fh)
    names = shot_names()
    problems = source_problems(names, golden)
    ran = ran_classes(results) if results else None
    seen, bad, lost, other = 0, [], [], []
    print("| shot | difference |\n| --- | ---: |")
    for name, want in sorted(golden.items()):
        if name not in present:
            classes = classes_of(names, name)
            if ran is not None and classes & ran:
                lost.append(name)
                print(f"| {name} | **비교 못 함** (its test ran here, no picture) |")
            elif ran is not None:
                other.append(name)
            continue
        seen += 1
        d = distance(want, fingerprint(present[name]))
        print(f"| {name} | {d:.2f}{' **changed**' if d > THRESHOLD else ''} |")
        if d > THRESHOLD:
            bad.append(name)
    if other:
        print(f"\n{len(other)} key shots belong to the other shard.")
    if seen == 0:
        print("none of the key screenshots was found", file=sys.stderr)
        return 1
    for name in bad:
        print(f"changed: {name} (if it was meant, run: python3 tools/shot_hashes.py update app/build/screenshots)", file=sys.stderr)
    for name in lost:
        print(f"not compared: {name} (its test class ran in this shard but drew no picture: did it fail, skip, or change its name?)", file=sys.stderr)
    for p in problems:
        print(p, file=sys.stderr)
    return 1 if bad or lost or problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
