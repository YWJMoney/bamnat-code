#!/usr/bin/env python3
"""2.0.0 [RVO-26] 처리방침 3.14 '나. 추가 후보'를 한 번에 넣어요 (총괄 11-19: '가'는 사용자가 고른 16줄, '나'는 도구 한 번으로 더함).

후보는 docs/legal/3.14-extra.json 에 있어요(무엇이 가는지·갈래·7일/30일·고칠 곳). 사용자가 줄을 고르면:

    python3 tools/legal_extra.py list                 후보와 지금 페이지에 들어갔는지
    python3 tools/legal_extra.py check                후보마다 고칠 곳이 페이지에 한 번씩 있는지(페이지가 바뀌어도 넣을 수 있는지)
    python3 tools/legal_extra.py add n4-1 n4-3        고른 줄을 페이지와 머리말 목록에 넣음
    python3 tools/legal_dates.py --rev 3.14 --site <공개 저장소>   그다음 공개 저장소와 기록을 맞춤

{P7}·{P30}은 3.14의 7일·30일 줄 날짜 표시(<time data-legal="r314-p7|r314-p30">)로 바뀌어 legal_dates.py가 함께 옮겨요.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import legal_dates as ld  # noqa: E402

DATA = ld.APP / "docs/legal/3.14-extra.json"
# 머리말 목록의 끝(3.14 줄 목록 다음에 (이전) 3.13이 와요).
HEADER_END = '</ul>\n<p class="muted">(이전) 처리방침 3.13'


def load() -> dict:
    return json.loads(DATA.read_text(encoding="utf-8"))


def tags() -> dict[str, str]:
    prefix = ld.REVS["3.14"]["prefix"]
    when = ld.days(ld.posted_in_code("3.14"))
    return {k.upper(): f'<time data-legal="{prefix}{k.lower()}" datetime="{when[k.lower()].isoformat()}">{ld.ymd(when[k.lower()])}</time>'
            for k in ("P7", "P30")}


def fill(text: str, t: dict[str, str]) -> str:
    return text.replace("{P7}", t["P7"]).replace("{P30}", t["P30"])


def page(name: str) -> Path:
    return ld.ROOT / name


def state(item: dict, t: dict[str, str]) -> str:
    """'들어감', '넣을 수 있음', 또는 무엇이 안 맞는지."""
    done, ready = True, True
    for e in item["edits"]:
        text = page(e["page"]).read_text(encoding="utf-8")
        if text.count(fill(e["replace"], t)) != 1:
            done = False
        if text.count(e["find"]) != 1:
            ready = False
    if done:
        return "들어감"
    return "넣을 수 있음" if ready else "고칠 곳이 페이지에 한 번 있지 않아요"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("what", choices=["list", "check", "add"])
    ap.add_argument("ids", nargs="*")
    a = ap.parse_args()
    data = load()
    t = tags()
    items = {i["id"]: i for i in data["items"]}
    if a.what in ("list", "check"):
        bad = 0
        for i in data["items"]:
            s = state(i, t)
            bad += s not in ("들어감", "넣을 수 있음")
            print(f"{i['id']:<15} {i['days']}일  {s:<12} {i['title']} · {i['lane']} · 추천 {i['advice']}")
        if a.what == "check" and bad:
            sys.exit(1)
        return
    unknown = [x for x in a.ids if x not in items]
    if not a.ids or unknown:
        sys.exit(f"넣을 id를 적어요 (모르는 id: {unknown}). 후보: {', '.join(items)}")
    texts: dict[str, str] = {}
    for x in a.ids:
        item = items[x]
        if state(item, t) == "들어감":
            print(f"{x}: 이미 들어감")
            continue
        for e in item["edits"]:
            text = texts.get(e["page"]) or page(e["page"]).read_text(encoding="utf-8")
            if text.count(e["find"]) != 1:
                sys.exit(f"{x}: {e['page']}에서 고칠 곳이 한 번 있지 않아요: {e['find'][:60]}")
            texts[e["page"]] = text.replace(e["find"], fill(e["replace"], t))
        if item.get("header"):
            p = "privacy/index.html"
            text = texts.get(p) or page(p).read_text(encoding="utf-8")
            if text.count(HEADER_END) != 1:
                sys.exit("머리말 목록의 끝을 찾지 못했어요")
            texts[p] = text.replace(HEADER_END, "  " + fill(item["header"], t) + "\n" + HEADER_END)
        print(f"{x}: 넣음 ({item['days']}일 줄) {item['title']}")
    for name, text in texts.items():
        page(name).write_text(text, encoding="utf-8")
    if texts:
        print("이제 python3 tools/legal_dates.py --rev 3.14 --site <공개 저장소> 로 공개 저장소와 기록을 맞춰요.")


if __name__ == "__main__":
    main()
