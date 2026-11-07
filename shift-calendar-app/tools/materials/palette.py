"""배경마다 어울리는 색 한 벌(바탕·카드·글자·포인트)을 그림에서 뽑아 out/palette.json에 써요. 테마 생성기가 읽어요."""
import colorsys, json, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")


def hexc(rgb):
    return "#%02X%02X%02X" % tuple(int(max(0, min(255, round(v)))) for v in rgb)


def lum(rgb):
    def ch(v):
        v = v / 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = sorted([lum(a), lum(b)], reverse=True)
    return (la + 0.05) / (lb + 0.05)


def kmeans(px, k=6, it=12, seed=1):
    rng = np.random.default_rng(seed)
    cen = px[rng.choice(len(px), k, replace=False)]
    for _ in range(it):
        d = ((px[:, None, :] - cen[None]) ** 2).sum(-1)
        lab = d.argmin(1)
        for j in range(k):
            m = px[lab == j]
            if len(m):
                cen[j] = m.mean(0)
    counts = np.bincount(lab, minlength=k)
    return cen, counts


def tune(rgb, light_bg):
    """An accent readable on the card: darker on light themes, lighter on dark ones (4.5:1 against the card)."""
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in rgb))
    s = max(s, 0.45)
    card = (255, 255, 255) if light_bg else (24, 24, 30)
    for _ in range(40):
        c = tuple(v * 255 for v in colorsys.hls_to_rgb(h, l, s))
        if contrast(c, card) >= 4.5:
            return c
        l = l - 0.03 if light_bg else l + 0.03
        l = max(0.05, min(0.95, l))
    return c


def main():
    items = json.load(open(os.path.join(OUT, "목록.json"), encoding="utf8"))["items"]
    out = {}
    for m in items:
        if m["kind"] != "bg":
            continue
        im = Image.open(os.path.join(OUT, m["file"])).convert("RGB").resize((60, 126))
        a = np.asarray(im, dtype=np.float64)
        px = a.reshape(-1, 3)
        avg = px.mean(0)
        top = a[:20].reshape(-1, 3).mean(0)
        light = m["light"] >= 0.5
        cen, counts = kmeans(px.copy())
        # The accent: the most colourful cluster that is not tiny.
        best, score = None, -1
        for c, n in zip(cen, counts):
            if n < len(px) * 0.03:
                continue
            h, l, s = colorsys.rgb_to_hls(*(c / 255))
            sc = s * (1 - abs(l - 0.5))
            if sc > score:
                best, score = c, sc
        accent = tune(best, light)
        if light:
            bg = avg * 0.25 + 255 * 0.75
            card = (255, 255, 255)
            ink = tuple(v * 0.18 for v in accent)
            ink = (max(20, ink[0]), max(20, ink[1]), max(28, ink[2]))
            if contrast(ink, card) < 9:
                ink = (28, 28, 36)
        else:
            bg = avg * 0.6
            card = tuple(min(60, v * 0.35 + 18) for v in avg)
            ink = (240, 240, 245)
        on_accent = "#FFFFFF" if contrast(accent, (255, 255, 255)) >= contrast(accent, (17, 17, 17)) else "#111111"
        out[m["key"]] = {
            "id": m["id"], "name": m["name"], "pack": m["pack"], "dark": not light,
            "bg": hexc(bg), "card": hexc(card), "ink": hexc(ink), "accent": hexc(accent), "onAccent": on_accent,
            "top": hexc(top), "avg": hexc(avg),
        }
    json.dump(out, open(os.path.join(OUT, "palette.json"), "w", encoding="utf8"), ensure_ascii=False, indent=1)
    print(len(out), "palettes")


if __name__ == "__main__":
    main()
