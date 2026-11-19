#!/usr/bin/env python3
"""2.0.0 [RVR-46] (총괄 11-19) '묶음 확인': one file that says what a whole run of Android CI passed, for the PC check to cite per line.

A hand-run of Actions › Android (workflow_dispatch) runs the core tests, every app shard (unit, shots-a … shots-e, api) and lint on one
commit. The last job gathers the results (the `tests-*.json` each of those wrote, tools/slow_tests.py --json) and the jobs' own results
(GitHub's `needs`) into `묶음-확인.md` and `bundle-check.json`:

- the commit and the verdict, '모두 통과' only when every job succeeded, no test failed and no shard is missing;
- one line per job (core, app shards, lint, servers, market): result, tests, failed, skipped, minutes;
- the tests that failed, by name, and apart those that were skipped (not a failure, but a test that quietly stopped running shows there);
- the list of test classes that passed (all their tests ran and none failed), by job: the evidence a line in the tracker can name
  ('합친 나무 시험 통과': the class name and the commit).

Usage: python3 tools/bundle_check.py <dir with tests-*.json files> --needs <needs.json> [--sha <commit>] [--run <url>] --out <dir>
The directory is searched for `tests-*.json` at any depth (gh run download puts each artifact in its own folder).
"""
import glob
import json
import os
import sys

# The shards a full run must bring (android.yml matrix), so a missing result is said, not passed over.
EXPECT = ["core", "unit", "shots-a", "shots-b", "shots-c", "shots-d", "shots-e", "api"]
# Jobs without tests of their own whose result still decides the verdict.
OTHER = ["servers", "lint", "market", "admin-shots"]


def name_of(path):
    """tests-core.json → core, tests-app-shots-b.json → shots-b."""
    base = os.path.basename(path)[:-5]
    return base[len("tests-app-"):] if base.startswith("tests-app-") else base[len("tests-"):]


def read(root):
    out = {}
    for p in sorted(glob.glob(os.path.join(root, "**", "tests-*.json"), recursive=True)):
        try:
            with open(p, encoding="utf-8") as f:
                out[name_of(p)] = json.load(f)
        except (OSError, ValueError):
            continue
    return out


def results_of(needs):
    """needs.json is GitHub's toJSON(needs): {job: {result: success|failure|cancelled|skipped}}."""
    return {k: (v or {}).get("result", "?") for k, v in (needs or {}).items()}


def passed_classes(run):
    """Classes whose tests all ran and passed (a class with a skipped test is listed apart, it did not fully run)."""
    counts = run.get("counts") or {}
    ok, partly = [], []
    for cls, (tests, failed, skipped) in sorted(counts.items()):
        if failed or tests == 0:
            continue
        (partly if skipped else ok).append(cls)
    if not counts:  # an older file without 'counts': only the classes with no failed test by name
        bad = {f["name"].split(".")[0] for f in run.get("failed", [])}
        ok = sorted(c for c in (run.get("classes") or {}) if c not in bad)
    return ok, partly


def build(runs, needs, sha="", url=""):
    jobs = results_of(needs)
    rows, problems, notes, passed = [], [], [], {}
    for name in EXPECT:
        run = runs.get(name)
        # core's result is the job 'core'; the app shards are the matrix of job 'app', one result for all of them.
        res = jobs.get("core") if name == "core" else jobs.get("app", "?")
        if run is None:
            problems.append(f"{name}: 결과 파일이 없어요(돌지 않았거나 멈췄어요)")
            rows.append((name, "결과 없음", 0, 0, 0, 0.0))
            continue
        failed, skipped = run.get("failed", []), run.get("skipped", [])
        # The matrix has one result for all shards, so a shard that ran and passed is not called a failure because another one failed.
        if name != "core" and res not in ("?", "success"):
            res = "failure" if failed else ("success" if run.get("tests", 0) else res)
        rows.append((name, res if res != "?" else "-", run.get("tests", 0), len(failed), len(skipped), round(run.get("seconds", 0) / 60, 1)))
        if failed:
            problems.append(f"{name}: 실패 {len(failed)}개: " + ", ".join(f"`{f['name']}`" for f in failed[:12]) + (" …" if len(failed) > 12 else ""))
        if skipped:  # not a failure (some tests are skipped on purpose, as the store shots), but said: a test that quietly stopped running shows here
            notes.append(f"{name}: 건너뜀 {len(skipped)}개: " + ", ".join(f"`{s}`" for s in skipped[:8]) + (" …" if len(skipped) > 8 else ""))
        passed[name] = passed_classes(run)
    for name in OTHER:
        res = jobs.get(name)
        if res is None:
            continue
        rows.append((name, res, None, None, None, None))
        if res not in ("success", "skipped"):
            problems.append(f"{name}: {res}")
    if jobs.get("app") not in (None, "success"):
        problems.append(f"app: {jobs.get('app')}(앱 묶음 가운데 실패·취소·시간 넘김이 있어요)")
    if jobs.get("core") not in (None, "success"):
        problems.append(f"core: {jobs.get('core')}")
    verdict = "모두 통과" if not problems else "통과하지 못함"
    return {"sha": sha, "run": url, "verdict": verdict, "problems": problems, "notes": notes, "jobs": rows, "passed": passed}


def markdown(b):
    out = [f"# 묶음 확인 — {b['sha'][:9] or '커밋 모름'}", "", f"**{b['verdict']}**" + (f" · [Actions 실행]({b['run']})" if b["run"] else ""), ""]
    if b["problems"]:
        out += ["## 통과하지 못한 것", ""] + [f"- {p}" for p in b["problems"]] + [""]
    if b["notes"]:
        out += ["## 건너뛴 시험(돌지 않았어요, 실패는 아니에요)", ""] + [f"- {p}" for p in b["notes"]] + [""]
    out += ["## 일마다", "", "| 일 | 결과 | 시험 | 실패 | 건너뜀 | 분(시험 시간 합) |", "| --- | --- | ---: | ---: | ---: | ---: |"]
    for name, res, tests, failed, skipped, mins in b["jobs"]:
        out.append(f"| {name} | {res} | " + " | ".join("-" if v is None else str(v) for v in (tests, failed, skipped, mins)) + " |")
    out += ["", "## 통과한 시험 클래스", "",
            "줄의 근거로 적을 때는 이 커밋과 클래스 이름을 함께 적어요(예: `" + (b['sha'][:9] or "<커밋>") + " 묶음 확인: <클래스>`).", ""]
    for name, (ok, partly) in b["passed"].items():
        out.append(f"### {name}: {len(ok)}개")
        out.append("")
        out.append(", ".join(ok) if ok else "(없음)")
        if partly:
            out += ["", f"건너뛴 시험이 있어 온전히 돌지 않은 클래스 {len(partly)}개: " + ", ".join(partly)]
        out.append("")
    return "\n".join(out)


def main(argv):
    root, needs_path, sha, url, out_dir, i = None, None, os.environ.get("GITHUB_SHA", ""), "", ".", 0
    while i < len(argv):
        a = argv[i]
        if a in ("--needs", "--sha", "--run", "--out"):
            v = argv[i + 1]
            if a == "--needs":
                needs_path = v
            elif a == "--sha":
                sha = v
            elif a == "--run":
                url = v
            else:
                out_dir = v
            i += 2
        else:
            root = a
            i += 1
    if not root:
        print(__doc__)
        return 2
    needs = {}
    if needs_path:
        try:
            with open(needs_path, encoding="utf-8") as f:
                needs = json.load(f)
        except (OSError, ValueError):
            needs = {}
    b = build(read(root), needs, sha, url)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "묶음-확인.md"), "w", encoding="utf-8") as f:
        f.write(markdown(b) + "\n")
    with open(os.path.join(out_dir, "bundle-check.json"), "w", encoding="utf-8") as f:
        json.dump(b, f, ensure_ascii=False, indent=1)
    print(markdown(b))
    return 0 if b["verdict"] == "모두 통과" else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
