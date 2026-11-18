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

    # 5·6. 올리기와 바인딩 대조
    def upload(extra_vars: dict[str, str]) -> tuple[str, list[dict]]:
        out = wd / ".deploy-out.ndjson"
        out.unlink(missing_ok=True)
        cmd = WRANGLER + ["versions", "upload", "dist/worker.js", "--config", deploy_toml.name, "--no-bundle", "--keep-vars",
                          "--tag", sha[:12], "--message", f"{new_ver} {sha[:8]} {reason}"[:100]]
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
    run(WRANGLER + ["versions", "deploy", f"{new_id}@100%", "--yes", "--message", f"{new_ver} {sha[:8]} {reason}"[:100]] + name, wd)
    problems = []
    try:
        want = crons_of(config_text)
        if want is not None:
            acc = os.environ["CLOUDFLARE_ACCOUNT_ID"]
            have = [s.get("cron") for s in (cf("GET", f"/accounts/{acc}/workers/scripts/{w['name']}/schedules").get("result") or {}).get("schedules", [])]
            if sorted(have) != sorted(want):
                cf("PUT", f"/accounts/{acc}/workers/scripts/{w['name']}/schedules", [{"cron": c} for c in want])
                say(f"- 정기 작업 맞춤: {have} → {want}")

        # 8. 확인, 실패면 되돌리기
        for i in range(5):
            time.sleep(10)
            ok, note = healthy(worker, base, new_ver)
            if not ok:
                problems.append(f"{i + 1}번째: {note}")
        serving = current_versions(wrangler_json(["deployments", "status"] + name, wd))
        if serving != [(new_id, 100.0)]:
            problems.append(f"배포 상태가 새 판 100%가 아니에요: {serving}")
    except Exception as e:  # noqa: BLE001 - 배포 뒤 무엇이 어긋나도 되돌려요
        problems.append(f"확인 중 오류: {e}")
    if problems:
        run(WRANGLER + ["versions", "deploy", f"{old_id}@100%", "--yes", "--message", f"자동 되돌리기 {sha[:8]}"] + name, wd, check=False)
        time.sleep(10)
        ok, note = healthy(worker, base, live or new_ver, deep=False)
        say(f"- **실패 → 앞 판 `{old_id[:8]}`으로 되돌림** (되돌린 뒤 {note}). 문제: " + " / ".join(problems))
        raise SystemExit(1)
    say(f"- **배포 끝**: {w['name']} {new_ver} `{new_id[:8]}`, health 5번 모두 맞음. 되돌릴 판 `{old_id[:8]}`")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--worker", choices=sorted(WORKERS), required=True)
    ap.add_argument("--mode", choices=["check", "deploy"], default="check")
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
