#!/usr/bin/env python3
"""[READ-07] Payslip and pay-rule photo fixtures (1.25, 사용자 2026-10-25 "급여 명세서를 보내도 제대로 인식하지 못하고").

Payslips drawn in the usual layouts (지급·공제 side by side, one list, a 시간 column), photographed the way a phone does
(tilted 0~4°, skewed, soft, shaded) and read by a real OCR on this machine, line by line as ML Kit gives a payslip, into
core/src/test/resources/payslip-photos/<name>.tsv. PayslipPhotoFixturesTest puts the lines back on their rows (OcrRows)
and reads them (PayslipReader) as the app does, and checks the amounts against what was drawn. Made-up amounts, no
real person or company. Setup as tools/roster_fixtures.py.

Run: python3 tools/payslip_fixtures.py [--images DIR]
"""
import argparse
import math
import os
import random
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from roster_fixtures import Ocr, Sheet, photograph  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "test", "resources", "payslip-photos")

EARN = [("기본급", 2508000), ("연장근로수당", 288000), ("야간근로수당", 154000), ("휴일근로수당", 96000), ("식대", 200000)]
DEDUCT = [("국민연금", 135000), ("건강보험", 106000), ("장기요양보험", 13700), ("고용보험", 27000), ("소득세", 98000), ("지방소득세", 9800)]
GROSS = sum(a for _, a in EARN)
DEDUCTED = sum(a for _, a in DEDUCT)
NET = GROSS - DEDUCTED
# What the reader must find: name on the slip → amount.
TRUTH = {"기본급": 2508000, "연장근로수당": 288000, "야간근로수당": 154000, "휴일근로수당": 96000, "지급합계": GROSS, "실수령액": NET}


def won(n):
    return f"{n:,}"


def side_by_side(face="NanumGothic-Regular.ttf", size=28, hours=False):
    """지급 항목 | 금액 | 공제 항목 | 금액, a row each, totals under: the most common payslip."""
    row_h = int(size * 1.9)
    w = 1500
    rows = max(len(EARN), len(DEDUCT))
    s = Sheet(w, 360 + row_h * (rows + 4), face)
    s.text((w // 2, 80), "2026년 9월 급여명세서", int(size * 1.4))
    s.text((90, 170), "성명  김민지", size, anchor="lm")
    s.text((700, 170), "소속  간호부", size, anchor="lm")
    y0 = 240
    cols = [60, 330, 740, 1010, 1440]
    s.text((cols[0] + 20, y0 + row_h // 2), "지급 항목", size, anchor="lm")
    s.text((cols[2] - 20, y0 + row_h // 2), "금액", size, anchor="rm")
    s.text((cols[2] + 20, y0 + row_h // 2), "공제 항목", size, anchor="lm")
    s.text((cols[4] - 20, y0 + row_h // 2), "금액", size, anchor="rm")
    for i in range(rows):
        cy = y0 + (i + 1) * row_h + row_h // 2
        if i < len(EARN):
            name, amount = EARN[i]
            s.text((cols[0] + 20, cy), name, size, anchor="lm")
            if hours and i in (1, 2, 3):
                s.text((cols[1] + 20, cy), ["", "12시간", "16시간", "8시간"][i], int(size * 0.85), anchor="lm", color=(80, 80, 80))
            s.text((cols[2] - 20, cy), won(amount), size, anchor="rm")
        if i < len(DEDUCT):
            name, amount = DEDUCT[i]
            s.text((cols[2] + 20, cy), name, size, anchor="lm")
            s.text((cols[4] - 20, cy), won(amount), size, anchor="rm")
    cy = y0 + (rows + 1) * row_h + row_h // 2
    s.text((cols[0] + 20, cy), "지급합계", size, anchor="lm")
    s.text((cols[2] - 20, cy), won(GROSS), size, anchor="rm")
    s.text((cols[2] + 20, cy), "공제합계", size, anchor="lm")
    s.text((cols[4] - 20, cy), won(DEDUCTED), size, anchor="rm")
    cy += row_h
    s.text((cols[2] + 20, cy), "실수령액", size, anchor="lm")
    s.text((cols[4] - 20, cy), won(NET), size, anchor="rm")
    ys = [y0 + k * row_h for k in range(rows + 4)]
    s.grid([cols[0], cols[2], cols[4]], ys)
    return s


def listing(face="NanumGothic-Regular.ttf", size=30):
    """One column, name left and amount right (a messenger or app capture)."""
    items = EARN + [("지급합계", GROSS)] + DEDUCT + [("공제합계", DEDUCTED), ("실수령액", NET)]
    row_h = int(size * 1.7)
    w = 900
    s = Sheet(w, 220 + row_h * len(items) + 60, face)
    s.text((w // 2, 80), "9월 급여 명세", int(size * 1.3))
    for i, (name, amount) in enumerate(items):
        cy = 200 + i * row_h
        s.text((60, cy), name, size, anchor="lm")
        s.text((w - 60, cy), won(amount) + "원", size, anchor="rm")
    return s


RULES = [
    "제12조(가산임금)",
    "연장근로는 통상시급의 1.5배를 지급한다.",
    "야간근로(22시~06시)는 50%를 가산하여 지급한다.",
    "휴일근로 8시간 이내는 50%, 8시간 초과분은 100%를 가산한다.",
    "토요일 특근은 1회 50,000원을 지급한다.",
    "식대는 월 200,000원을 지급한다.",
    "상여금은 기본급의 연 400%를 짝수달에 나누어 지급한다.",
]


def rules_page(face="NanumGothic-Regular.ttf", size=30):
    """A page of the company's pay rules (취업규칙의 임금 조항): the "회사 계산 방법" photo sent with a payslip."""
    row_h = int(size * 2.0)
    s = Sheet(1400, 200 + row_h * len(RULES) + 80, face)
    s.text((700, 80), "임금 지급 규정", int(size * 1.3))
    for i, line in enumerate(RULES):
        s.text((80, 200 + i * row_h), line, size, anchor="lm")
    return s


CASES = [
    ("01-side-level", lambda: side_by_side(), dict()),
    ("02-side-tilt1", lambda: side_by_side(), dict(angle=1.2, blur=0.6)),
    ("03-side-tilt2", lambda: side_by_side(hours=True), dict(angle=-2.2, persp=0.01, blur=0.7, jpeg=70)),
    ("04-side-tilt3", lambda: side_by_side(face="NanumMyeongjo-Regular.ttf"), dict(angle=3.0, persp=0.012, shadow=0.35, noise=5)),
    ("05-side-tilt4", lambda: side_by_side(size=24), dict(angle=-4.0, blur=0.8, scale=0.8)),
    ("06-list-tilt2", lambda: listing(), dict(angle=2.0, blur=0.5)),
    ("07-list-gothic", lambda: listing(face="GothicA1-Regular.ttf", size=26), dict(angle=-1.5, shadow=0.3)),
    ("rules-01-tilt2", lambda: rules_page(), dict(angle=2.0, blur=0.6)),
    ("rules-02-tilt3", lambda: rules_page(face="NanumMyeongjo-Regular.ttf", size=28), dict(angle=-3.0, persp=0.01, shadow=0.3, noise=4)),
]


def lines_of(engine, a):
    """Lines with boxes and slopes, as ML Kit's Text.Line."""
    out = []
    for quad, text, score in engine.lines(a):
        q = np.array(quad, dtype=np.float32)
        top_l, top_r = q[0], q[1]
        angle = math.degrees(math.atan2(top_r[1] - top_l[1], top_r[0] - top_l[0]))
        xs, ys = q[:, 0], q[:, 1]
        out.append((int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()), round(angle, 2), text))
    return out


def matched(read, sheet, where):
    """Each line read with the text drawn there (words of one row and column joined), so a misread letter is told apart
    from a row put back wrong."""
    drawn = []
    for text, l, t, r, b, _ in sheet.drawn:
        drawn.append((where(l, t), where(r, b), text))
    out = []
    for w in read:
        inside = [d for d in drawn if w[0] - 8 <= (d[0][0] + d[1][0]) / 2 <= w[2] + 8 and w[1] - 8 <= (d[0][1] + d[1][1]) / 2 <= w[3] + 8]
        inside.sort(key=lambda d: d[0][0])
        out.append(w + (" ".join(d[2] for d in inside),))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images")
    args = ap.parse_args()
    engine = Ocr()
    os.makedirs(OUT, exist_ok=True)
    for name, layout, photo in CASES:
        rng = random.Random(name)
        sheet = layout()
        a, where = photograph(sheet, rng, **photo)
        if args.images:
            os.makedirs(args.images, exist_ok=True)
            cv2.imwrite(os.path.join(args.images, name + ".jpg"), a)
        read = matched(lines_of(engine, a), sheet, where)
        with open(os.path.join(OUT, name + ".tsv"), "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# {name} {photo} (RapidOCR lines; last column: the text drawn there)\n")
            if name.startswith("rules"):
                for line in RULES:
                    f.write(f"rule\t{line}\n")
            else:
                for k, v in TRUTH.items():
                    f.write(f"truth\t{k}\t{v}\n")
            for w in read:
                f.write("line\t" + "\t".join(str(v) for v in w) + "\n")
        print(name, len(read), "lines", file=sys.stderr)


if __name__ == "__main__":
    main()
