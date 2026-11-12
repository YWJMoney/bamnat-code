// 1.38 [ADM3] 관리자 앱 개편의 새 화면들. 보고서 /mnt/project-files/조사/1.38-관리자앱-전면리뷰-보고서.md(사용자 2026-11-06 "전부 진행").
// 다른 화면 묶음처럼 글(String(fn))로 브라우저에 가고, 앱이 나눠 주는 것(A)으로 불려요. 밖의 이름은 쓰지 않아요.
//   사고 #/incident [ADM3-32] · Claude 조종석 #/cockpit [ADM3-31] · 스토어 리뷰 #/reviews [ADM3-18] · 데이터 요청 #/requests, 백업 #/backup [ADM3-16]
//   보안 한 장 #/security [RVQ-18]
//   출시 점검표 #/launch [ADM3-14] · 스튜디오 재료 #/materials [ADM3-06] · 판 건강 #/health [ADM3-15·33] · 돈 #/money [ADM3-12]
//   오류 묶음 #/errors [ADM3-13] · 서버 상태 판 [ADM3-04·05·19] · 사람 옆 패널 [ADM3-11] · 개발사항 이야기 [ADM3-34]
// 화면마다 [loads]가 읽을 길을, [has]가 다 왔는지를 앱에 알려요(앱의 load()·hasData()가 이것을 써요).
export function opsViews(A) {
  const { S, L, esc, icon, P, ago, can, href, seg, empty, skeleton, api, act, load, toast, openDialog, dialog, confirmBox, syncUrl, askText, danger, serverVersion } = A;

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
  // 2.0.0 [RVQ-16][RVQ-18] 총괄 사진 검토: 좁거나 큰 글자에서 제목이 '하루/백업'처럼 세로로 서지 않게, 설명이 다음 줄로 내려가요.
  const head = (title, meta = "", side = "") => `<div class="card-head wraps"><h2>${esc(title)}</h2>${meta ? `<span class="meta">${esc(meta)}</span>` : ""}${side}</div>`;
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
    // 2.0.0 [RVN-01] 신호마다 확실·의심, 표본, 평소 값, 다시 묻지 않기(U22c-01, I22ca-01). 글에 이미 있는 값은 둘째 줄에 되풀이하지 않고,
    // 점 색은 확실만 빨강·의심은 주황·잠잠하거나 다시 묻지 않는 신호는 회색(디자인 조건 6).
    const SAMPLE = { errors: (n) => `요청 ${n}번`, rate: (n) => `요청 ${n}번` };
    const sigLine = (s) => `<li class="${s.off || s.muted ? "quiet" : s.sure ? "bad" : ""}"><span><b>${esc(s.text)}</b><small>${esc([s.sure ? "확실 · 밤에도 바로 알려요" : "의심 · 아침에 알려요",
      s.n && SAMPLE[s.key] ? SAMPLE[s.key](s.n) : "", s.was && !s.text.includes(s.was) ? s.was : "", s.off ? "지금은 잠잠해요" : "", s.muted ? "다시 묻지 않기로 한 신호" : ""].filter(Boolean).join(" · "))}</small></span></li>`;
    const MUTE_NAMES = { errors: "서버 오류", rate: "서버 오류 비율", bugs: "문제 신고", jobs: "정기 작업 실패", stale: "5분 작업 멈춤", crash: "비정상 종료", anr: "ANR" };
    const muteLine = (d.mutes ?? []).length ? `<p class="hint">다시 묻지 않는 신호: ${esc(d.mutes.map((m) => `${MUTE_NAMES[m.key] ?? m.key}(${m.until ? `${when(m.until)}까지` : "한 번 꺼질 때까지"})`).join(", "))}</p>` : "";
    const sig = (d.signals.length ? `<ul class="alert-list">${d.signals.map(sigLine).join("")}</ul>`
      : `<p class="calm">${icon(P.check)}<span>지금 기준을 넘은 신호는 없어요.</span></p>`) + muteLine;
    const top = o ? `<section class="card incident-open"><div class="card-head"><h2>사고 진행 중</h2><span class="pill bad">${esc(agoMs(o.openedAt))}부터</span></div>
        <p class="hint">${o.auto ? "서버가 신호를 보고 열었어요." : `${esc(o.by)}님이 직접 열었어요.${o.note ? ` ${esc(o.note)}` : ""}`} 사람이 무엇을 했는지 적어야 닫혀요.</p>
        ${o.signals.length ? `<h3>이 사고의 신호</h3><ul class="mini">${o.signals.map((s) => `<li>${esc(s.text)}${s.off ? " · 지금은 잠잠해요" : ""}</li>`).join("")}</ul>` : ""}
        <div class="actions start"><button class="btn primary" type="button" data-act="incident-close" data-id="${esc(o.id)}">고쳤어요 · 닫기</button></div></section>`
      : `<section class="card">${head("사고 없음", "급증·실패가 보이면 서버가 열고 바로 알려요")}
        <div class="actions start"><button class="btn" type="button" data-act="incident-open">직접 열기</button></div></section>`;
    const steps = `<section class="card">${head("할 순서", "docs/incident-response.md")}<ol class="how-steps">${d.steps.map((s, i) => `<li><span class="step-no">${i + 1}</span><div><b>${esc(s)}</b></div></li>`).join("")}</ol>
      <div class="actions start"><a class="btn small" href="${href("flags")}">기능 끄기</a><a class="btn small" href="${href("health")}">판 건강</a><a class="btn small" href="${href("errors")}">오류 묶음</a></div></section>`;
    const marks = `<section class="card">${head("그 전 48시간에 바꾼 것", `${d.marks.length}개`)}${d.marks.length
      ? `<ul class="rows">${d.marks.slice().reverse().map((m) => `<li><div class="row"><span class="row-main"><b>${esc(m.text)}</b><small>${esc(when(m.at))}</small></span></div></li>`).join("")}</ul>`
      : '<p class="hint">바꾼 것이 없어요. 바깥(Play·Cloudflare) 문제일 수 있어요.</p>'}</section>`;
    const notice = `<section class="card">${head("공지 초안")}<textarea id="incident-notice" rows="3" aria-label="공지 초안">${esc(d.notice)}</textarea>
      <div class="actions start"><button class="btn small" type="button" data-act="copy-text" data-target="incident-notice">복사</button><a class="btn small" href="${href("notices")}">앱 공지로 가기</a></div></section>`;
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
    const state = `<div class="tiles core">${tile("보류 중인 답", d.held.length, "10분 안에 멈출 수 있어요", d.held.length ? "hot" : "")}${tile("오늘 Claude가 한 일", d.today, d.reads ? `고친 일 · 읽기 ${d.reads}번 따로` : "활동 기록 'Claude'")}
      ${tile("열쇠", key ? "켜짐" : "없음", key ? `${key.label}${key.lastAt ? ` · ${agoMs(key.lastAt)}` : ""}` : "관리 › Claude 열쇠", key ? "" : "hot", href("keys"))}
      ${tile("Claude 차례", "장부", "Claude가 볼 줄만 모아 보기", "", L.routeHash("desk", null, { view: "claude" }))}
      ${d.drafts ? tile("제보·보상 추천", d.drafts.agree === null ? `${d.drafts.open}개` : `${d.drafts.agree}%`,
        d.drafts.agree === null ? "사람이 정하면 맞았는지 세요" : `정한 ${d.drafts.done}건 중 같음 ${d.drafts.same} · 열린 추천 ${d.drafts.open}${d.drafts.gate.ready ? " · 더 맡길지 여쭐 때예요" : ` · ${d.drafts.gate.least}건·${d.drafts.gate.agree}%면 더 맡길지 여쭤요`}`,
        d.drafts.gate.ready ? "hot" : "", href("review")) : ""}</div>`;
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

  // 2.0.0 [RVQ-17] 검토 U21-07·I21b-02: 대신 한 일의 증빙 한 줄(누가·언제·지운 표와 줄 수·남은 것과 까닭, 또는 내보낸 표와 줄 수).
  const sumOf = (o) => Object.values(o ?? {}).reduce((a, b) => a + b, 0);
  function proofWords(p) {
    const who = `${dayOf(p.at)} ${p.by}${p.via === "code" ? " · 복구 코드로 찾음" : ""}`;
    if (p.do === "export") return `${who} · 열람 파일 내보냄: ${Object.keys(p.tables ?? {}).length}개 표 ${sumOf(p.tables)}줄`;
    const left = Object.entries(p.left ?? {});
    return `${who} · 지움: ${Object.keys(p.gone ?? {}).length}개 표 ${sumOf(p.gone)}줄 · ${left.length ? `남음 ${left.map(([t, n]) => `${t} ${n}(${p.leftWhy?.[t] ?? "남음"})`).join(", ")}` : "남은 것 없음"}`;
  }

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
    const rows = d.items.length ? `<ul class="rows req-rows">${d.items.map((r) => `<li><div class="row"><span class="row-main"><b>${esc(r.name)} · ${esc(r.summary || "")}</b>
        <small>${esc(dayOf(r.openedAt))} 받음 · 기한 ${esc(dayOf(r.dueAt))}${r.mark ? ` · 기기 ${esc(r.mark)}` : ""}${r.status !== "open" ? ` · ${esc(r.by)} ${esc(r.note)}` : ""}</small></span>
        <span class="row-side req-side">${r.status === "open" ? `<span class="pill ${r.late ? "bad" : r.soon ? "warn" : ""}">${esc(left(r.dueAt))}</span>` : `<span class="pill ${r.status === "done" ? "ok" : ""}">${r.status === "done" ? "처리함" : "거절"}</span>`}
        ${r.inquiry ? `<a class="btn small" href="${L.routeHash("inquiries", r.inquiry, {})}">문의</a>` : ""}
        ${r.status === "open" && r.runnable ? `<button class="btn small" type="button" data-act="request-run" data-id="${r.id}">대신 하기</button>` : ""}
        ${r.status === "open" ? `<button class="btn small" type="button" data-act="request-close" data-id="${r.id}">끝내기</button>` : ""}</span></div>
        ${r.proof?.length ? `<ul class="mini proof">${r.proof.slice().reverse().map((p) => `<li>${esc(proofWords(p))}</li>`).join("")}</ul>` : ""}</li>`).join("")}</ul>`
      : empty("기다리는 요청이 없어요", "열람·삭제·환불·법 신고가 오면 기한과 함께 적어 두세요.");
    const law = `<details class="data-table" data-key="req-law"><summary>기한 근거</summary><ul class="mini">${kinds.map(([, v]) => `<li><b>${esc(v.name)} ${v.days}일</b> ${esc(v.why)}</li>`).join("")}</ul></details>`;
    return `${tabs}${make}<section class="card">${head("법정 기한 있는 요청", d.late ? `기한 넘김 ${d.late}` : "")}${rows}${law}</section>`;
  }

  function backupView() {
    const d = S.data.backup;
    if (!d) return skeleton(2);
    const p = d.progress;
    const owner = S.me?.owner;
    const state = !d.ready ? '<span class="pill bad">R2 없음</span>' : d.late ? '<span class="pill bad">늦음</span>' : '<span class="pill ok">정상</span>';
    const sealedTables = Object.keys(d.sealed ?? {});
    const keyWords = { SEAL_KEY: "서버 비밀값 SEAL_KEY", RATE_SALT: "서버 비밀값 RATE_SALT", local: "로컬 서버의 소금(실서버 아님)" }[d.sealKey] ?? d.sealKey;
    // 2.0.0 [RVQ-16] 검토 SRV-15: 무엇을 잠그고 빼는지 한 줄, 표 이름은 펼쳐서.
    const rules = d.ready ? `<details class="data-table" data-key="backup-rules"${S.opened.get("backup-rules") ? " open" : ""}><summary>잠그는 칸 ${sealedTables.length}개 표 · 빼는 표 ${d.skipped?.length ?? 0}개</summary>
      <ul class="mini">${sealedTables.map((t) => `<li><b>${esc(t)}</b> ${esc(d.sealed[t].join("·"))}</li>`).join("")}</ul>
      <p class="meta">빼는 표(곧 지워지거나 다시 만들 수 있어요): ${esc((d.skipped ?? []).join(", ") || "없음")}</p></details>` : "";
    const backup = `<section class="card">${head("하루 백업", `매일 ${d.hour}시부터 · ${d.keepDays}일 보관`, state)}
      ${d.ready ? `<p>${d.lastOk ? `마지막 성공 <b>${esc(d.lastOk)}</b>` : "아직 끝난 백업이 없어요"}${p && !p.doneAt ? ` · 진행 중 ${p.done}/${p.of}표` : ""}${p?.bytes ? ` · ${esc(bytes(p.bytes))}` : ""}</p>
        <p class="meta">비밀 칸은 ${esc(keyWords)}로 잠가요. R2만 새어서는 열 수 없어요.</p>
        ${d.days.length ? `<p class="meta">보관 중: ${d.days.map(esc).join(" · ")}</p>` : ""}${rules}
        ${owner ? '<div class="actions start"><button class="btn small" type="button" data-act="backup-run">지금 이어서 백업</button></div>' : ""}`
      : '<p class="hint">Cloudflare R2 저장소를 만들어 서버에 BACKUP이라는 이름으로 묶으면 매일 표마다 내보내요. 그 전에는 아래 D1 되돌리기만 쓸 수 있어요.</p>'}
      </section>`;
    // 검토 U21-08: 되살리기 연습(마지막 날을 다시 읽어 줄 수·잠근 칸을 맞춰 봄)과 표마다 오늘 DB 줄 수.
    const x = d.drill;
    const drillState = !d.ready ? "" : !x ? '<span class="pill">아직 안 함</span>' : !x.doneAt ? `<span class="pill">하는 중 · ${x.left}표 남음</span>`
      : !x.ok ? '<span class="pill bad">문제 있음</span>' : d.drillLate ? '<span class="pill warn">늦음</span>' : '<span class="pill ok">되살릴 수 있음</span>';
    const drillBody = !x ? `<p class="hint">${d.ready ? `백업이 끝난 날이 생기면 ${d.drillEveryDays}일마다 매시 작업이 스스로 해요.` : "R2 백업이 생기면 할 수 있어요."}</p>`
      : `<p>${x.doneAt ? `마지막 연습 <b>${esc(dayOf(x.doneAt))}</b>` : "읽는 중"} · 표 ${x.tables}개 · ${Number(x.rowsTotal ?? 0).toLocaleString("ko-KR")}줄</p>
        <p class="meta">${esc(x.day)} 백업을 다시 읽음 · ${x.checked ? `잠근 칸 ${x.checked}개를 지금 열쇠로 열어 봄` : "잠근 칸은 아직 없어요"}</p>
        ${x.problems?.length ? `<ul class="mini">${x.problems.map((r) => `<li><b>${esc(r.table)}</b> ${esc(r.problem)}</li>`).join("")}</ul>` : ""}
        ${x.shrunk?.length ? `<p class="meta">백업보다 크게 줄어든 표: ${x.shrunk.map((r) => `${esc(r.table)} ${r.want}→${r.db}`).join(", ")} (지운 것이 맞는지 살펴보세요)</p>` : ""}
        ${x.rows?.length ? `<details class="data-table" data-key="drill-rows"${S.opened.get("drill-rows") ? " open" : ""}><summary>표마다 줄 수(백업 · 오늘 DB)</summary>
          <ul class="drill-rows">${[...x.rows].sort((a, b) => Boolean(b.problem) - Boolean(a.problem)).map((r) => `<li${r.problem ? ' class="bad"' : ""}><b>${esc(r.table)}</b>
            <span>백업 ${r.rows.toLocaleString("ko-KR")}${r.rows !== r.want ? ` · 목록 ${r.want.toLocaleString("ko-KR")}` : ""} · 오늘 ${r.db < 0 ? "—" : r.db.toLocaleString("ko-KR")}${r.problem ? ` · ${esc(r.problem)}` : ""}</span></li>`).join("")}</ul></details>` : ""}`;
    const drill = `<section class="card">${head("되살리기 연습", `${d.drillEveryDays}일마다 저절로`, drillState)}${drillBody}
      ${owner && d.ready && (d.days.length || p?.doneAt) ? '<div class="actions start"><button class="btn small" type="button" data-act="backup-drill">지금 연습하기</button></div>' : ""}</section>`;
    return `${backup}${drill}<section class="card">${head("되돌리기")}<p class="hint">${esc(d.timeTravel)}</p>${d.restore ? `<p class="hint">${esc(d.restore)}</p>` : ""}</section>`;
  }

  // ---- 2.0.0 [RVQ-18] 검토 I21b-01: 보안 한 장 ----

  function securityView() {
    const d = S.data.security;
    if (!d) return skeleton(3);
    const tone = { ok: ["ok", "괜찮아요"], warn: ["warn", "살펴보기"], bad: ["bad", "고치기"] };
    const sum = d.bad ? `<span class="pill bad">고칠 것 ${d.bad}</span>` : d.warn ? `<span class="pill warn">살펴볼 것 ${d.warn}</span>` : '<span class="pill ok">모두 괜찮아요</span>';
    const rows = `<ul class="rows security-rows">${d.rows.map((r) => `<li class="sec-${r.tone}"><div class="row"><span class="row-main"><b>${esc(r.name)}</b><small>${esc(r.state)}</small>
      <small class="fix">${r.fix?.href ? `<a href="${esc(r.fix.href)}">${esc(r.fix.label)}</a>` : esc(r.fix?.label ?? "")}</small></span>
      <span class="row-side"><span class="pill ${tone[r.tone][0]}">${tone[r.tone][1]}</span></span></div></li>`).join("")}</ul>`;
    return `<section class="card">${head("지금 안전한가", `${when(d.at)} 기준 · 값은 보이지 않아요`, sum)}${rows}</section>`;
  }

  // ---- [ADM3-14] 출시 날 점검표 ----

  function launchView() {
    const d = S.data.launch;
    if (!d) return skeleton(2);
    const id = S.route.id;
    const run = id ? d.items.find((r) => r.version === id) ?? (S.data.launchOne?.version === id ? S.data.launchOne : null) : d.items.find((r) => !r.doneAt) ?? d.items[0];
    const edit = can("ops", "edit");
    const start = edit ? `<form data-form="launch-start" class="toolbar"><label class="sr" for="ls-v">판</label><input id="ls-v" name="version" required pattern="\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}" placeholder="${esc(serverVersion() || "2.0.0")}" inputmode="decimal">
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
      <form data-form="price-table" data-guard="가격표"><div class="tiles">${[["bamnat_pro.monthly", "프로 월", t.bamnat_pro.monthly], ["bamnat_pro.yearly", "프로 연", t.bamnat_pro.yearly], ["bamnat_pro_pass_1y.pass", "프로 1년 이용권", t.bamnat_pro_pass_1y.pass], ["fee", "수수료 %", t.fee]]
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
    // 2.0.0 [RVQ-13] 검토 SRV-18·U21-06: 묶이지 않은 것을 세 갈래로, 그리고 세 숫자가 무엇을 세는지 한 줄씩.
    const sp = d.split ?? { before: d.loose, failed: 0, jobs: 0 };
    const parts = [sp.before ? `묶기 전 ${sp.before}번(1.38 전 서버이거나 배포 직후 묶음 표가 준비되기 전)` : "",
      sp.failed ? `묶기 실패 ${sp.failed}번(오류를 적다가 또 실패)` : ""].filter(Boolean);
    const loose = d.loose ? `<p class="note warn" data-loose-errors>지난 14일 서버 오류 ${d.loose}번은 묶음에 없어요: ${parts.join(" · ")}. 주소·이유를 몰라요.</p>` : "";
    const jobs = sp.jobs ? `<p class="note" data-job-errors>정기 작업 실패 ${sp.jobs}번은 요청 오류가 아니라 여기 없어요. 관리 › 서버 상태의 정기 작업에서 단계마다 봐요.</p>` : "";
    const what = `<p class="hint" data-error-kinds>세는 것: 날마다 서버 오류 = 요청 하나가 500으로 끝난 수 · 묶음 = 그 가운데 주소·이유로 묶인 것 · 응답 속도의 오류 = 모든 5xx(쉬는 기능 503, Play·공유 서버 502·504 포함)</p>`;
    return `${tabs}${loose}${jobs}${chart}<section class="card">${head("묶음", "고친 뒤 다시 나면 '다시 남'으로 올라와요")}${what}${list}</section>`;
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
    }).join("")}</ul>` : '<p class="hint">아직 돈 적이 없어요. 매시 17분에 돌아요.</p>'}</section>`;
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
      <p class="meta">${[d.app ? `앱 ${d.app}` : "", d.type ? `유형 ${L.WORK_NAMES[d.type] ?? d.type}` : ""].filter(Boolean).map(esc).join(" · ") || "판·유형 모름"}</p>
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
      <p class="hint">비밀번호 대신 이 휴대폰의 지문이나 얼굴로 들어와요. 기기에 남는 열쇠와 지문 두 가지라 두 번째 확인 코드를 묻지 않아요. 만들거나 지우면 알림이 와요.</p>
      ${d?.items.length ? `<ul class="rows side-wrap">${d.items.map((k) => `<li><div class="row"><span class="row-main"><b>${esc(k.name)}</b><small>${esc(dayOf(k.createdAt))} 만듦${k.lastAt ? ` · 마지막 ${esc(agoMs(k.lastAt))}${k.lastDevice ? ` · ${esc(k.lastDevice)}` : ""}` : " · 아직 안 씀"}</small></span>
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
    security: () => [["/security", "security"]],
    launch: (id) => [["/release", "launch"], ...(id ? [[`/release/${encodeURIComponent(id)}`, "launchOne"]] : [])],
    materials: () => [["/recipes/materials", "materials"]],
    health: () => [[`/health/versions?days=${S.healthDays ?? 14}`, "health"], [`/changes?from=${Date.now() - (S.healthDays ?? 14) * 864e5}`, "changes"]],
    money: () => [[`/money?days=${S.moneyDays ?? 28}`, "money"], ...(can("metrics") ? [[`/changes?from=${Date.now() - (S.moneyDays ?? 28) * 864e5}`, "changes"]] : [])],
    errors: () => [[`/watch/errors?status=${S.errorsShow ?? "open"}`, "errors"], ...(can("metrics") ? [[`/changes?from=${Date.now() - 14 * 864e5}`, "changes"]] : [])],
    server: () => (S.me?.owner ? [["/watch/health", "watch"]] : []),
    account: () => [["/me/passkeys", "passkeys"]],
  };
  const has = {
    incident: () => S.data.incident, cockpit: () => S.data.cockpit, reviews: () => S.data.storeReviews, requests: () => S.data.requests, backup: () => S.data.backup, security: () => S.data.security,
    launch: () => S.data.launch, materials: () => S.data.materials, health: () => S.data.health, money: () => S.data.money, errors: () => S.data.errors,
  };

  /** 출시 › 스튜디오 재료 [ADM3-06]: 장터 › 출시 대기에 있던 재료 잠금을 제 화면으로. 그리는 것은 앱의 materialsView 그대로. */
  function materialsTab(draw) {
    const d = S.data.materials;
    // 2.0.0 [RVA-24] 검토 HQ-03(조건 1·4): 묶음 목록이 먼저, 이 화면 설명은 아래 한 줄(펴면 설명).
    return `${d ? draw(d, can("community", "edit")) : skeleton(2)}
      <div class="list-foot"><details class="tip" data-key="material-how"><summary>스튜디오 › 꾸밈 › 넣기의 ‘밤낮 재료’ 칸 · 출시 날 '재료 잠금'</summary>
        <p class="hint">스튜디오 색 아이콘·배경 묶음을 공개하고, 무료로 줄지(아이콘은 모두 무료, 배경은 앞 3분의 1) 광고·프로로 열지 정해요. 무료가 아닌 재료는 광고 한 편으로 그 묶음을 30일 열거나 프로로 모두 써요. 출시 날 점검표의 '재료 잠금'이 이 화면이에요.</p></details></div>`;
  }

  const views = {
    incident: () => incidentView(), cockpit: () => cockpitView(), reviews: () => reviewsView(), requests: () => requestsView(), backup: () => backupView(), security: () => securityView(),
    launch: () => launchView(), health: () => healthView(), money: () => moneyView(), errors: () => errorsView(),
  };

  const reload = (...keys) => {
    for (const k of keys) S.data[k] = null;
    syncUrl();
    load({ quiet: true });
  };

  /** 패스키 만들기: 서버 도전값 → 이 기기의 지문·얼굴 → 공개 키 보내기. [body]는 등록 시작에 보낼 것(다시 확인한 비밀번호). */
  async function passkeyCreate(body) {
    const o = await act(() => api("/me/passkeys/start", "POST", body));
    if (!o) return false;
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
    return true;
  }

  const actions = {
    "passkey-add": async () => {
      // 1.38 [ADM3-36] PC 13차 (눌러도 말이 없어요): a device with no fingerprint or face unlock is told so at once, and every
      // way it can fail says why.
      const platform = await window.PublicKeyCredential?.isUserVerifyingPlatformAuthenticatorAvailable?.().catch(() => false);
      if (!platform) {
        openDialog(`<div class="box"><h2>이 기기는 지문 로그인을 못 해요</h2><p class="hint">이 브라우저에서 지문·얼굴 인식이나 화면 잠금을 쓸 수 없어요. 휴대폰의 Chrome이나 삼성 인터넷(지문·화면 잠금을 켠 기기)에서 관리자 앱을 열고 다시 눌러 주세요. 비밀번호 로그인은 그대로 쓸 수 있어요.</p>
          <div class="actions"><button class="btn" data-act="close">닫기</button></div></div>`);
        return;
      }
      // 2.0.0 [RVQ-06] 검토 SRV-06: 로그인한 지 10분이 지났으면 비밀번호를 한 번 더(열린 채 둔 화면에서 남이 더하지 못하게).
      if (S.data.passkeys?.needPassword) {
        openDialog(`<form data-form="passkey-reauth"><h2>비밀번호를 한 번 더 넣어 주세요</h2><p class="hint">패스키를 더하기 전에 본인인지 확인해요. 로그인한 지 10분 안이면 묻지 않아요.</p>
          <div class="fieldset"><label for="pk-pw">${S.me?.owner ? "최고 관리자 비밀번호" : "내 비밀번호"}</label><input id="pk-pw" name="password" type="password" autocomplete="current-password" required></div>
          <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">확인하고 만들기</button></div></form>`);
        return;
      }
      await passkeyCreate({});
    },
    "passkey-delete": ({ id }) => confirmBox("이 패스키를 지울까요?", "이 기기로 지문 로그인을 못 해요. 휴대폰의 패스키 목록에서도 지워 주세요.", "지우기", async () => {
      if (await act(() => api(`/me/passkeys/${id}/delete`, "POST", {}), "지웠어요")) reload("passkeys");
    }),
    "incident-open": () => openDialog(`<form data-form="incident-open"><h2>사고를 직접 열까요?</h2><p>신호가 기준을 넘지 않았지만 문제가 커 보일 때 열어요. 다른 관리자에게도 맨 위 빨간 띠가 보여요.</p>
      <div class="fieldset"><label for="io-note">무엇이 보이는지</label><textarea id="io-note" name="note" maxlength="300" required data-keep="incident-open-note"></textarea></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">열기</button></div></form>`),
    "incident-close": ({ id }) => openDialog(`<form data-form="incident-close" data-id="${esc(id)}"><h2>무엇을 했나요?</h2><p>한 줄이면 돼요. 다음에 같은 일이 생기면 이 기록을 먼저 봐요.</p>
      <div class="fieldset"><label for="ic-note">한 일</label><textarea id="ic-note" name="note" maxlength="1000" required data-keep="incident-note-${esc(id)}" placeholder="예: 1.38.0 결제 화면 기능을 강제로 끄고 1.38.1로 고쳤어요"></textarea></div>
      <div class="fieldset"><label for="ic-days">지금 있는 신호를 다시 묻지 않기</label><select id="ic-days" name="days">
        <option value="0" selected>신호가 꺼질 때까지</option><option value="1">하루</option><option value="3">3일</option><option value="7">7일</option><option value="14">14일</option></select>
        <small class="hint">닫을 때보다 1.3배 나빠지면 언제든 다시 열려요. Play 비정상 종료·ANR은 28일 평균이라 몇 주 남을 수 있어요.</small></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">사고 닫기</button></div></form>`),
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
    "backup-drill": async () => {
      const r = await act(() => api("/backup/drill", "POST", {}), (x) => (x.done ? (x.ok ? "되살리기 연습: 모두 맞아요" : "되살리기 연습: 문제가 있어요") : `읽는 중 · ${x.left ?? 0}표 남음`));
      if (r) reload("backup");
    },
    "request-run": ({ id }) => {
      const r = S.data.requests?.items.find((x) => x.id === id);
      if (!r) return;
      const first = r.kind === "access" ? "export" : "forget";
      openDialog(`<form data-form="request-run" data-id="${esc(id)}"><h2>${esc(r.name)} 대신 하기</h2>
        <p class="hint">${r.inquiry ? "이 요청에 묶인 문의를 보낸 설치의 자료로 해요." : "묶인 문의가 없어요. 그분이 가진 복구 코드로 설치를 찾아요. 코드는 저장하지 않아요."}</p>
        ${r.inquiry ? "" : '<div class="fieldset"><label for="rr-code">복구 코드</label><input id="rr-code" name="code" autocomplete="off" spellcheck="false" required></div>'}
        <div class="radios" role="radiogroup" aria-label="할 일">
          <label class="radio"><input type="radio" name="do" value="export"${first === "export" ? " checked" : ""}><span>가진 것 내보내기<small class="radio-sub">열람 · JSON 파일로 받아요</small></span></label>
          <label class="radio"><input type="radio" name="do" value="forget"${first === "forget" ? " checked" : ""}><span>모두 지우기<small class="radio-sub">앱의 '모두 지우기'와 같아요</small></span></label>
        </div>
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">다음</button></div></form>`);
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
        // 2.0.0 [RVA-02] 검토 ADM-13: 기본 입력 창 대신 앱의 여러 줄 창.
        return askText({ title: "메모", text: "출시 기록에 남아요.", label: "메모", value: run.steps.find((s) => s.key === key)?.note ?? "", multiline: true, maxlength: 500 },
          async (note) => {
            if (await act(() => api(`/release/${run.version}`, "POST", { step: key, note }), "적었어요")) reload("launch", "launchOne");
          });
      }
      if (await act(() => api(`/release/${run.version}`, "POST", body), what === "done" ? "했어요" : what === "skip" ? "건너뛰었어요" : what === "undo" ? "되돌렸어요" : "적었어요")) reload("launch", "launchOne");
    },
    "health-days": ({ key }) => { S.healthDays = Number(key); reload("health", "changes"); },
    "money-days": ({ key }) => { S.moneyDays = Number(key); reload("money", "changes"); },
    "errors-show": ({ key }) => { S.errorsShow = key; reload("errors"); },
    "error-state": async ({ id, key }) => {
      const save = async (note) => {
        if (await act(() => api(`/watch/errors/${id}`, "POST", { status: key, note }), key === "fixed" ? "고쳤어요로 바꿨어요" : "바꿨어요")) reload("errors");
      };
      // 2.0.0 [RVA-02] 검토 ADM-13: 기본 입력 창 대신 앱의 창.
      if (key === "fixed") return askText({ title: "고쳤어요로 바꾸기", label: "무엇을 고쳤나요? (선택)", submit: "고쳤어요", maxlength: 300 }, save);
      return save("");
    },
  };

  const forms = {
    // 2.0.0 [RVQ-06] 비밀번호를 다시 확인하고 패스키 만들기(틀리면 그 자리에서 다시).
    "passkey-reauth": async (form) => {
      const password = new FormData(form).get("password");
      dialog.close();
      await passkeyCreate({ password });
    },
    "incident-open": async (form) => {
      dialog.close();
      if (await act(() => api("/incident", "POST", { note: new FormData(form).get("note") }), "사고를 열었어요")) reload("incident");
    },
    "incident-close": async (form) => {
      dialog.close();
      const f = new FormData(form);
      if (await act(() => api(`/incident/${form.dataset.id}/close`, "POST", { note: f.get("note"), days: Number(f.get("days") ?? 0) }), "사고를 닫았어요")) {
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
    "request-run": async (form) => {
      const f = new FormData(form);
      const id = form.dataset.id;
      const body = { do: f.get("do") };
      const code = String(f.get("code") ?? "").trim();
      if (code) body.code = code;
      dialog.close();
      const failWords = { no_device: "이 요청에는 묶인 문의가 없어요. 복구 코드로 찾아 주세요", code: "그 복구 코드로 찾은 설치가 없어요", closed: "끝난 요청이에요", kind: "이 요청 종류는 대신 할 수 없어요" };
      const call = async (b) => {
        const res = await api(`/data-requests/${id}/run`, "POST", b).catch((e) => ({ error: e?.data?.error ?? e?.message ?? "fail" }));
        if (res?.error) {
          toast(failWords[res.error] ?? "하지 못했어요. 다시 해 주세요");
          return null;
        }
        return res;
      };
      if (body.do === "export") {
        const res = await call(body);
        if (!res) return;
        const url = URL.createObjectURL(new Blob([JSON.stringify(res.file, null, 2)], { type: "application/json" }));
        const a = document.createElement("a");
        a.href = url;
        a.download = `bamnat-data-${id}-${new Date().toISOString().slice(0, 10)}.json`;
        document.body.append(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        toast(`${Object.keys(res.proof.tables).length}개 표 ${sumOf(res.proof.tables)}줄을 받았어요. 증빙을 남겼어요`);
        reload("requests");
        return;
      }
      danger({ title: "이 설치의 자료를 모두 지울까요?", text: "앱의 '모두 지우기'와 같아요. 되돌릴 수 없어요.", label: "모두 지우기",
        rows: [["남는 것", "요청 문의 하나(답을 보내려고), 법이 정한 구매·동의 기록, 진행 중인 제재"], ["증빙", "지운 표와 줄 수가 이 요청에 남아요"]],
        run: async () => {
          const res = await call({ ...body, confirm: true });
          if (!res) return;
          toast(`${Object.keys(res.proof.gone).length}개 표 ${sumOf(res.proof.gone)}줄을 지웠어요. 증빙을 남겼어요`);
          reload("requests");
        } });
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
      // 2.0.0 [RVA-04] 검토 ADM-05: 요금표 저장도 무엇이 얼마에서 얼마로 바뀌는지 한 번 보여 주고 물어요(Play로 보내기처럼).
      const t = S.data.money?.table ?? {};
      const won = (v) => `${Number(v ?? 0).toLocaleString("ko-KR")}`;
      const changed = [["프로 월", t.bamnat_pro?.monthly, body.bamnat_pro.monthly], ["프로 연", t.bamnat_pro?.yearly, body.bamnat_pro.yearly],
        ["프로 1년 이용권", t.bamnat_pro_pass_1y?.pass, body.bamnat_pro_pass_1y.pass], ["수수료 %", t.fee, body.fee],
        ...["team", "staff"].flatMap((k) => (t[k] ?? []).flatMap((b, i) => ["monthly", "yearly", "pass"].map((f) => [`${k === "team" ? "팀" : "직원"} ${b.cap}명 ${{ monthly: "월", yearly: "연", pass: "이용권" }[f]}`, b[f], body[k][i]?.[f]])))]
        .filter(([, a, b]) => Number(a ?? 0) !== Number(b ?? 0));
      if (!changed.length) return toast("바뀐 값이 없어요");
      A.danger({ title: "가격표를 저장할까요?", text: "앱의 가격 안내와 매출 계산이 이 값을 따라요. Play Console 가격과 같은지 한 번 더 봐 주세요.",
        rows: changed.slice(0, 8).map(([k, a, b]) => [k, `${won(a)} → ${won(b)}`]).concat(changed.length > 8 ? [["그 밖", `${changed.length - 8}칸`]] : []),
        label: "저장", tone: "primary", run: async () => { if (await act(() => api("/money/prices", "POST", body), "가격표를 저장했어요")) reload("money"); } });
    },
  };

  return { opsViews: views, opsLoads: loads, opsHas: has, stageStrip, passkeyCard, passkeyAssert, passkeyOk, watchBoard, personPanel, storyCard, incidentBanner, materialsTab, actions, forms };
}
