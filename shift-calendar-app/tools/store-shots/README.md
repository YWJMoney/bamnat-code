# Play 스토어 스크린샷 만들기

1.38에서 처음 만들었어요(사용자 2026-11-07). 앱 화면은 Robolectric으로 찍고, 제목·휴대폰 틀은 Playwright로 얹어요.

1. 앱 화면: `StoreScreenshotTest`를 **시험 하나씩** 돌려요(한꺼번에 돌리면 그림 엔진이 멈출 수 있어요). 근무표 읽기 화면은 `UScreenshotTest.setupWhichLine`.
   ```bash
   export BAMNAT_STORE_SHOTS=1   # 없으면 시험이 건너뛰어져요(전체 시험에서는 돌지 않게)
   for t in calendarAllowed firstSetup shiftAlarms sleep payResult team memos; do
     bash gradlew :app:testDebugUnitTest --tests "com.bamnat.app.screenshots.StoreScreenshotTest.$t" -Pbamnat.robolectricRepo=<미러>
   done
   ```
   결과는 `app/build/screenshots/store_*.png`. 모두 시험용 기록(가짜 이름·금액)이에요.
2. 이 폴더를 작업 폴더로 복사하고 `screens/`에 위 사진을, `fonts/`에 Pretendard(OFL) woff2(Regular·Medium·SemiBold·Bold·ExtraBold, `cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/web/static/woff2/`)를 넣어요.
3. `python3 build.py` → `out/01-달력.png` … 1080×1920. 그래픽 이미지는 `node feature.js screens/store_1b_calendar.png screens/store_7_team.png` → `out/feature.png` 1024×500.
4. Play에는 투명 없는 PNG로 올려요(`build.py` 결과를 RGB로 저장).

문구는 `panels.json`. 지킬 것은 `docs/store-listing.md`와 같아요('광고 없음'·'1위'·'진단'·회사 이름 쓰지 않기).
