// AX 1.30 [ADMX-12] 광장's screens out of src/admin-app.js: 검토, 여러 판 and the 회사 list. Sent to the browser as its
// own text (String(fn)) and called by the app with what it shares (A); it may use no name from outside itself.
//   [ADMX-04] 검토 is one line per item that opens in place: what changed (the server's summary), the score, the wait
//             reason and 채택·거절 on the line; j/k, Space to open, a and x as before. 모양이 다른 판 has its own filter.
//   [ADMX-02][ADMX-03] What changed is the server's diffFacts: one line, then field by field with the words marked.
//             여러 판 is a table: candidates are columns, a cell that differs from the one in use is marked.
//   [ADMX-06] 회사 is a table the server sorts; on a phone each row is a small card.
//   [ADMX-09] 검토 rejects many at once (one reason, undo for all) and 회사 hides or shows many at once.
export function plazaViews(A) {
  const { S, L, esc, can, href, seg, skeleton, empty, lines, day, moment, icon, P, api, act, load, render, toast, openDialog, dialog, syncUrl, refilter } = A;
  const name = (c, dept) => `${esc(c.name)}${dept ? ` <span class="dept">${esc(dept)}</span>` : ""}`;

  // ---- 판 차이 [ADMX-02] ----

  /**
   * What changed against the rule in use: the server's one line, then each changed field as before → after with the
   * words that went and came marked, then the fields only one side has. [title] heads it.
   */
  function changes(c, title = "지금 쓰는 규칙과 다른 점") {
    if (!c) return "";
    const f = c.facts;
    if (!f) {
      // A server from before 1.30: whole lines as they were.
      const items = [...(c.removed ?? []).map((l) => ["del", l]), ...(c.added ?? []).map((l) => ["add", l])];
      if (!items.length) return '<p class="note">지금 쓰는 규칙과 내용이 같아요.</p>';
      return `<div class="diff"><p class="diff-title">${esc(title)}</p><ul>${items.map(([k, [label, text]]) =>
        `<li class="${k}"><span class="sign" aria-label="${k === "add" ? "새 값" : "지금 값"}">${k === "add" ? "+" : "−"}</span><span><b>${esc(label)}</b> ${esc(text)}</span></li>`).join("")}</ul></div>`;
    }
    if (!f.changed.length && !f.added.length && !f.removed.length) return '<p class="note">지금 쓰는 규칙과 내용이 같아요.</p>';
    const changed = f.changed.map((x) => `<li class="chg"><b>${esc(x.label)}</b><span class="was">${L.tokenHtml(x.tokens, "before")}</span><span class="arrow" aria-label="에서">→</span><span class="now">${L.tokenHtml(x.tokens, "after")}</span></li>`);
    const added = f.added.map(([label, text]) => `<li class="add"><b>${esc(label)}</b><span class="now"><ins>${esc(text)}</ins></span><span class="pill ok">더함</span></li>`);
    const removed = f.removed.map(([label, text]) => `<li class="del"><b>${esc(label)}</b><span class="was"><del>${esc(text)}</del></span><span class="pill bad">뺌</span></li>`);
    return `<div class="diff facts"><p class="diff-title">${esc(title)}${c.summary ? `<span class="sum">${esc(c.summary)}</span>` : ""}</p><ul>${[...changed, ...added, ...removed].join("")}</ul></div>`;
  }

  // ---- 검토 [ADMX-04] ----

  const groupKey = (g) => `${g.company.id}.${g.setup}`;

  function reviewView() {
    const q = S.data.queue;
    const points = S.overview?.settings?.adoptPoints ?? 3;
    const editable = can("review", "edit");
    const shape = q ? q.groups.filter((g) => g.shape) : [];
    const counts = q ? { all: q.groups.length + q.asks.length + q.flagged.length, groups: q.groups.length, asks: q.asks.length, flagged: q.flagged.length, shape: shape.length } : {};
    const f = S.reviewFilter;
    const picking = Boolean(S.reviewPick) && editable;
    const head = `<div class="segmented" role="group" aria-label="보기">
        ${seg("review-filter", "all", f, "전체", counts.all)}${seg("review-filter", "groups", f, "제보", counts.groups)}
        ${seg("review-filter", "asks", f, "앱에 없는 방식", counts.asks)}${seg("review-filter", "flagged", f, "달라요 달린 규칙", counts.flagged)}
        ${seg("review-filter", "shape", f, "모양이 다른 판", counts.shape)}</div>
      <div class="toolbar"><p class="hint grow">서로 다른 ${S.overview?.settings?.adoptPersons ?? 3}명 이상, ${points}점, 맞았어요 ${S.overview?.settings?.adoptAgree ?? 75}% 이상이면 자동 채택돼요. 명세서로 맞춘 제보는 2점이에요.${editable ? " 줄을 누르면 펼쳐져요. j·k로 고르고 a 채택, x 거절." : " 보기 권한이라 채택·거절은 할 수 없어요."}</p>
        ${editable && q?.groups.length ? `<button class="btn small" data-act="review-pick-mode" aria-pressed="${picking}">${picking ? "선택 마침" : `${icon(P.check)}여러 개 고르기`}</button>` : ""}</div>`;
    if (!q) return head + skeleton();
    const parts = [];
    const groups = f === "shape" ? shape : q.groups;
    if ((f === "all" || f === "groups" || f === "shape") && groups.length) {
      const title = f === "shape" ? "" : f === "all" ? `<h2 class="section">채택을 기다리는 제보<span class="count">${groups.length}</span></h2>` : "";
      const lead = f === "shape" ? '<p class="hint">같은 회사·부서인데 교대 수나 주기 길이가 쓰는 판과 크게 달라요. 다른 부서일 수 있으니 토론과 메모를 보고 정해 주세요.</p>' : "";
      parts.push(`${title}${lead}<ul class="rows review-rows">${groups.map((g) => groupRow(g, editable, picking)).join("")}</ul>`);
    }
    if ((f === "all" || f === "asks") && q.asks.length) {
      parts.push(`<h2 class="section">앱에 없는 방식<span class="count">${q.asks.length}</span></h2><p class="hint">앱으로 설정하지 못한 방식이에요. 답을 고르면 보낸 분 앱에 보여요.</p>
        <ul class="rows review-rows">${q.asks.map((a) => askRow(a, editable)).join("")}</ul>`);
    }
    if ((f === "all" || f === "flagged") && q.flagged.length) {
      parts.push(`<h2 class="section">달라요가 있는 규칙<span class="count">${q.flagged.length}</span></h2><ul class="rows review-rows">${q.flagged.map((x) => flagRow(x)).join("")}</ul>`);
    }
    if (!parts.length) {
      if (f === "shape") {
        return head + empty("모양이 다른 판이 없어요", "같은 회사·부서인데 근무 모양이 쓰는 판과 크게 다른 제보가 오면 여기 따로 모여요.");
      }
      return head + empty("검토할 것이 없어요", "새 제보가 오면 여기와 메뉴에 숫자로 보여요.",
        can("community") && S.overview?.flaggedPosts ? `<a class="btn" href="${L.routeHash("community", null, {})}">신고된 글 ${S.overview.flaggedPosts}개 보기</a>` : "");
    }
    const n = picking ? S.reviewPick.size : 0;
    const bar = picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 제보"><b>${n}개 골랐어요</b>
        <button class="btn small ghost" data-act="review-pick-all">${n && n === groups.length ? "모두 풀기" : "보이는 것 모두 고르기"}</button>
        <button class="btn small danger" data-act="review-bulk-reject"${n ? "" : " disabled"}>고른 ${n}개 거절</button></div>` : "";
    return head + parts.join("") + bar;
  }

  /** One report group as one line; opened, what changed, the notes and the setup. */
  function groupRow(g, editable, picking) {
    const verified = g.reports.filter((r) => r.verified).length;
    // AI 1.27 [PLAZA-30] Ticks from older apps with no matched month behind them.
    const claims = g.reports.filter((r) => r.claim).length;
    const kind = g.shape ? '<span class="pill warn">모양이 달라요</span>' : g.blocked && g.replaces ? '<span class="pill warn">새 버전 후보</span>'
      : g.replaces ? `<span class="pill">v${g.replaces.version}와 달라요</span>` : '<span class="pill info">새 규칙</span>';
    const key = `g:${groupKey(g)}`;
    // [ADMUX-09] Another group of the same company and department: this one's name and what only it has.
    const twin = L.setupDiff(S.data.queue?.groups ?? []).get(g);
    const why = L.waitReason(g, S.overview?.settings?.adoptAgree ?? 75);
    const gist = g.changes?.summary || twin?.diff || (g.changes ? "" : g.lines.find(([label]) => label === "근무")?.[1]) || "";
    const siblings = (S.data.queue?.groups ?? []).filter((x) => x.company.id === g.company.id && x !== g && (x.dept ?? "") !== (g.dept ?? "")).length;
    const picked = picking && S.reviewPick.has(groupKey(g));
    const warn = g.turnedDown ? `${g.turnedDown === "withdrawn" ? "전에 내린 규칙과 같은 설정이에요" : "전에 거절한 설정이에요"}. 자동으로 채택되지 않아요.`
      : g.persons >= 2 && g.networks < 2 ? "모두 1시간 안에 같은 네트워크에서 보냈어요. 한 분이 여러 번 보냈을 수도 있어서 자동으로 채택하지 않았어요."
      : g.doubted && g.bad ? "달라요가 많아 자동으로 채택하지 않아요. 토론을 읽고 정해 주세요." : "";
    return `<li class="${picked ? "picked" : ""}"><details data-key="${key}" data-review="group"${S.opened.get(key) ? " open" : ""}>
      <summary class="row">${picking ? `<input type="checkbox" class="check" data-act="review-pick" data-key="${esc(groupKey(g))}" aria-label="${esc(g.company.name)} 고르기"${picked ? " checked" : ""}>` : ""}
        <span class="row-main"><b>${name(g.company, g.dept)}${twin ? ` <span class="ver">${twin.label}</span>` : ""}</b>
          <small>${gist ? `<span class="gist">${esc(gist)}</span> · ` : ""}${g.persons}명 · 제보 ${g.reports.length}건${verified ? ` · 명세서 ${verified}` : ""} · ${day(g.reports[0].at)}</small></span>
        <span class="row-side inline">${kind}
          <span class="score mini" title="채택 점수"><meter min="0" max="${g.threshold}" value="${Math.min(g.points, g.threshold)}" aria-label="채택 점수"></meter><b>${g.points}/${g.threshold}</b></span>
          ${g.bad ? `<span class="pill bad">달라요 ${g.bad}</span>` : ""}
          ${editable ? `<button class="btn small" data-act="reject" data-company="${g.company.id}" data-setup="${g.setup}">거절</button>
          <button class="btn small primary" data-act="adopt" data-company="${g.company.id}" data-setup="${g.setup}">채택</button>` : ""}</span></summary>
      <div class="rv-body">
        ${why ? `<p class="meta wait">${esc(why)}</p>` : ""}
        <p class="meta">신뢰도 ${g.trust ?? 0} · 맞았어요 ${g.agree ?? 0}%${g.posts ? ` · 토론 ${g.posts}개` : ""}${claims ? ` · 명세서 확인(근거 없음) ${claims}건` : ""}${siblings ? ` · 같은 회사 다른 부서 제보 ${siblings}건` : ""}</p>
        ${g.company.hidden ? '<p class="note warn">숨긴 회사예요. 채택해도 회사 찾기에 나오지 않아요.</p>' : ""}
        ${warn ? `<p class="note warn">${warn}</p>` : ""}
        ${g.changes ? changes(g.changes, g.replaces ? `쓰는 규칙 v${g.replaces.version}와 다른 점` : "지금 쓰는 규칙과 다른 점") : ""}
        ${g.reports.filter((r) => r.note).map((r) => `<blockquote>${esc(r.note)}<cite>${r.verified ? "명세서 확인 · " : r.claim ? "명세서 확인(근거 없음) · " : ""}${moment(r.at)}</cite></blockquote>`).join("")}
        <details data-key="${key}:lines"${S.opened.get(`${key}:lines`) ?? !g.changes ? " open" : ""}><summary>보낸 설정</summary>${lines(g.lines)}</details>
        <div class="actions start">
          ${can("community") && g.posts ? `<a class="btn small ghost" href="${href("community", groupKey(g))}">토론 ${g.posts}개 읽기</a>` : ""}
          ${can("companies") ? `<a class="btn small ghost" href="${href("companies", g.company.id)}">회사 열기</a>` : ""}</div>
      </div></details></li>`;
  }

  function askRow(a, editable) {
    const key = `a:${a.id}`;
    return `<li><details data-key="${key}" data-review="ask"${S.opened.get(key) ? " open" : ""}>
      <summary class="row"><span class="row-main"><b>${name(a.company, a.dept)}</b><small><span class="gist">${esc([...String(a.note ?? "")].slice(0, 80).join(""))}</span> · ${a.verified ? "명세서 확인 · " : ""}${moment(a.at)}</small></span>
        <span class="row-side inline"><span class="pill warn">앱에 없는 방식</span>${editable ? `<button class="btn small primary" data-act="answer" data-id="${a.id}">답하기</button>` : ""}</span></summary>
      <div class="rv-body"><blockquote>${esc(a.note)}</blockquote><details><summary>보낸 분이 맞춰 둔 설정</summary>${lines(a.lines)}</details></div></details></li>`;
  }

  function flagRow(f) {
    const key = `f:${f.rule}`;
    return `<li><details data-key="${key}" data-review="flag"${S.opened.get(key) ? " open" : ""}>
      <summary class="row"><span class="row-main"><b>${name(f.company, f.dept)} <span class="ver">v${f.version}</span></b>
        <small>맞았어요 ${f.good}명 · 달라요 ${f.bad}명${f.names ? ` · 이름이 이상해요 ${f.names}` : ""}${f.old ? ` · 예전 정보 ${f.old}` : ""}</small></span>
        <span class="row-side inline"><span class="pill bad">달라요 ${f.bad}</span>
          ${can("takedown", "edit") ? `<button class="btn small danger" data-act="withdraw-rule" data-id="${f.rule}">규칙 내리기</button>` : ""}</span></summary>
      <div class="rv-body"><details open><summary>규칙 내용</summary>${lines(f.lines)}</details>
        ${can("companies") ? `<div class="actions start"><a class="btn small ghost" href="${href("companies", f.company.id)}">회사 열기</a></div>` : ""}</div></details></li>`;
  }

  // ---- 여러 판 [ADMX-03] ----

  const VERSION_STATE = { active: ["ok", "쓰는 판"], pending: ["info", "기다리는 판"], previous: ["", "이전 판"] };
  const stateName = (c) => `${VERSION_STATE[c.state]?.[1] ?? c.state}${c.version ? ` v${c.version}` : ""}`;

  function versionsView() {
    const d = S.data.versions;
    const head = `<div class="segmented" role="group" aria-label="보기">${seg("versions-show", "conflict", S.versionShow, "정할 것", d?.conflicts)}${seg("versions-show", "history", S.versionShow, "이전 판까지")}</div>
      <p class="hint">판이 열이에요. 쓰는 판과 다른 칸에 색이 있고, 바뀐 낱말은 밑줄로 보여요. 적용한 휴대폰·맞았어요가 많고 신뢰가 높은 판이 '추천'(금색)이에요. 신뢰 하한은 투표가 적을 때 비율이 튀지 않게 본 값(윌슨 95%)이에요.</p>`;
    if (!d) return head + skeleton(2);
    if (!d.items.length) return head + empty(S.versionShow === "history" ? "판이 여럿인 부서가 없어요" : "정할 판이 없어요", "같은 회사 부서에 판이 둘 이상 생기면 여기와 메뉴에 숫자로 보여요.");
    return head + d.items.map(versionTable).join("");
  }

  function versionTable(v) {
    const editRules = can("takedown", "edit");
    const editReview = can("review", "edit");
    const list = v.candidates;
    const ref = Math.max(0, list.findIndex((c) => c.state === "active"));
    const rows = L.compareRows(list, ref);
    const showAll = S.cmpAll?.has(`${v.company.id}:${v.dept ?? ""}`);
    const hiddenSame = rows.filter((r) => r.same).length;
    const best = (i) => (list[i].recommended ? " best" : "");
    const cell = (c, i, r) => {
      const value = r.values[i];
      if (value === null) return `<td class="${r.differs[i] ? "differs" : ""}${best(i)}"><span class="meta">없음</span></td>`;
      const tokens = i !== ref && r.differs[i] ? c.changes?.facts?.changed?.find((x) => x.label === r.label)?.tokens : null;
      return `<td class="${r.differs[i] ? "differs" : ""}${best(i)}">${tokens ? L.tokenHtml(tokens, "after") : esc(value)}</td>`;
    };
    const numbers = [
      ["적용한 휴대폰", (c) => c.uses],
      ["맞았어요 · 달라요", (c) => `${c.ok} · ${c.bad}`],
      ["신뢰 (하한)", (c) => `${c.trust} <span class="meta">${c.wilson}%</span>`],
      ["제보", (c) => (c.state === "pending" ? `${c.reports}건${c.verified ? ` · 명세서 ${c.verified}` : ""}` : "–")],
      ["토론", (c) => (c.posts ? `${c.posts}개` : "–")],
    ];
    const summaries = list.map((c) => {
      if (!c.changes?.summary) return "";
      const against = c.against ? `${VERSION_STATE[c.against.state]?.[1] ?? ""}${c.against.version ? ` v${c.against.version}` : ""}` : "쓰는 판";
      return `<li><span class="pill ${VERSION_STATE[c.state]?.[0] ?? ""}">${esc(stateName(c))}</span> <span>${esc(against)}와 다른 점: <b>${esc(c.changes.summary)}</b></span></li>`;
    }).filter(Boolean);
    const actions = list.map((c, i) => `<td class="acts${best(i)}">${c.state === "pending" && editReview ? `<button class="btn small primary" data-act="adopt" data-company="${v.company.id}" data-setup="${c.setup}">이 판으로 채택</button>` : ""}
      ${c.state === "previous" && editRules ? `<button class="btn small" data-act="version-promote" data-id="${c.rule}" data-version="${c.version}">다시 대표로</button>` : ""}
      ${c.state === "active" && editRules ? `<button class="btn small danger" data-act="withdraw-rule" data-id="${c.rule}">내리기</button>` : ""}</td>`).join("");
    const key = `${v.company.id}:${v.dept ?? ""}`;
    return `<article class="card vcmp">
      <div class="card-head"><h3>${can("companies") ? `<a href="${href("companies", v.company.id)}">${esc(v.company.name)}</a>` : esc(v.company.name)}${v.dept ? ` <span class="dept">${esc(v.dept)}</span>` : ""}</h3>
        ${v.conflict ? '<span class="pill warn">정할 것</span>' : ""}</div>
      ${summaries.length ? `<ul class="vsum">${summaries.join("")}</ul>` : ""}
      <div class="table-wrap"><table class="cmp"><thead><tr><th scope="col" class="lab">항목</th>${list.map((c, i) => `<th scope="col" class="${best(i).trim()}">
        <span class="pill ${VERSION_STATE[c.state]?.[0] ?? ""}">${esc(stateName(c))}</span>${c.recommended ? ' <span class="pill gold">추천</span>' : ""}${c.shape ? ' <span class="pill warn">모양이 다른 판</span>' : ""}
        <span class="meta">${esc(day(c.at))}${c.source === "promote" ? " · 되돌림" : ""}</span></th>`).join("")}</tr></thead>
      <tbody>${numbers.map(([label, f]) => `<tr class="num"><th scope="row">${label}</th>${list.map((c, i) => `<td class="${best(i).trim()}">${f(c)}</td>`).join("")}</tr>`).join("")}
        ${rows.filter((r) => showAll || !r.same).map((r) => `<tr><th scope="row">${esc(r.label)}</th>${list.map((c, i) => cell(c, i, r)).join("")}</tr>`).join("")}
        ${!rows.some((r) => !r.same) && !showAll ? `<tr><td colspan="${list.length + 1}" class="meta">설정은 모두 같아요. 숫자만 달라요.</td></tr>` : ""}
        <tr class="act-row"><th scope="row"><span class="sr">할 일</span></th>${actions}</tr></tbody></table></div>
      ${hiddenSame ? `<button class="btn small ghost" data-act="cmp-all" data-key="${esc(key)}" aria-pressed="${Boolean(showAll)}">${showAll ? "다른 항목만 보기" : `같은 항목 ${hiddenSame}개도 보기`}</button>` : ""}
    </article>`;
  }

  // ---- 회사 [ADMX-06] ----

  const COMPANY_COLS = [["name", "회사"], ["rules", "규칙"], ["pending", "대기"], ["reports", "제보"], ["members", "참여"], ["uses", "30일 적용"], ["bad", "달라요"]];

  function companiesView() {
    const edit = can("companies", "edit");
    return `<div class="searchbar"><input id="search" type="search" placeholder="회사 이름이나 다른 이름" value="${esc(S.query)}" autocomplete="off"
        enterkeyhint="search" aria-label="회사 찾기">${edit ? `<button class="btn" data-act="company-pick-mode" aria-pressed="${Boolean(S.companyPick)}">${S.companyPick ? "선택 마침" : "여러 개 고르기"}</button>
        <button class="btn primary" data-act="new-company">${icon(P.plus)}회사 만들기</button>` : ""}</div>
      <div id="company-list">${companyRows()}</div>`;
  }

  function companyRows() {
    const list = S.data.list?.companies;
    if (!list) return skeleton();
    if (!list.length) {
      return empty(S.query ? "찾는 회사가 없어요" : "아직 회사가 없어요", S.query ? "회사 만들기로 직접 만들 수 있어요." : "제보가 오면 회사가 저절로 생겨요.");
    }
    const sort = S.companySort || "new";
    const picking = Boolean(S.companyPick) && can("companies", "edit");
    const badShare = (c) => (c.ok + c.bad ? Math.round((c.bad / (c.ok + c.bad)) * 100) : null);
    const th = ([key, label]) => `<th scope="col"${sort === key ? ' aria-sort="descending"' : ""}><button class="sort" data-act="company-sort" data-key="${key}" aria-pressed="${sort === key}">${label}${sort === key ? " ▾" : ""}</button></th>`;
    const num = (label, v, hot = false) => `<td class="n${hot && v ? " hot" : ""}"><span class="lab">${label}</span> ${v ?? 0}</td>`;
    const n = picking ? S.companyPick.size : 0;
    const bar = picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 회사"><b>${n}개 골랐어요</b>
        <button class="btn small" data-act="company-bulk" data-key="hide"${n ? "" : " disabled"}>회사 찾기에서 숨기기</button>
        <button class="btn small primary" data-act="company-bulk" data-key="show"${n ? "" : " disabled"}>다시 보이기</button></div>` : "";
    return `<p class="meta">${list.length}곳${S.data.list.total && S.data.list.total > list.length ? ` / ${S.data.list.total}곳` : ""} · 머리글을 누르면 그 순서로 봐요(처음은 새로 생긴 순).</p>
      <div class="table-wrap"><table class="list companies"><thead><tr>${picking ? '<th scope="col" class="pickcol"><span class="sr">고르기</span></th>' : ""}${COMPANY_COLS.map(th).join("")}</tr></thead>
      <tbody>${list.map((c) => {
        const share = badShare(c);
        return `<tr class="${S.companyPick?.has(c.id) ? "picked" : ""}">${picking ? `<td class="pick"><input type="checkbox" class="check" data-act="company-pick" data-id="${c.id}" aria-label="${esc(c.name)} 고르기"${S.companyPick.has(c.id) ? " checked" : ""}></td>` : ""}
        <td class="name"><a href="${href("companies", c.id)}">${esc(c.name)}</a>
          ${c.mergedInto ? '<span class="pill">합쳐짐</span>' : c.hidden ? '<span class="pill">숨김</span>' : ""}${c.aliases ? `<small>다른 이름 ${c.aliases}</small>` : ""}${c.previous ? `<small>이전 판 ${c.previous}</small>` : ""}</td>
        ${num("규칙", c.rules)}${num("대기", c.pending, true)}${num("제보", c.reports)}${num("참여", c.members)}${num("30일 적용", c.uses30)}
        <td class="n${share !== null && share >= 40 && c.ok + c.bad >= 5 ? " hot" : ""}"><span class="lab">달라요</span> ${share === null ? "–" : `${share}%`}${c.bad ? ` <small>${c.bad}표</small>` : ""}</td></tr>`;
      }).join("")}</tbody></table></div>${bar}`;
  }

  // ---- what these screens do ----

  const actions = {
    // [ADMX-09] 검토 여러 개 거절
    "review-pick-mode": () => {
      S.reviewPick = S.reviewPick ? null : new Set();
      render({ keepScroll: true });
    },
    "review-pick": ({ key }) => {
      if (!S.reviewPick) return;
      if (S.reviewPick.has(key)) S.reviewPick.delete(key);
      else S.reviewPick.add(key);
      render({ keepScroll: true });
    },
    "review-pick-all": () => {
      const q = S.data.queue;
      if (!q || !S.reviewPick) return;
      const shown = (S.reviewFilter === "shape" ? q.groups.filter((g) => g.shape) : q.groups).map(groupKey);
      S.reviewPick = S.reviewPick.size === shown.length ? new Set() : new Set(shown);
      render({ keepScroll: true });
    },
    "review-bulk-reject": () => {
      const n = S.reviewPick?.size ?? 0;
      if (!n) return;
      const reasons = Object.entries(S.overview?.reasons ?? {});
      openDialog(`<form data-form="reject-bulk"><h2>${n}개를 거절할까요?</h2><p>보낸 분들 앱에 이 이유가 보여요. 10분 안에는 한 번에 되돌릴 수 있어요.</p>
        <div class="radios">${reasons.map(([key, text], i) => `<label class="radio"><input type="radio" name="reason" value="${key}"${i === 0 ? " checked" : ""}><span>${esc(text)}</span></label>`).join("")}</div>
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">${n}개 거절하기</button></div></form>`);
    },
    // [ADMX-03]
    "cmp-all": ({ key }) => {
      S.cmpAll ??= new Set();
      if (S.cmpAll.has(key)) S.cmpAll.delete(key);
      else S.cmpAll.add(key);
      render({ keepScroll: true });
    },
    // [ADMX-06]
    "company-sort": ({ key }) => {
      S.companySort = key === "new" ? "" : key;
      refilter("list");
    },
    "company-pick-mode": () => {
      S.companyPick = S.companyPick ? null : new Set();
      render({ keepScroll: true });
    },
    "company-pick": ({ id }) => {
      if (!S.companyPick) return;
      if (S.companyPick.has(id)) S.companyPick.delete(id);
      else S.companyPick.add(id);
      render({ keepScroll: true });
    },
    "company-bulk": async ({ key }) => {
      const ids = [...(S.companyPick ?? [])];
      if (!ids.length) return;
      const hidden = key === "hide";
      const set = (to) => Promise.all(ids.map((id) => api(`/companies/${id}`, "POST", { hidden: to })));
      await act(() => set(hidden), hidden ? `${ids.length}곳을 회사 찾기에서 숨겼어요` : `${ids.length}곳을 다시 보이게 했어요`,
        () => act(() => set(!hidden), "되돌렸어요").then(() => load()));
      S.companyPick = null;
      load();
    },
  };

  const forms = {
    "reject-bulk": async (form) => {
      const reason = new FormData(form).get("reason");
      const keys = [...(S.reviewPick ?? [])];
      dialog.close();
      const call = (k, what, body) => {
        const [company, setup] = k.split(".");
        return api(`/groups/${company}/${setup}/${what}`, "POST", body);
      };
      const done = await act(async () => {
        const out = await Promise.allSettled(keys.map((k) => call(k, "reject", { reason })));
        return keys.filter((_, i) => out[i].status === "fulfilled");
      }, (ok) => `${ok.length}개를 거절했어요${ok.length < keys.length ? `. ${keys.length - ok.length}개는 못 했어요` : ""}`,
      (ok) => act(() => Promise.all(ok.map((k) => call(k, "reopen"))), "거절을 되돌렸어요. 다시 검토를 기다려요").then(() => load()));
      if (done) S.reviewPick = null;
      syncUrl();
      load();
    },
  };

  return { reviewView, versionsView, companiesView, companyRows, changes, actions, forms };
}
