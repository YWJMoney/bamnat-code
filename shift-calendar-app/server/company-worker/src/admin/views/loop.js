// 1.39 [LOOP] 문의 → 개발 → 출시 → 답 자동 순환의 관리자 화면(계획 docs/plans/1.39-G10-자동순환.md, 사용자 2026-11-07).
// 다른 화면 묶음처럼 글(String(fn))로 브라우저에 가고, 앱이 나눠 주는 것(A)으로 불려요. 밖의 이름은 쓰지 않아요.
//   오늘 › 자동 순환 #/loop: Play 출시 감지·1시간 보류 안내(취소·지금), 업데이트 없이 고친 것, 저장소 동기화, 정기 출시 열차, 멈춤 스위치,
//                          믿을수록 더 맡기기, 쓰면 쌓이는 날 규칙, 영향도 순 개발 대기열 [LOOP-01~08][LOOP-12][LOOP-14]
//   오늘 › 결정함 #/decide: 최고 관리자 확인 문의(Claude 초안)와 요금을 정할 새 기능 [LOOP-09]
//   숫자 › 요금 판단 #/tiers: 새 기능마다 관측치와 무료·프로 결정(최고 관리자만), 요금 기준(새 기능만 묻기)·고르기 칸 [LOOP-10][LOOP-11][LOOP-35]
export function loopViews(A) {
  const { S, esc, icon, P, ago, can, href, empty, skeleton, api, act, load, render, toast, openDialog, dialog, confirmBox, syncUrl, copy } = A;

  const owner = () => Boolean(S.me?.owner);
  const head = (title, meta = "", side = "") => `<div class="card-head"><h2>${esc(title)}</h2>${meta ? `<span class="meta">${esc(meta)}</span>` : ""}${side}</div>`;
  const when = (isoText) => (isoText ? new Date(isoText).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");
  const left = (isoText) => {
    const m = Math.round((Date.parse(isoText) - Date.now()) / 60000);
    return m <= 0 ? "곧" : m < 60 ? `${m}분 뒤` : `${Math.floor(m / 60)}시간 ${m % 60}분 뒤`;
  };
  const reload = (...keys) => {
    for (const k of keys) S.data[k] = null;
    syncUrl();
    load({ quiet: true });
  };
  /** 설정 칸은 접어 둬요(휴대폰에서 화면이 길어지지 않게). 연 상태는 앱이 기억해요(S.opened). */
  // 2.0.0 [RVA-38] 검토 U23-04: 카드 열 장 모두 이 접힌 줄로(맨 위 한 줄 상태만 펴 둬요). id는 맨 위 '다음 한 걸음'이 그 줄을 펴려고.
  const fold = (key, title, meta, body, tone = "") => `<details class="card how loop-fold" id="loop-${key}" data-key="loop-${key}"${S.opened.get(`loop-${key}`) ? " open" : ""}><summary><span class="lf-t"><b>${esc(title)}</b>${meta ? `<small class="${tone}">${esc(meta)}</small>` : ""}</span></summary>${body}</details>`;
  const SHIP_NAMES = { review: "검토 중일 수 있음", waiting: "보류 중", done: "보냄", cancelled: "취소함" };
  const SHIP_TONES = { review: "info", waiting: "warn", done: "ok", cancelled: "" };
  /** [LOOP-32] Play 트랙 100% 판이 실제로 나갔는지(이 판으로 쓰는 휴대폰). */
  // [LOOP-33] 통계 동의한 휴대폰과, 동의와 상관없는 Play 보고서(트랙 100%를 본 뒤 새로 깔거나 올린 기기) 둘 중 하나면 나간 것으로 봐요.
  const playText = (s) => (s.play !== undefined ? ` · Play 보고서 ${s.play}${s.live ? "" : `/${s.playNeed}`}대` : " · Play 보고서는 아직(권한·하루 늦음)");
  const seenText = (s) => (s.live ? `실제로 나갔어요 · 이 판 휴대폰 ${s.phones}대${playText(s)}` : `검토 중이거나 아직 안 나갔을 수 있어요 · 통계 동의 휴대폰 ${s.phones}/${s.need}대${playText(s)}`);
  const REWARD_NAMES = [["bugDays", "문제 신고가 고쳐짐", "문제 신고(버그)를 보낸 폰에, 그 개발사항이 완료되면"], ["polishDays", "제안이 다듬기로 들어감", "건의·제안이 '다듬기' 크기로 완료되면"],
    ["featureDays", "제안이 새 기능으로 들어감", "건의·제안이 '새 기능' 크기로 완료되면"], ["knownDays", "알려진 문제에 '나도 겪어요'", "목록에서 나도 겪어요만 누른 폰에, 고쳐지면(0이면 안 줌)"],
    ["monthCap", "한 휴대폰 한 달 상한", "위 날을 다 더해 한 달(한국 시간)에 받을 수 있는 날"]];

  // ---- 오늘 › 자동 순환 ----

  function playCard(d) {
    const p = d.play ?? {};
    const pills = [p.ready ? '<span class="pill ok">Play 연결됨</span>' : '<span class="pill warn">Play 연결 열쇠 없음</span>',
      d.shipped ? `<span class="pill info">나간 판 ${esc(d.shipped.v)}</span>` : '<span class="pill warn">나간 판 없음</span>',
      d.switches.autoShip ? "" : '<span class="pill bad">자동 출시 안내 꺼짐</span>'].filter(Boolean).join(" ");
    const track = p.at ? `<dl class="lines"><div><dt>Play 트랙 100%</dt><dd>${esc(p.done ?? "—")}${p.seen ? `<br><small>${esc(seenText(p.seen))}</small>` : ""}</dd></div>
        <div><dt>단계적 출시</dt><dd>${p.rolling ? `${esc(p.rolling.v)} · ${Math.round((p.rolling.fraction ?? 0) * 100)}%` : "없음"}</dd></div>
        ${p.halted ? `<div><dt>멈춘 출시</dt><dd>${esc(p.halted)}</dd></div>` : ""}<div><dt>마지막으로 읽음</dt><dd>${esc(ago(p.at))}</dd></div></dl>`
      : `<p class="hint">${p.ready ? "매시 17분에 프로덕션 트랙을 읽어요. 아직 한 번도 읽지 않았어요." : "Play 연결 열쇠('앱 정보 보기' 권한의 서비스 계정)가 서버에 들어가면 매시 Play 프로덕션 트랙을 읽어요. 그 전에는 흐름 › '나간 판 저장'을 눌러요. 넣는 곳은 관리 › 서버 상태예요."}</p>`;
    const ships = d.ships.length ? `<ul class="rows">${d.ships.map((s) => `<li><div class="row"><span class="row-main"><b>${esc(s.version)}</b>
        <small>${s.status === "review" ? `Play 트랙에 100%로 올라갔어요. 이 판으로 쓰는 휴대폰이 늘거나 Play Console에서 '게시됨'을 확인하면 1시간 보류를 시작해요${s.version === p.done && p.seen ? ` · 휴대폰 ${p.seen.phones}/${p.seen.need}대` : ""}`
          : s.status === "waiting" ? `${esc(left(s.until))} 문의 안내·업데이트 소식이 나가요${d.switches.autoShip ? "" : " · 스스로 안내가 꺼져 있어 '지금 보내기'를 눌러야 나가요"}` : `${esc(when(s.doneAt))} · ${esc(s.by || "")}${s.result ? ` · ${esc(s.result)}` : ""}`}</small></span>
        <span class="pill ${SHIP_TONES[s.status] ?? ""}">${esc(SHIP_NAMES[s.status] ?? s.status)}</span>
        ${s.status === "review" && can("devStatus", "edit") ? `<button class="btn small" type="button" data-act="loop-ship-cancel" data-v="${esc(s.version)}">멈추기</button>
          <button class="btn small" type="button" data-act="loop-ship-live" data-v="${esc(s.version)}">게시됨 확인</button>` : ""}
        ${s.status === "waiting" && can("devStatus", "edit") ? `<button class="btn small" type="button" data-act="loop-ship-cancel" data-v="${esc(s.version)}">멈추기</button>
          <button class="btn small" type="button" data-act="loop-ship-now" data-v="${esc(s.version)}">지금 보내기</button>` : ""}</div></li>`).join("")}</ul>` : "";
    const waiting = d.ships.find((s) => s.status === "waiting");
    const meta = waiting ? `${waiting.version} 안내 ${left(waiting.until)}` : [d.shipped ? `나간 판 ${d.shipped.v}` : "나간 판 없음", p.ready ? "Play 연결됨" : "Play 연결 열쇠 없음"].join(" · ");
    return fold("play", "Play 출시 감지", meta, `<p>${pills}</p>${track}${ships}
      ${can("devStatus", "edit") && p.ready ? '<div class="actions start"><button class="btn small" type="button" data-act="loop-play">지금 읽기</button></div>' : ""}
      <p class="hint">판이 실제로 나가면 1시간 뒤 스스로 '나간 판 저장'을 해요. Play는 검토 중인 판도 '100%'로 보여 줘서, 이 판으로 쓰는 휴대폰이 보이거나 사람이 '게시됨 확인'을 눌러야 나간 것으로 봐요. 단계적 출시 중에는 저장하지 않아요. 그동안 고친 판으로 앱을 연 폰에는 그 폰에만 바로 안내가 가요. 새 판이 앞 판보다 나빠지면 '출시 멈춤을 생각해 보세요' 알림이 가요.</p>`, waiting || !p.ready ? "warn" : "");
  }

  // [LOOP-18] 연결 점검: 실제 Play·GitHub에 처음 붙기 전에 한 화면에서.
  const CHECK_TONES = { ok: "ok", warn: "warn", bad: "bad" };
  const CHECK_WORDS = { ok: "됨", warn: "확인", bad: "빠짐" };
  // 2.0.0 [RVA-39] 검토 U24-12: 'Play까지 점검'을 누르면 그 시각과 '방금 다시 봤어요', 바뀐 줄에는 '방금 빠짐 → 됨'이 줄에 남아요
  // (전에는 알림 '확인할 것 6개'뿐이라 눌러도 바뀐 것이 안 보였어요).
  function checkCard() {
    const c = S.data.loopCheck;
    const bad = c ? c.lines.filter((x) => x.state !== "ok").length : 0;
    const just = c && S.loopChecked?.at === c.at ? S.loopChecked : null;
    const moved = (x) => (just?.was[x.key] && just.was[x.key] !== x.state ? `<small class="changed">방금 ${esc(CHECK_WORDS[just.was[x.key]] ?? just.was[x.key])} → ${esc(CHECK_WORDS[x.state] ?? x.state)}</small>` : "");
    const seen = just ? `<b>방금 다시 봤어요</b> · ${esc(new Date(c.at).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" }))}${c.played ? " · Play까지 읽어 봄" : ""} · ${just.n ? `바뀐 줄 ${just.n}개` : "바뀐 것 없음"}`
      : `${esc(when(c?.at))}에 봄${c?.played ? " · Play까지 읽어 봄" : ""}`;
    const body = c ? `${can("devStatus", "edit") ? '<div class="actions start"><button class="btn small" type="button" data-act="loop-check">Play까지 점검</button></div>' : ""}
        <p class="meta check-seen${just ? " just" : ""}" role="status">${seen}</p>
        <ul class="rows">${c.lines.map((x) => `<li><div class="row"><span class="row-main"><b>${esc(x.name)}</b><small>${esc(x.text)}</small>${x.fix ? `<small class="meta">${esc(x.fix)}</small>` : ""}${moved(x)}</span>
        <span class="pill ${CHECK_TONES[x.state] ?? ""}">${esc(CHECK_WORDS[x.state] ?? x.state)}</span></div></li>`).join("")}</ul>
        <p class="hint">값은 보이지 않고 있는지만 봐요. 'Play까지 점검'은 프로덕션 트랙을 한 번 읽기만 해요(아무것도 바꾸지 않아요).</p>` : skeleton(1);
    const missing = c ? c.lines.filter((x) => x.state === "bad").length : 0;
    return fold("check", "연결 점검", c ? (bad ? `${missing ? `빠짐 ${missing}개 · ` : ""}확인할 것 ${bad}개` : "모두 됨") : "", body, missing ? "warn" : "");
  }

  // [LOOP-22] 판별 고침 성적표.
  function scoreCard(d) {
    const rows = d.scores ?? [];
    if (!rows.length) return "";
    return fold("scores", "판별 고침 성적표", rows[0].solved == null ? `${rows[0].version} · 아직 답 없음` : `${rows[0].version} 해결 ${rows[0].solved}%`, `
      <ul class="rows">${rows.map((r) => `<li><div class="row"><span class="row-main"><b>${esc(r.version)}</b>
        <small>고친 것 ${r.items} · 묶인 문의 ${r.asks} · 안내 ${r.told} · 나도 겪어요 ${r.meToo}${r.firstHours != null ? ` · 첫 답 평균 ${r.firstHours}시간` : ""}</small>
        <small>해결됐어요 ${r.yes} · 아직이에요 ${r.no}</small></span>
        <span class="pill ${r.solved == null ? "" : r.solved >= 80 ? "ok" : r.solved >= 50 ? "warn" : "bad"}">${r.solved == null ? "—" : `${r.solved}%`}</span></div></li>`).join("")}</ul>
      <p class="hint">해결 비율 = 해결됐어요 ÷ (해결됐어요 + 아직이에요). 아직이에요는 다시 열려 이 판에서 고쳐진 것도 셈해요. 다음 정기 열차에 무엇을 먼저 넣을지 볼 때 써요.</p>`);
  }

  function liveCard(d) {
    if (!d.waitingLive.length) return "";
    return fold("live", "업데이트 없이 고친 것", `닿기를 기다림 ${d.waitingLive.length}개`, `
      <ul class="rows">${d.waitingLive.map((x) => `<li><a class="row" href="${href("dev", x.id)}"><span class="row-main"><b>${esc(x.title)}</b><small>${esc(x.routeName ?? x.route)} · ${esc(x.want ?? "")}</small></span>${icon(P.chevron)}</a></li>`).join("")}</ul>
      <p class="hint">서버 배포·원격 자료 서명·기능 스위치가 실제로 닿으면 묶인 문의에 바로 답이 가요(앱 업데이트 없이).</p>`);
  }

  function syncCard(d) {
    const key = d.syncKeys.find((k) => k.alive);
    const rows = d.syncs.length ? `<ul class="rows">${d.syncs.map((s) => `<li><div class="row"><span class="row-main"><b>${s.pr ? `PR #${s.pr}` : esc(s.sha.slice(0, 8) || "직접")}${s.version ? ` · ${esc(s.version)}` : ""}</b>
        <small>${esc(when(s.at))} · ${s.n}줄 · 새 ${s.result.made ?? 0} · 바뀜 ${s.result.changed ?? 0} · 답 ${s.result.told ?? 0}${s.result.errors ? ` · 오류 ${s.result.errors}` : ""}</small></span>
        <span class="pill ${s.result.errors ? "warn" : "ok"}">${s.result.errors ? "확인" : "받음"}</span></div></li>`).join("")}</ul>`
      : '<p class="hint">아직 받은 것이 없어요. 개발한 것이 저장소에 합쳐지면 GitHub이 바뀐 개발 상태를 보내요.</p>';
    const last = d.syncs[0];
    const meta = !d.switches.sync ? "받기 꺼짐" : last ? `켜짐 · 마지막 ${ago(last.at)}` : key ? "켜짐 · 아직 안 옴" : "켜짐 · 동기화 열쇠 없음";
    return fold("sync", "저장소 동기화", meta, `${rows}
      ${owner() && !key ? '<div class="actions start"><button class="btn small" type="button" data-act="loop-sync-key">동기화 열쇠 만들기</button></div>' : ""}
      <p class="hint">저장소에는 문의 글 없이 개발 상태만 있어요(개발사항 번호·요약·판). 요금(무료·프로)은 저장소에서 정하지 못해요.
      ${key ? `동기화 열쇠: ${esc(key.label)}${key.lastAt ? ` · 마지막 ${esc(ago(key.lastAt))}` : " · 아직 안 씀"}. 끊기는 관리 › Claude 열쇠에서 해요.` : "동기화 열쇠가 없어요."}</p>`, d.switches.sync ? "" : "warn");
  }

  function trainCard(d) {
    const t = d.train;
    return fold("train", "정기 출시 열차", t.next ? `다음 출발 ${trainDay(t.next)} · ${t.days}일마다` : "", `
      <p class="hint">앱 판으로 고친 것은 ${t.days}일마다 한 번 모아 나가요. 고친 직후 문의에는 '○월 ○일 정기 업데이트에 들어가요'가 날짜와 함께 가요. 알람·데이터·결제·앱이 안 열림 같은 긴급은 열차를 기다리지 않아요.</p>
      ${owner() ? `<form class="inline-form" data-form="loop-train"><label for="lt-start">첫 출발일</label><input id="lt-start" name="start" type="date" required value="${esc(t.start)}">
        <label for="lt-days">며칠마다</label><input id="lt-days" name="days" type="number" min="7" max="60" required value="${t.days}">
        <div class="actions"><button class="btn small primary" type="submit">저장</button></div></form>` : ""}`);
  }

  /** 2.0.0 [RVA-05] 검토 ADM-06: 지금 나간 판 번호, 판 번호(versionCode)가 무엇인지 한 줄. */
  function liveLine(d) {
    const l = d.live ?? {};
    return `<p class="note info">${l.code ? `지금 Play에 나간 판은 <b>${esc(l.name ?? "")} (번호 ${l.code})</b>예요.` : "지금 나간 판 번호를 아직 몰라요(Play 연결 뒤 보여요)."}
      판 번호는 앱 설정 › 앱 정보의 괄호 안 숫자예요. 비우면 바꾸지 않아요.</p>`;
  }

  /** 2.0.0 [RVA-05] 이 번호보다 낮은 판을 쓰는 폰이 최근 7일에 몇 %인지(판 번호를 모르는 옛 판은 따로). */
  function reach(d, n) {
    const rows = d.live?.phones ?? [];
    const all = rows.reduce((a, r) => a + r.phones, 0);
    const below = rows.filter((r) => r.code !== null && r.code < n).reduce((a, r) => a + r.phones, 0);
    const unknown = rows.filter((r) => r.code === null).reduce((a, r) => a + r.phones, 0);
    return { all, below, unknown, share: all ? Math.round((1000 * below) / all) / 10 : null };
  }
  function reachText(d, n) {
    n = Number(n);
    if (!n) return "0이면 권하지 않아요.";
    const r = reach(d, n);
    const live = d.live?.code;
    const over = live && n > live ? ` 지금 나간 번호(${live})보다 커서 모든 폰이 걸려요.` : "";
    return r.all ? `최근 7일 폰 ${r.all}대 중 ${r.below}대(약 ${r.share}%)가 걸려요.${r.unknown ? ` 판 번호를 모르는 옛 판 ${r.unknown}대는 빼고 셌어요.` : ""}${over}` : `최근 7일 폰 수를 아직 몰라요.${over}`;
  }

  function switchCard(d) {
    const sw = d.switches;
    // [LOOP-32] 체크하면 바로 저장돼요(전에는 아래 '저장'을 눌러야 해서 화면만 바뀐 줄 몰랐어요, 실서버 11-08).
    const box = (k, label, hint) => `<label class="chk"><input type="checkbox" name="${k}" data-change="loop-switch" data-label="${esc(label)}"${sw[k] ? " checked" : ""}${owner() ? "" : " disabled"}><span><b>${esc(label)}</b> <small>${esc(hint)}</small></span></label>`;
    const off = ["autoShip", "sync", "phone", "halt"].filter((k) => !sw[k]).length;
    return fold("switches", "멈춤 스위치 · 인앱 업데이트", off ? `${off}개 꺼짐` : "모두 켜짐", `
      ${owner() ? '<p class="hint">스위치는 누르면 바로 저장돼요.</p>' : ""}
      <div class="switches">${box("autoShip", "Play 출시를 보고 스스로 안내", "끄면 지금처럼 '나간 판 저장'을 사람이 눌러요")}
        ${box("sync", "저장소 동기화 받기", "끄면 GitHub이 보내도 서버가 거절해요")}
        ${box("phone", "고친 판으로 연 폰에 바로 안내", "단계적 출시 중에 그 폰에만")}
        ${box("halt", "단계적 출시가 나빠지면 멈춤 권장 알림", "판 건강 비교로")}</div>
      <form data-form="loop-switches" data-guard="인앱 업데이트 기준"><h3>Play 인앱 업데이트</h3>
        <p class="hint">앱이 하루 한 번 읽고 열 때마다 봐요(첫 설정을 마친 폰만). 이 판 번호보다 낮은 앱은 긴급이면 Play 전체 화면 업데이트를, 정기면 Play의 '업데이트할까요?' 창을 새 판마다 한 번 띄우고 뒤에서 받은 뒤 '다시 시작'을 물어요. 0이면 권하지 않아요. Play에서 받은 앱에서만 돼요.</p>
        ${liveLine(d)}
        <div class="fieldset"><label for="ls-urgent">긴급(바로 업데이트) 기준 판 번호</label><input id="ls-urgent" name="urgentMin" type="number" min="0" inputmode="numeric" value="${sw.urgentMin ?? 0}" aria-describedby="ls-urgent-reach"${owner() ? "" : " disabled"}>
          <span class="help" id="ls-urgent-reach" aria-live="polite">${esc(reachText(d, sw.urgentMin))}</span></div>
        <div class="fieldset"><label for="ls-flex">정기(한 번 권함) 기준 판 번호</label><input id="ls-flex" name="flexibleMin" type="number" min="0" inputmode="numeric" value="${sw.flexibleMin ?? 0}" aria-describedby="ls-flex-reach"${owner() ? "" : " disabled"}>
          <span class="help" id="ls-flex-reach" aria-live="polite">${esc(reachText(d, sw.flexibleMin))}</span></div>
        ${owner() ? '<div class="actions"><button class="btn primary small" type="submit">기준 저장</button></div>' : '<p class="note">최고 관리자만 바꿔요.</p>'}</form>`);
  }

  function autonomyCard(d) {
    const a = d.autonomy;
    const ready = a.piles.filter((p) => p.suggest).length;
    return fold("trust", "믿을수록 더 맡기기", ready ? `맡겨도 되는 종류 ${ready}개` : `연속 ${a.need}번이면 제안`, `
      <p class="hint">Claude 답은 10분 보류 뒤 나가요. 종류마다 사람이 손대지 않고 그대로 나간 수를 세요. 켠 종류는 보류 없이 바로 가요. 결제·환불 말이 있거나 회사 방식 문의면 켜 두어도 보류해요. '새 문제'는 켤 수 없어요.</p>
      <form data-form="loop-instant"><ul class="rows">${a.piles.map((p) => `<li><div class="row"><span class="row-main"><b>${esc(p.name)}</b>
        <small>그대로 ${p.sent} · 고쳐서 ${p.edited} · 취소 ${p.cancelled}${p.quick ? ` · 바로 ${p.quick}` : ""} · 연속 ${p.streak}</small></span>
        ${p.suggest ? '<span class="pill gold">맡겨도 돼요</span>' : ""}
        ${p.pile === "new" ? "" : `<label class="chk"><input type="checkbox" name="instant" value="${esc(p.pile)}" data-change="loop-instant" data-label="${esc(p.name)}"${p.instant ? " checked" : ""}${owner() ? "" : " disabled"}><span>바로</span></label>`}</div></li>`).join("")}</ul>
      ${owner() ? '<p class="hint">바로는 누르면 바로 저장돼요.</p>' : ""}</form>`);
  }

  function rewardCard(d) {
    const r = d.rewards;
    return fold("rewards", "쓰면 쌓이는 날 규칙", `문제 신고 ${r.bugDays}일 · 새 기능 제안 ${r.featureDays}일 · 한 달 ${r.monthCap}일까지`, `
      <p class="hint">프로를 날 수만큼 열어 드려요. 회사 방식 채택·요청 반영의 개월 보상은 관리 › 채택 규칙에 있어요.</p>
      <form data-form="loop-rewards" data-guard="보상 규칙">${REWARD_NAMES.map(([k, label, hint]) => `<label for="lr-${k}">${esc(label)} <small>${esc(hint)}</small></label>
        <input id="lr-${k}" name="${k}" type="number" min="0" max="${k === "monthCap" ? 60 : 30}" inputmode="numeric" value="${r[k]}"${owner() ? "" : " disabled"}>`).join("")}
        ${owner() ? '<div class="actions"><button class="btn small primary" type="submit">저장</button></div>' : '<p class="note">최고 관리자만 바꿔요.</p>'}</form>`);
  }

  function queueCard() {
    const q = S.data.loopQueue?.items;
    if (!q) return fold("queue", "개발 대기열", "읽는 중", skeleton(1));
    return fold("queue", "개발 대기열", q.length ? `${q.length}개 · 맨 앞 ${q[0].title}` : "기다리는 것 없음", `<p class="meta">영향도 순 · Claude가 이 순서로 가져가요</p>
      ${q.length ? `<ol class="rows">${q.slice(0, 12).map((x) => `<li><a class="row" href="${href("dev", x.id)}"><span class="row-main"><b>${esc(x.title)}</b>
        <small>문의 ${x.asks} · 같아요 ${x.same}${x.again ? ` · 다시 생김 ${x.again}` : ""}${x.urgent ? " · 급함" : ""}${x.track ? ` · ${esc(x.track)}` : ""}</small></span>
        <span class="pill ${x.urgent || x.again ? "bad" : "info"}">${x.score}점</span>${icon(P.chevron)}</a></li>`).join("")}</ol>`
        : empty("기다리는 개발사항이 없어요", "")}
      <div class="actions start"><a class="btn small" href="${href("dev")}">개발사항 전체</a></div>
      <p class="hint">점수 = 묶인 문의 3 + 같아요 1 + 급함(알람·데이터·결제·앱이 안 열림) 5 + 걸린 판 1 + 고친 뒤 다시 생김 6.</p>`);
  }

  /**
   * 2.0.0 [RVA-38] 검토 U23-04·ADM-14: 주인이 '지금 저절로 도는지, 내가 눌러야 하는지'를 한 줄로. 저절로 도는지 · 다음 출시 · 내가 할 것 N,
   * 그리고 다음 한 걸음 하나(채운 단추는 이것 하나). 내가 할 것: 급한 문제 신고, 게시됨 확인, 스스로 안내가 꺼진 판 보내기, 빠진 설정.
   */
  const trainDay = (day) => (day ? new Date(`${day}T00:00:00+09:00`).toLocaleDateString("ko-KR", { month: "numeric", day: "numeric", weekday: "short", timeZone: "Asia/Seoul" }) : "—");
  function steps(d) {
    const c = S.data.loopCheck;
    const edit = can("devStatus", "edit");
    const out = [];
    if (d.urgentWaiting) out.push({ text: `급한 문제 신고 ${d.urgentWaiting}건이 답을 기다려요.`, go: `<a class="btn primary" href="${href("decide")}">급한 문제 신고 보기</a>` });
    for (const s of d.ships.filter((x) => x.status === "review")) {
      out.push({ text: `${s.version}이 Play 트랙 100%예요. Play Console에서 '게시됨'을 봤으면 눌러요(1시간 뒤 안내가 나가요).`,
        go: edit ? `<button class="btn primary" type="button" data-act="loop-ship-live" data-v="${esc(s.version)}">게시됨 확인</button>` : "" });
    }
    if (!d.switches.autoShip) for (const s of d.ships.filter((x) => x.status === "waiting")) {
      out.push({ text: `${s.version} 안내가 기다려요. 스스로 안내가 꺼져 있어 눌러야 나가요.`,
        go: edit ? `<button class="btn primary" type="button" data-act="loop-ship-now" data-v="${esc(s.version)}">지금 보내기</button>` : "" });
    }
    const missing = c ? c.lines.filter((x) => x.state === "bad") : [];
    if (missing.length) out.push({ text: `빠진 설정 ${missing.length}개: ${missing.map((x) => x.name).join(" · ")}.`, go: '<button class="btn primary" type="button" data-act="loop-open" data-key="loop-check">연결 점검 보기</button>' });
    return out;
  }
  function nowCard(d) {
    const sw = d.switches;
    const stopped = [!sw.autoShip && "스스로 출시 안내 꺼짐", !sw.sync && "저장소 받기 꺼짐", !d.play?.ready && "Play 연결 열쇠 없음"].filter(Boolean);
    const todo = steps(d);
    const waiting = sw.autoShip ? d.ships.find((s) => s.status === "waiting") : null;
    const next = todo[0];
    const calm = waiting ? `${waiting.version} 안내는 ${left(waiting.until)} 저절로 나가요(Play 출시 감지에서 멈출 수 있어요).` : "판이 나가면 1시간 뒤 묶인 문의에 저절로 답해요.";
    return `<section class="card loop-now" aria-labelledby="loop-now-h">
      <h2 class="loop-state" id="loop-now-h"><span class="pill ${stopped.length ? "warn" : "ok"}">${stopped.length ? "일부만 저절로" : "저절로 도는 중"}</span>
        <span>다음 출시 <b>${esc(trainDay(d.train.next))}</b></span><span>내가 할 것 <b>${todo.length}</b></span></h2>
      ${stopped.length ? `<p class="meta">멈춘 곳: ${esc(stopped.join(" · "))}</p>` : ""}
      ${next ? `<div class="loop-next"><p><b>다음 한 걸음</b> ${esc(next.text)}</p>${next.go}</div>`
        : `<p class="calm">${icon(P.check)}<span>지금 내가 할 것은 없어요. ${esc(calm)}</span></p>`}
      <p class="hint">문의 → 개발 → 출시 → 답을 서버가 보고, 묶인 문의에 스스로 답해요.</p></section>`;
  }

  function loopView() {
    const d = S.data.loopState;
    if (!d) return skeleton(3);
    return `${nowCard(d)}<div class="flow-cols loop-folds">${checkCard()}${playCard(d)}${liveCard(d)}${queueCard()}${syncCard(d)}${scoreCard(d)}${trainCard(d)}${switchCard(d)}${autonomyCard(d)}${rewardCard(d)}</div>`;
  }

  // ---- 오늘 › 결정함 ----

  function decideView() {
    const d = S.data.loopDecide;
    if (!d) return skeleton(2);
    // 2.0.0 [RVA-25] 검토 T24-04·T24-20·U24-04: 급함 문의와 같은 줄 틀(줄 전체가 문의를 열고, 왜 걸렸는지는 오른쪽 낱말 하나). '열기' 단추를 빼
    // 380에서 제목과 겹치지 않고 1440에서 멀리 떨어지지 않아요. Claude 초안과 '초안 보내기'는 줄 아래 한 칸(채운 단추는 화면에 하나라 글자 단추).
    const asks = d.asks.length ? `<ul class="rows decide-rows">${d.asks.map((x) => `<li><a class="row" href="${href("inquiries", x.id)}"><span class="row-main"><b>${esc(x.no ?? "")} ${esc(x.title ?? "")}</b>
        <small>${x.draft ? "Claude 초안 있음" : "초안 없음"}${x.late ? ' · <b class="due-late">기한 넘김</b>' : ""}</small></span>
        <span class="row-side">${x.why ? `<span class="pill warn">${esc(x.why)}</span>` : ""}</span>${icon(P.chevron)}</a>
        ${x.draft ? `<div class="draft-line"><p class="answer">${esc(x.draft.text)}</p>${x.draft.why ? `<small class="meta">이유: ${esc(x.draft.why)}</small>` : ""}
          ${can("inquiries", "edit") ? `<div class="actions start"><button class="btn small" type="button" data-act="loop-draft-send" data-id="${esc(x.id)}" data-key="${esc(x.key)}">초안 보내기</button></div>` : ""}</div>` : ""}</li>`).join("")}</ul>`
      : empty("최고 관리자가 볼 문의가 없어요", "환불·결제·보상, 법·안전, 개인정보, 크게 화난 민원, 약관·처리방침 질문은 여기에 모여요.");
    const tiers = owner() ? (d.tiers.length ? `<ul class="rows">${d.tiers.map((x) => `<li><a class="row" href="${href("tiers")}"><span class="row-main"><b>${esc(x.title)}</b>
        <small>${x.track ? `${esc(x.track)} · ` : ""}문의 ${x.asks}건${x.ask ? ` · Claude 제안: ${esc(x.askName)}` : ""}${x.later ? " · 더 논의 중" : ""}</small>${x.why ? `<small>${esc(x.why)}</small>` : ""}</span>
        <span class="pill warn">요금 정하기</span>${icon(P.chevron)}</a></li>`).join("")}</ul>` : '<p class="hint">요금을 정할 새 기능이 없어요.</p>')
      : '<p class="hint">새 기능의 무료·프로는 최고 관리자가 정해요.</p>';
    const urgent = d.urgent ?? [];
    // 2.0.0 [RVA-46] 검토 HQ-69(조건 6): 급함은 머리의 빨간 칩 하나로 말하고 줄마다 되풀이하지 않아요. 최고 관리자 확인 줄의 이유 낱말은 주황 하나,
    // 기한을 넘긴 줄은 '기한 넘김'을 굵은 글로(색만으로 뜻을 전하지 않게).
    const lateAsks = d.asks.filter((x) => x.late).length;
    const hurry = urgent.length ? `<section class="card">${head("급함 문의", "", `<span class="pill bad">답 기다림 ${urgent.length}건</span>`)}
      <ul class="rows">${urgent.map((x) => `<li><a class="row" href="${href("inquiries", x.id)}"><span class="row-main"><b>${esc(x.title)}</b>
        <small>${x.topic ? `${esc(x.topic)} · ` : ""}${esc(ago(x.at))}</small></span>${icon(P.chevron)}</a></li>`).join("")}</ul>
      <p class="hint">알람이 안 울림·앱이 안 열림·데이터 사라짐·결제·수면 긴급 넷을 말하는 문의예요. 조용한 시간에도 폰에 알려요.</p></section>` : "";
    const back = d.back ?? [];
    const halts = d.halts ?? [];
    const again = back.length || halts.length ? `<section class="card">${head("다시 봐야 할 것", `${back.length + halts.length}건`)}
      ${halts.length ? `<ul class="rows">${halts.map((h) => `<li><a class="row" href="${href("health")}"><span class="row-main"><b>${esc(h.version)} 출시 멈춤 권장</b>
        <small>${esc(h.why)} · ${esc(ago(h.at))}</small><small>멈춤은 Play Console › 프로덕션 › 출시 중지에서 직접 해요.</small></span><span class="pill bad">출시</span>${icon(P.chevron)}</a></li>`).join("")}</ul>` : ""}
      ${back.length ? `<ul class="rows">${back.map((x) => `<li><a class="row" href="${href("dev", x.id)}"><span class="row-main"><b>${esc(x.title)}</b>
        <small>${x.track ? `${esc(x.track)} · ` : ""}고친 판에서 '아직이에요' ${x.times}번 · ${esc(ago(x.at))} · 지금 ${esc(x.statusName ?? x.status)}</small></span>
        <span class="pill warn">다시 열림</span>${icon(P.chevron)}</a></li>`).join("")}</ul>` : ""}</section>` : "";
    return `<div class="decide-view">${hurry}${again}<section class="card">${head("최고 관리자 확인 문의", `${d.asks.length}건${lateAsks ? ` · 기한 넘김 ${lateAsks}` : ""}`)}${asks}</section>
      <section class="card">${head("무료·프로 정하기", owner() ? `${d.tiers.length}개` : "")}${tiers}
        <p class="hint">새 기능만 여기서 정해요. 기존 기능의 오류 고침·다듬기는 묻지 않고 원래 요금 그대로(무료면 무료), 관리자 앱·운영 같은 서버 일은 요금이 없어요. 기준과 걸러 보기는 숫자 › 요금 판단에서 봐요.</p></section></div>`;
  }

  // ---- 숫자 › 요금 판단 ----

  function obsLines(o) {
    const lines = [["요청", `문의 ${o.requests.asks}건 · 같아요 ${o.requests.same} · 나도 겪어요 ${o.requests.meToo} · 제안 ${o.requests.ideas}`],
      ["사용 통계 기준", o.statsDays ? `최근 28일 중 ${o.statsDays}일 · 폰·일 ${o.statsPhoneDays}` : "아직 없음"]];
    for (const u of o.usage) lines.push([`쓴 수 ${u.event}`, `폰·일 ${u.phoneDays} (${u.share ?? "—"}%) · 모두 ${u.total}번`]);
    if (o.flag) lines.push([`스위치 ${o.flag.key}`, `켜진 폰·일 ${o.flag.onPhoneDays} (${o.flag.share ?? "—"}%)${o.flag.since ? ` · ${o.flag.since.slice(0, 10)}부터` : ""}`]);
    if (o.around) {
      const b = o.around.before;
      const a = o.around.after;
      lines.push(["켜기 앞뒤 14일", `프로 화면 ${b.paywall_view}→${a.paywall_view} · 체험 ${b.trial_start}→${a.trial_start} · 구독 ${b.subscribe}→${a.subscribe} · 문제 신고 ${b.bugs}→${a.bugs}`]);
    }
    return `<dl class="lines">${lines.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl>${o.note ? `<p class="note">${esc(o.note)}</p>` : ""}`;
  }

  // [LOOP-35] 사용자 11-09 '설명이 너무 부족해, 기존 기능의 오류나 개선은 무료, 새 기능만 묻기, 필터링': 기준 설명·고르기 칸·항목마다 왜 묻는지.
  const VIEW_HINTS = {
    ask: "무료·프로를 정할 새 기능을 Claude 제안별로 모았어요. 누르면 숫자와 단추가 펼쳐져요. 정하기 전에는 스위치를 켤 수 없어요.",
    decided: "무료·프로를 이미 정한 새 기능이에요. 다시 정할 수 있어요(무료로 연 것은 잠그지 않아요).",
    existing: "이미 있는 기능을 고치거나 다듬는 일이라 묻지 않아요. 그 기능의 요금 그대로(무료면 무료) 가요.",
    internal: "관리자 앱·운영·통계처럼 서버로만 닿는 일이라 앱 사용자에게 요금이 없어요.",
    unknown: "크기(고침·다듬기·새 기능)가 아직 없어서 나누지 못한 것이에요. 개발사항에서 크기를 정하면 저절로 나뉘어요.",
    all: "모든 개발사항(반려와 오래전 완료는 빼고)을 요금 기준과 함께 봐요.",
  };
  const BASIS_TONES = { new: "warn", existing: "ok", internal: "", unknown: "" };
  const basisCriteria = () => `<details class="card how" data-key="loop-tier-criteria"${S.opened.get("loop-tier-criteria") ? " open" : ""}><summary><b>무엇을 묻고 무엇을 묻지 않나요</b><span class="meta">기준</span></summary>
    <ol class="steps"><li><b>기존 기능 고침·다듬기 → 묻지 않아요.</b> 크기가 '고침'(오류·버그) 또는 '다듬기'(이미 있는 기능을 더 편하게)면 그 기능과 같은 요금 그대로예요. 무료 기능을 고쳤으면 무료예요.</li>
      <li><b>운영·관리자용 → 묻지 않아요.</b> 새 기능이어도 전달 길이 '서버'(관리자 앱·운영·통계)면 앱 사용자가 쓰는 기능이 아니라 요금이 없어요.</li>
      <li><b>새 기능 → 여기서 정해요.</b> 크기가 '새 기능'이고 앱으로 닿는(앱 업데이트·기능 스위치·원격 자료) 것만 무료·프로를 물어요. 최고 관리자만 정해요(사용자 논의 뒤).</li>
      <li><b>자동 판단이 틀리면</b> 항목의 '기준 바꾸기'로 새 기능·기존 기능·운영용 중 하나로 직접 정해요. 크기는 Claude가 개발 계획(dev-status)에 적어요.</li></ol>
    <dl class="lines">${Object.entries(S.data.loopTiers?.tierHelp ?? {}).map(([k, v]) => `<div><dt>${esc(S.data.loopTiers.tiers[k] ?? k)}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl></details>`;

  function tiersView() {
    const d = S.data.loopTiers;
    if (!d) return skeleton(2);
    const view = d.view ?? "ask";
    const chips = `<div class="chips" role="group" aria-label="요금 기준으로 고르기">${Object.entries(d.views ?? {}).map(([k, name]) =>
      `<button type="button" class="chip pick" data-act="loop-tier-view" data-key="${k}" aria-pressed="${view === k}">${esc(name)} ${d.counts?.[k] ?? 0}</button>`).join("")}</div>`;
    // 2.0.0 [RVA-58] 조건 1: 목록이 위에서 시작하게 설명은 고른 칸 한 줄만(묻고 묻지 않는 기준은 '기준'을 펴서).
    const top = `${chips}<p class="hint">${esc(VIEW_HINTS[view] ?? "")}</p>`;
    if (!d.items.length) return top + basisCriteria() + empty(view === "ask" ? "지금 정할 새 기능이 없어요" : "이 칸에 해당하는 개발사항이 없어요", view === "ask" ? "새 기능이 생기면 여기에 모여요. 기존 기능 고침·다듬기는 여기 오지 않아요." : "");
    // 2.0.0 [RVA-58] 정할 새 기능이 한 번에 열네 개쯤 와요(LOOP-41): 한 장씩 큰 카드(380 폭에서 한 장 650px)는 견주기 어려워서,
    // Claude 제안별로 묶은 한 줄(제목 + 제안과 까닭 한 줄)로 보고, 누르면 그 자리에서 숫자·단추가 펼쳐져요. 요금은 사용자와 논의한 뒤
    // 최고 관리자가 하나씩 정해요(한꺼번에 정하는 단추는 두지 않아요). 논의용으로 제안과 까닭을 한 번에 복사해요.
    const body = (x) => {
      const asked = x.basis.key === "new";
      const meta = [x.statusName, x.sizeName ? `크기 ${x.sizeName}` : "크기 없음", x.routeName ? `전달 ${x.routeName}` : ""].filter(Boolean).join(" · ");
      const basisLine = `<p class="note${asked ? " warn" : ""}"><b>${esc(x.basis.name)}${x.basis.auto ? " (자동)" : ""}</b> — ${esc(x.basis.why)}</p>`;
      const pick = owner() && asked ? `<div class="actions start">${Object.entries(d.tiers).map(([k, name]) => `<button class="btn small${x.tier === k ? " primary" : ""}" type="button" data-act="loop-tier" data-id="${esc(x.id)}" data-tier="${k}"
          title="${esc(d.tierHelp?.[k] ?? "")}">${esc(name)}${x.ask === k ? " · Claude 제안" : ""}</button>`).join("")}</div>
        <p class="hint">${esc(d.tierHelp?.[x.tier || x.ask || "later"] ?? "")}</p>` : "";
      return `<div class="rv-body"><p class="meta">${esc(meta)}</p>
        ${x.preview ? `<p class="dev-body">${esc(x.preview)}</p>` : ""}
        ${basisLine}
        ${x.onBeforeTier ? '<p class="note warn">요금을 정하기 전에 스위치가 켜져 있어요(날짜 조건 등). 먼저 정해 주세요.</p>' : ""}
        ${x.tierBy ? `<p class="meta">${esc(x.tierBy)} · ${esc(when(x.tierAt))}${x.tierNote ? ` · ${esc(x.tierNote)}` : ""}</p>` : ""}
        ${x.observe ? `<details><summary>정하는 데 쓸 숫자(요청·쓰는 수·스위치 앞뒤)</summary>${obsLines(x.observe)}</details>` : ""}
        ${pick}
        <div class="actions start">${owner() ? `<button class="btn small ghost" type="button" data-act="loop-basis" data-id="${esc(x.id)}">기준 바꾸기</button>` : ""}<a class="btn small ghost" href="${href("dev", x.id)}">개발사항 열기</a></div></div>`;
    };
    const line = (x) => {
      const key = `tier:${x.id}`;
      const pill = `<span class="pill ${x.effective.decided ? (x.tier === "later" ? "warn" : "ok") : BASIS_TONES[x.basis.key] ?? ""}">${esc(x.effective.name)}</span>`;
      const gist = x.basis.key === "new" && !x.effective.decided ? (x.why ? x.why : x.ask ? "" : "Claude 제안이 아직 없어요") : x.basis.why;
      const small = [x.first ? "1순위" : "", x.track, x.onBeforeTier ? "스위치가 먼저 켜짐" : ""].filter(Boolean).map(esc).join(" · ");
      return `<li><details data-key="${esc(key)}"${S.opened.get(key) ? " open" : ""}>
        <summary class="row"><span class="row-main"><b>${esc(x.title)}</b><small>${small}${small && gist ? " · " : ""}${gist ? `<span class="gist">${esc(gist)}</span>` : ""}</small></span>
          ${view !== "ask" || x.tier === "later" ? `<span class="row-side inline">${pill}</span>` : ""}</summary>${body(x)}</details></li>`;
    };
    // 정할 것은 Claude 제안별로(제안 없음은 맨 끝), 나머지 칸은 한 묶음.
    const ORDER = [...Object.keys(d.tiers ?? {}), ""];
    const groups = view === "ask"
      ? ORDER.map((k) => [k, d.items.filter((x) => (x.ask ?? "") === k)]).filter(([, xs]) => xs.length)
      : [["*", d.items]];
    const title = (k, n) => k === "*" ? "" : `<h2 class="section">${k ? `Claude 제안 ${esc(d.tiers[k] ?? k)}` : "제안 없음"}<span class="count">${n}</span></h2>`;
    const copyBtn = view === "ask" && d.items.length > 1 ? `<div class="actions start"><button class="btn small" type="button" data-act="loop-tier-copy">${icon(P.copy)}논의용으로 복사 (${d.items.length}개)</button></div>` : "";
    return top + copyBtn + groups.map(([k, xs]) => `${title(k, xs.length)}<ul class="rows review-rows tier-rows">${xs.map(line).join("")}</ul>`).join("") + basisCriteria();
  }

  // ---- 길·다 왔는지·단추 ----

  const loads = {
    loop: () => [["/loop", "loopState"], ["/loop/queue", "loopQueue"], ["/loop/check", "loopCheck"]],
    decide: () => [["/loop/decide", "loopDecide"]],
    tiers: () => [[`/loop/tiers?view=${S.tierView || "ask"}`, "loopTiers"]],
  };
  const has = { loop: () => S.data.loopState, decide: () => S.data.loopDecide, tiers: () => S.data.loopTiers };
  const views = { loop: () => loopView(), decide: () => decideView(), tiers: () => tiersView() };

  const actions = {
    "loop-ship-cancel": ({ v }) => confirmBox(`${v} 자동 안내를 멈출까요?`, "멈추면 이 판은 스스로 저장되지 않아요. 나중에 흐름 › '나간 판 저장'으로 직접 저장해요.", "멈추기", async () => {
      if (await act(() => api(`/loop/ship/${encodeURIComponent(v)}/cancel`, "POST", {}), "멈췄어요")) reload("loopState");
    }),
    // [LOOP-32] Play Console에서 '게시됨'을 본 사람이 눌러요: 그때부터 1시간 보류.
    "loop-ship-live": ({ v }) => confirmBox(`${v}이 Play에 게시된 걸 확인했나요?`, "Play Console › 게시 개요에서 '검토 중'이 아니라 게시됨인지 보고 눌러요. 누르면 1시간 뒤 묶인 문의 안내와 업데이트 소식이 나가요(그 사이 멈출 수 있어요).", "게시됨 확인", async () => {
      if (await act(() => api(`/loop/ship/${encodeURIComponent(v)}/live`, "POST", {}), "1시간 보류를 시작했어요")) reload("loopState");
    }, "primary"),
    "loop-ship-now": ({ v }) => confirmBox(`${v} 안내를 지금 보낼까요?`, "나간 판으로 저장하고 묶인 문의에 업데이트 안내를, 앱에 업데이트 소식 공지를 올려요.", "지금 보내기", async () => {
      if (await act(() => api(`/loop/ship/${encodeURIComponent(v)}/now`, "POST", {}), (r) => `${r.version} 저장 · 문의 ${r.answered ?? 0}건 안내`)) reload("loopState");
    }, "primary"),
    "loop-check": async () => {
      const was = Object.fromEntries((S.data.loopCheck?.lines ?? []).map((x) => [x.key, x.state]));
      const r = await act(() => api("/loop/check?play=1"));
      if (!r) return;
      // 다시 읽지 않고 그 결과 그대로 그려요(다시 읽으면 Play를 읽지 않은 점검으로 덮여요).
      S.data.loopCheck = r;
      const n = r.lines.filter((x) => was[x.key] && was[x.key] !== x.state).length;
      S.loopChecked = { at: r.at, was, n };
      // 다시 그리면 화면은 지금 펼침을 지켜요: 그 줄을 먼저 펴 두어야 결과가 보여요.
      const fold = typeof document !== "undefined" ? document.getElementById("loop-check") : null;
      if (fold) fold.open = true;
      S.opened.set("loop-check", true);
      toast(`다시 봤어요 · ${r.state === "ok" ? "모두 됨" : `확인할 것 ${r.lines.filter((x) => x.state !== "ok").length}개`}${n ? ` · 바뀐 줄 ${n}개` : " · 바뀐 것 없음"}`);
      render({ keepScroll: true });
    },
    // 2.0.0 [RVA-38] 맨 위 '다음 한 걸음'이 가리키는 접힌 줄을 다시 그리지 않고 그 자리에서 펴요.
    "loop-open": ({ key }) => {
      const el = document.getElementById(String(key));
      if (!el) return;
      el.open = true;
      S.opened.set(String(key), true);
      el.scrollIntoView?.({ block: "start", behavior: matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
      el.querySelector("summary")?.focus({ preventScroll: true });
    },
    "loop-play": async () => {
      const r = await act(() => api("/loop/play", "POST", {}));
      if (r?.error) toast(`Play를 읽지 못했어요: ${r.error}`);
      else if (r) toast(r.held ? `${r.held} 출시를 봤어요. 1시간 뒤 안내가 나가요`
        : r.track?.seen && !r.track.seen.live ? `${r.track.done}은 Play 트랙 100%지만 아직 나간 것으로 보지 않아요(휴대폰 ${r.track.seen.phones}/${r.track.seen.need}대)` : "읽었어요. 새로 나간 판은 없어요");
      reload("loopState");
    },
    "loop-sync-key": async () => {
      const r = await act(() => api("/keys", "POST", { scope: "sync" }));
      if (!r) return;
      openDialog(`<div class="key-once" data-secret="key"><h2>동기화 열쇠를 만들었어요</h2><p><b>이 화면을 닫으면 다시 볼 수 없어요.</b> GitHub 저장소 bamnat-code › Settings › Secrets and variables › Actions에
        이름 BAMNAT_SYNC_KEY로 넣어요. 이 열쇠는 개발 상태 보내기 한 가지만 할 수 있어요.</p>
        <label for="key-once">열쇠</label><input id="key-once" readonly value="${esc(r.key)}" class="mono"><p class="meta">${esc(r.expiresAt.slice(0, 10))}까지 써요.</p>
        <div class="actions"><button class="btn" type="button" data-act="key-copy">복사</button><button class="btn primary" type="button" data-act="close">넣었어요</button></div></div>`);
      reload("loopState");
    },
    "loop-tier": ({ id, tier }) => {
      const names = S.data.loopTiers?.tiers ?? {};
      openDialog(`<form data-form="loop-tier" data-id="${esc(id)}" data-tier="${esc(tier)}"><h2>${esc(names[tier] ?? tier)}(으)로 정할까요?</h2>
        <p>${esc(S.data.loopTiers?.tierHelp?.[tier] ?? "")}</p>
        <p class="hint">${tier === "later" ? "그동안 이 기능 스위치는 켤 수 없어요." : "정한 뒤에 기능 관리에서 스위치를 켤 수 있어요. 나중에 다시 정할 수 있지만, 무료로 연 것을 다시 잠그지는 않아요."}</p>
        <label for="lt-note">메모(무엇을 보고 정했는지)</label><input id="lt-note" name="note" maxlength="300">
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">정하기</button></div></form>`);
    },
    // 2.0.0 [RVA-58] 요금은 사용자와 논의한 뒤 정해요: 정할 새 기능과 Claude 제안·까닭을 한 번에 글로 복사해요.
    "loop-tier-copy": () => {
      const d = S.data.loopTiers;
      if (!d) return;
      const text = [`요금 정할 새 기능 ${d.items.length}개 (Claude 제안과 까닭)`, ...d.items.map((x, i) =>
        `${i + 1}. ${x.title}${x.track ? ` [${x.track}]` : ""}\n   제안: ${x.askName ?? "없음"}${x.why ? ` — ${x.why}` : ""}`)].join("\n");
      copy(text, `${d.items.length}개를 복사했어요`);
    },
    "loop-tier-view": ({ key }) => {
      S.tierView = key;
      reload("loopTiers");
    },
    // [LOOP-35] 자동 판단(크기·전달 길)이 틀린 것을 최고 관리자가 바로잡아요.
    "loop-basis": ({ id }) => {
      const x = S.data.loopTiers?.items.find((i) => i.id === id);
      if (!x) return;
      const names = S.data.loopTiers.basisNames ?? {};
      const opts = [["", `자동(크기·전달 길로: 지금 ${x.basis.auto ? x.basis.name : "—"})`], ["new", `${names.new} — 무료·프로를 물어요`],
        ["existing", `${names.existing} — 묻지 않고 원래 요금 그대로`], ["internal", `${names.internal} — 앱 사용자 요금 없음`]];
      openDialog(`<form data-form="loop-basis" data-id="${esc(id)}"><h2>요금 기준 바꾸기</h2><p>${esc(x.title)}</p>
        <p class="hint">새 기능만 무료·프로를 물어요. 이미 있는 기능을 고치거나 다듬는 일이면 '기존 기능'으로 두면 묻지 않아요.</p>
        <fieldset>${opts.map(([k, label]) => `<label class="chk"><input type="radio" name="basis" value="${k}"${(x.basis.auto ? "" : x.basis.key) === k ? " checked" : ""}><span>${esc(label)}</span></label>`).join("")}</fieldset>
        <label for="lb-note">이유(기록에 남아요)</label><input id="lb-note" name="note" maxlength="300" placeholder="예: 이미 있는 급여 계산을 넓히는 일이에요">
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
    },
    "loop-draft-send": ({ id, key }) => {
      const x = S.data.loopDecide?.asks.find((a) => a.key === key);
      if (!x?.draft) return;
      confirmBox("Claude 초안을 보낼까요?", x.draft.text, "보내기", async () => {
        if (await act(() => api(`/inquiries/${id}/reply`, "POST", { text: x.draft.text }), "보냈어요")) {
          await api("/loop/drafts", "POST", { key, text: null }).catch(() => null);
          reload("loopDecide");
        }
      }, "primary");
    },
  };

  const forms = {
    "loop-train": async (form) => {
      const f = new FormData(form);
      if (await act(() => api("/loop/train", "POST", { start: f.get("start"), days: Number(f.get("days")) }), (r) => `다음 출발 ${r.next}`)) reload("loopState");
    },
    /**
     * 2.0.0 [RVA-05] 검토 ADM-06·U24-10: 빈 칸은 바꾸지 않아요(전에는 0이 됐어요). 바뀐 것만 '앞 → 뒤 · 걸리는 폰'으로 한 번 보여 주고 묻고,
     * 지금 나간 번호보다 크면 모든 폰이 강제 업데이트를 보니 빨간 창으로 한 번 더 분명히 물어요.
     */
    "loop-switches": async (form) => {
      const d = S.data.loopState;
      const sw = d?.switches ?? {};
      const body = {};
      for (const k of ["urgentMin", "flexibleMin"]) {
        const raw = String(form.elements[k].value ?? "").trim();
        if (raw === "") continue;
        if (!/^\d{1,6}$/.test(raw)) return A.fieldError(form.elements[k], "0 이상 숫자만 적어 주세요");
        if (Number(raw) !== Number(sw[k] ?? 0)) body[k] = Number(raw);
      }
      if (!Object.keys(body).length) return toast("바뀐 값이 없어요");
      const live = d?.live?.code;
      const over = live ? Object.entries(body).filter(([, v]) => v > live) : [];
      const name = { urgentMin: "긴급(바로 업데이트)", flexibleMin: "정기(한 번 권함)" };
      A.danger({ title: over.length ? "지금 나간 번호보다 큰 기준이에요" : "인앱 업데이트 기준을 바꿀까요?",
        text: over.length ? `지금 나간 판 번호는 ${live}이에요. 이대로면 모든 폰이 ${over.some(([k]) => k === "urgentMin") ? "앱을 쓰기 전에 업데이트 화면을" : "업데이트 창을"} 보고, 아직 받을 판이 없을 수 있어요.`
          : "앱이 하루 안에 이 기준을 읽어요.",
        rows: Object.entries(body).map(([k, v]) => [name[k], `${sw[k] ?? 0} → ${v} · ${reachText(d, v)}`]),
        label: over.length ? "그래도 저장" : "저장", tone: over.length || body.urgentMin ? "danger" : "primary",
        run: async () => { if (await act(() => api("/loop/switches", "POST", body), "인앱 업데이트 기준을 저장했어요")) reload("loopState"); } });
    },
    "loop-rewards": async (form) => {
      const body = {};
      for (const [k] of REWARD_NAMES) {
        const raw = String(form.elements[k].value ?? "").trim();
        if (raw === "") continue;
        const max = Number(form.elements[k].max);
        if (!/^\d{1,3}$/.test(raw) || Number(raw) > max) return A.fieldError(form.elements[k], `0~${max} 사이 숫자로 적어 주세요`);
        body[k] = Number(raw);
      }
      if (!Object.keys(body).length) return toast("바뀐 값이 없어요");
      if (await act(() => api("/loop/rewards", "POST", body), "보상 규칙을 저장했어요")) reload("loopState");
    },
    "loop-basis": async (form) => {
      dialog.close();
      const f = new FormData(form);
      if (await act(() => api(`/loop/basis/${form.dataset.id}`, "POST", { basis: String(f.get("basis") ?? ""), note: String(f.get("note") ?? "") }), "기준을 바꿨어요")) reload("loopTiers", "loopDecide");
    },
    "loop-tier": async (form) => {
      dialog.close();
      const note = String(new FormData(form).get("note") ?? "");
      if (await act(() => api(`/loop/tier/${form.dataset.id}`, "POST", { tier: form.dataset.tier, note }), "정했어요")) reload("loopTiers", "loopDecide");
    },
  };

  /** [LOOP-32] 체크 칸은 누르는 즉시 저장해요. 실패하면 원래대로 돌려요. */
  const saveBox = async (t, body, on) => {
    t.disabled = true;
    const r = await act(() => api("/loop/switches", "POST", body), `${t.dataset.label} ${on ? "켰어요" : "껐어요"} · 저장됨`);
    t.disabled = false;
    if (!r) t.checked = !t.checked;
    else reload("loopState");
  };
  // [RVD-04] Not `changes`: plaza gives a function by that name, and this object hid it (판 2개 회사 상세가 빈 화면).
  const changeHandlers = {
    "loop-switch": (t) => saveBox(t, { [t.name]: t.checked }, t.checked),
    "loop-instant": (t) => saveBox(t, { instant: [...t.form.querySelectorAll('input[name="instant"]:checked')].map((x) => x.value) }, t.checked),
  };

  // 2.0.0 [RVA-05] 기준 칸에 적는 대로 '걸리는 폰'을 바로 다시 세요(시험처럼 문서가 없는 곳에서는 건너뛰어요).
  if (typeof document !== "undefined") document.addEventListener("input", (e) => {
    const t = e.target;
    if ((t.id === "ls-urgent" || t.id === "ls-flex") && S.data.loopState) {
      const help = document.getElementById(`${t.id}-reach`);
      if (help) help.textContent = t.value.trim() === "" ? "비우면 바꾸지 않아요." : reachText(S.data.loopState, t.value);
    }
  });

  return { loopViews: views, loopLoads: loads, loopHas: has, actions, forms, changeHandlers };
}
