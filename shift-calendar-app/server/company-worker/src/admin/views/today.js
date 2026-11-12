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
  /**
   * 2.0.0 [RVA-31] 검토 T24-19: 7일 그래프가 없는 숫자(정할 판)는 그 자리가 비어 높이가 떠 보였어요. 그래프 대신 같은 높이의
   * '할 일 한 줄'(go: 0이면 done)을 둬서 네 칸이 같은 모양이에요.
   */
  function kpi({ label, value, to, desc, list, hot = true, name, go, done }) {
    const has = Array.isArray(list) && list.length === 7;
    const foot = has ? L.spark(list, { name: name ?? label })
      : go ? `<span class="tile-go${value ? "" : " done"}">${value ? `${esc(go)}${icon(P.chevron)}` : `${icon(P.check)}${esc(done ?? "없어요")}`}</span>` : "";
    const delta = has ? list[6] - list[5] : 0;
    const tone = has ? L.trendTone(list) : "";
    return `<a class="tile kpi${hot && value ? " hot" : ""}${tone ? " rising" : ""}" href="${to}"><span class="k">${esc(label)}</span>
      <span class="v">${value ?? 0}${delta ? `<span class="delta ${delta > 0 ? "up" : "down"}" title="어제보다">${delta > 0 ? "▲" : "▼"}${Math.abs(delta)}</span>` : ""}</span>
      <span class="d">${esc(desc)}</span>${foot}</a>`;
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
    return `<section class="card alerts" aria-labelledby="alerts-h"><div class="card-head"><h2 id="alerts-h">주의 필요</h2><span class="pill">${list.length}</span></div>
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
    // 2.0.0 [RVA-36] 검토 I23c-01: 위 셋은 미루면 다음 것이 올라와요(주의 필요도 이 기기에서 내일 아침까지).
    const live = alerts.filter((a) => !hide[`a:${a.key}`]);
    const ordered = [...live.filter((a) => a.tone === "bad"), ...live.filter((a) => a.tone !== "bad")];
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
  /**
   * 2.0.0 [RVA-36] 검토 U23-01·I23c-01·ADM-12(도약 41번): 오늘은 '내가 할 것' 셋과 '저절로 끝난 일'만 펴 두는 한 손 화면이에요. 셋마다 [내일 볼게요]가
   * 바로 옆에 있고(휴대폰은 왼쪽으로 밀어도, 오른쪽으로 밀면 열기), 미루면 다음 것이 올라와요. 채운 단추는 '차례로 답하기' 하나(큐 모드,
   * 문의를 한 장씩 엄지로 넘겨요). 나머지(할 일 전체·숫자·최근 활동)는 아래 접힌 줄이에요.
   */
  function heroCard(t, o, hi, { alerts, todo, picks }) {
    const bad = alerts.filter((a) => a.tone === "bad");
    const tone = bad.length ? "bad" : alerts.length ? "warn" : "ok";
    // 2.0.0 [RVA-31] 검토 U24-11: "바로 볼 것이 1개"인데 아래 줄은 셋이던 것. 아래 셋에 든 종류마다 그 수를 함께 말해요.
    const kinds = [["bad", "바로 볼 것", bad.length], ["warn", "살펴볼 것", alerts.length - bad.length], ["todo", "할 일", todo.length]]
      .filter(([k, , n]) => n > 0 && picks.some((p) => p.tone === k));
    const line = kinds.length > 1 ? `${kinds.map(([, w, n]) => `${w} ${n}개`).join(kinds.length === 2 ? "와 " : ", ")}가 있어요`
      : bad.length ? `바로 볼 것이 ${bad.length}개 있어요` : alerts.length ? `살펴볼 것이 ${alerts.length}개 있어요`
      : todo.length ? `급한 일은 없고, 할 일이 ${todo.length}개예요` : "지금 기다리는 일이 없어요";
    const sum = [can("inquiries") ? `답 대기 ${o.inquiries ?? 0}` : "", can("review") ? `검토 대기 ${L.reviewCount(o)}` : "", o.doneToday ? `오늘 처리 ${o.doneToday}` : ""].filter(Boolean).join(" · ");
    const restA = alerts.length - picks.filter((p) => p.key.startsWith("a:")).length;
    const restT = todo.length - picks.filter((p) => !p.key.startsWith("a:")).length;
    const rest = [restA > 0 ? `주의 필요 ${restA}개` : "", restT > 0 ? `할 일 ${restT}개` : ""].filter(Boolean).join(", ");
    // 지금 할 일 하나(조건 2): 내가 답할 수 있는 문의가 기다리면 큐 모드. 할 일 카드에 있던 단추를 여기로 옮겼어요.
    const waitingAsks = o.inquiries ?? 0;
    const go = !S.queue && can("inquiries", "edit") && waitingAsks ? `<button class="btn primary" type="button" data-act="queue-start">문의 ${waitingAsks}건 차례로 답하기</button>` : "";
    return `<section class="gl-hero today-hero ${tone}" aria-labelledby="today-hero-h"><div class="gl-hero-top"><span class="gl-pulse" aria-hidden="true"><span></span></span>
        <div class="gl-hero-text"><p class="gl-eyebrow">${esc(hi)}, ${esc(S.me.name)}님</p><h2 id="today-hero-h">${esc(line)}</h2>${sum ? `<p class="gl-sub">${esc(sum)}</p>` : ""}</div></div>
        ${picks.length ? `<ol class="today-three">${picks.map((p, i) => `<li class="${p.tone}" data-swipe="${esc(p.key)}"><a href="${esc(p.to)}"><span class="n" aria-hidden="true">${i + 1}</span>
          <span class="t"><b>${esc(p.title)}</b><span class="sub">${p.due ? dueText(p.due, Date.now()) : ""}${p.meta ? `<small>${esc(p.meta)}</small>` : ""}</span></span>${icon(P.chevron)}</a>
          <button class="icon-btn pick-later" type="button" data-act="inbox-snooze" data-key="${esc(p.key)}" aria-label="내일 볼게요: ${esc(p.title)}" title="내일 아침까지 이 기기에서 숨겨요">${icon(P.moon)}</button></li>`).join("")}</ol>` : ""}
        ${go || picks.length ? `<div class="today-go">${go}${picks.length ? `<p class="today-rest">먼저 볼 ${picks.length}가지${rest ? ` · <button class="linkish" type="button" data-act="home-fold" data-key="home:todo">남은 ${esc(rest)} 보기</button>` : ""}</p>` : ""}</div>` : ""}</section>`;
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

  /**
   * 2.0.0 [RVA-37] 검토 I24c-01 '무엇 때문에 멈췄나': 서버가 한 줄(문의·개발사항)을 읽지 못해 빼 두고 계속 돌고 있으면, 그 줄과 까닭과
   * 할 수 있는 것(그 줄 열기 · 개발사항으로)을 한 장으로. 오류 묶음과 로그를 오가지 않고 0~1번 눌러 이어 가요. 주의(주황)예요: 장부는 멈추지 않았어요.
   */
  function stuckCard(raw) {
    if (!Array.isArray(raw) || !raw.length) return "";
    // 같은 줄을 여러 곳(장부·정리함)이 못 읽었으면 한 줄로: 곳 이름을 잇고, 개발사항 메모는 어느 것에서든.
    const byKey = new Map();
    for (const r of raw) {
      const k = r.type && r.id ? `${r.type}:${r.id}` : r.sig;
      const had = byKey.get(k);
      if (!had) byKey.set(k, { ...r, wheres: [r.whereName] });
      else {
        if (!had.wheres.includes(r.whereName)) had.wheres.push(r.whereName);
        had.total += r.total;
        if (!/^개발사항 /.test(had.note ?? "") && /^개발사항 /.test(r.note ?? "")) had.note = r.note;
      }
    }
    const order = ["ledger", "sort", "today", "inquiries"];
    const list = [...byKey.values()].map((r) => ({ ...r, wheres: [...r.wheres].sort((a, b) => order.indexOf(raw.find((x) => x.whereName === a)?.where) - order.indexOf(raw.find((x) => x.whereName === b)?.where)) }));
    const what = (r) => (r.type === "inquiry" ? "문의 한 줄" : r.type === "dev" ? "개발사항 한 줄" : "할 일 묶기");
    const open = (r) => (r.type === "inquiry" && r.id ? href("inquiries", r.id) : r.type === "dev" && r.id ? href("dev", r.id) : null);
    const devOf = (r) => /^개발사항 (d[A-Za-z0-9]{15})/.exec(r.note ?? "")?.[1] ?? null;
    return `<section class="card stuck" aria-labelledby="stuck-h"><div class="card-head"><h2 id="stuck-h">읽지 못한 ${list.length}줄은 빼고 돌고 있어요</h2></div>
      <ul class="stuck-list">${list.map((r) => {
        const to = open(r);
        const dev = devOf(r);
        // 오류 원문(영어일 수 있어요)은 접어 두고, 보이는 줄은 우리말로(조건 4·13, 검토 T23-06 '개발자가 아닌 최고 관리자').
        return `<li><p><b>${esc(r.wheres.join("·"))}의 ${esc(what(r))}</b> <small>읽다가 오류가 났어요 · ${r.total}번 · ${esc(ago(new Date(r.lastAt).toISOString()))}</small></p>
          <details class="quick" data-key="stuck:${esc(r.sig)}"><summary>오류 원문</summary><code class="mono">${esc(r.message)}</code></details>
          <div class="actions start">${to ? `<a class="btn small" href="${esc(to)}">그 줄 열기</a>` : ""}
          ${dev ? `<a class="btn small ghost" href="${href("dev", dev)}">개발사항 보기</a>` : S.me.owner ? `<button class="btn small" type="button" data-act="stuck-dev" data-id="${esc(r.sig)}">개발사항으로</button>` : ""}</div></li>`;
      }).join("")}</ul>
      <p class="hint">그 줄만 빼고 운영 장부·정리함·오늘은 그대로예요.${S.me.owner ? " 고친 뒤에는 관리 › 서버 상태 › 오류 묶음에서 '고쳤어요'." : " 최고 관리자 화면에도 같은 줄이 보여요."}</p></section>`;
  }

  /** 접어 두는 줄 하나: 제목 + 한 줄 요약(+ 숫자). 펼침은 기기에 남아요(S.opened). */
  function fold(key, title, meta, inner, count = null) {
    return `<details class="card home-fold" data-key="${key}" id="${key.replace(":", "-")}"${S.opened.get(key) ? " open" : ""}>
      <summary><span class="hf-t"><b>${esc(title)}</b>${meta ? `<small>${meta}</small>` : ""}</span>${count === null ? "" : `<span class="pill">${count}</span>`}</summary>
      <div class="hf-body">${inner}</div></details>`;
  }

  function homeView() {
    const t = S.data.today;
    const top = flagAlertCard();
    if (!t) return `${top}<section class="gl-hero skeleton" aria-hidden="true"></section>${skeleton(2)}`;
    const o = { ...(S.overview ?? {}), ...t };
    const tr = t.trend ?? {};
    const hour = new Date().getHours();
    const hi = hour < 6 ? "늦은 밤이에요" : hour < 12 ? "좋은 아침이에요" : hour < 18 ? "좋은 오후예요" : "좋은 저녁이에요";
    const inq = (params) => L.routeHash("inquiries", null, params);
    // The four that need a hand, on every screen size.
    const core = [];
    const coreLine = [];
    if (can("inquiries")) {
      core.push(kpi({ label: "답 대기", value: o.inquiries, to: inq({ status: "open", sort: "sla" }), desc: `오늘 새 문의 ${o.inquiriesToday ?? 0}건`, list: tr.inquiries, name: "날마다 새 문의" }));
      coreLine.push(`답 대기 ${o.inquiries ?? 0}`);
    }
    if (can("review")) {
      core.push(kpi({ label: "검토 대기", value: L.reviewCount(o), to: href("review"), desc: `제보 ${o.pending ?? 0} · 앱에 없는 방식 ${o.asks ?? 0} · 달라요 ${o.flagged ?? 0}`, list: tr.reports, name: "날마다 받은 제보" }));
      coreLine.push(`검토 대기 ${L.reviewCount(o)}`);
    }
    // AT 1.29 [ADMCP-04] New problem reports nobody copied for development yet, one tap from the list that copies them.
    if (can("inquiries")) {
      core.push(kpi({ label: "복사 전 문제 신고", value: o.bugsUncopied, to: inq({ status: "all", kind: "bug", copied: "no" }), desc: "개발에 넘기기 전이에요", list: tr.bugs, name: "날마다 문제 신고" }));
      coreLine.push(`복사 전 신고 ${o.bugsUncopied ?? 0}`);
    }
    if (can("review") && t.versionConflicts !== undefined) {
      core.push(kpi({ label: "정할 판", value: t.versionConflicts, to: href("versions"), desc: "한 부서에 판이 둘 이상", hot: true, go: "정하러 가기", done: "다 정했어요" }));
      coreLine.push(`정할 판 ${t.versionConflicts}`);
    }
    const rest = [];
    // hot: true(기다리는 일) | "warn"(주의) | "bad"(정말 급함, 빨강).
    const tile = (k, v, d, to, hot) => rest.push(`<a class="tile${hot && v ? ` hot${hot === true ? "" : ` ${hot}`}` : ""}" href="${to}"><span class="k">${esc(k)}</span><span class="v">${v ?? 0}</span><span class="d">${esc(d)}</span></a>`);
    if (can("inquiries")) {
      tile("오늘 새 문의", o.inquiriesToday, `어제 ${o.inquiriesYesterday ?? 0}건`, inq({ status: "open", sort: "newest" }), false);
      tile("오늘 목표 넘김", o.slaLateToday, "오늘 안에 답 목표를 넘겨요", inq({ status: "open", sort: "sla" }), "bad");
      tile("아쉬워요", o.inquiriesBad, "답이 아쉬웠다는 문의", inq({ status: "answered" }), "warn");
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
    const feed = o.recent ?? null;
    const people = (o.assigned ?? []).length ? `<h3 class="hf-h">누가 몇 건 맡았나</h3><ul class="people">${o.assigned.map((a) =>
      `<li>${a.id === S.me.id ? "나" : esc(a.name)} ${a.n}</li>`).join("")}</ul>` : "";
    const views = S.prefs.views.filter((v) => String(v.hash).startsWith("#/"));
    const hero = heroPicks(t);
    // 2.0.0 [RVA-36] 검토 U23-01·ADM-12: 위 셋과 저절로 끝난 일 밖의 것은 모두 접힌 줄 하나씩(1440에서 3,682px → 한 화면).
    const hide = snoozed();
    const left = (t.todo?.items ?? []).filter((x) => !hide[`${x.type}:${x.id}`]);
    const late = left.filter((x) => x.due && Date.parse(x.due) < Date.parse(t.todo?.now ?? "")).length;
    const alertsN = Array.isArray(t.alerts) ? t.alerts.length : 0;
    const todoMeta = [late ? `<span class="late">넘김 ${late}</span>` : "", alertsN ? `주의 필요 ${alertsN}` : "", `할 일 ${left.length}`].filter(Boolean).join(" · ");
    const todoFold = fold("home:todo", "할 일 모두", todoMeta, `${alertsCard(t.alerts, hero.shown)}${todoCard(t.todo, hero.shown)}`, alertsN + left.length);
    const numbers = core.length || rest.length || stats.length ? fold("home:tiles", "숫자", esc(coreLine.join(" · ")), `${core.length ? `<div class="tiles core">${core.join("")}</div>` : ""}
        ${rest.length ? `<div class="tiles">${rest.join("")}</div>` : ""}
        ${stats.length ? `<p class="stats">${stats.map(([k, v]) => `<span>${esc(k)}<b>${v ?? 0}</b></span>`).join("")}</p>` : ""}${people}`) : "";
    const recent = can("audit") ? fold("home:recent", "최근 활동", feed?.length ? `${esc(ago(feed[0].at))} · ${esc(L.actionName(feed[0].action))}` : "",
      `${feed === null ? '<div class="card skeleton short"></div>' : feed.length ? `<ul class="feed">${feed.map(feedItem).join("")}</ul>` : '<p class="hint">아직 활동이 없어요.</p>'}
        <p class="actions start"><a class="btn small ghost" href="${href("audit")}">활동 기록 모두 보기</a></p>`) : "";
    return `${top}${stuckCard(t.stuck)}${heroCard(t, o, hi, hero)}${nightCard(t.night)}
      <div class="home-more">${todoFold}${numbers}${recent}</div>
      <div class="home-foot"><a href="#/desk">${icon(P.check)}<span>운영 장부</span>${icon(P.chevron)}</a>${views.length ? `<nav class="home-views" aria-label="저장한 보기">${views.map((v) => `<a href="${esc(v.hash)}"><span>${esc(v.name)}</span>${icon(P.chevron)}</a>`).join("")}</nav>` : ""}</div>
      <p class="hello-who">${S.me.owner ? "최고 관리자로 로그인했어요." : `내 권한: ${esc(L.permSummary(S.me.perms, S.me.areas ?? {}, A.toggles()))}`}</p>`;
  }

  /**
   * [ADMUX-33] 할 일: everything waiting on this admin's areas in one list, nearest deadline first. [ADMX-01] One line
   * per item: what and where on the left, the deadline and the buttons on the right.
   */
  function todoCard(d, shown = new Set()) {
    const c = d?.counts ?? {};
    const editInq = can("inquiries", "edit");
    // 2.0.0 [RVA-36] '차례로 답하기'(큐 모드)는 위 큰 카드의 채운 단추 하나로 옮겼어요.
    const head = `<div class="card-head"><h2>할 일</h2></div>
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
          <a class="btn small" href="${inboxLink(x)}">${x.type === "inquiry" ? "열기" : "보기"}</a>
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

  /**
   * 2.0.0 [RVA-36] 검토 I23c-01 '엄지로 넘기며 끝내요': 휴대폰에서 위 셋의 줄을 왼쪽으로 밀면 [내일 볼게요](되돌리기 15초), 오른쪽으로 밀면 열기.
   * 보이는 단추는 그대로 있어요(밀기는 덤). 세로 스크롤은 막지 않아요(touch-action: pan-y). 민 뒤 따라오는 누르기는 먹어요.
   */
  if (typeof document !== "undefined") {
    let sw = null;
    // 민 뒤 바로 따라오는 누르기(손을 뗀 자리에 다시 그려진 다음 줄 위)는 먹어요.
    let quietUntil = 0;
    document.addEventListener("pointerdown", (e) => {
      if (e.pointerType !== "touch") return;
      const li = e.target.closest?.(".today-three li[data-swipe]");
      sw = li ? { li, x: e.clientX, y: e.clientY, dx: 0, on: false } : null;
    }, { passive: true });
    document.addEventListener("pointermove", (e) => {
      if (!sw) return;
      const dx = e.clientX - sw.x;
      const dy = e.clientY - sw.y;
      if (!sw.on) {
        if (Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(dx)) {
          sw = null;
          return;
        }
        if (Math.abs(dx) < 12) return;
        sw.on = true;
        sw.li.classList.add("swiping");
      }
      sw.dx = dx;
      sw.li.style.setProperty("--dx", `${Math.max(-120, Math.min(120, dx))}px`);
      sw.li.dataset.dir = dx < 0 ? "later" : "open";
    }, { passive: true });
    const end = () => {
      if (!sw) return;
      const { li, dx, on } = sw;
      sw = null;
      li.classList.remove("swiping");
      li.style.removeProperty("--dx");
      delete li.dataset.dir;
      if (!on) return;
      quietUntil = 0;
      if (dx <= -80) li.querySelector(".pick-later")?.click();
      else if (dx >= 80) li.querySelector("a")?.click();
      quietUntil = Date.now() + 400;
    };
    document.addEventListener("pointerup", end);
    document.addEventListener("pointercancel", end);
    document.addEventListener("click", (e) => {
      if (Date.now() < quietUntil) {
        e.preventDefault();
        e.stopPropagation();
      }
    }, true);
  }

  const actions = {
    // '남은 N개 보기': 아래 접힌 '할 일 모두'를 펴고 그 자리로.
    "home-fold": ({ key }) => {
      // 다시 그리지 않고 그 줄만 펴요(다시 그리면 화면은 지금 펼침을 그대로 지켜요).
      const d = document.getElementById(String(key).replace(":", "-"));
      if (!d) return;
      d.open = true;
      S.opened.set(key, true);
      d.scrollIntoView?.({ block: "start", behavior: matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
      d.querySelector("summary")?.focus({ preventScroll: true });
    },
    // 2.0.0 [RVA-37] 읽지 못한 줄을 개발사항으로(최고 관리자). 오류 묶음은 '보는 중'이 되고 메모에 개발사항 번호가 남아요.
    "stuck-dev": async ({ id }) => {
      const r = await A.act(() => A.api(`/watch/errors/${id}/dev`, "POST", {}), "개발사항으로 넘겼어요");
      if (r) A.load();
    },
  };

  return { homeView, todoCard, feedItem, alertsCard, actions };
}
