#!/usr/bin/env python3
"""Builds remote/v1/cycles.json from tools/data/company-cycles.csv [SPAT-01].

The CSV is the public-source research (research/shift-patterns/회사별-교대주기.csv in the project files, 148 rows):
표시 이름, 회사·기관, 업종, 사업장·직무, 근무형태, 순서(한 주기), 시간, 시점, 신뢰도, 출처. Only public facts with
their source links; nothing about a person.

Each row keeps its columns as they are and gains what the app needs to put it on a calendar:
  formKey     the 근무형태 in one of a few families (4조2교대, 3조2교대, ...), for search and order candidates
  codes       the rotation in preset codes (D 주간 · E 오후 · N 야간 · X 당번 · B 비번 · O 휴무), Monday first when
              weekly is true; empty when the source gives no order
  orderState  exact (the source spells the order), usual (the source gives a count such as 6근2휴 or 2근2휴 and the
              app fills in the usual order, which the user confirms), none (형태만: the user picks the order)
  shifts      the working shifts with their times; usual=true when the source gave only a length (12시간) and the
              usual times stand in, to be confirmed in the times step
  roster      a monthly roster (병원 D·E·N 근무표), so no order at all

Usage (from shift-calendar-app/):
  python3 tools/cycles_build.py           # writes ../remote/v1/cycles.json and prints the counts
  python3 tools/cycles_build.py --check   # exit 1 when the file on disk differs from a fresh build

The file is a remote data file (RemoteFiles envelope), so after changing it bump nothing by hand: the version is the
build date + 01. Sign it with tools/remote_sign.py like the others before it goes to GitHub Pages.
"""
import csv
import json
import pathlib
import re
import sys

APP = pathlib.Path(__file__).resolve().parent.parent
SRC = APP / "tools" / "data" / "company-cycles.csv"
OUT = APP.parent / "remote" / "v1" / "cycles.json"
VERSION = 2026102501
ISSUED = "2026-10-25"
MIN_APP = 37

FORMS = [
    ("주간연속2교대", "주간연속2교대"),
    ("6조3교대", "6조 3교대"),
    ("5조3교대", "5조 3교대"),
    ("4조3교대", "4조 3교대"),
    ("4조2교대", "4조 2교대"),
    ("3조3교대", "3조 3교대"),
    ("3조2교대", "3조 2교대"),
    ("3조1교대", "3조 1교대"),
    ("2조2교대", "2조 2교대"),
    ("격일제", "격일제"),
    ("4부제", "4부제"),
    ("주야교대", "주야 교대"),
    ("3교대", "3교대"),
    ("2교대", "2교대"),
    ("고정", "고정 근무"),
    ("기타", "그 밖의 교대"),
]


def form_key(form):
    f = form.replace(" ", "")
    if f.startswith("1일2교대"):
        return "2교대"
    if "주간연속2교대" in f:
        return "주간연속2교대"
    for key in ["6조3교대", "5조3교대", "4조3교대", "4조2교대", "3조3교대", "3조2교대", "3조1교대", "2조2교대"]:
        if f.startswith(key) or (key in f and not re.match(r"^\d조\d교대", f)):
            return key
    if "격일" in f:
        return "격일제"
    if "4부제" in f:
        return "4부제"
    if f.startswith("주야4근4휴"):
        return "4조2교대"
    if f.startswith("주야"):
        return "주야교대"
    if f.startswith("3교대"):
        return "3교대"
    if "2교대" in f and not f.startswith("1일"):
        return "2교대"
    if "고정" in f or "일근" in f or f.startswith("주간·야간"):
        return "고정"
    return "기타"


# ---- the order ----

def order_of(row, key):
    """(codes, state, weekly, roster) for a row."""
    o = row["order"].replace(" ", "")
    t = row["time"]
    exact, usual, none = "exact", "usual", "none"
    if not o or o.startswith("공개순서없음") or o.startswith("확인필요") or "투표부결" in o or "순서확인필요" in o:
        return "", none, False, False
    if o.startswith("코드") or "(D·E·N+" in o or "D·A·N순환" in o or "데이·애프터·나이트" in o:
        return "", none, False, False
    if key == "3교대" or o in ("D·E·N", "A·D·N", "E·N순환", "D·E") or "병원이근무표" in o:
        return "", none, False, True
    if "2222" in o or "주2·휴2·야2·휴2" in o:
        return "DDOONNOO", exact, False, False
    if "3333" in o or "주3·휴3·야3·휴3" in o:
        return "DDDOOONNNOOO", exact, False, False
    if "주4·휴4·야4·휴4" in o or "4444" in o:
        return "DDDDOOOONNNNOOOO", usual if "추정" in o else exact, False, False
    if o.startswith("주·주·휴·휴·야·야·휴·휴"):
        return "DDOONNOO", exact, False, False
    if o.startswith("주·야·비·휴또는"):
        return "DNBO", usual, False, False
    if o.startswith("주·야·비·휴(원래)"):
        return "DNBO", usual, False, False
    if o.startswith("주·야·비·휴"):
        return "DNBO", exact, False, False
    if o.startswith("주·주·야·야·휴·휴"):
        return "DDNNOO", exact, False, False
    if o.startswith("주·주·야·야·비·휴"):
        return "DDNNBO", exact, False, False
    if "주4·휴2·야4·휴2" in o and key == "3조2교대":
        return "DDDDOONNNNOO", exact, False, False
    if o.startswith("4근2휴") and key == "3조2교대":
        return "DDDDOONNNNOO", usual, False, False
    if o.startswith("주·야·휴(3일)"):
        return "DNO", exact, False, False
    if o.startswith("당(24시간)·비·비") or o == "당·비·비":
        return "XBO", exact, False, False
    if o.startswith("근무·휴무(2일)"):
        return "XB", exact, False, False
    if o.startswith("근무·휴무격일"):
        return "DO", exact, False, False
    if o.startswith("월·수·금근무"):
        return "NONONOO", exact, True, False
    if o.startswith("6근2휴(8일·전근→야간→후근)"):
        return "DDNNEEOO", exact, False, False
    if o.startswith("6근2휴"):
        return "DDEENNOO", usual, False, False
    if o.startswith("3근1휴"):
        return "DDDOEEEONNNO", usual, False, False
    if o.startswith("1조→2조→3조주단위") and key == "3조3교대":
        # 1조 00-08 · 2조 08-16 · 3조 16-24, a week each, 주5일 가동.
        return "NNNNNOODDDDDOOEEEEEOO", usual, False, False
    if o.startswith("1조·2조주단위교대"):
        return "DDDDDOOEEEEEOO", usual, False, False
    if o.startswith("주간1주·야간1주") or o.startswith("주간1주·야간1주"):
        return "DDDDDOONNNNNOO", usual, False, False
    if o.startswith("주간2주·야간2주"):
        return "DDDDDOODDDDDOONNNNNOONNNNNOO", usual, False, False
    if o.startswith("주간1주+야·비반복2주"):
        return "DDDDDOONBNBNBNBNBNBNB", usual, False, False
    if "2근2휴" in o or o.startswith("2일주기패턴"):
        return "DDOONNOO", usual, False, False
    if o.startswith("월~금"):
        return "DDDDDOO", exact, True, False
    if o == "주5일" and key == "고정":
        code = "N" if row["form"].startswith("야간") else "D"
        return code * 5 + "OO", usual, True, False
    return "", none, False, False


# ---- the shifts ----

NAMES = {"D": ("주간", "주"), "E": ("오후", "오"), "N": ("야간", "야"), "X": ("당번", "당")}
TIME = r"(\d{1,2})(?::(\d{2}))?"
RANGE = re.compile(r"(?:(익일)\s*)?" + TIME + r"\s*-\s*(?:익일\s*)?" + TIME)


def hhmm(h, m):
    h = int(h) % 24
    return "%02d:%02d" % (h, int(m or 0))


def ranges(text):
    out = []
    for part in re.split(r"/", text):
        part = part.strip()
        m = RANGE.search(part)
        if not m:
            continue
        prefix = part[: m.start()].strip()
        out.append((prefix, hhmm(m.group(2), m.group(3)), hhmm(m.group(4), m.group(5))))
    return out


PREFIX = {"주": "D", "주간": "D", "전근": "D", "오전": "D", "M": "D", "1조": "D", "D": "D",
          "E": "E", "후근": "E", "오후": "E", "2조": "E", "야": "N", "야간": "N", "N": "N"}


def code_for(prefix, start, end, n, used):
    """The code of one time range: by its start (a shift of 20 hours or more is a 당번), and by its label only when the
    start gives a code already taken (D 07:30-15:30 / E 09-17)."""
    sh = int(start[:2])
    span = (int(end[:2]) * 60 + int(end[3:]) - sh * 60 - int(start[3:])) % (24 * 60)
    if span == 0 or span >= 20 * 60:
        c = "X"
    elif n == 2:
        c = "D" if 4 <= sh <= 11 else "N"
    elif 4 <= sh <= 11:
        c = "D"
    elif 12 <= sh <= 17:
        c = "E"
    else:
        c = "N"
    if c in used:
        c = PREFIX.get(prefix.strip(), c)
    return None if c in used else c


def shifts_of(row, codes):
    t = row["time"].strip()
    found = ranges(t)
    work = sorted(set(c for c in codes if c in "DENX"))
    out = {}
    if found:
        if len(found) == 1 and len(work) == 1:
            # One time for one kind of work (야간 고정 18:00-08:30, 월·수·금 14:00-08:00): it is that work's.
            out[work[0]] = (found[0][1], found[0][2], False)
        for prefix, start, end in found if not out else []:
            c = code_for(prefix, start, end, len(found), out)
            if c is not None:
                out[c] = (start, end, False)
    usual = {
        "D": ("07:00", "19:00") if "N" in work and "E" not in work else ("06:00", "14:00"),
        "E": ("14:00", "22:00"),
        "N": ("19:00", "07:00") if "E" not in work else ("22:00", "06:00"),
        "X": ("09:00", "09:00"),
    }
    if "12시간" in t:
        usual["D"], usual["N"] = ("07:00", "19:00"), ("19:00", "07:00")
    if "8시간" in t or "7시간" in t:
        usual["D"], usual["E"], usual["N"] = ("06:00", "14:00"), ("14:00", "22:00"), ("22:00", "06:00")
    if "24시간" in t:
        usual["X"] = ("09:00", "09:00")
    m = re.search(r"(\d{2}):(\d{2})\s*교대", t)
    if m:
        usual["X"] = (m.group(1) + ":" + m.group(2), m.group(1) + ":" + m.group(2))
    for c in work:
        if c not in out:
            s, e = usual[c]
            out[c] = (s, e, True)
    # A source with times but no order still brings its shifts (형태만 rows with 06-14 / 14-22 / 22-06).
    if not codes:
        if not found and ("12시간" in t or "8시간" in t or "24시간" in t):
            key = row["formKey"]
            if "12시간" in t:
                out = {"D": ("07:00", "19:00", True), "N": ("19:00", "07:00", True)}
            elif "24시간" in t:
                out = {"X": ("09:00", "09:00", True)}
            elif key in ("4조3교대", "3조3교대", "5조3교대", "6조3교대", "3교대"):
                out = {"D": ("06:00", "14:00", True), "E": ("14:00", "22:00", True), "N": ("22:00", "06:00", True)}
    order = [c for c in "DENX" if c in out]
    return [{"code": c, "name": NAMES[c][0], "label": NAMES[c][1], "start": out[c][0], "end": out[c][1], "usual": out[c][2]} for c in order]


def trust_of(raw):
    kinds = []
    for word, key in (("노조 게시판", "UNION"), ("노무사이트", "LABOR"), ("커뮤니티", "COMMUNITY"), ("기사", "ARTICLE"), ("공고", "NOTICE")):
        if word in raw:
            kinds.append(key)
    return kinds


def build():
    with open(SRC, encoding="utf-8") as f:
        rows = list(csv.reader(f))
    head, body = rows[0], rows[1:]
    assert head == ["표시 이름", "회사·기관", "업종", "사업장·직무", "근무형태", "순서(한 주기)", "시간", "시점", "신뢰도", "출처"], head
    cycles = []
    for i, r in enumerate(body, 1):
        row = dict(display=r[0], company=r[1], industry=r[2], site=r[3], form=r[4], order=r[5], time=r[6], asOf=r[7],
                   trust=r[8], source=r[9])
        row["formKey"] = form_key(row["form"])
        codes, state, weekly, roster = order_of(row, row["formKey"])
        shifts = shifts_of(row, codes)
        if codes and any(c in "DENX" and c not in [s["code"] for s in shifts] for c in codes):
            raise SystemExit("row %d: codes %s without a shift" % (i, codes))
        cycles.append({
            "id": "c%03d" % i,
            **row,
            "trustKinds": trust_of(row["trust"]),
            "linkCheck": "링크 대응 확인 필요" in row["trust"],
            "partner": "협력" in row["site"] or "협력" in row["company"] or "협력" in row["industry"],
            "codes": codes,
            "orderState": state,
            "weekly": weekly,
            "roster": roster,
            "shifts": shifts,
        })
    return {
        "format": 1,
        "kind": "cycles",
        "version": VERSION,
        "issued": ISSUED,
        "minApp": MIN_APP,
        "source": "공개 자료(기사·채용 공고·노무 사이트·정부 자료·커뮤니티)로 모은 회사별 교대 주기, 2026-10-25. 사실(근무형태·순서·시간)과 출처 링크만",
        "data": {"forms": [{"key": k, "name": n} for k, n in FORMS], "cycles": cycles},
    }


def text(doc):
    return json.dumps(doc, ensure_ascii=False, indent=1) + "\n"


def main():
    doc = build()
    out = text(doc)
    if "--check" in sys.argv:
        if OUT.read_text(encoding="utf-8") != out:
            print("cycles.json differs from a fresh build", file=sys.stderr)
            sys.exit(1)
        return
    OUT.write_text(out, encoding="utf-8")
    cs = doc["data"]["cycles"]
    from collections import Counter
    st = Counter(c["orderState"] for c in cs)
    print("rows", len(cs), "exact", st["exact"], "usual", st["usual"], "none", st["none"],
          "roster", sum(c["roster"] for c in cs), "with shifts", sum(1 for c in cs if c["shifts"]))
    print(Counter(c["formKey"] for c in cs).most_common())


if __name__ == "__main__":
    main()
