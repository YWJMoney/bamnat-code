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

  function alertsCard(list) {
    if (!Array.isArray(list)) return "";
    if (!list.length) return `<p class="calm">${icon(P.check)}<span>지금 살필 것이 없어요. 급증·기한 넘김·정할 판이 생기면 여기 먼저 보여요.</span></p>`;
    return `<section class="card alerts" aria-labelledby="alerts-h"><div class="card-head"><h2 id="alerts-h">주의 필요</h2><span class="pill bad">${list.length}</span></div>
      <ul class="alert-list">${list.map((a) => `<li class="${a.tone === "bad" ? "bad" : "warn"}"><a href="${esc(a.to)}"><b>${esc(a.title)}</b>${a.meta ? `<small>${esc(a.meta)}</small>` : ""}</a>${icon(P.chevron)}</li>`).join("")}</ul></section>`;
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
    return `${top}<div class="hello"><h2>${esc(hi)}, ${esc(S.me.name)}님</h2>
        <p>${o.doneToday ? `오늘 ${o.doneToday}건 처리했어요. ` : ""}${S.me.owner ? "최고 관리자로 로그인했어요." : `내 권한: ${esc(L.permSummary(S.me.perms, S.me.areas ?? {}, A.toggles()))}`}</p></div>
      ${core.length ? `<div class="tiles core">${core.join("")}</div>` : ""}
      ${can("inquiries") && o.inquiries > 1 ? `<a class="sort-nudge" href="${href("sort")}">${icon(P.inquiries)}<span><b>정리함</b> 답 대기 ${o.inquiries}건을 이미 고침·개발 목록·새 문제로 나눠 두었어요. 한 번에 처리해요</span>${icon(P.chevron)}</a>` : ""}
      ${views.length ? `<nav class="chips" aria-label="저장한 보기">${views.map((v) => `<a class="chip pick" href="${esc(v.hash)}">${esc(v.name)}</a>`).join("")}</nav>` : ""}
      <div class="home-grid"><div class="home-main">${alertsCard(t.alerts)}${todoCard(t.todo)}</div>
        <div class="home-side">${more}${people}
        ${can("audit") ? `<section class="card"><div class="card-head"><h2>최근 활동</h2><a class="btn small ghost" href="${href("audit")}">모두 보기</a></div>
          ${feed === null ? '<div class="card skeleton short"></div>' : feed.length ? `<ul class="feed">${feed.map(feedItem).join("")}</ul>` : '<p class="hint">아직 활동이 없어요.</p>'}</section>` : ""}
        </div></div>`;
  }

  /**
   * [ADMUX-33] 할 일: everything waiting on this admin's areas in one list, nearest deadline first. [ADMX-01] One line
   * per item: what and where on the left, the deadline and the buttons on the right.
   */
  function todoCard(d) {
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
    return `<section class="card todo-card">${head}
      <ul class="rows inbox">${list.map((x) => {
        const answer = x.type === "inquiry" && editInq && (!x.assignee || x.mine) ? `<details class="quick" data-key="qr:${esc(x.id)}"><summary>바로 답하기</summary>
          <form data-form="quick-reply" data-id="${esc(x.id)}"><label class="sr" for="qr-${esc(x.id)}">답</label>
            <textarea id="qr-${esc(x.id)}" name="text" maxlength="4000" required placeholder="보낸 분 앱에 보일 답을 적어요"></textarea>
            <div class="actions"><button class="btn" type="submit" data-close="1">답하고 닫기</button><button class="btn primary" type="submit">보내기</button></div></form></details>` : "";
        return `<li><div class="row">
        <span class="row-main"><b><span class="kind">${esc(INBOX_NAMES[x.type] ?? x.type)}</span> <a href="${inboxLink(x)}">${esc(x.title)}</a></b>
          <small>${esc(ago(x.at))}${x.company ? ` · ${esc(x.company)}` : ""}${x.n ? ` · ${x.n}${flagged(x) ? "번 신고" : "건"}` : ""}${x.type === "inquiry" ? (x.assignee ? ` · ${x.mine ? "내가 맡음" : esc(x.assignee.name)}` : " · 아무도 안 맡음") : ""}</small></span>
        <span class="row-side">${dueText(x, Date.now())}
          ${x.type === "inquiry" && editInq && !x.assignee ? `<button class="btn small" data-act="inbox-assign" data-id="${x.id}">맡기</button>` : ""}
          <a class="btn small${x.type === "inquiry" ? "" : " primary"}" href="${inboxLink(x)}">${x.type === "inquiry" ? "열기" : "보기"}</a>
          <button class="btn small ghost" data-act="inbox-snooze" data-key="${esc(`${x.type}:${x.id}`)}" title="이 기기에서 내일 아침까지 숨겨요">내일 볼게요</button></span></div>${answer}</li>`;
      }).join("")}</ul>${again}</section>`;
  }

  function feedItem(e) {
    const about = e.label ? (e.link ? ` <a class="meta" href="${esc(e.link)}">${esc(e.label)}</a>` : ` <span class="meta">${esc(e.label)}</span>`)
      : e.target && !/^[0-9a-f-]{16,}$/i.test(e.target) ? ` <span class="meta">${esc(e.target)}</span>` : "";
    return `<li><span class="when">${esc(ago(e.at))}</span><span>${esc(L.actionName(e.action))}${about}</span>
      <span class="who">${esc(e.actor || "앱 사용자")}</span></li>`;
  }

  return { homeView, todoCard, feedItem, alertsCard };
}
