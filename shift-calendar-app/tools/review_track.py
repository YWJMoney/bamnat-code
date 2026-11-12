#!/usr/bin/env python3
"""2.0.0 검토 추적표 검사·요약.

docs/plans/2.0.0-검토-추적.csv 의 모든 줄(검토 2026-11-12의 발견·사용성 제안·유형 빈틈·더할 것)이
상태를 가지고, 끝난 줄에는 추적 번호가, '안 함'·'결정 대기'에는 이유가 있는지 봐요.

  python3 tools/review_track.py check        # 문제가 있으면 1로 끝나요
  python3 tools/review_track.py summary      # 갈래별 남은 수
  python3 tools/review_track.py todo H       # 갈래 H의 남은 줄(믿음 줄 → 심각도 순, 같은 뿌리·날짜·결정 표시)
  python3 tools/review_track.py stats        # 통합할 때마다 계획 6절 표에 적는 한 줄

발견 852건의 우선·PC 실동작·날짜·결정·같은 뿌리는 docs/plans/2.0.0/검토-보탬.csv(검토 스레드, id로 붙음)에서 읽어요.
"""
import csv
import signal
import sys
from collections import Counter, defaultdict
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "docs/plans/2.0.0-검토-추적.csv"
EXTRA = Path(__file__).resolve().parent.parent / "docs/plans/2.0.0/검토-보탬.csv"
STATUSES = ["할 일", "진행 중", "코드 고침(확인 전)", "확인됨", "결정 대기", "안 함", "이미 됨"]
NEEDS_TRACK = {"진행 중", "코드 고침(확인 전)", "확인됨"}
NEEDS_NOTE = {"안 함", "결정 대기", "이미 됨"}
SEV_ORDER = {"높음": 0, "상": 0, "중간": 1, "중": 1, "낮음": 2, "하": 2}
KIND_ORDER = {"발견": 0, "유형 빈틈": 1, "사용성": 2, "더할것(다듬기)": 3, "더할것(새 기능)": 4, "더할것(혁신)": 5}


def rows():
    with PATH.open(newline="") as f:
        return list(csv.DictReader(f))


def extra():
    """id → 보탬 줄(우선, PC 실동작, 날짜, 결정, 같은 뿌리). 파일이 없으면 빈 것."""
    if not EXTRA.exists():
        return {}
    with EXTRA.open(newline="") as f:
        return {r["id"]: r for r in csv.DictReader(f)}


def trust(r, ex):
    """믿음 줄: 높음이거나, 보탬 표의 우선이 '1 믿음'으로 시작하는 줄."""
    e = ex.get(r["id"])
    return r["sev"] == "높음" or bool(e and e["우선"].startswith("1 "))


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
    ex = extra()
    left = [r for r in rs if r["branch"] == branch and r["status"] in ("할 일", "진행 중")]
    left.sort(key=lambda r: (0 if trust(r, ex) else 1, SEV_ORDER.get(r["sev"], 3), KIND_ORDER.get(r["kind"], 9), r["id"]))
    for r in left:
        e = ex.get(r["id"], {})
        tags = [e.get(k, "") for k in ("PC 실동작(9차 빌드, 11-12)", "날짜", "결정(11장)", "같은 뿌리")]
        tag = " · ".join(t for t in tags if t)
        mark = "★" if trust(r, ex) else " "
        print(f"{mark} {r['id']}\t{r['sev'] or '-'}\t{r['kind']}\t{r['title'][:90]}" + (f"\t[{tag}]" if tag else ""))
    print(f"({len(left)}줄, ★ 믿음 줄 {sum(1 for r in left if trust(r, ex))})")


def stats(rs):
    ex = extra()
    c = Counter(r["status"] for r in rs)
    todo_rows = [r for r in rs if r["status"] == "할 일"]
    high = sum(1 for r in todo_rows if r["kind"] == "발견" and r["sev"] == "높음")
    tr = sum(1 for r in todo_rows if r["kind"] == "발견" and trust(r, ex))
    print("| 날짜 | 할 일 | 진행 중 | 코드 고침(확인 전) | 확인됨 | 결정 대기 | 이미 됨·안 함 | 할 일 가운데 높음 | 할 일 가운데 믿음 줄 |")
    print(f"| | {c['할 일']} | {c['진행 중']} | {c['코드 고침(확인 전)']} | {c['확인됨']} | {c['결정 대기']} | {c['이미 됨'] + c['안 함']} | {high} | {tr} |")


if __name__ == "__main__":
    if hasattr(signal, "SIGPIPE"):
        signal.signal(signal.SIGPIPE, signal.SIG_DFL)  # | head 로 잘라 봐도 조용히 끝나게
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
    elif cmd == "stats":
        stats(rs)
    else:
        print(__doc__)
        sys.exit(2)
