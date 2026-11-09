// AX 1.30 [ADMX-12] 오늘 (the home screen) out of src/admin-app.js. Like admin-lib, this function is sent to the
// browser as its own text (String(fn)) and called by the app with what it shares (A), so it may use no name from
// outside itself. [ADMX-01] What changed for 1.30:
//   - the four numbers that need a hand (답 대기, 검토 대기, 복사 전 문제 신고, 정할 판) stay on a phone too, 2×2,
//     each with its last 7 days as a small line and how today differs from yesterday; the other numbers fold away;
//   - 주의 필요: what the server's rules found (기한 넘김, 급증, 정할 판, 모양이 다른 판), the worst first;
//   - the 할 일 rows are one line each; 최근 활동 says every code in words (L.actionName).
export function todayViews(A) {
  const { S, L, esc, can, href, seg, skeleton, icon, P, ago, flagAlertCard, waitingItems, snoozed, inboxLink, dueText, INBOX_NAMES, wide } = A;

  /** One number with its week: [list] 7 numbers, the last one today. */
  function kpi({ label, value, to, desc, list, hot = true, name }) {
    const has = Array.isArray(list) && list.length === 7;
    const delta = has ? list[6] - list[5] : 0;
    const tone = has ? L.trendTone(list) : "";
    return `<a class="tile kpi${hot && value ? " hot" : ""}${tone ? " rising" : ""}" href="${to}"><span class="k">${esc(label)}</span>
      <span class="v">${value ?? 0}${delta ? `<span class="delta ${delta > 0 ? "up" : "down"}" title="어제보다">${delta > 0 ? "▲" : "▼"}${Math.abs(delta)}</span>` : ""}</span>
      <span class="d">${esc(desc)}</span>${has ? L.spark(list, { name: name ?? label }) : ""}</a>`;
  }

  /**
   * [shown]: 위 큰 카드(오늘 첫 화면)에 이미 보여 준 줄의 key. 1.39 실서버(11-08 크롬 둘러보기): 위아래에 같은 줄이 두 번 나와서,
   * 아래에서는 '위에서 본 N개'로 접어 둬요. 개수와 목록은 그대로라 메뉴 숫자와 맞아요.
   */
  function alertsCard(list, shown = new Set()) {
    if (!Array.isArray(list)) return "";
    if (!list.length) return `<p class="calm">${icon(P.check)}<span>지금 살필 것이 없어요. 급증·기한 넘김·정할 판이 생기면 여기 먼저 보여요.</span></p>`;
    const line = (a) => `<li class="${a.tone === "bad" ? "bad" : "warn"}"><a href="${esc(a.to)}"><b>${esc(a.title)}</b>${a.meta ? `<small>${esc(a.meta)}</small>` : ""}</a>${icon(P.chevron)}</li>`;
    const top = list.filter((a) => !shown.has(`a:${a.key}`));
    const above = list.filter((a) => shown.has(`a:${a.key}`));
    return `<section class="card alerts" aria-labelledby="alerts-h"><div class="card-head"><h2 id="alerts-h">주의 필요</h2><span class="pill bad">${list.length}</span></div>
      ${top.length ? `<ul class="alert-list">${top.map(line).join("")}</ul>` : ""}${aboveFold(above.length, `<ul class="alert-list">${above.map(line).join("")}</ul>`, "home:above-alerts")}</section>`;
  }

  /** 위 카드에서 본 줄을 접어 두는 칸. 펼침은 기기에 남아요. */
  function aboveFold(n, inner, key) {
    if (!n) return "";
    return `<details class="shown-above" data-key="${key}"${S.opened.get(key) ? " open" : ""}><summary>위에서 본 ${n}개</summary>${inner}</details>`;
  }

  /** 오늘 첫 화면의 셋: 주의 필요(빨강 먼저) 다음 기한이 가까운 할 일. key는 아래 카드가 접을 때 써요. */
  function heroPicks(t) {
    const alerts = Array.isArray(t.alerts) ? t.alerts : [];
    const hide = snoozed();
    const todo = (t.todo?.items ?? []).filter((x) => !hide[`${x.type}:${x.id}`]);
    const ordered = [...alerts.filter((a) => a.tone === "bad"), ...alerts.filter((a) => a.tone !== "bad")];
    const picks = [
      ...ordered.map((a) => ({ key: `a:${a.key}`, tone: a.tone === "bad" ? "bad" : "warn", to: a.to, title: a.title, meta: a.meta ?? "", due: null })),
      ...todo.map((x) => ({ key: `${x.type}:${x.id}`, tone: "todo", to: inboxLink(x), title: `${INBOX_NAMES[x.type] ?? x.type} · ${x.title}`, meta: x.company ?? "", due: x })),
    ].slice(0, 3);
    return { alerts, todo, picks, shown: new Set(picks.map((p) => p.key)) };
  }

  /**
   * 1.39 [ADM3-47] 크롬 둘러보기 디자인 관찰 '첫 화면은 오늘 상태 한 문장 + 할 일 3개': 주의 필요의 수로 한 문장(빨강이 있으면 빨강),
   * 그 아래 먼저 볼 셋(heroPicks). 아래 주의 필요·할 일 카드는 이 셋을 '위에서 본 N개'로 접어요. 모양은 숫자 › 한눈에의 큰 신호와 같아요.
   */
  function heroCard(t, o, hi, { alerts, todo, picks }) {
    const bad = alerts.filter((a) => a.tone === "bad");
    const tone = bad.length ? "bad" : alerts.length ? "warn" : "ok";
    const line = bad.length ? `바로 볼 것이 ${bad.length}개 있어요` : alerts.length ? `살펴볼 것이 ${alerts.length}개 있어요`
      : todo.length ? `급한 일은 없고, 할 일이 ${todo.length}개예요` : "지금 기다리는 일이 없어요";
    const sum = [can("inquiries") ? `답 대기 ${o.inquiries ?? 0}` : "", can("review") ? `검토 대기 ${L.reviewCount(o)}` : "", o.doneToday ? `오늘 처리 ${o.doneToday}` : ""].filter(Boolean).join(" · ");
    const restA = alerts.length - picks.filter((p) => p.key.startsWith("a:")).length;
    const restT = todo.length - picks.filter((p) => !p.key.startsWith("a:")).length;
    const rest = [restA > 0 ? `주의 필요 ${restA}개` : "", restT > 0 ? `할 일 ${restT}개` : ""].filter(Boolean).join(", ");
    return `<section class="gl-hero today-hero ${tone}" aria-labelledby="today-hero-h"><div class="gl-hero-top"><span class="gl-pulse" aria-hidden="true"><span></span></span>
        <div class="gl-hero-text"><p class="gl-eyebrow">${esc(hi)}, ${esc(S.me.name)}님</p><h2 id="today-hero-h">${esc(line)}</h2>${sum ? `<p class="gl-sub">${esc(sum)}</p>` : ""}</div></div>
        ${picks.length ? `<ol class="today-three">${picks.map((p, i) => `<li class="${p.tone}"><a href="${esc(p.to)}"><span class="n" aria-hidden="true">${i + 1}</span>
          <span class="t"><b>${esc(p.title)}</b><span class="sub">${p.due ? dueText(p.due, Date.now()) : ""}${p.meta ? `<small>${esc(p.meta)}</small>` : ""}</span></span>${icon(P.chevron)}</a></li>`).join("")}</ol>` : ""}
        ${picks.length ? `<p class="today-rest">먼저 볼 ${picks.length}가지${rest ? ` · 아래에 ${rest} 더` : ""}</p>` : ""}</section>`;
  }

  /**
   * 1.39 [LOOP-40] 사용자 11-09 승인 '열면 끝나 있는 관리자 앱': 지난 12시간 동안 사람 없이 끝난 일을 한 장으로. 사람 손이 필요한 것은
   * 위 큰 카드(주의 필요)로만 오고, 여기는 '끝났어요'만 보여요. 효과 판정은 개발사항 이름과 한 줄.
   */
  function nightCard(n) {
    if (!n) return "";
    const lines = Array.isArray(n.lines) ? n.lines : [];
    const fx = Array.isArray(n.effects) ? n.effects : [];
    if (!lines.length && !fx.length) return `<p class="calm night-calm">${icon(P.check)}<span>지난 ${esc(n.hours ?? 12)}시간 동안 저절로 끝난 일은 없어요. 사람 손이 필요한 것은 위에 먼저 보여요.</span></p>`;
    const key = "home:night";
    const open = S.opened.get(key) ?? true;
    const line = (l) => `<li>${l.to ? `<a href="${esc(l.to)}">${esc(l.text)}</a>` : esc(l.text)}</li>`;
    const tone = { better: "ok", same: "warn", worse: "bad", none: "" };
    return `<details class="card night" data-key="${key}"${open ? " open" : ""}><summary><h2>지난 ${esc(n.hours ?? 12)}시간 저절로 끝난 일</h2>
        <span class="pill ok">${lines.reduce((a, l) => a + (l.n ?? 0), 0)}</span></summary>
      ${lines.length ? `<ul class="feed">${lines.map(line).join("")}</ul>` : ""}
      ${fx.length ? `<h3>출시 2주 뒤 효과</h3><ul class="feed">${fx.map((e) => `<li><a href="${href("dev", e.id)}">${esc(e.title)}</a>
        <span class="pill ${tone[e.effect] ?? ""}">${esc(e.name)}</span>${e.why ? ` <small>${esc(e.why)}</small>` : ""}</li>`).join("")}</ul>` : ""}
      <p class="hint">사람이 봐야 할 문의(환불·법·개인정보·보안·'사람이냐' 등)는 자동으로 답하지 않고 위 '주의 필요'에 남아요.</p></details>`;
  }

  function homeView() {
    const t = S.data.today;
    const top = flagAlertCard();
    if (!t) return `${top}<div class="tiles core">${'<div class="tile skeleton"></div>'.repeat(4)}</div>${skeleton(2)}`;
    const o = { ...(S.overview ?? {}), ...t };
    const tr = t.trend ?? {};
    const hour = new Date().getHours();
    const hi = hour < 6 ? "늦은 밤이에요" : hour < 12 ? "좋은 아침이에요" : hour < 18 ? "좋은 오후예요" : "좋은 저녁이에요";
    const inq = (params) => L.routeHash("inquiries", null, params);
    // The four that need a hand, on every screen size.
    const core = [];
    if (can("inquiries")) {
      core.push(kpi({ label: "답 대기", value: o.inquiries, to: inq({ status: "open", sort: "sla" }), desc: `오늘 새 문의 ${o.inquiriesToday ?? 0}건`, list: tr.inquiries, name: "날마다 새 문의" }));
    }
    if (can("review")) {
      core.push(kpi({ label: "검토 대기", value: L.reviewCount(o), to: href("review"), desc: `제보 ${o.pending ?? 0} · 앱에 없는 방식 ${o.asks ?? 0} · 달라요 ${o.flagged ?? 0}`, list: tr.reports, name: "날마다 받은 제보" }));
    }
    // AT 1.29 [ADMCP-04] New problem reports nobody copied for development yet, one tap from the list that copies them.
    if (can("inquiries")) {
      core.push(kpi({ label: "복사 전 문제 신고", value: o.bugsUncopied, to: inq({ status: "all", kind: "bug", copied: "no" }), desc: "개발에 넘기기 전이에요", list: tr.bugs, name: "날마다 문제 신고" }));
    }
    if (can("review") && t.versionConflicts !== undefined) {
      core.push(kpi({ label: "정할 판", value: t.versionConflicts, to: href("versions"), desc: "한 부서에 판이 둘 이상", hot: true }));
    }
    // The rest, folded on a phone and open on a wide screen.
    const rest = [];
    const tile = (k, v, d, to, hot) => rest.push(`<a class="tile${hot && v ? " hot" : ""}" href="${to}"><span class="k">${esc(k)}</span><span class="v">${v ?? 0}</span><span class="d">${esc(d)}</span></a>`);
    if (can("inquiries")) {
      tile("오늘 새 문의", o.inquiriesToday, `어제 ${o.inquiriesYesterday ?? 0}건`, inq({ status: "open", sort: "newest" }), false);
      tile("오늘 목표 넘김", o.slaLateToday, "오늘 안에 답 목표를 넘겨요", inq({ status: "open", sort: "sla" }), true);
      tile("아쉬워요", o.inquiriesBad, "답이 아쉬웠다는 문의", inq({ status: "answered" }), true);
    }
    if (can("dev")) tile("개발사항 접수", o.devTodo, can("devStatus", "edit") ? "처리를 기다려요" : "개발 전이에요", L.routeHash("dev", null, { status: "todo" }), can("devStatus", "edit"));
    if (can("community")) tile("신고된 글", o.flaggedPosts, "토론에서 확인할 글", L.routeHash("community", null, { show: "flagged" }), true);
    if (can("rewards")) tile("보상 대기", o.owed, "드릴 프로 기간", href("rewards"), false);
    if (S.me.owner) tile("승인 대기 관리자", o.admins?.waiting, "가입한 관리자", href("admins"), true);
    if (can("ops")) tile("이달 할 일", o.opsLeft, "요율·공휴일·처리방침 점검", href("chores"), false);
    const stats = [];
    if (can("companies")) stats.push(["회사", o.companies], ["쓰는 규칙", o.rules]);
    if (can("review")) stats.push(["모든 제보", o.reports]);
    if (can("community")) stats.push(["토론 글", o.posts]);
    const restKey = "home:tiles";
    const more = rest.length || stats.length ? `<details class="more-tiles" data-key="${restKey}"${S.opened.get(restKey) ?? wide() ? " open" : ""}><summary>숫자 모두 보기</summary>
        ${rest.length ? `<div class="tiles">${rest.join("")}</div>` : ""}
        ${stats.length ? `<p class="stats">${stats.map(([k, v]) => `<span>${esc(k)}<b>${v ?? 0}</b></span>`).join("")}</p>` : ""}</details>` : "";
    const feed = o.recent ?? null;
    const people = (o.assigned ?? []).length ? `<section class="card"><h2>누가 몇 건 맡았나</h2><ul class="people">${o.assigned.map((a) =>
      `<li>${a.id === S.me.id ? "나" : esc(a.name)} ${a.n}</li>`).join("")}</ul></section>` : "";
    const views = S.prefs.views.filter((v) => String(v.hash).startsWith("#/"));
    const hero = heroPicks(t);
    return `${top}${heroCard(t, o, hi, hero)}${nightCard(t.night)}
      <p class="hello-who">${S.me.owner ? "최고 관리자로 로그인했어요." : `내 권한: ${esc(L.permSummary(S.me.perms, S.me.areas ?? {}, A.toggles()))}`}</p>
      ${core.length ? `<div class="tiles core">${core.join("")}</div>` : ""}
      <a class="sort-nudge" href="#/desk">${icon(P.check)}<span><b>운영 장부</b> 문의·개발사항·스튜디오 요구·신고·사진을 한 줄씩. ${can("inquiries") && o.inquiries ? `답 대기 ${o.inquiries}건은 겹침과 보낼 답까지 적어 두었어요. ` : ""}오늘 할 것부터 빠짐없이 처리해요</span>${icon(P.chevron)}</a>
      ${views.length ? `<nav class="chips" aria-label="저장한 보기">${views.map((v) => `<a class="chip pick" href="${esc(v.hash)}">${esc(v.name)}</a>`).join("")}</nav>` : ""}
      <div class="home-grid"><div class="home-main">${alertsCard(t.alerts, hero.shown)}${todoCard(t.todo, hero.shown)}</div>
        <div class="home-side">${more}${people}
        ${can("audit") ? `<section class="card"><div class="card-head"><h2>최근 활동</h2><a class="btn small ghost" href="${href("audit")}">모두 보기</a></div>
          ${feed === null ? '<div class="card skeleton short"></div>' : feed.length ? `<ul class="feed">${feed.map(feedItem).join("")}</ul>` : '<p class="hint">아직 활동이 없어요.</p>'}</section>` : ""}
        </div></div>`;
  }

  /**
   * [ADMUX-33] 할 일: everything waiting on this admin's areas in one list, nearest deadline first. [ADMX-01] One line
   * per item: what and where on the left, the deadline and the buttons on the right.
   */
  function todoCard(d, shown = new Set()) {
    const c = d?.counts ?? {};
    const editInq = can("inquiries", "edit");
    const head = `<div class="card-head"><h2>할 일</h2>${S.queue ? "" : editInq && (S.overview?.inquiries ?? 0) ? '<button class="btn small primary" data-act="queue-start">큐 모드로 답하기</button>' : ""}</div>
      <p class="waiting" id="waiting" data-hub="today">${waitingItems("today")}</p>
      <div class="segmented" role="group" aria-label="할 일 보기">${seg("inbox-view", "today", S.inboxView, "오늘까지", c.today)}${seg("inbox-view", "week", S.inboxView, "이번 주", c.week)}
        ${seg("inbox-view", "mine", S.inboxView, "내 것", c.mine)}${seg("inbox-view", "all", S.inboxView, "모두", c.all)}</div>`;
    const now = Date.parse(d.now);
    const kst = new Date(now + 9 * 3600e3);
    const todayEnd = Date.UTC(kst.getUTCFullYear(), kst.getUTCMonth(), kst.getUTCDate() + 1) - 9 * 3600e3;
    const hide = snoozed();
    const list = d.items.filter((x) => !hide[`${x.type}:${x.id}`]).filter((x) => (S.inboxView === "all" ? true : S.inboxView === "mine" ? x.mine
      : x.due && Date.parse(x.due) < (S.inboxView === "today" ? todayEnd : todayEnd + 6 * 864e5)));
    const hidden = Object.keys(hide).length;
    const again = hidden ? `<p class="meta">내일로 미룬 ${hidden}개는 숨겼어요. <button class="btn small ghost" data-act="inbox-unsnooze">다시 보기</button></p>` : "";
    if (!list.length) {
      return `<section class="card todo-card">${head}<p class="hint">${S.inboxView === "all" ? "기다리는 일이 없어요." : "이 보기에는 기다리는 일이 없어요."} 새로 오면 여기와 메뉴에 숫자로 보여요.</p>${again}</section>`;
    }
    const flagged = (x) => x.type === "flagged_post" || x.type === "hidden_post";
    // 1.39 [ADM3-37] 13차 확인 7: 같은 말로 읽히는 문의는 한 줄로. 첫 줄(기한이 가장 가까운 것) 아래 '비슷한 문의 N건'으로 접고,
    // 첫 줄이 이 보기에 없으면(내 것만 보기 등) 나머지는 따로 보여요.
    const asks = new Map(list.filter((x) => x.type === "inquiry").map((x) => [x.id, x]));
    const rows = list.filter((x) => !(x.type === "inquiry" && x.bundle && asks.has(x.bundle)));
    const bundleOf = (x) => (x.type === "inquiry" ? (x.others ?? []).map((id) => asks.get(id)).filter(Boolean) : []);
    const bundled = (x, more) => !more.length ? "" : `<details class="quick bundle" data-key="bd:${esc(x.id)}"><summary>비슷한 문의 ${more.length}건 함께 보기</summary>
          <div class="bundle-list">${more.map((o) => `<p><a href="${inboxLink(o)}">${esc(o.title)}</a>
            <small>${esc(ago(o.at))}${o.assignee ? ` · ${o.mine ? "내가 맡음" : esc(o.assignee.name)}` : ""}</small>${dueText(o, Date.now())}</p>`).join("")}</div>
          <p class="meta">같은 답이면 운영 장부에서 한 개발사항으로 묶거나 한꺼번에 답해요. <a href="#/desk?view=today">운영 장부 열기</a></p></details>`;
    const row = (x) => {
        const more = bundleOf(x);
        // 1.39 [ADM3-39] 발굴: 묶음의 다른 문의에도 같은 답을 한 번에(다른 사람이 맡은 것은 빼요). 기본은 꺼 둬요.
        const also = more.filter((o) => !o.assignee || o.mine);
        const answer = x.type === "inquiry" && editInq && (!x.assignee || x.mine) ? `<details class="quick" data-key="qr:${esc(x.id)}"><summary>바로 답하기</summary>
          <form data-form="quick-reply" data-id="${esc(x.id)}" data-others="${esc(also.map((o) => o.id).join(","))}"><label class="sr" for="qr-${esc(x.id)}">답</label>
            <textarea id="qr-${esc(x.id)}" name="text" maxlength="4000" required placeholder="보낸 분 앱에 보일 답을 적어요"></textarea>
            ${also.length ? `<label class="switch"><input type="checkbox" name="all"><span class="track" aria-hidden="true"></span><span>비슷한 문의 ${also.length}건에도 같은 답 보내기</span></label>` : ""}
            <div class="actions"><button class="btn" type="submit" data-close="1">답하고 닫기</button><button class="btn primary" type="submit">보내기</button></div></form></details>` : "";
        return `<li><div class="row">
        <span class="row-main"><b><span class="kind">${esc(INBOX_NAMES[x.type] ?? x.type)}${more.length ? ` ${more.length + 1}건` : ""}</span> <a href="${inboxLink(x)}">${esc(x.title)}</a></b>
          <small>${esc(ago(x.at))}${x.company ? ` · ${esc(x.company)}` : ""}${x.n ? ` · ${x.n}${flagged(x) ? "번 신고" : "건"}` : ""}${x.type === "inquiry" ? (x.assignee ? ` · ${x.mine ? "내가 맡음" : esc(x.assignee.name)}` : " · 아무도 안 맡음") : ""}</small></span>
        <span class="row-side">${dueText(x, Date.now())}
          ${x.type === "inquiry" && editInq && !x.assignee ? `<button class="btn small" data-act="inbox-assign" data-id="${x.id}">맡기</button>` : ""}
          <a class="btn small${x.type === "inquiry" ? "" : " primary"}" href="${inboxLink(x)}">${x.type === "inquiry" ? "열기" : "보기"}</a>
          <button class="btn small ghost" data-act="inbox-snooze" data-key="${esc(`${x.type}:${x.id}`)}" title="이 기기에서 내일 아침까지 숨겨요">내일 볼게요</button></span></div>${bundled(x, more)}${answer}</li>`;
    };
    const top = rows.filter((x) => !shown.has(`${x.type}:${x.id}`));
    const above = rows.filter((x) => shown.has(`${x.type}:${x.id}`));
    return `<section class="card todo-card">${head}
      ${top.length ? `<ul class="rows inbox">${top.map(row).join("")}</ul>` : ""}${aboveFold(above.length, `<ul class="rows inbox">${above.map(row).join("")}</ul>`, "home:above-todo")}${again}</section>`;
  }

  function feedItem(e) {
    const about = e.label ? (e.link ? ` <a class="meta" href="${esc(e.link)}">${esc(e.label)}</a>` : ` <span class="meta">${esc(e.label)}</span>`)
      : e.target && !/^[0-9a-f-]{16,}$/i.test(e.target) ? ` <span class="meta">${esc(e.target)}</span>` : "";
    return `<li><span class="when">${esc(ago(e.at))}</span><span>${esc(L.actionName(e.action))}${about}</span>
      <span class="who">${esc(e.actor || "앱 사용자")}</span></li>`;
  }

  return { homeView, todoCard, feedItem, alertsCard };
}
