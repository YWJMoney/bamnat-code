#!/usr/bin/env python3
"""2.0.0 [RVS-04] 판 호환 자동표: a released app's names, as that release's own core knew them.

For each release (commit, versionCode) it makes a git worktree at the commit, puts today's core RecipeVocab.kt in it (the file only reads
what the 1.36.0 core already had), runs one small test there that writes RecipeVocab.encode(versionCode, RecipeVocab.now()), and keeps
the answer as docs/recipes/vocab/<versionCode>.json. PartsSinceExportTest reads those files to write the server's parts-since.js.

  python3 tools/vocab_snapshot.py                 # every release in RELEASES that has no file yet
  python3 tools/vocab_snapshot.py 53 --again      # one release, again

On the day a version ships, keep its names from the release commit (add it to RELEASES), or from the tree itself with
BAMNAT_WRITE_VOCAB=1 ./gradlew -p core test --tests '*PartsSinceExportTest*' --rerun.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

APP = Path(__file__).resolve().parent.parent          # shift-calendar-app
REPO = APP.parent
VOCAB = APP / "docs/recipes/vocab"
SRC = "shift-calendar-app/core/src/main/kotlin/com/bamnat/core/recipe/RecipeVocab.kt"

# (versionCode, commit of the release): a commit whose gradle.properties says that versionCode and whose core is the one that went to
# Play. The one list is tools/data/release-cores.json (갈래 R's old_core_check.py reads it too), when it is there; this is the same.
RELEASES = [
    (49, "1716e688"),  # 1.36.0 출시 (main)
    (50, "d8c2dca2"),  # 1.36.1 (core = #161 327b0829)
    (51, "d892e64f"),  # 1.37.0 (core = #177 93d2a85a)
    (52, "2230d3af"),  # 1.38.0 (#178, 통합 마지막 커밋)
    (53, "2c93ea17"),  # 1.39.0 (core = #425 da0788cd)
]


def releases() -> list:
    """RELEASES, or tools/data/release-cores.json's: a list of {versionCode, commit} or a map versionCode → commit (or → {commit})."""
    f = APP / "tools/data/release-cores.json"
    if not f.exists():
        return RELEASES
    data = json.loads(f.read_text())
    if isinstance(data, dict):
        data = data.get("releases", data)
    out = []
    if isinstance(data, dict):
        for k, v in data.items():
            if str(k).isdigit():
                out.append((int(k), v["commit"] if isinstance(v, dict) else v))
    else:
        for r in data:
            out.append((int(r.get("versionCode") or r.get("code")), r["commit"]))
    have = {c for c, _ in out}
    return sorted(out + [r for r in RELEASES if r[0] not in have])

TEST = """package com.bamnat.core.recipe

class VocabSnapshotTest {
    @org.junit.Test fun write() {
        java.io.File(System.getenv("VOCAB_OUT")).writeText(RecipeVocab.encode(System.getenv("VOCAB_CODE").toInt(), RecipeVocab.now()))
    }
}
"""


def snapshot(code: int, commit: str) -> None:
    out = VOCAB / f"{code}.json"
    tmp = Path(tempfile.mkdtemp(prefix=f"vocab-{code}-"))
    tree = tmp / "tree"
    subprocess.run(["git", "worktree", "add", "--detach", str(tree), commit], cwd=REPO, check=True, capture_output=True)
    try:
        props = (tree / "shift-calendar-app/gradle.properties").read_text()
        if f"bamnat.versionCode={code}" not in props:
            sys.exit(f"{commit} is not versionCode {code}")
        shutil.copy(REPO / SRC, tree / SRC)
        test = tree / "shift-calendar-app/core/src/test/kotlin/com/bamnat/core/recipe/VocabSnapshotTest.kt"
        test.write_text(TEST)
        env = dict(os.environ, VOCAB_OUT=str(out), VOCAB_CODE=str(code), LANG="C.UTF-8", LC_ALL="C.UTF-8")
        r = subprocess.run(["bash", "./gradlew", "-p", "core", "test", "--tests", "*VocabSnapshotTest*", "--rerun", "-q"],
                           cwd=tree / "shift-calendar-app", env=env, capture_output=True, text=True)
        if r.returncode != 0 or not out.exists():
            sys.exit(f"{code} ({commit}) failed:\n{r.stdout[-3000:]}\n{r.stderr[-3000:]}")
        print(f"{code} ({commit}) -> {out.relative_to(APP)}")
    finally:
        subprocess.run(["git", "worktree", "remove", "--force", str(tree)], cwd=REPO, capture_output=True)
        shutil.rmtree(tmp, ignore_errors=True)


def main() -> None:
    VOCAB.mkdir(parents=True, exist_ok=True)
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    again = "--again" in sys.argv
    for code, commit in releases():
        if args and str(code) not in args:
            continue
        if (VOCAB / f"{code}.json").exists() and not again:
            continue
        snapshot(code, commit)


if __name__ == "__main__":
    main()
