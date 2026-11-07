# Play 스토어 스크린샷 만들기

1.38에서 처음 만들었어요(사용자 2026-11-07). 1.37 Play 그림과 같은 모양(남색·크림 바탕, 위 이름표, 금빛 강조 제목, 앱 화면을 확대한 카드, 금테로 들어 올린 핵심 부분, 별·금빛 궤도 선)이에요. 앱 화면은 Robolectric으로 찍고, 나머지는 Playwright로 얹어요.

1. 앱 화면: `StoreScreenshotTest`를 **시험 하나씩** 돌려요(한꺼번에 돌리면 그림 엔진이 멈출 수 있어요). 근무표 읽기 화면은 `UScreenshotTest.setupWhichLine`(`819_u_setup_which_line.png`).
   ```bash
   export BAMNAT_STORE_SHOTS=1   # 없으면 시험이 건너뛰어져요(전체 시험에서는 돌지 않게)
   for t in calendarAllowed calendarFlow shiftKinds payReal team payrollNoSales memos; do
     bash gradlew :app:testDebugUnitTest --tests "com.bamnat.app.screenshots.StoreScreenshotTest.$t" -Pbamnat.robolectricRepo=<미러>
   done
   ```
   결과는 `app/build/screenshots/store_*.png`. 모두 시험용 기록(가짜 이름·금액)이에요.
2. 이 폴더를 작업 폴더로 복사하고 `screens/`에 위 사진을, `fonts/`에 Pretendard(OFL) woff2(Medium·SemiBold·Bold·ExtraBold, `cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/web/static/woff2/`)를 넣어요.
3. `python3 build.py` → `out/play/01-대표.png` … `08-메모.png`(1080×1920, 투명 없는 PNG)와 `out/play/feature.png`(그래픽 이미지 1024×500). 한 장만 다시 그릴 때는 `node compose.js panels.json 05-월급`.

## panels.json

| 칸 | 뜻 |
|---|---|
| `theme` | `night`(남색) 또는 `day`(크림). 번갈아 써요 |
| `label`, `title`, `sub` | 이름표, 제목(줄은 `\n`, 금빛 글자는 `<g>…</g>`), 작은 설명 |
| `image`, `main.crop` | 앱 화면과 그중 보일 부분 `[x, y, 폭, 높이]`, `main.width`·`main.top`은 카드 크기·위치 |
| `pops[]` | 금테로 들어 올릴 부분. `inplace: true`면 카드의 같은 자리에서 `scale`배로 떠올라요. `tag`는 금빛 꼬리표 |
| `badges[]` | 흰 알약 모양 설명(`text`, `left`, `top`) |

제목은 기능 이름보다 **사용자가 겪는 순간**으로 써요(예: '근무표 나오는 날, 찍으면 한 달 끝'). 첫 장은 근무자·팀 리더·사장님 세 사람을 다 불러요. 지킬 것은 `docs/store-listing.md`와 같아요('광고 없음'·'1위'·'진단'·회사 이름·다른 앱 이름 쓰지 않기).
