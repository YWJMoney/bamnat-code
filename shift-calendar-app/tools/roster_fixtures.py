#!/usr/bin/env python3
"""[READ-01] Duty roster photo fixtures (1.25 순번 367).

1.24 tested the roster reader only with boxes typed by hand, every one level, and real photos failed (사용자 2026-10-25:
"표 자체를 인식하지 못하고 있다"). This draws rosters of many layouts the way a phone photographs them (tilted 0~5°,
a little perspective, blur, a shadow, JPEG) and writes two fixtures of each under core/src/test/resources/roster-photos/:

  <name>.ocr.tsv   what a real OCR on this machine read from the photo, word by word with its box (and the word
                   drawn there, so a misread letter can be told from a word put on the wrong day);
  <name>.boxes.tsv every word drawn, its box carried through the same tilt and skew, with an OCR's usual slips
                   (O read as 0, full-width letters, a cell dropped, two day numbers run together "1213").

RosterPhotoFixturesTest feeds both to DutyRoster.read as the app feeds ML Kit's words, and checks every day against the
truth drawn. The OCR here is RapidOCR (PP-OCRv4 text detection and recognition, and PaddleOCR's Korean recognition for
한글 lines, ONNX), not ML Kit, which runs only on a phone. It reads lone letters (a cell's "N") worse than ML Kit, so its
fixtures are held to "every cell read lands on its day" and the boxes fixtures to "29 of 31 days". The rosters are made
up: no real names or companies.

Setup (once, outside the repository):
  pip install rapidocr_onnxruntime==1.2.3
  models in /tmp/ocrmodels from https://huggingface.co/SWHL/RapidOCR/resolve/main/:
    PP-OCRv4/ch_PP-OCRv4_det_infer.onnx → ch_v4_det.onnx, PP-OCRv4/ch_PP-OCRv4_rec_infer.onnx → ch_v4_rec.onnx,
    PP-OCRv1/korean_mobile_v2.0_rec_infer.onnx → korean_rec.onnx
  fonts (OFL) in /tmp/fonts from https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/<family>/<file>.ttf:
    NanumGothic-Regular, NanumMyeongjo-Regular, NanumPenScript-Regular, GothicA1-Regular
Run:
  python3 tools/roster_fixtures.py [--images DIR] [--only NAME]
"""
import argparse
import calendar
import math
import os
import random
import re
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTS = os.environ.get("ROSTER_FONTS", "/tmp/fonts")
MODELS = os.environ.get("ROSTER_MODELS", "/tmp/ocrmodels")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "test", "resources", "roster-photos")
WEEK = "월화수목금토일"
PAPER = (252, 252, 250)
DESK = (120, 110, 100)

NAMES = ["김민지", "이수진", "박하늘", "최유리", "정다은", "한지우", "오세영", "윤가람", "장미소", "임도연"]
LATIN = ["D", "D", "E", "E", "N", "N", "O", "O"]
TEAM = ["주", "주", "야", "야", "비", "휴"]
KOREAN = ["데이", "이브", "나이트", "휴무"]


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


class Sheet:
    """A roster drawn on white paper, remembering the box of every text it drew."""

    def __init__(self, w, h, face="NanumGothic-Regular.ttf"):
        self.img = Image.new("RGB", (w, h), PAPER)
        self.d = ImageDraw.Draw(self.img)
        self.face = face
        self.drawn = []

    def text(self, xy, s, size, color=(20, 20, 20), anchor="mm", face=None, cell=None):
        """[cell] is the (person, day) a code stands for, so a cell the photo loses is known."""
        f = font(face or self.face, size)
        self.d.text(xy, s, font=f, fill=color, anchor=anchor)
        # One box per word, as ML Kit's elements.
        x = self.d.textbbox(xy, s, font=f, anchor=anchor)[0]
        for i, word in enumerate(s.split(" ")):
            if word:
                l, t, r, b = self.d.textbbox((x, xy[1]), word, font=f, anchor="l" + anchor[1])
                self.drawn.append((word, l, t, r, b, cell))
                x = r
            x += self.d.textlength(" ", font=f)

    def fill(self, box, color):
        self.d.rectangle(box, fill=color)

    def grid(self, xs, ys, color=(90, 90, 90), width=2):
        for x in xs:
            self.d.line([(x, ys[0]), (x, ys[-1])], fill=color, width=width)
        for y in ys:
            self.d.line([(xs[0], y), (xs[-1], y)], fill=color, width=width)


def rotation(year, month, people, pattern, rng, leave=None):
    """Each person's codes for the month: the pattern shifted per person, with a few days of leave."""
    days = calendar.monthrange(year, month)[1]
    out = {}
    for i, p in enumerate(people):
        row = {d: pattern[(d + i * 2) % len(pattern)] for d in range(1, days + 1)}
        for word in (leave or []):
            row[rng.randint(1, days)] = word
        out[p] = row
    return out


def horizontal(rng, year, month, people, pattern, *, cell=64, size=26, lines=True, weekday=True, title=True,
               face="NanumGothic-Regular.ttf", leave=None, shade=True, halves=False, label_extra=None, day_fmt="{d}"):
    """People down, days across (간호 듀티표, 교대 조 표, 엑셀 캡처)."""
    truth = rotation(year, month, people, pattern, rng, leave)
    days = calendar.monthrange(year, month)[1]
    blocks = [range(1, 16), range(16, days + 1)] if halves else [range(1, days + 1)]
    name_w = 170 if not label_extra else 250
    row_h = int(size * 2.0)
    width = 60 + name_w + cell * max(len(b) for b in blocks) + 60
    block_h = row_h * (len(people) + (2 if weekday else 1)) + 40
    height = 60 + (90 if title else 0) + block_h * len(blocks) + 60
    s = Sheet(width, height, face)
    y0 = 60
    if title:
        s.text((width // 2, y0 + 30), f"{year}년 {month}월 근무표", int(size * 1.4))
        y0 += 90
    first = calendar.weekday(year, month, 1)
    for block in blocks:
        x0 = 60 + name_w
        ys = [y0 + k * row_h for k in range(len(people) + (2 if weekday else 1) + 1)]
        xs = [60] + [x0 + k * cell for k in range(len(block) + 1)]
        if shade:
            for k, d in enumerate(block):
                if (first + d - 1) % 7 >= 5:
                    s.fill((x0 + k * cell, ys[0], x0 + (k + 1) * cell, ys[-1]), (255, 232, 232))
        for k, d in enumerate(block):
            cx = x0 + k * cell + cell // 2
            s.text((cx, ys[0] + row_h // 2), day_fmt.format(d=d, m=month), size)
            if weekday:
                wd = WEEK[(first + d - 1) % 7]
                s.text((cx, ys[1] + row_h // 2), wd, int(size * 0.8), color=(200, 30, 30) if wd in "토일" else (60, 60, 60))
        top = 2 if weekday else 1
        for i, p in enumerate(people):
            cy = ys[top + i] + row_h // 2
            s.text((60 + 14, cy), p, size, anchor="lm")
            if label_extra:
                s.text((60 + 150, cy), label_extra, int(size * 0.8), anchor="lm", color=(80, 80, 80))
            for k, d in enumerate(block):
                code = truth[p][d]
                color = (20, 20, 160) if code in ("N", "야", "나이트") else (20, 20, 20)
                s.text((x0 + k * cell + cell // 2, cy), code, size if len(code) < 3 else int(size * 0.7), color=color, cell=(p, d))
        if lines:
            s.grid(xs, ys)
        y0 += block_h
    return s, truth


def transposed(rng, year, month, people, pattern, *, size=26, face="NanumGothic-Regular.ttf"):
    """Days down, people across (날짜가 세로인 표)."""
    truth = rotation(year, month, people, pattern, rng)
    days = calendar.monthrange(year, month)[1]
    row_h = int(size * 1.7)
    col_w = 150
    width = 60 + 160 + col_w * len(people) + 60
    height = 60 + 90 + row_h * (days + 1) + 60
    s = Sheet(width, height, face)
    s.text((width // 2, 90), f"{month}월 근무표", int(size * 1.3))
    y0 = 150
    first = calendar.weekday(year, month, 1)
    for i, p in enumerate(people):
        s.text((60 + 160 + i * col_w + col_w // 2, y0 + row_h // 2), p, size)
    for d in range(1, days + 1):
        cy = y0 + d * row_h + row_h // 2
        s.text((60 + 16, cy), f"{d}({WEEK[(first + d - 1) % 7]})", int(size * 0.9), anchor="lm")
        for i, p in enumerate(people):
            s.text((60 + 160 + i * col_w + col_w // 2, cy), truth[p][d], size, cell=(p, d))
    xs = [60, 60 + 160] + [60 + 160 + (i + 1) * col_w for i in range(len(people))]
    ys = [y0 + k * row_h for k in range(days + 2)]
    s.grid(xs, ys, width=1)
    return s, truth


def listing(rng, year, month, pattern, *, size=30, face="NanumGothic-Regular.ttf"):
    """One person's month as a list, a day a line (메신저로 받은 내 근무)."""
    days = calendar.monthrange(year, month)[1]
    row = {d: pattern[d % len(pattern)] for d in range(1, days + 1)}
    first = calendar.weekday(year, month, 1)
    row_h = int(size * 1.6)
    s = Sheet(700, 160 + row_h * days + 60, face)
    s.text((40, 80), f"{month}월 내 근무", int(size * 1.2), anchor="lm")
    for d in range(1, days + 1):
        cy = 160 + (d - 1) * row_h
        s.text((40, cy), f"{month}/{d}", size, anchor="lm")
        s.text((220, cy), f"({WEEK[(first + d - 1) % 7]})", size, anchor="lm", color=(90, 90, 90))
        s.text((360, cy), row[d], size, anchor="lm", cell=("", d))
    return s, {"": row}


def photograph(sheet, rng, *, angle=0.0, persp=0.0, blur=0.0, noise=0.0, shadow=0.0, scale=1.0, jpeg=80):
    """The paper as a phone sees it: on a desk, tilted, a little skewed, soft, shaded, compressed. The photo, and where
    a point of the paper went in it."""
    a = np.array(sheet.img)[:, :, ::-1].copy()
    h, w = a.shape[:2]
    pad = int(max(w, h) * 0.08)
    a = cv2.copyMakeBorder(a, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=DESK)
    H, W = a.shape[:2]
    src = np.float32([[pad, pad], [W - pad, pad], [W - pad, H - pad], [pad, H - pad]])
    j = persp * w
    dst = src + np.float32([[rng.uniform(-j, j), rng.uniform(-j, j)] for _ in range(4)])
    m = cv2.getPerspectiveTransform(src, dst)
    a = cv2.warpPerspective(a, m, (W, H), borderValue=DESK)
    r = cv2.getRotationMatrix2D((W / 2, H / 2), angle, 1.0)
    a = cv2.warpAffine(a, r, (W, H), borderValue=DESK)
    if shadow:
        g = np.linspace(1.0, 1.0 - shadow, W)[None, :, None]
        a = np.clip(a * g, 0, 255).astype(np.uint8)
    if blur:
        a = cv2.GaussianBlur(a, (0, 0), blur)
    if noise:
        a = np.clip(a + np.random.default_rng(rng.randint(0, 1 << 30)).normal(0, noise, a.shape), 0, 255).astype(np.uint8)
    if scale != 1.0:
        a = cv2.resize(a, (int(W * scale), int(H * scale)), interpolation=cv2.INTER_AREA)
    ok, enc = cv2.imencode(".jpg", a, [cv2.IMWRITE_JPEG_QUALITY, jpeg])

    def where(x, y):
        p = cv2.perspectiveTransform(np.float32([[[x + pad, y + pad]]]), m)[0][0]
        q = r @ np.array([p[0], p[1], 1.0])
        return q[0] * scale, q[1] * scale

    return cv2.imdecode(enc, cv2.IMREAD_COLOR), where


HANGUL = re.compile("[가-힣]")


class Ocr:
    """PP-OCRv4 text detection, then each line read twice: by the Chinese+Latin model (digits, A–Z) and by the Korean
    one (한글). The Korean reading is kept when it has 한글 and is about as sure, as ML Kit's Korean model reads both."""

    def __init__(self):
        from rapidocr_onnxruntime import RapidOCR
        self.latin = RapidOCR(det_model_path=os.path.join(MODELS, "ch_v4_det.onnx"),
                              rec_model_path=os.path.join(MODELS, "ch_v4_rec.onnx"), text_score=0.3)
        self.korean = RapidOCR(rec_model_path=os.path.join(MODELS, "korean_rec.onnx"), rec_img_shape=[3, 32, 320], text_score=0.3)

    def lines(self, a):
        boxes, _ = self.latin.text_detector(a)
        if boxes is None or len(boxes) == 0:
            return []
        boxes = self.latin.sorted_boxes(boxes)
        crops = self.latin.get_crop_img_list(a, boxes)
        lat, _ = self.latin.text_recognizer(crops)
        kor, _ = self.korean.text_recognizer(crops)
        out = []
        for box, (lt, ls), (kt, ks) in zip(boxes, lat, kor):
            text, score = (kt, ks) if HANGUL.search(kt) and ks >= ls * 0.8 else (lt, ls)
            if score >= 0.3 and text.strip():
                out.append((box, text.strip(), score))
        return out


def ocr_words(engine, a):
    """Words with boxes, as ML Kit's elements: each line the OCR found, split at spaces in proportion to its letters."""
    words = []
    for quad, text, score in engine.lines(a):
        q = np.array(quad, dtype=np.float32)
        top_l, top_r, bot_r, bot_l = q
        angle = math.degrees(math.atan2(top_r[1] - top_l[1], top_r[0] - top_l[0]))
        parts = text.split()
        total = sum(len(p) for p in parts) + max(0, len(parts) - 1)
        at = 0
        for p in parts:
            f0, f1 = at / total, (at + len(p)) / total
            at += len(p) + 1
            pts = [top_l + (top_r - top_l) * f0, top_l + (top_r - top_l) * f1, bot_l + (bot_r - bot_l) * f1, bot_l + (bot_r - bot_l) * f0]
            words.append(box_of(pts, angle, p))
    return words


def box_of(pts, angle, text):
    xs = [pt[0] for pt in pts]
    ys = [pt[1] for pt in pts]
    return int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys)), round(angle, 2), text


def matched(read, sheet, where):
    """Each word read with the word drawn where it stands ("" when none is), so the test can tell a word read wrong from
    a word put on the wrong day."""
    drawn = []
    for text, l, t, r, b, _ in sheet.drawn:
        x, y = where((l + r) / 2, (t + b) / 2)
        drawn.append((x, y, (b - t), text))
    out = []
    for w in read:
        cx, cy = (w[0] + w[2]) / 2, (w[1] + w[3]) / 2
        near = min(drawn, key=lambda d: (d[0] - cx) ** 2 + (d[1] - cy) ** 2)
        ok = math.hypot(near[0] - cx, near[1] - cy) <= max(near[2], 12)
        out.append(w + (near[3] if ok else "",))
    return out


def drawn_words(sheet, where, rng):
    """Every word drawn, where the photo put it, with an OCR's usual slips; and the cells lost."""
    out = []
    lost = []
    for text, l, t, r, b, cell in sheet.drawn:
        pts = [where(l, t), where(r, t), where(r, b), where(l, b)]
        angle = math.degrees(math.atan2(pts[1][1] - pts[0][1], pts[1][0] - pts[0][0]))
        roll = rng.random()
        if roll < 0.03:
            if cell:
                lost.append(cell)
            continue
        if text == "O" and roll < 0.35:
            text = "0"
        elif text in ("D", "E", "N") and roll < 0.12:
            text = chr(ord(text) - ord("A") + ord("Ａ"))
        out.append(box_of(pts, angle, text))
    # Two day numbers next to each other read as one word now and then ("1213").
    merged = []
    i = 0
    while i < len(out):
        w = out[i]
        if i + 1 < len(out) and w[5].isdigit() and out[i + 1][5].isdigit() and abs(w[1] - out[i + 1][1]) < 20 and rng.random() < 0.06:
            n = out[i + 1]
            merged.append((min(w[0], n[0]), min(w[1], n[1]), max(w[2], n[2]), max(w[3], n[3]), w[4], w[5] + n[5]))
            i += 2
            continue
        merged.append(w)
        i += 1
    return merged, lost


CASES = [
    # name, layout, photo
    ("01-nurse-level", lambda r: horizontal(r, 2026, 10, NAMES[:6], LATIN), dict()),
    ("02-nurse-tilt1", lambda r: horizontal(r, 2026, 10, NAMES[:6], LATIN), dict(angle=1.0, blur=0.6)),
    ("03-nurse-tilt2", lambda r: horizontal(r, 2026, 11, NAMES[:5], LATIN, leave=["연차"]), dict(angle=-2.0, persp=0.01, blur=0.8, jpeg=70)),
    ("04-nurse-tilt3", lambda r: horizontal(r, 2026, 12, NAMES[:7], LATIN), dict(angle=3.0, persp=0.012, shadow=0.35, noise=6)),
    ("05-nurse-tilt5", lambda r: horizontal(r, 2026, 10, NAMES[:5], LATIN), dict(angle=-5.0, blur=0.7, scale=0.8)),
    ("06-nurse-nolines", lambda r: horizontal(r, 2026, 11, NAMES[:6], LATIN, lines=False, shade=False), dict(angle=1.5, noise=4)),
    ("07-nurse-tight", lambda r: horizontal(r, 2026, 10, NAMES[:8], LATIN, cell=46, size=22), dict(angle=-1.2, blur=0.6, jpeg=65)),
    ("08-nurse-halves", lambda r: horizontal(r, 2026, 10, NAMES[:4], LATIN, halves=True), dict(angle=2.0, blur=0.6)),
    ("09-team-korean", lambda r: horizontal(r, 2026, 11, ["1조", "2조", "3조", "4조"], TEAM, weekday=False), dict(angle=-2.5, persp=0.01, shadow=0.25)),
    ("10-korean-words", lambda r: horizontal(r, 2026, 10, NAMES[:4], KOREAN, cell=80, size=24), dict(angle=1.8, blur=0.5)),
    ("11-excel-dates", lambda r: horizontal(r, 2026, 10, NAMES[:6], LATIN, cell=58, size=20, title=False, weekday=False,
                                            day_fmt="{m}/{d}", face="GothicA1-Regular.ttf", label_extra="RN"), dict(angle=0.6, scale=0.9)),
    ("12-myeongjo", lambda r: horizontal(r, 2026, 12, NAMES[:5], LATIN, face="NanumMyeongjo-Regular.ttf"), dict(angle=-3.5, persp=0.015, blur=0.9, noise=5)),
    ("13-handwriting", lambda r: horizontal(r, 2026, 10, NAMES[:4], LATIN, face="NanumPenScript-Regular.ttf", size=34, cell=70), dict(angle=2.2, blur=0.6)),
    ("14-transposed", lambda r: transposed(r, 2026, 10, NAMES[:4], LATIN), dict(angle=1.5, blur=0.5)),
    ("15-list", lambda r: listing(r, 2026, 11, LATIN), dict(angle=-2.0, blur=0.5)),
    ("16-big-shadow", lambda r: horizontal(r, 2026, 10, NAMES[:6], LATIN, leave=["병가"]), dict(angle=4.0, persp=0.02, shadow=0.5, noise=8, jpeg=60)),
]


def write(path, title, truth, words, lost=()):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# {title}\n")
        for label, row in truth.items():
            f.write("truth\t" + label + "\t" + ",".join(row.get(d, "") for d in range(1, max(row) + 1)) + "\n")
        for label, day in lost:
            f.write(f"lost\t{label}\t{day}\n")
        for w in words:
            f.write("word\t" + "\t".join(str(v) for v in w) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", help="also write the photos here")
    ap.add_argument("--only")
    args = ap.parse_args()
    engine = Ocr()
    os.makedirs(OUT, exist_ok=True)
    for name, layout, photo in CASES:
        if args.only and args.only not in name:
            continue
        rng = random.Random(name)
        sheet, truth = layout(rng)
        a, where = photograph(sheet, rng, **photo)
        if args.images:
            os.makedirs(args.images, exist_ok=True)
            cv2.imwrite(os.path.join(args.images, name + ".jpg"), a)
        title = f"{name} {photo}"
        read = ocr_words(engine, a)
        write(os.path.join(OUT, name + ".ocr.tsv"), title + " (RapidOCR; last column: the word drawn there)", truth, matched(read, sheet, where))
        boxes, lost = drawn_words(sheet, where, rng)
        write(os.path.join(OUT, name + ".boxes.tsv"), title + " (drawn boxes; lost: cells the photo lost)", truth, boxes, lost)
        print(name, len(read), "words read", file=sys.stderr)


if __name__ == "__main__":
    main()
