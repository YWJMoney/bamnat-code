# 1.36 장터 출시 작품

작품 JSON을 이 폴더에 둬요(구간별 파일, 예: `theme-shift.json`, `tab-memo.json`, `logic.json`). 기획은 `docs/plans/1.36-장터-출시작품-기획.md`.
작품은 1.36 모든 갈래 개발이 끝난 뒤에 만들어요(사용자 2026-11-01).

- 검사: `cd server/company-worker && node tools/market-seed.mjs --check`
- 묶음 파일 만들기: `node tools/market-seed.mjs` → `docs/recipes/market-136.json` (관리자 앱 › 마을 장터 › 출시 대기에 통째로 붙여 넣어요)
- PC 로컬 서버에 넣기: `ADMIN_PASSWORD=… node tools/market-seed.mjs --post http://127.0.0.1:8788` (실서버에는 넣지 않아요)
