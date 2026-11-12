#!/usr/bin/env python3
"""2.0.0 [RVM-21] MKT-32 장터 카드 글꼴 조각 만들기.

장터 카드(예시 폰)가 그리는 글자만 담은 공개 글꼴 조각을 우리 쪽에서 내보내려고 만들어요(장터를 보기만 해도 Google Fonts로
요청이 가던 것을 없애기, 처리방침 '공개 글꼴을 고르지 않으면 이 요청은 생기지 않습니다').

- 글꼴 목록: core `FontCatalog.kt`의 받는 글꼴(공급처 이름이 있는 것).
- 글자: app `MarketFonts.kt`의 CHARS(앱과 같은 순서·같은 판 번호 `version` = Java String.hashCode의 16진수).
- 원본: google/fonts 저장소(`ofl/`·`apache/`)의 METADATA.pb와 TTF. 가변 글꼴은 굵기를 고정해요(보통 400→500→300, 굵게 700).
- 조각은 OFL에서 '고친 판'이에요(OFL-FAQ 1.1-update8 2.5·2.6, https://openfontlicense.org/ofl-faq/). 예약 글꼴 이름(RFN)을
  쓰지 못하니, 모든 조각의 이름(name 표 1·3·4·6·16·17·21·22)을 'BN Card <목록 순번>'으로 바꾸고(원래 이름이 조금도 들어가지 않게
  id 대신 번호) 저작권(0)·라이선스(13·14) 줄은 그대로 둬요.
  라이선스 글(OFL.txt·LICENSE.txt)은 `licenses/<id>.txt`로 같이 둬요(OFL 2항).
- 결과: `<out>/<id>-<version>-r.ttf`·`-b.ttf`, `<out>/licenses/`, `<out>/manifest.json`(sha256·크기·출처).

쓰는 법(fontTools가 필요해요: `pip install fonttools`):
    python3 tools/card_fonts.py --out build/card-fonts            # 목록의 받는 글꼴 전부
    python3 tools/card_fonts.py --out build/card-fonts --only notosanskr,jua
    python3 tools/card_fonts.py --letters                          # 글자 수와 판 번호만
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "core/src/main/kotlin/com/bamnat/core/recipe/FontCatalog.kt"
MARKET_FONTS = ROOT / "app/src/main/java/com/bamnat/app/ui/recipe/MarketFonts.kt"
RAW = "https://raw.githubusercontent.com/google/fonts/main"
LICENSE_FILES = {"ofl": "OFL.txt", "apache": "LICENSE.txt", "ufl": "UFL.txt"}
# The name table entries that name the font (family, unique id, full name, PostScript, typographic and WWS family/style).
NAME_IDS = (1, 3, 4, 6, 16, 17, 21, 22)


def catalog() -> list[tuple[str, str]]:
    """(id, provider family) of every font that comes down, in the catalogue's order."""
    src = CATALOG.read_text(encoding="utf-8")
    return [(m.group(2), m.group(4)) for m in re.finditer(r'\b([kl])\("([a-z0-9]+)",\s*"([^"]+)",\s*Group\.\w+,\s*"([^"]+)"\)', src)]


def _kotlin_string(lit: str) -> str:
    out, i = [], 0
    while i < len(lit):
        c = lit[i]
        if c == "\\" and i + 1 < len(lit):
            nxt = lit[i + 1]
            out.append({"n": "\n", "t": "\t", "\\": "\\", '"': '"', "$": "$", "'": "'"}.get(nxt, nxt))
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def _const(src: str, name: str) -> str:
    """A `const val NAME = "a" + "b" + OTHER` of MarketFonts.kt, worked out."""
    m = re.search(rf"(?:private\s+)?const val {name}\s*=\s*(.+?)\n\s*\n", src, re.S)
    if not m:
        raise SystemExit(f"MarketFonts.kt에서 {name}를 찾지 못했어요")
    expr, parts = m.group(1), []
    for tok in re.finditer(r'"((?:[^"\\]|\\.)*)"|\b([A-Z_]+)\b', expr):
        if tok.group(1) is not None:
            parts.append(_kotlin_string(tok.group(1)))
        elif tok.group(2) != name:
            parts.append(_const(src, tok.group(2)))
    return "".join(parts)


def java_hash_hex(s: str) -> str:
    """Integer.toHexString(s.hashCode()), as the app names its card font files."""
    h = 0
    for unit in s.encode("utf-16-be").hex(" ", 2).split():
        h = (31 * h + int(unit, 16)) & 0xFFFFFFFF
    return format(h, "x")


def letters() -> tuple[str, str]:
    """MarketFonts' letters as sent (once each, in order) and the version its file names carry."""
    src = MARKET_FONTS.read_text(encoding="utf-8")
    chars = _const(src, "CHARS")
    sent = "".join(sorted(set(chars)))
    return sent, java_hash_hex(sent)


def fetch(url: str) -> bytes | None:
    r = subprocess.run(["curl", "-sfL", "-m", "60", url], capture_output=True)
    return r.stdout if r.returncode == 0 else None


def metadata(family: str) -> tuple[str, str, list[dict]] | None:
    """(license dir, family dir, its files with weight·style·axes) from google/fonts' METADATA.pb."""
    d = family.lower().replace(" ", "")
    for lic in LICENSE_FILES:
        pb = fetch(f"{RAW}/{lic}/{d}/METADATA.pb")
        if pb is None:
            continue
        text = pb.decode("utf-8")
        files = []
        for block in re.finditer(r"fonts \{(.*?)\n\}", text, re.S):
            b = block.group(1)
            files.append({
                "file": re.search(r'filename: "([^"]+)"', b).group(1),
                "weight": int(re.search(r"weight: (\d+)", b).group(1)),
                "style": re.search(r'style: "([^"]+)"', b).group(1),
            })
        axes = {m.group(1): (float(m.group(2)), float(m.group(3)))
                for m in re.finditer(r'axes \{\s*tag: "(\w+)"\s*min_value: ([\d.]+)\s*max_value: ([\d.]+)', text)}
        for f in files:
            f["axes"] = axes if "[" in f["file"] else {}
        return lic, d, files
    return None


def pick(files: list[dict], weights: list[int]) -> tuple[dict, int] | None:
    """The normal-style file for the first of [weights] it has (a variable file has every weight in its range)."""
    normal = [f for f in files if f["style"] == "normal"]
    for w in weights:
        for f in normal:
            lo, hi = f["axes"].get("wght", (f["weight"], f["weight"]))
            if lo <= w <= hi:
                return f, w
    return None


def subset(raw: bytes, axes: dict, weight: int, text: str, label: str, bold: bool) -> bytes:
    from fontTools.ttLib import TTFont
    from fontTools.subset import Options, Subsetter
    from fontTools.varLib.instancer import instantiateVariableFont

    font = TTFont(io.BytesIO(raw))
    if axes:
        # Every axis pinned: wght at [weight], the others at their defaults.
        pins = {tag: (weight if tag == "wght" else None) for tag in axes}
        font = instantiateVariableFont(font, {k: v for k, v in pins.items() if v is not None}, inplace=False, updateFontNames=False)
        if "fvar" in font:
            font = instantiateVariableFont(font, {a.axisTag: a.defaultValue for a in font["fvar"].axes}, inplace=False)
    opts = Options()
    opts.hinting = False
    opts.desubroutinize = True
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.layout_features = ["*"]
    opts.notdef_outline = True
    sub = Subsetter(opts)
    sub.populate(text=text)
    sub.subset(font)
    # OFL 3항: a Modified Version does not carry the Reserved Font Names; every card font is named the same way.
    family = f"BN Card {label}"
    style = "Bold" if bold else "Regular"
    names = {1: family, 2: style, 3: f"{family} {style} card", 4: f"{family} {style}", 6: f"BNCard{label}-{style}",
             16: family, 17: style, 21: family, 22: style}
    table = font["name"]
    for rec in list(table.names):
        if rec.nameID in NAME_IDS:
            table.removeNames(nameID=rec.nameID)
    for nid, value in names.items():
        if nid == 2 or nid in NAME_IDS:
            table.setName(value, nid, 3, 1, 0x409)
            table.setName(value, nid, 1, 0, 0)
    out = io.BytesIO()
    font.save(out)
    return out.getvalue()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=ROOT / "build/card-fonts")
    ap.add_argument("--only", default="", help="쉼표로 나눈 글꼴 id")
    ap.add_argument("--letters", action="store_true", help="글자 수와 판 번호만 보여 줘요")
    a = ap.parse_args()
    text, version = letters()
    if a.letters:
        print(f"글자 {len(text)}개, 판 {version}")
        return 0
    only = {s for s in a.only.split(",") if s}
    a.out.mkdir(parents=True, exist_ok=True)
    (a.out / "licenses").mkdir(exist_ok=True)
    manifest = {"version": version, "letters": len(text), "source": "github.com/google/fonts (main)", "fonts": {}}
    total, failed = 0, []
    for n, (fid, family) in enumerate(catalog(), start=1):
        if only and fid not in only:
            continue
        meta = metadata(family)
        if meta is None:
            failed.append(f"{fid}: google/fonts에 없어요")
            continue
        lic, d, files = meta
        entry = {"family": family, "name": f"BN Card {n:02d}", "license": lic, "from": f"{lic}/{d}"}
        lic_text = fetch(f"{RAW}/{lic}/{d}/{LICENSE_FILES[lic]}")
        if lic_text is None:
            failed.append(f"{fid}: 라이선스 글을 못 받았어요")
            continue
        (a.out / "licenses" / f"{fid}.txt").write_bytes(lic_text)
        for bold, weights in ((False, [400, 500, 300]), (True, [700])):
            got = pick(files, weights)
            if got is None:
                continue
            f, w = got
            raw = fetch(f"{RAW}/{lic}/{d}/{f['file'].replace('[', '%5B').replace(']', '%5D')}")
            if raw is None:
                failed.append(f"{fid}: {f['file']}를 못 받았어요")
                continue
            data = subset(raw, f["axes"], w, text, f"{n:02d}", bold)
            name = f"{fid}-{version}-{'b' if bold else 'r'}.ttf"
            (a.out / name).write_bytes(data)
            entry["b" if bold else "r"] = {"file": name, "weight": w, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
            total += len(data)
            print(f"{name}: {len(data) / 1024:.0f} KB (원본 {f['file']} {len(raw) / 1024 / 1024:.1f} MB, 굵기 {w})", flush=True)
        manifest["fonts"][fid] = entry
    (a.out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"합계 {total / 1024 / 1024:.1f} MB, 글꼴 {len(manifest['fonts'])}개, 판 {version}")
    for f in failed:
        print("못 함:", f, file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
