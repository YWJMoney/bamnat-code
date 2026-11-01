// 1.36 [GRW-07] 할 일함 and [GRW-04][GRW-05][GRW-06] 스튜디오 요구·기본 도구 후보·손잡이 지도. Sent to the browser as its own text
// (String(fn)) like the other screen groups and called with what the app shares (A); it may use no name from outside itself.
//   할 일함: 모든 들어온 일을 한 줄씩, 기한 넘김·다시 열림이 맨 위. 상태(새로 → 보는 중 → 기다림 → 끝)는 줄에서 바로 바꾸고, 끝낼 때와
//            기다릴 때는 이유를 골라요. 여러 개 골라 한 번에, 되돌리기 알림, 줄마다 기록. j/k·Enter·s(보는 중)·w(기다림)·e(끝내기)·h(기록).
//   스튜디오 요구: 묶음 카드(같은 요구 N명, 예시 글), 장터에 올리기·정식으로 만들기·판에 나옴·하지 않기·합치기·제목 고치기·되돌리기.
export function workViews(A) {
  const { S, L, esc, can, href, seg, skeleton, empty, icon, P, api, act, load, render, toast, openDialog, dialog, ago, inboxLink } = A;

  const STATE_TONE = { new: "info", seeing: "warn", waiting: "", done: "ok" };
  const linkOf = (x) => x.link ?? inboxLink(x);

  function hoursText(h) {
    if (h < 1) return "방금";
    if (h < 24) return `${h}시간째`;
    return `${Math.floor(h / 24)}일째`;
  }

  function dueText(x, now) {
    if (!x.due) return "";
    const left = Math.round((Date.parse(x.due) - now) / 60000);
    const t = L.slaText(left);
    return `<span class="pill ${x.late ? "bad" : t.tone}">${esc(left < 0 ? t.text.replace("목표", "기한") : `기한 ${t.text}`)}</span>`;
  }

  // ---- 할 일함 ----

  function workRow(x, now, names) {
    const pick = S.workPick ? `<label class="pick"><input type="checkbox" data-act="work-pick" data-key="${esc(x.key)}"${S.workPick.has(x.key) ? " checked" : ""}><span class="sr">고르기</span></label>` : "";
    const state = `<span class="pill ${STATE_TONE[x.state]}">${esc(x.stateName)}${x.reasonName ? ` · ${esc(x.reasonName)}` : ""}</span>`;
    const back = x.reopened > 0 && x.state === "new" ? `<span class="pill bad" title="끝낸 뒤 다시 왔어요">다시 열림${x.reopened > 1 ? ` ${x.reopened}` : ""}</span>` : "";
    const who = x.by ? ` · ${x.byMe ? "나" : esc(x.by)}` : "";
    const link = linkOf(x);
    return `<li data-key="${esc(x.key)}"><div class="row">${pick}
      <span class="row-main"><b><span class="kind">${esc(x.typeName)}</span> ${link ? `<a href="${esc(link)}">${esc(x.title)}</a>` : esc(x.title)}</b>
        <small>${esc(hoursText(x.age))}${x.n ? ` · ${x.n}${["recipe_flag", "flagged_post", "hidden_post", "recipe_hidden", "maker_flag", "photo"].includes(x.type) ? "번 신고" : x.type === "studio_need" ? "명" : x.type === "tool" ? "명이 씀" : "건"}` : ""}${x.meta ? ` · ${esc(x.meta)}` : ""}${who}${x.note ? ` · ${esc(x.note)}` : ""}</small></span>
      <span class="row-side">${back}${state}${dueText(x, now)}
        ${x.state === "new" ? `<button class="btn small" data-act="work-set" data-key="${esc(x.key)}" data-state="seeing" title="s">보는 중</button>` : ""}
        ${x.state !== "waiting" && x.state !== "done" ? `<button class="btn small ghost" data-act="work-wait" data-key="${esc(x.key)}" title="w">기다림</button>` : ""}
        ${x.state !== "done" ? `<button class="btn small primary" data-act="work-done" data-key="${esc(x.key)}" title="e">끝내기</button>`
          : `<button class="btn small" data-act="work-set" data-key="${esc(x.key)}" data-state="new">다시 열기</button>`}
        <button class="btn small ghost" data-act="work-log" data-key="${esc(x.key)}" title="h">기록</button></span></div></li>`;
  }

  function workView() {
    const d = S.data.work;
    if (!d) return `<div class="tiles core">${'<div class="tile skeleton"></div>'.repeat(4)}</div>${skeleton(3)}`;
    const c = d.counts;
    const now = Date.parse(d.now);
    const st = S.workState ?? "open";
    const gr = S.workGroup ?? "";
    const tile = (k, v, desc, key, hot) => `<button type="button" class="tile kpi${hot && v ? " hot" : ""}" data-act="work-state" data-key="${key}" aria-pressed="${st === key}">
      <span class="k">${esc(k)}</span><span class="v">${v ?? 0}</span><span class="d">${esc(desc)}</span></button>`;
    const sum = d.summary;
    const tiles = `<div class="tiles core">${tile("남은 일", c.open, `오늘 들어온 일 ${sum.inToday}건`, "open", false)}${tile("기한 넘음", c.late, "맨 위에 있어요", "late", true)}
      ${tile("기다림", c.waiting, "7일 지나면 기한 넘음", "waiting", false)}${tile("오늘 끝냄", sum.doneToday, sum.byWho.length ? sum.byWho.map((w) => `${w.name} ${w.n}`).join(" · ") : "아직 없어요", "done", false)}</div>`;
    const segs = `<div class="segmented" role="group" aria-label="상태">${seg("work-state", "open", st, "남은 것", c.open)}${seg("work-state", "new", st, "새로", c.new)}
      ${seg("work-state", "seeing", st, "보는 중", c.seeing)}${seg("work-state", "waiting", st, "기다림", c.waiting)}${seg("work-state", "mine", st, "내 것", c.mine)}${seg("work-state", "done", st, "끝", c.done)}</div>`;
    const groups = `<nav class="chips" aria-label="종류">${[["", "모두", c.open], ...Object.entries(d.names.groups).map(([k, v]) => [k, v, c.groups[k]])].map(([k, label, n]) =>
      `<button type="button" class="chip pick" data-act="work-group" data-key="${k}" aria-pressed="${gr === k}">${esc(label)}${n ? ` <span class="n">${n}</span>` : ""}</button>`).join("")}</nav>`;
    const picking = S.workPick;
    const bar = `<div class="actions start">${picking ? `<span class="meta">${picking.size}개 고름</span>
        <button class="btn small" data-act="work-pick-all">모두 고르기</button>
        <button class="btn small" data-act="work-bulk" data-state="seeing"${picking.size ? "" : " disabled"}>보는 중</button>
        <button class="btn small" data-act="work-wait"${picking.size ? "" : " disabled"}>기다림</button>
        <button class="btn small primary" data-act="work-done"${picking.size ? "" : " disabled"}>끝내기</button>
        <button class="btn small ghost" data-act="work-pick-mode">그만 고르기</button>`
      : `<button class="btn small" data-act="work-pick-mode">여러 개 고르기</button><button class="btn small ghost" data-act="work-log">오늘의 기록</button>`}
      <span class="meta">${c.reopened ? `다시 열린 일 ${c.reopened}건 · ` : ""}j/k 이동 · Enter 열기 · s 보는 중 · w 기다림 · e 끝내기 · h 기록</span></div>`;
    const list = d.items.length ? `<ul class="rows work">${d.items.map((x) => workRow(x, now, d.names)).join("")}</ul>`
      : empty(st === "done" ? "끝낸 일 중 남아 있는 것이 없어요" : "할 일이 없어요", st === "open" ? "새로 들어오면 여기 맨 아래에, 기한이 넘으면 맨 위에 와요." : "");
    return `${tiles}<section class="card">${segs}${groups}${bar}${list}</section>`;
  }

  const DEFAULT_DONE = { inquiry: "answered", studio_need: "handled", photo: "handled", recipe_flag: "handled", tool: "handled" };

  function openDone(keys) {
    const d = S.data.work;
    if (!d || !keys.length) return;
    const type = keys[0].split(":")[0];
    const first = DEFAULT_DONE[type] ?? "handled";
    openDialog(`<form data-form="work-done" data-keys="${esc(keys.join(","))}"><h2>${keys.length > 1 ? `${keys.length}개를 끝낼까요?` : "끝낼까요?"}</h2>
      <p>끝낸 이유를 골라요. 원래 칸에 새 움직임이 생기거나, 7일 뒤에도 처리되지 않고 남아 있으면 다시 와요.</p>
      <div class="radios">${Object.entries(d.names.done).map(([k, v]) => `<label class="radio"><input type="radio" name="reason" value="${k}"${k === first ? " checked" : ""}><span>${esc(v)}</span></label>`).join("")}</div>
      <label for="work-note">메모 (선택)</label><input id="work-note" name="note" maxlength="300" placeholder="예: 1.36에서 고침, 개발사항 d12">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">끝내기</button></div></form>`);
  }

  function openWait(keys) {
    const d = S.data.work;
    if (!d || !keys.length) return;
    openDialog(`<form data-form="work-wait" data-keys="${esc(keys.join(","))}"><h2>무엇을 기다리나요?</h2>
      <p>기다림은 7일이 지나면 기한 넘김으로 맨 위에 와요.</p>
      <div class="radios">${Object.entries(d.names.wait).map(([k, v], i) => `<label class="radio"><input type="radio" name="reason" value="${k}"${i === 0 ? " checked" : ""}><span>${esc(v)}</span></label>`).join("")}</div>
      <label for="wait-note">메모 (선택)</label><input id="wait-note" name="note" maxlength="300">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">기다림으로</button></div></form>`);
  }

  async function openLog(key) {
    const d = await api(`/work/log${key ? `?key=${encodeURIComponent(key)}` : ""}`).catch(() => null);
    if (!d) return;
    openDialog(`<div><h2>${key ? "이 일의 기록" : "할 일함 기록"}</h2>
      ${d.items.length ? `<ul class="feed">${d.items.map((e) => `<li><span class="when">${esc(ago(e.at))}</span><span>${key ? "" : `<span class="meta">${esc(e.key)}</span> `}${esc(e.actionName)}${e.reasonName ? ` · ${esc(e.reasonName)}` : ""}${e.note ? ` · ${esc(e.note)}` : ""}</span>
        <span class="who">${esc(e.by || "자동")}</span></li>`).join("")}</ul>` : '<p class="hint">아직 기록이 없어요.</p>'}
      <div class="actions"><button class="btn" type="button" data-act="close">닫기</button></div></div>`);
  }

  async function setState(keys, state, reason = "", note = "") {
    const r = await act(() => api("/work", "POST", { keys, state, reason, note }),
      `${keys.length > 1 ? `${keys.length}개를 ` : ""}${S.data.work?.names.states[state] ?? state}(으)로 바꿨어요`,
      (res) => act(() => Promise.all(Object.entries(res.before).map(([k, was]) => api("/work", "POST", { keys: [k], state: was === "done" ? "new" : was }))), "되돌렸어요").then(() => load()));
    if (r) S.workPick = null;
    load();
    return r;
  }

  /** The key of the line in focus (j/k), else the first line. */
  function cursorKey() {
    const rows = [...document.querySelectorAll("#main .rows.work > li")];
    return (rows[S.cursor] ?? rows[0])?.dataset?.key ?? null;
  }

  // ---- 스튜디오 요구·기본 도구 후보·손잡이 지도 ----

  const GROWTH_TABS = [["needs", "스튜디오 요구"], ["unsolved", "장터에서도 못 푼 것"], ["tools", "기본 도구 후보"], ["map", "손잡이 지도"], ["ended", "끝난 요구"]];

  function needCard(n, edit, others) {
    const st = { open: "info", market: "warn", building: "warn", solved: "ok", official: "ok", declined: "" }[n.state];
    const live = ["open", "market", "building"].includes(n.state);
    return `<li class="item"><div class="card-head"><h3>${esc(n.title)}</h3><span class="pill ${st}">${esc(n.stateName)}</span></div>
      <p class="meta"><b>${n.askers}명</b>이 부탁 (${n.asks}건)${n.area ? ` · ${esc(n.area)}${n.tool ? ` › ${esc(n.tool)}` : ""}` : ""}${n.style ? ` · ${esc(n.style)}` : ""} · ${esc(ago(n.at))}에 처음${n.code ? ` · 풀어 준 작품 ${esc(n.code)}` : ""}${n.version ? ` · ${esc(n.version)} 판` : ""}</p>
      ${n.note ? `<p class="hint">${esc(n.note)}</p>` : ""}
      <ul class="samples">${n.samples.map((x) => `<li><span class="meta">${x.src === "req" ? "요청 판" : "스튜디오"}</span> ${esc(x.text)}</li>`).join("")}</ul>
      ${edit ? `<div class="actions start">
        ${n.state === "open" ? `<button class="btn small" data-act="need" data-id="${n.id}" data-key="market">장터에 올리기</button>` : ""}
        ${live ? `<button class="btn small" data-act="need-note" data-id="${n.id}" data-key="build">정식으로 만들기</button>
          <button class="btn small primary" data-act="need-version" data-id="${n.id}">판에 나옴</button>
          <button class="btn small danger" data-act="need-note" data-id="${n.id}" data-key="decline">하지 않기</button>` : `<button class="btn small" data-act="need" data-id="${n.id}" data-key="reopen">되돌리기</button>`}
        <button class="btn small ghost" data-act="need-title" data-id="${n.id}" data-title="${esc(n.title)}">제목 고치기</button>
        ${others.length ? `<button class="btn small ghost" data-act="need-merge" data-id="${n.id}">합치기</button>` : ""}</div>` : ""}</li>`;
  }

  function toolsBlock(d, edit) {
    const t = S.growthTools ?? "candidates";
    const c = d.counts ?? {};
    const rules = d.rules;
    const segs = `<div class="segmented" role="group" aria-label="단계">${seg("growth-tools", "candidates", t, "기본 도구 후보", c.candidates)}${seg("growth-tools", "tools", t, "모두의 도구", c.tools)}
      ${seg("growth-tools", "later", t, "다음에", c.later)}${seg("growth-tools", "adopted", t, "채택함", c.adopted)}</div>`;
    const hint = `<p class="hint">표가 아니라 실제로 입힌 사람 수와 7일 뒤에도 쓰는 비율로 저절로 올라와요. 모두의 도구: ${rules.toolUsers}명·${rules.toolKept}% 이상, 기본 도구 후보: ${rules.candidateUsers}명·${rules.candidateKept}% 이상, 신고가 있으면 내려가요.
      판마다 위쪽만 정식 손잡이로 만들고, 채택하면 출시 노트에 만든 분 이름이 들어가요.</p>`;
    if (!d.items.length) return `${segs}${hint}${empty("아직 없어요", "장터 작품이 많이 쓰이면 저절로 올라와요.")}`;
    return `${segs}${hint}<ul class="rows">${d.items.map((x) => `<li><div class="row"><span class="row-main"><b>${esc(x.title)}</b>
      <small>${x.users}명이 씀 · 7일 유지 ${x.kept}% · 좋아요 ${x.likes} · 신고 ${x.flags}${x.fromNeeds ? ` · 스튜디오 요구에서 ${x.fromNeeds}` : ""} · 만든 분 ${esc(x.nick ?? x.maker ?? "운영자")} · ${esc(x.code)}${x.version ? ` · ${esc(x.version)} 판 채택` : ""}</small></span>
      ${edit ? `<span class="row-side">${x.tier === 3 && t !== "later" ? `<button class="btn small primary" data-act="tool-adopt" data-id="${x.code}">기본 도구로 채택</button><button class="btn small" data-act="tool" data-id="${x.code}" data-key="later">다음에</button>` : ""}
        ${x.tier === 4 || t === "later" ? `<button class="btn small" data-act="tool" data-id="${x.code}" data-key="undo">되돌리기</button>` : ""}</span>` : ""}</div></li>`).join("")}</ul>`;
  }

  function mapBlock(d) {
    const v = S.growthVersion ?? "";
    return `<p class="hint">요구가 많은 손잡이 순위예요. 기다리는 분 = 아직 풀리지 않은 묶음에서 부탁한 분.</p>
      <div class="table-wrap"><table class="table"><thead><tr><th scope="col">영역</th><th scope="col">도구</th><th scope="col">기다리는 분</th><th scope="col">모두</th><th scope="col">사용자 부품으로 풀림</th><th scope="col">손잡이로 나옴</th></tr></thead>
      <tbody>${d.items.map((r) => `<tr><td>${esc(r.area)}</td><td>${esc(r.tool || "-")}</td><td><b>${r.waiting}</b></td><td>${r.askers}</td><td>${r.solved}</td><td>${r.official}</td></tr>`).join("") || '<tr><td colspan="6">아직 요구가 없어요</td></tr>'}</tbody></table></div>
      <form data-form="growth-lines" class="mini"><label for="gv">출시 노트 줄 만들기 (판)</label><input id="gv" name="version" placeholder="1.36" value="${esc(v)}" maxlength="8"><button class="btn small" type="submit">만들기</button></form>
      ${d.lines?.length ? `<pre class="copy-box">${esc(d.lines.join("\n"))}</pre><button class="btn small" data-act="growth-copy">복사</button>` : v ? '<p class="hint">이 판에 나온 요구·채택한 도구가 없어요.</p>' : ""}`;
  }

  function growthView() {
    const t = S.growthTab ?? "needs";
    const d = S.data.growth;
    const tabs = `<div class="chips">${GROWTH_TABS.filter(([k]) => (k === "tools" ? can("community") : true))
      .map(([k, label]) => `<button type="button" class="chip pick" data-act="growth-tab" data-key="${k}" aria-pressed="${t === k}">${esc(label)}${k === "needs" && d?.counts ? ` <span class="n">${(d.counts.open ?? 0) + (d.counts.market ?? 0) + (d.counts.building ?? 0)}</span>` : k === "unsolved" && d?.counts?.unsolved ? ` <span class="n">${d.counts.unsolved}</span>` : ""}</button>`).join("")}</div>`;
    if (!d || d.tab !== t) return `${tabs}${skeleton(2)}`;
    if (t === "tools") return `${tabs}${toolsBlock(d, can("community", "edit"))}`;
    if (t === "map") return `${tabs}${mapBlock(d)}`;
    const edit = can("inquiries", "edit");
    const intro = t === "unsolved" ? `<p class="hint">장터 요청 판에 올린 지 ${d.rules.staleDays}일이 지나도 아무도 사용자 부품으로 못 푼 요구예요. 지금 부품으로는 못 만드는 것일 수 있어요. 정식 손잡이 후보로 보세요.</p>`
      : t === "needs" ? `<p class="hint">스튜디오 "원하는 꾸미기가 없나요?"와 요청 판에서 ${d.rules.staleDays}일 동안 아무도 못 만든 요청이 비슷한 것끼리 저절로 묶여요. ${d.rules.marketAskers}명이 모이면 장터 요청 판에 저절로 올라가고, 누가 만들어 완성하면 부탁한 분 모두에게 답이 가요.</p>` : "";
    if (!d.items.length) return `${tabs}${intro}${empty("요구가 없어요", "")}`;
    return `${tabs}${intro}<ul class="rows needs">${d.items.map((n) => needCard(n, edit, d.items.filter((o) => o.id !== n.id))).join("")}</ul>`;
  }

  async function needAct(id, body, done) {
    const r = await act(() => api(`/growth/needs/${id}`, "POST", body), done);
    load();
    return r;
  }

  const actions = {
    "work-state": ({ key }) => { S.workState = key; S.data.work = null; S.cursor = -1; load(); },
    "work-group": ({ key }) => { S.workGroup = key; S.data.work = null; S.cursor = -1; load(); },
    "work-pick-mode": () => { S.workPick = S.workPick ? null : new Set(); render({ keepScroll: true }); },
    "work-pick": ({ key }) => {
      if (!S.workPick) return;
      if (S.workPick.has(key)) S.workPick.delete(key);
      else S.workPick.add(key);
      render({ keepScroll: true });
    },
    "work-pick-all": () => {
      const all = (S.data.work?.items ?? []).map((x) => x.key);
      S.workPick = S.workPick && S.workPick.size === all.length ? new Set() : new Set(all);
      render({ keepScroll: true });
    },
    "work-set": ({ key, state }) => setState([key], state),
    "work-bulk": ({ state }) => setState([...(S.workPick ?? [])], state),
    "work-done": ({ key }) => openDone(key ? [key] : [...(S.workPick ?? [])]),
    "work-wait": ({ key }) => openWait(key ? [key] : [...(S.workPick ?? [])]),
    "work-log": ({ key }) => openLog(key),
    "growth-tab": ({ key }) => { S.growthTab = key; S.data.growth = null; load(); },
    "growth-tools": ({ key }) => { S.growthTools = key; S.data.growth = null; if (S.route.name === "recipes") S.data.recipes = null; load(); },
    "growth-copy": async () => {
      const text = (S.data.growth?.lines ?? []).join("\n");
      try {
        await navigator.clipboard.writeText(text);
        toast("복사했어요");
      } catch {
        toast("복사하지 못했어요. 글을 길게 눌러 복사해요");
      }
    },
    need: ({ id, key }) => needAct(id, { action: key }, key === "market" ? "장터 요청 판에 올렸어요" : "되돌렸어요"),
    "need-note": ({ id, key }) => openDialog(`<form data-form="need-note" data-id="${id}" data-key="${key}"><h2>${key === "decline" ? "하지 않기로 할까요?" : "정식으로 만들까요?"}</h2>
      <p>${key === "decline" ? "부탁한 분 모두에게 이 이유가 답으로 가요." : "개발계획 순번이나 갈래를 적어 두면 누가 보든 이어 가요."}</p>
      <label for="need-n">${key === "decline" ? "이유 (꼭)" : "메모"}</label><textarea id="need-n" name="note" maxlength="300"${key === "decline" ? " required" : ""}></textarea>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn ${key === "decline" ? "danger" : "primary"}" type="submit">${key === "decline" ? "하지 않기" : "만들기로"}</button></div></form>`),
    "need-version": ({ id }) => openDialog(`<form data-form="need-version" data-id="${id}"><h2>어느 판에 나오나요?</h2>
      <p>부탁한 분 모두에게 "이 판에서 기본 손잡이로 나와요"라는 답이 가고, 손잡이 지도의 출시 노트 줄에 들어가요. 에뮬레이터에서 확인한 뒤에만 눌러요.</p>
      <label for="need-v">판</label><input id="need-v" name="version" required pattern="\\d{1,2}\\.\\d{1,2}(\\.\\d{1,3})?" placeholder="1.36">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">알리기</button></div></form>`),
    "need-title": ({ id, title }) => openDialog(`<form data-form="need-title" data-id="${id}"><h2>제목 고치기</h2><p>장터 요청 판에 올라간 줄도 함께 바뀌어요.</p>
      <label for="need-t">제목</label><input id="need-t" name="title" maxlength="60" required value="${esc(title)}">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`),
    "need-merge": ({ id }) => {
      const others = (S.data.growth?.items ?? []).filter((n) => n.id !== id);
      openDialog(`<form data-form="need-merge" data-id="${id}"><h2>어느 묶음에 합칠까요?</h2><p>부탁한 분은 휴대폰 기준으로 다시 세요. 이 묶음의 장터 줄은 가려져요.</p>
        <div class="radios">${others.map((n, i) => `<label class="radio"><input type="radio" name="into" value="${n.id}"${i === 0 ? " checked" : ""}><span>${esc(n.title)} (${n.askers}명)</span></label>`).join("")}</div>
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">합치기</button></div></form>`);
    },
    tool: async ({ id, key }) => { await act(() => api(`/growth/tools/${id}`, "POST", { action: key }), key === "later" ? "다음에 보기로 했어요" : "되돌렸어요"); load(); },
    "tool-adopt": ({ id }) => openDialog(`<form data-form="tool-adopt" data-id="${id}"><h2>기본 도구로 채택할까요?</h2>
      <p>정식 손잡이로 만들 판을 적어요. 출시 노트 '모두의 도구에서 기본이 된 것'에 만든 분 이름과 함께 들어가요.</p>
      <label for="tool-v">판</label><input id="tool-v" name="version" required placeholder="1.37" maxlength="8"><label for="tool-n">메모</label><input id="tool-n" name="note" maxlength="300">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">채택</button></div></form>`),
  };

  const forms = {
    "work-done": (form) => {
      const data = new FormData(form);
      dialog.close();
      return setState(form.dataset.keys.split(","), "done", String(data.get("reason") ?? ""), String(data.get("note") ?? ""));
    },
    "work-wait": (form) => {
      const data = new FormData(form);
      dialog.close();
      return setState(form.dataset.keys.split(","), "waiting", String(data.get("reason") ?? ""), String(data.get("note") ?? ""));
    },
    "need-note": (form) => {
      dialog.close();
      const key = form.dataset.key;
      return needAct(form.dataset.id, { action: key, note: new FormData(form).get("note") }, (r) => (key === "decline" ? `하지 않기로 했어요. ${r.told}분께 답했어요` : "정식으로 만드는 중으로 바꿨어요"));
    },
    "need-version": (form) => {
      dialog.close();
      return needAct(form.dataset.id, { action: "official", version: new FormData(form).get("version") }, (r) => `${r.told}분께 알렸어요`);
    },
    "need-title": (form) => {
      dialog.close();
      return needAct(form.dataset.id, { action: "title", title: new FormData(form).get("title") }, "제목을 바꿨어요");
    },
    "need-merge": (form) => {
      dialog.close();
      return needAct(form.dataset.id, { action: "merge", into: new FormData(form).get("into") }, (r) => `합쳤어요. 이제 ${r.askers}명이에요`);
    },
    "tool-adopt": async (form) => {
      const data = new FormData(form);
      dialog.close();
      await act(() => api(`/growth/tools/${form.dataset.id}`, "POST", { action: "adopt", version: data.get("version"), note: data.get("note") }), "기본 도구로 채택했어요");
      load();
    },
    "growth-lines": (form) => {
      S.growthVersion = String(new FormData(form).get("version") ?? "").trim();
      S.data.growth = null;
      load();
    },
  };

  /** [GRW-07] s, w, e, h on 할 일함: the line in focus (the first when none). */
  function workKey(what) {
    const key = cursorKey();
    if (!key) return false;
    if (what === "seeing") setState([key], "seeing");
    if (what === "wait") openWait([key]);
    if (what === "done") openDone([key]);
    if (what === "log") openLog(key);
    return true;
  }

  return { workView, growthView, toolsBlock, workKey, actions, forms };
}
