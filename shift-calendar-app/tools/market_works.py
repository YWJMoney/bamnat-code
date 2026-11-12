#!/usr/bin/env python3
"""2.0.0 [RVR-21] (검토 SD-07, 스튜디오 제작 오류 점검 5장 S6-3·S6-4) 장터 작품 전체를 한 목록으로.

docs/recipes/*.json(작품 배열: market-*·showcase·starter·theme, 갈래 S의 가장자리 작품 edge-200)의 모든 작품을 한 목록으로
만들어요. 판 호환 시험(tools/old_core_check.py), 작품마다 달력 사진 대조(app MarketWorksShotsTest), 바깥 길 시험(SD-09)이
같은 목록과 같은 이름(key)을 써요.

- key: "<파일 이름>#<1부터 센 번호>" (예: "market-139#12"). 갈래 S의 가장자리 작품은 제목 앞에서 "edge:E1"처럼, edge: true
  (실서버 장터에 내지 않는 작품이라 장터 숫자에 섞지 않아요).
- hiddenFrom: 장터 고르기 계획(*-refresh-plan.json의 hide)이 내린 작품. 0이면 모든 앱에서, N이면 versionCode N 이상 앱에서
  내림(belowApp: 그보다 옛 앱에는 계속 보여요). null이면 내리지 않았어요.
- replacedBy: "<판>-refresh" 파일이 같은 제목으로 다시 올린 옛 작품(서버에서는 같은 코드에 새 몸통이라 옛 몸통은 더 안 나가요).
- hash: 작품을 정렬된 JSON으로 쓴 sha256. 빌드끼리 같은 작품인지 볼 때만 써요(서버의 hash는 served-works.mjs가 서버 식으로).

  python3 tools/market_works.py                       # app/build/market-works.json, 요약을 화면에
  python3 tools/market_works.py --out other.json
"""
import argparse
import glob
import hashlib
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
RECIPES = os.path.join(ROOT, "docs", "recipes")
OUT = os.path.join(ROOT, "app", "build", "market-works.json")
EDGE = re.compile(r"^시험\s*(E\d+)(?!\d)")


def recipe_hash(recipe):
    text = json.dumps(recipe, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def hide_plans(folder):
    """{(file, title): hiddenFrom} from every market-<판>-refresh-plan.json's hide list (0 = every app, N = apps at N and newer).

    A line names its file with from ("138-studio" → market-138-studio); one without names every market file older than its plan."""
    out = {}
    files = [os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(folder, "market-*.json"))]
    for path in sorted(glob.glob(os.path.join(folder, "*-plan.json"))):
        plan_of = re.sub(r"-refresh-plan$|-plan$", "", os.path.splitext(os.path.basename(path))[0])
        with open(path, encoding="utf-8") as f:
            plan = json.load(f)
        for h in plan.get("hide", []) if isinstance(plan, dict) else []:
            h = h if isinstance(h, dict) else {"title": h}
            title = h.get("title")
            if not isinstance(title, str):
                continue
            below = h.get("belowApp")
            frm = below if isinstance(below, int) and below > 0 else 0
            src = h.get("from")
            targets = [f"market-{src}"] if isinstance(src, str) else [f for f in files if f < plan_of and not f.endswith("-plan")]
            for t in targets:
                # Hidden by two plans: the one that hides it from more apps.
                out[(t, title)] = min(out.get((t, title), frm), frm)
    return out


def load(folder=RECIPES):
    """Every work in the folder's work files, in file order. Raises ValueError on a file that is not a list of works."""
    hidden = hide_plans(folder)
    works, keys = [], set()
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        name = os.path.splitext(os.path.basename(path))[0]
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict):  # a plan, not works
            continue
        if not isinstance(data, list):
            raise ValueError(f"{name}.json: 작품 배열이 아니에요")
        for i, entry in enumerate(data):
            recipe = entry.get("recipe") if isinstance(entry, dict) else None
            if not isinstance(recipe, dict):
                raise ValueError(f"{name}.json {i + 1}번: recipe가 없어요")
            title = recipe.get("title") if isinstance(recipe.get("title"), str) else ""
            m = EDGE.match(title)
            key = f"edge:{m.group(1)}" if m and name.startswith("edge") else f"{name}#{i + 1}"
            if key in keys:
                raise ValueError(f"{name}.json {i + 1}번: 이름 {key}이 겹쳐요")
            keys.add(key)
            min_app = entry.get("minApp")
            works.append({
                "key": key,
                "file": name,
                "n": i + 1,
                "title": title,
                "minApp": min_app if isinstance(min_app, int) and min_app > 0 else None,
                "batch": entry.get("batch"),
                "edge": key.startswith("edge:"),
                "hiddenFrom": hidden.get((name, title)),
                "replacedBy": None,
                "styles": recipe.get("styles") if isinstance(recipe.get("styles"), list) else [],
                "hash": recipe_hash(recipe),
                "recipe": recipe,
            })
    # A "<판>-refresh" file's work replaces the same title in older market files (the operator upload's replace: the same code, a new body).
    by_title = {}
    for w in works:
        by_title.setdefault(w["title"], []).append(w)
    for w in works:
        if not w["file"].endswith("-refresh"):
            continue
        base = w["file"][: -len("-refresh")]
        for old in by_title.get(w["title"], []):
            if old is not w and old["file"].startswith("market-") and old["file"] < base:
                old["replacedBy"] = w["key"]
    return works


def shown_to(work, app):
    """Whether the 장터 lists [work] to an app at versionCode [app], by its own minApp and the plans (the server adds parts' minApp)."""
    if work["replacedBy"] or (work["minApp"] and work["minApp"] > app):
        return False
    h = work["hiddenFrom"]
    return h is None or (h > 0 and app < h)


def summary(works):
    files = {}
    for w in works:
        files[w["file"]] = files.get(w["file"], 0) + 1
    edge = sum(1 for w in works if w["edge"])
    hidden_all = sum(1 for w in works if w["hiddenFrom"] == 0)
    hidden_new = sum(1 for w in works if w["hiddenFrom"])
    replaced = sum(1 for w in works if w["replacedBy"])
    lines = [f"작품 {len(works)}개 (가장자리 {edge}개, 모든 앱에서 내림 {hidden_all}개, 새 앱에서만 내림 {hidden_new}개, 다시 올려 바뀐 옛 몸통 {replaced}개)"]
    lines += [f"  {k}: {v}" for k, v in files.items()]
    return "\n".join(lines)


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--recipes", default=RECIPES)
    ap.add_argument("--out", default=OUT)
    a = ap.parse_args(argv)
    try:
        works = load(a.recipes)
    except (ValueError, json.JSONDecodeError) as e:
        print(f"작품 목록을 못 만들었어요: {e}", file=sys.stderr)
        return 1
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump({"works": works}, f, ensure_ascii=False)
    print(summary(works))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
