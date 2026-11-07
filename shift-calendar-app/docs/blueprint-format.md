# 부품 설계도 형식 1판 (BLU, 개발계획 861~866)

작성: 2026-11-01, 갈래 A '스튜디오 부품 설계도와 그리기 엔진'. 정본 계획은 프로젝트 파일 `계획/1.36-스튜디오-완전자유도-개발계획.md`예요.

앱 화면을 **부품 나무**로 나누고, 부품마다 **같은 스타일 칸**(채우기·선·모서리·그림자·여백·글자·투명도·보이기)과 **자동 배치 칸**(방향·간격·정렬·순서·늘어남)을 값으로 담아요. 레시피 안에서는 조각 하나(`parts`, '부품 꾸미기')예요.

- core: `core/src/main/kotlin/com/bamnat/core/recipe/Blueprint.kt` (`BlueprintParts`·`PartStyle`·`Blueprint`)
- 서버: `server/company-worker/src/recipes.js`의 `RECIPE_PIECES.parts`와 `PART_IDS`
- 같은 시험 사례: `docs/recipe-cases.json` (`RecipeCasesTest` ↔ `recipes.test.mjs`)

## 1. 지킬 원칙

1. **값만 담아요(D106).** 부품·스타일·배치는 모두 앱이 읽는 값이에요. 수식은 갈래 D(RUL-02)가 넣었어요: 스타일 칸 값 자리의 `{"fx":"…"}`와 줄의 `when` 조건(아래 5항). 수식 언어는 앱 함수만 쓰는 닫힌 언어예요(`core/formula/Formula.kt`, 서버 `src/formula.js`, 공통 사례 `docs/formula-cases.json`).
2. **데이터는 꾸밈이 못 바꿔요.** 날짜·근무 같은 데이터 부품은 `hide`를 켜도 TalkBack에는 남아요(`mark:none`과 같아요).
3. **옛 조각 위에 얹어요.** 화면은 먼저 옛 조각(`calTheme`·`calView`·`screenLook`·`memoLook`·`moneyLook`·`widget`)이 그리고, 설계도는 **적은 칸만** 덮어써요. 그래서 옛 테마 8계열과 장터 작품은 설계도가 없으면 그대로 보여요.
4. **덧붙이기만 하면 판을 올리지 않아요.** 새 칸·새 부품 id·새 상태는 선택 칸으로 더하면 되고, 옛 앱은 모르는 칸을 버려요(조각 사전 규칙). 뜻이 바뀌는 변경만 갈래 A에 알려 판을 올려요.

## 2. 레시피 안의 모양

```json
{"kind":"parts","parts":[
  {"id":"cal.head","fill":4280295456,"r":12,"pad":8},
  {"id":"cal.head.title","font":"serif","size":20,"weight":700,"ink":{"token":"포인트"}},
  {"id":"cal.cell","on":"role.night","line":"solid","lineW":1.5,"lineC":4294198070},
  {"id":"cal.cell","dir":"col","order":"mark,date,memo","gap":2,"align":"center"},
  {"id":"cal.cell.date","on":"today","fill":4294940928,"rTL":8,"rBR":8}
]}
```

- 한 줄 = 부품 하나(`id`)의 한 상태(`on`, 없으면 기본). 같은 (`id`, `on`)은 한 번만(`dup`).
- 최대 96줄, 조각 하나. 레시피 전체 한도(32KB·깊이 8)는 그대로예요.

## 3. 부품 id (1판)

부모는 마지막 `.` 앞이에요. `cal`·`memo`·`money`·`pay`의 부모는 `app`이고, `widget`은 따로예요(홈 화면이라 앱 글자를 물려받지 않아요).

| 무리 | id | 무엇 |
|---|---|---|
| 앱 | `app` | 앱 전체: 채우기 = 화면 바탕, 글자 = 앱 기본 글자 |
| | `app.bar` · `app.bar.title` | 위 막대와 제목 |
| | `app.tabs` · `app.tab` | 아래 탭 막대와 탭 하나(`on: sel` = 고른 탭) |
| | `app.button` · `app.chip` · `app.card` · `app.sheet` | 주 단추, 칩, 카드, 아래에서 올라오는 창 |
| 달력 | `cal` | 달력 한 달 덩어리 |
| | `cal.head` · `cal.head.title` · `cal.head.nav` · `cal.head.today` | 머리, 달 이름, 이동 단추, 오늘 단추 |
| | `cal.week` · `cal.week.day` | 요일 줄, 요일 글자 하나(`on: sat`·`sun`) |
| | `cal.grid` | 칸들을 담는 판(칸 사이 간격·줄) |
| | `cal.cell` | 날짜 칸 하나 |
| | `cal.cell.date` · `cal.cell.mark` | 칸 속 날짜 숫자, 근무 표시 |
| | `cal.cell.memo` · `cal.cell.money` · `cal.cell.hours` · `cal.cell.spend` · `cal.cell.lunar` · `cal.cell.holiday` | 칸 속 줄: 메모, 번 돈, 근무 시간, 쓴 돈, 음력, 공휴일 이름 |
| | `cal.foot` | 달력 아래 요약 |
| 메모장 | `memo.head` · `memo.tags` · `memo.card` · `memo.card.title` · `memo.card.body` · `memo.card.date` | 머리, 꼬리표 줄, 카드와 카드 속 제목·내용·날짜 |
| 메모 쓰기 (1.38) | `memo.page` · `memo.page.rule` · `memo.photo` | 메모 쓰기 종이(바탕 색·글자), 종이 줄(색·굵기·실선/점선/점, 선을 켜면 종이를 안 고른 메모도 줄 노트), 메모 속 사진(모서리·테두리·그림자·틀) [MPH-06] |
| 가계부 | `money.head` · `money.day` · `money.row` · `money.amount` | 머리, 날짜 머리, 한 줄, 금액(`on: in`·`out`) |
| 급여 | `pay.head` · `pay.total` · `pay.row` | 머리, 합계, 한 줄 |
| 위젯 | `widget` · `widget.head` · `widget.cell` · `widget.task` · `widget.task.check` · `widget.task.add` | 위젯 바탕, 머리, 칸, 할 일 한 줄·동그라미·＋ 단추(1.39) |

갈래가 새 부품을 더할 때(예: E의 메모 보드 `memo.board.col`, 달력 판 `cal.band`)는 core `BlueprintParts.IDS`와 서버 `PART_IDS`를 같은 커밋에서 고치고, `CHILDREN`(순서에 쓸 아이 이름)도 함께 적어요.

## 4. 칸 (모두 선택)

**상태 `on`**: `today`(오늘) · `sat` · `sun` · `holiday`(공휴일) · `dim`(다른 달) · `sel`(고른 것) · `role.day` · `role.eve` · `role.night` · `role.long` · `role.off` · `role.other`(근무 종류, `ShiftRole`) · `leave`(연차·반차) · `extra`(잔업·특근·대근이 있는 날) · `cover`(대근) · `edited`(직접 바꾼 날) · `in`·`out`(수입·지출)
- **1.39 [LINK-01] 근무 입력 그대로**: 하루의 근무 종류와 근무 입력 상태는 core `schedule/DayWork` 한 곳에서 정해요. 쉬는 날(휴·비 같은 쉬는 근무 종류)에 넣은 대근은 대신한 근무(야간 대근이면 `role.night`), 특근만 있는 날은 그 특근의 시각으로 정한 근무(`role.day` 등)예요. 종일 연차·결근은 `role.off`. 달력 칸, 근무 띠·타임라인·주 판의 하루, 위젯 칸, 꾸밈 층(`on`), 수식 값이 모두 같은 판단을 써요. 1.38 이하 앱은 새 상태 줄을 버려요(덧붙이기 규칙).
- 여러 상태가 맞으면 이 차례로 덮어요: 기본 → `role.*` → `leave`·`extra`·`cover`·`edited` → `in`·`out` → `sat`·`sun` → `holiday` → `dim` → `today` → **`when` 줄(조건부 꾸미기)** → `sel`.
- **`when`(RUL-02)**: 줄에 수식 조건을 달면 그날 값(근무·메모·꼬리표·지출 등, `FormulaName`)으로 맞을 때만 덮어요. 예: `{"id":"cal.cell","when":"근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\")","line":"solid","lineC":"빨강"}`. 같은 줄 열쇠는 (`id`, `on`, `when`). 그날 값이 없는 화면(그날이 아닌 곳)에서는 `when` 줄을 건너뛰어요. 앱은 `blueprint.style(id, states, LocalDayFacts.current?.scope(date))`로 불러요.

**스타일**

| 칸 | 값 | 뜻 |
|---|---|---|
| `fill` · `fillA` | 색 · 0~100 | 채우기 색과 진하기(%) |
| `paint` | 갈래 B의 `Fill` 글(`lg.…`·`pt.…`·`gl.…` 등) | 그라데이션·무늬·질감·유리·셰이더. 있으면 `fill`보다 이겨요(갈래 B가 더해요) |
| `line` · `lineW` · `lineC` · `lineA` | `none`·`solid`·`dash`·`dot`·`double` · 0~8dp(소수 1자리) · 색 · 0~100 | 테두리 |
| `r` · `rTL` · `rTR` · `rBR` · `rBL` | 0~48dp | 모서리(모두, 네 귀퉁이 따로가 이김) |
| `shadow` · `shadowC` | 0~24dp · 색 | 그림자 높이와 색 |
| `pad` · `padX` · `padY` | 0~32dp | 안 여백(모두, 가로·세로가 이김) |
| `out` | 0~32dp | 바깥 간격 |
| `font` | 글꼴 id(`^[a-z][a-z0-9-]{1,39}$`) | 1판: `sans`·`serif`·`mono`·`hand`·`round`. 모르는 id는 `sans`로 그려요. 글꼴 목록은 갈래 B 몫 |
| `size` | 6~96sp(소수 1자리) | 글자 크기. 큰 글자 설정은 그대로 곱해요 |
| `weight` | 100~900 | 굵기(100 단위로 반올림) |
| `ink` | 색 | 글자 색 |
| `track` | -5~30 | 자간(글자 크기의 %) |
| `leading` | 80~300 | 줄 간격(글자 크기의 %) |
| `stroke` · `strokeC` | 0~4dp · 색 | 글자 외곽선 |
| `italic` | 참·거짓 | 기울임 |
| `tshadow` · `tshadowC` | 0~12dp(소수 1자리) · 색 | 글자 그림자 흐림과 색(색을 비우면 반투명 검정). 1.36 BLU-08 |
| `tshadowX` · `tshadowY` | -12~12dp(소수 1자리) | 글자 그림자 자리. 비우면 흐림의 반만큼 오른쪽·아래. 둘 다 0이면 둘레가 빛나요 |
| `deco` | `none`·`under`·`strike`·`both` | 밑줄·가운데 줄. 1.36 BLU-08 |
| `talign` | `start`·`center`·`end` | 글자 정렬 |
| `lines` | 1~10 | 글자 줄 수 한도 |
| `alpha` | 0~100 | 부품 전체 투명도 |
| `hide` | 참·거짓 | 숨기기(데이터 부품은 TalkBack에 남아요) |

글자 칸(`font`·`size`·`weight`·`ink`·`track`·`leading`·`stroke`·`strokeC`·`italic`·`tshadow`·`tshadowC`·`tshadowX`·`tshadowY`·`deco`)은 비어 있으면 **부모에게서 물려받아요**(`cal.cell.date` → `cal.cell` → `cal` → `app`). 나머지 칸은 물려받지 않아요.

**배치 (부품을 담는 부품)**

| 칸 | 값 | 뜻 |
|---|---|---|
| `dir` | `row`·`col`·`stack` | 아이를 가로로, 세로로, 겹쳐서 |
| `gap` | 0~32dp | 아이 사이 간격 |
| `align` | `start`·`center`·`end`·`stretch` | 가로지르는 쪽 정렬 |
| `justify` | `start`·`center`·`end`·`between`·`around` | 놓이는 쪽 정렬 |
| `order` | 아이 이름을 쉼표로(예: `mark,date,memo`) | 아이 차례. 빠진 아이는 뒤에 원래 차례로. 그 부품의 아이가 아니거나 두 번이면 거절(`order`) |
| `place` | `tl`·`tc`·`tr`·`ml`·`mc`·`mr`·`bl`·`bc`·`br` | 겹침(`stack`) 부모 안에서 이 부품의 자리 |
| `grow` | 0~10 | 남는 자리를 나눠 갖는 몫 |
| `w` · `h` | 0~600dp | 크기(0 = 저절로) |

**색 값**은 다른 조각과 같아요: ARGB 숫자, `{"token":"이름"}`, 받는 사람 칸 `{"input":"i3"}`(색 칸).

## 5. 갈래별로 얹는 자리

| 갈래 | 이 형식에 더하는 것 | 방법 |
|---|---|---|
| B 꾸미기 재료 | 채우기 종류, 글꼴 id, 사진 조정, 움직임 | `paint`(Fill 글, `fill`보다 이김)·`linePaint`(선 채우기)·`photo`(PhotoAdjust 글)·`motion` 같은 새 선택 칸을 `BlueprintParts.STYLE_FIELDS`·서버 `PART_STYLE`에 더하고, `PartStyle`과 앱 그리기(`ui/parts/PartPaint.kt`)를 넓혀요. 글꼴 id는 이미 `sans`·카탈로그 id·`u`+16자리를 받아요 |
| C 꾸밈 층 | 도형·스티커·글상자 | 따로 조각 `decor`. 붙는 곳(`anchor`)은 **이 표의 부품 id**, 날짜·근무 조건은 이 문서의 상태 이름(`today`·`role.night`·`sat`)을 같이 써요. 꾸밈 하나의 스타일 칸 이름도 4항과 같게 해요 |
| C 고급 편집기·레이어 | 부품 나무 보기 | `BlueprintParts.IDS`·`parentOf`·`CHILDREN`·`LABELS`를 그대로 써요 |
| D 수식·규칙 (넣음) | 스타일 칸 수식·`when` 줄 | 값 자리에 `{"fx":"…"}`(코덱 `Ref.Formula`), 실패하거나 칸이 못 받는 값이면 그 칸만 아래 줄대로. 조건부 꾸미기는 `when` 줄. 단추·규칙은 따로 `logic` 조각 |
| E 탭 자유도 | 새 판(주·목록·타임라인·근무 띠·메모 보드·표) | 새 부품 id와 `CHILDREN`을 3항 방식으로 더해요 |
| F 사용자 부품·리믹스 | 부품 하나 가져오기 | 설계도에서 한 `id`(와 아이들)의 줄만 떼어 옮겨요(`Blueprint.subtree`) |

## 6. 옛 조각 옮겨 읽기

`Blueprint.fromLegacy(calTheme, calView, …)`이 옛 조각을 같은 뜻의 부품 줄로 바꿔요. 스튜디오의 부품 보기가 이 값을 '지금 모습'으로 보여 주고, 옛 레시피를 부품으로 고칠 때 시작점이 돼요. 그리기는 3항대로 옛 조각 그대로 + 설계도 덮어쓰기라, 옮겨 읽기가 화면을 바꾸는 일은 없어요.

## 8. 부품 하나 올리기·담기·얹기 (BLU-07, 사용자 2026-11-01)

- **단위**: 설계도 id를 두 번째 이름까지 자른 것(`cal.head.title` → `cal.head`, `app` → `app`). core `BlueprintParts.unitOf/UNITS`, 서버 `partUnit(id)`.
- **앱 id**: 담기·얹기·입어 보기에서 부품 하나는 `part:<단위>`(`LookParts.UNIT`). `LookParts.byId`가 그때그때 만들고, `LookParts.of(r)`가 부품을 하나씩 나열해요. 탭 단위 '달력 부품' 등(`cal.parts` …)은 바꾼 곳·탭 처음대로에만 써요.
- **부품 작품**(장터 '부품' 구간, 서버 scope `part`): 조각이 하나뿐이고 그 조각 전체가 한 단위예요. core `LookParts.partUnit(r)`, 서버 `partUnitOf(body)`가 같은 규칙으로 앱 id를 돌려줘요: 설계도 `part:cal.head`, 갈래 E의 판 `cal.board`·`memo.view`·`money.board`(tabView 조각이 한 탭 몫일 때).
- **올리기**: 스튜디오 › 부품 › "‘달력 머리’ 부품만 장터에 올리기"(`StudioViewModel.uploadPart`). 장터 구간·목록 필터(`scope=part&unit=`)는 갈래 F.
- 서버 `tabsOf`는 parts 줄을 첫 이름으로 화면에 넣어요(cal→calendar, memo, money, pay, app, widget).

## 9. 누락 점검 보완 (BLU-08~12, 사용자 2026-11-01 "1.36에 넣어서 누락 없이")

**9-1. 글자 그림자·밑줄 (BLU-08, N11)**: 4항의 `tshadow`·`tshadowC`·`tshadowX`·`tshadowY`·`deco`. 글자 칸이라 물려받아요. 앱은 `PartStyle.text()`가 그려요(그림자는 dp를 휴대폰 보통 밀도 2.6으로 px로 바꿔요).

**9-2. 미리 보기에서 누른 부품 (BLU-09, N4)**: 스튜디오에서 '부품' 도구가 열려 있으면 미리 보기를 누른 곳의 가장 안쪽 부품이 열려요. 같은 곳을 다시 누르면 그 바깥 부품, 가장 바깥에서 다시 누르면 가장 안쪽으로 돌아와요. 고른 부품은 분홍 점선으로 짚어요.
- 장치: `ui/parts/PartSpot.kt`. 그리는 쪽은 `Modifier.partSpot(id)`(Composable 안에서는 `partSpotModifier(id)`)만 달아요. 스튜디오 밖에서는 아무 일도 하지 않아요.
- 지금 짚이는 곳: 달력 머리·달 이름(예시 제목), 요일 줄·요일, 날짜 칸과 날짜·근무 표시·칸 속 줄, 탭 막대·탭, 카드, 메모 카드·제목·내용·날짜, 가계부 날짜 머리·한 줄·금액, 급여 머리·합계·한 줄, 위젯 판·머리·칸.

**9-3. 위젯 부품 (BLU-10, N2)**: 홈 화면 위젯(Glance)이 그려요(`widget/WidgetParts.kt`).
- `widget`: 판 전체. 채우기·진하기는 위젯 모양의 바탕색·투명도보다 이기고, 모서리·안 여백·글자 색도 이겨요. 글꼴·기울임·밑줄은 위젯의 모든 글자에 들어가요.
- `widget.head`: 달 위젯의 달 이름 줄, 주 위젯의 '이번 주 근무'·'다음 주', 오늘 위젯의 날짜 줄. 상태 `holiday`.
- `widget.cell`: 달 위젯의 날짜 칸, 주 위젯의 하루, 오늘 위젯의 오늘·내일 줄. 달력 칸과 같은 상태(오늘·토·일·공휴일·다른 달·근무 종류).
- 1.39 [TASK-07] 할 일 위젯: `widget.task`(할 일 한 줄, 상태 `today`·`late` 늦은 할 일·`done` 끝낸 할 일), `widget.task.check`(줄 앞 동그라미 ○/✓, 상태 `late`·`done`), `widget.task.add`(머리의 ＋ 단추). 머리 줄은 `widget.head`. 상태 `late`·`done`은 1.39에 새로 생겼고 상태 순서에서 `today` 다음, `sel` 앞이에요(끝냄이 늦음 위에 덮여요). 1.38 이하 앱은 이 부품·상태를 모르니 이 줄이 든 작품은 1.39부터 읽어요.
- 홈 화면은 런처가 그려서 선·그림자·글자 테두리·글자 그림자·자간은 위젯에서 그리지 않아요. 굵기는 보통(~400)·중간(500)·굵게(600~)로, 모서리는 안드로이드 12부터 보여요.
- 스튜디오 '위젯' 영역에 '부품' 도구가 있고, 위젯 예시도 부품대로 그려요. 위젯 편집 화면은 갈래 C가 이 위에 만들어요.

**9-4. 급여 부품 (BLU-11, N3)**: `pay.head`(일 이름·달 넘기기 줄), `pay.total`(예상 급여 카드: 급여 모양의 합계 카드 색보다 이겨요), `pay.row`(항목 한 줄, 상태 `in` 더하는 항목·`out` 빼는 항목). 스튜디오 '급여' 영역에 '부품' 도구가 있고 급여 예시도 부품대로 그려요.

**9-5. 어두운 화면 (BLU-12)**: 상태 `dark`. 부품이 묻는 상태가 아니라, 화면이 어두우면 그 부품의 기본 줄(상태 없음) 위에 한 번 덮이고 밝으면 버려져요(`Blueprint.forDark`). 앱 테마·스튜디오 예시·위젯이 각자의 밝기로 접어요.
- 토큰(`{"token": …}`)을 쓴 상태 없는 줄은 받는 휴대폰에서 `on: dark` 줄이 저절로 생겨 토큰의 어두운 색을 써요(`RecipeResolve.piece`). 만든 분이 직접 쓴 `dark` 줄이 이겨요.
- 다른 상태가 있는 줄(예: `today`)의 토큰은 밝은 색 그대로예요(상태와 어두운 화면을 한 줄에 함께 담지 않아요).
- 스튜디오 부품 도구의 상태 칩 맨 끝 '어두운 화면'에서 어두운 화면 색을 직접 정해요.

**단추 모서리**: `app.button` 모서리는 갈래 B의 앱 단추(`ui/components/AppButton.kt`, B 커밋 4e32c995)가 따라요. 합치면 A에서 따로 할 것은 없어요.
