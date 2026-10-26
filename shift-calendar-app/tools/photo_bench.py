#!/usr/bin/env python3
"""[READ-12][READ-13] Hard photo bench (1.27 순번 446, 사용자 2026-10-25 "사진 인식 기술을 비약적으로 향상").

1.25 made 16 roster and 9 payslip photos (tools/roster_fixtures.py, tools/payslip_fixtures.py) and read each once. The
phone reads each photo once too, so a cell the reading lost or misread stayed lost (or, worse, went in wrong). This
bench draws harder photos — low light, a screen photographed (moire, glare), small print, strong perspective, a big
shadow, marks drawn over cells (a pen circle, a highlighter), a leave written once across merged cells, heavy blur, a
dark-mode capture — and reads every photo the way 1.27 does on the phone: as taken, and made over in four ways
(core `PhotoVariants`, mirrored here in numpy):

  orig     the photo as it is
  contrast grey, contrast ×1.6 around the middle (1.25's second look)
  flat     the paper's light evened out: each pixel over the paper's brightness around it (shade and dim light go)
  binary   flat, then black or white by the mean of its neighbourhood (Bradley adaptive threshold)
  up2      flat at twice the size (small print)
  invert   for a dark capture: light letters on dark turned dark on light

Each reading goes to core/src/test/resources/photo-bench/<kind>/<case>.<variant>.tsv, the same columns as the 1.25
fixtures (truth lines; one word or line a row with the text drawn there last). PhotoBenchTest reads each case once
(orig) and all together (RosterVote, SlipVote, FillVote), and checks two things the user asked for: clean photos all
right, and a wrong value never goes in without a yellow mark.

Setup as tools/roster_fixtures.py (RapidOCR 1.2.3 + PP-OCRv4 + korean_mobile_v2.0, Nanum fonts in /tmp/fonts).
Run: python3 tools/photo_bench.py [--images DIR] [--only NAME] [--kind roster|payslip|fill]
"""
import argparse
import calendar
import math
import os
import random
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import roster_fixtures as rf  # noqa: E402
import payslip_fixtures as pf  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "test", "resources", "photo-bench")

# ---------------------------------------------------------------- variants (mirror of core PhotoVariants)


def gray(a):
    b, g, r = a[:, :, 0].astype(np.int32), a[:, :, 1].astype(np.int32), a[:, :, 2].astype(np.int32)
    return ((r * 299 + g * 587 + b * 114) // 1000).astype(np.uint8)


def contrast(g, c=1.6):
    return np.clip((g.astype(np.float32) - 128) * c + 128, 0, 255).astype(np.uint8)


def block_of(w, h):
    return max(8, max(w, h) // 64)


def background(g):
    """The paper's brightness around each pixel: the brightest of each block, smoothed twice, spread back out."""
    h, w = g.shape
    k = block_of(w, h)
    bw, bh = (w + k - 1) // k, (h + k - 1) // k
    pad = np.pad(g, ((0, bh * k - h), (0, bw * k - w)), mode="edge")
    small = pad.reshape(bh, k, bw, k).max(axis=(1, 3)).astype(np.float32)
    for _ in range(2):
        small = cv2.blur(small, (3, 3), borderType=cv2.BORDER_REPLICATE)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)


def flat(g, gain=1.6):
    bg = np.maximum(background(g), 24.0)
    ratio = g.astype(np.float32) / bg
    return np.clip(255 * (1 - (1 - ratio) * gain), 0, 255).astype(np.uint8)


def binary(g, k=0.12):
    h, w = g.shape
    win = max(15, max(w, h) // 50) | 1
    mean = cv2.blur(g.astype(np.float32), (win, win), borderType=cv2.BORDER_REPLICATE)
    return np.where(g.astype(np.float32) < mean * (1 - k), 0, 255).astype(np.uint8)


def up2(g):
    return cv2.resize(g, (g.shape[1] * 2, g.shape[0] * 2), interpolation=cv2.INTER_LINEAR)


def variants(a):
    """Name → (picture to read, factor back to the photo's pixels)."""
    g = gray(a)
    f = flat(g)
    out = {"orig": (a, 1.0), "contrast": (contrast(g), 1.0), "flat": (f, 1.0), "binary": (binary(f), 1.0), "up2": (up2(f), 0.5)}
    if g.mean() < 110:
        out["invert"] = (255 - g, 1.0)
    return out


def bgr(g):
    return g if g.ndim == 3 else cv2.cvtColor(g, cv2.COLOR_GRAY2BGR)

# ---------------------------------------------------------------- harder photos


def harder(a, rng, *, dark=0.0, moire=0.0, glare=0.0, marks=None):
    """After rf.photograph: dim light (darker, warmer, noisier), a screen's moire and glare. Geometry stays."""
    a = a.astype(np.float32)
    h, w = a.shape[:2]
    if dark:
        a = a * (1 - dark)
        a[:, :, 0] *= 0.85  # less blue under a lamp
        a = a + np.random.default_rng(rng.randint(0, 1 << 30)).normal(0, 6 + 10 * dark, a.shape)
    if moire:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        wave = np.sin((xx * 0.93 + yy * 0.35) * 0.9) * np.sin((xx * 0.21 - yy * 0.97) * 0.85)
        a = a + wave[:, :, None] * 40 * moire
        a[:, ::3, 2] *= 1 - 0.15 * moire  # sub-pixel stripes
    if glare:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        cx, cy = w * rng.uniform(0.3, 0.7), h * rng.uniform(0.3, 0.6)
        blob = np.exp(-(((xx - cx) / (w * 0.18)) ** 2 + ((yy - cy) / (h * 0.15)) ** 2))
        a = a + blob[:, :, None] * 160 * glare
    return np.clip(a, 0, 255).astype(np.uint8)


def marked(sheet, rng, share=0.06):
    """A pen circle or a highlighter over some cells, as a nurse marks her own line."""
    d = sheet.d
    cells = [w for w in sheet.drawn if w[5] is not None]
    for text, l, t, r, b, cell in rng.sample(cells, max(1, int(len(cells) * share))):
        if rng.random() < 0.5:
            d.ellipse((l - 10, t - 8, r + 10, b + 8), outline=(200, 30, 30), width=3)
        else:
            d.rectangle((l - 8, t - 2, r + 8, b + 2), fill=None, outline=(240, 220, 40), width=6)
    return sheet


def merged_leave(rng, year, month, people, pattern, size=26, cell=64):
    """A 연차 written once across three days (merged cells): those days are 연차 by the table, one word on the photo."""
    s, truth = rf.horizontal(rng, year, month, people, pattern, size=size, cell=cell)
    p = people[1]
    start = 9
    x0 = 60 + 170
    # Clear the three cells and write one word across them.
    row_h = int(size * 2.0)
    y = 60 + 90 + row_h * (2 + 1) + row_h // 2
    s.d.rectangle((x0 + (start - 1) * cell + 2, y - row_h // 2 + 3, x0 + (start + 2) * cell - 2, y + row_h // 2 - 3), fill=rf.PAPER)
    s.drawn = [w for w in s.drawn if not (w[5] == (p, start) or w[5] == (p, start + 1) or w[5] == (p, start + 2))]
    s.text((x0 + (start - 1) * cell + cell * 3 // 2, y), "연차", size, cell=(p, start + 1))
    for d in range(start, start + 3):
        truth[p][d] = "연차"
    return s, truth


L = rf.LATIN
N = rf.NAMES
ROSTER = [
    # name, layout, photo, after
    ("r01-clean", lambda r: rf.horizontal(r, 2026, 11, N[:5], L), dict(), dict()),
    ("r02-lowlight", lambda r: rf.horizontal(r, 2026, 11, N[:5], L), dict(angle=1.5, blur=0.8), dict(dark=0.6)),
    ("r03-screen", lambda r: rf.horizontal(r, 2026, 10, N[:6], L, size=22, cell=52), dict(angle=-1.0, blur=0.7), dict(moire=0.8, glare=0.5)),
    ("r04-small", lambda r: rf.horizontal(r, 2026, 12, N[:8], L, size=15, cell=34), dict(angle=0.8, blur=0.5, jpeg=70), dict()),
    ("r05-perspective", lambda r: rf.horizontal(r, 2026, 10, N[:6], L), dict(angle=2.5, persp=0.045, blur=0.6), dict()),
    ("r06-shadow", lambda r: rf.horizontal(r, 2026, 11, N[:6], L), dict(angle=-2.0, shadow=0.7, noise=6), dict()),
    ("r07-marks", lambda r: (lambda s_t: (marked(s_t[0], r), s_t[1]))(rf.horizontal(r, 2026, 10, N[:5], L)), dict(angle=1.2, blur=0.6), dict()),
    ("r08-merged", lambda r: merged_leave(r, 2026, 10, N[:5], L), dict(angle=-1.5, blur=0.6), dict()),
    ("r09-blur", lambda r: rf.horizontal(r, 2026, 11, N[:5], L), dict(angle=1.0, blur=2.0, jpeg=60), dict()),
    ("r10-team-dim", lambda r: rf.horizontal(r, 2026, 11, ["1조", "2조", "3조", "4조"], rf.TEAM, weekday=False), dict(angle=-2.0, persp=0.02), dict(dark=0.45)),
    ("r11-korean-screen", lambda r: rf.horizontal(r, 2026, 10, N[:4], rf.KOREAN, cell=80, size=24), dict(angle=1.0, blur=0.6), dict(moire=0.6)),
    ("r12-vertical-dim", lambda r: rf.transposed(r, 2026, 10, N[:4], L), dict(angle=1.5, blur=0.6), dict(dark=0.4)),
    ("r13-hand-shadow", lambda r: rf.horizontal(r, 2026, 10, N[:4], L, face="NanumPenScript-Regular.ttf", size=34, cell=70), dict(angle=2.0, shadow=0.5), dict()),
    ("r14-tight-glare", lambda r: rf.horizontal(r, 2026, 12, N[:7], L, cell=46, size=22), dict(angle=-1.2, blur=0.6), dict(glare=0.7)),
]


def slip_side(**k):
    return pf.side_by_side(**k)


PAYSLIP = [
    ("p01-clean", lambda: slip_side(), dict(), dict()),
    ("p02-lowlight", lambda: slip_side(), dict(angle=1.5, blur=0.8), dict(dark=0.6)),
    ("p03-screen", lambda: slip_side(size=22), dict(angle=-1.0, blur=0.6), dict(moire=0.8, glare=0.4)),
    ("p04-small", lambda: slip_side(size=17), dict(angle=0.8, blur=0.5, jpeg=65), dict()),
    ("p05-perspective", lambda: slip_side(hours=True), dict(angle=2.5, persp=0.04, blur=0.6), dict()),
    ("p06-shadow", lambda: pf.listing(), dict(angle=-2.0, shadow=0.7, noise=6), dict()),
    ("p07-blur", lambda: slip_side(face="NanumMyeongjo-Regular.ttf"), dict(angle=1.0, blur=1.8, jpeg=60), dict()),
    ("p08-glare", lambda: pf.listing(face="GothicA1-Regular.ttf", size=26), dict(angle=1.5), dict(glare=0.8)),
]

# Made-up fills for capture photos (the 1.25 W 12 examples are text; these are drawn as a phone screen would show them).
FILLS = [
    [("삼성전자", "매수", 15, 71500), ("카카오", "매도", 7, 48250)],
    [("현대차", "매수", 3, 251000)],
    [("SK하이닉스", "매도", 12, 182300), ("NAVER", "매수", 4, 205500), ("기아", "매수", 20, 98700)],
]


def capture(fills, dark=False, size=30):
    """An app's 체결 내역 screen: a card per fill (name, side, 체결수량, 체결단가, 체결금액), light or dark mode."""
    w = 1080
    rows = sum(5 for _ in fills)
    h = 260 + rows * int(size * 1.9) + 80
    bg, fg, dim = ((18, 18, 20), (235, 235, 235), (150, 150, 150)) if dark else ((250, 250, 252), (25, 25, 25), (110, 110, 110))
    s = rf.Sheet(w, h)
    s.img.paste(bg, (0, 0, w, h))
    s.text((60, 120), "체결내역", int(size * 1.2), color=fg, anchor="lm")
    y = 220
    for name, side, q, p in fills:
        s.text((60, y), name, size, color=fg, anchor="lm"); y += int(size * 1.9)
        s.text((60, y), f"{side} 체결", int(size * 0.9), color=(220, 60, 60) if side == "매수" else (60, 110, 230), anchor="lm"); y += int(size * 1.9)
        for label, value in (("체결수량", f"{q}주"), ("체결단가", f"{p:,}원"), ("체결금액", f"{q * p:,}원")):
            s.text((60, y), label, int(size * 0.9), color=dim, anchor="lm")
            s.text((w - 60, y), value, size, color=fg, anchor="rm")
            y += int(size * 1.9)
    return s


FILL = [
    ("f01-light", lambda: capture(FILLS[0]), dict(), dict()),
    ("f02-dark", lambda: capture(FILLS[1], dark=True), dict(), dict()),
    ("f03-small-dark", lambda: capture(FILLS[2], dark=True, size=20), dict(scale=0.8), dict()),
    ("f04-photo-of-screen", lambda: capture(FILLS[0], size=26), dict(angle=2.0, blur=0.8), dict(moire=0.7, glare=0.5)),
    ("f05-small-light", lambda: capture(FILLS[2], size=18), dict(scale=0.7, jpeg=60), dict()),
]

# ---------------------------------------------------------------- reading


def words_back(engine, pic, factor):
    out = []
    for w in rf.ocr_words(engine, bgr(pic)):
        l, t, r, b, angle, text = w
        out.append((int(l * factor), int(t * factor), int(r * factor), int(b * factor), angle, text))
    return out


def lines_back(engine, pic, factor):
    out = []
    for w in pf.lines_of(engine, bgr(pic)):
        l, t, r, b, angle, text = w
        out.append((int(l * factor), int(t * factor), int(r * factor), int(b * factor), angle, text))
    return out


def run_roster(engine, args):
    out = os.path.join(OUT, "roster")
    os.makedirs(out, exist_ok=True)
    for name, layout, photo, after in ROSTER:
        if args.only and args.only not in name:
            continue
        rng = random.Random(name)
        sheet, truth = layout(rng)
        a, where = rf.photograph(sheet, rng, **photo)
        a = harder(a, rng, **after)
        for vname, (pic, factor) in variants(a).items():
            if args.images:
                os.makedirs(args.images, exist_ok=True)
                cv2.imwrite(os.path.join(args.images, f"{name}.{vname}.jpg"), bgr(pic))
            read = rf.matched(words_back(engine, pic, factor), sheet, where)
            rf.write(os.path.join(out, f"{name}.{vname}.tsv"), f"{name} {vname} {photo} {after}", truth, read)
        print(name, file=sys.stderr)


def write_lines(path, title, truth, read, rules=None):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# {title} (RapidOCR lines; last column: the text drawn there)\n")
        for k, v in truth.items():
            f.write(f"truth\t{k}\t{v}\n")
        for w in read:
            f.write("line\t" + "\t".join(str(v) for v in w) + "\n")


def run_lines(engine, args, kind, cases, truth_of):
    out = os.path.join(OUT, kind)
    os.makedirs(out, exist_ok=True)
    for name, layout, photo, after in cases:
        if args.only and args.only not in name:
            continue
        rng = random.Random(name)
        sheet = layout()
        a, where = rf.photograph(sheet, rng, **photo)
        a = harder(a, rng, **after)
        for vname, (pic, factor) in variants(a).items():
            if args.images:
                os.makedirs(args.images, exist_ok=True)
                cv2.imwrite(os.path.join(args.images, f"{name}.{vname}.jpg"), bgr(pic))
            read = pf.matched(lines_back(engine, pic, factor), sheet, where)
            write_lines(os.path.join(out, f"{name}.{vname}.tsv"), f"{name} {vname} {photo} {after}", truth_of(name), read)
        print(name, file=sys.stderr)


def fill_truth(name):
    i = int(name[1:3]) - 1
    fills = [FILLS[0], FILLS[1], FILLS[2], FILLS[0], FILLS[2]][i]
    return {f"{n}|{s}|{q}": p for n, s, q, p in fills}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images")
    ap.add_argument("--only")
    ap.add_argument("--kind")
    args = ap.parse_args()
    engine = rf.Ocr()
    if args.kind in (None, "roster"):
        run_roster(engine, args)
    if args.kind in (None, "payslip"):
        run_lines(engine, args, "payslip", PAYSLIP, lambda n: pf.TRUTH)
    if args.kind in (None, "fill"):
        run_lines(engine, args, "fill", FILL, fill_truth)


if __name__ == "__main__":
    main()
