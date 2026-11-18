#!/usr/bin/env python3
"""2.0.0 [RVA-56] (검토 SX-04) 장터 건강 한 장: 작품 사진 대조(SD-07 market_shots.py --json)와 옛 앱 열기(old_core_check.py --json)를 묶어
관리자 앱 › 장터 › 장터 건강과 운영 장부 '깨진 작품' 줄이 읽는 모양(market-health.json)으로 만들어요.

  python3 tools/market_shots.py compare prev.json cur.json --json app/build/market-compare.json
  python3 tools/old_core_check.py --json app/build/old-core-check.json
  python3 tools/market_health.py --shots app/build/market-compare.json --old app/build/old-core-check.json \\
      --build "$GITHUB_SHA" --out app/build/market-health.json

나온 파일은 CI artifact(market-shots)에 들어가요. 올리기는 운영 열쇠를 가진 쪽(Claude)이나 관리자가 해요:
  curl -s -H "authorization: Bearer $BAMNAT_OPS_KEY" -H "x-admin: 1" -H "content-type: application/json" \\
      --data @app/build/market-health.json https://<서버>/api/admin/recipes/health
작품 이름·제목·까닭 한 줄·빌드뿐이고 사람 정보는 없어요.
"""
import argparse
import datetime
import json
import os
import sys

MAX = 400


def load(path):
    if not path or path == "-" or not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build_report(shots, old, build, at=None):
    """{format, v, build, at, works, listed, opened, broken[], changed[], unchanged[]} from the two tools' JSON (either may be None)."""
    broken, seen = [], set()

    def add(key, title, kind, why, app=None):
        if (key, kind) in seen or len(broken) >= MAX:
            return
        seen.add((key, kind))
        row = {"key": key, "title": title or "", "kind": kind, "why": str(why or "")[:160]}
        if app is not None:
            row["app"] = app
        broken.append(row)

    works = 0
    changed, unchanged = [], []
    if shots:
        works = shots.get("count", 0)
        for k, t, why in shots.get("errors", []):
            add(k, t, "error", why)
        for line in shots.get("drift", []):
            # "a#3 셋 (40px)": 이름은 줄로, 나머지(제목·차이)는 까닭으로.
            key, _, rest = str(line).partition(" ")
            add(key, "", "drift", rest or line)
        changed = [{"key": k, "title": t, "d": round(float(d), 1)} for k, t, d in shots.get("changed", [])][:60]
        unchanged = [{"key": k, "title": t} for k, t in shots.get("unchanged", [])][:60]
    listed = opened = 0
    if old:
        for r in old.get("reports", []):
            listed += r.get("listed", 0)
            opened += r.get("opened", 0)
            for b in r.get("broken", []):
                add(b.get("key", ""), b.get("title", ""), "old", b.get("why", ""), r.get("app"))
            for b in r.get("refused", []):
                add(b.get("key", ""), b.get("title", ""), "refused", b.get("why", ""))
    return {"format": "bamnat-market-health", "v": 1, "build": str(build or "")[:40],
            "at": at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "works": works, "listed": listed, "opened": opened, "broken": [b for b in broken if b["key"]], "changed": changed, "unchanged": unchanged}


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--shots", help="market_shots.py compare --json 결과")
    ap.add_argument("--old", help="old_core_check.py --json 결과")
    ap.add_argument("--build", required=True, help="빌드 커밋")
    ap.add_argument("--out", required=True)
    a = ap.parse_args(argv)
    shots, old = load(a.shots), load(a.old)
    if shots is None and old is None:
        print("장터 건강: 묶을 결과가 없어요")
        return 0
    r = build_report(shots, old, a.build)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(r, f, ensure_ascii=False, indent=1)
    print(f"장터 건강: 작품 {r['works']} · 깨짐 {len(r['broken'])} · 달라짐 {len(r['changed'])} → {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
