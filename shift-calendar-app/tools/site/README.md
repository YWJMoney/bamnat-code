# 공개 사이트 bamnat.app 만들기

사용자 결정(2026-11-07 마케팅): 검색(네이버·구글)으로 밤낮을 만나게 하는 무료 계산기·근무 달력 쪽을 공개 저장소 `YWJMoney/-`에 둬요.
**정본은 이 폴더**예요. 공개 저장소에는 여기서 만든 결과(정적 파일)만 나가고, 코드·설계 문서·시험·내부 주소는 나가지 않아요(2.0.0 총괄 결정).

## 한 번에 만들기 (2.0.0부터)

```bash
python3 tools/site/build.py ../-                 # 공개 저장소를 받아 둔 폴더에 다시 만들기 (사이트맵 날짜는 바뀐 쪽만 오늘로)
python3 tools/site/build.py ../- --og            # 미리보기 그림(og/)까지
python3 tools/site/build.py --check              # 임시 폴더에 만들어 검사·시험만 (CI가 돌려요)
node tools/site/check_pages.js ../- <사진 폴더>   # 모든 쪽을 폭 380·840·1440 × 밝은·어두운으로 열어 점검, 대표 쪽 사진
```

`build.py`는 끝에 **공개로 나가는 파일 목록**(공개 저장소 git 기준 바뀐 것)을 찍고, 아래 중 하나라도 걸리면 실패해요.
- 공개 저장소에 둘 수 없는 파일(맨 위 허용 목록 밖, 어디든 `.py`·`.mjs`·`.kt`·`.md`)
- 공개 파일 안의 내부 주소(`10.0.2.2`·`localhost`), 비밀 이름, 개발 저장소·도구 경로, 추적 번호(`[WEB-12]` 같은 것)
- 공휴일 쪽 밖에서 올해보다 앞선 'NNNN년'(새해에 손으로 고칠 곳이 남았다는 뜻)

소스 주석의 추적 번호는 공개 파일로 옮길 때 지워요(소스에는 남아 개발 기록과 이어져요).

## 무엇이 어디에

| 파일 | 하는 일 |
|---|---|
| `site_common.py` | 오늘·올해(`BAMNAT_SITE_TODAY`로 박을 수 있음), 원격 자료(`remote/v1`), 인쇄·공휴일 해, 머리·바닥, 쪽 틀 `page()`, 사이트맵 |
| `public_pages.py` | 계산기 `calc/`(주휴·알바 월급·야간·일당·실수령·연차·퇴직금), 교대 달력 `shift/`, 순서별 `shift/p/<순서>/`, 공휴일 `holiday/`·`holiday/<해>/` |
| `print_pages.py` | 인쇄 달력 `shift/print/`(우리 조 열두 달, 조 나란히 팀 근무표, 범례 시각, 종이에 한 해 요약), 양식 `form/`·`form/files/*.xlsx`(9mm 칸 빈 양식 인쇄) |
| `r_page.py` | 레시피 링크 받는 쪽 `r/`(noindex, Play 출처 `bamnat_share`) |
| `home_page.py` + `home.html` | 첫 화면 `index.html` |
| `static/` | 손으로 쓰는 정적 파일: `calc/calc.js`(공식·칸·링크), `calc/calc.css`(디자인 체계), `assets/home.js`·`home.css`, `robots.txt`, `shift/manifest.webmanifest`·`shift/sw.js`(내 근무 달력을 홈 화면에, 인터넷 없이도) |
| `og_images.js` | 미리보기 그림 `og/calc·shift·form·recipe·holiday-<해>.png` |
| `app_icons.js` | 홈 화면 아이콘 `static/assets/icon-180·192·512·maskable-512.png`(공개 저장소의 `assets/icon.svg`에서, 바꿀 때만 `node app_icons.js <공개 폴더>`) |
| `calc-cases.json` + `test/` | 웹 계산기와 앱 core가 같은 답을 내는지(`WebCalcParityTest`가 같은 파일을 봐요), 공개 파일 검사 시험 |
| `check_pages.js` | 넘침·콘솔 오류·바깥 요청·숫자 가운데 줄바꿈·접힌 메뉴 초점 점검과 사진 |
| `indexnow.py` | 공개 저장소에 합쳐 나간 뒤 바뀐 쪽을 네이버·빙에 알리기 |

## 지킬 것

- **계산은 앱과 같은 식**: 실수령(4대보험 10원 미만 버림, 장기요양 = 건강보험료 × 장기요양률 ÷ 건강보험률, 건강보험 하한·상한), 3.3%(버림), 일용 세금(날마다·모아서 소액부징수), 주휴, 퇴직금(만 1년은 달력으로), 연차(민법 제160조, 회계연도 비례). 사례를 바꾸면 `calc-cases.json` 하나만 고쳐요.
- **해가 바뀌어도 손으로 고치지 않아요**: 연도·FAQ 숫자·메뉴는 날짜와 `rates.json`·`holidays.json`에서 나와요. 새해 값이 고시되면 `remote/v1`만 고치고 다시 만들어요. 12월 출시 열차 할 일에 '사이트 다시 만들기'를 넣어요.
- 입력값은 보내지 않음, 분석 도구 없음, 바깥 요청 0(글꼴도 `assets/fonts/`).
- Play 링크 출처: 검색 쪽 `utm_source=bamnat_web`(방문 출처는 `utm_term`과 campaign 앞머리에만), 받는 쪽 `bamnat_share`, 쪽 종류는 `utm_medium`(calc·shift·holiday·form·recipe·home).
- 아이폰에서는 받기 단추 대신 'Android 앱이에요' 한 줄과 이 사이트에서 바로 쓰는 곳.
- 회사별 교대 방식(`cycles.json`)은 웹 쪽으로 만들지 않아요(사용자 2026-11-11).
- 디자인 품질 조건 16가지(`docs/디자인-품질-조건.md`)를 폭 380·840·1440, 밝은·어두운으로 봐요. 인쇄는 어두운 모드에서도 흰 종이예요.
