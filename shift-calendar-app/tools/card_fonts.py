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
- 결과: `<out>/<version>/<id>-r.ttf`·`-b.ttf`, `<out>/<version>/licenses/<id>.txt`, `<out>/<version>/manifest.json`(sha256·크기·출처).
  공개 저장소 `YWJMoney/-`의 `remote/card-fonts/`에 그대로 올리고, 앱은 `<remoteBaseUrl>card-fonts/<version>/<id>-r.ttf`를 받아
  `--pins`가 쓴 sha256(`CardFontPins.kt`)과 맞을 때만 써요(사용자 2026-11-12 MKT-32 카드 '우리 쪽에서 글꼴 내주기').
- 원본은 google/fonts의 한 커밋에 묶어요(`--commit`, 없으면 그때의 main). 라이선스 확인 표(`--table`)에 글꼴마다 라이선스·예약 이름·
  원본 저장소와 커밋을 적어요. 라이선스 글을 못 받았거나 OFL·Apache가 아닌 글꼴은 만들지 않아요(앱은 폰 글꼴로 그려요).

쓰는 법(fontTools가 필요해요: `pip install fonttools`):
    python3 tools/card_fonts.py --out build/card-fonts            # 목록의 받는 글꼴 전부
    python3 tools/card_fonts.py --out build/card-fonts --only notosanskr,jua
    python3 tools/card_fonts.py --letters                          # 글자 수와 판 번호만
    python3 tools/card_fonts.py --out <공개 저장소>/remote/card-fonts \
        --pins app/src/main/java/com/bamnat/app/ui/recipe/CardFontPins.kt --table docs/card-fonts-라이선스.md
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
# Only these are made: the SIL OFL 1.1 and the Apache License 2.0 allow a changed copy to be passed on (OFL 1·3, Apache 4).
MADE = ("ofl", "apache")
# The name table entries that name the font (family, unique id, full name, PostScript, typographic and WWS family/style,
# Mac compatible full name, variations PostScript prefix).
NAME_IDS = (1, 3, 4, 6, 16, 17, 18, 21, 22, 25)
CHANGED = ("Changed by bamnat (2026): only the letters a bamnat market card shows, hinting removed, renamed 'BN Card {n}' "
           "(SIL OFL 1.1 condition 3 / Apache-2.0 section 4). License: licenses/{id}.txt")


def head(commit: str | None) -> str:
    """The google/fonts commit to read: [commit], or main's now (git ls-remote, no API)."""
    if commit:
        return commit
    out = subprocess.run(["git", "ls-remote", "https://github.com/google/fonts", "refs/heads/main"], capture_output=True, text=True, check=True)
    return out.stdout.split()[0]


def rfn(license_text: str) -> str:
    """The Reserved Font Names a license names, in one line ('' for none)."""
    top = license_text.split("This Font Software is licensed")[0]  # the license's own definition of the words is not one
    m = re.search(r"Reserved Font Names?\s*:?\s*(.+?)(?:\.(?:\s|$)|\n\s*\n|\Z)", top, re.S | re.I | re.M)
    return re.sub(r"\s+", " ", m.group(1)).strip().strip("\"'“”").rstrip(".") if m else ""


def copyright_lines(license_text: str) -> str:
    """The copyright lines at the top of an OFL.txt (before the license itself)."""
    top = license_text.split("This Font Software is licensed")[0]
    return re.sub(r"\s+", " ", top).strip()


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


def metadata(family: str) -> tuple[str, str, list[dict], dict] | None:
    """(license dir, family dir, its files with weight·style·axes, what it says of itself) from google/fonts' METADATA.pb."""
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
        def one(pattern: str) -> str:
            m = re.search(pattern, text)
            return m.group(1) if m else ""
        about = {"license": one(r'\nlicense: "([^"]+)"'), "designer": one(r'\ndesigner: "([^"]+)"'),
                 "repository": one(r'repository_url: "([^"]+)"'), "commit": one(r'source \{[^}]*?commit: "([0-9a-f]+)"'),
                 "copyright": one(r'copyright: "((?:[^"\\]|\\.)*)"')}
        return lic, d, files, about
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


def subset(raw: bytes, axes: dict, weight: int, text: str, label: str, bold: bool, fid: str) -> bytes:
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
             10: CHANGED.format(n=label, id=fid)}
    table = font["name"]
    for rec in list(table.names):
        if rec.nameID in NAME_IDS or rec.nameID in names:
            table.removeNames(nameID=rec.nameID)
    for nid, value in names.items():
        table.setName(value, nid, 3, 1, 0x409)
        table.setName(value, nid, 1, 0, 0)
    if "CFF " in font:  # a PostScript-outline font names itself in its CFF table too
        cff = font["CFF "].cff
        cff.fontNames = [names[6]]
        top = cff.topDictIndex[0]
        for key, value in (("FullName", names[4]), ("FamilyName", family)):
            if hasattr(top, key):
                setattr(top, key, value)
    left = [r.toUnicode() for r in table.names if r.nameID in (1, 4, 6, 16, 18, 21, 25)]
    assert all(v.startswith(("BN Card", "BNCard")) for v in left), left
    # The same original and letters always make the same bytes (anyone can make them again and match the sha256).
    font.recalcTimestamp = False
    out = io.BytesIO()
    font.save(out)
    return out.getvalue()


def pins_kt(version: str, manifest: dict) -> str:
    rows = []
    for fid, e in manifest["fonts"].items():
        for k in ("r", "b"):
            if k in e:
                rows.append(f'        "{fid}-{k}" to "{e[k]["sha256"]}",')
    return ("package com.bamnat.app.ui.recipe\n\n"
            "/**\n * 2.0.0 [RVM-21] MKT-32 장터 카드 글꼴의 sha256 (tools/card_fonts.py --pins가 써요, 손으로 고치지 않아요). The app takes a card font from\n"
            " * our public files only when its bytes are these, so a changed file on the public site is never opened as a font.\n */\n"
            "internal object CardFontPins {\n"
            f'    /** The letters these were made for ([MarketFonts.CHARS]\'s version). */\n    const val VERSION = "{version}"\n\n'
            "    val SHA: Map<String, String> = mapOf(\n" + "\n".join(rows) + "\n    )\n}\n")


def table_md(manifest: dict, skipped: list[str]) -> str:
    out = ["# 장터 카드 글꼴 라이선스 확인표 (MKT-32, tools/card_fonts.py --table)", "",
           f"글자 {manifest['letters']}개, 판 `{manifest['version']}`. 원본: google/fonts 커밋 `{manifest['commit']}`. "
           "라이선스는 그 커밋의 글꼴 폴더 라이선스 글(OFL.txt·LICENSE.txt) 원문과 METADATA.pb의 `license`로 확인했어요.", "",
           "| 번호·바꾼 이름 | 글꼴 | 라이선스 | 예약 이름(RFN) | 원본(google/fonts 폴더 · 위 저장소와 커밋) | 굵기·크기 | 함께 두는 글 |",
           "|---|---|---|---|---|---|---|"]
    for fid, e in manifest["fonts"].items():
        size = " · ".join(f"{e[k]['weight']} {e[k]['bytes'] / 1024:.0f} KB" for k in ("r", "b") if k in e)
        up = f"[{e['repository'].replace('https://github.com/', '')}]({e['repository']}) `{e['upstreamCommit'][:10]}`" if e.get("repository") else "(METADATA에 없음)"
        out.append(f"| {e['name']} | {e['family']} (`{fid}`) | {e['licenseName']} | {e['rfn'] or '없음'} | `{e['from']}` · {up} | {size} | `licenses/{fid}.txt` |")
    if skipped:
        out += ["", "**만들지 않은 것(앱은 폰 글꼴로, 확인 필요)**", ""] + [f"- {s}" for s in skipped]
    return "\n".join(out) + "\n"


def main() -> int:
    global RAW
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=ROOT / "build/card-fonts")
    ap.add_argument("--only", default="", help="쉼표로 나눈 글꼴 id")
    ap.add_argument("--commit", default="", help="google/fonts 커밋(없으면 지금 main)")
    ap.add_argument("--pins", type=Path, help="앱의 CardFontPins.kt를 쓸 곳")
    ap.add_argument("--table", type=Path, help="라이선스 확인표(마크다운)를 쓸 곳")
    ap.add_argument("--letters", action="store_true", help="글자 수와 판 번호만 보여 줘요")
    a = ap.parse_args()
    text, version = letters()
    if a.letters:
        print(f"글자 {len(text)}개, 판 {version}")
        return 0
    commit = head(a.commit or None)
    RAW = f"https://raw.githubusercontent.com/google/fonts/{commit}"
    only = {s for s in a.only.split(",") if s}
    out = a.out / version
    (out / "licenses").mkdir(parents=True, exist_ok=True)
    manifest = {"version": version, "letters": len(text), "source": "github.com/google/fonts", "commit": commit,
                "path": "card-fonts/" + version + "/<id>-r.ttf", "fonts": {}}
    total, failed, skipped = 0, [], []
    for n, (fid, family) in enumerate(catalog(), start=1):
        if only and fid not in only:
            continue
        meta = metadata(family)
        if meta is None:
            failed.append(f"{fid}: google/fonts에 없어요")
            skipped.append(f"{family} (`{fid}`): google/fonts 커밋에 없어요")
            continue
        lic, d, files, about = meta
        if lic not in MADE:
            skipped.append(f"{family} (`{fid}`): 라이선스 {lic.upper()}, 고친 판을 내보내는 조건을 따로 확인해야 해요")
            continue
        lic_text = fetch(f"{RAW}/{lic}/{d}/{LICENSE_FILES[lic]}")
        if lic_text is None:
            failed.append(f"{fid}: 라이선스 글을 못 받았어요")
            skipped.append(f"{family} (`{fid}`): 라이선스 글을 못 받았어요")
            continue
        body = lic_text.decode("utf-8", "replace")
        flat = re.sub(r"\s+", " ", body).upper()
        is_ofl = "SIL OPEN FONT LICENSE VERSION 1.1" in flat
        is_apache = "APACHE LICENSE" in flat and "VERSION 2.0" in flat
        if not (is_ofl if lic == "ofl" else is_apache):
            skipped.append(f"{family} (`{fid}`): 라이선스 글이 {lic.upper()} 원문과 달라요")
            continue
        notice = fetch(f"{RAW}/{lic}/{d}/NOTICE") if lic == "apache" else None
        (out / "licenses" / f"{fid}.txt").write_bytes(lic_text + ((b"\n\n----- NOTICE -----\n" + notice) if notice else b""))
        label = f"{n:02d}"
        entry = {"family": family, "name": f"BN Card {label}", "license": lic,
                 "licenseName": "SIL OFL 1.1" if lic == "ofl" else "Apache 2.0", "metadataLicense": about["license"],
                 "rfn": rfn(body) if lic == "ofl" else "", "copyright": copyright_lines(body) if lic == "ofl" else about["copyright"],
                 "from": f"{lic}/{d}", "repository": about["repository"], "upstreamCommit": about["commit"], "notice": bool(notice)}
        for bold, weights in ((False, [400, 500, 300]), (True, [700])):
            got = pick(files, weights)
            if got is None:
                continue
            f, w = got
            raw = fetch(f"{RAW}/{lic}/{d}/{f['file'].replace('[', '%5B').replace(']', '%5D')}")
            if raw is None:
                failed.append(f"{fid}: {f['file']}를 못 받았어요")
                continue
            data = subset(raw, f["axes"], w, text, label, bold, fid)
            name = f"{fid}-{'b' if bold else 'r'}.ttf"
            (out / name).write_bytes(data)
            entry["b" if bold else "r"] = {"file": name, "weight": w, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                                            "original": f["file"], "originalBytes": len(raw)}
            total += len(data)
            print(f"{name}: {len(data) / 1024:.0f} KB (원본 {f['file']} {len(raw) / 1024 / 1024:.1f} MB, 굵기 {w})", flush=True)
        if "r" in entry:
            manifest["fonts"][fid] = entry
    manifest["bytes"] = total
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    if a.pins:
        a.pins.write_text(pins_kt(version, manifest), encoding="utf-8")
    if a.table:
        a.table.write_text(table_md(manifest, skipped), encoding="utf-8")
    print(f"합계 {total / 1024 / 1024:.1f} MB, 글꼴 {len(manifest['fonts'])}개, 판 {version}, google/fonts {commit[:10]}")
    for f in failed:
        print("못 함:", f, file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
