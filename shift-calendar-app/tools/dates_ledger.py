#!/usr/bin/env python3
"""2.0.0 [RVR-38] 검토 I60c-01: 날짜가 걸린 약속을 한 장부(ops/dates.json)로.

처리방침 시행·보류 끝, 마을 쉬기, 출시 열차, 새해 요율, 공휴일 표 끝 해 같은 날이 코드 곳곳에 흩어져 있어요. 장부는 날짜를
따로 적지 않고 그 줄(from: 파일과 정규식)을 가리켜 코드에서 읽어요. 그래서 코드가 날을 옮겨도 장부가 낡지 않고, 그 줄이 없어지거나
둘이 되면 check가 실패해요(장부가 가리키는 곳이 사라진 것).

  python3 tools/dates_ledger.py check                  장부가 가리키는 줄이 모두 하나씩 있는지(시험·CI)
  python3 tools/dates_ledger.py list [--today D] [--days N]
                                                       다가오는 날(D-n), 지난 7일, 미리 할 일. 빈 다음 해 경고도
  python3 tools/dates_ledger.py json [--today D]       같은 것을 JSON으로(관리자 앱 '다가오는 날짜' 카드·날짜 시험용)
  python3 tools/dates_ledger.py shifts [--today D] [--days N] [--base 0,7,30,90,365]
                                                       날짜 시험(date-watch)이 돌릴 '오늘부터 며칠 뒤' 목록: base와,
                                                       N일(기본 10) 안에 오는 장부 날마다 그날·다음 날(켜지는 날과 켜진 뒤)
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path

APP = Path(__file__).resolve().parents[1]
LEDGER = APP / "ops/dates.json"
KST = dt.timezone(dt.timedelta(hours=9))


class Bad(Exception):
    pass


def today_kst() -> dt.date:
    return dt.datetime.now(KST).date()


def plus_years(d: dt.date, n: int) -> dt.date:
    try:
        return d.replace(year=d.year + n)
    except ValueError:  # 2월 29일
        return d.replace(year=d.year + n, day=28)


def read_from(src: dict, root: Path) -> dt.date:
    """from: {file, re, pick?, plus_days?, plus_years?, md?} → 그 줄의 날짜."""
    path = (root / src["file"]).resolve()
    if not path.exists():
        raise Bad(f"파일이 없어요: {src['file']}")
    found = re.findall(src["re"], path.read_text(encoding="utf-8"))
    pick = src.get("pick", "only")
    if not found:
        raise Bad(f"{src['file']}에서 가리키는 줄을 못 찾았어요: {src['re']}")
    if pick == "only" and len(found) != 1:
        raise Bad(f"{src['file']}에서 가리키는 줄이 {len(found)}개예요(하나여야 해요): {src['re']}")
    g = found[-1] if pick == "last" else found[0]
    g = g if isinstance(g, tuple) else (g,)
    if len(g) == 3:
        d = dt.date(int(g[0]), int(g[1]), int(g[2]))
    elif re.fullmatch(r"\d{4}-\d{2}-\d{2}", g[0]):
        d = dt.date.fromisoformat(g[0])
    elif re.fullmatch(r"\d{4}", g[0]):
        m, day = (int(x) for x in src.get("md", "01-01").split("-"))
        d = dt.date(int(g[0]), m, day)
    else:
        raise Bad(f"{src['file']}: 날짜로 읽을 수 없어요: {g}")
    d = plus_years(d, int(src.get("plus_years", 0)))
    return d + dt.timedelta(days=int(src.get("plus_days", 0)))


def load(path: Path = LEDGER, root: Path = APP) -> tuple[list[dict], list[str]]:
    """장부의 줄마다 날짜를 읽어 {id, date, what, area, before, source, next?}. 문제는 따로 모아요."""
    data = json.loads(path.read_text(encoding="utf-8"))
    rows, problems, seen = [], [], set()
    for e in data.get("dates", []):
        rid = e.get("id", "")
        if not rid or rid in seen:
            problems.append(f"id가 없거나 두 번이에요: {rid!r}")
            continue
        seen.add(rid)
        empty = [k for k in ("what", "area", "before") if not e.get(k)]
        if empty:
            problems.append(f"{rid}: {'·'.join(empty)}이 비었어요")
            continue
        try:
            if "from" in e and "date" in e:
                raise Bad("from과 date 중 하나만 적어요(날짜는 코드에서 읽어요)")
            if "from" in e:
                d, source = read_from(e["from"], root), e["from"]["file"]
            elif "date" in e:
                if not e.get("source"):
                    raise Bad("사람이 정한 날(date)은 누가·어디서 정했는지 source를 적어요")
                d, source = dt.date.fromisoformat(e["date"]), e["source"]
            else:
                raise Bad("from(코드의 줄)이나 date가 있어야 해요")
        except (Bad, ValueError, KeyError, re.error) as x:
            problems.append(f"{rid}: {x}")
            continue
        rows.append({"id": rid, "date": d, "what": e["what"], "area": e["area"], "before": e["before"], "source": source, "next": e.get("next")})
    rows.sort(key=lambda r: (r["date"], r["id"]))
    return rows, problems


def gaps(rows: list[dict], today: dt.date) -> list[dict]:
    """다음 해 자료가 아직 없을 때(next): 빈 날이 warn_days 안으로 들어오면 경고."""
    out = []
    for r in rows:
        nx = r.get("next")
        if not nx:
            continue
        empty = plus_years(r["date"], int(nx.get("plus_years", 0))) if nx.get("plus_years") else r["date"] + dt.timedelta(days=1)
        left = (empty - today).days
        if left <= int(nx.get("warn_days", 90)):
            out.append({"id": r["id"], "date": empty, "left": left, "what": nx.get("what", r["what"]), "before": r["before"]})
    return out


def upcoming(rows, today, days):
    return [r for r in rows if -7 <= (r["date"] - today).days <= days]


def dday(d: dt.date, today: dt.date) -> str:
    k = (d - today).days
    return "오늘" if k == 0 else (f"D-{k}" if k > 0 else f"{-k}일 지남")


def kdate(d: dt.date) -> str:
    return f"{d.year}. {d.month}. {d.day}. ({'월화수목금토일'[d.weekday()]})"


def as_json(rows, today, days) -> dict:
    def one(r):
        return {"id": r["id"], "date": r["date"].isoformat(), "left": (r["date"] - today).days, "what": r["what"],
                "area": r["area"], "before": r["before"], "source": r["source"]}
    return {"today": today.isoformat(), "upcoming": [one(r) for r in upcoming(rows, today, days)],
            "gaps": [{**g, "date": g["date"].isoformat()} for g in gaps(rows, today)],
            "all": [one(r) for r in rows]}


def shifts(rows, today, days, base) -> list[int]:
    out = set(base)
    for r in rows:
        k = (r["date"] - today).days
        if 0 <= k <= days:
            out.update((k, k + 1))
    return sorted(out)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["check", "list", "json", "shifts"])
    ap.add_argument("--today", type=dt.date.fromisoformat, default=None)
    ap.add_argument("--days", type=int, default=None)
    ap.add_argument("--base", default="0,7,30,90,365")
    ap.add_argument("--ledger", type=Path, default=LEDGER)
    a = ap.parse_args(argv)
    today = a.today or today_kst()
    rows, problems = load(a.ledger)
    if a.cmd == "check":
        for p in problems:
            print("문제:", p)
        print(f"날짜 장부 {len(rows)}줄, 문제 {len(problems)}개")
        return 1 if problems else 0
    if problems:  # 읽은 줄로는 계속하되 알려요(날짜 시험이 장부 탓에 멈추지 않게)
        print("\n".join("문제: " + p for p in problems), file=sys.stderr)
    if a.cmd == "json":
        print(json.dumps(as_json(rows, today, a.days or 60), ensure_ascii=False, indent=1))
    elif a.cmd == "shifts":
        print(json.dumps(shifts(rows, today, a.days or 10, [int(x) for x in a.base.split(",") if x.strip()])))
    else:
        days = a.days or 60
        up = upcoming(rows, today, days)
        print(f"## 다가오는 날짜 ({kdate(today)} 기준, {days}일 안)\n")
        if not up:
            print("없어요.")
        for r in up:
            print(f"- **{dday(r['date'], today)}** {kdate(r['date'])} {r['what']} ({r['area']})\n  - 미리: {r['before']}\n  - 어디: `{r['source']}`")
        for g in gaps(rows, today):
            print(f"- ⚠️ **{dday(g['date'], today)}** {kdate(g['date'])}부터 {g['what']}\n  - 미리: {g['before']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
