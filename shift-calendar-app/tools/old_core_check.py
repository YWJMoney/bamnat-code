#!/usr/bin/env python3
"""2.0.0 [RVR-21] (검토 SD-07, 스튜디오 제작 오류 점검 5장 S6-4) 판 호환 시험: 나간 판의 앱이 장터에서 보는 작품을 모두 열 수 있나요.

옛 앱의 RecipeCodec은 모르는 이름이 하나라도 있으면 작품 전체를 거절하고, 읽기는 했어도 다시 쓴 hash가 서버 hash와 다르면
'레시피가 오는 길에 바뀌었어요'로 받지 않아요(앱 RecipeClient.RecipeDoc.recipe). 서버는 그런 작품을 판 호환표(parts-since.js →
min_app)로 옛 앱의 목록에서 빼요. 이 시험은 표가 맞는지를 진짜 옛 core로 봐요.

1. tools/market_works.py: docs/recipes의 작품 전체(app/build/market-works.json).
2. server/company-worker/tools/served-works.mjs: 서버가 작품마다 정한 min_app과, 판마다 실제로 보내는 몸통·hash.
3. tools/data/release-cores.json의 판마다 그 출시 커밋의 core를 꺼내(git archive, 얕은 저장소면 그 커밋만 받아요) 시험 대신
   작은 탐침 하나만 넣고 빌드해, 작품마다 RecipeCodec.read와 RecipeCodec.hash가 서버 hash와 같은지 봐요.

실패: 그 판 목록에 나오는(min_app ≤ 그 판, 계획 파일로 내리지 않은) 작품이 하나라도 그 판 core에서 안 열리면. 내린 작품이나 다시
올려 옛 몸통이 된 작품이 안 열리는 것은 참고로만 적어요. 판 호환표(부품·기능 장치)가 막았지만 실제로는 열리는 작품 수도 적어요
(표가 너무 엄할 때, 실패 아님). 운영자가 올릴 때 정한 minApp(출시 묶음)으로 막힌 것은 세지 않아요.

  python3 tools/old_core_check.py                     # 네 판 모두, 요약은 화면(마크다운)에
  python3 tools/old_core_check.py --only 53 --json app/build/old-core-check.json
  GRADLE_FLAGS="--no-daemon" python3 tools/old_core_check.py   # CI
"""
import argparse
import base64
import json
import os
import shlex
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
REPO = os.path.normpath(os.path.join(ROOT, ".."))
RELEASES = os.path.join(HERE, "data", "release-cores.json")
BUILD = os.path.join(ROOT, "app", "build")
SERVED_TOOL = os.path.join(ROOT, "server", "company-worker", "tools", "served-works.mjs")

sys.path.insert(0, HERE)
import market_works  # noqa: E402

# The probe replaces the old core's own tests (only its main sources are built). It uses what every 출시 core has had since 1.36:
# RecipeCodec.read(String) and RecipeCodec.hash(Recipe). Input and output are files in the core folder (the test's working folder).
PROBE = """package com.bamnat.oldcore

import com.bamnat.core.recipe.RecipeCodec
import org.junit.Test
import java.io.File
import java.util.Base64

/** 2.0.0 [RVR-21] tools/old_core_check.py: each line "key\\thash\\tbase64(body)" read as this 판's app reads a 장터 작품. */
class OldCoreProbe {
    @Test fun readEveryWork() {
        val out = StringBuilder()
        File("probe-in.txt").forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val (key, hash, b64) = line.split('\\t')
            val body = String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
            val result = try {
                val r = RecipeCodec.read(body)
                if (RecipeCodec.hash(r) == hash) "ok" else "hash"
            } catch (e: Throwable) {
                "read:" + (e.message ?: e.javaClass.simpleName).replace('\\t', ' ').replace('\\n', ' ').take(200)
            }
            out.append(key).append('\\t').append(result).append('\\n')
        }
        File("probe-out.txt").writeText(out.toString())
    }
}
"""


def releases(path=RELEASES):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    out = data["releases"]
    for r in out:
        if not (isinstance(r.get("versionCode"), int) and isinstance(r.get("commit"), str) and len(r["commit"]) == 40):
            raise ValueError(f"release-cores.json: 판 줄이 이상해요 {r}")
    return out


def probe_lines(served, app):
    """[(key, hash, body)] for every work the server takes, as the app at [app] would receive it."""
    lines = []
    for w in served["works"]:
        if w.get("refused"):
            continue
        a = w["apps"][str(app)]
        lines.append((w["key"], a["hash"], a.get("body", w["text"])))
    return lines


def probe_text(lines):
    return "".join(f"{k}\t{h}\t{base64.b64encode(b.encode('utf-8')).decode('ascii')}\n" for k, h, b in lines)


def parse_results(text):
    out = {}
    for line in text.splitlines():
        if not line.strip():
            continue
        key, _, result = line.partition("\t")
        out[key] = result
    return out


def judge(works, served, app, results):
    """What [results] (key → "ok" | "hash" | "read:…") say for the app at [app]."""
    meta = {w["key"]: w for w in works}
    report = {"app": app, "listed": 0, "opened": 0, "broken": [], "brokenHidden": [], "tooStrict": [], "refused": [], "missing": []}
    for w in served["works"]:
        m = meta.get(w["key"], {})
        if w.get("refused"):
            report["refused"].append({"key": w["key"], "title": m.get("title", ""), "why": w["refused"]})
            continue
        listed = w["apps"][str(app)]["listed"]
        table_blocks = (w.get("partsMinApp") or 0) > app
        shown = listed and market_works.shown_to(m, app) if m else listed
        r = results.get(w["key"])
        if r is None:
            report["missing"].append(w["key"])
            continue
        if listed:
            report["listed"] += 1
            if r == "ok":
                report["opened"] += 1
            else:
                row = {"key": w["key"], "title": m.get("title", ""), "minApp": w.get("minApp"), "why": r}
                (report["broken"] if shown else report["brokenHidden"]).append(row)
        elif r == "ok" and table_blocks:
            report["tooStrict"].append(w["key"])
    return report


def failed(reports):
    return any(r["broken"] or r["refused"] or r["missing"] for r in reports)


def markdown(reports, rel_names):
    out = ["### 판 호환 시험 (옛 core로 장터 작품 열기, SD-07 S6-4)", "",
           "| 판 | 목록에 나옴 | 열림 | 안 열림 | 내린 작품 중 안 열림 | 표가 막았지만 열림 |", "|---|---|---|---|---|---|"]
    for r in reports:
        out.append(f"| {rel_names.get(r['app'], r['app'])} ({r['app']}) | {r['listed']} | {r['opened']} | {len(r['broken'])} | "
                   f"{len(r['brokenHidden'])} | {len(r['tooStrict'])} |")
    for r in reports:
        for row in r["broken"][:30]:
            out.append(f"- **{rel_names.get(r['app'], r['app'])}에서 안 열림** `{row['key']}` {row['title']} (min_app {row['minApp']}): {row['why']}")
        if len(r["broken"]) > 30:
            out.append(f"- … {len(r['broken']) - 30}개 더")
        for row in r["brokenHidden"][:5]:
            out.append(f"- 참고({rel_names.get(r['app'], r['app'])}, 내린 작품) `{row['key']}` {row['title']}: {row['why']}")
        for k in r["missing"][:5]:
            out.append(f"- **탐침 결과 없음** `{k}`")
    refused = reports[0]["refused"] if reports else []
    for row in refused[:20]:
        out.append(f"- **서버가 거절** `{row['key']}` {row['title']}: {row['why']}")
    out.append("")
    out.append("실패하면: 그 작품이 쓴 이름을 server/company-worker/src/parts-since.js에 그 이름이 처음 읽히는 판으로 적어요(갈래 S SD-03이 "
               "core 내보내기로 바꾸는 중). 표가 막았지만 열림은 실패가 아니에요.")
    return "\n".join(out)


def run(cmd, cwd=None, env=None, log=None):
    p = subprocess.run(cmd, cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if log:
        with open(log, "w", encoding="utf-8") as f:
            f.write(p.stdout)
    return p


def ensure_commit(sha):
    if run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], cwd=REPO).returncode == 0:
        return
    p = run(["git", "fetch", "--quiet", "--depth=1", "origin", sha], cwd=REPO)
    if p.returncode != 0:
        raise RuntimeError(f"커밋 {sha[:10]}을 받지 못했어요: {p.stdout[-400:]}")


def extract(sha, dest):
    """The release's core (main sources only), its gradle wrapper and remote/v1 (core bundles it), into [dest]."""
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    os.makedirs(dest)
    paths = ["shift-calendar-app/core", "shift-calendar-app/gradlew", "shift-calendar-app/gradle", "remote/v1"]
    archive = subprocess.Popen(["git", "archive", sha, *paths], cwd=REPO, stdout=subprocess.PIPE)
    subprocess.run(["tar", "-x", "-C", dest], stdin=archive.stdout, check=True)
    if archive.wait() != 0:
        raise RuntimeError(f"git archive {sha[:10]} 실패")
    core = os.path.join(dest, "shift-calendar-app", "core")
    shutil.rmtree(os.path.join(core, "src", "test"), ignore_errors=True)
    probe = os.path.join(core, "src", "test", "kotlin", "com", "bamnat", "oldcore")
    os.makedirs(probe)
    with open(os.path.join(probe, "OldCoreProbe.kt"), "w", encoding="utf-8") as f:
        f.write(PROBE)
    return core


def probe_release(rel, lines, work):
    ensure_commit(rel["commit"])
    dest = os.path.join(work, str(rel["versionCode"]))
    core = extract(rel["commit"], dest)
    with open(os.path.join(core, "probe-in.txt"), "w", encoding="utf-8") as f:
        f.write(probe_text(lines))
    flags = shlex.split(os.environ.get("GRADLE_FLAGS", "--console=plain"))
    app_dir = os.path.dirname(core)
    log = os.path.join(work, f"gradle-{rel['versionCode']}.log")
    p = run(["sh", "./gradlew", *flags, "-p", "core", "test", "--rerun"], cwd=app_dir, log=log)
    out = os.path.join(core, "probe-out.txt")
    if p.returncode != 0 or not os.path.isfile(out):
        raise RuntimeError(f"{rel['name']} core 빌드·탐침 실패 (기록 {log}):\n{p.stdout[-1500:]}")
    with open(out, encoding="utf-8") as f:
        return parse_results(f.read())


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--only", help="이 versionCode들만 (쉼표)")
    ap.add_argument("--json", help="결과를 JSON으로도")
    ap.add_argument("--works", help="작품 목록 파일(market_works.py 모양) — 없으면 docs/recipes에서 만들어요")
    ap.add_argument("--work", default=os.path.join(BUILD, "old-cores"), help="옛 core를 꺼낼 곳")
    a = ap.parse_args(argv)
    rels = releases()
    if a.only:
        want = {int(x) for x in a.only.split(",")}
        rels = [r for r in rels if r["versionCode"] in want]
    os.makedirs(a.work, exist_ok=True)
    if a.works:
        with open(a.works, encoding="utf-8") as f:
            works = json.load(f)["works"]
    else:
        works = market_works.load()
    works_path = os.path.join(a.work, "market-works.json")
    served_path = os.path.join(a.work, "served-works.json")
    with open(works_path, "w", encoding="utf-8") as f:
        json.dump({"works": works}, f, ensure_ascii=False)
    apps = ",".join(str(r["versionCode"]) for r in rels)
    p = run(["node", "--disable-warning=ExperimentalWarning", SERVED_TOOL, works_path, served_path, apps])
    if p.returncode != 0:
        print(p.stdout, file=sys.stderr)
        return 1
    with open(served_path, encoding="utf-8") as f:
        served = json.load(f)
    reports = []
    for rel in rels:
        t = time.time()
        lines = probe_lines(served, rel["versionCode"])
        results = probe_release(rel, lines, a.work)
        rep = judge(works, served, rel["versionCode"], results)
        rep["seconds"] = round(time.time() - t)
        reports.append(rep)
        print(f"{rel['name']}({rel['versionCode']}): 목록 {rep['listed']} 중 열림 {rep['opened']}, 안 열림 {len(rep['broken'])}, "
              f"내린 작품 중 안 열림 {len(rep['brokenHidden'])}, 표가 막았지만 열림 {len(rep['tooStrict'])} ({rep['seconds']}초)", file=sys.stderr)
    names = {r["versionCode"]: r["name"] for r in rels}
    print(markdown(reports, names))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"releases": rels, "reports": reports}, f, ensure_ascii=False, indent=1)
    return 1 if failed(reports) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
