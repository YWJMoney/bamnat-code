# 운영 장부: 흐름·나간 판·Claude 운영 열쇠 (1.37 ADM2)

관리자 앱과 Claude가 문의를 끝까지 처리하는 법. 설계는 `docs/plans/1.37-문의-개발-운영장부-설계.md`(DESK-01·04·07·10·13), 코드는 `server/company-worker/src/desk.js`·`src/admin/views/flow.js`.

## 관리자 앱
- **오늘 › 흐름**: 새 문의 → 개발 대기 → Claude에게 넘김 → 개발 중 → 고침·출시 전 → 출시됨·답 남음. 단계마다 건수, 가장 오래 기다린 시간, 막힘(새 문의 24시간, 개발 대기 3일, 넘김 7일, 개발 중·출시 전 14일, 답 남음 바로), 다음 할 일 단추. 아래에 30일 숫자(첫 답 중앙값, 답 목표 지킴, 도움이 됐어요, 개발 걸린 날)와 8주 추이.
- **나간 판 저장**(흐름 위 단추, '개발사항 처리' 권한): Play에 나간 판. 개발사항의 반영 판이 그 이하일 때만 앱에 '반영', 정리함 '이미 고침', "업데이트해 주세요" 답이 나가요. 저장하면 새로 나간 판에 묶여 기다리던 문의(또는 "업데이트에 들어가요" 답만 받은 문의)에 한꺼번에 안내해요. 아직 한 번도 저장하지 않았으면 1.36까지처럼 동작해요.
- **관리 › Claude 열쇠**(주인만): 열쇠 만들기(한 번만 보임, 서버엔 해시만), 끊기, 마지막 사용, 오늘 쓴 수. 살아 있는 열쇠는 3개까지, 90일.

## 열쇠로 할 수 있는 것
헤더 `authorization: Bearer bko_…`와 `x-admin: 1`. 활동 기록에는 'Claude'.

| 읽기 (GET) | 쓰기 (POST) |
|---|---|
| `/api/admin/desk` 장부 한 번에(규칙, shipped, 흐름, 정리함, 진행 중 개발사항, 넘김 글) | `/inquiries/<id>/reply·status·assign·notes` |
| `/flow`, `/me`, `/badges` | `/inquiries/sort/apply`, `/inquiries/handoff/mark·result` |
| `/inquiries`, `/inquiries/<id>`(알람 점검 기록은 빠짐), `/inquiries/sort`, `/inquiries/handoff` | `/dev`, `/dev/<id>`, `/dev/<id>/links·links/remove·notes·reply` |
| `/dev`, `/dev/<id>`, `/templates` | `/desk/ship` |

그 밖(지우기, 여러 개 한 번에 닫기, 공개 바꾸기, 사진, 결제 기록, 자동 대응 설정, 기능 스위치, 공지, 관리자, 열쇠 목록)은 403. 하루 3000번·바꾸기 400번을 넘으면 429.

## 시험
`npm test`의 `test/desk.test.mjs`(나간 판 전후 앱 단계·답·묶음 답, 흐름 숫자, 열쇠 범위·끊기). 로컬에서 보기: `ADMIN_PASSWORD=… npm run local -- 8788 --seed --test-clock` 뒤 `/admin/#/flow`.
