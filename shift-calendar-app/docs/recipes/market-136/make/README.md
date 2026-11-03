# 1.36 장터 출시 작품 생성기

장터 출시 작품(`docs/recipes/market-136/*.json`)은 여기 스크립트가 만들어요. **JSON을 손으로 고치지 말고 여기 작품 줄을 고친 뒤 다시 돌려요.**

```bash
cd shift-calendar-app
node docs/recipes/market-136/make/index.mjs            # 작품 JSON 다시 쓰기 + 유형 말·상호작용 점검(lint.mjs)
cd server/company-worker
node tools/market-seed.mjs --check                     # 서버 cleanRecipe 검사 + 구간 목표
node tools/market-seed.mjs                             # docs/recipes/market-136.json (관리자 앱 출시 대기에 붙여 넣는 묶음) 쓰기
cd ../.. && node docs/recipes/market-136/make/list.mjs # 목록.md
```

| 파일 | 담긴 것 |
|---|---|
| `lib.mjs` | 레시피 틀, 색·부품·꾸밈 도우미 |
| `kit.mjs` | 달력 칸 수식 줄(C, 지금 돎), 날짜 있는 부품 수식 줄(D, 1.36 고침 뒤), 규칙(R), 단추(B), 메모 틀(T) |
| `build-theme.mjs` | 유형별 테마 조립 |
| `themes-*.mjs` | 테마 68 (유형별 48 + 앱 전체 20) |
| `tab-*.mjs` | 기능별 달력·메모·가계부·근무급여·알람·위젯 각 22 |
| `parts.mjs` | 부품 31 (단위 하나씩, 판 셋) |
| `logic.mjs` | 규칙·단추 24 |
| `showcase.mjs` | 보여주기 30: 1.35 보여주기 고친 판 12(제목 끝 '플러스') + 새 작품 18의 제목 |
| `lint.mjs` | 유형에 안 맞는 말(알바·현장·자영업·투자의 회사 말 등), 상호작용 없는 작품, 긴 소개 |
| `list.mjs` | 작품 목록 문서 |

지킬 것
- 작품마다 상호작용 하나 이상(수식·규칙·단추·상태 줄). 투자 작품에 추천·보장 말 없음.
- 설명에 금액 숫자를 쓰지 않아요(서버 글 검사). 1.36 고침이 있어야 도는 줄은 설명에 '고침 뒤'라고 적어요.
- 1.35에 이미 있는 제목은 쓰지 않아요(같은 제목은 그 작품의 새 판이 되어 1.35 앱에서 사라져요).
- 실서버에는 넣지 않아요. 운영자 경로로, 사용자의 마지막 확인 뒤에만(루트 CLAUDE.md 3-1).
