#!/usr/bin/env python3
"""2.0.0 [RVO-01] 출시 APK의 dex에 개발용 글귀가 없는지 봐요(출시 파일 자가 점검, 저장소 맨 위 CLAUDE.md 4항).

    python3 tools/release_dex_check.py app/build/outputs/apk/release/app-release-unsigned.apk

등급 미리 보기(TierPreview.kt)와 예시 요금제는 상수 BuildConfig.DEBUG로만 열려, R8이 출시 빌드에서 지워요. 이 점검은 그것이
정말 지워졌는지 출시 파일로 확인해요(총괄 11-12: 출시 빌드에서 등급을 바꾸는 길이 코드에 남지 않게). 한국어 글귀는 BMP 글자라
dex의 MUTF-8과 UTF-8 바이트가 같아요.
"""
import sys
import zipfile

# 개발용에만 있는 글귀. 하나라도 dex에 있으면 출시 빌드에 개발용 길이 남은 것이에요.
FORBIDDEN = [
    "출시 뒤 화면 미리 보기 · 개발용",   # TierPreview.TIER_PREVIEW_TITLE
    "미리 보기라 결제는 되지 않아요",     # TierPreview.PREVIEW_NO_PAY
    "4,990원(예시)",                    # TierPreview.previewPlans()
    "39,000원(예시)",
    "45,000원(예시)",                   # TierPreview.previewPass()
    "맛보기 처음부터",                   # TierPreviewCard 디버그 단추
    "하루 써 보기 처음부터",
]


def check(apk: str) -> list[str]:
    found = []
    with zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")]
        if not dexes:
            return [f"{apk}: dex가 없어요"]
        blob = b"".join(z.read(n) for n in dexes)
    for words in FORBIDDEN:
        if words.encode("utf-8") in blob:
            found.append(f"출시 dex에 개발용 글귀가 있어요: {words}")
    return found


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    problems = check(argv[1])
    for p in problems:
        print(p)
    print(f"개발용 글귀 {len(FORBIDDEN)}가지 점검, 문제 {len(problems)}개")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
