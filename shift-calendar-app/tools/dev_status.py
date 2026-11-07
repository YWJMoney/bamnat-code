#!/usr/bin/env python3
"""1.39 [LOOP-05][LOOP-06] 개발 상태 파일(ops/dev-status.jsonl) 검사와 그것으로 만드는 것들.

저장소에는 개발 상태만 둬요. 문의 원문·사진·폰 정보·이름·연락처는 서버에만 있고 여기에는 절대 적지 않아요
(처리방침 5항: GitHub에는 맡기는 개인정보 없음). 한 줄이 개발사항 하나예요(JSON 한 덩어리).

    python3 tools/dev_status.py check            # 줄 검사 + src/dev-built.js가 맞는지
    python3 tools/dev_status.py build            # src/dev-built.js 다시 만들기
    python3 tools/dev_status.py payload --version 1.39.0 --sha <커밋> [--pr N]   # 서버 /api/admin/dev/sync 몸통
    python3 tools/dev_status.py notes 1.39.0     # 그 판의 '업데이트 소식' 줄(출시 노트 초안)
    python3 tools/dev_status.py pc 1.39.0        # 그 판의 PC 확인 목록(check 칸)

칸(쓰는 것만): id(d로 시작하는 서버 개발사항 번호) 또는 track(추적 번호, 예 LOOP-17) 하나는 꼭,
title, status(todo·doing·check·done·rejected), size(fix·polish·feature), route(server·remote·flag·app·hotfix),
remote("kind@번호"), flag, events, tierAsk(free·pro·pass·trial·later, Claude의 제안일 뿐), tierWhy,
publicTitle(앱 '지금 알려진 문제'에 보일 쉬운 제목, 60자), known(true·false), styles(보일 근무 유형 목록, 없으면 모두), test, check, pr,
news(앱 업데이트 소식 한 줄, 80자), shipReply(판이 나간 날 묶인 문의에 가는 답), reason(반려 이유).
요금(tier)은 사용자만 관리자 앱에서 정해요. 이 파일에 tier를 적으면 검사가 막아요.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATUS_FILE = ROOT / "ops" / "dev-status.jsonl"
BUILT_FILE = ROOT / "server" / "company-worker" / "src" / "dev-built.js"

KEYS = {"id", "track", "title", "status", "size", "route", "remote", "flag", "events", "tierAsk", "tierWhy", "publicTitle",
        "known", "styles", "test", "check", "pr", "news", "shipReply", "reason"}
STATUS = {"todo", "doing", "check", "done", "rejected"}
SIZES = {"fix", "polish", "feature"}
ROUTES = {"server", "remote", "flag", "app", "hotfix"}
TIERS = {"free", "pro", "pass", "trial", "later"}
STYLES = {"SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"}
ID = re.compile(r"^d[A-Za-z0-9]{15}$")
TRACK = re.compile(r"^[A-Z][A-Z0-9]{0,9}-\d{1,4}$")
REMOTE = re.compile(r"^[a-z0-9-]{2,30}@\d{1,9}$")
FLAG = re.compile(r"^[A-Za-z0-9._]{2,50}$")
EVENT = re.compile(r"^[a-z0-9_]{2,40}$")
VERSION = re.compile(r"^\d{1,2}\.\d{1,2}\.\d{1,3}$")
# 개인정보가 섞이면 막아요: 메일, 전화번호, 문의 번호(q…), 기기 번호처럼 보이는 긴 16진수.
PERSONAL = [
    (re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"), "메일 주소"),
    (re.compile(r"01[016789][-. ]?\d{3,4}[-. ]?\d{4}"), "전화번호"),
    (re.compile(r"\bq[A-Za-z0-9]{21}\b"), "문의 번호"),
    (re.compile(r"\b[0-9a-f]{32,}\b"), "기기 번호처럼 보이는 값"),
]
LIMITS = {"title": 120, "publicTitle": 60, "news": 80, "shipReply": 600, "tierWhy": 600, "test": 200, "check": 1000, "reason": 300}


def read(path=STATUS_FILE):
    items, errors = [], []
    if not path.exists():
        return items, errors
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            x = json.loads(line)
        except json.JSONDecodeError as e:
            errors.append(f"{n}줄: JSON이 아니에요 ({e.msg})")
            continue
        if not isinstance(x, dict):
            errors.append(f"{n}줄: 한 줄은 {{…}} 하나여야 해요")
            continue
        items.append((n, x))
    return items, errors


def check_item(n, x):
    out = []
    say = lambda m: out.append(f"{n}줄{(' ' + (x.get('track') or x.get('id') or '')) if isinstance(x, dict) else ''}: {m}")
    if "tier" in x:
        say("tier(무료·프로)는 이 파일에 적지 않아요. 사용자가 관리자 앱 › 요금 판단에서 정해요. 제안은 tierAsk로")
    extra = set(x) - KEYS - {"tier"}
    if extra:
        say("모르는 칸: " + ", ".join(sorted(extra)))
    if not x.get("id") and not x.get("track"):
        say("id나 track 중 하나는 꼭 있어야 해요")
    if "id" in x and not (isinstance(x["id"], str) and ID.match(x["id"])):
        say("id는 d로 시작하는 16자예요(관리자 앱 개발사항 번호)")
    if "track" in x and not (isinstance(x["track"], str) and TRACK.match(x["track"])):
        say("track은 LOOP-17 같은 추적 번호예요")
    if x.get("status") not in STATUS:
        say("status는 todo·doing·check·done·rejected 중 하나예요")
    if "size" in x and x["size"] not in SIZES:
        say("size는 fix·polish·feature 중 하나예요")
    if "route" in x and x["route"] not in ROUTES:
        say("route는 server·remote·flag·app·hotfix 중 하나예요")
    if x.get("route") == "remote" and not (isinstance(x.get("remote"), str) and REMOTE.match(x["remote"])):
        say("route remote에는 remote(\"kind@번호\")가 있어야 언제 닿았는지 알아요")
    if x.get("route") == "flag" and not (isinstance(x.get("flag"), str) and FLAG.match(x["flag"])):
        say("route flag에는 flag 이름이 있어야 해요")
    if "events" in x and not (isinstance(x["events"], list) and all(isinstance(e, str) and EVENT.match(e) for e in x["events"]) and len(x["events"]) <= 8):
        say("events는 소문자 이름 8개까지의 목록이에요")
    if "tierAsk" in x and x["tierAsk"] not in TIERS:
        say("tierAsk는 free·pro·pass·trial·later 중 하나예요")
    if "styles" in x and not (isinstance(x["styles"], list) and all(v in STYLES for v in x["styles"])):
        say("styles는 SHIFT·DAY·HOURLY·FIELD·BUSINESS·INVEST 중에서 고른 목록이에요(빈 목록 = 모두)")
    if "known" in x and not isinstance(x["known"], bool):
        say("known은 true나 false예요")
    if "pr" in x and not (isinstance(x["pr"], int) and x["pr"] > 0):
        say("pr은 PR 번호(숫자)예요")
    for k, most in LIMITS.items():
        v = x.get(k)
        if v is None:
            continue
        if not isinstance(v, str):
            say(f"{k}는 글이어야 해요")
        elif len(v) > most:
            say(f"{k}는 {most}자까지예요(지금 {len(v)}자)")
    if x.get("status") == "done":
        if not x.get("shipReply"):
            say("done에는 shipReply(판이 나간 날 묶인 문의에 가는 답)를 꼭 미리 적어요")
        if not x.get("news"):
            say("done에는 news(앱 업데이트 소식 한 줄, 사용자 말로)를 꼭 적어요")
    if x.get("status") == "check" and not x.get("check"):
        say("check에는 PC에서 무엇을 볼지(check 칸)를 적어요")
    if x.get("status") == "rejected" and not x.get("reason"):
        say("rejected에는 reason(반려 이유)을 적어요")
    for k in ("title", "publicTitle", "news", "shipReply", "tierWhy", "check", "test", "reason"):
        v = x.get(k)
        if isinstance(v, str):
            for pat, what in PERSONAL:
                if pat.search(v):
                    say(f"{k}에 {what}가 있어요. 문의 원문·개인정보는 서버에만 둬요")
    return out


def check_all(items):
    errors = []
    seen = {}
    for n, x in items:
        errors += check_item(n, x)
        for k in ("id", "track"):
            v = x.get(k)
            if v:
                if (k, v) in seen:
                    errors.append(f"{n}줄: {k} {v}가 {seen[(k, v)]}줄과 겹쳐요")
                seen[(k, v)] = n
    return errors


def built_text(items):
    names = []
    for _, x in items:
        if x.get("route") == "server" and x.get("status") == "done":
            for k in ("id", "track"):
                if x.get(k) and x[k] not in names:
                    names.append(x[k])
    body = ", ".join(json.dumps(v) for v in names)
    return ("// 1.39 [LOOP-06] 이 서버 코드에 들어간 개발사항(서버로 고친 것). 손으로 고치지 않아요:\n"
            "// tools/dev_status.py가 ops/dev-status.jsonl에서 route \"server\"이고 done인 줄의 d 번호·추적 번호로 만들어요.\n"
            "// 서버가 이 목록에 든 개발사항을 '실제로 닿음'으로 보고 묶인 문의에 바로 답해요(loop.js liveTick).\n"
            f"export const SERVER_BUILT = [{body}];\n")


def payload(items, version, sha, pr):
    if version and not VERSION.match(version):
        raise SystemExit(f"판 번호가 이상해요: {version}")
    out = []
    for _, x in items:
        y = {k: v for k, v in x.items() if k in KEYS}
        out.append(y)
    body = {"version": version or None, "sha": sha or "", "items": out}
    if pr:
        body["pr"] = pr
    return body


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check")
    sub.add_parser("build")
    pl = sub.add_parser("payload")
    pl.add_argument("--version", default="")
    pl.add_argument("--sha", default="")
    pl.add_argument("--pr", type=int, default=0)
    for name in ("notes", "pc"):
        s = sub.add_parser(name)
        s.add_argument("version")
    a = p.parse_args(argv)
    items, errors = read()
    errors += check_all(items)
    if a.cmd == "check":
        if BUILT_FILE.read_text(encoding="utf-8") != built_text(items):
            errors.append("server/company-worker/src/dev-built.js가 개발 상태 파일과 달라요: python3 tools/dev_status.py build")
        for e in errors:
            print(e)
        print(f"개발 상태 {len(items)}줄, 문제 {len(errors)}개")
        return 1 if errors else 0
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1
    if a.cmd == "build":
        BUILT_FILE.write_text(built_text(items), encoding="utf-8")
        print(f"{BUILT_FILE.relative_to(ROOT)} 만듦")
    elif a.cmd == "payload":
        print(json.dumps(payload(items, a.version, a.sha, a.pr), ensure_ascii=False))
    elif a.cmd == "notes":
        for _, x in items:
            if x.get("status") == "done" and x.get("news") and x.get("route", "app") in ("app", "hotfix"):
                print(f"- {x['news']}")
    elif a.cmd == "pc":
        print(f"# {a.version} PC 확인 목록 (개발 상태 파일에서)\n")
        for _, x in items:
            if x.get("check") and x.get("status") in ("check", "done"):
                print(f"- [ ] {x.get('track') or x.get('id')} {x.get('title', '')}: {x['check']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
