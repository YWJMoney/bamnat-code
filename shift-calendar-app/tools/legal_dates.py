#!/usr/bin/env python3
"""2.0.0 처리방침 3.13·이용약관 1.4 (사용자 2026-11-12 "게시해")와 3.14 (사용자 11-19 '16줄 모두', [RVO-26]): 판마다 게시일 하나로 모든 날짜를 맞춰요.

게시일은 공개 저장소(YWJMoney/-) PR을 합치고 앱 공지를 올린 날이에요. 처리방침 9항대로 7일 줄은 게시일 + 7일,
30일 줄(이용자에게 불리할 수 있는 곳)은 게시일 + 30일에 시행해요. 하루 늦어지면 이 한 줄로 함께 옮겨요:

    python3 tools/legal_dates.py 2026-11-13 --rev 3.13 [--site ../../pub-site] [--notes /mnt/project-files/계획/2.0.0-처리방침-3.13-게시용-문장.md]
    python3 tools/legal_dates.py 2026-11-21 --rev 3.14 [--site …] [--notes …/2.0.0-처리방침-3.14-게시용-문장.md] [--share]

판(--rev, 없으면 가장 새 판 3.14)마다 따로 움직여요. 3.13의 날짜는 <time data-legal="posted|p7|p30">와 REVISION_POSTED,
3.14의 날짜는 <time data-legal="r314-posted|r314-p7|r314-p30">와 REVISION14_POSTED예요. 한 판을 옮겨도 다른 판의 날짜는 그대로이고,
--check는 두 판을 모두 봐요.

바꾸는 곳
- 저장소 맨 위 privacy/index.html, terms/index.html, community/index.html 의 그 판 <time data-legal> (글자와 datetime)
- core PolicyConsent의 그 판 게시일 줄 (7일·30일 시행일은 거기서 계산해요. LegalPagesTest가 페이지와 맞춰 봐요)
- --share (3.14): 공유 서버 share-worker src/index.js 의 POLICY_314_FROM 을 30일 줄 날짜로(배포는 갈래 D). 게시할 때만 써요
- --site: 공개 저장소 checkout 의 privacy/·terms/·community/ 를 이 저장소 것으로 덮어쓰고 sha256을 docs/legal-published.txt에 적어요
  (LegalPagesTest가 이 저장소의 페이지가 공개 저장소에 올린 것과 같은지 봐요)
- --notes: 게시용 문장 파일의 <!-- legal-dates:start --> ~ <!-- legal-dates:end --> 사이(관리자 앱 일정 값과 앱 공지 글)

아무 날짜 없이 부르면 지금 날짜로 맞는지만 보고(--check와 같음) 관리자 앱에 넣을 값을 찍어요.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import re
import shutil
import sys
from pathlib import Path

APP = Path(__file__).resolve().parents[1]
ROOT = APP.parent
PAGES = [ROOT / "privacy/index.html", ROOT / "terms/index.html", ROOT / "community/index.html"]
CONSENT = APP / "core/src/main/kotlin/com/bamnat/core/ops/PolicyConsent.kt"
SHARE = APP / "server/share-worker/src/index.js"
# 판마다: 게시일 줄(PolicyConsent)과 페이지 날짜 표시의 앞붙이. 3.13은 앞붙이 없이(이미 게시한 페이지 그대로).
REVS = {
    "3.13": {"const": "REVISION_POSTED", "prefix": ""},
    "3.14": {"const": "REVISION14_POSTED", "prefix": "r314-"},
}
NEWEST = "3.14"
SHARE_LINE = re.compile(r'(export const POLICY_314_FROM = ")([0-9-]*)(";)')
TIME = re.compile(r'<time data-legal="((?:r\d+-)?)(posted|p7|p30)" datetime="(\d{4}-\d{2}-\d{2})">([^<]*)</time>')
NOTES = re.compile(r"(<!-- legal-dates:start -->\n)(.*?)(<!-- legal-dates:end -->)", re.S)
RECORD = APP / "docs/legal-published.txt"


def posted_line(rev: str) -> re.Pattern:
    return re.compile(r"(val " + REVS[rev]["const"] + r": LocalDate = LocalDate\.of\()(\d{4}), (\d{1,2}), (\d{1,2})(\))")


def days(posted: dt.date) -> dict[str, dt.date]:
    return {"posted": posted, "p7": posted + dt.timedelta(days=7), "p30": posted + dt.timedelta(days=30)}


def ymd(d: dt.date) -> str:
    return f"{d.year}년 {d.month}월 {d.day}일"


def md(d: dt.date) -> str:
    return f"{d.month}월 {d.day}일"


def posted_in_code(rev: str) -> dt.date:
    m = posted_line(rev).search(CONSENT.read_text(encoding="utf-8"))
    if not m:
        sys.exit(f"{CONSENT}: {REVS[rev]['const']} 줄이 없어요")
    return dt.date(int(m.group(2)), int(m.group(3)), int(m.group(4)))


def all_days() -> dict[str, dict[str, dt.date]]:
    """앞붙이 → 그 판의 날짜들. 페이지의 표시 하나하나가 이 가운데 하나여야 해요."""
    return {REVS[r]["prefix"]: days(posted_in_code(r)) for r in REVS}


def retag(text: str, rev: str, when: dict[str, dt.date]) -> str:
    """그 판의 표시만 다시 써요(다른 판의 표시는 그대로)."""
    prefix = REVS[rev]["prefix"]

    def one(m):
        if m.group(1) != prefix:
            return m.group(0)
        d = when[m.group(2)]
        return f'<time data-legal="{prefix}{m.group(2)}" datetime="{d.isoformat()}">{ymd(d)}</time>'
    return TIME.sub(one, text)


def problems(every: dict[str, dict[str, dt.date]]) -> list[str]:
    out = []
    for page in PAGES:
        if not page.exists():
            continue
        tags = TIME.findall(page.read_text(encoding="utf-8"))
        if not tags and page.name == "index.html" and page.parent.name in ("privacy", "terms"):
            out.append(f"{page.relative_to(ROOT)}: 날짜 표시(<time data-legal>)가 없어요")
        for prefix, kind, iso, words in tags:
            when = every.get(prefix)
            if when is None:
                out.append(f"{page.relative_to(ROOT)}: 모르는 판 표시 '{prefix}{kind}'")
                continue
            if iso != when[kind].isoformat() or words != ymd(when[kind]):
                out.append(f"{page.relative_to(ROOT)}: {prefix}{kind} = {iso} '{words}', 게시일로 계산하면 {when[kind].isoformat()} '{ymd(when[kind])}'")
    share = SHARE_LINE.search(SHARE.read_text(encoding="utf-8")) if SHARE.exists() else None
    if share and share.group(2) and share.group(2) != every[REVS["3.14"]["prefix"]]["p30"].isoformat():
        out.append(f"share-worker POLICY_314_FROM = {share.group(2)}, 3.14의 30일 줄은 {every[REVS['3.14']['prefix']]['p30'].isoformat()}")
    return out


def notes_block_313(when: dict[str, dt.date]) -> str:
    """관리자 앱에 넣을 값, 창의 칸 이름 그대로(admin-app.js openFlagPolicy·openNotice). 앱 공지 글은 사용자 말로 짧게.

    총괄 11-12(사용자에게 준 값, 정본 계획/2.0.0-처리방침-3.13-관리자앱에-넣을-값.md): 약관 제3조가 '앱과 이 페이지에 바뀌는 내용과
    시행일'을 알리라고 하니 약관 1.4(위젯 두 번째부터 프로, 참고용 도구, 동의하지 않으면 시행일 전에 해지)도 넣고, 새 공지 창에는
    단추·대상 칸이 없어(HQ-33) 어디서 볼 수 있는지를 마지막 줄에 적어요.
    """
    p, p7, p30 = when["posted"], when["p7"], when["p30"]
    body = (f"{md(p7)}부터: 체험이 끝나기 3일 전에 한 번 더 알려 드리고, 앱 잠금·백업·글꼴 설명을 실제와 맞췄어요.\n"
            f"{md(p30)}부터: 친구 초대·작품 보상은 운영팀이 확인한 뒤 드리고, 모두 합쳐 1년 12개월까지예요. "
            "사용 통계를 켠 분만 ‘안 써 본 기능’ 카드 횟수 4가지를 더 보내요(어떤 기능인지는 빼고).\n"
            f"약관({md(p30)}부터): 위젯은 두 번째부터 프로이고, 투자·세금 어림·수면 도구·사장님 서류는 참고용이라고 적었어요. "
            "동의하지 않으시면 그 전에 구독을 해지할 수 있어요.\n"
            "바뀐 글은 설정 › 개인정보·앱 정보 › 개인정보 처리방침에서 볼 수 있어요.")
    title = "처리방침·이용약관이 바뀌어요"
    # The form's limits: 제목 40, 내용 500, 마지막 날 90일 안.
    assert len(title) <= 40 and len(body) <= 500 and (p30 - p).days <= 90
    return "\n".join([
        f"- 게시일 **{p.isoformat()}** → 7일 줄 시행 **{p7.isoformat()}**, 30일 줄 시행 **{p30.isoformat()}** (이 칸은 `tools/legal_dates.py`가 써요)",
        "",
        "**1. 처리방침 일정**: 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정 › '판 더하기·날짜 고치기'",
        "",
        "| 칸 | 값 |",
        "|---|---|",
        "| 판 | `3.13` |",
        f"| 게시일 | `{p.isoformat()}` |",
        f"| 시행일 | `{p30.isoformat()}` |",
        "| 동의하지 않은 휴대폰에도 시행하는 날(선택) | 비움 |",
        "| 이용자에게 불리한 변경(30일 전에 알려요) | 체크 |",
        "| 이유(필수) | `처리방침 3.13 게시(사용 통계 4가지·보상 상한은 30일 고지)` |",
        "",
        "그리고 '저장'.",
        "",
        "**2. 앱 공지**: 관리자 앱 › 출시 › 앱 공지 › '새 공지'",
        "",
        "| 칸 | 값 |",
        "|---|---|",
        f"| 제목 | `{title}` |",
        "| 내용 | 아래 글을 그대로 붙여 넣기 |",
        "| 알림 / 중요 | **알림** (처음 값 그대로) |",
        f"| 시작 | `{p.isoformat()}` (처음 값 그대로) |",
        f"| 마지막 날 | `{p30.isoformat()}` |",
        "| 앱 버전 번호부터·까지 | 둘 다 비움 |",
        "",
        "내용(그대로 붙여 넣기):",
        "",
        "```",
        *body.split("\n"),
        "```",
        "",
        "그리고 '올리기'. 공지 창에는 단추(링크)를 넣는 칸이 없어서, 어디서 볼 수 있는지를 글 마지막 줄에 넣었어요.",
    ]) + "\n"


def notes_block_314(when: dict[str, dt.date]) -> str:
    """3.14 (사용자 11-19 '16줄 모두'): 관리자 앱 처리방침 일정 3.14와 앱 공지, 칸 이름은 admin-app.js 그대로(3.13과 같은 창).

    처리방침 일정의 3.14 '게시일'도 꼭 넣어요: 광장 방 글 금액은 서버가 3.14 게시일 + 7일부터 받아요(갈래 N, flags.js
    roomMoneyFromPolicy). '시행일'은 30일 줄이라 Condition.Date(policy = "3.14") 깃발이 그날 켜져요. 약관은 바뀌지 않아요.
    """
    p, p7, p30 = when["posted"], when["p7"], when["p30"]
    body = (f"{md(p7)}부터: 회사 정보·일 유형 광장 글에 금액을 적을 수 있어요(계좌번호·연락처는 안 돼요, 광장 운영 규칙 3). "
            "회사에 나누는 근무주기·근무 시간·급여 방식은 하나씩 골라 나눠요.\n"
            f"{md(p30)}부터: 근무표 링크에 ‘확인했어요’ 누른 시각과 ‘영업일’ 같은 이름 한 마디가 담기고, 켠 분만 폰을 바꿀 때 백업을 "
            "저절로 옮겨요. 사용 통계를 켠 분만 일하는 방식별 기능 횟수, 앱이 멈춘 곳 같은 합계를 더 보내요(누구인지 없이).\n"
            "바뀐 글은 설정 › 개인정보·앱 정보 › 개인정보 처리방침에서 볼 수 있어요.")
    title = "개인정보 처리방침이 바뀌어요"
    # The form's limits: 제목 40, 내용 500, 마지막 날 90일 안.
    assert len(title) <= 40 and len(body) <= 500 and (p30 - p).days <= 90, len(body)
    return "\n".join([
        f"- 게시일 **{p.isoformat()}** → 7일 줄 시행 **{p7.isoformat()}**, 30일 줄 시행 **{p30.isoformat()}** (이 칸은 `tools/legal_dates.py --rev 3.14`가 써요)",
        "",
        "**1. 처리방침 일정**: 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정 › '판 더하기·날짜 고치기'",
        "",
        "| 칸 | 값 |",
        "|---|---|",
        "| 판 | `3.14` |",
        f"| 게시일 | `{p.isoformat()}` |",
        f"| 시행일 | `{p30.isoformat()}` |",
        "| 동의하지 않은 휴대폰에도 시행하는 날(선택) | 비움 |",
        "| 이용자에게 불리한 변경(30일 전에 알려요) | 체크 |",
        "| 이유(필수) | `처리방침 3.14 게시(새로 받는 것은 30일 고지, 방 글 금액은 게시일 + 7일)` |",
        "",
        "그리고 '저장'. 3.13 줄은 그대로 둬요(3.13 시행일 2026-12-13).",
        "",
        "**2. 앱 공지**: 관리자 앱 › 출시 › 앱 공지 › '새 공지'",
        "",
        "| 칸 | 값 |",
        "|---|---|",
        f"| 제목 | `{title}` |",
        "| 내용 | 아래 글을 그대로 붙여 넣기 |",
        "| 알림 / 중요 | **알림** (처음 값 그대로) |",
        f"| 시작 | `{p.isoformat()}` (처음 값 그대로) |",
        f"| 마지막 날 | `{p30.isoformat()}` |",
        "| 앱 버전 번호부터·까지 | 둘 다 비움 |",
        "",
        "내용(그대로 붙여 넣기):",
        "",
        "```",
        *body.split("\n"),
        "```",
        "",
        "그리고 '올리기'.",
        "",
        f"**3. 공유 서버**: 갈래 D가 `POLICY_314_FROM = \"{p30.isoformat()}\"`(이 도구의 `--share`가 씀)로 공유 서버를 배포해요. 게시하는 날 함께.",
    ]) + "\n"


NOTES_BLOCKS = {"3.13": notes_block_313, "3.14": notes_block_314}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("posted", nargs="?", help="게시일 YYYY-MM-DD (없으면 지금 값을 확인만)")
    ap.add_argument("--rev", choices=sorted(REVS), default=NEWEST, help=f"옮길 판 (기본 {NEWEST})")
    ap.add_argument("--site", type=Path, help="공개 저장소 checkout (privacy/·terms/·community/를 이 저장소 것으로 덮어씀)")
    ap.add_argument("--notes", type=Path, help="게시용 문장 파일 (legal-dates 표시 사이를 다시 씀)")
    ap.add_argument("--share", action="store_true", help="3.14: 공유 서버 POLICY_314_FROM을 30일 줄 날짜로 (게시할 때만)")
    ap.add_argument("--check", action="store_true", help="고치지 않고 맞는지만")
    a = ap.parse_args()

    if a.posted and not a.check:
        posted = dt.date.fromisoformat(a.posted)
        line = posted_line(a.rev)
        text = CONSENT.read_text(encoding="utf-8")
        CONSENT.write_text(line.sub(lambda m: f"{m.group(1)}{posted.year}, {posted.month}, {posted.day}{m.group(5)}", text, count=1), encoding="utf-8")
        for page in PAGES:
            if page.exists():
                page.write_text(retag(page.read_text(encoding="utf-8"), a.rev, days(posted)), encoding="utf-8")
        # 2.0.0 [RVR-38] 날짜 장부의 서버 사본(dates-built.js)도 같은 게시일로(판마다 게시·7일·30일 줄).
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import dates_ledger
        dates_ledger.write_built()
    if a.share and not a.check:
        if a.rev != "3.14":
            sys.exit("--share는 3.14만 (공유 서버 POLICY_314_FROM)")
        p30 = days(posted_in_code("3.14"))["p30"].isoformat()
        text = SHARE.read_text(encoding="utf-8")
        if not SHARE_LINE.search(text):
            sys.exit(f"{SHARE}: POLICY_314_FROM 줄이 없어요")
        SHARE.write_text(SHARE_LINE.sub(lambda m: m.group(1) + p30 + m.group(3), text, count=1), encoding="utf-8")
        print(f"공유 서버 POLICY_314_FROM = {p30} (배포는 갈래 D)")
    every = all_days()
    bad = problems(every)
    if bad:
        print("\n".join(bad))
        sys.exit(1)
    pages = [p for p in PAGES if p.exists()]
    if a.site and not a.check:
        for page in pages:
            target = a.site / page.relative_to(ROOT)
            shutil.copyfile(page, target)
            print(f"덮어씀: {target}")
    if a.site:
        for page in pages:
            target = a.site / page.relative_to(ROOT)
            if target.read_bytes() != page.read_bytes():
                print(f"다름: {target} (이 저장소 {page.relative_to(ROOT)}와 같아야 해요)")
                sys.exit(1)
        if not a.check:
            # LegalPagesTest.theRepoPagesAreTheOnesPublished: the pages here are the ones sent to the public repository.
            lines = ["# tools/legal_dates.py --site 가 씀: 공개 저장소 YWJMoney/- 에 올린 처리방침·약관·광장 운영 규칙의 sha256 (LegalPagesTest)"]
            lines += [f"{hashlib.sha256(page.read_bytes()).hexdigest()}  {page.relative_to(ROOT).as_posix()}" for page in pages]
            RECORD.write_text("\n".join(lines) + "\n", encoding="utf-8")
            print(f"기록: {RECORD.relative_to(ROOT)}")
    when = every[REVS[a.rev]["prefix"]]
    block = NOTES_BLOCKS[a.rev](when)
    if a.notes and not a.check:
        text = a.notes.read_text(encoding="utf-8")
        if not NOTES.search(text):
            sys.exit(f"{a.notes}: <!-- legal-dates:start --> ~ <!-- legal-dates:end --> 표시가 없어요")
        a.notes.write_text(NOTES.sub(lambda m: m.group(1) + block + m.group(3), text, count=1), encoding="utf-8")
        print(f"고침: {a.notes}")
    for rev in sorted(REVS):
        w = every[REVS[rev]["prefix"]]
        print(f"{rev}: 게시일 {w['posted']} · 7일 줄 {w['p7']} · 30일 줄 {w['p30']}")
    print("페이지·앱 상수 맞음\n")
    print(block)


if __name__ == "__main__":
    main()
