// 1.38 [ADM3] 관리자 앱 개편의 새 화면들. 보고서 /mnt/project-files/조사/1.38-관리자앱-전면리뷰-보고서.md(사용자 2026-11-06 "전부 진행").
// 다른 화면 묶음처럼 글(String(fn))로 브라우저에 가고, 앱이 나눠 주는 것(A)으로 불려요. 밖의 이름은 쓰지 않아요.
//   사고 #/incident [ADM3-32] · Claude 조종석 #/cockpit [ADM3-31] · 스토어 리뷰 #/reviews [ADM3-18] · 데이터 요청 #/requests, 백업 #/backup [ADM3-16]
//   출시 점검표 #/launch [ADM3-14] · 스튜디오 재료 #/materials [ADM3-06] · 판 건강 #/health [ADM3-15·33] · 돈 #/money [ADM3-12]
//   오류 묶음 #/errors [ADM3-13] · 서버 상태 판 [ADM3-04·05·19] · 사람 옆 패널 [ADM3-11] · 개발사항 이야기 [ADM3-34]
// 화면마다 [loads]가 읽을 길을, [has]가 다 왔는지를 앱에 알려요(앱의 load()·hasData()가 이것을 써요).
export function opsViews(A) {
  const { S, L, esc, icon, P, ago, can, href, seg, empty, skeleton, api, act, load, toast, openDialog, dialog, confirmBox, syncUrl } = A;

  const won = (n) => `${Math.round(n ?? 0).toLocaleString("ko-KR")}원`;
  const when = (ms) => (ms ? new Date(ms).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");
  const dayOf = (ms) => (ms ? new Date(ms).toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" }) : "—");
  const agoMs = (ms) => (ms ? ago(new Date(ms).toISOString()) : "");
  const left = (ms) => {
    const h = Math.round((ms - Date.now()) / 3600000);
    if (h < 0) return `${Math.ceil(-h / 24)}일 넘김`;
    return h < 48 ? `${h}시간 남음` : `${Math.floor(h / 24)}일 남음`;
  };
  const tile = (k, v, d, tone = "", to = "") => `<${to ? `a href="${to}"` : "div"} class="tile kpi${tone ? ` ${tone}` : ""}"><span class="k">${esc(k)}</span><span class="v">${v}</span>${d ? `<span class="d">${esc(d)}</span>` : ""}</${to ? "a" : "div"}>`;
  const head = (title, meta = "", side = "") => `<div class="card-head"><h2>${esc(title)}</h2>${meta ? `<span class="meta">${esc(meta)}</span>` : ""}${side}</div>`;
  const segs = (act, list, current) => `<div class="segmented" role="group">${list.map(([k, label, n]) => seg(act, k, current, label, n)).join("")}</div>`;
  const bytes = (n) => (n >= 1024 ** 3 ? `${(n / 1024 ** 3).toFixed(2)}GB` : n >= 1024 ** 2 ? `${(n / 1024 ** 2).toFixed(1)}MB` : `${Math.round((n ?? 0) / 1024)}KB`);

  /**
   * 날마다 막대 하나, 아래에 '이날 바꾼 것' 점(ADM3-33). 색만으로 나누지 않게 점마다 title과 아래 목록을 같이 둬요.
   * [days] 'YYYY-MM-DD' 목록, [values] 같은 길이, [marks] {day, text}.
   */
  function dayBars(days, values, marks = [], label = "날마다", fmt = (d) => d.slice(5).replace("-", ".")) {
    const n = Math.max(1, days.length);
    const W = 560;
    const H = 120;
    const top = Math.max(1, ...values);
    const band = W / n;
    const bw = Math.max(2, Math.min(14, band * 0.6));
    const byDay = new Map();
    for (const m of marks) byDay.set(m.day, [...(byDay.get(m.day) ?? []), m.text]);
    const bars = days.map((d, i) => {
      const v = values[i] ?? 0;
      const h = ((H - 30) * v) / top;
      const x = i * band + (band - bw) / 2;
      const mk = byDay.get(d);
      return `<g><rect class="bar-in" x="${x}" y="${H - 18 - h}" width="${bw}" height="${h}" rx="2"><title>${esc(fmt(d))} ${v}</title></rect>
        ${mk ? `<circle class="mark" cx="${x + bw / 2}" cy="${H - 8}" r="4"><title>${esc(d.slice(5))} ${esc(mk.join(" · "))}</title></circle>` : ""}</g>`;
    }).join("");
    const ticks = [0, Math.floor(n / 2), n - 1].filter((v, i, a) => a.indexOf(v) === i && days[v])
      .map((i) => `<text x="${i === 0 ? 0 : i === n - 1 ? W : i * band + band / 2}" y="${H}" text-anchor="${i === 0 ? "start" : i === n - 1 ? "end" : "middle"}">${esc(fmt(days[i]))}</text>`).join("");
    return `<figure class="week-chart day-bars"><svg viewBox="0 0 ${W} ${H + 2}" role="img" aria-label="${esc(label)} 막대">${bars}${ticks}</svg>
      ${marks.length ? `<figcaption><span class="key mark"></span>이날 바꾼 것 ${marks.length}개</figcaption>` : ""}</figure>`;
  }

  /** 바꾼 것 목록(그래프 점의 글). */
  function markList(marks) {
    if (!marks?.length) return "";
    return `<details class="data-table" data-key="marks"><summary>이 기간에 바꾼 것 ${marks.length}개</summary><ul class="mini">${marks.slice(-40).reverse()
      .map((m) => `<li><small>${esc(when(m.at))}</small> ${esc(m.text)}</li>`).join("")}</ul></details>`;
  }

  // ---- [ADM3-32] 사고 ----

  function incidentView() {
    const d = S.data.incident;
    if (!d) return skeleton(3);
    const o = d.open;
    const sig = d.signals.length ? `<ul class="alert-list">${d.signals.map((s) => `<li class="bad"><span><b>${esc(s.text)}</b></span></li>`).join("")}</ul>`
      : `<p class="calm">${icon(P.check)}<span>지금 기준을 넘은 신호는 없어요.</span></p>`;
    const top = o ? `<section class="card incident-open"><div class="card-head"><h2>사고 진행 중</h2><span class="pill bad">${esc(agoMs(o.openedAt))}부터</span></div>
        <p class="hint">${o.auto ? "서버가 신호를 보고 열었어요." : `${esc(o.by)}님이 직접 열었어요.${o.note ? ` ${esc(o.note)}` : ""}`} 사람이 무엇을 했는지 적어야 닫혀요.</p>
        ${o.signals.length ? `<h3>열 때 본 신호</h3><ul class="mini">${o.signals.map((s) => `<li>${esc(s.text)}</li>`).join("")}</ul>` : ""}
        <div class="actions start"><button class="btn primary" type="button" data-act="incident-close" data-id="${esc(o.id)}">고쳤어요 · 닫기</button></div></section>`
      : `<section class="card">${head("사고 없음", "급증·실패가 보이면 서버가 열고 바로 알려요")}
        <div class="actions start"><button class="btn" type="button" data-act="incident-open">직접 열기</button></div></section>`;
    const steps = `<section class="card">${head("할 순서", "docs/incident-response.md")}<ol class="how-steps">${d.steps.map((s, i) => `<li><span class="step-no">${i + 1}</span><div><b>${esc(s)}</b></div></li>`).join("")}</ol>
      <div class="actions start"><a class="btn small" href="${href("flags")}">기능 끄기</a><a class="btn small" href="${href("health")}">판 건강</a><a class="btn small" href="${href("errors")}">오류 묶음</a></div></section>`;
    const marks = `<section class="card">${head("그 전 48시간에 바꾼 것", `${d.marks.length}개`)}${d.marks.length
      ? `<ul class="rows">${d.marks.slice().reverse().map((m) => `<li><div class="row"><span class="row-main"><b>${esc(m.text)}</b><small>${esc(when(m.at))}</small></span></div></li>`).join("")}</ul>`
      : '<p class="hint">바꾼 것이 없어요. 바깥(Play·Cloudflare) 문제일 수 있어요.</p>'}</section>`;
    const notice = `<section class="card">${head("공지 초안")}<textarea id="incident-notice" rows="3" aria-label="공지 초안">${esc(d.notice)}</textarea>
      <div class="actions start"><button class="btn small" type="button" data-act="copy-text" data-target="incident-notice">복사</button><a class="btn small primary" href="${href("notices")}">앱 공지로 가기</a></div></section>`;
    const bugs = `<section class="card">${head("그동안 들어온 문제 신고", `${d.bugs.length}건`)}${d.bugs.length
      ? `<ul class="rows">${d.bugs.map((b) => `<li><a class="row" href="${L.routeHash("inquiries", b.id, {})}"><span class="row-main"><b>${esc(b.first || "(글 없음)")}</b><small>${esc(when(b.at))}${b.app ? ` · ${esc(b.app)}` : ""}</small></span>${icon(P.chevron)}</a></li>`).join("")}</ul>
        <p class="hint">같은 문제면 운영 장부에서 여러 줄을 골라 개발사항 하나로 묶어요.</p><div class="actions start"><a class="btn small" href="${L.routeHash("desk", null, { view: "open", group: "bug" })}">장부에서 묶기</a></div>`
      : '<p class="hint">아직 없어요.</p>'}</section>`;
    const past = d.past.length ? `<section class="card">${head("지난 사고")}<ul class="rows">${d.past.map((p) => `<li><div class="row"><span class="row-main"><b>${esc(p.note || "(기록 없음)")}</b>
        <small>${esc(when(p.openedAt))} ~ ${esc(when(p.closedAt))} · ${esc(p.by)}</small></span></div></li>`).join("")}</ul></section>` : "";
    return `${top}<section class="card">${head("지금 신호")}${sig}</section><div class="flow-cols">${steps}${notice}</div><div class="flow-cols">${marks}${bugs}</div>${past}`;
  }

  // ---- [ADM3-31] Claude 조종석 ----

  const ACTION_WORDS = { inquiry_reply: "답함", inquiry_reply_held: "답 보류", inquiry_status: "상태", dev_create: "개발사항 만듦", dev_status: "개발 상태", dev_link: "묶음",
    dev_note: "개발 기록", review_draft: "리뷰 초안", shipped_version: "나간 판" };

  function cockpitView() {
    const d = S.data.cockpit;
    if (!d) return skeleton(2);
    const key = d.keys.find((k) => k.alive);
    const state = `<div class="tiles core">${tile("보류 중인 답", d.held.length, "10분 안에 멈출 수 있어요", d.held.length ? "hot" : "")}${tile("오늘 Claude가 한 일", d.today, "활동 기록 'Claude'")}
      ${tile("열쇠", key ? "켜짐" : "없음", key ? `${esc(key.label)}${key.lastAt ? ` · ${agoMs(key.lastAt)}` : ""}` : "관리 › Claude 열쇠", key ? "" : "hot", href("keys"))}
      ${tile("Claude 차례", "장부", "Claude가 볼 줄만 모아 보기", "", L.routeHash("desk", null, { view: "claude" }))}</div>`;
    const held = d.held.length ? `<ul class="rows">${d.held.map((h) => `<li><div class="row held-row"><span class="row-main"><small>${esc(h.ask || "(질문 글 없음)")}</small>
        <b>${esc(h.text)}</b><small>${esc(left(h.until))} 뒤 보내요${h.close ? " · 보내고 닫기" : ""}</small></span>
        <span class="row-side"><a class="btn small" href="${L.routeHash("inquiries", h.inquiry, {})}">열기</a>
        <button class="btn small danger" type="button" data-act="cockpit-stop" data-id="${h.id}" data-inquiry="${esc(h.inquiry)}">보내지 않기</button></span></div></li>`).join("")}</ul>`
      : '<p class="hint">보류 중인 답이 없어요. Claude가 열쇠로 답하면 10분 동안 여기 머물러요.</p>';
    const done = d.done.length ? `<ul class="rows">${d.done.map((a) => `<li><div class="row"><span class="row-main"><b>${esc(ACTION_WORDS[a.action] ?? L.actionName?.(a.action) ?? a.action)}</b>
        <small>${esc(when(a.at))} · ${esc(a.target ?? "")}${a.detail ? ` · ${esc(String(a.detail).slice(0, 80))}` : ""}</small></span></div></li>`).join("")}</ul>` : '<p class="hint">하루 동안 한 일이 없어요.</p>';
    return `${state}<section class="card">${head("보내기 전 답", "멈추면 보낸 분께 가지 않아요")}${held}</section>
      <section class="card">${head("Claude가 한 일", "어제부터")}${done}<div class="actions start"><a class="btn small" href="${L.routeHash("audit", null, { actor: "Claude" })}">활동 기록 전체</a></div></section>`;
  }

  // ---- [ADM3-18] 스토어 리뷰 ----

  const stars = (n) => `<span class="stars" aria-label="별 ${n}개">${"★".repeat(n)}<span class="off">${"★".repeat(5 - n)}</span></span>`;

  function reviewsView() {
    const d = S.data.storeReviews;
    const tabs = segs("reviews-show", [["open", "답 기다림", d?.waiting], ["replied", "답함"], ["all", "모두"]], S.reviewsShow ?? "open");
    if (!d) return tabs + skeleton(2);
    const m = d.month;
    const owner = Boolean(S.me?.owner);
    const top = `<div class="tiles core">${tile("30일 평균", m.avg ?? "—", `${m.total}개`)}${tile("답 기다림", d.waiting, "", d.waiting ? "hot" : "")}
      ${[1, 2].map((s) => tile(`별 ${s}개`, m.byStars[s] ?? 0, "30일", m.byStars[s] ? "hot" : "")).join("")}</div>`;
    const off = d.ready ? "" : `<section class="card setup-card"><h2>Play 서비스 계정이 없어요</h2><p class="hint">서버에 PLAY_SERVICE_ACCOUNT를 넣고 Play Console › 사용자 및 권한에서 그 계정에 '리뷰 보기·답하기'를 주면 매시 새 리뷰를 가져와요.</p></section>`;
    const fetchBtn = owner && d.ready ? '<button class="btn small" type="button" data-act="reviews-fetch">지금 가져오기</button>' : "";
    const list = d.items.length ? `<ul class="rows review-list">${d.items.map((r) => `<li><div class="card review-card${r.stars <= 2 && r.status !== "replied" ? " low" : ""}">
        <div class="card-head">${stars(r.stars)}<span class="meta">${esc(dayOf(r.at))}${r.app ? ` · ${esc(r.app)}` : ""}${r.lang ? ` · ${esc(r.lang)}` : ""}</span>
          <span class="pill ${r.status === "replied" ? "ok" : r.status === "seen" ? "" : "warn"}">${r.status === "replied" ? "답함" : r.status === "seen" ? "봄" : "새로"}</span></div>
        <p>${esc(r.text)}</p>
        ${r.reply ? `<p class="bubble admin">${esc(r.reply)}<small> · ${esc(r.repliedBy || "Play")} ${esc(dayOf(r.replyAt))}</small></p>` : ""}
        ${r.status !== "replied" ? `<form data-form="review-draft" data-id="${esc(r.id)}"><label class="sr" for="rv-${esc(r.id)}">답 초안</label>
          <textarea id="rv-${esc(r.id)}" name="text" rows="3" maxlength="${d.replyMax}" placeholder="답 초안(${d.replyMax}자 안). Claude가 적어 두기도 해요">${esc(r.draft)}</textarea>
          ${r.draft ? `<p class="meta">초안 · ${esc(r.draftBy)}</p>` : ""}
          <div class="actions start"><button class="btn small" type="submit">초안 저장</button>
          ${owner && d.ready ? `<button class="btn small primary" type="submit" name="send" value="1">Play로 보내기</button>` : ""}
          ${r.status === "new" ? `<button class="btn small ghost" type="button" data-act="review-seen" data-id="${esc(r.id)}">봤어요</button>` : ""}</div></form>` : ""}
      </div></li>`).join("")}</ul>` : empty("리뷰가 없어요", d.ready ? "새 리뷰가 오면 매시 가져와요." : "");
    return `${tabs}${off}${top}<div class="toolbar end">${fetchBtn}</div>${list}`;
  }

  // ---- [ADM3-16] 데이터 요청·백업 ----

  function requestsView() {
    const d = S.data.requests;
    const tabs = segs("requests-show", [["open", "진행 중", d?.open], ["done", "끝남"], ["all", "모두"]], S.requestsShow ?? "open");
    if (!d) return tabs + skeleton(2);
    const kinds = Object.entries(d.kinds);
    const make = `<details class="card" data-key="req-new"${S.opened.get("req-new") ? " open" : ""}><summary><b>요청 적기</b><span class="meta">문의 번호나 기기 표시(6자리)로</span></summary>
      <form data-form="request-new"><div class="fieldset"><label for="rq-kind">종류</label><select id="rq-kind" name="kind">${kinds.map(([k, v]) => `<option value="${k}">${esc(v.name)} · ${v.days}일</option>`).join("")}</select></div>
      <div class="fieldset"><label for="rq-inq">문의 번호(선택)</label><input id="rq-inq" name="inquiry" maxlength="32" autocomplete="off" value="${esc(S.requestFrom ?? "")}"></div>
      <div class="fieldset"><label for="rq-mark">기기 표시(선택)</label><input id="rq-mark" name="mark" maxlength="6" pattern="[0-9a-f]{6}" autocomplete="off"></div>
      <div class="fieldset"><label for="rq-sum">무엇을 바라는지</label><textarea id="rq-sum" name="summary" maxlength="300" rows="2" required></textarea></div>
      <div class="fieldset"><label for="rq-at">받은 날(선택, 비우면 지금)</label><input id="rq-at" name="openedAt" type="date"></div>
      <div class="actions"><button class="btn primary" type="submit">적기</button></div></form></details>`;
    const rows = d.items.length ? `<ul class="rows">${d.items.map((r) => `<li><div class="row"><span class="row-main"><b>${esc(r.name)} · ${esc(r.summary || "")}</b>
        <small>${esc(dayOf(r.openedAt))} 받음 · 기한 ${esc(dayOf(r.dueAt))}${r.mark ? ` · 기기 ${esc(r.mark)}` : ""}${r.status !== "open" ? ` · ${esc(r.by)} ${esc(r.note)}` : ""}</small></span>
        <span class="row-side">${r.status === "open" ? `<span class="pill ${r.late ? "bad" : r.soon ? "warn" : ""}">${esc(left(r.dueAt))}</span>` : `<span class="pill ${r.status === "done" ? "ok" : ""}">${r.status === "done" ? "처리함" : "거절"}</span>`}
        ${r.inquiry ? `<a class="btn small" href="${L.routeHash("inquiries", r.inquiry, {})}">문의</a>` : ""}
        ${r.status === "open" ? `<button class="btn small primary" type="button" data-act="request-close" data-id="${r.id}">끝내기</button>` : ""}</span></div></li>`).join("")}</ul>`
      : empty("기다리는 요청이 없어요", "열람·삭제·환불·법 신고가 오면 기한과 함께 적어 두세요.");
    const law = `<details class="data-table" data-key="req-law"><summary>기한 근거</summary><ul class="mini">${kinds.map(([, v]) => `<li><b>${esc(v.name)} ${v.days}일</b> ${esc(v.why)}</li>`).join("")}</ul></details>`;
    return `${tabs}${make}<section class="card">${head("법정 기한 있는 요청", d.late ? `기한 넘김 ${d.late}` : "")}${rows}${law}</section>`;
  }

  function backupView() {
    const d = S.data.backup;
    if (!d) return skeleton(2);
    const p = d.progress;
    const state = !d.ready ? '<span class="pill bad">R2 없음</span>' : d.late ? '<span class="pill bad">늦음</span>' : '<span class="pill ok">정상</span>';
    return `<section class="card">${head("하루 백업", `매일 ${d.hour}시부터 · ${d.keepDays}일 보관`, state)}
      ${d.ready ? `<p>${d.lastOk ? `마지막 성공 <b>${esc(d.lastOk)}</b>` : "아직 끝난 백업이 없어요"}${p && !p.doneAt ? ` · 진행 중 ${p.done}/${p.of}표` : ""}${p?.bytes ? ` · ${esc(bytes(p.bytes))}` : ""}</p>
        ${d.days.length ? `<p class="meta">보관 중: ${d.days.map(esc).join(" · ")}</p>` : ""}
        ${S.me?.owner ? '<div class="actions start"><button class="btn small" type="button" data-act="backup-run">지금 이어서 백업</button></div>' : ""}`
      : '<p class="hint">Cloudflare R2 저장소를 만들어 서버에 BACKUP이라는 이름으로 묶으면 매일 표마다 내보내요. 그 전에는 아래 D1 되돌리기만 쓸 수 있어요.</p>'}
      </section><section class="card">${head("되돌리기")}<p class="hint">${esc(d.timeTravel)}</p></section>`;
  }

  // ---- [ADM3-14] 출시 날 점검표 ----

  function launchView() {
    const d = S.data.launch;
    if (!d) return skeleton(2);
    const id = S.route.id;
    const run = id ? d.items.find((r) => r.version === id) ?? (S.data.launchOne?.version === id ? S.data.launchOne : null) : d.items.find((r) => !r.doneAt) ?? d.items[0];
    const edit = can("ops", "edit");
    const start = edit ? `<form data-form="launch-start" class="toolbar"><label class="sr" for="ls-v">판</label><input id="ls-v" name="version" required pattern="\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" placeholder="1.38.0" inputmode="decimal">
      <button class="btn primary small" type="submit">${icon(P.plus)}새 점검표</button></form>` : "";
    const others = d.items.length > 1 ? `<div class="chips">${d.items.map((r) => `<a class="chip${r.version === run?.version ? " on" : ""}" href="${L.routeHash("launch", r.version, {})}">${esc(r.version)}${r.doneAt ? " ✓" : ` · ${r.left}`}</a>`).join("")}</div>` : "";
    if (!run) return `${start}${empty("점검표가 없어요", "Play에 올릴 판을 적으면 할 일이 순서대로 나와요.")}`;
    const steps = `<ol class="launch-steps">${run.steps.map((s, i) => `<li class="${s.done ? "done" : s.skipped ? "skipped" : run.next === s.key ? "next" : ""}">
        <span class="step-no">${s.done ? "✓" : i + 1}</span><div class="step-main"><b>${esc(s.name)}</b>${s.autoDone ? ' <span class="pill ok">저절로 확인</span>' : ""}${s.skipped ? ' <span class="pill">건너뜀</span>' : ""}
        <p>${esc(s.what)}</p>${s.hint ? `<p class="meta">${esc(s.hint)}</p>` : ""}${s.doneAt ? `<p class="meta">${esc(s.by)} · ${esc(when(s.doneAt))}</p>` : ""}${s.note ? `<p class="meta">메모: ${esc(s.note)}</p>` : ""}
        <div class="actions start">${s.to ? `<a class="btn small" href="${esc(s.to)}">열기</a>` : ""}${edit ? (s.done && !s.autoDone ? `<button class="btn small ghost" type="button" data-act="launch-step" data-key="${s.key}" data-do="undo">되돌리기</button>`
          : s.done ? "" : `<button class="btn small primary" type="button" data-act="launch-step" data-key="${s.key}" data-do="done">했어요</button><button class="btn small ghost" type="button" data-act="launch-step" data-key="${s.key}" data-do="skip">건너뛰기</button>`)
          + `<button class="btn small ghost" type="button" data-act="launch-step" data-key="${s.key}" data-do="note">메모</button>` : ""}</div></div></li>`).join("")}</ol>`;
    return `${start}${others}<section class="card">${head(`${run.version} 출시 날`, run.doneAt ? `끝남 ${when(run.doneAt)}` : `남은 일 ${run.left}`, run.doneAt ? '<span class="pill ok">끝</span>' : "")}${steps}</section>`;
  }

  // ---- [ADM3-15][ADM3-33] 판 건강 ----

  function healthView() {
    const d = S.data.health;
    if (!d) return skeleton(2);
    const marks = S.data.changes?.marks ?? [];
    const v = d.vitals && !d.vitals.error ? d.vitals : null;
    const tiles = `<div class="tiles core">${tile("가장 새 판", d.latest ?? "—", d.prev ? `앞 판 ${d.prev}` : "")}${tile("서버 오류", d.serverErrors, `${d.days}일 합${d.groupedErrors !== undefined && d.groupedErrors < d.serverErrors ? ` · 오류 묶음에 ${d.groupedErrors}` : ""}`, d.serverErrors ? "hot" : "", href("errors"))}
      ${tile("비정상 종료", v?.crash != null ? `${(v.crash * 100).toFixed(2)}%` : "—", v ? "Play 기준 1.09%" : "Play 서비스 계정 필요", v?.crash >= 0.0109 ? "hot" : "")}
      ${tile("ANR", v?.anr != null ? `${(v.anr * 100).toFixed(2)}%` : "—", v ? "Play 기준 0.47%" : "", v?.anr >= 0.0047 ? "hot" : "")}</div>`;
    const worse = d.worse.length ? `<section class="card alerts"><ul class="alert-list">${d.worse.map((w) => `<li class="bad"><span><b>${esc(w)}</b></span></li>`).join("")}</ul></section>` : "";
    const table = `<section class="card">${head("판마다", `지난 ${d.days}일 · 휴대폰-날 1000당`)}${segs("health-days", [["7", "7일"], ["14", "14일"], ["28", "28일"]], String(S.healthDays ?? 14))}
      <div class="table-wrap"><table class="grid-table"><thead><tr><th scope="col">판</th><th scope="col">휴대폰-날</th><th scope="col">문의</th><th scope="col">문제 신고</th><th scope="col">신고 비율</th><th scope="col">문의 비율</th></tr></thead>
      <tbody>${d.versions.map((r) => `<tr class="${r.app === d.latest && d.worse.length ? "bad" : ""}"><th scope="row">${esc(r.app)}</th><td>${r.phoneDays}</td><td>${r.inquiries}</td><td>${r.bugs}</td><td>${r.bugRate ?? "—"}</td><td>${r.inquiryRate ?? "—"}</td></tr>`).join("")}</tbody></table></div></section>`;
    const chg = `<section class="card">${head("바꾼 것", "판·기능·공지·가격·사고")}${marks.length ? `<ul class="rows">${marks.slice(-30).reverse().map((m) => `<li><div class="row"><span class="row-main"><b>${esc(m.text)}</b><small>${esc(when(m.at))}</small></span></div></li>`).join("")}</ul>` : '<p class="hint">이 기간에 바꾼 것이 없어요.</p>'}</section>`;
    return `${tiles}${worse}${table}${chg}`;
  }

  // ---- [ADM3-12] 돈 ----

  function moneyView() {
    const d = S.data.money;
    if (!d) return skeleton(3);
    const p = d.period;
    const marks = S.data.changes?.marks ?? [];
    const conv = p.paywall ? `${Math.round(((p.started + p.passes) / p.paywall) * 1000) / 10}%` : "—";
    const warn = d.missingPrice ? `<section class="card setup-card"><h2>가격표를 채워 주세요</h2><p class="hint">팔린 상품의 가격이 0원이라 금액이 비어 있어요. 아래 '가격표'에 Play Console의 가격을 적으면 바로 계산해요.</p></section>` : "";
    const tiles = `<div class="tiles core">${tile("지금 프로", d.now.paid, `체험 ${d.now.trials} · 이용권 ${d.now.passes}`)}${tile("한 달 어림", won(d.now.monthly), "구독 가격 합(연간은 12로 나눔)")}
      ${tile(`${d.days.length}일 매출`, won(p.amount), `수수료 뒤 ${won(p.afterFee)}`)}${tile("해지 · 환불", `${p.stopped} · ${p.refunds}`, `해지 예약 ${d.now.ending}`, p.refunds ? "hot" : "")}</div>`;
    const daysSeg = segs("money-days", [["7", "7일"], ["28", "28일"], ["90", "90일"]], String(S.moneyDays ?? 28));
    const chart = `<section class="card">${head("날마다 새 구독·이용권")}${daysSeg}${dayBars(d.days, d.days.map((_, i) => d.series.started[i] + d.series.passes[i]), marks, "날마다 새 구독과 이용권")}${markList(marks)}</section>`;
    const funnel = `<section class="card">${head("살 때까지")}<dl class="lines"><div><dt>프로 화면 봄</dt><dd>${p.paywall}</dd></div><div><dt>체험 누름</dt><dd>${p.trialTaps}</dd></div>
      <div><dt>구독 누름</dt><dd>${p.subscribeTaps}</dd></div><div><dt>새 구독·이용권</dt><dd>${p.started + p.passes} (${conv})</dd></div><div><dt>체험 시작</dt><dd>${p.trials}</dd></div>
      <div><dt>팀·직원 요금 봄 · 누름</dt><dd>${p.planViews} · ${p.planTaps}</dd></div><div><dt>보상형으로 준 날</dt><dd>${p.rewardDays}일</dd></div>
      <div><dt>광고 못 보여 줌</dt><dd>${p.adFails}${Object.keys(d.adFailReasons).length ? ` <small>(${Object.entries(d.adFailReasons).map(([k, n]) => `${esc(k.replace("theme_free_", ""))} ${n}`).join(", ")})</small>` : ""}</dd></div></dl>
      <p class="hint">광고 수익은 AdMob 콘솔에서 봐요(서버에는 실패 수만 와요).</p></section>`;
    const prods = `<section class="card">${head("상품마다")}${d.products.length ? `<div class="table-wrap"><table class="grid-table"><thead><tr><th scope="col">상품</th><th scope="col">지금</th><th scope="col">체험</th><th scope="col">새로</th><th scope="col">해지</th><th scope="col">이용권</th><th scope="col">금액</th></tr></thead>
      <tbody>${d.products.map((x) => `<tr><th scope="row">${esc(x.name)}</th><td>${x.paid}</td><td>${x.trials}</td><td>${x.started}</td><td>${x.stopped}</td><td>${x.passes}</td><td>${won(x.amount)}</td></tr>`).join("")}</tbody></table></div>` : '<p class="hint">아직 팔린 것이 없어요.</p>'}</section>`;
    const t = d.table;
    const band = (kind) => t[kind].map((b, i) => `<tr><th scope="row">${b.cap}명</th>${["monthly", "yearly", "pass"].map((f) => `<td><input name="${kind}.${i}.${f}" type="number" min="0" max="1000000" value="${b[f]}" aria-label="${kind} ${b.cap}명 ${f}"><input type="hidden" name="${kind}.${i}.cap" value="${b.cap}"></td>`).join("")}</tr>`).join("");
    const prices = S.me?.owner ? `<details class="card" data-key="price-table"${S.opened.get("price-table") ?? d.missingPrice ? " open" : ""}><summary><b>가격표</b><span class="meta">Play Console 가격을 그대로</span></summary>
      <form data-form="price-table"><div class="tiles">${[["bamnat_pro.monthly", "프로 월", t.bamnat_pro.monthly], ["bamnat_pro.yearly", "프로 연", t.bamnat_pro.yearly], ["bamnat_pro_pass_1y.pass", "프로 1년 이용권", t.bamnat_pro_pass_1y.pass], ["fee", "수수료 %", t.fee]]
        .map(([n, l, v]) => `<label class="fieldset">${esc(l)}<input name="${n}" type="number" min="0" max="1000000" value="${v}"></label>`).join("")}</div>
      ${["team", "staff"].map((k) => `<h3>${k === "team" ? "팀 요금제" : "직원 요금제"}</h3><div class="table-wrap"><table class="grid-table"><thead><tr><th scope="col">인원</th><th scope="col">월</th><th scope="col">연</th><th scope="col">1년 이용권</th></tr></thead><tbody>${band(k)}</tbody></table></div>`).join("")}
      <div class="actions"><button class="btn primary" type="submit">저장</button></div></form></details>` : "";
    return `${warn}${tiles}${chart}<div class="flow-cols">${funnel}${prods}</div>${prices}`;
  }

  // ---- [ADM3-13] 오류 묶음 ----

  const ERR_STATE = { new: ["새로", "bad"], again: ["다시 남", "bad"], seen: ["보는 중", "warn"], fixed: ["고침", "ok"] };

  function errorsView() {
    const d = S.data.errors;
    const c = d?.counts ?? {};
    const tabs = segs("errors-show", [["open", "열린 것", (c.new ?? 0) + (c.again ?? 0) + (c.seen ?? 0)], ["fixed", "고친 것"], ["all", "모두"]], S.errorsShow ?? "open");
    if (!d) return tabs + skeleton(2);
    const marks = S.data.changes?.marks ?? [];
    const chart = `<section class="card">${head("날마다 서버 오류", "14일")}${dayBars(d.days, d.daily, marks, "날마다 서버 오류")}${markList(marks)}</section>`;
    const list = d.groups.length ? `<ul class="rows">${d.groups.map((g) => {
      const [name, tone] = ERR_STATE[g.status] ?? [g.status, ""];
      return `<li><details class="err-group" data-key="err-${esc(g.sig)}"${S.opened.get(`err-${g.sig}`) ? " open" : ""}><summary class="row"><span class="row-main"><b>${esc(g.message)}</b>
        <small>${esc(g.route)} · ${g.total}번 · 처음 ${esc(dayOf(g.firstAt))} · 마지막 ${esc(agoMs(g.lastAt))}${g.app ? ` · ${esc(g.app)}` : ""}</small></span>
        <span class="row-side">${L.spark ? L.spark(g.trend.slice(-7), { name: "7일" }) : ""}<span class="pill ${tone}">${esc(name)}</span></span></summary>
        <pre class="mono err-sample">${esc(g.sample || "")}</pre>${g.note ? `<p class="meta">메모: ${esc(g.note)}</p>` : ""}
        <div class="actions start">${g.status !== "seen" ? `<button class="btn small" type="button" data-act="error-state" data-id="${esc(g.sig)}" data-key="seen">보는 중</button>` : ""}
        ${g.status !== "fixed" ? `<button class="btn small primary" type="button" data-act="error-state" data-id="${esc(g.sig)}" data-key="fixed">고쳤어요</button>` : `<button class="btn small" type="button" data-act="error-state" data-id="${esc(g.sig)}" data-key="new">다시 열기</button>`}</div></details></li>`;
    }).join("")}</ul>` : empty(d.loose ? "묶인 오류가 없어요" : "오류가 없어요", "서버가 500을 내면 주소·이유로 묶여 여기 쌓여요.");
    // 1.39 [ADM3-43] 판 건강의 '서버 오류'와 이 화면이 다르게 보이지 않게: 날마다 센 오류 가운데 묶음에 없는 것.
    const loose = d.loose ? `<p class="note warn" data-loose-errors>지난 14일 서버 오류 ${d.loose}번은 묶음에 없어요. 오류 묶음을 만들기 전(1.38 전 서버)이거나, 배포 직후 묶음 표가 준비되기 전에 난 것이라 주소·이유를 몰라요. 앞으로 나는 오류는 여기에 묶여요.</p>` : "";
    return `${tabs}${loose}${chart}<section class="card">${head("묶음", "고친 뒤 다시 나면 '다시 남'으로 올라와요")}${list}</section>`;
  }

  // ---- [ADM3-04][ADM3-05][ADM3-19] 서버 상태 판(관리 › 서버 상태 위) ----

  const JOB_TONE = { ok: ["정상", "ok"], fail: ["실패", "bad"], stale: ["멈춤", "warn"], never: ["아직", ""], off: ["꺼짐", "off"] };

  /**
   * 1.39 [ADM3-41][ADM3-43] 공유 서버 칸: 살아 있는지, 걸린 시간, 그리고 회사 서버와 판이 다르면 '판 다름'(빨강 아님).
   * 1.38 공유 서버는 판을 말하지 않아 '옛 판'으로만 보여요.
   */
  function shareTile(d) {
    const sh = d.share;
    if (!sh) return tile("공유 서버", "—", "SHARE_BASE_URL 없음");
    if (!sh.ok) return tile("공유 서버", "답 없음", `${sh.ms}ms`, "hot");
    const differ = sh.old || (sh.version && d.version && sh.version !== d.version);
    const note = sh.old ? ` · 판 다름: 공유 서버가 1.39 전 판이에요(회사 서버 ${d.version ?? "?"})`
      : differ ? ` · 판 다름: 공유 ${sh.version} · 회사 ${d.version}` : sh.version ? ` · ${sh.version}` : "";
    return tile("공유 서버", differ ? "살아 있음 · 판 다름" : "살아 있음", `${sh.ms}ms${note}`, differ ? "warn" : "");
  }

  function watchBoard() {
    const d = S.data.watch;
    if (!S.me?.owner) return "";
    if (!d) return skeleton(2);
    const s = d.setup;
    const setup = `<section class="card${s.missing.length ? " setup-card" : ""}">${head("서버 상태와 설정", s.missing.length ? `꼭 필요 ${s.missing.length}개 빠짐` : "꼭 필요한 것은 다 있어요",
        s.missing.length ? '<span class="pill bad">빠짐</span>' : '<span class="pill ok">정상</span>')}
      <ul class="rows wrap">${s.items.map((i) => `<li><div class="row"><span class="row-main"><b>${esc(i.what)}</b><small>${esc(i.key)}${i.ok ? "" : ` · 없으면: ${esc(i.lose)}`}</small></span>
        <span class="pill ${i.ok ? "ok" : i.need === "must" ? "bad" : "warn"}">${i.ok ? "있음" : i.need === "must" ? "꼭 필요" : "있으면 좋음"}</span></div></li>`).join("")}</ul>
      <p class="hint">값은 Cloudflare › Workers › bamnat-company › 설정 › 변수 및 비밀에 넣어요. 값은 이 화면에 보이지 않아요.</p></section>`;
    // 1.39 [ADM3-43] 줄마다 마지막으로 돌린 서버 판(지금 판과 다르면 '옛 판'), 할 것이 없어 건너뛰는 단계는 '꺼짐'과 그 이유.
    const jobs = `<section class="card">${head("정기 작업", `매시 단계마다 따로 돌아요${d.version ? ` · 지금 서버 ${d.version}` : ""}`)}${d.jobs.length ? `<ul class="rows wrap">${d.jobs.map((j) => {
      const [name, tone] = JOB_TONE[j.state] ?? [j.state, ""];
      const ver = j.ver ? ` · ${esc(j.ver)}${d.version && j.ver !== d.version ? "(옛 판 기록)" : ""}` : d.version && j.runs ? " · 1.39 전 판 기록" : "";
      return `<li><div class="row"><span class="row-main"><b>${esc(j.name)}</b><small>${j.off ? esc(j.off) : `${j.okAt ? `마지막 성공 ${esc(agoMs(j.okAt))}` : "성공 기록 없음"}${j.ms ? ` · ${j.ms}ms` : ""} · ${j.runs}번 중 실패 ${j.fails}${j.error ? ` · ${esc(j.error)}` : ""}${ver}`}</small></span>
        <span class="pill ${tone}">${name}</span></div></li>`;
    }).join("")}</ul>` : '<p class="hint">아직 돈 적이 없어요. 매시 정각에 돌아요.</p>'}</section>`;
    const t = d.speed.total;
    const used = d.dbBytes ? d.dbBytes / d.dbLimit : 0;
    const tiles = `<div class="tiles core">${tile("응답 중간", t.n ? `${t.p50}ms` : "—", `24시간 ${t.n}번`)}${tile("느린 5%", t.n ? `${t.p95}ms` : "—", `가장 느림 ${t.max}ms`, t.p95 > 1500 ? "hot" : "")}
      ${tile("서버 오류", t.err, t.n ? `${Math.round((t.err / t.n) * 1000) / 10}%` : "", t.err ? "hot" : "", href("errors"))}
      ${tile("DB 크기", d.dbBytes ? bytes(d.dbBytes) : "—", d.dbBytes ? `한도의 ${Math.round(used * 100)}%` : "", used > 0.7 ? "hot" : "")}
      ${shareTile(d)}</div>`;
    const slow = d.speed.routes.length ? `<details class="data-table" data-key="slow-routes"><summary>느린 길 ${d.speed.routes.length}개</summary><div class="table-wrap"><table class="grid-table"><thead><tr><th scope="col">길</th><th scope="col">번</th><th scope="col">중간</th><th scope="col">느린 5%</th><th scope="col">오류</th></tr></thead>
      <tbody>${d.speed.routes.map((r) => `<tr><th scope="row" class="mono">${esc(r.key)}</th><td>${r.n}</td><td>${r.p50}ms</td><td>${r.p95}ms</td><td>${r.err}</td></tr>`).join("")}</tbody></table></div></details>` : "";
    return `${tiles}${setup}${jobs}<section class="card">${head("응답 속도", "24시간")}${dayBars(d.speed.hours.map((h) => String(h.key)), d.speed.hours.map((h) => h.p95), [], "시간마다 느린 5%", (k) => `${String(k).slice(8, 10)}일 ${String(k).slice(11, 13)}시`)}${slow}</section>`;
  }

  // ---- [ADM3-11] 사람 옆 패널 ----

  const KIND = { bug: "문제", problem: "문제", question: "질문", idea: "제안", other: "기타" };

  function personPanel() {
    const got = S.data.person;
    if (!can("inquiries") || !got || got.for !== S.route.id) return "";
    const d = got.d;
    if (got.loading) return '<aside class="card person skeleton" aria-label="이 휴대폰"></aside>';
    if (!d) return '<aside class="card person"><p class="hint">이 휴대폰 정보를 불러오지 못했어요.</p></aside>';
    const c = d.counts;
    const m = d.money;
    const n = (o) => Object.values(o ?? {}).reduce((a, b) => a + b, 0);
    return `<aside class="card person" aria-label="이 휴대폰"><div class="card-head"><h2>이 휴대폰 <span class="mono">${esc(d.mark)}</span></h2></div>
      <p class="meta">${[d.app ? `앱 ${d.app}` : "", d.type ? `유형 ${d.type}` : ""].filter(Boolean).map(esc).join(" · ") || "판·유형 모름"}</p>
      ${d.flags.length ? `<div class="chips">${d.flags.map((f) => `<span class="pill ${f.tone}">${esc(f.text)}</span>`).join("")}</div>` : ""}
      <dl class="lines"><div><dt>문의</dt><dd>${d.inquiries.length}</dd></div><div><dt>제보</dt><dd>${n(c.reports)}</dd></div><div><dt>글 · 신고 받음</dt><dd>${n(c.posts)} · ${c.postFlags}</dd></div>
        <div><dt>레시피 · 신고 받음</dt><dd>${n(c.recipes)} · ${c.recipeFlags}</dd></div><div><dt>회사</dt><dd>${c.companies}</dd></div><div><dt>초대</dt><dd>${n(c.invited)}</dd></div></dl>
      ${m ? `<h3>결제</h3><p>${m.pro ? `${esc(m.pro.by)}${m.pro.trial ? "(체험)" : ""} · ${esc(dayOf(m.pro.until))}까지${m.pro.renews === false ? " · 해지 예약" : ""}` : "프로 아님"}</p>
        ${m.subs.length || m.passes.length ? `<ul class="mini">${m.subs.map((s) => `<li>${esc(s.product)} ${esc(s.plan ?? "")} · ${esc(s.state)} · ${esc(dayOf(s.at))}</li>`).join("")}${m.passes.map((p) => `<li>${esc(p.product)} · ${esc(p.state)} · ${esc(dayOf(p.at))}</li>`).join("")}</ul>` : ""}
        ${m.rewards.length ? `<p class="meta">보상 ${m.rewards.map((r) => `${esc(r.kind)} ${r.days}일`).join(" · ")}</p>` : ""}${m.consents.length ? `<p class="meta">동의 ${m.consents.map((x) => `${esc(x.kind)} ${esc(dayOf(x.at))}`).join(" · ")}</p>` : ""}` : ""}
      ${d.inquiries.length > 1 ? `<h3>이전 문의</h3><ul class="rows">${d.inquiries.filter((q) => q.id !== S.route.id).slice(0, 8).map((q) => `<li><a class="row" href="${L.routeHash("inquiries", q.id, {})}"><span class="row-main"><b>${esc(q.first || "(글 없음)")}</b>
        <small>${esc(KIND[q.kind] ?? q.kind)} · ${esc(dayOf(q.at))}${q.billing ? " · 결제" : ""}</small></span></a></li>`).join("")}</ul>` : ""}
      ${S.me?.owner ? `<div class="actions start"><button class="btn small" type="button" data-act="request-from" data-id="${esc(S.route.id ?? "")}">데이터 요청으로 적기</button></div>` : ""}</aside>`;
  }

  // ---- [ADM3-34] 개발사항 이야기 ----

  const STORY_ICON = { ask: "문의", link: "묶음", told: "답", made: "만듦", status: "상태", ship: "출시", note: "기록",
    sync: "저장소", live: "닿음", phone_told: "답", fixed_yes: "해결", fixed_no: "다시", me_too: "나도", tier: "요금", release: "판" };

  function storyCard() {
    const d = S.data.story;
    if (!d || d.id !== S.route.id) return "";
    return `<details class="card story" data-key="story"${S.opened.get("story") ?? true ? " open" : ""}><summary><b>이야기</b><span class="meta">문의부터 출시 답까지 ${d.days}일 · ${d.events.length}걸음</span></summary>
      <ol class="timeline">${d.events.map((e) => `<li class="ev-${esc(e.kind)}"><span class="pill">${esc(STORY_ICON[e.kind] ?? "일")}</span><span><b>${esc(e.text)}</b><small>${esc(when(e.at))}</small></span>
        ${e.ref ? `<a class="btn small ghost" href="${L.routeHash("inquiries", e.ref, {})}">문의</a>` : ""}</li>`).join("")}</ol></details>`;
  }

  // ---- [ADM3-10] 장부 위의 단계 막대: 흐름(문의에서 출시까지)을 장부 맨 위 한 줄로 ----

  function stageStrip() {
    const d = S.data.flowStrip;
    if (!d?.stages?.length) return "";
    return `<nav class="stage-strip" aria-label="문의에서 출시까지">${d.stages.map((x) => `<a href="${href("flow")}" class="${x.stuck ? "stuck" : x.n ? "" : "zero"}${x.key === d.next ? " next" : ""}" title="${esc(x.hint ?? "")}">
      <span>${esc(x.name)}</span><b>${x.n}</b></a>`).join('<span class="sep" aria-hidden="true">›</span>')}</nav>`;
  }

  // ---- [ADM3-01] 패스키(지문·얼굴) ----

  const b64 = {
    enc: (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""),
    dec: (t) => Uint8Array.from(atob(t.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((t.length + 3) % 4)), (c) => c.charCodeAt(0)),
  };
  const passkeyOk = () => typeof window !== "undefined" && Boolean(window.PublicKeyCredential && navigator.credentials?.create);

  /** 로그인 화면: 서버 도전값으로 이 기기 패스키에 서명을 받아 그대로 보내요. 성공하면 true. */
  async function passkeyAssert() {
    const o = await api("/passkey/start", "POST", {});
    const cred = await navigator.credentials.get({ publicKey: { challenge: b64.dec(o.challenge), rpId: o.rpId, userVerification: "required", timeout: 60000 } });
    if (!cred) return false;
    const r = cred.response;
    await api("/passkey/login", "POST", { id: cred.id, clientDataJSON: b64.enc(r.clientDataJSON), authenticatorData: b64.enc(r.authenticatorData), signature: b64.enc(r.signature) });
    return true;
  }

  function passkeyCard() {
    const d = S.data.passkeys;
    const on = passkeyOk();
    return `<section class="card"><div class="card-head"><h2>패스키(지문·얼굴)</h2>${d?.items.length ? `<span class="pill ok">${d.items.length}개</span>` : ""}</div>
      <p class="hint">비밀번호 대신 이 휴대폰의 지문이나 얼굴로 들어와요. 기기에 남는 열쇠와 지문 두 가지라 두 번째 확인 코드를 묻지 않아요. 비밀번호 로그인도 그대로 돼요.</p>
      ${d?.items.length ? `<ul class="rows">${d.items.map((k) => `<li><div class="row"><span class="row-main"><b>${esc(k.name)}</b><small>${esc(dayOf(k.createdAt))} 만듦${k.lastAt ? ` · 마지막 ${esc(agoMs(k.lastAt))}` : " · 아직 안 씀"}</small></span>
        <button class="btn small danger" type="button" data-act="passkey-delete" data-id="${esc(k.id)}">지우기</button></div></li>`).join("")}</ul>` : ""}
      ${on ? `<div class="actions start"><button class="btn primary small" type="button" data-act="passkey-add"${d && d.items.length >= d.max ? " disabled" : ""}>이 기기 패스키 만들기</button></div>`
        : '<p class="meta">이 브라우저는 패스키를 쓸 수 없어요. 휴대폰의 Chrome·삼성 인터넷에서 관리자 앱을 열어 주세요.</p>'}</section>`;
  }

  // ---- [ADM3-32] 맨 위 빨간 띠 ----

  function incidentBanner() {
    const n = S.overview?.incidentOpen ?? 0;
    if (!n || !S.me?.owner || S.route.name === "incident") return "";
    return `<a class="banner bad incident-band" role="alert" href="${href("incident")}">${icon(P.help)}<span><b>사고가 열려 있어요</b> 무엇이 늘었는지·바꾼 것·끌 스위치를 한 화면에서 봐요</span>${icon(P.chevron)}</a>`;
  }

  // ---- 읽을 길과 다 왔는지 ----

  const loads = {
    incident: () => [["/incident", "incident"]],
    cockpit: () => [["/cockpit", "cockpit"]],
    reviews: () => [[`/reviews?status=${S.reviewsShow ?? "open"}`, "storeReviews"]],
    requests: () => [[`/data-requests?status=${S.requestsShow ?? "open"}`, "requests"]],
    backup: () => [["/backup", "backup"]],
    launch: (id) => [["/release", "launch"], ...(id ? [[`/release/${encodeURIComponent(id)}`, "launchOne"]] : [])],
    materials: () => [["/recipes/materials", "materials"]],
    health: () => [[`/health/versions?days=${S.healthDays ?? 14}`, "health"], [`/changes?from=${Date.now() - (S.healthDays ?? 14) * 864e5}`, "changes"]],
    money: () => [[`/money?days=${S.moneyDays ?? 28}`, "money"], ...(can("metrics") ? [[`/changes?from=${Date.now() - (S.moneyDays ?? 28) * 864e5}`, "changes"]] : [])],
    errors: () => [[`/watch/errors?status=${S.errorsShow ?? "open"}`, "errors"], ...(can("metrics") ? [[`/changes?from=${Date.now() - 14 * 864e5}`, "changes"]] : [])],
    server: () => (S.me?.owner ? [["/watch/health", "watch"]] : []),
    account: () => [["/me/passkeys", "passkeys"]],
  };
  const has = {
    incident: () => S.data.incident, cockpit: () => S.data.cockpit, reviews: () => S.data.storeReviews, requests: () => S.data.requests, backup: () => S.data.backup,
    launch: () => S.data.launch, materials: () => S.data.materials, health: () => S.data.health, money: () => S.data.money, errors: () => S.data.errors,
  };

  /** 출시 › 스튜디오 재료 [ADM3-06]: 장터 › 출시 대기에 있던 재료 잠금을 제 화면으로. 그리는 것은 앱의 materialsView 그대로. */
  function materialsTab(draw) {
    const d = S.data.materials;
    return `<p class="hint">스튜디오 색 아이콘·배경 묶음을 공개하고, 무료로 줄지(아이콘은 모두 무료, 배경은 앞 3분의 1) 광고·프로로 열지 정해요. 출시 날 점검표의 '재료 잠금'이 이 화면이에요.</p>
      ${d ? draw(d, can("community", "edit")) : skeleton(2)}`;
  }

  const views = {
    incident: () => incidentView(), cockpit: () => cockpitView(), reviews: () => reviewsView(), requests: () => requestsView(), backup: () => backupView(),
    launch: () => launchView(), health: () => healthView(), money: () => moneyView(), errors: () => errorsView(),
  };

  const reload = (...keys) => {
    for (const k of keys) S.data[k] = null;
    syncUrl();
    load({ quiet: true });
  };

  const actions = {
    "passkey-add": async () => {
      // 1.38 [ADM3-36] PC 13차 (눌러도 말이 없어요): a device with no fingerprint or face unlock is told so at once, and every
      // way it can fail says why.
      const platform = await window.PublicKeyCredential?.isUserVerifyingPlatformAuthenticatorAvailable?.().catch(() => false);
      if (!platform) {
        openDialog(`<div class="box"><h2>이 기기는 지문 로그인을 못 해요</h2><p class="hint">이 브라우저에서 지문·얼굴 인식이나 화면 잠금을 쓸 수 없어요. 갤럭시 폴드의 Chrome이나 삼성 인터넷에서 관리자 앱을 열고 다시 눌러 주세요. 비밀번호 로그인은 그대로 쓸 수 있어요.</p>
          <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`);
        return;
      }
      const o = await act(() => api("/me/passkeys/start", "POST", {}));
      if (!o) return;
      toast("휴대폰 화면의 지문·얼굴 확인을 해 주세요");
      let cred;
      try {
        cred = await navigator.credentials.create({ publicKey: { challenge: b64.dec(o.challenge), rp: { id: o.rpId, name: "밤낮 관리자" },
          user: { id: b64.dec(o.user.id), name: o.user.name, displayName: o.user.displayName }, pubKeyCredParams: [{ type: "public-key", alg: -7 }],
          authenticatorSelection: { userVerification: "required", residentKey: "required" }, attestation: "none", timeout: 60000,
          excludeCredentials: o.exclude.map((id) => ({ type: "public-key", id: b64.dec(id) })) } });
      } catch (e) {
        toast(e?.name === "InvalidStateError" ? "이 기기에는 이미 패스키가 있어요" : e?.name === "NotAllowedError" ? "확인을 취소했거나 시간이 지났어요. 다시 눌러 주세요"
          : "이 기기에서는 패스키를 만들 수 없어요");
        return;
      }
      if (!cred) return;
      const name = /Android/.test(navigator.userAgent) ? "안드로이드 휴대폰" : /iPhone|iPad/.test(navigator.userAgent) ? "아이폰" : "이 컴퓨터";
      if (await act(() => api("/me/passkeys", "POST", { name, clientDataJSON: b64.enc(cred.response.clientDataJSON), attestationObject: b64.enc(cred.response.attestationObject) }),
        "패스키를 만들었어요. 다음부터 지문으로 들어와요")) reload("passkeys");
    },
    "passkey-delete": ({ id }) => confirmBox("이 패스키를 지울까요?", "이 기기로 지문 로그인을 못 해요. 휴대폰의 패스키 목록에서도 지워 주세요.", "지우기", async () => {
      if (await act(() => api(`/me/passkeys/${id}/delete`, "POST", {}), "지웠어요")) reload("passkeys");
    }),
    "incident-open": () => openDialog(`<form data-form="incident-open"><h2>사고를 직접 열까요?</h2><p>신호가 기준을 넘지 않았지만 문제가 커 보일 때 열어요. 다른 관리자에게도 맨 위 빨간 띠가 보여요.</p>
      <div class="fieldset"><label for="io-note">무엇이 보이는지</label><textarea id="io-note" name="note" maxlength="300" required></textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">열기</button></div></form>`),
    "incident-close": ({ id }) => openDialog(`<form data-form="incident-close" data-id="${esc(id)}"><h2>무엇을 했나요?</h2><p>한 줄이면 돼요. 다음에 같은 일이 생기면 이 기록을 먼저 봐요.</p>
      <div class="fieldset"><label for="ic-note">한 일</label><textarea id="ic-note" name="note" maxlength="1000" required placeholder="예: 1.38.0 결제 화면 기능을 강제로 끄고 1.38.1로 고쳤어요"></textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">닫기</button></div></form>`),
    "copy-text": async ({ target }) => {
      const el = document.getElementById(target);
      try {
        await navigator.clipboard.writeText(el?.value ?? el?.textContent ?? "");
        toast("복사했어요");
      } catch {
        el?.select?.();
        toast("복사하지 못했어요. 길게 눌러 복사해요");
      }
    },
    "cockpit-stop": ({ id, inquiry }) => confirmBox("이 답을 보내지 않을까요?", "보낸 분께 가지 않고, 기록에 취소로 남아요.", "보내지 않기", async () => {
      if (await act(() => api(`/inquiries/${inquiry}/held/${id}`, "POST", { cancel: true }), "보내지 않았어요")) reload("cockpit");
    }),
    "reviews-show": ({ key }) => { S.reviewsShow = key; reload("storeReviews"); },
    "reviews-fetch": async () => {
      const r = await act(() => api("/reviews/fetch", "POST", {}), (x) => (x.skipped ? "Play 서비스 계정이 없어요" : `새 리뷰 ${x.fresh ?? 0}개`));
      if (r) reload("storeReviews");
    },
    "review-seen": async ({ id }) => {
      if (await act(() => api(`/reviews/${encodeURIComponent(id)}/seen`, "POST", {}))) reload("storeReviews");
    },
    "requests-show": ({ key }) => { S.requestsShow = key; reload("requests"); },
    "request-close": ({ id }) => openDialog(`<form data-form="request-close" data-id="${esc(id)}"><h2>요청을 끝낼까요?</h2>
      <div class="fieldset"><label for="rc-status">결과</label><select id="rc-status" name="status"><option value="done">처리함</option><option value="refused">거절(이유 적기)</option></select></div>
      <div class="fieldset"><label for="rc-note">무엇을 했는지(필수, 기록에 남아요)</label><textarea id="rc-note" name="note" maxlength="1000" required></textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">끝내기</button></div></form>`),
    "request-from": ({ id }) => {
      S.requestFrom = id;
      S.opened.set("req-new", true);
      location.hash = href("requests");
    },
    "backup-run": async () => {
      const r = await act(() => api("/backup/run", "POST", {}), (x) => `백업 ${x.done ?? 0}/${x.of ?? 0}표`);
      if (r) reload("backup");
    },
    "launch-step": async ({ key, do: what }) => {
      const run = S.data.launchOne ?? S.data.launch?.items.find((r) => r.version === S.route.id) ?? S.data.launch?.items.find((r) => !r.doneAt) ?? S.data.launch?.items[0];
      if (!run) return;
      let body = { step: key };
      if (what === "done") body.done = true;
      else if (what === "undo") body.done = false;
      else if (what === "skip") body.skip = true;
      else {
        const note = window.prompt("메모(기록에 남아요)", run.steps.find((s) => s.key === key)?.note ?? "");
        if (note === null) return;
        body = { step: key, note };
      }
      if (await act(() => api(`/release/${run.version}`, "POST", body), what === "done" ? "했어요" : what === "skip" ? "건너뛰었어요" : what === "undo" ? "되돌렸어요" : "적었어요")) reload("launch", "launchOne");
    },
    "health-days": ({ key }) => { S.healthDays = Number(key); reload("health", "changes"); },
    "money-days": ({ key }) => { S.moneyDays = Number(key); reload("money", "changes"); },
    "errors-show": ({ key }) => { S.errorsShow = key; reload("errors"); },
    "error-state": async ({ id, key }) => {
      const note = key === "fixed" ? window.prompt("무엇을 고쳤나요? (선택)", "") : "";
      if (note === null) return;
      if (await act(() => api(`/watch/errors/${id}`, "POST", { status: key, note }), key === "fixed" ? "고쳤어요로 바꿨어요" : "바꿨어요")) reload("errors");
    },
  };

  const forms = {
    "incident-open": async (form) => {
      dialog.close();
      if (await act(() => api("/incident", "POST", { note: new FormData(form).get("note") }), "사고를 열었어요")) reload("incident");
    },
    "incident-close": async (form) => {
      dialog.close();
      if (await act(() => api(`/incident/${form.dataset.id}/close`, "POST", { note: new FormData(form).get("note") }), "사고를 닫았어요")) {
        if (S.overview) S.overview.incidentOpen = 0;
        reload("incident");
      }
    },
    "review-draft": async (form, submitter) => {
      const text = String(new FormData(form).get("text") ?? "").trim();
      const id = form.dataset.id;
      if (submitter?.name === "send") {
        confirmBox("Play로 보낼까요?", "보내면 스토어에 공개로 보여요. 고치려면 Play Console에서 해요.", "보내기", async () => {
          if (await act(() => api(`/reviews/${encodeURIComponent(id)}/reply`, "POST", { text }), "Play로 보냈어요")) reload("storeReviews");
        }, "primary");
        return;
      }
      if (await act(() => api(`/reviews/${encodeURIComponent(id)}/draft`, "POST", { text }), "초안을 저장했어요")) reload("storeReviews");
    },
    "request-new": async (form) => {
      const f = new FormData(form);
      const body = { kind: f.get("kind"), summary: f.get("summary") };
      const inq = String(f.get("inquiry") ?? "").trim();
      const mark = String(f.get("mark") ?? "").trim();
      if (inq) body.inquiry = inq;
      if (mark) body.mark = mark;
      if (f.get("openedAt")) body.openedAt = `${f.get("openedAt")}T09:00:00+09:00`;
      if (await act(() => api("/data-requests", "POST", body), "요청을 적었어요")) {
        S.requestFrom = "";
        reload("requests");
      }
    },
    "request-close": async (form) => {
      const f = new FormData(form);
      dialog.close();
      if (await act(() => api(`/data-requests/${form.dataset.id}`, "POST", { status: f.get("status"), note: f.get("note") }), "끝냈어요")) reload("requests");
    },
    "launch-start": async (form) => {
      const v = String(new FormData(form).get("version") ?? "").trim();
      const r = await act(() => api("/release", "POST", { version: v }), `${v} 점검표를 열었어요`);
      if (r) {
        S.data.launch = null;
        location.hash = L.routeHash("launch", v, {});
      }
    },
    "price-table": async (form) => {
      const f = new FormData(form);
      const n = (k) => Number(f.get(k) ?? 0) || 0;
      const bands = (kind) => (S.data.money?.table?.[kind] ?? []).map((_, i) => ({ cap: n(`${kind}.${i}.cap`), monthly: n(`${kind}.${i}.monthly`), yearly: n(`${kind}.${i}.yearly`), pass: n(`${kind}.${i}.pass`) }));
      const body = { bamnat_pro: { monthly: n("bamnat_pro.monthly"), yearly: n("bamnat_pro.yearly") }, bamnat_pro_pass_1y: { pass: n("bamnat_pro_pass_1y.pass") },
        team: bands("team"), staff: bands("staff"), fee: n("fee") };
      if (await act(() => api("/money/prices", "POST", body), "가격표를 저장했어요")) reload("money");
    },
  };

  return { opsViews: views, opsLoads: loads, opsHas: has, stageStrip, passkeyCard, passkeyAssert, passkeyOk, watchBoard, personPanel, storyCard, incidentBanner, materialsTab, actions, forms };
}
