#!/usr/bin/env python3
"""[RCP-18] 1.34 나눔 판: the emoji the app offers and the server takes, from Unicode's own lists.

Inputs (downloaded by hand, not kept in the repository):
  emoji-test.txt  https://unicode.org/Public/emoji/16.0/emoji-test.txt  (UTS #51 fully-qualified list)
  ko.xml          https://github.com/unicode-org/cldr/blob/main/common/annotations/ko.xml  (CLDR 한국어 이름·검색어)
Both are © Unicode, Inc., under the Unicode License v3 (https://www.unicode.org/license.txt), which allows copying
and changing them with this notice kept.

Only emoji up to Emoji 15.0 are taken, so a phone a few years old still draws every one (Android 14 = Emoji 15.0).

Outputs:
  core/src/main/resources/com/bamnat/core/recipe/emoji.tsv   one base emoji a line: emoji, group, 한국어 검색어, skin-tone forms
  server/company-worker/src/emoji-set.js                       every allowed emoji string, for cleanRecipe()

Usage: python3 tools/emoji_list.py emoji-test.txt ko.xml
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAX_VERSION = 15.0
# The app's own group names, in the order the picker shows them (UTS #51 groups; Component is never offered alone).
GROUPS = {
    "Smileys & Emotion": "표정",
    "People & Body": "사람",
    "Animals & Nature": "동물·자연",
    "Food & Drink": "음식",
    "Travel & Places": "장소",
    "Activities": "활동",
    "Objects": "물건",
    "Symbols": "기호",
    "Flags": "깃발",
}
TONES = ["\U0001F3FB", "\U0001F3FC", "\U0001F3FD", "\U0001F3FE", "\U0001F3FF"]


def read_emoji(path):
    group = None
    out = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
            continue
        if not line or line.startswith("#"):
            continue
        m = re.match(r"^([0-9A-F ]+);\s*fully-qualified\s*#\s*\S+\s+E(\d+\.\d+)\s+(.*)$", line)
        if not m or group not in GROUPS:
            continue
        if float(m.group(2)) > MAX_VERSION:
            continue
        cps = [int(c, 16) for c in m.group(1).split()]
        out.append(("".join(chr(c) for c in cps), group, m.group(3)))
    return out


def read_names(path):
    names = {}
    for a in ET.parse(path).getroot().iter("annotation"):
        cp = a.get("cp")
        words = [w.strip() for w in (a.text or "").split("|") if w.strip()]
        if a.get("type") == "tts":
            names.setdefault(cp, []).insert(0, words[0] if words else "")
        else:
            names.setdefault(cp, []).extend(words)
    return names


def main(test_txt, ko_xml):
    emoji = read_emoji(test_txt)
    names = read_names(ko_xml)
    allowed = [e for e, _, _ in emoji]
    toned = {}
    bases = []
    for e, g, en in emoji:
        if any(t in e for t in TONES):
            base = e
            for t in TONES:
                base = base.replace(t, "")
            # A toned form's base is the same sequence without the tone (and with U+FE0F where the base needs it).
            key = base if base in {x for x, _, _ in emoji} else base + "️" if (base + "️") in allowed else None
            if key is None:
                key = base.replace("️", "")
            toned.setdefault(key, []).append(e)
        else:
            bases.append((e, g, en))
    lines = []
    for e, g, en in bases:
        words = names.get(e.replace("️", ""), [])
        seen = []
        for w in words:
            if w and w not in seen:
                seen.append(w)
        forms = toned.get(e, [])
        lines.append("\t".join([e, GROUPS[g], " ".join(seen).replace("\t", " "), " ".join(forms)]))
    core = ROOT / "core/src/main/resources/com/bamnat/core/recipe/emoji.tsv"
    core.parent.mkdir(parents=True, exist_ok=True)
    core.write_text(
        "# [RCP-18] tools/emoji_list.py에서 만들어요. Unicode emoji-test.txt 16.0(Emoji 15.0까지)와 CLDR ko 이름, "
        "© Unicode, Inc. Unicode License v3.\n" + "\n".join(lines) + "\n", encoding="utf-8")
    server = ROOT / "server/company-worker/src/emoji-set.js"
    js = []
    for x in allowed:
        js.append('"' + "".join(c if 0x20 <= ord(c) < 0x7f else "\\u{%x}" % ord(c) for c in x) + '"')
    server.write_text(
        "// [RCP-18] tools/emoji_list.py에서 만들어요. 손으로 고치지 않아요.\n"
        "// Unicode emoji-test.txt 16.0 (Emoji 15.0까지), © Unicode, Inc. Unicode License v3.\n"
        f"export const EMOJI = new Set([{','.join(js)}]);\n", encoding="utf-8")
    print(f"{len(bases)} bases, {len(allowed)} allowed")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
