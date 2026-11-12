#!/usr/bin/env python3
"""2.0.0 [RVL-68] (검토 SCAN-34) 실기기 판독 기록을 시험 표본으로: 에뮬레이터 ML Kit이 읽은 낱말을 코어 시험이 다시 읽는 파일로.

The reading benches (tools/roster_fixtures.py, photo_bench.py, slip_screen_bench.py) read made-up photos with RapidOCR on a PC,
which reads joined cells ('NOFFOFF') and tilted lines differently from the phone's ML Kit: a bench at 100% can still miss on
the phone (1.38 PC 12차). A debug build logs every reading's words (`adb logcat -s BamnatOcr`, RosterScanner·PayslipScanner).
At each PC check, read the made-up fixture photos on the emulator, save the log, and turn it into a replay file here:

  adb logcat -c   # then read the photo in the app
  adb logcat -d -s BamnatOcr > ocr.txt
  python3 tools/ocr_capture.py ocr.txt --fixture 05-nurse-tilt5 --build <커밋>   # → roster-reads/05-nurse-tilt5-device.tsv
  python3 tools/ocr_capture.py ocr.txt --slip s04 --made-up --build <커밋>       # → slip-screen-device/s04.tsv

Only made-up samples: a roster must be one of core/src/test/resources/roster-photos, or a PC check photo whose truth is already
in roster-reads/<name>-pc.tsv (roster1: /mnt/project-files/roster1.jpg); its truth is copied in and DeviceReadsTest checks every
line against it; a payslip needs --made-up (지어낸 금액·이름만, 실제 명세서는 넣지 않아요).
The log may be UTF-16 (PowerShell's `>`) and in any logcat format; the last reading in it is taken unless --all.
"""
import argparse
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
RES = HERE.parent / "core" / "src" / "test" / "resources"
# "11-12 10:00:00.000  1234  1234 D BamnatOcr: msg", "D/BamnatOcr( 1234): msg", "D/BamnatOcr: msg", or the message alone (-v raw).
PREFIX = re.compile(r"^.*?BamnatOcr\s*(?:\(\s*\d+\))?\s*:\s?")
START = re.compile(r"^# (slip )?read (\d+) of (\d+)")


def decode(raw):
    if raw[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return raw.decode("utf-16")
    return raw.decode("utf-8-sig", errors="replace")


def messages(text):
    """The BamnatOcr lines' messages, in order."""
    out = []
    for line in text.splitlines():
        line = line.rstrip("\r")
        if "BamnatOcr" in line:
            out.append(PREFIX.sub("", line, count=1))
        elif line.startswith("# ") or line.startswith("word\t"):
            out.append(line)  # -v raw
    return out


def captures(msgs, slip):
    """Each capture: the reads of one photo, from '# read 0 of N' to its last word; [(n, [(header, [words])])]."""
    caps = []
    for m in msgs:
        s = START.match(m)
        if s:
            if bool(s.group(1)) != slip:
                continue
            i, n = int(s.group(2)), int(s.group(3))
            if i == 0:
                caps.append((n, []))
            if caps:
                caps[-1][1].append((m, []))
            continue
        if m.startswith("word\t") and caps and caps[-1][1]:
            f = m.split("\t")
            if len(f) >= 7 and all(re.fullmatch(r"-?\d+", x) for x in f[1:5]):
                caps[-1][1][-1][1].append("\t".join(f[:7]))
    return [c for c in caps if c[1] and len(c[1]) == c[0]]


def truth_of(fixture):
    """The drawn truth: a roster-photos fixture's, or a PC photo's already in roster-reads (roster1 → roster1-pc.tsv, its truth lines)."""
    for path in (RES / "roster-photos" / (fixture + ".ocr.tsv"), RES / "roster-reads" / (fixture + "-pc.tsv")):
        if path.exists():
            truth = [l for l in path.read_text(encoding="utf-8").splitlines() if l.startswith("truth\t")]
            if truth:
                return truth
    sys.exit(f"{fixture}: 정답이 있는 표본이 아니에요. roster-photos의 지어낸 표본이나 정답 줄이 있는 roster-reads/<이름>-pc.tsv만 넣어요.")


def render(cap, title, truth):
    n, reads = cap
    lines = [f"# {title}, {n} reads; \"# read\" starts each"] + truth
    for header, words in reads:
        lines.append(header)
        lines.extend(words)
    return "\n".join(lines) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("log")
    kind = ap.add_mutually_exclusive_group(required=True)
    kind.add_argument("--fixture", help="roster-photos 표본 이름(예: 05-nurse-tilt5)")
    kind.add_argument("--slip", help="slip-screen-device에 넣을 이름(예: s04)")
    ap.add_argument("--made-up", action="store_true", help="명세서가 지어낸 표본임을 확인")
    ap.add_argument("--build", default="?", help="읽은 빌드의 커밋")
    ap.add_argument("--all", action="store_true", help="기록의 읽기를 모두 (-2, -3 …을 붙여)")
    ap.add_argument("--out", help="쓸 폴더(시험용)")
    a = ap.parse_args(argv)
    slip = a.slip is not None
    if slip and not a.made_up:
        sys.exit("명세서는 지어낸 표본만 넣어요: --made-up으로 확인해 주세요.")
    caps = captures(messages(decode(pathlib.Path(a.log).read_bytes())), slip)
    if not caps:
        sys.exit(f"{a.log}: BamnatOcr {'slip ' if slip else ''}read 기록이 없어요 (adb logcat -d -s BamnatOcr).")
    if not a.all:
        caps = caps[-1:]
    if slip:
        out = pathlib.Path(a.out) if a.out else RES / "slip-screen-device"
        name, title, truth = a.slip, f"{a.slip} (표본, 지어낸 금액) ML Kit on the emulator ({a.build}), logcat BamnatOcr", []
    else:
        out = pathlib.Path(a.out) if a.out else RES / "roster-reads"
        name, title, truth = a.fixture + "-device", f"{a.fixture} as ML Kit read it on the emulator ({a.build})", truth_of(a.fixture)
    out.mkdir(parents=True, exist_ok=True)
    written = []
    for k, cap in enumerate(caps):
        path = out / (name + (f"-{k + 1}" if len(caps) > 1 else "") + ".tsv")
        path.write_text(render(cap, title, truth), encoding="utf-8")
        written.append(f"{path.name} ({cap[0]} reads, {sum(len(w) for _, w in cap[1])} words)")
    print("\n".join(written))
    return 0


if __name__ == "__main__":
    sys.exit(main())
