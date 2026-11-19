#!/usr/bin/env python3
"""1.39 [DEPLOY-01] 서버 자동 배포: 크롬·대시보드 붙여 넣기 없이 GitHub Actions에서 두 Worker를 올려요.

사용자 결정(2026-11-08): 'Claude가 끝까지'. 승인 단계 없이 Claude가 workflow를 시작하고, 아래 안전장치가 막아요.

  python3 tools/deploy_worker.py --worker company|share --mode check|deploy --sha <커밋> --reason <글> [--allow-older]

순서 (한 단계라도 어긋나면 손님을 받기 전에 멈춰요)
  1. 지금 100%로 도는 판(앞 판)과 그 판의 바인딩(DB·KV·변수·비밀값 이름)을 읽어요. 두 판이 나눠 돌고 있으면 멈춰요.
  2. 서버 시험 전부(npm test) → 번들(npm run bundle, 대시보드에 붙여 넣던 dist/worker.js와 같은 파일).
  3. wrangler.toml의 자리표시자(DB·KV 번호)를 앞 판의 바인딩에서 채운 배포용 설정을 만들어요(저장소에 번호를 적지 않아요).
  4. 판 이름(src/version.js)이 지금 도는 판보다 낮으면 멈춰요(--allow-older로만 넘어가요). 지금 판은 그 Worker 자신의 것으로 봐요:
     공유는 /api/health, 회사는 /api/admin/health의 판(2.0.0 [PC-32]), 없으면 앞 판을 올릴 때 이 도구가 적은 메시지 맨 앞
     판 이름(2.0.0 [RVD-19]). 회사 판 이름을 알 수 없으면(대시보드에서 올린 옛 판 등) 낮은지 모르니 멈춰요.
  5. `wrangler versions upload`: 새 판을 올리기만 해요(아직 손님 안 받음).
  6. 새 판의 바인딩 이름이 앞 판을 모두 담는지 대조해요. 대시보드에서만 넣은 일반 변수가 빠졌으면 앞 판 값으로 한 번 더 올리고,
     그래도 빠지면 멈춰요(올린 판은 쓰이지 않고 남을 뿐이에요). check 모드는 여기까지.
  7. `wrangler versions deploy <새 판>@100%`, 정기 작업(cron)이 wrangler.toml과 다르면 맞춰요.
  8. health를 10초 간격 5번 + 배포 상태가 새 판 100%인지 확인. 하나라도 실패하면 앞 판을 100%로 되돌리고 실패로 끝나요.
     되돌리기는 코드만이에요. DB 판(스키마)은 앞으로만 가요.

열쇠: 환경 변수 CLOUDFLARE_API_TOKEN(GitHub Secret). 출력하지 않아요. 계정 번호는 CLOUDFLARE_ACCOUNT_ID가 없으면 토큰이 닿는
계정이 하나일 때 그 계정을 써요.

2.0.0 [RVD-33] Durable Object 첫 배포 (--mode migrate-check|migrate, 한 Worker씩)
  Durable Object 클래스를 새로 만드는(지우는·옮기는) migration이 든 판은 `versions upload`로 못 올려요(wrangler 4.150.0:
  "pending Durable Object migration … must be applied with wrangler deploy"). Cloudflare 문서: 그런 판은 `wrangler deploy`로 올리고
  (…/versions-and-deployments/gradual-deployments/#durable-objects), 올린 뒤에는 그 앞 판으로 되돌리기를 막아요
  (…/versions-and-deployments/rollbacks/, 주소 앞은 https://developers.cloudflare.com/workers/configuration).
  그래서 보통 deploy는 설정에 실서버보다 새 migration이 있으면 올리기 전에 멈추고, 아래 순서로만 올려요.
  1~4. 보통 deploy와 같아요(앞 판·시험·번들·설정·판 이름).
  5. 실서버 migration 태그(wrangler가 보는 /workers/services/<이름>)와 설정을 맞춰 적용할 migration을 고르고, 멈출 까닭을 봐요:
     적용할 것이 없음, 실서버 태그가 설정에 없음, 지우기·이름 바꾸기·옮기기(데이터가 사라지거나 옮겨짐, 사람이 정해요),
     바인딩 클래스를 만드는 migration 없음, 앞 판 바인딩이 설정에 없어 사라짐, 설정 이름이 비밀값을 덮어씀.
  6. 지금 붙은 연결(사용자 도메인·route·workers.dev·미리보기 URL)을 읽어 남기고, workers.dev·미리보기 URL은 그 값으로 배포용 설정에
     적어요(wrangler deploy가 매번 보내는 값). route·사용자 도메인은 설정에 없으면 wrangler deploy가 건드리지 않아요
     (4.150.0 triggersDeploy: 설정에 있을 때만 PUT). `wrangler deploy --dry-run`. migrate-check는 여기까지(손님은 앞 판 그대로).
  7. `wrangler deploy --keep-vars`(앞 판 일반 변수는 --var로 값까지 넘겨요). 새 판이 100%가 되고 이 판이 새 되돌리기 바닥이에요.
  8. 배포 상태 새 판 100%, 바인딩 대조(앞 판 것 모두 + DO 바인딩), 실서버 migration 태그가 설정 마지막 태그, 붙은 연결이 6과 같음,
     정기 작업, health 5번.
     실패해도 앞 판으로 되돌리지 않아요(Cloudflare가 막아요). 크게 알리고 실패로 끝나요: 고친 판을 보통 deploy로 올려요.
  토큰 권한은 그대로예요. Durable Object 전용 권한은 없고 'Workers Scripts 편집'에 들어 있어요
  (https://developers.cloudflare.com/api/resources/durable_objects/subresources/namespaces/methods/list/).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# 배포할 코드는 workflow가 고른 브랜치를 따로 받은 곳(BAMNAT_SERVER_ROOT), 없으면 이 저장소의 server/.
SERVER = Path(os.environ.get("BAMNAT_SERVER_ROOT") or ROOT / "server").resolve()
WRANGLER = ["npx", "--yes", "wrangler@4.150.0"]
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
    """손님을 받기 전에 멈춤(앞 판 그대로)."""


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


# ---------- 바깥과 닿는 곳 ----------

def say(line: str) -> None:
    print(line, flush=True)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


def run(cmd: list[str], cwd: Path, extra_env: dict | None = None, check: bool = True) -> subprocess.CompletedProcess:
    env = {**os.environ, "NO_COLOR": "1", "WRANGLER_SEND_METRICS": "false", **(extra_env or {})}
    p = subprocess.run(cmd, cwd=cwd, env=env, text=True, capture_output=True)
    if check and p.returncode != 0:
        tail = (p.stdout + p.stderr)[-1500:]
        raise Stop(f"`{' '.join(cmd[:4])} …` 실패 (코드 {p.returncode})\n```\n{tail}\n```")
    return p


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


def get(url: str) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"cache-control": "no-cache", "user-agent": "bamnat-deploy"}), timeout=15) as r:
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


def healthy(worker: str, base: str, want_version: str, deep: bool = True) -> tuple[bool, str]:
    if worker == "company":
        code, body = get(f"{base}/api/admin/health?deep=1" if deep else f"{base}/api/admin/health")
        try:
            h = json.loads(body)
        except ValueError:
            h = {}
        ok = company_ok(code, h, deep, want_version)
        code2, _ = get(f"{base}/admin/")
        return ok and code2 == 200, (f"health {code} 판 {h.get('version', '-')} db/password/salt{'/표 판/config' if deep else ''} "
                                     f"{'모두 맞음' if ok else '빠짐'}, /admin/ {code2}")
    code, body = get(f"{base}/api/health")
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


def sync_crons(w: dict, config_text: str) -> None:
    """정기 작업(cron)이 설정과 다르면 맞춰요."""
    want = crons_of(config_text)
    if want is None:
        return
    acc = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    have = [s.get("cron") for s in (cf("GET", f"/accounts/{acc}/workers/scripts/{w['name']}/schedules").get("result") or {}).get("schedules", [])]
    if sorted(have) != sorted(want):
        cf("PUT", f"/accounts/{acc}/workers/scripts/{w['name']}/schedules", [{"cron": c} for c in want])
        say(f"- 정기 작업 맞춤: {have} → {want}")


def watch(worker: str, base: str, new_ver: str, new_id: str, name: list[str], wd: Path) -> list[str]:
    """health를 10초 간격 5번 + 배포 상태가 새 판 100%인지. 어긋난 것 목록."""
    problems = []
    for i in range(5):
        time.sleep(10)
        ok, note = healthy(worker, base, new_ver)
        if not ok:
            problems.append(f"{i + 1}번째: {note}")
    serving = current_versions(wrangler_json(["deployments", "status"] + name, wd))
    if serving != [(new_id, 100.0)]:
        problems.append(f"배포 상태가 새 판 100%가 아니에요: {serving}")
    return problems


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


def deploy(worker: str, mode: str, sha: str, reason: str, allow_older: bool) -> None:
    w = WORKERS[worker]
    wd = SERVER / w["dir"]
    base = (os.environ.get(w["health_env"]) or w["health_base"]).rstrip("/")
    if not os.environ.get("CLOUDFLARE_API_TOKEN"):
        raise Stop("CLOUDFLARE_API_TOKEN이 없어요(사용자 할 일: 저장소 Settings › Secrets › Actions)")
    if not os.environ.get("CLOUDFLARE_ACCOUNT_ID"):
        accounts = cf("GET", "/accounts?per_page=5").get("result") or []
        if len(accounts) != 1:
            raise Stop(f"토큰이 닿는 계정이 {len(accounts)}개예요. Variables에 CLOUDFLARE_ACCOUNT_ID를 넣어요")
        os.environ["CLOUDFLARE_ACCOUNT_ID"] = accounts[0]["id"]
    name = ["--name", w["name"]]

    # 1. 앞 판
    now = current_versions(wrangler_json(["deployments", "status"] + name, wd))
    if len(now) != 1 or now[0][1] < 100:
        raise Stop(f"지금 판이 나뉘어 돌고 있어요({now}). 사람이 먼저 한 판으로 맞춰야 해요")
    old_id = now[0][0]
    old_view = wrangler_json(["versions", "view", old_id] + name, wd)
    old = find_bindings(old_view)
    if not old:
        raise Stop("앞 판의 바인딩을 읽지 못했어요(대조할 수 없어 멈춰요)")
    say(f"- 앞 판 `{old_id[:8]}` 바인딩 {len(old)}개: {', '.join(sorted(binding_names(old)))}")

    # 2. 시험·번들
    if (wd / "package-lock.json").exists():
        run(["npm", "ci", "--no-audit", "--no-fund"], wd)
    run(["npm", "test"], wd)
    run(["npm", "run", "bundle"], wd)
    bundle = wd / "dist/worker.js"
    if not bundle.exists():
        raise Stop("dist/worker.js가 없어요")
    say(f"- 서버 시험 통과, 번들 {bundle.stat().st_size // 1024}KB")

    # 3. 배포용 설정
    config_text = fill_config((wd / "wrangler.toml").read_text(encoding="utf-8"), w["fill"], old)
    deploy_toml = wd / "wrangler.deploy.toml"
    deploy_toml.write_text(config_text, encoding="utf-8")

    # 4. 판 이름
    new_ver = source_version(wd)
    live = live_version(worker, base, old_view)
    why = version_gate(worker, new_ver, live, allow_older)
    if why:
        raise Stop(why)
    say(f"- 판 이름 {new_ver} (지금 {live or '알 수 없음'}{', --allow-older' if allow_older else ''})")
    message = f"{new_ver} {sha[:8]} {reason}"[:100]

    # 2.0.0 [RVD-33] Durable Object migration: 적용 안 된 것이 있으면 보통 deploy는 올리기 전에 멈추고 migrate로만.
    lc = do_lifecycle(config_text)
    if mode.startswith("migrate"):
        migrate(w, worker, mode, sha, base, name, wd, old_id, old, config_text, deploy_toml, new_ver, message, lc)
        return
    if lc["migrations"]:
        try:
            pending = pending_migrations(lc, live_migration_tag(w))
        except Exception as e:  # noqa: BLE001 - 못 읽으면 wrangler versions upload가 직접 막아요
            say(f"- 실서버 migration 태그를 못 읽었어요({type(e).__name__}). wrangler가 올릴 때 확인해요")
            pending = []
        if pending is None or pending:
            tags = "설정에 없는 실서버 태그" if pending is None else ", ".join(str(m.get("tag")) for m in pending)
            raise Stop(f"아직 적용되지 않은 Durable Object migration({tags})이 있어 versions upload로 못 올려요. "
                       f"먼저 mode migrate-check → migrate로 이 Worker만 따로 올려요(그 판이 새 되돌리기 바닥이 돼요)")

    # 5·6. 올리기와 바인딩 대조
    def upload(extra_vars: dict[str, str]) -> tuple[str, list[dict]]:
        out = wd / ".deploy-out.ndjson"
        out.unlink(missing_ok=True)
        cmd = WRANGLER + ["versions", "upload", "dist/worker.js", "--config", deploy_toml.name, "--no-bundle", "--keep-vars",
                          "--tag", sha[:12], "--message", message]
        for k, v in extra_vars.items():
            cmd += ["--var", f"{k}:{v}"]
        p = run(cmd, wd, {"WRANGLER_OUTPUT_FILE_PATH": str(out)})
        vid = upload_version_id(out.read_text(encoding="utf-8") if out.exists() else "", p.stdout + p.stderr)
        if not vid:
            raise Stop("올린 판 번호를 읽지 못했어요")
        return vid, find_bindings(wrangler_json(["versions", "view", vid] + name, wd))

    new_id, new = upload({})
    lost = missing_bindings(old, new)
    if lost and set(lost.values()) <= {"plain_text"}:
        say(f"- 대시보드에만 있던 일반 변수 {len(lost)}개를 앞 판 값으로 다시 올려요: {', '.join(sorted(lost))}")
        new_id, new = upload(plain_vars(old, lost))
        lost = missing_bindings(old, new)
    if lost:
        raise Stop(f"새 판에 앞 판 바인딩이 빠져요: {lost}. wrangler.toml에 먼저 옮겨야 해요(올린 판 `{new_id[:8]}`은 쓰이지 않아요)")
    say(f"- 새 판 `{new_id[:8]}` 올림, 바인딩 {len(new)}개 모두 이어짐")
    if mode == "check":
        say("- check 모드: 여기서 멈춰요(손님은 앞 판 그대로)")
        return

    # 7. 배포와 정기 작업
    run(WRANGLER + ["versions", "deploy", f"{new_id}@100%", "--yes", "--message", message] + name, wd)
    problems = []
    try:
        sync_crons(w, config_text)
        # 8. 확인, 실패면 되돌리기
        problems += watch(worker, base, new_ver, new_id, name, wd)
    except Exception as e:  # noqa: BLE001 - 배포 뒤 무엇이 어긋나도 되돌려요
        problems.append(f"확인 중 오류: {e}")
    if problems:
        run(WRANGLER + ["versions", "deploy", f"{old_id}@100%", "--yes", "--message", f"자동 되돌리기 {sha[:8]}"] + name, wd, check=False)
        time.sleep(10)
        ok, note = healthy(worker, base, live or new_ver, deep=False)
        say(f"- **실패 → 앞 판 `{old_id[:8]}`으로 되돌림** (되돌린 뒤 {note}). 문제: " + " / ".join(problems))
        raise SystemExit(1)
    say(f"- **배포 끝**: {w['name']} {new_ver} `{new_id[:8]}`, health 5번 모두 맞음. 되돌릴 판 `{old_id[:8]}`")


def migrate(w: dict, worker: str, mode: str, sha: str, base: str, name: list[str], wd: Path, old_id: str, old: list[dict],
            config_text: str, deploy_toml: Path, new_ver: str, message: str, lc: dict) -> None:
    """2.0.0 [RVD-33] Durable Object 첫 배포: `wrangler deploy` + 확인. 올린 뒤에는 앞 판으로 되돌리지 않아요(Cloudflare가 막아요)."""
    try:
        live_tag = live_migration_tag(w)
    except Exception as e:  # noqa: BLE001 - 되돌릴 수 없는 배포라 모르면 멈춰요
        raise Stop(f"실서버 migration 태그를 읽지 못했어요({type(e).__name__}: {e}). 되돌릴 수 없는 배포라 멈춰요") from e
    pending = pending_migrations(lc, live_tag)
    problems = migrate_problems(config_text, old, pending)
    if problems:
        raise Stop("Durable Object 첫 배포를 하지 않아요: " + " / ".join(problems))
    tags = ", ".join(str(m.get("tag")) for m in pending or [])
    classes = sorted({c for m in pending or [] for c in (m.get("new_classes") or []) + (m.get("new_sqlite_classes") or [])})
    binds = ", ".join(f"{n}→{c}" for n, c in sorted(lc["bindings"].items()))
    # wrangler deploy는 트리거도 맞춰요. 설정에 route·사용자 도메인이 없으면 대시보드의 것을 건드리지 않지만(4.150.0 triggersDeploy:
    # routes·custom domains는 설정에 있을 때만 PUT), workers.dev·미리보기 URL은 매번 보내요. 그래서 지금 연결을 읽어 남기고,
    # 그 두 값은 지금 값으로 못 박고, 올린 뒤 같은지 견줘요.
    try:
        before = attachments(w)
    except Exception as e:  # noqa: BLE001 - 지금 연결을 모르면 견줄 수 없어 멈춰요
        raise Stop(f"지금 붙은 연결(workers.dev·사용자 도메인)을 읽지 못했어요({type(e).__name__}: {e}). 되돌릴 수 없는 배포라 멈춰요") from e
    config_text = pin_subdomain(config_text, before["workers_dev"], before["previews"])
    deploy_toml.write_text(config_text, encoding="utf-8")
    say(f"- 지금 연결: {attachment_line(before)}. workers.dev·미리보기 URL은 이 값으로 적어 올려요")
    run(WRANGLER + ["deploy", "dist/worker.js", "--config", deploy_toml.name, "--no-bundle", "--dry-run", "--outdir", ".deploy-dry"], wd)
    say(f"- 적용할 migration {tags} (실서버 지금 {live_tag or '없음'}): 새 클래스 {', '.join(classes)}, 바인딩 {binds}. "
        f"`wrangler deploy --dry-run` 통과")
    if mode == "migrate-check":
        say(f"- migrate-check: 여기서 멈춰요(손님은 앞 판 그대로). migrate로 올리면 그 판이 새 되돌리기 바닥이 되고 "
            f"앞 판 `{old_id[:8]}`과 그 앞으로는 Cloudflare가 되돌리기를 막아요")
        return

    # 7. wrangler deploy. 앞 판의 일반 변수는 --keep-vars로 남지만, 되돌릴 수 없어 값까지 --var로 넘겨요(설정에 있는 이름은 빼요).
    keep = {n for n, t in binding_names(old).items() if t == "plain_text"} - config_binding_names(config_text)
    out = wd / ".deploy-out.ndjson"
    out.unlink(missing_ok=True)
    cmd = WRANGLER + ["deploy", "dist/worker.js", "--config", deploy_toml.name, "--no-bundle", "--keep-vars",
                      "--tag", sha[:12], "--message", message]
    for k, v in plain_vars(old, keep).items():
        cmd += ["--var", f"{k}:{v}"]
    say(f"- **되돌릴 수 없는 배포 시작**: migration {tags}")
    p = run(cmd, wd, {"WRANGLER_OUTPUT_FILE_PATH": str(out)}, check=False)
    new_id = deploy_version_id(out.read_text(encoding="utf-8") if out.exists() else "", p.stdout + p.stderr)
    problems = []
    if p.returncode != 0:
        problems.append(f"`wrangler deploy` 코드 {p.returncode}\n```\n{(p.stdout + p.stderr)[-1500:]}\n```")
    try:
        serving = current_versions(wrangler_json(["deployments", "status"] + name, wd))
    except Stop as e:
        serving = []
        problems.append(f"배포 상태를 읽지 못했어요: {e}")
    if serving == [(old_id, 100.0)] and p.returncode != 0:
        raise Stop("`wrangler deploy`가 새 판을 올리지 못했어요. " + " / ".join(problems))
    new_id = new_id or (serving[0][0] if len(serving) == 1 else "")

    # 8. 확인. 실패해도 되돌리지 않아요.
    try:
        if not new_id or new_id == old_id:
            problems.append(f"새 판 번호를 알 수 없어요(배포 상태 {serving})")
        else:
            new = find_bindings(wrangler_json(["versions", "view", new_id] + name, wd))
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
        sync_crons(w, config_text)
        if new_id and new_id != old_id:
            problems += watch(worker, base, new_ver, new_id, name, wd)
    except Exception as e:  # noqa: BLE001 - 무엇이 어긋나도 크게 알리고 끝내요
        problems.append(f"확인 중 오류: {e}")
    if problems:
        say(f"- **실패, 되돌리지 않아요**: Cloudflare가 migration 앞 판(`{old_id[:8]}`)으로 되돌리기를 막아요. 지금 손님은 "
            f"`{(new_id or '?')[:8]}`이 받아요. 고친 판을 보통 deploy로 올려요(이제 versions upload가 돼요). 문제: " + " / ".join(problems))
        raise SystemExit(1)
    say(f"- **Durable Object 첫 배포 끝**: {w['name']} {new_ver} `{new_id[:8]}`, migration {lc['tags'][-1]} 적용, health 5번 모두 맞음. "
        f"새 되돌리기 바닥 `{new_id[:8]}`(그 앞 `{old_id[:8]}`로는 못 돌아가요)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--worker", choices=sorted(WORKERS), required=True)
    ap.add_argument("--mode", choices=["check", "deploy", "migrate-check", "migrate"], default="check")
    ap.add_argument("--sha", default=os.environ.get("GITHUB_SHA", "local"))
    ap.add_argument("--reason", default="")
    ap.add_argument("--allow-older", action="store_true")
    a = ap.parse_args()
    say(f"### {WORKERS[a.worker]['name']} ({a.mode}) {a.sha[:8]}")
    try:
        deploy(a.worker, a.mode, a.sha, re.sub(r"\s+", " ", a.reason).strip(), a.allow_older)
    except Stop as e:
        say(f"- **멈춤(앞 판 그대로)**: {e}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
