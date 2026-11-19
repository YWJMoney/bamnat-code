#!/usr/bin/env python3
"""1.39 [DEPLOY-01] 서버 자동 배포: 크롬·대시보드 붙여 넣기 없이 GitHub Actions에서 두 Worker를 올려요.

사용자 결정(2026-11-08): 'Claude가 끝까지'. 승인 단계 없이 Claude가 workflow를 시작하고, 아래 안전장치가 막아요.

2.0.0 [RVQ-01] 검토 SRV-02: 고른 브랜치의 코드(npm ci·npm test·번들)는 Cloudflare 토큰이 **없는** 일(build)에서만 돌고, 토큰이 있는
일(ship)은 main의 이 도구와 번들 파일·설정만 받아 wrangler를 깨끗한 폴더에서 불러요. 고른 브랜치의 코드는 토큰과 한 기계에 있지 않아요.

  python3 tools/deploy_worker.py --check-ref <ref>                       # 0. main·열린 PR의 브랜치·그 안의 커밋만(sha= 출력)
  python3 tools/deploy_worker.py --phase probe --worker both              # 0. 토큰(main 도구만): 지금 판을 올린 커밋(<worker>_prev=)
  python3 tools/deploy_worker.py --phase build --worker both --out <폴더> [--prev <앞 판 server/>]  # 1. 토큰 없이: 시험·번들·스키마 호환·판 이름
  python3 tools/deploy_worker.py --phase ship --worker both --from <폴더> --mode check|deploy --sha <커밋> --reason <글> [--allow-older]
  python3 tools/deploy_worker.py --phase ship --worker share --from <폴더> --mode migrate-check|migrate ...   # 2.0.0 [RVD-33] DO 첫 배포(한 서버씩)

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

2.0.0 [RVR-17] (검토 TEST-14·I60a-03) 날짜에 묶여 낡은 시험 하나가 급한 보안 고침을 막지 않게: build에 --skip-test '시험 이름|까닭|기한'
(workflow 입력 skip_tests, ';'로 여러 개). 까닭이 없거나 기한(YYYY-MM-DD)이 지났거나 14일보다 멀면 멈춰요. 넘긴 시험은 build 요약과
notes.txt(배포 기록)에 남아요. build·ship 흐름(나뉜 판·check·변수 다시 올리기·바인딩 빠짐·판 낮음·카나리·배포·되돌리기)은
tools/test_deploy_flow.py가 가짜 wrangler로 끝까지 돌려 봐요.
  쓰는 규칙(총괄 2026-11-12): ① 날짜에 묶여 낡은 시험에만(어제 main에서도 깨지는 시험, 아래 explain이 알려 줘요) ② 그 배포에 대해
  사용자가 고르기 카드로 허락했을 때만 ③ 넘긴 시험·까닭·기한을 배포 결과 한 줄(사용자에게 알리는 판·배포 번호·되돌릴 판)에 함께.
  갈래 스레드가 스스로 쓰지 않아요. 넘긴 시험은 기한 안에 고치는 PR을 따로 열어요. 자세히는 docs/ops-deploy.md.

2.0.0 [RVR-25] (검토 U60-02) 시험에서 멈추면 build가 실패한 시험 이름·줄을 fail/<서버>-fail.json으로 넘기고, workflow의 explain 일이
어제 main의 날짜 시험(+0일, date-watch.yml) 결과와 견줘 '어제 main에서도 깨짐 → 이 브랜치 탓이 아님 / 어제 main에서는 통과 → 이 브랜치의
바뀜을 먼저'를 요약에 적어요(tools/date_watch.py explain).

2.0.0 [RVD-33] Durable Object 첫 배포 (--mode migrate-check|migrate, 한 Worker씩, ship 일에서만)
  Durable Object 클래스를 새로 만드는(지우는·옮기는) migration이 든 판은 `versions upload`로 못 올려요(wrangler 4.150.0:
  "pending Durable Object migration … must be applied with wrangler deploy"). Cloudflare 문서: 그런 판은 `wrangler deploy`로 올리고
  (…/versions-and-deployments/gradual-deployments/#durable-objects), 올린 뒤에는 그 앞 판으로 되돌리기를 막아요
  (…/versions-and-deployments/rollbacks/, 주소 앞은 https://developers.cloudflare.com/workers/configuration).
  그래서 보통 check·deploy는 설정에 실서버보다 새 migration이 있으면 올리기 전에 멈추고, 아래 순서로만 올려요. 카나리(10%)도 없어요:
  migration이 든 판은 한 번에 100%로만 올라가요. build(시험·번들·스키마 호환)는 보통 때와 같아요.
  1~4. 보통 ship과 같아요(앞 판·설정 칸·배포용 설정·판 이름).
  5. 실서버 migration 태그(wrangler가 보는 /workers/services/<이름>)와 설정을 맞춰 적용할 migration을 고르고, 멈출 까닭을 봐요:
     적용할 것이 없음, 실서버 태그가 설정에 없음, 지우기·이름 바꾸기·옮기기(데이터가 사라지거나 옮겨짐, 사람이 정해요),
     바인딩 클래스를 만드는 migration 없음, 앞 판 바인딩이 설정에 없어 사라짐, 설정 이름이 비밀값을 덮어씀.
  6. 지금 붙은 연결(사용자 도메인·route·workers.dev·미리보기 URL)을 읽어 남기고, workers.dev·미리보기 URL은 그 값으로 배포용 설정에
     적어요(wrangler deploy가 매번 보내는 값). route·사용자 도메인은 설정에 없으면 wrangler deploy가 건드리지 않아요
     (4.150.0 triggersDeploy: 설정에 있을 때만 PUT). `wrangler deploy --dry-run`. migrate-check는 여기까지(손님은 앞 판 그대로).
  7. `wrangler deploy --keep-vars`(앞 판 일반 변수는 --var로 값까지 넘겨요). 새 판이 100%가 되고 이 판이 새 되돌리기 바닥이에요.
  8. 배포 상태 새 판 100%, 바인딩 대조(앞 판 것 모두 + DO 바인딩), 실서버 migration 태그가 설정 마지막 태그, 붙은 연결이 6과 같음,
     정기 작업, health 5번.
     실패해도 앞 판으로 되돌리지 않아요(Cloudflare가 막아요). 크게 알리고 실패로 끝나요: 고친 판을 보통 ship으로 올려요.
  토큰 권한은 그대로예요. Durable Object 전용 권한은 없고 'Workers Scripts 편집'에 들어 있어요
  (https://developers.cloudflare.com/api/resources/durable_objects/subresources/namespaces/methods/list/).
  공유 서버 묶음은 이 첫 배포(바닥 판) 뒤에도 같은 클래스 이름·바인딩·migration 태그를 설정에 가져가야 해요. 없으면 보통 ship이
  '새 판에 앞 판 바인딩이 빠져요'로 멈춰요.

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
# 2.0.0 [RVD-33] durable_objects·migrations: 공유 서버의 앱 없는 팀원 방(TeamRound, Q1 4h). 명령을 돌리거나 폴더를 올리는 칸이
# 아니라 바인딩과 클래스 생애 선언이에요. 클래스를 새로 만드는 첫 판은 versions upload가 못 올려 mode migrate로 따로 올리고
# (그 판이 되돌리기 바닥), 그 뒤 판은 같은 설정으로 이 도구가 보통대로 올려요.
CONFIG_KEYS = {
    "name", "main", "compatibility_date", "compatibility_flags", "workers_dev", "keep_vars", "placement", "triggers",
    "version_metadata", "d1_databases", "kv_namespaces", "vars", "observability", "limits", "upload_source_maps",
    "durable_objects", "migrations",
}

DO_PENDING_NEXT = ("아직 적용되지 않은 Durable Object migration이 있어요. versions upload로는 못 올려요: 배포 도구 mode "
                   "migrate-check → migrate로 이 Worker만 따로 올린 뒤(그 판이 새 되돌리기 바닥) 다시 돌려요(손님은 앞 판 그대로)")


def upload_hint(output: str) -> str | None:
    """2.0.0 [RVD-33] versions upload 실패 글이 적용 안 된 DO migration 때문이면 다음에 할 일."""
    return DO_PENDING_NEXT if "pending Durable Object migration" in output else None


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


# ---------- 2.0.0 [RVD-33] Durable Object 첫 배포 (순수 함수) ----------

# --keep-vars로 올리면 설정에 없어도 남는 바인딩 종류(비밀값은 원래 남고, 일반 변수는 --keep-vars로 남아요).
KEPT_TYPES = {"secret_text", "secret_key", "plain_text", "json"}
# 데이터를 지우거나 다른 클래스·Worker로 옮기는 migration 단계. 이 도구는 하지 않아요(사람이 정해요).
DESTRUCTIVE_STEPS = ("deleted_classes", "renamed_classes", "transferred_classes")


def do_lifecycle(config_text: str) -> dict:
    """설정의 Durable Object 바인딩(이름 → 클래스, 이 Worker의 것만)과 migration 목록·태그."""
    c = tomllib.loads(config_text)
    binds = {}
    for b in (c.get("durable_objects") or {}).get("bindings") or []:
        if isinstance(b, dict) and b.get("name") and not b.get("script_name"):
            binds[str(b["name"])] = str(b.get("class_name", ""))
    migs = [m for m in c.get("migrations") or [] if isinstance(m, dict)]
    return {"bindings": binds, "migrations": migs, "tags": [str(m.get("tag", "")) for m in migs]}


def script_migration_tag(service) -> str | None:
    """`GET /accounts/<계정>/workers/services/<이름>`의 지금 migration 태그(wrangler 4.150.0이 보는 곳과 같아요). 없으면 None.

    모양이 다르면 ValueError: 태그가 없는 것과 못 읽은 것을 섞지 않아요.
    """
    try:
        script = service["result"]["default_environment"]["script"]
    except (KeyError, TypeError) as e:
        raise ValueError("services 응답에 default_environment.script가 없어요") from e
    if not isinstance(script, dict):
        raise ValueError("services 응답의 script가 객체가 아니에요")
    return script.get("migration_tag") or None


def pending_migrations(lc: dict, live_tag: str | None) -> list[dict] | None:
    """실서버에 아직 적용되지 않은 migration(wrangler가 올릴 것과 같은 규칙). 실서버 태그가 설정에 없으면 None."""
    if not live_tag:
        return list(lc["migrations"])
    if live_tag not in lc["tags"]:
        return None
    return lc["migrations"][lc["tags"].index(live_tag) + 1:]


def config_binding_names(config_text: str) -> set[str]:
    """설정이 직접 만드는 바인딩 이름."""
    c = tomllib.loads(config_text)
    names = set((c.get("vars") or {}).keys())
    for key in ("kv_namespaces", "d1_databases", "r2_buckets", "services", "analytics_engine_datasets", "hyperdrive",
                "vectorize", "mtls_certificates", "dispatch_namespaces", "workflows", "send_email"):
        for b in c.get(key) or []:
            if isinstance(b, dict):
                names.add(b.get("binding") or b.get("name"))
    for key in ("version_metadata", "ai", "browser", "images", "assets"):
        b = c.get(key)
        if isinstance(b, dict):
            names.add(b.get("binding"))
    for b in (c.get("durable_objects") or {}).get("bindings") or []:
        if isinstance(b, dict):
            names.add(b.get("name"))
    for b in (c.get("queues") or {}).get("producers") or []:
        if isinstance(b, dict):
            names.add(b.get("binding"))
    names.discard(None)
    return {str(n) for n in names}


def migrate_problems(config_text: str, old: list[dict], pending: list[dict] | None) -> list[str]:
    """DO 첫 배포(wrangler deploy)를 하기 전에 멈출 까닭. 올린 뒤 앞 판으로 되돌릴 수 없어서 미리 다 봐요."""
    lc = do_lifecycle(config_text)
    out = []
    if not lc["migrations"]:
        out.append("설정에 [[migrations]]가 없어요(보통 deploy로 올려요)")
    elif pending is None:
        out.append("실서버의 migration 태그가 설정에 없어요(무엇이 다시 적용될지 몰라요)")
    elif not pending:
        out.append("실서버에 설정의 migration이 이미 모두 적용돼 있어요(보통 deploy로 올려요)")
    for m in pending or []:
        for k in DESTRUCTIVE_STEPS:
            if m.get(k):
                out.append(f"migration {m.get('tag')}의 {k}는 데이터를 지우거나 옮겨요(이 도구로 하지 않아요)")
    made: set[str] = set()
    for m in lc["migrations"]:
        made |= set(m.get("new_classes") or []) | set(m.get("new_sqlite_classes") or [])
        made -= set(m.get("deleted_classes") or [])
    for name, cls in sorted(lc["bindings"].items()):
        if cls not in made:
            out.append(f"바인딩 {name}의 클래스 {cls or '(없음)'}를 만드는 migration이 없어요")
    have = config_binding_names(config_text)
    for b in old:
        n, t = b.get("name"), str(b.get("type", ""))
        if n and n not in have and t not in KEPT_TYPES:
            out.append(f"앞 판 바인딩 {n}({t})이 설정에 없어 사라져요")
    clash = sorted(have & {str(b.get("name")) for b in old if b.get("type") in ("secret_text", "secret_key")})
    if clash:
        out.append(f"설정의 이름이 비밀값을 덮어써요: {', '.join(clash)}")
    return out


def pin_subdomain(config_text: str, workers_dev: bool, previews: bool) -> str:
    """배포용 설정의 workers.dev·미리보기 URL을 지금 실서버 값으로 못 박아요.

    `wrangler deploy`는 versions upload와 달리 매번 `POST …/subdomain {enabled: workers_dev, previews_enabled: preview_urls}`를
    보내요(wrangler 4.150.0 subdomainDeploy). preview_urls가 설정에 없으면 그 칸을 빼고 보내는데, 그때 Cloudflare가 무엇으로
    두는지 API 문서에 없어요. 되돌릴 수 없는 배포라 지금 상태를 그대로 적어 보내요.
    """
    out, top = [], True
    for line in config_text.split("\n"):
        s = line.strip()
        if s.startswith("["):
            top = False
        if top and re.match(r"(workers_dev|preview_urls)\s*=", s):
            continue
        out.append(line)
    text = (f"# 2.0.0 [RVD-33] 배포 도구가 지금 실서버 값으로 적음\nworkers_dev = {str(bool(workers_dev)).lower()}\n"
            f"preview_urls = {str(bool(previews)).lower()}\n" + "\n".join(out))
    c = tomllib.loads(text)
    if c.get("workers_dev") is not bool(workers_dev) or c.get("preview_urls") is not bool(previews):
        raise Stop("workers.dev·미리보기 URL 값을 배포용 설정에 적지 못했어요")
    return text


def attachment_state(subdomain, domains, routes, name: str) -> dict:
    """Worker에 붙은 것: workers.dev·미리보기 URL(`GET …/scripts/<이름>/subdomain`), 사용자 도메인(`GET …/workers/domains?service=`),
    route(읽지 못하면 None). 모양이 다르면 ValueError."""
    if not isinstance(subdomain, dict) or not isinstance(subdomain.get("enabled"), bool):
        raise ValueError("subdomain 응답에 enabled가 없어요")
    if not isinstance(domains, list):
        raise ValueError("domains 응답이 목록이 아니에요")
    doms = sorted(f"{d.get('hostname')}{'' if d.get('enabled', True) else '(꺼짐)'}"
                  for d in domains if isinstance(d, dict) and d.get("service", name) == name)
    return {"workers_dev": subdomain["enabled"], "previews": bool(subdomain.get("previews_enabled", False)),
            "domains": doms, "routes": None if routes is None else sorted(str(r) for r in routes)}


def attachment_line(a: dict) -> str:
    routes = "읽지 못함" if a["routes"] is None else (", ".join(a["routes"]) or "없음")
    return (f"사용자 도메인 {', '.join(a['domains']) or '없음'} · workers.dev {'켜짐' if a['workers_dev'] else '꺼짐'} · "
            f"미리보기 URL {'켜짐' if a['previews'] else '꺼짐'} · route {routes}")


def attachment_diff(before: dict, after: dict) -> list[str]:
    """배포 앞뒤로 Worker에 붙은 것이 달라졌으면 그 줄들. route는 앞뒤 모두 읽었을 때만 견줘요."""
    out = []
    for k, label in (("domains", "사용자 도메인"), ("workers_dev", "workers.dev"), ("previews", "미리보기 URL")):
        if before[k] != after[k]:
            out.append(f"{label}: {before[k]} → {after[k]}")
    if before["routes"] is not None and after["routes"] is not None and before["routes"] != after["routes"]:
        out.append(f"route: {before['routes']} → {after['routes']}")
    return out


def deploy_version_id(ndjson: str, text: str) -> str | None:
    """`wrangler deploy`가 올려 100%로 돌린 판 번호."""
    for line in ndjson.splitlines():
        try:
            e = json.loads(line)
        except ValueError:
            continue
        if e.get("type") == "deploy" and e.get("version_id"):
            return e["version_id"]
    m = re.search(r"Current Version ID:\s*(" + UUID.pattern + ")", text)
    return m.group(1) if m else None


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


TEST_PLACE = (re.compile(r"""^\s*location:\s*'([^']+?):(\d+):\d+'"""), re.compile(r"([A-Za-z0-9_.-]+\.test\.mjs):(\d+):\d+"))


def failing_places(output: str, limit: int = 20) -> list[dict]:
    """2.0.0 [U60-02] (검토 2026-11-12) 실패한 시험마다 이름과 줄: [{name: '파일.test.mjs › 이름', at: '파일.test.mjs:줄'}].
    tap은 'not ok' 뒤 location(시험을 적은 줄), spec은 '✖' 뒤 첫 .test.mjs 줄. 이름은 날짜 시험 결과(tools/date_watch.py)와 같은 모양이라
    '어제 main에서는 통과/실패'를 견줄 수 있어요."""
    lines = output.splitlines()
    out: list[dict] = []
    for i, line in enumerate(lines):
        name = None
        for pat in TEST_FAIL:
            m = pat.match(line)
            if m:
                name = m.group(1).strip()
                break
        if not name or re.match(r"^(failing tests:?|tests? \d+|\d+ failing)$", name, re.I) or name.endswith(".mjs"):
            continue
        at = ""
        for nxt in lines[i + 1:i + 25]:
            if TEST_FAIL[0].match(nxt) or TEST_FAIL[1].match(nxt):
                break
            m = TEST_PLACE[0].match(nxt) or TEST_PLACE[1].search(nxt)
            if m:
                at = f"{Path(m.group(1)).name}:{m.group(2)}"
                break
        key = f"{at.split(':')[0]} › {name}" if at else name
        if all(x["name"] != key for x in out):
            out.append({"name": key, "at": at})
    return out[:limit]


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
    "DO 첫 배포": "멈춘 까닭을 고친 뒤 mode migrate-check부터 다시 돌려요(손님은 앞 판 그대로)",
    "DO 첫 배포 확인": "**되돌릴 수 없어요**(Cloudflare가 migration 앞 판으로 되돌리기를 막아요). 문제를 보고 고친 판을 보통 deploy로 올려요. 붙은 연결이 달라졌으면 Cloudflare 대시보드 › Workers › 설정 › 도메인·경로에서 다시 붙여요",
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


SKIP_MAX_DAYS = 14


def parse_skips(items, today) -> list[tuple[str, str, str]]:
    """2.0.0 [RVR-17] '시험 이름|까닭|YYYY-MM-DD' → [(이름, 까닭, 기한)]. 모양이 틀리거나 기한이 지났거나 너무 멀면 Stop."""
    import datetime
    out = []
    for item in items or []:
        for one in str(item).split(";"):
            if not one.strip():
                continue
            parts = [x.strip() for x in one.split("|")]
            if len(parts) != 3 or not all(parts):
                raise Stop(f"넘길 시험은 '시험 이름|까닭|기한(YYYY-MM-DD)' 모양이어야 해요: {one.strip()}", stage="시험")
            name, why, until = parts
            try:
                day = datetime.date.fromisoformat(until)
            except ValueError:
                raise Stop(f"넘길 시험의 기한이 날짜가 아니에요: {until}", stage="시험") from None
            if day < today:
                raise Stop(f"넘길 시험 '{name}'의 기한 {until}이 지났어요: 시험을 고치거나 새 기한과 까닭으로", stage="시험")
            if (day - today).days > SKIP_MAX_DAYS:
                raise Stop(f"넘길 시험 '{name}'의 기한 {until}이 {SKIP_MAX_DAYS}일보다 멀어요", stage="시험")
            out.append((name, why, until))
    return out


def test_command(wd: Path, skips) -> list[str]:
    """[RVR-17] 서버 시험 명령: 넘길 것이 없으면 npm test, 있으면 같은 시험을 이름으로 건너뛰어요(node --test-skip-pattern)."""
    if not skips:
        return ["npm", "test"]
    pattern = "|".join("^" + re.escape(name) + "$" for name, _, _ in skips)
    files = sorted(str(p.relative_to(wd)) for p in (wd / "test").glob("*.test.mjs"))
    return ["node", "--test", "--disable-warning=ExperimentalWarning", f"--test-skip-pattern={pattern}", *files]


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

def build(workers: list[str], out: Path, prev: Path | None, skips=()) -> None:
    """시험·번들·판 이름을 [out]/<worker>/에(worker.js, wrangler.toml, version.txt, notes.txt). 토큰·열쇠 없는 환경에서만 불러요."""
    if os.environ.get("CLOUDFLARE_API_TOKEN"):
        raise Stop("build에 Cloudflare 토큰이 보여요. 토큰은 ship 일에만 둬요(RVQ-01)", stage="시험")
    for w in workers:
        wd = SERVER / WORKERS[w]["dir"]
        dest = out / w
        dest.mkdir(parents=True, exist_ok=True)
        if (wd / "package-lock.json").exists():
            run(["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], wd, stage="시험", clean_env=True)
        p = run(test_command(wd, skips), wd, stage="시험", clean_env=True, check=False)
        if p.returncode != 0:
            # [U60-02] 실패한 시험 이름·줄을 남겨 두면, 배포 workflow의 explain 일이 어제 main의 날짜 시험 결과와 견줘 요약에 적어요.
            text = p.stdout + p.stderr
            places = failing_places(text)
            (out / "fail").mkdir(parents=True, exist_ok=True)
            (out / "fail" / f"{w}-fail.json").write_text(json.dumps({"worker": w, "failed": places}, ensure_ascii=False), encoding="utf-8")
            raise Stop(f"`{' '.join(test_command(wd, skips)[:2])} …` 실패 (코드 {p.returncode})\n```\n{text[-1500:]}\n```", stage="시험",
                       tests=[f"{x['name']} ({x['at']})" if x["at"] else x["name"] for x in places][:8] or failing_tests(text))
        run(["npm", "run", "bundle"], wd, stage="번들", clean_env=True)
        bundle = wd / "dist/worker.js"
        if not bundle.exists():
            raise Stop("dist/worker.js가 없어요", stage="번들")
        notes = [f"서버 시험 통과, 번들 {bundle.stat().st_size // 1024}KB"]
        # [RVR-17] 넘긴 시험은 배포 기록에 남아요('name'은 아래 시험 이름이에요).
        notes += [f"**넘긴 시험**: `{test_name}` — {why} (기한 {until})" for test_name, why, until in skips]
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


class Prep:
    """올리기 앞 단계(앞 판·설정·배포용 설정·판 이름)의 결과. 보통 ship(stage_upload)과 DO 첫 배포(stage_migrate)가 같이 써요."""

    def __init__(self, w, base, name, old_id, old_view, old, config_text, new_ver, live_ver):
        self.w, self.base, self.name, self.old_id, self.old_view, self.old = w, base, name, old_id, old_view, old
        self.config_text, self.new_ver, self.live_ver = config_text, new_ver, live_ver


def prepare(worker: str, src: Path, work: Path, allow_older: bool) -> Prep:
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
    return Prep(w, base, name, old_id, old_view, old, config_text, new_ver, live_ver)


def stage_upload(worker: str, src: Path, work: Path, sha: str, reason: str, allow_older: bool) -> Ready:
    p = prepare(worker, src, work, allow_older)
    w, name, old_id, old, config_text, new_ver, live_ver = p.w, p.name, p.old_id, p.old, p.config_text, p.new_ver, p.live_ver
    # 2.0.0 [RVD-33] 적용 안 된 Durable Object migration이 있으면 versions upload가 못 올려요: 올리기 전에 migrate로 보내요.
    lc = do_lifecycle(config_text)
    if lc["migrations"]:
        try:
            pending = pending_migrations(lc, live_migration_tag(w))
        except Exception as e:  # noqa: BLE001 - 못 읽으면 wrangler versions upload가 직접 막아요(upload_hint)
            say(f"- 실서버 migration 태그를 못 읽었어요({type(e).__name__}). wrangler가 올릴 때 확인해요")
            pending = []
        if pending is None or pending:
            tags = "설정에 없는 실서버 태그" if pending is None else ", ".join(str(m.get("tag")) for m in pending)
            raise Stop(f"아직 적용되지 않은 Durable Object migration({tags})이 있어 versions upload로 못 올려요", stage="올리기", next_step=DO_PENDING_NEXT)

    # 5·6. 올리기와 바인딩 대조
    def upload(extra_vars: dict[str, str]) -> tuple[str, list[dict]]:
        out = work / ".deploy-out.ndjson"
        out.unlink(missing_ok=True)
        cmd = WRANGLER + ["versions", "upload", "worker.js", "--config", "wrangler.deploy.toml", "--no-bundle", "--keep-vars",
                          "--tag", sha[:12], "--message", f"{new_ver} {sha[:8]} {reason}"[:100]]
        for k, v in extra_vars.items():
            cmd += ["--var", f"{k}:{v}"]
        try:
            p = run(cmd, work, {"WRANGLER_OUTPUT_FILE_PATH": str(out)}, stage="올리기")
        except Stop as e:
            hint = upload_hint(str(e))
            if hint:
                raise Stop(str(e), stage="올리기", next_step=hint) from e
            raise
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


def attachments(w: dict) -> dict:
    """지금 Worker에 붙은 연결(workers.dev·미리보기 URL·사용자 도메인·route). route는 wrangler가 읽는 곳에서 읽고, 못 읽으면 None."""
    acc = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    n = w["name"]
    sub = cf("GET", f"/accounts/{acc}/workers/scripts/{n}/subdomain").get("result")
    doms = cf("GET", f"/accounts/{acc}/workers/domains?service={n}").get("result") or []
    try:
        rs = cf("GET", f"/accounts/{acc}/workers/services/{n}/environments/production/routes").get("result") or []
        routes = [r.get("pattern") for r in rs if isinstance(r, dict)]
    except Exception:  # noqa: BLE001 - route는 zone 권한이 없으면 못 읽어요(wrangler deploy는 설정에 route가 없으면 건드리지 않아요)
        routes = None
    return attachment_state(sub, doms, routes, n)


def live_migration_tag(w: dict) -> str | None:
    acc = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    return script_migration_tag(cf("GET", f"/accounts/{acc}/workers/services/{w['name']}"))


def stage_migrate(worker: str, src: Path, work: Path, sha: str, reason: str, allow_older: bool, mode: str) -> None:
    """2.0.0 [RVD-33] Durable Object 첫 배포: `wrangler deploy` + 확인. 올린 뒤에는 앞 판으로 되돌리지 않아요(Cloudflare가 막아요).
    migrate-check는 dry-run까지(손님은 앞 판 그대로). 순서는 맨 위 설명의 'Durable Object 첫 배포'."""
    p = prepare(worker, src, work, allow_older)
    w, base, name, old_id, old, config_text, new_ver = p.w, p.base, p.name, p.old_id, p.old, p.config_text, p.new_ver
    message = f"{new_ver} {sha[:8]} {reason}"[:100]
    lc = do_lifecycle(config_text)
    try:
        live_tag = live_migration_tag(w)
    except Exception as e:  # noqa: BLE001 - 되돌릴 수 없는 배포라 모르면 멈춰요
        raise Stop(f"실서버 migration 태그를 읽지 못했어요({type(e).__name__}: {e}). 되돌릴 수 없는 배포라 멈춰요", stage="DO 첫 배포") from e
    pending = pending_migrations(lc, live_tag)
    problems = migrate_problems(config_text, old, pending)
    if problems:
        raise Stop("Durable Object 첫 배포를 하지 않아요: " + " / ".join(problems), stage="DO 첫 배포")
    tags = ", ".join(str(m.get("tag")) for m in pending or [])
    classes = sorted({c for m in pending or [] for c in (m.get("new_classes") or []) + (m.get("new_sqlite_classes") or [])})
    binds = ", ".join(f"{n}→{c}" for n, c in sorted(lc["bindings"].items()))
    # wrangler deploy는 트리거도 맞춰요. 설정에 route·사용자 도메인이 없으면 대시보드의 것을 건드리지 않지만(4.150.0 triggersDeploy:
    # routes·custom domains는 설정에 있을 때만 PUT), workers.dev·미리보기 URL은 매번 보내요. 그래서 지금 연결을 읽어 남기고,
    # 그 두 값은 지금 값으로 못 박고, 올린 뒤 같은지 견줘요.
    try:
        before = attachments(w)
    except Exception as e:  # noqa: BLE001 - 지금 연결을 모르면 견줄 수 없어 멈춰요
        raise Stop(f"지금 붙은 연결(workers.dev·사용자 도메인)을 읽지 못했어요({type(e).__name__}: {e}). 되돌릴 수 없는 배포라 멈춰요",
                   stage="DO 첫 배포") from e
    config_text = pin_subdomain(config_text, before["workers_dev"], before["previews"])
    (work / "wrangler.deploy.toml").write_text(config_text, encoding="utf-8")
    say(f"- 지금 연결: {attachment_line(before)}. workers.dev·미리보기 URL은 이 값으로 적어 올려요")
    run(WRANGLER + ["deploy", "worker.js", "--config", "wrangler.deploy.toml", "--no-bundle", "--dry-run", "--outdir", ".deploy-dry"], work,
        stage="DO 첫 배포")
    say(f"- 적용할 migration {tags} (실서버 지금 {live_tag or '없음'}): 새 클래스 {', '.join(classes)}, 바인딩 {binds}. "
        f"`wrangler deploy --dry-run` 통과")
    if mode == "migrate-check":
        say(f"- migrate-check: 여기서 멈춰요(손님은 앞 판 그대로). migrate로 올리면 그 판이 새 되돌리기 바닥이 되고 "
            f"앞 판 `{old_id[:8]}`과 그 앞으로는 Cloudflare가 되돌리기를 막아요")
        return

    # 7. wrangler deploy. 앞 판의 일반 변수는 --keep-vars로 남지만, 되돌릴 수 없어 값까지 --var로 넘겨요(설정에 있는 이름은 빼요).
    keep = {n for n, t in binding_names(old).items() if t == "plain_text"} - config_binding_names(config_text)
    out = work / ".deploy-out.ndjson"
    out.unlink(missing_ok=True)
    cmd = WRANGLER + ["deploy", "worker.js", "--config", "wrangler.deploy.toml", "--no-bundle", "--keep-vars",
                      "--tag", sha[:12], "--message", message]
    for k, v in plain_vars(old, keep).items():
        cmd += ["--var", f"{k}:{v}"]
    say(f"- **되돌릴 수 없는 배포 시작**: migration {tags}")
    pr = run(cmd, work, {"WRANGLER_OUTPUT_FILE_PATH": str(out)}, check=False)
    new_id = deploy_version_id(out.read_text(encoding="utf-8") if out.exists() else "", pr.stdout + pr.stderr)
    problems = []
    if pr.returncode != 0:
        problems.append(f"`wrangler deploy` 코드 {pr.returncode}\n```\n{(pr.stdout + pr.stderr)[-1500:]}\n```")
    try:
        serving = current_versions(wrangler_json(["deployments", "status"] + name, work))
    except Stop as e:
        serving = []
        problems.append(f"배포 상태를 읽지 못했어요: {e}")
    if serving == [(old_id, 100.0)] and pr.returncode != 0:
        raise Stop("`wrangler deploy`가 새 판을 올리지 못했어요(손님은 앞 판 그대로). " + " / ".join(problems), stage="DO 첫 배포")
    new_id = new_id or (serving[0][0] if len(serving) == 1 else "")

    # 8. 확인. 실패해도 되돌리지 않아요.
    try:
        if not new_id or new_id == old_id:
            problems.append(f"새 판 번호를 알 수 없어요(배포 상태 {serving})")
        else:
            new = find_bindings(wrangler_json(["versions", "view", new_id] + name, work))
            lost = missing_bindings(old, new)
            if lost:
                problems.append(f"앞 판 바인딩이 빠졌어요: {lost}")
            no_do = sorted(set(lc["bindings"]) - set(binding_names(new)))
            if no_do:
                problems.append(f"새 판에 DO 바인딩이 없어요: {no_do}")
        tag_now = live_migration_tag(w)
        if tag_now != lc["tags"][-1]:
            problems.append(f"실서버 migration 태그가 {tag_now}예요(바라는 것 {lc['tags'][-1]})")
        moved = attachment_diff(before, attachments(w))
        if moved:
            problems.append("붙은 연결이 달라졌어요(사람이 Cloudflare 대시보드 › Workers › 설정 › 도메인·경로에서 다시 붙여요): " + " / ".join(moved))
        want = crons_of(config_text)
        if want is not None:
            have = schedules(worker)
            if have != sorted(want):
                now = schedules(worker, want)
                say(f"- 정기 작업 맞춤: {have} → {now}")
        if new_id and new_id != old_id:
            for i in range(5):
                time.sleep(10)
                ok, note = healthy(worker, base, new_ver)
                if not ok:
                    problems.append(f"{i + 1}번째: {note}")
            serving = current_versions(wrangler_json(["deployments", "status"] + name, work))
            if serving != [(new_id, 100.0)]:
                problems.append(f"배포 상태가 새 판 100%가 아니에요: {serving}")
    except Exception as e:  # noqa: BLE001 - 무엇이 어긋나도 크게 알리고 끝내요
        problems.append(f"확인 중 오류: {e}")
    if problems:
        raise Stop(f"Cloudflare가 migration 앞 판(`{old_id[:8]}`)으로 되돌리기를 막아서 되돌리지 않아요. 지금 손님은 `{(new_id or '?')[:8]}`이 받아요. "
                   "문제: " + " / ".join(problems), stage="DO 첫 배포 확인")
    say(f"- **Durable Object 첫 배포 끝**: {w['name']} {new_ver} `{new_id[:8]}`, migration {lc['tags'][-1]} 적용, health 5번 모두 맞음. "
        f"새 되돌리기 바닥 `{new_id[:8]}`(그 앞 `{old_id[:8]}`로는 못 돌아가요)")


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
    if mode.startswith("migrate"):
        # 2.0.0 [RVD-33] Durable Object 첫 배포는 되돌릴 수 없어서 한 서버씩, 카나리·되돌리기 없이.
        if len(workers) != 1:
            raise Stop("DO 첫 배포(migrate)는 한 서버씩: target을 company나 share로 골라요", stage="DO 첫 배포")
        account()
        with tempfile.TemporaryDirectory(prefix="bamnat-ship-") as tmp:
            work = Path(tmp) / workers[0]
            work.mkdir()
            say(f"#### {WORKERS[workers[0]]['name']}")
            stage_migrate(workers[0], src / workers[0], work, sha, reason, allow_older, mode)
        return
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
    ap.add_argument("--mode", choices=["check", "deploy", "migrate-check", "migrate"], default="check")
    ap.add_argument("--sha", default=os.environ.get("GITHUB_SHA", "local"))
    ap.add_argument("--reason", default="")
    ap.add_argument("--allow-older", action="store_true")
    ap.add_argument("--skip-test", action="append", default=[], help="build: '시험 이름|까닭|YYYY-MM-DD' (RVR-17, 기한 14일 안)")
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
            import datetime
            skips = parse_skips(a.skip_test, datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).date())
            build(workers, a.out, a.prev if a.prev and a.prev.exists() else None, skips)
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
