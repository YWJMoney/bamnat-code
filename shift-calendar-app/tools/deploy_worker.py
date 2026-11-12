#!/usr/bin/env python3
"""1.39 [DEPLOY-01] 서버 자동 배포: 크롬·대시보드 붙여 넣기 없이 GitHub Actions에서 두 Worker를 올려요.

사용자 결정(2026-11-08): 'Claude가 끝까지'. 승인 단계 없이 Claude가 workflow를 시작하고, 아래 안전장치가 막아요.

2.0.0 [RVQ-01] 검토 SRV-02: 고른 브랜치의 코드(npm ci·npm test·번들)는 Cloudflare 토큰이 **없는** 일(build)에서만 돌고, 토큰이 있는
일(ship)은 main의 이 도구와 번들 파일·설정만 받아 wrangler를 깨끗한 폴더에서 불러요. 고른 브랜치의 코드는 토큰과 한 기계에 있지 않아요.

  python3 tools/deploy_worker.py --check-ref <ref>                       # 0. main·열린 PR의 브랜치·그 안의 커밋만(sha= 출력)
  python3 tools/deploy_worker.py --phase probe --worker both              # 0. 토큰(main 도구만): 지금 판을 올린 커밋(<worker>_prev=)
  python3 tools/deploy_worker.py --phase build --worker both --out <폴더> [--prev <앞 판 server/>]  # 1. 토큰 없이: 시험·번들·스키마 호환·판 이름
  python3 tools/deploy_worker.py --phase ship --worker both --from <폴더> --mode check|deploy --sha <커밋> --reason <글> [--allow-older]

ship 순서 (한 단계라도 어긋나면 손님을 받기 전에 멈춰요)
  1. 지금 100%로 도는 판(앞 판)과 그 판의 바인딩(DB·KV·변수·비밀값 이름)을 읽어요. 두 판이 나눠 돌고 있으면 멈춰요.
  2. build가 만든 설정(wrangler.toml)이 아는 칸만 쓰는지 봐요([build] 같은 '명령을 돌리는 칸'이 있으면 멈춤, RVQ-01).
  3. wrangler.toml의 자리표시자(DB·KV 번호)를 앞 판의 바인딩에서 채운 배포용 설정을 만들어요(저장소에 번호를 적지 않아요).
  4. 판 이름이 지금 도는 판보다 낮으면 멈춰요(--allow-older로만 넘어가요). 지금 판은 그 Worker 자신의 것으로 봐요:
     공유는 /api/health, 회사는 /api/admin/health의 판(2.0.0 [PC-32]), 없으면 앞 판을 올릴 때 이 도구가 적은 메시지 맨 앞
     판 이름(2.0.0 [RVD-19]). 회사 판 이름을 알 수 없으면(대시보드에서 올린 옛 판 등) 낮은지 모르니 멈춰요.
  5. `wrangler versions upload`: 새 판을 올리기만 해요(아직 손님 안 받음).
  6. 새 판의 바인딩 이름이 앞 판을 모두 담는지 대조해요. 대시보드에서만 넣은 일반 변수가 빠졌으면 앞 판 값으로 한 번 더 올리고,
     그래도 빠지면 멈춰요(올린 판은 쓰이지 않고 남을 뿐이에요). check 모드는 여기까지.
  7. 2.0.0 [RVQ-03] 회사 서버는 먼저 새 판 10%·앞 판 90%로 10분(카나리). 그동안 새 판에 직접(판 고정 머리글) health·config를 보내고,
     서버가 판마다 센 실제 요청의 5xx·응답 시간을 견줘 새 판이 눈에 띄게 나쁘면 앞 판 100%로 되돌리고 멈춰요. 요청이 적어 견줄 수
     없으면 새 판 직접 확인만 보고 넘어가요. (모든 서버를 먼저 올리고 대조한 뒤에 배포를 시작해요.)
  8. `wrangler versions deploy <새 판>@100%`, 정기 작업(cron)이 wrangler.toml과 다르면 맞춰요(앞 cron은 적어 둬요).
  9. health를 10초 간격 5번 + 배포 상태가 새 판 100%인지 확인. 하나라도 실패하면 앞 판을 100%로, cron도 앞 값으로 되돌리고
     배포 상태가 앞 판 100%인지 다시 봐요(아니면 크게 알림, RVQ-02 검토 SRV-04). 되돌리기는 코드만이에요. DB 판(스키마)은 앞으로만
     가고, 앞 판 코드가 새 스키마에서 도는지는 build가 앞 판 커밋의 코드로 직접 돌려 봐요(company-worker/tools/schema-compat.mjs).
 10. 둘(both)을 올리다 뒤의 것이 실패하면 앞에서 올린 것도 앞 판으로 되돌려요(반쪽 상태로 끝나지 않게, RVQ-02).

열쇠: 환경 변수 CLOUDFLARE_API_TOKEN(GitHub Secret, ship 일에만). 출력하지 않아요. 계정 번호는 CLOUDFLARE_ACCOUNT_ID가 없으면 토큰이
닿는 계정이 하나일 때 그 계정을 써요.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import tomllib
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# 배포할 코드는 workflow가 고른 브랜치를 따로 받은 곳(BAMNAT_SERVER_ROOT), 없으면 이 저장소의 server/.
SERVER = Path(os.environ.get("BAMNAT_SERVER_ROOT") or ROOT / "server").resolve()
# 2.0.0 [RVQ-01] 검토 SRV-02 (5): wrangler는 tools/deploy/package-lock.json으로 묶어(무결성 값까지) `npm ci --ignore-scripts`로 받아요.
# 받지 않은 곳(내 컴퓨터)에서만 npx로 같은 판을 불러요.
DEPLOY_TOOLS = ROOT / "tools/deploy"
_LOCAL_WRANGLER = DEPLOY_TOOLS / "node_modules/.bin/wrangler"
WRANGLER = [str(_LOCAL_WRANGLER)] if _LOCAL_WRANGLER.exists() else ["npx", "--yes", "wrangler@4.150.0"]
CF_API = "https://api.cloudflare.com/client/v4"

WORKERS = {
    "company": {
        "dir": "company-worker",
        "name": "bamnat-company",
        "fill": {"REPLACE_WITH_D1_DATABASE_ID": ("DB", ("id", "database_id"))},
        "health_env": "BAMNAT_API",
        "health_base": "https://api.bamnat.app",
    },
    "share": {
        "dir": "share-worker",
        "name": "bamnat-share",
        "fill": {"REPLACE_WITH_KV_NAMESPACE_ID": ("SHARES", ("namespace_id", "id"))},
        "health_env": "BAMNAT_SHARE",
        "health_base": "https://s.bamnat.app",
    },
}

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


class Stop(Exception):
    """손님을 받기 전에 멈춤(앞 판 그대로). 2.0.0 [RVQ-04] 검토 U21-09: 요약 세 줄(어느 단계·어느 시험·다음에 할 일)을 같이 들고 다녀요."""

    def __init__(self, message: str, stage: str = "", tests: list[str] | None = None, next_step: str = ""):
        super().__init__(message)
        self.stage = stage
        self.tests = tests or []
        self.next_step = next_step


# ---------- 순수 함수 (tools/test_deploy_worker.py가 시험해요) ----------

def find_bindings(view) -> list[dict]:
    """`wrangler versions view --json` 결과 어디에 있든 'bindings' 목록을 찾아요. 못 찾으면 []."""
    if isinstance(view, dict):
        b = view.get("bindings")
        if isinstance(b, list) and all(isinstance(x, dict) for x in b):
            return b
        for v in view.values():
            found = find_bindings(v)
            if found:
                return found
    elif isinstance(view, list):
        for v in view:
            found = find_bindings(v)
            if found:
                return found
    return []


def binding_names(bindings: list[dict]) -> dict[str, str]:
    return {str(b.get("name")): str(b.get("type", "")) for b in bindings if b.get("name")}


def missing_bindings(old: list[dict], new: list[dict]) -> dict[str, str]:
    """앞 판에 있었는데 새 판에 없는 바인딩 이름 → 종류."""
    o, n = binding_names(old), binding_names(new)
    return {k: t for k, t in o.items() if k not in n}


def plain_vars(bindings: list[dict], names) -> dict[str, str]:
    """빠진 것 중 일반 변수(plain_text)의 앞 판 값. 비밀값은 여기 오지 않아요(배포가 지우지 않아요)."""
    out = {}
    for b in bindings:
        if b.get("name") in names and b.get("type") == "plain_text" and isinstance(b.get("text"), str):
            out[b["name"]] = b["text"]
    return out


def fill_config(text: str, fill: dict, old: list[dict]) -> str:
    """wrangler.toml 자리표시자를 앞 판 바인딩의 번호로 채워요."""
    for placeholder, (name, keys) in fill.items():
        if placeholder not in text:
            continue
        b = next((x for x in old if x.get("name") == name), None)
        value = next((b.get(k) for k in keys if b and isinstance(b.get(k), str) and b.get(k)), None)
        if not value:
            raise Stop(f"앞 판에 바인딩 {name}의 번호가 없어 설정을 채울 수 없어요")
        text = text.replace(placeholder, value)
    if "REPLACE_WITH_" in text:
        raise Stop("wrangler.toml에 채우지 못한 자리표시자가 남았어요")
    return text


def current_versions(status) -> list[tuple[str, float]]:
    """`wrangler deployments status --json` → [(판 번호, 비율)]."""
    vs = status.get("versions") if isinstance(status, dict) else None
    out = []
    for v in vs or []:
        vid = v.get("version_id") or v.get("id")
        if vid:
            out.append((vid, float(v.get("percentage", 0))))
    return out


def version_tuple(v: str) -> tuple[int, ...]:
    return tuple(int(x) for x in re.findall(r"\d+", v or "")[:3]) or (0,)


def source_version(worker_dir: Path) -> str:
    src = worker_dir / "src/version.js"
    m = re.search(r'SERVER_VERSION\s*=\s*"([^"]+)"', src.read_text(encoding="utf-8")) if src.exists() else None
    if not m:
        raise Stop("src/version.js에서 판 이름을 못 읽었어요")
    return m.group(1)


def upload_version_id(ndjson: str, text: str) -> str | None:
    for line in ndjson.splitlines():
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("type") == "version-upload" and e.get("version_id"):
            return e["version_id"]
    m = re.search(r"Worker Version ID:\s*(" + UUID.pattern + ")", text)
    return m.group(1) if m else None


def crons_of(config_text: str) -> list[str] | None:
    t = tomllib.loads(config_text).get("triggers")
    return list(t.get("crons", [])) if isinstance(t, dict) else None


# 2.0.0 [RVQ-01] 검토 SRV-02: build가 넘긴 설정에서 받는 칸. 그 밖의 칸(특히 [build]처럼 명령을 돌리는 칸, [site]·[assets]처럼 폴더를
# 올리는 칸)이 있으면 ship이 멈춰요. 토큰이 있는 곳에서 고른 브랜치의 무엇도 '실행'되지 않게.
CONFIG_KEYS = {
    "name", "main", "compatibility_date", "compatibility_flags", "workers_dev", "keep_vars", "placement", "triggers",
    "version_metadata", "d1_databases", "kv_namespaces", "vars", "observability", "limits", "upload_source_maps",
}


def config_problems(text: str, worker: str) -> list[str]:
    """설정이 아는 칸만 쓰는지, 이름이 그 Worker인지. 문제 목록(없으면 [])."""
    try:
        cfg = tomllib.loads(text)
    except tomllib.TOMLDecodeError as e:
        return [f"wrangler.toml을 읽지 못했어요({e})"]
    out = [f"모르는 칸 [{k}]" for k in sorted(set(cfg) - CONFIG_KEYS)]
    if cfg.get("name") != WORKERS[worker]["name"]:
        out.append(f"이름이 {WORKERS[worker]['name']}이 아니에요({cfg.get('name')!r})")
    return out


TEST_FAIL = (re.compile(r"^\s*not ok \d+ - (.+?)\s*(?:#.*)?$"), re.compile(r"^\s*✖\s+(.+?)(?:\s+\([\d.]+m?s\))?\s*$"))


def failing_tests(output: str, limit: int = 8) -> list[str]:
    """2.0.0 [RVQ-04] 검토 U21-09: node --test 출력(tap·spec 둘 다)에서 실패한 시험 이름. 묶음 이름·요약 줄은 빼요."""
    names: list[str] = []
    for line in output.splitlines():
        for pat in TEST_FAIL:
            m = pat.match(line)
            if not m:
                continue
            name = m.group(1).strip()
            if name and not re.match(r"^(failing tests:?|tests? \d+|\d+ failing)$", name, re.I) and name not in names:
                names.append(name)
    # 묶음(파일)이 먼저 나오고 그 안의 시험이 뒤에 나와요. 파일 이름은 test/…mjs라 시험 이름이 있으면 파일 줄은 빼요.
    tests = [n for n in names if not n.endswith(".mjs")] or names
    return tests[:limit]


# 2.0.0 [RVQ-03] 검토 I21c-02: 카나리(새 판 일부에만). 판마다 센 요청은 회사 서버 /api/admin/health?canary= 가 줘요.
CANARY = {"percent": 10, "minutes": 10, "every": 60, "min_requests": 30, "min_errors": 3, "rate_gap": 0.02, "slow_ms": 300}


def canary_verdict(new: dict, base: dict) -> tuple[str, str]:
    """새 판·앞 판의 {n, err, ms}(같은 시간 동안) → ('ok'|'worse'|'few', 한 줄).

    'few': 새 판 요청이 적어 견줄 수 없음(health만 보고 넘어가요). 'worse': 5xx가 3건 넘고 앞 판보다 2배·2%p 넘게 많거나,
    평균 응답이 앞 판보다 2배 + 300ms 넘게 느림.
    """
    n1, e1, ms1 = int(new.get("n") or 0), int(new.get("err") or 0), int(new.get("ms") or 0)
    n0, e0, ms0 = int(base.get("n") or 0), int(base.get("err") or 0), int(base.get("ms") or 0)
    note = f"새 판 {n1}건 중 5xx {e1}, 앞 판 {n0}건 중 5xx {e0}"
    if n1 < CANARY["min_requests"]:
        return "few", note + f" (새 판 요청이 {CANARY['min_requests']}건보다 적어 견주지 않음)"
    r1, r0 = e1 / n1, (e0 / n0 if n0 else 0.0)
    if e1 >= CANARY["min_errors"] and r1 > max(2 * r0, r0 + CANARY["rate_gap"]):
        return "worse", note + f" — 새 판 5xx {r1:.1%} > 앞 판 {r0:.1%}"
    a1, a0 = ms1 / n1, (ms0 / n0 if n0 else 0.0)
    if n0 and a1 > 2 * a0 + CANARY["slow_ms"]:
        return "worse", note + f" — 새 판 평균 {a1:.0f}ms > 앞 판 {a0:.0f}ms"
    return "ok", note


def view_annotation(view, key: str) -> str | None:
    """`wrangler versions view --json`의 annotations 한 칸(예 workers/tag). 어디에 있든 찾아요."""
    if isinstance(view, dict):
        v = view.get(key)
        if isinstance(v, str) and v:
            return v
        for x in view.values():
            found = view_annotation(x, key)
            if found:
                return found
    elif isinstance(view, list):
        for x in view:
            found = view_annotation(x, key)
            if found:
                return found
    return None


SHA = re.compile(r"^[0-9a-f]{7,40}$")


def ref_verdict(ref: str, on_main: bool, open_heads: set[str], in_open_head: bool) -> str | None:
    """2.0.0 [RVQ-01] 검토 SRV-02 (4): 배포할 ref는 main, 열린 PR의 브랜치, 그 둘 중 하나에 든 커밋만. 멈출 까닭(없으면 None)."""
    ref = (ref or "").strip()
    if not ref:
        return "배포할 ref가 비었어요"
    if ref in ("main", "refs/heads/main"):
        return None
    if SHA.match(ref):
        return None if (on_main or in_open_head) else f"커밋 {ref[:8]}이 main에도 열린 PR의 브랜치에도 없어요"
    if ref.removeprefix("refs/heads/") in open_heads:
        return None
    return f"'{ref}'는 main도 열린 PR의 브랜치도 아니에요(PR을 먼저 열어요)"


# 2.0.0 [RVQ-04] 검토 U21-09: 실패하면 요약 맨 위에 세 줄(어느 단계·어느 시험·다음에 할 일). 긴 로그는 그 아래.
NEXT_STEP = {
    "ref": "main이나 열린 PR의 브랜치로 다시 시작해요",
    "시험": "실패한 시험을 그 브랜치에서 고쳐 push한 뒤 다시 돌려요(손님은 앞 판 그대로)",
    "번들": "`npm run bundle`을 그 브랜치에서 돌려 오류를 고쳐요",
    "스키마 호환": "앞 판 코드가 새 표에서 깨지는 곳을 고쳐요(칸을 지우거나 이름을 바꾸지 말고, 새 칸은 기본값을 줘요)",
    "설정": "wrangler.toml에서 모르는 칸을 빼거나 이 도구의 CONFIG_KEYS에 까닭과 함께 더해요(main PR)",
    "앞 판": "Cloudflare 대시보드에서 지금 판이 하나로 100%인지 보고 다시 돌려요",
    "판 이름": "src/version.js 판 이름을 올리거나, 정말 되돌리는 것이면 allow_older로 다시 돌려요",
    "올리기": "로그의 wrangler 오류를 보고 다시 돌려요(손님은 앞 판 그대로)",
    "바인딩": "빠진 바인딩을 wrangler.toml에 옮긴 뒤 다시 돌려요",
    "카나리": "새 판이 앞 판보다 오류가 많았어요. 관리자 앱 › 오류 묶음에서 새 판 오류를 보고 고쳐요",
    "확인": "앞 판으로 되돌렸어요. 관리자 앱 › 오류 묶음·로그를 보고 고쳐요",
    "되돌리기": "**사람이 바로**: Cloudflare 대시보드 › Deployments에서 앞 판을 100%로 두고 정기 작업을 맞춰요",
}


def summary_lines(stage: str, tests: list[str] | None = None, next_step: str = "") -> list[str]:
    t = ", ".join(f"`{x}`" for x in (tests or [])) or "해당 없음"
    return [f"- **어느 단계**: {stage or '알 수 없음'}", f"- **어느 시험**: {t}",
            f"- **다음에 할 일**: {next_step or NEXT_STEP.get(stage, '로그를 보고 다시 돌려요')}"]


# ---------- 바깥과 닿는 곳 ----------

def say(line: str) -> None:
    print(line, flush=True)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


def run(cmd: list[str], cwd: Path, extra_env: dict | None = None, check: bool = True, stage: str = "",
        clean_env: bool = False) -> subprocess.CompletedProcess:
    """[clean_env] 2.0.0 [RVQ-01]: build에서 고른 브랜치의 코드를 돌릴 때는 토큰·GitHub 열쇠 같은 환경 변수를 넘기지 않아요."""
    base = {k: v for k, v in os.environ.items() if k in SAFE_ENV or k.startswith(("LC_", "RUNNER_"))} if clean_env else os.environ
    env = {**base, "NO_COLOR": "1", "WRANGLER_SEND_METRICS": "false", **(extra_env or {})}
    p = subprocess.run(cmd, cwd=cwd, env=env, text=True, capture_output=True)
    if check and p.returncode != 0:
        tail = (p.stdout + p.stderr)[-1500:]
        raise Stop(f"`{' '.join(Path(cmd[0]).name if i == 0 else c for i, c in enumerate(cmd[:4]))} …` 실패 (코드 {p.returncode})\n```\n{tail}\n```",
                   stage=stage, tests=failing_tests(p.stdout + p.stderr) if stage == "시험" else None)
    return p


# build에서 고른 브랜치의 코드에 넘기는 환경 변수(그 밖은 모두 빼요).
SAFE_ENV = {"PATH", "HOME", "LANG", "TZ", "TMPDIR", "CI", "NODE_OPTIONS", "npm_config_cache", "NPM_CONFIG_CACHE"}


def wrangler_json(args: list[str], cwd: Path):
    p = run(WRANGLER + args + ["--json"], cwd)
    out = p.stdout
    start = min([i for i in (out.find("{"), out.find("[")) if i >= 0], default=-1)
    if start < 0:
        raise Stop(f"wrangler {' '.join(args[:2])}가 JSON을 주지 않았어요")
    return json.loads(out[start:])


def cf(method: str, path: str, body=None):
    req = urllib.request.Request(CF_API + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={"authorization": f"Bearer {os.environ['CLOUDFLARE_API_TOKEN']}",
                                          "content-type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def get(url: str, headers: dict | None = None) -> tuple[int, str]:
    try:
        h = {"cache-control": "no-cache", "user-agent": "bamnat-deploy", **(headers or {})}
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=15) as r:
            return r.status, r.read(4096).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception as e:  # noqa: BLE001 - 연결 실패도 health 실패로 셈
        return 0, type(e).__name__


def company_ok(code: int, h: dict, deep: bool, want_version: str | None = None) -> bool:
    """1.40 [RVD-08] 검토 SRV-03: 새 판은 deep=1로 DB까지(표 판 맞음·/api/config 읽기) 봐요. 되돌린 옛 판은 deep을 몰라 예전 기준으로.

    2.0.0 [PC-32]: health가 판 이름(version)을 알려 주면 바라는 판과 같아야 해요. 판 이름이 없는 옛 판(2.0.0 앞)은 건너뛰어요.
    """
    ok = code == 200 and h.get("database") is True and h.get("password") is True and h.get("salt") is True
    if want_version and h.get("version") and h.get("version") != want_version:
        return False
    return ok and (not deep or (h.get("schema") is True and h.get("config") is True))


def pin(worker: str, version_id: str | None) -> dict:
    """2.0.0 [RVQ-03] 카나리 동안 이 요청만 그 판으로(Cloudflare-Workers-Version-Overrides, 그 판이 지금 배포에 있을 때만 먹혀요).
    출처: developers.cloudflare.com/workers/configuration/versions-and-deployments/version-overrides/ (2026-11-12 확인)."""
    return {"Cloudflare-Workers-Version-Overrides": f'{WORKERS[worker]["name"]}="{version_id}"'} if version_id else {}


def healthy(worker: str, base: str, want_version: str, deep: bool = True, version_id: str | None = None) -> tuple[bool, str]:
    hdr = pin(worker, version_id)
    if worker == "company":
        code, body = get(f"{base}/api/admin/health?deep=1" if deep else f"{base}/api/admin/health", hdr)
        try:
            h = json.loads(body)
        except ValueError:
            h = {}
        ok = company_ok(code, h, deep, want_version)
        code2, _ = get(f"{base}/admin/", hdr)
        # 2.0.0 [RVQ-03] 모든 앱이 매일 부르는 길도 직접(deep 안의 config 읽기와 같은 길, 바깥에서 한 번 더).
        code3, _ = get(f"{base}/api/config", hdr) if deep else (200, "")
        return ok and code2 == 200 and code3 == 200, (f"health {code} 판 {h.get('version', '-')} db/password/salt{'/표 판/config' if deep else ''} "
                                                      f"{'모두 맞음' if ok else '빠짐'}, /admin/ {code2}{f', /api/config {code3}' if deep else ''}")
    code, body = get(f"{base}/api/health", hdr)
    try:
        h = json.loads(body)
    except ValueError:
        h = {}
    ok = code == 200 and h.get("ok") is True and h.get("version") == want_version
    return ok, f"health {code} 판 {h.get('version', '-')}"


SEMVER_HEAD = re.compile(r"^\s*v?(\d+\.\d+\.\d+)(?=\s|$)")


def message_version(view) -> str | None:
    """2.0.0 [RVD-19] `wrangler versions view --json`에서 올릴 때 적은 메시지(`workers/message`)의 맨 앞 판 이름.

    이 도구는 메시지를 '판 커밋 까닭'으로 올려요. 대시보드에서 올린 판처럼 메시지가 없거나 판 이름으로 시작하지 않으면 None.
    """
    def messages(x):
        if isinstance(x, dict):
            for k, v in x.items():
                if k in ("workers/message", "message") and isinstance(v, str):
                    yield v
                else:
                    yield from messages(v)
        elif isinstance(x, list):
            for v in x:
                yield from messages(v)
    for msg in messages(view):
        m = SEMVER_HEAD.match(msg)
        if m:
            return m.group(1)
    return None


def live_version(worker: str, base: str, view=None) -> str | None:
    """지금 도는 판 이름은 그 Worker 자신의 것으로 봐요(2.0.0 [RVD-19]).

    공유는 /api/health, 회사는 /api/admin/health의 판(2.0.0 [PC-32]부터), 없으면 앞 판을 올릴 때 적은 메시지 맨 앞 판이에요. 1.39까지는
    회사도 공유 서버 판과 비교해서, 회사만 먼저 2.0.0이 나간 뒤 main(1.39.0)을 올리면 '지금 1.39.0'으로 통과해 2.0.0 서버 고침이 조용히 빠졌어요.
    """
    code, body = get(f"{base}/api/health" if worker == "share" else f"{base}/api/admin/health")
    try:
        v = json.loads(body).get("version") if code == 200 else None
    except (ValueError, AttributeError):
        v = None
    if isinstance(v, str) and SEMVER_HEAD.match(v):
        return SEMVER_HEAD.match(v).group(1)
    return message_version(view) if view is not None else None


def version_gate(worker: str, new_ver: str, live: str | None, allow_older: bool) -> str | None:
    """2.0.0 [RVD-19] 판 이름 확인: 멈출 까닭(없으면 None). 같은 판은 다시 올려도 돼요."""
    if allow_older:
        return None
    if live is None:
        if worker == "company":
            return (f"회사 서버의 지금 판 이름을 알 수 없어요(앞 판 메시지가 판 이름으로 시작하지 않음). "
                    f"올릴 판 {new_ver}이 낮지 않은지 확인한 뒤 --allow-older로만")
        return None
    if version_tuple(new_ver) < version_tuple(live):
        return f"올릴 판 {new_ver}이 지금 도는 {live}보다 낮아요(--allow-older로만)"
    return None


# ---------- 0. ref 확인 (토큰 없음, GitHub 읽기만) ----------

def gh(path: str):
    req = urllib.request.Request(f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}{path}",
                                 headers={"authorization": f"Bearer {os.environ['GITHUB_TOKEN']}", "accept": "application/vnd.github+json",
                                          "user-agent": "bamnat-deploy"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def contains(head: str, sha: str) -> bool:
    """[head](브랜치·커밋)에 [sha]가 들었는지(compare: sha...head가 ahead·identical)."""
    try:
        return gh(f"/compare/{sha}...{urllib.parse.quote(head, safe='')}").get("status") in ("ahead", "identical")
    except urllib.error.HTTPError:
        return False


def check_ref(ref: str) -> str:
    """2.0.0 [RVQ-01] 검토 SRV-02 (4). 받은 ref의 커밋 번호를 돌려줘요(멈출 까닭이 있으면 Stop)."""
    repo = os.environ["GITHUB_REPOSITORY"]
    try:
        sha = gh(f"/commits/{urllib.parse.quote(ref.strip(), safe='')}")["sha"]
    except urllib.error.HTTPError as e:
        raise Stop(f"ref '{ref}'를 찾지 못했어요({e.code})", stage="ref") from None
    pulls = gh("/pulls?state=open&per_page=100")
    heads = {p["head"]["ref"]: p["head"]["sha"] for p in pulls if (p.get("head", {}).get("repo") or {}).get("full_name") == repo}
    is_sha = bool(SHA.match(ref.strip()))
    on_main = is_sha and contains("main", sha)
    in_head = is_sha and not on_main and any(contains(h, sha) for h in heads)
    why = ref_verdict(ref, on_main, set(heads), in_head)
    if why:
        raise Stop(why, stage="ref")
    say(f"- ref `{ref}` → `{sha[:8]}` ({'main' if ref in ('main', 'refs/heads/main') or on_main else '열린 PR의 브랜치'})")
    return sha


# ---------- 계정·앞 판 (토큰 있음, main의 이 도구만) ----------

def account() -> None:
    if not os.environ.get("CLOUDFLARE_API_TOKEN"):
        raise Stop("CLOUDFLARE_API_TOKEN이 없어요(사용자 할 일: 저장소 Settings › Secrets › Actions)", stage="앞 판")
    if not os.environ.get("CLOUDFLARE_ACCOUNT_ID"):
        accounts = cf("GET", "/accounts?per_page=5").get("result") or []
        if len(accounts) != 1:
            raise Stop(f"토큰이 닿는 계정이 {len(accounts)}개예요. Variables에 CLOUDFLARE_ACCOUNT_ID를 넣어요", stage="앞 판")
        os.environ["CLOUDFLARE_ACCOUNT_ID"] = accounts[0]["id"]


def live(worker: str, cwd: Path) -> tuple[str, dict]:
    name = ["--name", WORKERS[worker]["name"]]
    now = current_versions(wrangler_json(["deployments", "status"] + name, cwd))
    if len(now) != 1 or now[0][1] < 100:
        raise Stop(f"지금 판이 나뉘어 돌고 있어요({now}). 사람이 먼저 한 판으로 맞춰야 해요", stage="앞 판")
    return now[0][0], wrangler_json(["versions", "view", now[0][0]] + name, cwd)


def probe(workers: list[str]) -> None:
    """2.0.0 [RVQ-02] 검토 SRV-04 (마): 지금 도는 판을 올린 커밋(이 도구가 붙인 tag = 커밋 앞 12자)을 build에 넘겨, 앞 판 코드가 새 표에서
    도는지 build가 봐요. 대시보드에서 올린 판처럼 tag가 없으면 빈 값(build가 '건너뜀'으로 적어요)."""
    account()
    with tempfile.TemporaryDirectory() as tmp:
        for w in workers:
            vid, view = live(w, Path(tmp))
            tag = view_annotation(view, "workers/tag") or ""
            full = ""
            if SHA.match(tag) and os.environ.get("GITHUB_TOKEN"):
                try:  # tag는 커밋 앞 12자라 checkout에 넘길 온 번호로(저장소 안의 커밋만)
                    full = gh(f"/commits/{tag}")["sha"]
                except (urllib.error.HTTPError, KeyError):
                    full = ""
            output(f"{w}_prev", full)
            say(f"- {WORKERS[w]['name']} 지금 판 `{vid[:8]}` 커밋 {full[:8] or tag[:8] or '모름'}")


def output(key: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(f"{key}={value}\n")


# ---------- 1. build (토큰 없음, 고른 브랜치의 코드) ----------

def build(workers: list[str], out: Path, prev: Path | None) -> None:
    """시험·번들·판 이름을 [out]/<worker>/에(worker.js, wrangler.toml, version.txt, notes.txt). 토큰·열쇠 없는 환경에서만 불러요."""
    if os.environ.get("CLOUDFLARE_API_TOKEN"):
        raise Stop("build에 Cloudflare 토큰이 보여요. 토큰은 ship 일에만 둬요(RVQ-01)", stage="시험")
    for w in workers:
        wd = SERVER / WORKERS[w]["dir"]
        dest = out / w
        dest.mkdir(parents=True, exist_ok=True)
        if (wd / "package-lock.json").exists():
            run(["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], wd, stage="시험", clean_env=True)
        run(["npm", "test"], wd, stage="시험", clean_env=True)
        run(["npm", "run", "bundle"], wd, stage="번들", clean_env=True)
        bundle = wd / "dist/worker.js"
        if not bundle.exists():
            raise Stop("dist/worker.js가 없어요", stage="번들")
        notes = [f"서버 시험 통과, 번들 {bundle.stat().st_size // 1024}KB"]
        if w == "company":
            old = prev / WORKERS[w]["dir"] if prev else None
            if old and (old / "src/index.js").exists():
                p = run(["node", "--disable-warning=ExperimentalWarning", "tools/schema-compat.mjs", str(old)], wd, check=False, clean_env=True)
                if p.returncode != 0:
                    raise Stop(f"앞 판 코드가 새 표에서 깨져요\n```\n{(p.stdout + p.stderr)[-1500:]}\n```", stage="스키마 호환")
                notes.append(f"스키마 호환: {p.stdout.strip().splitlines()[-1] if p.stdout.strip() else '맞음'}")
            else:
                notes.append("스키마 호환: 앞 판 커밋을 몰라 건너뜀(대시보드에서 올린 판)")
        shutil.copyfile(bundle, dest / "worker.js")
        shutil.copyfile(wd / "wrangler.toml", dest / "wrangler.toml")
        (dest / "version.txt").write_text(source_version(wd), encoding="utf-8")
        (dest / "notes.txt").write_text("\n".join(notes), encoding="utf-8")
        for n in notes:
            say(f"- {WORKERS[w]['name']}: {n}")


# ---------- 2. ship (토큰 있음, main의 이 도구 + build가 만든 파일만) ----------

class Ready:
    """올리고 대조까지 끝난 판(아직 손님 안 받음)."""

    def __init__(self, worker: str, old_id: str, new_id: str, new_ver: str, live_ver: str | None, config_text: str):
        self.worker, self.old_id, self.new_id, self.new_ver, self.live_ver, self.config_text = worker, old_id, new_id, new_ver, live_ver, config_text
        self.crons_before: list[str] | None = None
        self.deployed = False


def stage_upload(worker: str, src: Path, work: Path, sha: str, reason: str, allow_older: bool) -> Ready:
    w = WORKERS[worker]
    base = (os.environ.get(w["health_env"]) or w["health_base"]).rstrip("/")
    name = ["--name", w["name"]]
    for f in ("worker.js", "wrangler.toml", "version.txt"):
        if not (src / f).exists():
            raise Stop(f"build가 만든 {worker}/{f}가 없어요", stage="번들")

    # 1. 앞 판
    old_id, old_view = live(worker, work)
    old = find_bindings(old_view)
    if not old:
        raise Stop("앞 판의 바인딩을 읽지 못했어요(대조할 수 없어 멈춰요)", stage="앞 판")
    say(f"- 앞 판 `{old_id[:8]}` 바인딩 {len(old)}개: {', '.join(sorted(binding_names(old)))}")

    # 2·3. 설정 칸 확인과 배포용 설정
    raw = (src / "wrangler.toml").read_text(encoding="utf-8")
    problems = config_problems(raw, worker)
    if problems:
        raise Stop("build가 넘긴 wrangler.toml에 받지 않는 칸이 있어요: " + ", ".join(problems), stage="설정")
    config_text = fill_config(raw, w["fill"], old)
    (work / "wrangler.deploy.toml").write_text(config_text, encoding="utf-8")
    shutil.copyfile(src / "worker.js", work / "worker.js")

    # 4. 판 이름
    new_ver = (src / "version.txt").read_text(encoding="utf-8").strip()
    if not SEMVER_HEAD.match(new_ver):
        raise Stop(f"판 이름 '{new_ver}'을 읽지 못했어요", stage="판 이름")
    live_ver = live_version(worker, base, old_view)
    why = version_gate(worker, new_ver, live_ver, allow_older)
    if why:
        raise Stop(why, stage="판 이름")
    say(f"- 판 이름 {new_ver} (지금 {live_ver or '알 수 없음'}{', --allow-older' if allow_older else ''})")

    # 5·6. 올리기와 바인딩 대조
    def upload(extra_vars: dict[str, str]) -> tuple[str, list[dict]]:
        out = work / ".deploy-out.ndjson"
        out.unlink(missing_ok=True)
        cmd = WRANGLER + ["versions", "upload", "worker.js", "--config", "wrangler.deploy.toml", "--no-bundle", "--keep-vars",
                          "--tag", sha[:12], "--message", f"{new_ver} {sha[:8]} {reason}"[:100]]
        for k, v in extra_vars.items():
            cmd += ["--var", f"{k}:{v}"]
        p = run(cmd, work, {"WRANGLER_OUTPUT_FILE_PATH": str(out)}, stage="올리기")
        vid = upload_version_id(out.read_text(encoding="utf-8") if out.exists() else "", p.stdout + p.stderr)
        if not vid:
            raise Stop("올린 판 번호를 읽지 못했어요", stage="올리기")
        return vid, find_bindings(wrangler_json(["versions", "view", vid] + name, work))

    new_id, new = upload({})
    lost = missing_bindings(old, new)
    if lost and set(lost.values()) <= {"plain_text"}:
        say(f"- 대시보드에만 있던 일반 변수 {len(lost)}개를 앞 판 값으로 다시 올려요: {', '.join(sorted(lost))}")
        new_id, new = upload(plain_vars(old, lost))
        lost = missing_bindings(old, new)
    if lost:
        raise Stop(f"새 판에 앞 판 바인딩이 빠져요: {lost}. wrangler.toml에 먼저 옮겨야 해요(올린 판 `{new_id[:8]}`은 쓰이지 않아요)", stage="바인딩")
    say(f"- 새 판 `{new_id[:8]}` 올림, 바인딩 {len(new)}개 모두 이어짐")
    return Ready(worker, old_id, new_id, new_ver, live_ver, config_text)


def schedules(worker: str, crons: list[str] | None = None) -> list[str]:
    """정기 작업 읽기(crons가 None) 또는 바꾸기. 돌려주는 값은 그 뒤의 목록."""
    acc = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    path = f"/accounts/{acc}/workers/scripts/{WORKERS[worker]['name']}/schedules"
    if crons is not None:
        cf("PUT", path, [{"cron": c} for c in crons])
    return sorted(s.get("cron") for s in (cf("GET", path).get("result") or {}).get("schedules", []))


def canary_stats(worker: str, base: str, new_id: str, old_id: str) -> tuple[dict, dict] | None:
    """새 판에 물어요(앞 판 코드는 이 칸을 모를 수 있어요). 숫자는 D1에 있어 두 판 것이 다 보여요. 앞 판이 판마다 세지 않는 옛 코드면
    앞 판 쪽은 비어 있고, canary_verdict가 절대 기준(5xx 3건·2% 넘음)으로만 봐요."""
    code, body = get(f"{base}/api/admin/health?canary={new_id[:8]}&base={old_id[:8]}&minutes={CANARY['minutes']}", pin(worker, new_id))
    try:
        c = json.loads(body).get("canary") if code == 200 else None
    except (ValueError, AttributeError):
        c = None
    return (c.get("new") or {}, c.get("base") or {}) if isinstance(c, dict) else None


def canary(r: Ready, work: Path, sha: str) -> None:
    """2.0.0 [RVQ-03] 검토 I21c-02: 회사 서버는 새 판 10%·앞 판 90%로 10분. 그동안 새 판에 직접(판 고정 머리글) health·config를 보내고,
    서버가 판마다 센 실제 요청의 5xx·응답 시간을 1분마다 견줘요. 나쁘면 앞 판 100%로 되돌리고 멈춰요(아직 정기 작업은 그대로)."""
    w = WORKERS[r.worker]
    base = (os.environ.get(w["health_env"]) or w["health_base"]).rstrip("/")
    name = ["--name", w["name"]]
    pct = CANARY["percent"]
    run(WRANGLER + ["versions", "deploy", f"{r.new_id}@{pct}%", f"{r.old_id}@{100 - pct}%", "--yes",
                    "--message", f"카나리 {r.new_ver} {sha[:8]}"[:100]] + name, work, stage="카나리")
    r.deployed = True
    say(f"- 카나리: 새 판 `{r.new_id[:8]}` {pct}% · 앞 판 {100 - pct}%, {CANARY['minutes']}분")
    verdict, note, smoke_bad = "few", "판마다 센 요청을 아직 못 읽었어요", []
    rounds = max(1, CANARY["minutes"] * 60 // CANARY["every"])
    for i in range(rounds):
        time.sleep(CANARY["every"])
        ok, n = healthy(r.worker, base, r.new_ver, version_id=r.new_id)
        if not ok:
            smoke_bad.append(f"{i + 1}분: {n}")
        stats = canary_stats(r.worker, base, r.new_id, r.old_id)
        if stats:
            verdict, note = canary_verdict(*stats)
        if verdict == "worse" or len(smoke_bad) >= 2:
            break
    if verdict == "worse" or len(smoke_bad) >= 2:
        undo(r, work, "카나리")
        raise Stop(f"카나리에서 새 판이 나빴어요: {note}{' / 새 판 직접 확인 실패: ' + ' / '.join(smoke_bad) if smoke_bad else ''}", stage="카나리")
    say(f"- 카나리 끝: {note}{f' (새 판 직접 확인 실패 {len(smoke_bad)}번)' if smoke_bad else ''} → 100%")


def go_live(r: Ready, work: Path, sha: str, reason: str) -> None:
    """7·8·9. (회사는 카나리 뒤) 100%, 정기 작업, health 5번. 실패면 undo(코드·정기 작업 함께)."""
    w = WORKERS[r.worker]
    base = (os.environ.get(w["health_env"]) or w["health_base"]).rstrip("/")
    name = ["--name", w["name"]]
    run(WRANGLER + ["versions", "deploy", f"{r.new_id}@100%", "--yes", "--message", f"{r.new_ver} {sha[:8]} {reason}"[:100]] + name, work, stage="확인")
    r.deployed = True
    problems = []
    try:
        want = crons_of(r.config_text)
        if want is not None:
            have = schedules(r.worker)
            if have != sorted(want):
                r.crons_before = have
                now = schedules(r.worker, want)
                say(f"- 정기 작업 맞춤: {have} → {now} (되돌릴 때 앞 값으로)")
        for i in range(5):
            time.sleep(10)
            ok, note = healthy(r.worker, base, r.new_ver)
            if not ok:
                problems.append(f"{i + 1}번째: {note}")
        serving = current_versions(wrangler_json(["deployments", "status"] + name, work))
        if serving != [(r.new_id, 100.0)]:
            problems.append(f"배포 상태가 새 판 100%가 아니에요: {serving}")
    except Exception as e:  # noqa: BLE001 - 배포 뒤 무엇이 어긋나도 되돌려요
        problems.append(f"확인 중 오류: {e}")
    if problems:
        undo(r, work, "확인")
        raise Stop("배포 뒤 확인 실패, 앞 판으로 되돌렸어요: " + " / ".join(problems), stage="확인")
    say(f"- **배포 끝**: {w['name']} {r.new_ver} `{r.new_id[:8]}`, health 5번 모두 맞음. 되돌릴 판 `{r.old_id[:8]}`")


def undo(r: Ready, work: Path, why: str) -> None:
    """2.0.0 [RVQ-02] 검토 SRV-04 (가)(나): 앞 판 100% + 정기 작업 앞 값, 그리고 배포 상태가 정말 앞 판 100%인지 다시 봐요.
    되돌리기마저 어긋나면 Stop(stage='되돌리기')로 크게 알려요."""
    if not r.deployed:
        return
    w = WORKERS[r.worker]
    base = (os.environ.get(w["health_env"]) or w["health_base"]).rstrip("/")
    name = ["--name", w["name"]]
    bad = []
    p = run(WRANGLER + ["versions", "deploy", f"{r.old_id}@100%", "--yes", "--message", f"자동 되돌리기({why})"] + name, work, check=False)
    if p.returncode != 0:
        bad.append(f"되돌리기 명령 실패(코드 {p.returncode})")
    if r.crons_before is not None:
        try:
            now = schedules(r.worker, r.crons_before)
            if now != sorted(r.crons_before):
                bad.append(f"정기 작업이 앞 값으로 안 돌아왔어요: {now}")
        except Exception as e:  # noqa: BLE001
            bad.append(f"정기 작업 되돌리기 오류: {type(e).__name__}")
    serving = []
    for _ in range(3):
        time.sleep(10)
        try:
            serving = current_versions(wrangler_json(["deployments", "status"] + name, work))
        except Stop:
            serving = []
        if serving == [(r.old_id, 100.0)]:
            break
    if serving != [(r.old_id, 100.0)]:
        bad.append(f"배포 상태가 앞 판 100%가 아니에요: {serving}")
    ok, note = healthy(r.worker, base, r.live_ver or r.new_ver, deep=False)
    if bad:
        raise Stop(f"{w['name']} 되돌리기가 어긋났어요: " + " / ".join(bad) + f" (health {note})", stage="되돌리기")
    r.deployed = False
    say(f"- {w['name']} **앞 판 `{r.old_id[:8]}`으로 되돌림**(배포 상태 앞 판 100% 확인{', 정기 작업 앞 값' if r.crons_before is not None else ''}, {note})")


def ship(workers: list[str], src: Path, mode: str, sha: str, reason: str, allow_older: bool) -> None:
    """올리기·대조는 모든 Worker 먼저(손님 안 받음), 그다음 차례로 배포. 뒤의 것이 실패하면 앞에서 배포한 것도 되돌려요(RVQ-02 (라))."""
    account()
    with tempfile.TemporaryDirectory(prefix="bamnat-ship-") as tmp:
        ready = []
        for w in workers:
            work = Path(tmp) / w
            work.mkdir()
            say(f"#### {WORKERS[w]['name']}")
            ready.append((stage_upload(w, src / w, work, sha, reason, allow_older), work))
        if mode == "check":
            say("- check 모드: 여기서 멈춰요(손님은 앞 판 그대로)")
            return
        done: list[tuple[Ready, Path]] = []
        try:
            for r, work in ready:
                if r.worker == "company":
                    canary(r, work, sha)
                go_live(r, work, sha, reason)
                done.append((r, work))
        except Stop:
            for r, work in reversed(done):
                say(f"- 뒤의 서버가 실패해 {WORKERS[r.worker]['name']}도 앞 판으로 되돌려요(반쪽 상태로 끝내지 않음)")
                r.deployed = True
                undo(r, work, "다른 서버 실패")
            raise


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check-ref")
    ap.add_argument("--phase", choices=["probe", "build", "ship"])
    ap.add_argument("--worker", choices=sorted(WORKERS) + ["both"], default="both")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--from", dest="src", type=Path)
    ap.add_argument("--prev", type=Path, help="앞 판 커밋의 server/ 폴더(스키마 호환 확인)")
    ap.add_argument("--mode", choices=["check", "deploy"], default="check")
    ap.add_argument("--sha", default=os.environ.get("GITHUB_SHA", "local"))
    ap.add_argument("--reason", default="")
    ap.add_argument("--allow-older", action="store_true")
    a = ap.parse_args()
    workers = ["company", "share"] if a.worker == "both" else [a.worker]
    try:
        if a.check_ref is not None:
            output("sha", check_ref(a.check_ref))
        elif a.phase == "probe":
            probe(workers)
        elif a.phase == "build":
            if not a.out:
                ap.error("--out")
            build(workers, a.out, a.prev if a.prev and a.prev.exists() else None)
        elif a.phase == "ship":
            if not a.src:
                ap.error("--from")
            say(f"### 서버 {a.mode} {a.sha[:8]}")
            ship(workers, a.src, a.mode, a.sha, re.sub(r"\s+", " ", a.reason).strip(), a.allow_older)
        else:
            ap.error("--check-ref 또는 --phase")
    except Stop as e:
        for line in summary_lines(e.stage, e.tests, e.next_step):
            say(line)
        say(f"- **멈춤**: {e}")
        raise SystemExit(2 if e.stage == "되돌리기" else 1)


if __name__ == "__main__":
    main()
