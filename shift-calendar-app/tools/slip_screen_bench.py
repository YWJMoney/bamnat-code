#!/usr/bin/env python3
"""[READ-38] Payslips and 계산 방법 pages on a company HR screen, photographed off the monitor (1.38, 사용자 2026-11-06
"이런식으로 사람들은 사진을 찍을수도 있어 … 어떠한 유형에서도 인식률이 100%에 근접해야해").

The user's own photo showed what such a photo is: the HR portal's payslip in a browser, the monitor's moire over all of
it, a diagonal watermark (company, 사번, date, time, IP) repeated across the page, the photo taken at an angle. On the page:
three boxes at the top (총지급액 − 공제총액 = 차감지급액, the label top left and the amount bold at the right, lower), then
지급내역 | 금액 | 공제내역 | 금액 with the names centred and the amounts right-aligned in their cells, clubs and dues in
brackets (볼링회(1공장), 축구회 (ABS/EP)), a blank row, 지급계 · 공제계 on a tinted row, and a 근태 table of hours heads with
the values under them (some blank). The second page is the company's 계산 방법 table (구분 | 산출방법, "해당시간 × 1.5 ×
(통상임금 / 시수)").

Everything here is drawn: made-up names of items, made-up amounts that add up, no real person, company or number from
the user's photo (CLAUDE.md: no personal data in the repository). Each case is photographed (tilt, perspective, blur),
the screen's moire and glare put over it, read as the phone reads it (each made-over picture, words with their boxes) by
RapidOCR, and written to core/src/test/resources/slip-screen/<case>.<variant>.tsv with the truth. SlipScreenBenchTest
reads them as the app does (SlipReading over all the readings; RuleText for the rules page).

Setup as tools/roster_fixtures.py. Run: python3 tools/slip_screen_bench.py [--only NAME] [--images DIR]
"""
import argparse
import os
import random
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import roster_fixtures as rf  # noqa: E402
import photo_bench as pb  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "test", "resources", "slip-screen")

EARN_NAMES = ["기본급I", "기본급II", "자격수당", "안전교육/LMS", "상여금", "휴일근로수당", "휴일연장수당", "야간근무수당", "향군수당", "", "소급분(지급)"]
DED_NAMES = ["소득세", "주민세", "건강보험", "고용보험", "국민연금", "볼링회 (1공장)", "축구회 (ABS/EP)", "산악회 (1공장)", "노동조합",
             "1공장_사택관리비", "소급분(공제)"]
EARN_ALT = ["기본급", "직책수당", "자격수당", "가족수당", "상여금", "연장근로수당", "야간근로수당", "휴일근로수당", "식대", "교통비", ""]
DED_ALT = ["소득세", "지방소득세", "국민연금", "건강보험", "장기요양보험", "고용보험", "친목회", "상조회 (본사)", "노동조합", "", ""]


def won(n):
    return f"{n:,}"


def amounts(rng, names, big):
    out = []
    for i, n in enumerate(names):
        if not n:
            out.append(None)
            continue
        if i < big:
            out.append(rng.randrange(800_000, 2_900_000))
        else:
            out.append(rng.choice([rng.randrange(3, 60) * 1000, rng.randrange(10_000, 700_000)]))
    return out


def watermark(img, rng, text, color=(150, 150, 150), alpha=70, size=34):
    """The portal's diagonal watermark, repeated over the page, light and see-through."""
    w, h = img.size
    layer = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = rf.font("NanumGothic-Regular.ttf", size)
    step_x, step_y = int(size * 22), int(size * 5)
    for y in range(0, h * 2, step_y):
        off = (y // step_y) % 2 * step_x // 2
        for x in range(-step_x, w * 2, step_x):
            d.text((x + off, y), text, font=f, fill=color + (alpha,))
    layer = layer.rotate(rng.uniform(20, 30), resample=Image.BICUBIC, center=(w, h))
    layer = layer.crop((w // 2, h // 2, w // 2 + w, h // 2 + h))
    base = img.convert("RGBA")
    base.alpha_composite(layer)
    return base.convert("RGB")


def payslip(rng, earn_names, ded_names, *, size=26, face="NanumGothic-Regular.ttf", hours=True):
    earn = amounts(rng, earn_names, 2)
    ded = amounts(rng, ded_names, 1)
    gross = sum(a for a in earn if a)
    total_ded = sum(a for a in ded if a)
    net = gross - total_ded
    W, row = 2000, int(size * 2.0)
    rows = max(len(earn_names), len(ded_names))
    H = 330 + row * (rows + 2) + 330
    s = rf.Sheet(W, H, face)
    s.img.paste((246, 247, 249), (0, 0, W, H))
    # Browser bar and the tab.
    s.fill((0, 0, W, 70), (222, 225, 230))
    s.text((40, 35), "hr.example.com/service/payslip#noback", int(size * 0.8), anchor="lm", color=(60, 60, 60))
    s.text((40, 110), "급여명세서 ×", int(size * 0.85), anchor="lm", color=(40, 40, 40))
    s.text((W - 260, 110), "00:58:39", int(size * 0.8), anchor="lm", color=(90, 90, 90))
    # The three boxes.
    y = 150
    boxes = [("총지급액", gross), ("공제총액", total_ded), ("차감지급액", net)]
    bw = (W - 160) // 3
    for i, (label, value) in enumerate(boxes):
        x0 = 60 + i * (bw + 20)
        s.fill((x0, y, x0 + bw, y + 110), (255, 255, 255))
        s.d.line([(x0, y), (x0, y + 110)], fill=(120, 130, 150), width=4)
        s.text((x0 + 30, y + 26), label, int(size * 0.85), anchor="lm", color=(40, 40, 40))
        s.text((x0 + bw - 30, y + 74), won(value), int(size * 1.15), anchor="rm", color=(30, 30, 30))
        if i < 2:
            s.text((x0 + bw + 10, y + 55), "−" if i == 0 else "=", int(size * 1.2), color=(150, 150, 150))
    # The table: 지급내역 | 금액 | 공제내역 | 금액.
    top = y + 150
    xs = [80, 560, 1010, 1470, 1920]
    s.fill((xs[0], top, xs[-1], top + row), (226, 228, 232))
    for (a, b), head in zip(zip(xs, xs[1:]), ["지급내역", "금액", "공제내역", "금액"]):
        s.text(((a + b) // 2, top + row // 2), head, int(size * 0.9), color=(40, 40, 40))
    for i in range(rows):
        cy = top + (i + 1) * row + row // 2
        if i < len(earn_names) and earn_names[i]:
            s.text(((xs[0] + xs[1]) // 2, cy), earn_names[i], int(size * 0.9))
            s.text((xs[2] - 40, cy + 6), won(earn[i]), int(size * 0.85), anchor="rm")
        if i < len(ded_names) and ded_names[i]:
            s.text(((xs[2] + xs[3]) // 2, cy), ded_names[i], int(size * 0.9))
            s.text((xs[4] - 40, cy + 6), won(ded[i]), int(size * 0.85), anchor="rm")
    foot = top + (rows + 1) * row
    s.fill((xs[0], foot, xs[-1], foot + row), (250, 244, 222))
    s.text(((xs[0] + xs[1]) // 2, foot + row // 2), "지급계", int(size * 0.9))
    s.text((xs[2] - 40, foot + row // 2 + 6), won(gross), int(size * 0.85), anchor="rm")
    s.text(((xs[2] + xs[3]) // 2, foot + row // 2), "공제계", int(size * 0.9))
    s.text((xs[4] - 40, foot + row // 2 + 6), won(total_ded), int(size * 0.85), anchor="rm")
    s.grid(xs, [top + k * row for k in range(rows + 3)], color=(205, 208, 214), width=1)
    truth = [("item", n, a) for n, a in zip(earn_names, earn) if n] + [("item", n, a) for n, a in zip(ded_names, ded) if n]
    truth += [("total", "지급합계", gross), ("total", "공제합계", total_ded), ("total", "실수령액", net)]
    # The 근태 table: hours heads, the values under them, some blank.
    if hours:
        ay = foot + row + 60
        s.text((90, ay), "근태 정보(현장직)", int(size * 0.95), anchor="lm")
        heads = ["근로일수", "휴업일수", "휴일근로시간수", "평일연장근로시간수", "휴일연장근로시간수", "야간근로시간수"]
        vals = ["31.00", "", f"{rng.randrange(8, 90)}.00", "", f"{rng.randrange(8, 40)}.00", f"{rng.randrange(8, 80)}.00"]
        cw = (xs[-1] - xs[0]) // len(heads)
        s.fill((xs[0], ay + 30, xs[-1], ay + 30 + row), (226, 228, 232))
        for k, (hd, v) in enumerate(zip(heads, vals)):
            cx = xs[0] + k * cw + cw // 2
            s.text((cx, ay + 30 + row // 2), hd, int(size * 0.8), color=(40, 40, 40))
            if v:
                s.text((cx, ay + 30 + row + row // 2), v, int(size * 0.8))
    return s, truth


RULE_ROWS = [
    ("휴일근로수당", "해당시간 × 1.5 × ( 통상임금 / 시수 )"),
    ("평일연장수당", "해당시간 × 1.5 × ( 통상임금 / 시수 )"),
    ("휴일연장수당", "해당시간 × 2 × ( 통상임금 / 시수 )"),
    ("야간근로수당", "해당시간 × 0.5 × ( 통상임금 / 시수 )"),
    ("보상휴가금액(사무직限)", "해당시간 × 1.5 × ( 기준급 / 시수 ) ※사무직 평일연장/야간/휴일근로에 대해 보상휴가로 부여함"),
    ("휴가보상", "해당일수 × 1.5 × ( 기준급 / 시수 × 8 )"),
    ("이전 미공제액", "직전 지급건 중에 미공제 된 금액으로 이번 지급건에서 공제된 금액"),
    ("기준급", "기본급, 직책수당, 자격수당, 기술교육수당"),
    ("통상임금", "기본급, 직책수당, 자격수당, 기술교육수당 등 소정근로대가성, 정기성, 일률성, 고정성이 인정되는 임금 일체"),
]
# What the rules page must give: the premium each row sets (배율 − 1, 야간 as it is).
RULE_TRUTH = [("HOLIDAY", "0.5", "false"), ("OVERTIME", "0.5", "false"), ("HOLIDAY", "1.0", "true"), ("NIGHT", "0.5", "false")]


def rules(size=26, face="NanumGothic-Regular.ttf"):
    W, row = 2000, int(size * 2.1)
    H = 260 + row * (len(RULE_ROWS) + 1) + 120
    s = rf.Sheet(W, H, face)
    s.img.paste((246, 247, 249), (0, 0, W, H))
    s.text((40, 60), "※ 당월 급여산정을 위한 근태는 HR Service ▶ 근태 메뉴에서 확인하시기 바랍니다.", int(size * 0.8), anchor="lm")
    s.text((40, 150), "계산 방법", int(size * 1.0), anchor="lm")
    top, xs = 200, [40, 1000, 1960]
    s.fill((xs[0], top, xs[-1], top + row), (226, 228, 232))
    s.text(((xs[0] + xs[1]) // 2, top + row // 2), "구분", int(size * 0.9))
    s.text(((xs[1] + xs[2]) // 2, top + row // 2), "산출방법", int(size * 0.9))
    for i, (name, how) in enumerate(RULE_ROWS):
        cy = top + (i + 1) * row + row // 2
        s.text(((xs[0] + xs[1]) // 2, cy), name, int(size * 0.9))
        s.text(((xs[1] + xs[2]) // 2, cy), how, int(size * 0.85) if len(how) < 50 else int(size * 0.72))
    s.grid([xs[0], xs[-1]], [top + k * row for k in range(len(RULE_ROWS) + 2)], color=(205, 208, 214), width=1)
    return s


MARK = "회사 ghtiq 2026-10-25 16:25 10.0.0.54"
CASES = [
    # name, layout, photo, after (moire/glare/dark), watermark. The moire as strong as the user's photo had it (the letters
    # still whole); 0.9 broke every letter, which no phone photo the user sends does.
    ("s01-monitor", lambda r: payslip(r, EARN_NAMES, DED_NAMES, size=32), dict(angle=1.5, persp=0.02, blur=0.7, jpeg=75), dict(moire=0.4, glare=0.3), True),
    ("s02-monitor-dim", lambda r: payslip(r, EARN_NAMES, DED_NAMES, size=32, face="GothicA1-Regular.ttf"), dict(angle=-2.0, persp=0.03, blur=0.8, jpeg=70), dict(moire=0.35, dark=0.25), True),
    ("s03-side-angle", lambda r: payslip(r, EARN_ALT, DED_ALT, size=32), dict(angle=3.0, persp=0.05, blur=0.7), dict(moire=0.3, glare=0.4), True),
    ("s04-capture", lambda r: payslip(r, EARN_ALT, DED_ALT, size=24), dict(), dict(), False),
    ("s05-monitor-small", lambda r: payslip(r, EARN_NAMES, DED_NAMES, size=26), dict(angle=0.8, blur=0.7, jpeg=65), dict(moire=0.4), True),
    ("r01-rules-monitor", lambda r: (rules(size=32), None), dict(angle=1.2, persp=0.02, blur=0.7, jpeg=75), dict(moire=0.4, glare=0.3), True),
    ("r02-rules-dim", lambda r: (rules(size=32, face="GothicA1-Regular.ttf"), None), dict(angle=-1.8, persp=0.03, blur=0.8), dict(moire=0.35, dark=0.25), True),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    ap.add_argument("--images")
    args = ap.parse_args()
    engine = rf.Ocr()
    os.makedirs(OUT, exist_ok=True)
    for name, layout, photo, after, mark in CASES:
        if args.only and args.only not in name:
            continue
        rng = random.Random(name)
        sheet, truth = layout(rng)
        if mark:
            sheet.img = watermark(sheet.img, rng, MARK)
            sheet.d = ImageDraw.Draw(sheet.img)
        a, _ = rf.photograph(sheet, rng, **photo)
        a = pb.harder(a, rng, **after)
        if args.images:
            os.makedirs(args.images, exist_ok=True)
            cv2.imwrite(os.path.join(args.images, f"{name}.jpg"), a)
        with open(os.path.join(OUT, f"{name}.truth.tsv"), "w", encoding="utf-8", newline="\n") as f:
            if truth is None:
                for t in RULE_TRUTH:
                    f.write("rule\t" + "\t".join(t) + "\n")
            else:
                for kind, n, v in truth:
                    f.write(f"{kind}\t{n}\t{v}\n")
        for vname, (pic, factor) in pb.variants(a).items():
            if vname == "up2" and pic.shape[0] * pic.shape[1] > 9_000_000:
                continue
            words = pb.words_back(engine, pic, factor)
            with open(os.path.join(OUT, f"{name}.{vname}.tsv"), "w", encoding="utf-8", newline="\n") as f:
                f.write(f"# {name} {vname} {photo} {after}\n")
                for l, t, r, b, angle, text in words:
                    f.write(f"word\t{l}\t{t}\t{r}\t{b}\t{angle:.2f}\t{text}\n")
        print(name, file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
