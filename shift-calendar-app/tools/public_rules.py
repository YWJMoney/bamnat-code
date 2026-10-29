#!/usr/bin/env python3
"""
[J133-89] 1.33 (사용자 2026-10-29 15:48 "공고에 있는것이 있으면 가져오고 ... 최대한 데이터를 확보"): public institutions' own pay
rules (알리오 공공기관 내부규정 '보수규정', 클린아이, 국가법령정보센터), gathered by hand into one table, made into what the app can
show and take one value at a time: each rule line in words with its source, and a company file (bamnat-company, pay part only) that
holds what is one number for everyone (통상시급 기준 시간, a 매월 정액 수당 paid to all at one amount).

  python3 -I tools/public_rules.py <공공기관-보수.csv (UTF-8)> <out dir, e.g. ../remote/firms/v1>

Nothing is guessed: a line with more than one amount (갑종·을종, 직급별) is shown in words only, and a line the table marks
'표 판독 불확실' is never taken into the file.
"""
import csv
import hashlib
import json
import os
import re
import sys
import unicodedata

MARKS = re.compile(r"\((주|株|유|사|재|합)\)|주식회사|유한회사|유한책임회사|합자회사|합명회사|사단법인|재단법인|\b(co|ltd|inc|corp|llc)\b\.?")
MONTHLY = re.compile(r"^월\s*([\d,]+)\s*원$")
HOURS = re.compile(r"(?:1\s*/\s*|÷\s*)(\d{3})(?![\d.])")
SALARY_WORD = re.compile(r"([가-힣]{2,12}(수당|비|금))")


# What people call an institution (코레일, 한수원, SR), so the search finds it by that too.
ALIASES = {
    "한국철도공사": ["코레일", "korail"], "주식회사에스알": ["sr", "srt", "에스알"], "한국수력원자력(주)": ["한수원", "khnp"],
    "인천국제공항공사": ["인천공항", "인국공"], "국민건강보험공단": ["건보", "건강보험공단", "건보공단"], "근로복지공단": ["근복", "근로공단"],
    "한국도로공사": ["도공", "도로공사"], "한국가스공사": ["가스공사"], "서울교통공사": ["서울지하철", "서울메트로"],
    "한국보훈복지의료공단": ["보훈공단", "보훈병원", "중앙보훈병원"], "국민건강보험공단일산병원": ["일산병원"],
    "한국남부발전(주)": ["남부발전"], "한국서부발전(주)": ["서부발전"], "한국중부발전(주)": ["중부발전"], "한국남동발전(주)": ["남동발전"],
    "한국동서발전(주)": ["동서발전"], "공무원(법령)": ["공무원수당", "공무원보수"],
}


def norm(name: str) -> str:
    text = MARKS.sub("", unicodedata.normalize("NFKC", name).lower())
    return "".join(ch for ch in text if unicodedata.category(ch)[0] in "LN")


def item_name(kind: str, calc: str) -> str:
    """The allowance's own word: the heading when it is one ('진료지원수당'), else '위험근무수당' from '위험근무수당 전 임직원 매월'
    (a heading that groups several, '위험·특수근무수당'), else the heading without its notes."""
    head = re.sub(r"\(.*?\)", "", kind).strip()
    if SALARY_WORD.fullmatch(head):
        return head
    m = SALARY_WORD.match(calc.strip())
    if m:
        return m.group(1)
    return re.sub(r"\(.*?\)", "", kind).split("/")[0].strip()[:20]


def item(name: str, amount: int, ordinary: bool) -> dict:
    key = "p" + hashlib.sha1(name.encode()).hexdigest()[:10]
    return {"id": key, "name": name, "amount": {"entries": [], "raise": None}, "cadence": "MONTHLY", "types": [],
            "ordinary": ordinary, "taxable": True, "months": [], "fixedOvertime": 0, "festivals": [], "festivalBefore": False,
            "share": "FIXED", "fixedAmount": amount}


def main(src: str, out: str) -> None:
    orgs: dict[str, dict] = {}
    with open(src, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            name = row["기관명"].strip()
            if not name:
                continue
            org = orgs.setdefault(name, {"name": name, "key": norm(name), "rule": row["규정이름"].strip(), "effective": row["시행일"].strip(),
                                         "url": row["출처URL"].strip(), "lines": [], "hours": None, "items": []})
            # One institution, several documents (보수규정 and its 시행세칙): every name said, the newest date.
            rule, effective = row["규정이름"].strip(), row["시행일"].strip()
            if rule and rule not in org["rule"].split(" · "):
                org["rule"] += " · " + rule
            if effective > org["effective"]:
                org["effective"] = effective
            kind, calc, amount, note = (row[k].strip() for k in ("항목", "계산", "금액", "메모"))
            org["lines"].append({"item": kind, "calc": calc, "amount": amount, "note": note, "doc": rule})
            unsure = "불확실" in note
            if kind == "통상임금 기준 시간" and not unsure:
                m = HOURS.search(calc)
                if m and 100 <= int(m.group(1)) <= 320:
                    org["hours"] = int(m.group(1))
            m = MONTHLY.match(amount)
            if m and not unsure and ("매월" in calc or "전 임직원" in calc):
                n = item_name(kind, calc)
                value = int(m.group(1).replace(",", ""))
                if n and 0 < value <= 10_000_000 and all(i["name"] != n for i in org["items"]):
                    org["items"].append(item(n, value, ordinary=True))
    result = []
    for org in orgs.values():
        # Which document a line is from, said only when there is more than one (조항 numbers differ between them).
        several = " · " in org["rule"]
        for line in org["lines"]:
            doc = line.pop("doc")
            if several and doc:
                line["note"] = f"{doc} {line['note']}".strip()
        pay = {"fiveOrMore": True, "monthlyHours": org["hours"] or 209, "holidayMode": "FULL", "withAmounts": False, "items": org["items"]}
        setup = {"app": "com.bamnat.app", "format": "bamnat-company", "v": 4, "company": org["name"], "shiftTypes": [], "pay": pay}
        result.append({k: org[k] for k in ("name", "key", "rule", "effective", "url", "hours", "lines")} |
                      {"aliases": [norm(a) for a in ALIASES.get(org["name"], [])],
                       "takes": [i["name"] for i in org["items"]], "setup": json.dumps(setup, ensure_ascii=False)})
    meta = {"source": "공공기관 보수규정 (알리오 공공기관 내부규정·클린아이·국가법령정보센터)", "orgs": result}
    with open(os.path.join(out, "rules.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(result)} institutions, {sum(len(o['lines']) for o in result)} lines, "
          f"{sum(len(o['takes']) for o in result)} amounts to take, hours {sum(1 for o in result if o['hours'])}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
