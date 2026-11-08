# 공개 사이트 검색용 페이지 만들기 (bamnat.app)

사용자 결정(2026-11-07 마케팅): 사용자가 커뮤니티에 직접 글을 올리지 않고, 검색(네이버·구글)으로 밤낮을 만나게 하는 무료 계산기·근무 달력 페이지를 공개 저장소 `YWJMoney/-`에 둬요. 페이지 코드는 공개 저장소에, 만드는 도구는 여기에 둬요.

- `public_pages.py <YWJMoney/- 폴더>`: `calc/`(주휴·알바 월급·야간·일당·실수령액·연차·퇴직금), `holiday/<연도>/`, `shift/`, `shift/p/<순서>/`, `shift/firms/<id>/`, `sitemap.xml`을 다시 만들어요. 그 저장소의 `remote/v1/`(rates·taxtable·holidays·presets·cycles)을 읽으니 **원격 자료를 고친 뒤에 다시 돌리면** 새 해 공휴일·새 회사가 페이지로 늘어나요. `calc/calc.css`·`calc/calc.js`·`index.html`·`robots.txt`는 손으로 고쳐요.
- `print_pages.py <YWJMoney/- 폴더>` (public_pages.py 다음에, 2026-11-08 3차): 인쇄용 연간 교대 달력 `shift/print/`·`shift/print/<순서>/`(A4 가로 한 장, 공휴일·야간 횟수)과 근무표 양식 `form/`·`form/files/*.xlsx`(듀티표·조별·알바 주간·현장 출역표, openpyxl). 기존 쪽에 링크를 붙이고 sitemap에 더해요. 해가 바뀌면 `holidays.json`과 연도 칩(2026·2027)을 같이 고쳐요.
- `og_images.js <YWJMoney/- 폴더>`: 카톡·SNS 미리보기 그림 `og/calc|shift|holiday.png`(1200×630).
- 지킬 것: 계산은 앱 `core`의 `TakeHome`·`WithholdingTable`과 같은 방식(10원 미만 버림, 장기요양 = 건강보험료 × 장기요양률 ÷ 건강보험률). 입력값은 보내지 않음, 분석 도구 없음. 앱 받기 링크 출처는 `utm_source=bamnat_web`.
- 고친 뒤: 휴대폰 폭 360·320px에서 가로 넘침·콘솔 오류가 없는지 Playwright로 모든 페이지를 봐요.
- 계획·성과: 프로젝트 파일 `마케팅/사용자-모으기-계획.md`.
