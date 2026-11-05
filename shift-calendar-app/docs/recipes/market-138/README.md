# 1.38 장터 첫 가계부 틀 [LEDH-18]

`ledger-frames.json`: 자취 첫 달, 야간 근무자, 배달 라이더 장부, 1인 카페 장부. 금액·기록은 담기지 않고 예산·봉투 금액은 넣는 사람이 정해요(InputExtract).

- 검사: `cd server/company-worker && node tools/market-seed.mjs --set 138 --check`
- 묶음 파일: `node tools/market-seed.mjs --set 138` → `docs/recipes/market-138.json` (관리자 앱 › 마을 장터 › 출시 대기에 통째로, 1.38 앱(52)부터 보여요)
- 실서버에는 사용자가 확인한 뒤 넣어요.
