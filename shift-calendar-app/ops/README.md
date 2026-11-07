# ops: 개발 상태 (1.39 LOOP-05)

`dev-status.jsonl`은 한 줄에 개발사항 하나를 적는 파일이에요. 관리자 앱 개발사항과 같은 것을 저장소 쪽에서 적고,
main에 합쳐지면 GitHub Action(`.github/workflows/dev-status.yml`)이 서버로 보내요.

- **문의 원문·사진·폰 정보·이름·연락처는 절대 적지 않아요.** 그런 것은 서버에만 있어요(처리방침 5항). 검사가 메일·전화번호·문의 번호를 막아요.
- **요금(무료·프로)은 적지 않아요.** 사용자가 관리자 앱 › 요금 판단에서만 정해요. Claude의 제안은 `tierAsk`·`tierWhy`로.
- 판 번호는 적지 않아요. 합친 커밋의 `gradle.properties` `bamnat.versionName`이 그 판이에요.
- `route`가 `server`이고 `done`인 줄은 `python3 tools/dev_status.py build`로 `server/company-worker/src/dev-built.js`에 들어가야 해요(검사가 확인).

```json
{"id":"dAbCdEfGhIjKlMnO","track":"LOOP-17","title":"알려진 문제판","status":"done","size":"feature","route":"app","publicTitle":"달력 위젯이 하루 늦게 바뀌어요","news":"위젯이 자정에 바로 바뀌어요","shipReply":"말씀하신 위젯 문제를 고친 업데이트가 나왔어요. 업데이트한 뒤에도 그대로면 '아직이에요'를 눌러 주세요.","check":"자정 넘겨 위젯 날짜 확인","pr":300}
```

칸 설명과 명령은 `tools/dev_status.py` 맨 위에 있어요. 상태: `todo` 접수 · `doing` 만드는 중 · `check` PC 확인 대기 · `done` 고침 · `rejected` 반려(이유 `reason`).
보일 유형(`styles`): 알려진 문제를 보일 근무 유형 목록(SHIFT·DAY·HOURLY·FIELD·BUSINESS·INVEST, 없으면 모두).
전달 길(`route`): `server` 서버 배포로 닿음 · `remote` 원격 자료(`remote: "kind@번호"`) · `flag` 기능 스위치(`flag`) · `app` 2주 정기 앱 업데이트 · `hotfix` 긴급 앱 업데이트.
