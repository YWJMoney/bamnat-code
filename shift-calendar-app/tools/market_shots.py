#!/usr/bin/env python3
"""2.0.0 [RVR-21] (검토 SD-07, 스튜디오 제작 오류 점검 5장 S6-3) 장터 작품 사진 대조: 지난 통합 빌드와 크게 달라진 작품만.

app MarketWorksShotsTest(-Pbamnat.marketShots=all)가 작품마다 넣은 달력의 24×40 색 지문을 build/market-shots/shots.json에 남겨요.
이 도구가 지난 빌드의 shots.json과 대어 평균 차이(0~255)가 THRESHOLD를 넘는 작품을 큰 차이 순으로 알려요. 달라진 것은 실패가
아니에요(일부러 바꾼 화면도 있어요): 사람이 그 사진을 열어 보고 판단해요. 실패는 이것뿐이에요.
- 넣거나 그리다 넘어진 작품(shots.json의 error)
- 모두 찍었는데 작품 이름이 tools/market_works.py와 다름(두 목록이 어긋나면 대조가 말없이 빠져요)
- 되돌린 뒤 달력이 처음과 다름(shots.json의 drift)

달력을 꾸미는 조각(달력 테마·모양, 근무 모양, 달력 칸·머리·주·바닥과 앱 막대·카드 부품)이 있는데 넣어도 달력이 한 점도 바뀌지 않은
작품(px 0)은 따로 알려요(실패 아님): 효과가 말없이 사라진 작품일 수 있어요(2.0.0 첫 실행에서 수식 값 부품이 넣어지지 않던 8개를
이렇게 찾음). 시험 폰에 맞는 날이 없는 조건(메모 3개 이상인 날 등)도 여기 나올 수 있어요.

  python3 tools/market_shots.py compare prev/shots.json app/build/market-shots/shots.json \\
      --png app/build/market-shots/png --changed-out app/build/market-shots/changed >> "$GITHUB_STEP_SUMMARY"
  python3 tools/market_shots.py compare - app/build/market-shots/shots.json      # 지난 것 없음: 이번 것이 기준
"""
import argparse
import base64
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import market_works  # noqa: E402

THRESHOLD = 6.0
SHOW = 40
# What shows on the month calendar the shot is taken of: these kinds of piece, and parts on these ids (the first two words of the id,
# or the app's own bars, cards and chips). Parts of other screens (가계부, 메모, 위젯, 알람), screen colours and 꾸밈 층 often change
# nothing there (2.0.0 전체 실행: 그런 작품 660개 중 535개), so they are not named.
VISIBLE = {"calTheme", "calView", "shiftLook"}
VISIBLE_PARTS = {"cal.cell", "cal.head", "cal.week", "cal.foot"}


def shows_on_calendar(work):
    pieces = work["recipe"].get("pieces", [])
    if VISIBLE & {p.get("kind") for p in pieces}:
        return True
    for p in pieces:
        if p.get("kind") != "parts":
            continue
        for line in p.get("parts", []):
            words = str(line.get("id", "")).split(".")
            if ".".join(words[:2]) in VISIBLE_PARTS or words[0] == "app":
                return True
    return False


def visible_keys(works):
    return {w["key"] for w in works if shows_on_calendar(w)}


def load(path):
    if not path or path == "-" or not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def fp(row):
    return base64.b64decode(row["fp"]) if row and row.get("fp") else None


def distance(a, b):
    if a is None or b is None or len(a) != len(b):
        return None
    return sum(abs(x - y) for x, y in zip(a, b)) / len(a)


def png_name(key):
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in key) + ".png"


def compare(prev, cur, keys=None, threshold=THRESHOLD, visible=None):
    """{changed: [(key, title, d)], new, gone, errors, drift, keyProblems, unchanged: [(key, title)]} for [cur] against [prev]."""
    works = cur.get("works", {})
    res = {"count": len(works), "changed": [], "new": [], "gone": [], "errors": [], "drift": list(cur.get("drift", [])), "keyProblems": [],
           "unchanged": []}
    for k, row in works.items():
        if row.get("error"):
            res["errors"].append((k, row.get("title", ""), row["error"]))
        elif visible is not None and k in visible and row.get("px") == 0:
            res["unchanged"].append((k, row.get("title", "")))
    if keys is not None and cur.get("all"):
        mine, theirs = set(works), set(keys)
        for k in sorted(theirs - mine)[:20]:
            res["keyProblems"].append(f"market_works.py에는 있는데 사진이 없어요: {k}")
        for k in sorted(mine - theirs)[:20]:
            res["keyProblems"].append(f"사진은 있는데 market_works.py에 없어요: {k}")
    if prev is None:
        return res
    old = prev.get("works", {})
    for k, row in works.items():
        if k not in old:
            res["new"].append((k, row.get("title", "")))
            continue
        d = distance(fp(old[k]), fp(row))
        if d is not None and d > threshold:
            res["changed"].append((k, row.get("title", ""), d))
    res["gone"] = [(k, r.get("title", "")) for k, r in old.items() if k not in works]
    res["changed"].sort(key=lambda x: -x[2])
    return res


def failed(res):
    return bool(res["errors"] or res["keyProblems"] or res["drift"])


def markdown(res, had_prev):
    out = ["### 장터 작품 사진 대조 (작품마다 넣은 달력, SD-07 S6-3)", ""]
    if not had_prev:
        out.append(f"지난 통합 빌드의 지문이 없어요. 이번 {res['count']}개가 다음 비교의 기준이에요.")
    else:
        out.append(f"작품 {res['count']}개 중 크게 달라짐 {len(res['changed'])}개 (평균 차이 > {THRESHOLD:g}), 새 작품 {len(res['new'])}개, "
                   f"빠진 작품 {len(res['gone'])}개. 달라진 것은 실패가 아니에요: 사진(artifact market-shots › changed)을 열어 보고 판단해요.")
        if res["changed"]:
            out += ["", "| 작품 | 제목 | 차이 |", "|---|---|---|"]
            out += [f"| `{k}` | {t} | {d:.1f} |" for k, t, d in res["changed"][:SHOW]]
            if len(res["changed"]) > SHOW:
                out.append(f"| … | {len(res['changed']) - SHOW}개 더 | |")
        for k, t in res["gone"][:10]:
            out.append(f"- 빠진 작품 `{k}` {t}")
    for k, t, why in res["errors"][:30]:
        out.append(f"- **넘어짐** `{k}` {t}: {why}")
    for line in res["drift"][:20]:
        out.append(f"- **되돌린 뒤 달력이 처음과 다름** {line}")
    if res.get("unchanged"):
        out += ["", f"달력을 꾸미는 조각이 있는데 넣어도 달력이 그대로인 작품 {len(res['unchanged'])}개 (실패 아님, 효과가 사라졌는지 사진으로 봐요):"]
        out += [f"- `{k}` {t}" for k, t in res["unchanged"][:SHOW]]
        if len(res["unchanged"]) > SHOW:
            out.append(f"- … {len(res['unchanged']) - SHOW}개 더")
    for p in res["keyProblems"]:
        out.append(f"- **이름 어긋남** {p}")
    return "\n".join(out)


def copy_changed(res, png_dir, out_dir):
    if not png_dir or not out_dir:
        return 0
    os.makedirs(out_dir, exist_ok=True)
    n = 0
    for k, *_ in res["changed"] + res["new"] + res["errors"] + res.get("unchanged", []):
        src = os.path.join(png_dir, png_name(k))
        if os.path.isfile(src):
            shutil.copy(src, os.path.join(out_dir, png_name(k)))
            n += 1
    return n


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("compare")
    c.add_argument("prev", help="지난 빌드 shots.json (없으면 -)")
    c.add_argument("cur", help="이번 빌드 shots.json")
    c.add_argument("--png", help="이번 빌드 사진 폴더")
    c.add_argument("--changed-out", help="달라진 작품 사진을 모을 폴더")
    c.add_argument("--json", help="결과를 JSON으로도")
    a = ap.parse_args(argv)
    cur = load(a.cur)
    if cur is None:
        print(f"이번 빌드 사진 지문이 없어요: {a.cur}")
        return 1
    prev = load(a.prev)
    works = market_works.load()
    res = compare(prev, cur, [w["key"] for w in works], visible=visible_keys(works))
    copy_changed(res, a.png, a.changed_out)
    print(markdown(res, prev is not None))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(res, f, ensure_ascii=False, indent=1)
    return 1 if failed(res) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
