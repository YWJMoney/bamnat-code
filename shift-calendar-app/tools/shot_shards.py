#!/usr/bin/env python3
"""2.0.0 [RVR-43] (총괄 HQ-121): how the screenshot tests are split into CI shards (shots-a … shots-e).

app/build.gradle.kts reads tools/data/shot-seconds.tsv (seconds each screenshot class took in CI) and hands the classes out
by time: longest first, each to the shard with the least so far. A class not in the file counts as DEFAULT seconds, and the
last shard also takes every screenshot class no other shard named, so a new test is never left out.

  python3 tools/shot_shards.py show                       # the shards Gradle makes now, with their seconds
  python3 tools/shot_shards.py update tests-shots-*.json  # new seconds from the CI results (tests-app-shots-* artifacts)

`show` follows the same steps as Gradle (keep the two the same).
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WEIGHTS = ROOT / "tools/data/shot-seconds.tsv"
TESTS = ROOT / "app/src/test/java/com/bamnat/app/screenshots"
SHARDS = ["shots-a", "shots-b", "shots-c", "shots-d", "shots-e"]  # [RVR-45] five (app/build.gradle.kts)
DEFAULT = 30.0


def read_weights(path=WEIGHTS):
    w = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                name, sec = line.split("\t")[:2]
                w[name] = float(sec)
    return w


def classes(folder=TESTS):
    return sorted(p.stem for p in folder.glob("*Test.kt"))


def plan(names, weights, n=len(SHARDS)):
    """Longest first into the emptiest shard (ties: the first). Returns [(seconds, [names])] per shard."""
    bins = [[0.0, []] for _ in range(n)]
    for name in sorted(names, key=lambda c: (-weights.get(c, DEFAULT), c)):
        b = min(range(n), key=lambda i: (bins[i][0], i))
        bins[b][0] += weights.get(name, DEFAULT)
        bins[b][1].append(name)
    return bins


def main(argv):
    if not argv or argv[0] not in ("show", "update"):
        print(__doc__)
        return 2
    weights = read_weights()
    if argv[0] == "update":
        for f in argv[1:]:
            data = json.loads(pathlib.Path(f).read_text(encoding="utf-8"))
            for name, sec in data.get("classes", {}).items():
                if sec > 0:
                    weights[name] = round(float(sec), 1)
        live = set(classes())
        lines = ["# 2.0.0 [RVR-43] seconds each screenshot class took in CI (tools/shot_shards.py update). Gradle splits the shards by these."]
        lines += [f"{k}\t{v}" for k, v in sorted(weights.items()) if k in live]
        WEIGHTS.write_text("\n".join(lines) + "\n", encoding="utf-8")
        weights = read_weights()
    names = classes()
    missing = [c for c in names if c not in weights]
    for shard, (sec, members) in zip(SHARDS, plan(names, weights)):
        print(f"{shard}: {len(members)}개 클래스, 약 {sec / 60:.1f}분(시험 시간 합)")
    if missing:
        print(f"걸린 시간을 모르는 클래스 {len(missing)}개는 {DEFAULT:.0f}초로 셈: {', '.join(missing[:12])}{' …' if len(missing) > 12 else ''}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
