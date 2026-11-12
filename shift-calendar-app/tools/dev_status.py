#!/usr/bin/env python3
"""1.39 [LOOP-05][LOOP-06] 개발 상태 파일(ops/dev-status.jsonl) 검사와 그것으로 만드는 것들.

저장소에는 개발 상태만 둬요. 문의 원문·사진·폰 정보·이름·연락처는 서버에만 있고 여기에는 절대 적지 않아요
(처리방침 5항: GitHub에는 맡기는 개인정보 없음). 한 줄이 개발사항 하나예요(JSON 한 덩어리).

    python3 tools/dev_status.py check            # 줄 검사 + src/dev-built.js가 맞는지
    python3 tools/dev_status.py build            # src/dev-built.js 다시 만들기
    python3 tools/dev_status.py payload --version 1.39.0 --sha <커밋> [--pr N]   # 서버 /api/admin/dev/sync 몸통
    python3 tools/dev_status.py payload --sha <커밋> --out <폴더>   # 같은 몸통을 50줄씩 나눈 파일들(body-001.json …)
    python3 tools/dev_status.py notes 1.39.0     # 그 판의 '업데이트 소식' 줄(출시 노트 초안)
    python3 tools/dev_status.py pc 1.39.0        # 그 판의 PC 확인 목록(check 칸)
    python3 tools/dev_status.py misses           # PC 확인·문의로 찾은 문제: 잡을 시험과 '왜 못 잡았나'

칸(쓰는 것만): id(d로 시작하는 서버 개발사항 번호) 또는 track(추적 번호, 예 LOOP-17) 하나는 꼭,
title, status(todo·doing·check·done·rejected), size(fix·polish·feature), route(server·remote·flag·app·hotfix),
remote("kind@번호"), flag, events, tierAsk(free·pro·pass·trial·later, Claude의 제안일 뿐), tierWhy,
publicTitle(앱 '지금 알려진 문제'에 보일 쉬운 제목, 60자), known(true·false), styles(보일 근무 유형 목록, 없으면 모두), test, check, pr,
news(앱 업데이트 소식 한 줄, 80자), shipReply(판이 나간 날 묶인 문의에 가는 답), reason(반려 이유),
miss(2.0.0 [RVR-26] 시험이 왜 못 잡았나, 200자: PC 확인·문의로 찾은 문제는 done 전에 test와 함께 꼭).
test에 적은 시험(클래스·파일·'[번호] 이름'·test_함수)은 check가 저장소에 정말 있는지 봐요. 시험으로 못 잡는 것은 "PC: …".
요금(tier)은 사용자만 관리자 앱에서 정해요. 이 파일에 tier를 적으면 검사가 막아요.
"""
import argparse
import csv
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATUS_FILE = ROOT / "ops" / "dev-status.jsonl"
BUILT_FILE = ROOT / "server" / "company-worker" / "src" / "dev-built.js"
TRACKER = ROOT / "docs" / "tracker.csv"
GRADLE = ROOT / "gradle.properties"
# 2.0.0 [RVR-10] (검토 TEST-15·U60-05) 판이 합쳐진 것: 1.39.1은 따로 내지 않고 2.0.0으로(사용자 2026-11-12), 1.40은 2.0.0이 됐어요.
MERGED = {"1.39.1": "2.0.0", "1.40.0": "2.0.0"}

KEYS = {"id", "track", "title", "status", "size", "route", "remote", "flag", "events", "tierAsk", "tierWhy", "publicTitle",
        "known", "styles", "test", "check", "pr", "news", "shipReply", "reason", "miss"}
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
# 2.0.0 [RVQ-28] 검토 SVC-10: news·shipReply는 판이 나갈 때 사람 확인 없이 모든 사용자·묶인 문의에 나가요. 서버(desk.js broadcastRisk)가
# 걸린 칸을 빼고 보내기 전에, 여기서 먼저 알려요(뜻은 같고 서버가 정본).
BROADCAST = [
    (re.compile(r"(^|\s)a\s?i\s*(상담|답|응답|도우미|어시스턴트|비서|챗)|에이\s*아이|인공\s*지능|챗\s*봇|gpt|\bllm\b|claude|클로드|anthropic|자동\s*(답변|응답)|사람이\s*아니", re.I), "AI·자동 답 표현"),
    (re.compile(r"(https?|ftp)\s*:\s*/\s*/|www\s*\.|[a-z0-9-]+\s*\.\s*(com|net|org|kr|co|io|me|ly|gl|app|dev|xyz|link|site|shop|kakao)(?![A-Za-z0-9])", re.I), "링크·주소"),
    (re.compile(r"(\+?82[\s.-]?0?|0)\d{1,2}[\s.-]?\d{3,4}[\s.-]?\d{4}"), "전화번호"),
    (re.compile(r"오픈\s*채팅|단톡|카톡\s*방|텔레그램|디스코드|/api/|/admin|관리자\s*(페이지|화면|앱|계정)|서버\s*(주소|구조|코드)|소스\s*코드|깃허브|github|토큰|비밀\s*(값|키)|데이터\s*베이스", re.I), "보안 정보·단톡방"),
]
LIMITS = {"title": 120, "publicTitle": 60, "news": 80, "shipReply": 600, "tierWhy": 600, "test": 200, "check": 1000, "reason": 300, "miss": 200}


def read(path=None):
    path = path or STATUS_FILE
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


def one_of(v, allowed):
    """[RVR-10] A value from a set: a list or a number in the line is a wrong value, not a crash (TypeError)."""
    return isinstance(v, str) and v in allowed


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
    if not one_of(x.get("status"), STATUS):
        say("status는 todo·doing·check·done·rejected 중 하나예요")
    if "size" in x and not one_of(x["size"], SIZES):
        say("size는 fix·polish·feature 중 하나예요")
    if "route" in x and not one_of(x["route"], ROUTES):
        say("route는 server·remote·flag·app·hotfix 중 하나예요")
    if x.get("route") == "remote" and not (isinstance(x.get("remote"), str) and REMOTE.match(x["remote"])):
        say("route remote에는 remote(\"kind@번호\")가 있어야 언제 닿았는지 알아요")
    if x.get("route") == "flag" and not (isinstance(x.get("flag"), str) and FLAG.match(x["flag"])):
        say("route flag에는 flag 이름이 있어야 해요")
    if "events" in x and not (isinstance(x["events"], list) and all(isinstance(e, str) and EVENT.match(e) for e in x["events"]) and len(x["events"]) <= 8):
        say("events는 소문자 이름 8개까지의 목록이에요")
    if "tierAsk" in x and not one_of(x["tierAsk"], TIERS):
        say("tierAsk는 free·pro·pass·trial·later 중 하나예요")
    if "styles" in x and not (isinstance(x["styles"], list) and all(one_of(v, STYLES) for v in x["styles"])):
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
        # [LOOP-21] 재현 시험 먼저: 문의로 생긴(d 번호) 고침은 그 문제를 다시 만드는 시험 이름을 남겨요. 시험으로 못 잡는 것은 "PC: …"로.
        if x.get("id") and x.get("size") == "fix" and not x.get("test"):
            say("문의로 생긴 고침(size fix + id)의 done에는 test(그 문제를 재현한 시험 이름, 시험으로 못 잡으면 \"PC: 무엇을 봤는지\")를 적어요")
    if x.get("status") == "check" and not x.get("check"):
        say("check에는 PC에서 무엇을 볼지(check 칸)를 적어요")
    if x.get("status") == "rejected" and not x.get("reason"):
        say("rejected에는 reason(반려 이유)을 적어요")
    for k in ("news", "shipReply"):
        v = x.get(k)
        if isinstance(v, str):
            for pat, what in BROADCAST:
                if pat.search(v):
                    say(f"{k}에 {what}이 있어요. 판이 나갈 때 사람 확인 없이 나가는 글이라 서버가 그 칸을 빼요")
    for k in ("title", "publicTitle", "news", "shipReply", "tierWhy", "check", "test", "reason", "miss"):
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


# ---------- 2.0.0 [RVR-26] (검토 I60c-02) 시험이 왜 못 잡았나 ----------
# PC 확인(검토 추적의 PC-… 줄)이나 문의(d 번호 + fix)로 찾은 문제는 '이걸 잡을 시험'(test)과 '시험이 왜 못 잡았나'(miss)를 남겨요.
# done이 되려면 둘 다 있어야 하고(오류), check인 동안은 경고 한 줄로 세어요. test에 적은 시험은 저장소에 정말 있어야 해요(오류).

REVIEW = ROOT / "docs" / "plans" / "2.0.0-검토-추적.csv"
TEST_DIRS = ("app/src/test", "core/src/test", "wear/src/test", "server/company-worker/test", "server/share-worker/test",
             "server/site-worker/test", "tools")
WORKER_DIRS = ("", "server/company-worker", "server/share-worker", "server/site-worker")
PATH_TOKEN = re.compile(r"[\w./-]*[\w-]+\.(?:test\.mjs|mjs|py|js|kt)\b")
CLASS_TOKEN = re.compile(r"\b([A-Z][A-Za-z0-9]*Test)\b(?:\.([A-Za-z_][A-Za-z0-9_]*))?")
PY_TOKEN = re.compile(r"\btest_[A-Za-z0-9_]+")


class Tests:
    """저장소의 시험 파일 색인: 파일 이름, Kotlin 시험 클래스, 시험 글(이름으로 찾기)."""

    def __init__(self, root=ROOT):
        self.root = Path(root)
        self.paths: list[Path] = []
        for d in TEST_DIRS:
            base = self.root / d
            if not base.is_dir():
                continue
            for f in base.rglob("*"):
                if f.suffix in (".kt", ".mjs", ".js", ".py") and f.is_file() and not {"node_modules", "build", "__pycache__"} & set(f.parts):
                    self.paths.append(f)
        self.by_name: dict[str, list[Path]] = {}
        for f in self.paths:
            self.by_name.setdefault(f.name, []).append(f)
        self._text: dict[Path, str] = {}
        self.classes: dict[str, Path] = {}
        for f in self.paths:
            if f.suffix == ".kt":
                self.classes.setdefault(f.stem, f)
                for m in re.finditer(r"\bclass\s+([A-Z][A-Za-z0-9]*Test)\b", self.text(f)):
                    self.classes.setdefault(m.group(1), f)

    def text(self, f: Path) -> str:
        if f not in self._text:
            try:
                self._text[f] = f.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                self._text[f] = ""
        return self._text[f]

    def has_path(self, p: str) -> bool:
        if any((self.root / w / p).is_file() for w in WORKER_DIRS):
            return True
        return bool(self.by_name.get(Path(p).name))

    def problems(self, test: str) -> list[str]:
        """test 칸 하나 → 못 찾은 것들(빈 목록이면 됨)."""
        t = (test or "").strip()
        if not t or t.startswith("PC:"):
            return []
        out, found = [], 0
        rest = t
        for m in PATH_TOKEN.finditer(t):
            found += 1
            if not self.has_path(m.group(0)):
                out.append(f"시험 파일 {m.group(0)}: 저장소에 없어요")
        rest = PATH_TOKEN.sub(" ", rest)
        for m in CLASS_TOKEN.finditer(rest):
            found += 1
            f = self.classes.get(m.group(1))
            if not f:
                out.append(f"시험 {m.group(1)}: 저장소에 없어요")
            elif m.group(2) and not re.search(r"\bfun\s+`?" + re.escape(m.group(2)) + r"\b", self.text(f)):
                out.append(f"{m.group(1)}.{m.group(2)}: 그 클래스에 이 시험이 없어요")
        rest = CLASS_TOKEN.sub(" ", rest)
        for m in PY_TOKEN.finditer(rest):
            found += 1
            if not any(f.suffix == ".py" and ("def " + m.group(0) + "(") in self.text(f) for f in self.paths):
                out.append(f"파이썬 시험 {m.group(0)}: 저장소에 없어요")
        if found:
            return out
        if t.startswith("["):
            # node·관리자 앱 시험은 이름으로: '[RVD-03] …' 앞 40자가 어느 시험 파일에 그대로 있어야 해요.
            head = t[:40].rstrip()
            if not any(head in self.text(f) for f in self.paths if f.suffix in (".mjs", ".js", ".py")):
                return [f"'{head}…'(으)로 시작하는 시험이 없어요"]
            return []
        return ["시험을 알아보지 못했어요(클래스 이름·시험 파일·'[번호] 시험 이름'·test_함수, 시험으로 못 잡으면 \"PC: …\")"]


def missing_tests(items, tests: "Tests | None" = None) -> list[str]:
    tests = tests or Tests()
    out = []
    for n, x in items:
        if isinstance(x.get("test"), str):
            for m in tests.problems(x["test"]):
                out.append(f"{n}줄 {x.get('track') or x.get('id')}: test: {m}")
    return out


def pc_found(path=None) -> dict[str, list[str]]:
    """검토 추적에서 PC 확인으로 찾은 줄(PC-…)이 이어진 추적 번호 → PC 번호들."""
    out: dict[str, list[str]] = {}
    try:
        with (path or REVIEW).open(encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                if (r.get("id") or "").startswith("PC-"):
                    for t in re.split(r"[,·\s]+", r.get("track") or ""):
                        if t:
                            out.setdefault(t, []).append(r["id"])
    except OSError:
        pass
    return out


def found_outside(x, pcs) -> list[str]:
    """시험 밖에서 찾은 문제인지: PC 확인(PC-… 줄이 이어짐·track이 PC-…) 또는 문의로 생긴 고침(d 번호 + fix). 근거 목록."""
    why = []
    t = x.get("track") if isinstance(x.get("track"), str) else ""
    if t.startswith("PC-"):
        why.append(t)
    why += [p for p in pcs.get(t, []) if p not in why]
    if x.get("id") and x.get("size") == "fix":
        why.append("문의")
    return why


def miss_rules(items, pcs) -> tuple[list[str], list[str]]:
    """(오류, 경고): done인데 test·miss가 없으면 오류, check·doing이면 경고 한 줄로 셈."""
    errors, waiting = [], []
    for n, x in items:
        why = found_outside(x, pcs)
        if not why:
            continue
        lack = [k for k in ("test", "miss") if not x.get(k)]
        if not lack:
            continue
        if x.get("status") == "done":
            errors.append(f"{n}줄 {x.get('track') or x.get('id')}: {'·'.join(why[:3])}에서 찾은 문제라 done에는 "
                          + " · ".join({"test": "test(이걸 잡을 시험)", "miss": "miss(시험이 왜 못 잡았나)"}[k] for k in lack) + "를 적어요")
        elif x.get("status") in ("doing", "check"):
            waiting.append(x.get("track") or x.get("id"))
    warns = [f"PC 확인·문의로 찾은 줄 {len(waiting)}개가 아직 test·miss가 없어요(done 전에 적어요): " + ", ".join(waiting[:12])
             + (" …" if len(waiting) > 12 else "")] if waiting else []
    return errors, warns


def norm(v):
    """[RVR-10] '1.39' → '1.39.0', '2 (1.39.0)' → '1.39.0', a merged release to the one it went out in; None when there is none."""
    m = re.search(r"\d+\.\d+(?:\.\d+)?", str(v or ""))
    if not m:
        return None
    s = m.group(0) + (".0" if m.group(0).count(".") == 1 else "")
    return MERGED.get(s, s)


def key(v):
    return tuple(int(p) for p in v.split("."))


def phases(path=None):
    """[RVR-10] 판 of each tracking number, from docs/tracker.csv's phase (the file itself carries no 판, ops/README)."""
    try:
        with (path or TRACKER).open(encoding="utf-8") as f:
            return {r["id"]: norm(r["phase"]) for r in csv.DictReader(f)}
    except OSError:
        return {}


def version_of(x, ph):
    return ph.get(x.get("track")) if isinstance(x.get("track"), str) else None


def current_version(path=None):
    try:
        m = re.search(r"^bamnat\.versionName=(\S+)", (path or GRADLE).read_text(encoding="utf-8"), re.M)
        return norm(m.group(1)) if m else None
    except OSError:
        return None


def stale(items, ph, now):
    """[RVR-10] (TEST-15) Lines of a release that already went out (판 below the one in gradle.properties) still not done:
    the release's notes and answers to inquiries are sent only for done lines, so they are said as warnings."""
    out = []
    if not now:
        return out
    for n, x in items:
        v = version_of(x, ph)
        if v and key(v) < key(now) and x.get("status") in ("todo", "doing", "check"):
            out.append(f"{n}줄 {x.get('track') or x.get('id')}: 나간 판 {v}의 줄이 아직 {x.get('status')}예요 (PC 확인이 끝났으면 done + news·shipReply로)")
    return out


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


# 2.0.0 [HQ-77] 서버는 한 번에 200줄까지만 받아요(loop.js LOOP.syncItems). 2.0.0은 574줄이라 한 덩어리로는 통째로 거절돼요.
# 새 줄 하나에 DB를 열 번쯤 부르니(Workers 한 번의 일에 1,000번까지) 넉넉히 50줄씩 나눠 보내요.
SYNC_CHUNK = 50


def payloads(items, version, sha, pr, chunk=SYNC_CHUNK):
    """The sync bodies in order, at most [chunk] lines each; no lines, one empty body."""
    if not 1 <= chunk <= 200:
        raise SystemExit(f"한 번에 보내는 줄 수가 이상해요: {chunk} (1~200)")
    body = payload(items, version, sha, pr)
    rows = body.pop("items")
    return [{**body, "items": rows[i:i + chunk]} for i in range(0, max(len(rows), 1), chunk)]


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check")
    sub.add_parser("build")
    pl = sub.add_parser("payload")
    pl.add_argument("--version", default="")
    pl.add_argument("--sha", default="")
    pl.add_argument("--pr", type=int, default=0)
    pl.add_argument("--out", default="", help="나눈 몸통을 쓸 폴더(body-001.json …). 없으면 한 덩어리를 그대로 찍어요")
    pl.add_argument("--chunk", type=int, default=SYNC_CHUNK)
    sub.add_parser("misses")
    for name in ("notes", "pc"):
        s = sub.add_parser(name)
        s.add_argument("version")
    a = p.parse_args(argv)
    items, errors = read()
    errors += check_all(items)
    ph = phases()
    pcs = pc_found()
    if a.cmd == "check":
        if BUILT_FILE.read_text(encoding="utf-8") != built_text(items):
            errors.append("server/company-worker/src/dev-built.js가 개발 상태 파일과 달라요: python3 tools/dev_status.py build")
        errors += missing_tests(items)
        miss_errors, miss_warns = miss_rules(items, pcs)
        errors += miss_errors
        for e in errors:
            print(e)
        warns = stale(items, ph, current_version()) + miss_warns
        if warns:
            print(f"경고 {len(warns)}개 (실패는 아니에요):")
            for w in warns:
                print("  " + w)
        print(f"개발 상태 {len(items)}줄, 문제 {len(errors)}개" + (f", 경고 {len(warns)}개" if warns else ""))
        return 1 if errors else 0
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1
    if a.cmd == "misses":
        rows = [(x, found_outside(x, pcs)) for _, x in items]
        rows = [(x, w) for x, w in rows if w]
        print(f"PC 확인·문의로 찾은 문제 {len(rows)}줄: 잡을 시험 있음 {sum(1 for x, _ in rows if x.get('test'))}, "
              f"왜 못 잡았나 있음 {sum(1 for x, _ in rows if x.get('miss'))}")
        for x, w in rows:
            print(f"- {x.get('track') or x.get('id')} ({'·'.join(w[:3])}) {x.get('status')}: 시험 {x.get('test') or '없음'}"
                  + (f" / 왜 못 잡았나: {x['miss']}" if x.get("miss") else ""))
        return 0
    if a.cmd == "build":
        BUILT_FILE.write_text(built_text(items), encoding="utf-8")
        print(f"{BUILT_FILE.relative_to(ROOT)} 만듦")
    elif a.cmd == "payload":
        if a.out:
            out = Path(a.out)
            out.mkdir(parents=True, exist_ok=True)
            parts = payloads(items, a.version, a.sha, a.pr, a.chunk)
            for n, body in enumerate(parts, 1):
                (out / f"body-{n:03d}.json").write_text(json.dumps(body, ensure_ascii=False), encoding="utf-8")
            print(len(parts))
        else:
            print(json.dumps(payload(items, a.version, a.sha, a.pr), ensure_ascii=False))
    elif a.cmd in ("notes", "pc"):
        # [RVR-10] (U60-05) Only the lines of the 판 asked for; lines whose 판 is not known (a d 번호 without a tracking number)
        # are counted on stderr, not mixed in.
        want = norm(a.version)
        if not want:
            raise SystemExit(f"판 번호가 이상해요: {a.version}")
        mine = [(n, x) for n, x in items if version_of(x, ph) == want]
        unknown = [x.get("id") or x.get("track") for _, x in items if not version_of(x, ph)]
        if a.cmd == "notes":
            for _, x in mine:
                if x.get("status") == "done" and x.get("news") and x.get("route", "app") in ("app", "hotfix"):
                    print(f"- {x['news']}")
        else:
            print(f"# {want} PC 확인 목록 (개발 상태 파일에서)\n")
            for _, x in mine:
                if x.get("check") and x.get("status") in ("check", "done"):
                    # [RVR-26] 잡을 시험이 있으면 같이: PC에서 본 것이 시험과 다르면 그 시험이 틀린 거예요.
                    print(f"- [ ] {x.get('track') or x.get('id')} {x.get('title', '')}: {x['check']}"
                          + (f" (잡을 시험: {x['test']})" if x.get("test") else ""))
        if unknown:
            print(f"판을 모르는 줄 {len(unknown)}개(추적 번호 없음): " + ", ".join(map(str, unknown[:10])), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
