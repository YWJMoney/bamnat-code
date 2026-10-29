#!/usr/bin/env python3
"""
[J133-88] 1.33 (사용자 2026-10-29 15:48 "인터넷에서 최대한 폭 넓게 급여에 대한 정보를 ... 검색으로 적용할수 있게"): the 국민연금공단
'국민연금 가입 사업장 내역' file (공공데이터포털 15083277, 이용허락범위 제한 없음) cut into small files the company server reads
when a company is searched: the workplace's name, 시·군·구, 업종, 가입자 수 and the average monthly income it reported.

Only workplaces still in (가입상태 1) with 5 or more members: 5 people paying into the pension is already 5인 이상, the one
fact the pay calculation can take from it. A smaller one says nothing sure (part-timers under 60 hours are not members).

  python3 -I tools/public_firms.py <국민연금 CSV (CP949)> <out dir, e.g. ../remote/firms/v1> [pension rate, default 0.095]

Each file is <hex of the first letter>.tsv, one workplace a line: name, region, 업종 code, members, average 만원.
"""
import csv
import json
import os
import re
import sys
import unicodedata

MIN_MEMBERS = 5
# 2026: 9.5% of 기준소득월액 (연금개혁, 2025년 9%에서 해마다 0.5%p). The notice is both shares together.
RATE = 0.095
SIDO = {
    "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구", "인천광역시": "인천", "광주광역시": "광주", "대전광역시": "대전",
    "울산광역시": "울산", "세종특별자치시": "세종", "경기도": "경기", "강원도": "강원", "강원특별자치도": "강원", "충청북도": "충북",
    "충청남도": "충남", "전라북도": "전북", "전북특별자치도": "전북", "전라남도": "전남", "경상북도": "경북", "경상남도": "경남",
    "제주특별자치도": "제주",
}
MARKS = re.compile(r"\((주|株|유|사|재|합)\)|주식회사|유한회사|유한책임회사|합자회사|합명회사|사단법인|재단법인|\b(co|ltd|inc|corp|llc)\b\.?")


def norm(name: str) -> str:
    """The company server's normName: NFKC, lower case, no 주식회사·(주), letters and digits only."""
    text = MARKS.sub("", unicodedata.normalize("NFKC", name).lower())
    return "".join(ch for ch in text if unicodedata.category(ch)[0] in "LN")


def clean(text: str) -> str:
    return unicodedata.normalize("NFKC", text).replace("\t", " ").replace("\n", " ").strip()


def main(src: str, out: str, rate: float = RATE) -> None:
    os.makedirs(out, exist_ok=True)
    shards: dict[str, list[str]] = {}
    industries: dict[str, str] = {}
    month = ""
    count = 0
    with open(src, encoding="cp949", errors="replace", newline="") as f:
        rows = csv.reader(f)
        head = next(rows)
        col = {name.split(" ")[0]: i for i, name in enumerate(head)}
        for row in rows:
            if row[col["사업장가입상태코드"]] != "1":
                continue
            members = int(row[col["가입자수"]] or 0)
            if members < MIN_MEMBERS:
                continue
            name = clean(row[col["사업장명"]])
            key = norm(name)
            if not key:
                continue
            month = month or row[col["자료생성년월"]]
            words = clean(row[col["사업장지번상세주소"]] or row[col["사업장도로명상세주소"]]).split(" ")
            region = " ".join([SIDO.get(words[0], words[0])] + words[1:2]) if words and words[0] else ""
            code = row[col["사업장업종코드"]].strip()
            industries.setdefault(code, clean(row[col["사업장업종코드명"]]))
            billed = int(row[col["당월고지금액"]] or 0)
            average = round(billed / members / rate / 10_000)
            shards.setdefault(f"{ord(key[0]):04x}", []).append(f"{name}\t{region}\t{code}\t{members}\t{average}")
            count += 1
    for old in os.listdir(out):
        if old.endswith(".tsv"):
            os.remove(os.path.join(out, old))
    for shard, lines in shards.items():
        lines.sort(key=lambda l: -int(l.split("\t")[3]))
        with open(os.path.join(out, f"{shard}.tsv"), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")
    with open(os.path.join(out, "industries.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(f"{c}\t{n}" for c, n in sorted(industries.items())) + "\n")
    meta = {
        "asOf": month, "count": count, "minMembers": MIN_MEMBERS, "rate": rate,
        "source": "국민연금공단 국민연금 가입 사업장 내역 (공공데이터포털, 이용허락범위 제한 없음)",
        "url": "https://www.data.go.kr/data/15083277/fileData.do",
    }
    with open(os.path.join(out, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print(f"{count} workplaces, {len(shards)} files, as of {month}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else RATE)
