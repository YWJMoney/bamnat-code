#!/usr/bin/env python3
"""2.0.0 검토 추적표 검사·요약.

docs/plans/2.0.0-검토-추적.csv 의 모든 줄(검토 2026-11-12의 발견·사용성 제안·유형 빈틈·더할 것)이
상태를 가지고, 끝난 줄에는 추적 번호가, '안 함'·'결정 대기'에는 이유가 있는지 봐요.

  python3 tools/review_track.py check        # 문제가 있으면 1로 끝나요
  python3 tools/review_track.py summary      # 갈래별 남은 수
  python3 tools/review_track.py todo H       # 갈래 H의 남은 줄(심각도 순)
"""
import csv
import sys
from collections import Counter, defaultdict
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "docs/plans/2.0.0-검토-추적.csv"
STATUSES = ["할 일", "진행 중", "코드 고침(확인 전)", "확인됨", "결정 대기", "안 함", "이미 됨"]
NEEDS_TRACK = {"진행 중", "코드 고침(확인 전)", "확인됨"}
NEEDS_NOTE = {"안 함", "결정 대기", "이미 됨"}
SEV_ORDER = {"높음": 0, "상": 0, "중간": 1, "중": 1, "낮음": 2, "하": 2}
KIND_ORDER = {"발견": 0, "유형 빈틈": 1, "사용성": 2, "더할것(다듬기)": 3, "더할것(새 기능)": 4, "더할것(혁신)": 5}


def rows():
    with PATH.open(newline="") as f:
        return list(csv.DictReader(f))


def check(rs):
    problems = []
    seen = set()
    for r in rs:
        i = r["id"]
        if i in seen:
            problems.append(f"{i}: 같은 번호가 두 줄")
        seen.add(i)
        s = r["status"]
        if s not in STATUSES:
            problems.append(f"{i}: 모르는 상태 '{s}' (쓸 수 있는 것: {', '.join(STATUSES)})")
        if s in NEEDS_TRACK and not r["track"].strip():
            problems.append(f"{i}: '{s}'인데 추적 번호(track)가 비었어요")
        if s in NEEDS_NOTE and not r["note"].strip():
            problems.append(f"{i}: '{s}'인데 이유(note)가 비었어요")
        if not r["branch"].strip():
            problems.append(f"{i}: 맡은 갈래가 없어요")
    return problems


def summary(rs):
    by = defaultdict(Counter)
    for r in rs:
        by[r["branch"]][r["status"]] += 1
    total = Counter(r["status"] for r in rs)
    print(f"전체 {len(rs)}줄: " + ", ".join(f"{s} {total[s]}" for s in STATUSES if total[s]))
    for b in sorted(by):
        c = by[b]
        left = c["할 일"] + c["진행 중"]
        print(f"  {b}: 남음 {left} / {sum(c.values())}  (" + ", ".join(f"{s} {c[s]}" for s in STATUSES if c[s]) + ")")


def todo(rs, branch):
    left = [r for r in rs if r["branch"] == branch and r["status"] in ("할 일", "진행 중")]
    left.sort(key=lambda r: (SEV_ORDER.get(r["sev"], 3), KIND_ORDER.get(r["kind"], 9), r["id"]))
    for r in left:
        print(f"{r['id']}\t{r['sev'] or '-'}\t{r['kind']}\t{r['title'][:90]}")
    print(f"({len(left)}줄)")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    rs = rows()
    if cmd == "check":
        p = check(rs)
        for x in p[:50]:
            print(x)
        print(f"검토 추적 {len(rs)}줄, 문제 {len(p)}개")
        sys.exit(1 if p else 0)
    elif cmd == "summary":
        summary(rs)
    elif cmd == "todo":
        todo(rs, sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)
