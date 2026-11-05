// 1.37 [ADM2-01][ADM2-05] 흐름(관제)과 [ADM2-03] Claude 운영 열쇠. Sent to the browser as its own text (String(fn)) like the other
// screen groups and called with what the app shares (A); it may use no name from outside itself.
//   흐름: 들어온 문의 → 개발 대기 → Claude에게 넘김 → 개발 중 → 고침·출시 전 → 출시됨·답 남음. 단계마다 건수·가장 오래 기다린 시간·막힘과
//         다음 할 일 단추 하나, 30일 운영 숫자와 8주 추이, 'Play에 나간 판' 저장(묶음 답 미리 보기 뒤).
//   운영 열쇠: 주인이 만드는 열쇠 하나 = 'Claude' 관리자 하나. 한 번만 보이고, 끊기·기한·하루 한도.
export function flowViews(A) {
  const { S, esc, can, href, skeleton, empty, icon, P, api, act, load, render, toast, openDialog, dialog, confirmBox, ago } = A;

  const waitText = (h) => (h < 1 ? "방금" : h < 48 ? `${h}시간` : `${Math.floor(h / 24)}일`);
  const num = (v, unit = "") => (v === null || v === undefined ? "—" : `${v}${unit}`);

  function stageCard(s, i, next) {
    const go = s.act.route ? `<a class="btn small${s.key === next ? " primary" : ""}" href="${href(s.act.route)}">${esc(s.act.label)}</a>`
      : `<button class="btn small${s.key === next ? " primary" : ""}" type="button" data-act="${s.act.act}">${esc(s.act.label)}</button>`;
    return `<li class="pipe-step${s.stuck ? " stuck" : ""}${s.n ? "" : " zero"}${s.key === next ? " next" : ""}" data-stage="${s.key}">
      <div class="pipe-top"><span class="pipe-no">${i + 1}</span><b>${esc(s.name)}</b>${s.stuck ? '<span class="pill bad">막힘</span>' : ""}</div>
      <div class="pipe-n"><span class="v">${s.n}</span><span class="u">건</span></div>
      <p class="pipe-old">${s.n ? `가장 오래 ${esc(waitText(s.hours))}째` : "비어 있어요"}</p>
      <p class="pipe-hint">${esc(s.hint)}</p>
      <div class="pipe-act">${go}</div></li>`;
  }

  /** 8주 들어옴·첫 답 막대(같은 축). 색만으로 나누지 않게 범례와 숫자 표를 함께. */
  function weekChart(weeks) {
    const top = Math.max(1, ...weeks.flatMap((w) => [w.in, w.out]));
    const W = 560;
    const H = 150;
    const pad = 22;
    const band = (W - pad) / weeks.length;
    const bw = Math.min(16, band / 3);
    const y = (v) => H - 20 - ((H - 34) * v) / top;
    const bars = weeks.map((w, i) => {
      const x = pad + i * band + band / 2;
      const label = w.from.slice(5, 10).replace("-", ".");
      return `<g><rect class="bar-in" x="${x - bw - 1}" y="${y(w.in)}" width="${bw}" height="${H - 20 - y(w.in)}" rx="3"><title>${label} 주 들어옴 ${w.in}</title></rect>
        <rect class="bar-out" x="${x + 1}" y="${y(w.out)}" width="${bw}" height="${H - 20 - y(w.out)}" rx="3"><title>${label} 주 첫 답 ${w.out}</title></rect>
        <text x="${x}" y="${H - 4}" text-anchor="middle">${label}</text></g>`;
    }).join("");
    const grid = [0, 0.5, 1].map((f) => `<line x1="${pad}" x2="${W}" y1="${y(top * f)}" y2="${y(top * f)}"/><text class="axis" x="0" y="${y(top * f) + 4}">${Math.round(top * f)}</text>`).join("");
    return `<figure class="week-chart"><svg viewBox="0 0 ${W} ${H}" role="img" aria-label="8주 동안 들어온 문의와 첫 답 수"><g class="grid">${grid}</g>${bars}</svg>
      <figcaption><span class="key in"></span>들어온 문의 <span class="key out"></span>첫 답 보냄 · 주마다</figcaption></figure>`;
  }

  function guide(d) {
    const keyOn = d.claude.keys > 0;
    const steps = keyOn
      ? [["문의가 들어와요", "앱에서 보낸 문의·버그·제안이 '새 문의'에 쌓여요. 아무것도 안 해도 돼요."],
        ["Claude에게 \"처리해\"", "프로젝트 채팅에 한 마디. Claude가 운영 열쇠로 이 장부를 직접 읽고, 답할 것은 답하고 개발할 것은 개발사항으로 만들어 개발까지 이어 가요."],
        ["Play에 올리면 '나간 판' 저장", "출시한 판을 여기 저장(또는 Claude에게 \"1.37.0 나갔어\")하면 고친 문의마다 업데이트 안내가 한꺼번에 가요."]]
      : [["운영 장부에서 나누기", "'새 문의'를 운영 장부에서 답하거나 개발사항으로 넘겨요. 줄마다 겹침 후보와 보낼 답이 적혀 있어요."],
        ["Claude에게 넘기기", "운영 장부 › 'Claude에게 넘기기'로 복사해 프로젝트 채팅에 붙여 넣어요. Claude가 개발하고 결과 줄(d… 완료 1.37.0)을 줘요."],
        ["결과 붙여 넣기 · 나간 판 저장", "결과 줄을 운영 장부 › '개발 결과 붙여 넣기'에, Play에 올린 뒤 나간 판을 여기 저장해요. 운영 열쇠를 만들면 이 복사·붙여 넣기가 없어져요."]];
    const setup = [];
    if (!d.shipped) setup.push(`<li><b>나간 판을 적어 주세요.</b> 지금 Play에 나가 있는 판(예: 1.36.1)을 저장해야 출시 전 '반영'·'업데이트하세요' 답이 나가지 않아요. <button class="btn small" type="button" data-act="flow-ship">나간 판 저장</button></li>`);
    if (!keyOn && S.me?.owner) setup.push(`<li><b>Claude 운영 열쇠를 만들어요.</b> 한 번 넣어 두면 "처리해" 한 마디로 Claude가 직접 처리해요. <a class="btn small" href="${href("keys")}">Claude 열쇠</a></li>`);
    return `${setup.length ? `<section class="card setup-card" aria-label="처음 한 번"><div class="card-head"><h2>처음 한 번만</h2><span class="pill warn">${setup.length}개 남음</span></div><ol class="setup">${setup.join("")}</ol></section>` : ""}
      <details class="card how" data-key="flow-how"${S.opened.get("flow-how") ?? !keyOn ? " open" : ""}><summary><b>이렇게 써요</b><span class="meta">${keyOn ? "Claude 직접 처리 모드" : "복사·붙여 넣기 모드"}</span></summary>
        <ol class="how-steps">${steps.map(([t, x], i) => `<li><span class="step-no">${i + 1}</span><div><b>${esc(t)}</b><p>${esc(x)}</p></div></li>`).join("")}</ol></details>`;
  }

  function flowView() {
    const d = S.data.flow;
    if (!d) return `<div class="pipe-skel">${'<div class="tile skeleton"></div>'.repeat(6)}</div>${skeleton(2)}`;
    const m = d.metrics;
    const next = d.stages.find((s) => s.key === d.next);
    const head = `<section class="flow-head"><div><h2>문의에서 출시까지</h2><p class="meta">앱에서 들어온 일이 지금 어느 단계에 몇 건 있는지, 어디가 막혔는지 한눈에 봐요.</p></div>
      <div class="flow-state"><span class="pill ${d.shipped ? "ok" : "warn"}">${d.shipped ? `나간 판 ${esc(d.shipped.v)}` : "나간 판 없음"}</span>
        <span class="pill ${d.claude.keys ? "info" : ""}">${d.claude.keys ? `Claude 연결됨${d.claude.lastAt ? ` · ${esc(ago(d.claude.lastAt))}` : ""}` : "Claude 열쇠 없음"}</span>
        ${can("devStatus", "edit") ? '<button class="btn small" type="button" data-act="flow-ship">나간 판 저장</button>' : ""}</div></section>`;
    const nextBar = next ? `<div class="next-bar${next.stuck ? " stuck" : ""}" role="status">${icon(P.chevron)}<span><b>지금 할 일</b> ${esc(next.name)} ${next.n}건${next.stuck ? ` · 가장 오래 ${esc(waitText(next.hours))}째 기다려요` : ""}</span>
        ${next.act.route ? `<a class="btn primary small" href="${href(next.act.route)}">${esc(next.act.label)}</a>` : `<button class="btn primary small" type="button" data-act="${next.act.act}">${esc(next.act.label)}</button>`}</div>`
      : `<div class="next-bar calm" role="status">${icon(P.check)}<span><b>막힌 곳이 없어요</b> 새로 들어오면 여기서 알려 드려요.</span></div>`;
    const pipe = `<ol class="pipe" aria-label="단계">${d.stages.map((s, i) => stageCard(s, i, d.next)).join("")}</ol>`;
    const kpi = (k, v, desc, tone = "") => `<div class="tile kpi${tone ? ` ${tone}` : ""}"><span class="k">${esc(k)}</span><span class="v">${v}</span><span class="d">${esc(desc)}</span></div>`;
    const kpis = `<div class="tiles core">${kpi("첫 답까지", num(m.firstAnswerHours, "시간"), "30일 중앙값")}
      ${kpi("답 목표 지킴", num(m.slaKept, "%"), "문의 종류별 목표 안에 첫 답", m.slaKept !== null && m.slaKept < 80 ? "hot" : "")}
      ${kpi("도움이 됐어요", num(m.helpful, "%"), "답을 평가한 분 중")}${kpi("개발 걸린 날", num(m.devDays, "일"), "접수부터 완료까지 중앙값(60일)")}
      ${kpi("고친 판이 닿기까지", num(m.fixDays ?? null, "일"), `문의부터 '고친 판 나왔어요' 안내까지 중앙값(60일${m.fixedTold ? `, ${m.fixedTold}건` : ""})`)}
      ${kpi("밀린 건수", num(m.backlog ?? null, "건"), m.backlogStuck ? `그중 막힌 단계 ${m.backlogStuck}건` : "모든 단계를 더한 수", m.backlogStuck ? "hot" : "")}</div>`;
    const trend = `<section class="card"><div class="card-head"><h2>8주 추이</h2><span class="meta">30일 들어옴 ${m.inMonth} · 답함 ${m.answeredMonth}</span></div>${weekChart(m.weeks)}
      <details class="data-table"><summary>숫자로 보기</summary><table><thead><tr><th scope="col">주 시작</th><th scope="col">들어옴</th><th scope="col">첫 답</th></tr></thead>
      <tbody>${m.weeks.map((w) => `<tr><td>${esc(w.from.slice(0, 10))}</td><td>${w.in}</td><td>${w.out}</td></tr>`).join("")}</tbody></table></details></section>`;
    const built = d.built.length ? `<section class="card"><div class="card-head"><h2>고침 · 출시 전</h2><span class="meta">나간 판을 저장하면 문의마다 업데이트 안내가 가요</span></div>
      <ul class="rows">${d.built.map((b) => `<li><a class="row" href="${href("dev", b.id)}"><span class="row-main"><b>${esc(b.title)}</b><small>문의 ${b.asks}건</small></span>
        <span class="pill ${b.releasedIn ? "gold" : ""}">${b.releasedIn ? esc(b.releasedIn) : "판 없음"}</span>${icon(P.chevron)}</a></li>`).join("")}</ul></section>` : "";
    return `${head}${nextBar}${pipe}${guide(d)}${kpis}<div class="flow-cols">${trend}${built}</div>`;
  }

  // ---- 운영 열쇠 ----

  function keysView() {
    const d = S.data.keys;
    if (!d) return skeleton(2);
    const alive = d.keys.filter((k) => k.alive);
    const rows = d.keys.length ? `<ul class="rows">${d.keys.map((k) => `<li><div class="row"><span class="row-main"><b>${esc(k.label)}</b>
        <small>${esc(k.createdAt.slice(0, 10))} 만듦 · ${k.alive ? `${esc(k.expiresAt.slice(0, 10))}까지` : k.revokedAt ? "끊음" : "기한 지남"}${k.lastAt ? ` · 마지막 ${esc(ago(k.lastAt))}` : " · 아직 안 씀"}${k.today ? ` · 오늘 ${k.today}번` : ""}</small></span>
        <span class="pill ${k.alive ? "ok" : ""}">${k.alive ? "쓰는 중" : "꺼짐"}</span>${k.alive ? `<button class="btn small danger" type="button" data-act="key-revoke" data-id="${k.id}">끊기</button>` : ""}</div></li>`).join("")}</ul>`
      : empty("아직 열쇠가 없어요", "열쇠를 만들어 Claude 프로젝트에 넣으면 \"처리해\" 한 마디로 Claude가 직접 처리해요.");
    return `<section class="card"><div class="card-head"><h2>Claude 운영 열쇠</h2>
        <button class="btn primary small" type="button" data-act="key-make"${alive.length >= d.limits.alive ? " disabled" : ""}>${icon(P.plus)}열쇠 만들기</button></div>
      <p class="hint">열쇠 하나가 'Claude' 관리자 하나예요. 활동 기록에 'Claude'로 남아요. 문의·개발사항 읽기, 답, 개발사항 만들기·상태·묶기, 나간 판 저장만 할 수 있어요.
        지우기·사용자 제한·기능 스위치·공지·관리자 계정·결제는 못 해요. ${d.limits.days}일 기한, 하루 ${d.limits.daily}번(바꾸기 ${d.limits.writes}번)까지, 언제든 바로 끊을 수 있어요.</p>
      ${rows}</section>
      <section class="card"><h2>넣는 곳</h2><ol class="how-steps">
        <li><span class="step-no">1</span><div><b>Claude 프로젝트 설정 › 클라우드 환경</b><p>비밀값(없으면 환경 변수)에 이름 BAMNAT_OPS_KEY로 붙여 넣어요. 채팅에는 붙여 넣지 않아요.</p></div></li>
        <li><span class="step-no">2</span><div><b>네트워크 허용</b><p>같은 곳의 네트워크 허용 목록에 api.bamnat.app을 더해요.</p></div></li>
        <li><span class="step-no">3</span><div><b>"처리해"</b><p>그다음부터는 프로젝트 채팅에 한 마디면 돼요. 계정을 바꾸면 새 프로젝트에 열쇠만 다시 넣고, 옛 열쇠는 여기서 끊어요.</p></div></li></ol></section>`;
  }

  async function shipDry(form) {
    const v = String(new FormData(form).get("version") ?? "").trim();
    const r = await act(() => api("/desk/ship", "POST", { version: v, dry: true }));
    if (!r) return;
    // 1.37 [ADM2-10] 저장하면 일어날 일 셋: 미리 써 둔 출시 답이 묶인 문의에, 업데이트 소식 공지가 앱에, 개발사항마다 준비됐는지.
    const fresh = r.fresh ?? [];
    const missing = fresh.filter((d) => d.asks && !d.shipReply).length;
    const noNews = fresh.filter((d) => !d.news).length;
    openDialog(`<form data-form="flow-ship-go" data-version="${esc(r.version)}"><h2>${esc(r.version)}을 나간 판으로 저장할까요?</h2>
      <p>${r.was ? `지금은 ${esc(r.was)}이에요. ` : ""}${r.version} 이하로 고친 개발사항이 앱에 '반영'으로 보여요.</p>
      ${fresh.length ? `<h3>이번에 나가는 개발사항 ${fresh.length}개</h3><ul class="mini">${fresh.map((d) => `<li>${esc(d.title)} <small>${esc(d.releasedIn)} · 문의 ${d.asks}건</small>
          ${d.news ? `<small class="answer">공지: ${esc(d.news)}</small>` : ""}<span>${d.asks ? `<span class="pill ${d.shipReply ? "ok" : "warn"}">${d.shipReply ? "출시 답 준비됨" : "출시 답 없음(기본 답)"}</span>` : ""}
          <span class="pill ${d.news ? "ok" : "warn"}">${d.news ? "공지 한 줄 있음" : "공지 한 줄 없음"}</span></span></li>`).join("")}</ul>
        ${missing || noNews ? `<p class="note warn">미리 쓴 안내가 빠진 개발사항이 있어요. 개발사항 화면의 ‘출시 안내 미리 쓰기’에서 채우거나, 그대로 저장하면 기본 답이 가요.</p>` : ""}` : ""}
      ${r.answers ? `<h3>문의 ${r.answers}건에 갈 답</h3><ul class="mini">${(r.replies ?? []).map((x) => `<li>${esc(x.title)} <small>문의 ${x.n}건</small><small class="answer">${esc(x.text)}</small></li>`).join("")}</ul>
        <label class="chk"><input type="checkbox" name="notify" checked><span>문의에 업데이트 안내 보내기</span></label>` : "<p>지금 안내를 보낼 문의는 없어요.</p>"}
      ${r.notice ? `<h3>앱에 올라갈 공지 (${r.notice.days}일)</h3><div class="np notice-full"><b>${esc(r.notice.title)}</b><p>${esc(r.notice.body)}</p></div>
        <label class="chk"><input type="checkbox" name="notice" checked><span>업데이트 소식 공지 올리기</span></label>` : fresh.length ? "<p class=\"meta\">공지 한 줄이 있는 개발사항이 없어 공지는 올라가지 않아요.</p>" : ""}
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`, "wide");
  }

  const actions = {
    "flow-ship": () => openDialog(`<form data-form="flow-ship"><h2>Play에 나간 판</h2>
      <p>Play Console에서 출시(프로덕션 또는 사용자에게 열린 트랙)를 마친 판을 적어요. 먼저 무엇이 바뀌는지 보여 드려요.</p>
      <label for="ship-v">판</label><input id="ship-v" name="version" required inputmode="decimal" pattern="\\d{1,2}\\.\\d{1,2}\\.\\d{1,3}" placeholder="1.37.0" value="${esc(S.data.flow?.shipped?.v ?? "")}">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">미리 보기</button></div></form>`),
    "key-make": () => openDialog(`<form data-form="key-make"><h2>운영 열쇠 만들기</h2><p>어디에 넣을 열쇠인지 이름을 붙여요. 열쇠는 만든 직후 한 번만 보여요.</p>
      <label for="key-l">이름</label><input id="key-l" name="label" maxlength="40" value="Claude 프로젝트">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">만들기</button></div></form>`),
    "key-revoke": ({ id }) => confirmBox("이 열쇠를 끊을까요?", "끊으면 Claude가 이 열쇠로 더는 읽거나 처리하지 못해요. 되돌릴 수 없고, 새로 만들어 다시 넣으면 돼요.", "끊기", async () => {
      await act(() => api(`/keys/${id}/revoke`, "POST", {}), "열쇠를 끊었어요");
      S.data.keys = null;
      load();
    }),
    "key-copy": async () => {
      const el = document.getElementById("key-once");
      try {
        await navigator.clipboard.writeText(el?.value ?? "");
        toast("복사했어요. Claude 프로젝트 설정에 붙여 넣어 주세요");
      } catch {
        el?.select();
        toast("복사하지 못했어요. 칸을 길게 눌러 복사해요");
      }
    },
  };

  const forms = {
    "flow-ship": (form) => shipDry(form),
    "flow-ship-go": async (form) => {
      const fd = new FormData(form);
      const notify = Boolean(fd.get("notify"));
      const notice = Boolean(fd.get("notice"));
      dialog.close();
      const r = await act(() => api("/desk/ship", "POST", { version: form.dataset.version, notify, notice }), (x) => `나간 판 ${x.version}을 저장했어요${x.answered ? ` · 문의 ${x.answered}건에 안내` : ""}${x.notice ? " · 업데이트 소식 공지를 올렸어요" : ""}`);
      if (r) { S.data.flow = null; load(); }
    },
    "key-make": async (form) => {
      const r = await act(() => api("/keys", "POST", { label: new FormData(form).get("label") }));
      if (!r) return;
      openDialog(`<div class="key-once"><h2>열쇠를 만들었어요</h2><p><b>이 화면을 닫으면 다시 볼 수 없어요.</b> 지금 복사해서 Claude 프로젝트 설정 › 클라우드 환경 비밀값 BAMNAT_OPS_KEY에 넣어요.</p>
        <label for="key-once">열쇠</label><input id="key-once" readonly value="${esc(r.key)}" class="mono">
        <p class="meta">${esc(r.expiresAt.slice(0, 10))}까지 써요.</p>
        <div class="actions"><button class="btn" type="button" data-act="key-copy">복사</button><button class="btn primary" type="button" data-act="close">넣었어요</button></div></div>`);
      S.data.keys = null;
      load();
    },
  };

  return { flowView, keysView, actions, forms };
}
