# 공개 사이트 검색용 페이지 만들기 (bamnat.app)

사용자 결정(2026-11-07 마케팅): 사용자가 커뮤니티에 직접 글을 올리지 않고, 검색(네이버·구글)으로 밤낮을 만나게 하는 무료 계산기·근무 달력 페이지를 공개 저장소 `YWJMoney/-`에 둬요. 페이지 코드는 공개 저장소에, 만드는 도구는 여기에 둬요.

- `public_pages.py <YWJMoney/- 폴더>`: `calc/`(주휴·알바 월급·야간·일당·실수령액·연차·퇴직금), `holiday/<연도>/`, `shift/`, `shift/p/<순서>/`, `sitemap.xml`을 다시 만들어요. 그 저장소의 `remote/v1/`(rates·taxtable·holidays·presets)을 읽으니 **원격 자료를 고친 뒤에 다시 돌리면** 새 해 공휴일·새 순서가 페이지로 늘어나요. **회사별 교대 방식(`cycles.json`)은 웹에 쪽으로 만들지 않아요**(사용자 2026-11-11: 앱 자료는 웹에 보이지 않게, 앱에 들어와 쓰게). 2026-11-11에 `shift/firms/` 162쪽과 `/shift/`의 회사 검색을 내렸어요. `calc/calc.css`·`calc/calc.js`·`assets/`·`robots.txt`는 손으로 고쳐요(`index.html`은 `home_page.py`가 만들어요).
- `print_pages.py <YWJMoney/- 폴더>` (public_pages.py 다음에, 2026-11-08 3차): 인쇄용 연간 교대 달력 `shift/print/`·`shift/print/<순서>/`(A4 가로 한 장, 공휴일·야간 횟수)과 근무표 양식 `form/`·`form/files/*.xlsx`(듀티표·조별·알바 주간·현장 출역표, openpyxl). 기존 쪽에 링크를 붙이고 sitemap에 더해요. 해가 바뀌면 `holidays.json`과 연도 칩(2026·2027)을 같이 고쳐요.
- `home_page.py <YWJMoney/- 폴더>` (웹 2판, 2026-11-10 사용자 '최정상의 인터랙션을 가지는 대표 페이지'): 첫 화면 `index.html`을 `home.html` 틀로 만들어요. 움직임과 모양은 공개 저장소의 `assets/home.js`·`assets/home.css`, 설치 QR은 `home-qr.svg`(Play 링크 `utm_medium=qr`). 세 도구를 `public_pages.py` → `print_pages.py` → `home_page.py` 차례로 돌려요(`form/files/*.xlsx`는 날짜만 바뀌니 내용이 같으면 되돌려요).
- 웹 2판 디자인 체계: 로고 다이얼의 남색 `#15192b`·아이보리 `#f2ece1`·금빛 `#c2a36b`, 글꼴 Pretendard(OFL, `assets/fonts/`에 동적 subset을 두고 바깥 CDN을 쓰지 않음). 모든 쪽이 `calc/calc.css`(토큰·머리·바닥·카드·결과·달력)와 `calc/calc.js`(머리 띠·메뉴·결과 숫자 움직임·나타나기)를 함께 써요. 머리·바닥은 `public_pages.py`의 `site_header()`·`site_footer()` 하나뿐이고, 사업자 정보·처리방침·약관·광장 운영 규칙 링크는 바닥에 늘 있어요. 앱으로 이어 가기 카드의 화면은 `cta_shot()`이 고르고 그림은 `assets/app/<번호>-<이름>-360|720.webp`(스토어 사진에서 만듦).
- 방문 출처: 커뮤니티 글처럼 `bamnat.app/?utm_source=…&utm_campaign=…`로 들어오면 그 방문 동안(sessionStorage) Play 링크 referrer의 출처로 이어 붙여요. 서버로 보내는 것은 없어요.
- `og_images.js <YWJMoney/- 폴더>`: 카톡·SNS 미리보기 그림 `og/calc|shift|holiday.png`(1200×630).
- 지킬 것: 계산은 앱 `core`의 `TakeHome`·`WithholdingTable`과 같은 방식(10원 미만 버림, 장기요양 = 건강보험료 × 장기요양률 ÷ 건강보험률). 입력값은 보내지 않음, 분석 도구 없음. 앱 받기 링크 출처는 `utm_source=bamnat_web`.
- 고친 뒤: 휴대폰 폭 360·320px과 데스크톱 1280px, 밝은·어두운 모드에서 가로 넘침·콘솔 오류가 없는지 Playwright로 모든 페이지를 봐요.
- 계획·성과: 프로젝트 파일 `마케팅/사용자-모으기-계획.md`.
- `indexnow.py <YWJMoney/- 폴더> [경로 ...]` (2026-11-11): 공개 저장소 PR을 main에 합쳐 bamnat.app에 나간 뒤 돌려서 바뀐 쪽을 네이버·빙(IndexNow)에 바로 알려요. 열쇠 파일은 공개 저장소 맨 위 `<열쇠>.txt`. 휴대폰 아래 받기 띠(`.dock`)는 `calc/calc.css`·`calc.js`에 있고 모든 기능 쪽이 써요(첫 화면은 `assets/home.js`).
