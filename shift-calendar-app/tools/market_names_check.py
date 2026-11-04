#!/usr/bin/env python3
"""Check that every 조건부 꾸미기 line in the market works reads names its part can see.

PC 4277abca: '분류 색 가계부' and '고정 메모 금색' did nothing, because a ledger line's 분류 and a memo's 고정
(FormulaNames group ROW) were never given to those parts. This reads the formula names and the dated parts from
core and reports, for each `when` or `{"fx": …}` in docs/recipes/market-136.json:
  - a name formulas do not know,
  - a condition on a part that has no day (it never runs),
  - a line value (ROW) on a part that is not a line or card.

Usage (from shift-calendar-app/): python3 tools/market_names_check.py [market.json]
Exit 1 when anything is found. Keep ROW_PARTS in step with logic.scopeFor callers in the app.
"""
import collections
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAMES_KT = ROOT / "core/src/main/kotlin/com/bamnat/core/formula/FormulaNames.kt"
BLUEPRINT_KT = ROOT / "core/src/main/kotlin/com/bamnat/core/recipe/Blueprint.kt"

# Parts drawn for one line or card, given that thing's own values (logic.scopeFor + TabFacts).
ROW_PARTS = {"money.row", "money.amount", "money.table.row",
             "memo.card", "memo.card.title", "memo.card.body", "memo.card.date", "memo.table.row"}
# 급여 › 날짜별 내역 rows get only these line values.
PAY_ROW_NAMES = {"금액", "제목"}
WORDS = set("만약 고르기 모두 하나라도 아님 없으면 칸 포함 시작 끝 길이 개수 합 평균 최대 최소 반올림 내림 올림 절댓값 사이 "
            "글자 숫자 원 비었나 앞 바꾸기 잇기 목록 중하나 그리고 또는 이면 아니면 참 거짓".split())


def formula_names():
    groups, alias = {}, {}
    for line in NAMES_KT.read_text(encoding="utf-8").splitlines():
        m = re.match(r'\s+[A-Z_]+\("([^"]+)", "[a-zA-Z]+", Group\.([A-Z_]+),', line)
        if not m:
            continue
        groups[m.group(1)] = m.group(2)
        tail = re.findall(r'listOf\(([^)]*)\)\)?,?\s*$', line)
        if tail:
            for a in re.findall(r'"([가-힣]+)"', tail[-1]):
                alias[a] = m.group(1)
    return groups, alias


def dated_parts():
    text = BLUEPRINT_KT.read_text(encoding="utf-8").split("val DATED")[1].split(")")[0]
    return set(re.findall(r'"([a-z.]+)"', text))


def main():
    market = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/recipes/market-136.json"
    groups, alias = formula_names()
    dated = dated_parts()
    found = collections.Counter()
    checked = 0

    def check(pid, expr, title):
        nonlocal checked
        checked += 1
        bare = re.sub(r'"(?:[^"\\]|\\.)*"', "", expr)
        for w in re.findall(r"[가-힣]+", bare):
            if w in WORDS:
                continue
            name = alias.get(w, w)
            group = groups.get(name)
            if group is None:
                found[(title, pid, w, "모르는 이름")] += 1
            elif pid not in dated:
                found[(title, pid, w, "날짜 없는 부품이라 돌지 않음")] += 1
            elif group == "ROW" and not (pid in ROW_PARTS or (pid == "pay.row" and name in PAY_ROW_NAMES)):
                found[(title, pid, w, "줄 값인데 줄·카드 부품이 아님")] += 1

    def walk(x, title=None):
        if isinstance(x, dict):
            t = x.get("title", title)
            if "id" in x:
                for k, v in x.items():
                    if k == "when" and isinstance(v, str):
                        check(x["id"], v, t)
                    elif isinstance(v, dict) and isinstance(v.get("fx"), str):
                        check(x["id"], v["fx"], t)
            for v in x.values():
                walk(v, t)
        elif isinstance(x, list):
            for v in x:
                walk(v, title)
        elif isinstance(x, str) and x[:1] in "{[":
            try:
                walk(json.loads(x), title)
            except ValueError:
                pass

    walk(json.loads(market.read_text(encoding="utf-8")))
    for (title, pid, word, why), n in sorted(found.items(), key=lambda kv: (kv[0][3], kv[0][1])):
        print(f"{why}: '{title}' {pid} · {word} ({n})")
    print(f"조건 {checked}개를 봤어요. 문제 {sum(found.values())}개.")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
