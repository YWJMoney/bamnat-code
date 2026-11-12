#!/usr/bin/env python3
"""2.0.0 [I60b-01][U60-01][U60-02] (검토 2026-11-12) 날짜 시험 결과를 사람이 읽는 말로.

날마다 main에서 시험을 시계를 옮겨 돌리는 일(.github/workflows/date-watch.yml)과 PR·배포 요약이 같이 써요.

  python3 tools/date_watch.py report --label core --days 30 <JUnit 파일·폴더…> [--out core+30.json] [--today 2026-11-12]
      '12월 12일(+30일)에 깨질 시험 N개'와 이름·줄(파일:줄). --out에는 같은 내용을 JSON으로(다음 날 견주기·관리자 앱 신호용).
  python3 tools/date_watch.py compare <어제.json> <오늘.json>
      어제는 통과했는데 오늘 깨진 시험. 두 결과의 커밋이 같으면 '코드 변경 없음'(날짜·바깥 탓)이라고 말해요.
  python3 tools/date_watch.py against <기준.json> <이름…>
      실패한 시험마다 기준(어제 main·기준 브랜치)에서는 통과였는지 실패였는지 없었는지.
  python3 tools/date_watch.py explain <배포 build의 실패.json> <어제 main의 +0일 결과.json>
      [U60-02] 배포가 시험에서 멈췄을 때: 실패한 시험 이름·줄과 어제 main에서는 통과/실패, 그래서 무엇을 할지.

JUnit은 Gradle(core·앱)과 node --test-reporter=junit(서버) 둘 다 읽어요. 시험 이름은 'Class.method'(Gradle),
'파일.test.mjs › 시험 이름'(node, 파일을 알 때)이에요.
"""
from __future__ import annotations

import datetime
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

KST = datetime.timezone(datetime.timedelta(hours=9))
KT_FRAME = re.compile(r"\(([A-Za-z0-9_]+\.kt):(\d+)\)")
MJS_FRAME = re.compile(r"([A-Za-z0-9_.-]+\.test\.mjs):(\d+)")


def files(paths) -> list[str]:
    out = []
    for p in paths:
        if os.path.isdir(p):
            out += sorted(glob.glob(os.path.join(p, "**", "*.xml"), recursive=True))
        elif os.path.exists(p):
            out.append(p)
    return out


def where(text: str, cls: str) -> str:
    """실패 글에서 시험 파일의 줄: Gradle은 그 시험 클래스 파일(없으면 첫 .kt), node는 첫 .test.mjs."""
    frames = KT_FRAME.findall(text)
    simple = cls.rsplit(".", 1)[-1].split("$")[0]
    for f, n in frames:
        if f == simple + ".kt":
            return f"{f}:{n}"
    m = MJS_FRAME.search(text)
    if m:
        return f"{m.group(1)}:{m.group(2)}"
    return f"{frames[0][0]}:{frames[0][1]}" if frames else ""


def read(paths) -> dict:
    """JUnit 결과 → {tests, skipped, failed: [{name, at}]} (이름 순, 같은 이름은 한 번)."""
    tests, skipped, failed, seen = 0, 0, [], set()
    for f in files(paths):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            tests += 1
            if case.find("skipped") is not None:
                skipped += 1
                continue
            bad = case.find("failure")
            if bad is None:
                bad = case.find("error")
            if bad is None:
                continue
            cls, name = case.get("classname") or "", case.get("name") or "?"
            text = (bad.text or "") + " " + (bad.get("message") or "")
            at = where(text, cls)
            if cls and cls != "test":
                full = f"{cls.rsplit('.', 1)[-1]}.{name}"
            else:
                full = f"{at.split(':')[0]} › {name}" if at.endswith(tuple("0123456789")) and ".test.mjs" in at else name
            if full not in seen:
                seen.add(full)
                failed.append({"name": full, "at": at})
    return {"tests": tests, "skipped": skipped, "failed": sorted(failed, key=lambda x: x["name"])}


def day_label(today: datetime.date, days: int) -> str:
    d = today + datetime.timedelta(days=days)
    return f"{d.month}월 {d.day}일" + (f"(+{days}일)" if days else "(오늘)")


def report(label: str, days: int, result: dict, today: datetime.date) -> list[str]:
    head = day_label(today, days)
    fails = result["failed"]
    if not fails:
        return [f"**{label} · {head}: 깨질 시험 없음** ({result['tests']}개 돎)"]
    if days:
        out = [f"**{label} · {head}에 깨질 시험 {len(fails)}개** ({result['tests']}개 중) — 그날이 오기 전에 고쳐요"]
    else:
        out = [f"**{label} · {head} 깨진 시험 {len(fails)}개** ({result['tests']}개 중)"]
    for f in fails[:30]:
        out.append(f"- `{f['name']}`" + (f" ({f['at']})" if f["at"] else ""))
    if len(fails) > 30:
        out.append(f"- … {len(fails) - 30}개 더")
    return out


def names(result: dict) -> set[str]:
    return {f["name"] for f in result.get("failed", [])}


def compare(prev: dict, cur: dict) -> list[str]:
    """[U60-01] 어제는 통과, 오늘 처음 깨짐. 커밋이 같으면 코드 변경 없음(날짜·바깥 탓)."""
    fresh = sorted(names(cur) - names(prev))
    healed = sorted(names(prev) - names(cur))
    same = prev.get("sha") and prev.get("sha") == cur.get("sha")
    out = []
    if fresh:
        why = "코드 변경 없음: 날짜나 바깥(자료·서버) 탓이에요" if same else f"그사이 main이 바뀜({(prev.get('sha') or '?')[:8]} → {(cur.get('sha') or '?')[:8]})"
        out.append(f"**어제는 통과, 오늘 처음 깨짐 {len(fresh)}개** — {why}")
        out += [f"- `{n}`" for n in fresh[:30]]
    if healed:
        out.append(f"어제 깨졌다가 오늘 통과 {len(healed)}개: " + ", ".join(f"`{n}`" for n in healed[:10]))
    return out or ["어제와 같아요(새로 깨진 시험 없음)"]


def against(base: dict | None, failing: list[str], where: str = "기준") -> list[tuple[str, str]]:
    """[U60-01][U60-02] 실패한 시험마다 기준 결과에서의 모습: '기준에서도 깨짐' | '기준에서는 통과' | '기준에 없음'(새 시험이거나 결과 없음)."""
    if not base:
        return [(n, f"{where} 결과 없음") for n in failing]
    bad = names(base)
    ran = base.get("tests", 0) > 0
    out = []
    for n in failing:
        if n in bad:
            out.append((n, f"{where}에서도 깨짐"))
        elif ran:
            out.append((n, f"{where}에서는 통과"))
        else:
            out.append((n, f"{where}에 없음"))
    return out


NEXT = {
    "어제 main에서도 깨짐": "이 브랜치 탓이 아니에요(날짜·바깥). 고치는 PR을 먼저, 급하면 skip_tests '이름|까닭|기한'으로 넘겨요",
    "어제 main에서는 통과": "이 브랜치의 바뀜을 먼저 봐요",
    "어제 main에 없음": "새 시험이에요. 이 브랜치에서 고쳐요",
    "어제 main 결과 없음": "어제 날짜 시험 결과가 없어 견주지 못했어요",
}


def explain(fail: dict, base: dict | None) -> list[str]:
    """[U60-02] 배포 build가 시험에서 멈췄을 때 한 줄씩: 이름·줄, 어제 main, 할 일."""
    failed = fail.get("failed", [])
    label = {"company": "회사 서버", "share": "공유 서버"}.get(fail.get("worker", ""), fail.get("worker", ""))
    if not failed:
        return [f"**{label}**: 실패한 시험 이름을 읽지 못했어요(로그의 맨 끝을 봐요)"]
    out = [f"**{label}: 실패한 시험 {len(failed)}개**"]
    at = {f["name"]: f.get("at", "") for f in failed}
    for n, how in against(base, [f["name"] for f in failed], "어제 main"):
        out.append(f"- `{n}`" + (f" ({at[n]})" if at[n] else "") + f": {how} → {NEXT.get(how, '')}")
    return out


def load(path: str) -> dict | None:
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def main(argv: list[str]) -> int:
    if not argv or argv[0] not in ("report", "compare", "against", "explain"):
        print(__doc__)
        return 2
    cmd, rest = argv[0], argv[1:]
    if cmd == "report":
        label, days, out, today, paths = "시험", 0, None, datetime.datetime.now(KST).date(), []
        i = 0
        while i < len(rest):
            a = rest[i]
            if a in ("--label", "--days", "--out", "--today"):
                v = rest[i + 1]
                if a == "--label":
                    label = v
                elif a == "--days":
                    days = int(v)
                elif a == "--out":
                    out = v
                else:
                    today = datetime.date.fromisoformat(v)
                i += 2
            else:
                paths.append(a)
                i += 1
        result = read(paths)
        print("\n".join(report(label, days, result, today)))
        if out:
            os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
            with open(out, "w", encoding="utf-8") as f:
                json.dump({"label": label, "days": days, "date": (today + datetime.timedelta(days=days)).isoformat(),
                           "ran": today.isoformat(), "sha": os.environ.get("GITHUB_SHA", ""), **result}, f, ensure_ascii=False, indent=1)
        return 0
    if cmd == "compare":
        prev, cur = load(rest[0]), load(rest[1])
        if cur is None:
            print("오늘 결과를 못 읽었어요")
            return 0
        print("\n".join(compare(prev, cur) if prev else ["어제 결과가 없어 견주지 못했어요(첫날이거나 지난 결과가 지워짐)"]))
        return 0
    if cmd == "explain":
        fail = load(rest[0])
        if fail is None:
            print("실패 기록을 못 읽었어요")
            return 0
        print("\n".join(explain(fail, load(rest[1]) if len(rest) > 1 else None)))
        return 0
    base = load(rest[0])
    for n, how in against(base, rest[1:]):
        print(f"- `{n}`: {how}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
