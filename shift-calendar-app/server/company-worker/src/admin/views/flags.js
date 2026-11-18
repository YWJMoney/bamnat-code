// 2.0.0 [RVA-57] 검토 ADM-11·I23c-02: AM 1.28 기능 관리(목록·일정·기능 하나·창들·근무 유형·기능별 효과)를 첫 묶음에서 떼어 낸 화면 묶음이에요.
// 그 화면을 열 때 받아요(src/admin-app.js adminFiles). 다른 묶음처럼 문자열로 보내지니 바깥 이름은 A로만 받아요.
export function flagsViews(A) {
  const { L, P, S, V, act, ago, api, app, busy, confirmBox, danger, day, dialog, empty, enter, esc, etags, fieldError, flagLink, go, goFromDialog, href, hubs, icon, lines, listHead, load, md, moment, openDialog, render, seg, skeleton, syncUrl, tabName, timeline, toast, updateBadges, view, visits, wide } = A;


  const FLAG_STATE = { forced_off: ["bad", "강제로 끔"], forced_on: ["info", "강제로 켬"], on: ["ok", "켜짐"], waiting: ["warn", "대기"], off: ["", "꺼짐"] };
  const FLAG_SHOW = [["all", "전체"], ["waiting", "대기"], ["on", "켜짐"], ["forced_off", "강제로 끔"], ["cleanup", "정리 대상"]];
  const FLAG_GROUPS = { money: "결제·광고", type: "근무 유형", law: "법 시행", server: "서버 기능", stats: "사용 통계", free: "무료로 연 프로 기능", phase3: "Phase 3(출시일에 함께 켜짐 · 날짜는 일정에서)" };
  /** 1.22's 기능 스위치 by their flag (운영 screen's card links each to its flag). */
  const SWITCH_FLAGS = { PLAZA_WRITE: "plaza.write", COMPANY_REPORT: "company.report", INQUIRY_PHOTOS: "inquiry.photos", SHARE_CREATE: "share.create",
    USAGE_STATS: "usage.stats", PLAZA_NEWS: "plaza.news" };
  const PRIVACY_URL = "https://ywjmoney.github.io/-/privacy/";
  const LOCK = {
    off: "강제로 끄기는 최고 관리자나 '기능 강제로 끄기' 권한을 받은 관리자만 해요(결정 D97).",
    on: "강제로 켜기는 최고 관리자만 해요.",
    legal: "법적 기능이라 조건 바꾸기·확인·되돌리기는 최고 관리자만 해요.",
    edit: "조건을 바꾸려면 기능 관리 고치기 권한이 있어야 해요.",
  };

  const statePill = (r) => {
    const [tone, label] = FLAG_STATE[r.state] ?? ["", r.state];
    const left = r.state === "waiting" && r.daysLeft !== null && r.daysLeft !== undefined && r.daysLeft >= 0 ? ` · ${r.daysLeft === 0 ? "오늘" : `${r.daysLeft}일 남음`}` : "";
    return `<span class="pill ${tone}">${label}${left}</span>`;
  };
  const riskPill = (r) => (r.risk === "LEGAL" ? '<span class="pill gold" title="결제·광고·새로 보내는 정보처럼 법적 조건이 붙은 기능">법적</span>' : "");
  const flagPills = (r) => [statePill(r), riskPill(r), r.warnings?.length ? '<span class="pill bad">고지 기간 확인</span>' : "",
    r.sunsetDue ? '<span class="pill">정리 대상</span>' : "", r.reviewDue ? '<span class="pill warn">다시 볼 날</span>' : ""].join("");

  /** What a step needs, by the reach the server sent: null when allowed, else the reason the button is locked. */
  function flagLock(r, reach, step) {
    if (reach.owner) return null;
    const legal = r.risk === "LEGAL";
    const forced = r.override?.force && !(r.override.until && Date.parse(r.override.until) <= Date.now()) ? r.override.force : null;
    if (step === "on") return LOCK.on;
    if (step === "off") return reach.off ? null : LOCK.off;
    if (step === "clear") return forced === "on" ? (reach.off ? null : LOCK.off) : legal ? LOCK.legal : reach.off ? null : LOCK.off;
    if (step === "conditions") return legal ? LOCK.legal : reach.edit ? null : LOCK.edit;
    if (step === "check") return legal ? LOCK.legal : reach.edit ? null : LOCK.edit;
    return null;
  }
  const lockedBtn = (label, lock, attrs, tone = "") => `<button class="btn small${tone ? ` ${tone}` : ""}" ${lock ? `disabled title="${esc(lock)}"` : attrs}>${esc(label)}</button>`;

  // ---- the list [ADMUX-21] ----

  function flagsView() {
    const d = S.data.flags;
    const timeline = S.flagView === "timeline";
    const viewSeg = `<div class="segmented" role="group" aria-label="보기">${seg("flag-view", "", S.flagView, "목록")}${seg("flag-view", "timeline", S.flagView, "일정")}</div>`;
    if (!d) return listHead(viewSeg, "") + skeleton(3);
    if (timeline) return listHead(viewSeg, "") + flagTimeline(d);
    const rows = d.flags;
    const match = (r) => ({ all: true, waiting: r.state === "waiting", on: r.state === "on" || r.state === "forced_on", forced_off: r.state === "forced_off", cleanup: r.sunsetDue }[S.flagShow] ?? true);
    const q = S.flagQuery.trim().toLowerCase();
    const found = rows.filter(match).filter((r) => !q || `${r.name} ${r.key} ${r.what}`.toLowerCase().includes(q));
    const counts = Object.fromEntries(FLAG_SHOW.map(([k]) => [k, rows.filter((r) => ({ all: true, waiting: r.state === "waiting", on: r.state === "on" || r.state === "forced_on",
      forced_off: r.state === "forced_off", cleanup: r.sunsetDue })[k]).length]));
    const alerts = rows.filter((r) => r.warnings?.length);
    // 2.0.0 [RVA-24] 검토 HQ-03(조건 1): 목록·일정과 찾기가 한 줄, 상태 칩은 '거르기' 안, 설명은 목록 아래 한 줄(펴면 설명).
    const chips = `<div class="chips" role="group" aria-label="상태">${FLAG_SHOW.map(([k, label]) =>
        `<button type="button" class="chip pick" data-act="flag-show" data-key="${k}" aria-pressed="${S.flagShow === k}">${label}${counts[k] ? ` ${counts[k]}` : ""}</button>`).join("")}</div>`;
    const head = listHead(viewSeg, `<input id="search" type="search" placeholder="이름이나 키로 찾기(예: billing, 결제)" value="${esc(S.flagQuery)}" autocomplete="off" enterkeyhint="search" aria-label="기능 찾기">`,
      { busy: Boolean(q), filters: chips, active: S.flagShow !== "all" ? 1 : 0 });
    const filters = ``;
    const foot = `<div class="list-foot"><details class="tip" data-key="flag-how"><summary>기능은 조건대로 저절로 켜져요 · 법적 기능은 처리방침 조건을 지켜요</summary>
      <p class="hint">앱 안에 들어 있는 기능이 언제 저절로 켜지는지(날짜·처리방침·확인), 지금 왜 그런지 보여요. 서버는 덮어쓸 것만 보내고, 휴대폰은 인터넷이 없어도 조건대로 켜요. 법적 기능은 강제로 켜도 처리방침 조건을 계속 지켜요.${d.statsPhones ? ` 켜진 비율은 지난 7일 '사용 통계 더 보내기'를 보낸 휴대폰 ${d.statsPhones}대 기준이에요.` : ""}</p></details></div>`;
    if (!found.length) return head + filters + empty(q ? "찾는 기능이 없어요" : "이 상태의 기능이 없어요", "", '<button class="btn" data-act="flag-show" data-key="all">전체 보기</button>');
    // [RVA-24] 고지 기간이 모자란 기능은 경고 상자 대신 목록 맨 위 묶음으로(조건 1·3·6): 줄 자체가 할 일이고, 누르면 그 기능으로 가요.
    const warned = new Set(alerts.map((r) => r.key));
    const groups = [...(found.some((r) => warned.has(r.key)) ? ["warned"] : []), ...new Set(found.filter((r) => !warned.has(r.key)).map((r) => r.group))];
    const inGroup = (r, g) => (g === "warned" ? warned.has(r.key) : !warned.has(r.key) && r.group === g);
    const groupName = (g) => (g === "warned" ? "고지 기간이 모자라요 · 처리방침 9항(7일, 불리하면 30일)" : FLAG_GROUPS[g] ?? g);
    return head + filters + groups.map((g) => `<h2 class="day-head${g === "warned" ? " bad" : ""}" ${g === "warned" ? 'role="status"' : ""}>${esc(groupName(g))}</h2><ul class="rows">${found.filter((r) => inGroup(r, g)).map((r) =>
      `<li><a class="row" href="${flagLink(r.key)}"><span class="row-main"><b>${esc(r.name)}</b><small>${esc(r.key)}${r.onShare !== null && r.onShare !== undefined ? ` · 휴대폰 ${r.onShare}%에서 켜짐` : ""}</small>
        <span class="preview">${esc(r.reason)}</span></span><span class="row-side pills">${flagPills(r)}</span>${icon(P.chevron)}</a></li>`).join("")}</ul>`).join("") + foot;
  }

  // ---- the calendar [ADMUX-25] ----

  function flagTimeline(d) {
    const today = L.flagDay(Date.now());
    const from = L.flagDay(Date.now() - 14 * 864e5);
    const items = [];
    const add = (day, text, to, tone, kind) => day && day >= from && items.push({ day, text, to, tone, kind });
    for (const r of d.flags) {
      for (const c of r.conditions.flatMap((c) => (c.type === "any" ? c.of : [c]))) {
        if (c.type !== "date") continue;
        const day = L.dateFrom(c, d.policy);
        if (day && day > today) add(day, `${r.name}: ${L.conditionLabel(c, d.policy)} 조건`, flagLink(r.key), r.risk === "LEGAL" ? "gold" : "info", "날짜 조건");
      }
      if (r.override?.until) add(L.flagDay(Date.parse(r.override.until)), `${r.name}: 강제${r.override.force === "on" ? " 켬" : " 끔"} 기한 끝`, flagLink(r.key), "warn", "강제 기한");
      if (r.override?.reviewAt) add(r.override.reviewAt, `${r.name}: 강제로 끈 것 다시 보기`, flagLink(r.key), "warn", "다시 볼 날");
    }
    for (const p of d.policy) {
      add(p.posted, `처리방침 ${p.version} 게시`, null, "", "처리방침");
      add(p.effective, `처리방침 ${p.version} 시행`, null, "info", "처리방침");
      if (p.heldUntil) add(p.heldUntil, `처리방침 ${p.version}: 동의하지 않은 휴대폰에도 시행`, null, "info", "처리방침");
    }
    items.sort((a, b) => a.day.localeCompare(b.day));
    let month = "";
    const out = [];
    for (const x of items) {
      const m = x.day.slice(0, 7);
      if (m !== month) {
        if (month) out.push("</ul>");
        out.push(`<h2 class="day-head">${Number(m.slice(0, 4))}년 ${Number(m.slice(5))}월</h2><ul class="rows agenda">`);
        month = m;
      }
      const n = L.daysLeft(x.day);
      const when = x.day === today ? "오늘" : n > 0 ? `D-${n}` : "지남";
      const inner = `<span class="date">${md(x.day)}<small>${when}</small></span><span class="row-main"><b class="wrap">${esc(x.text)}</b></span><span class="pill ${x.tone}">${esc(x.kind)}</span>`;
      out.push(`<li>${x.to ? `<a class="row" href="${x.to}">${inner}</a>` : `<div class="row">${inner}</div>`}</li>`);
    }
    if (month) out.push("</ul>");
    const o = S.data.flagOps;
    const chores = o ? `<section class="card"><div class="card-head"><h2>이달 운영 할 일</h2><a class="btn small ghost" href="${href("chores")}">이달 할 일에서 보기</a></div>
      <ul class="rows">${o.items.map((i) => `<li><div class="row"><span class="row-main"><b class="wrap">${esc(i.text)}</b><small>${esc(i.source)}</small></span>
        <span class="pill ${i.done ? "ok" : ""}">${i.done ? "마침" : "남음"}</span></div></li>`).join("")}</ul></section>` : "";
    const owner = d.reach.owner;
    const policy = `<section class="card"><div class="card-head"><h2>처리방침 일정</h2>${owner ? '<button class="btn small" data-act="flag-policy">판 더하기·날짜 고치기</button>' : ""}</div>
      <p class="hint">휴대폰은 앱에 든 날짜를 쓰다가 서버가 고친 날짜로 바꿔요. 시행일이 바뀌면 그 판에 기댄 기능도 함께 움직여요.${owner ? "" : " 고치기는 최고 관리자만 해요."}</p>
      <ul class="rows">${d.policy.map((p) => `<li><div class="row"><span class="row-main"><b>처리방침 ${esc(p.version)}${p.changed ? ' <span class="pill info">고침</span>' : ""}</b>
        <small>게시 ${esc(p.posted)} · 시행 ${esc(p.effective)}${p.heldUntil ? ` · 동의 없이 ${esc(p.heldUntil)}` : ""}${p.unfavourable ? " · 불리한 변경(30일 전 고지)" : ""}</small></span>
        ${owner ? `<button class="btn small ghost" data-act="flag-policy" data-version="${esc(p.version)}">고치기</button>` : ""}</div></li>`).join("")}</ul></section>`;
    return `${out.length ? out.join("") : empty("다가오는 날짜가 없어요", "날짜 조건, 강제 기한, 다시 볼 날, 처리방침 날짜가 여기에 모여요.")}<div class="grid two">${policy}${chores}</div>`;
  }

  // ---- one flag [ADMUX-22][ADMUX-23][ADMUX-24] ----

  function flagHeading() {
    const r = S.data.flag;
    const ok = r && r.key === S.route.id;
    return { title: ok ? r.name : "기능", sub: ok ? `${r.key} · ${(FLAG_STATE[r.state] ?? ["", ""])[1]}` : "", back: href("flags") };
  }

  function flagView() {
    const r = S.data.flag;
    if (!r || r.key !== S.route.id) return skeleton(3);
    const reach = r.reach;
    const share = r.onShare !== null && r.onShare !== undefined ? `휴대폰 ${r.onShare}%` : null;
    const top = `<section class="card${r.alert ? " flag-alert" : ""}"><div class="card-head"><h2>지금 상태</h2><span class="pills">${flagPills(r)}</span></div>
      <p class="lead">${esc(r.reason)}</p>
      ${r.next ? `<p class="meta">다음 날짜: ${md(r.next)}(${r.daysLeft === 0 ? "오늘" : `${r.daysLeft}일 남음`}) 0시, 한국 시간</p>` : ""}
      ${r.override?.force ? `<p class="note ${r.override.force === "off" ? "bad" : "info"}">${esc(r.override.by || "시스템")} · ${esc(moment(r.override.at))}에 강제로 ${r.override.force === "off" ? "껐어요" : "켰어요"}${r.override.auto ? "(사용량 자동 쉼)" : ""}: ${esc(r.override.why)}${r.override.until ? ` · ${esc(moment(r.override.until))}까지` : ""}${r.override.reviewAt ? ` · 다시 볼 날 ${esc(r.override.reviewAt)}` : ""}</p>` : ""}
      ${r.alert ? `<p class="note bad" role="status">켜진 지 24시간이 안 됐어요(${esc(ago(r.onSince))}). 문제가 보이면 바로 강제로 꺼 주세요.</p>` : ""}
      ${r.warnings.map((w) => `<p class="note warn">${esc(w)}</p>`).join("")}
      ${r.sunsetDue ? `<p class="note">정리 예정 판(${esc(r.sunset)})이 나왔어요. 두 판 넘게 켜져 있었다면 코드에서 이 플래그를 걷어 내요.</p>` : ""}
      <div class="actions start">
        ${r.state === "forced_off" ? lockedBtn("강제 끔 풀기", flagLock(r, reach, "clear"), `data-act="flag-clear" data-key="${esc(r.key)}"`, "primary")
          : lockedBtn("강제로 끄기", flagLock(r, reach, "off"), `data-act="flag-force" data-key="${esc(r.key)}" data-force="off"`, "danger")}
        ${r.override?.force === "on" ? lockedBtn("강제 켬 풀기", flagLock(r, reach, "clear"), `data-act="flag-clear" data-key="${esc(r.key)}"`)
          : r.state !== "forced_off" ? lockedBtn("강제로 켜기", flagLock(r, reach, "on"), `data-act="flag-force" data-key="${esc(r.key)}" data-force="on"`) : ""}
        ${lockedBtn("조건 고치기", flagLock(r, reach, "conditions"), `data-act="flag-cond" data-key="${esc(r.key)}"`)}</div>
      ${[["off", "강제로 끄기"], ["on", "강제로 켜기"], ["conditions", "조건 고치기"]].map(([s, label]) => [label, flagLock(r, reach, s)]).filter(([, l]) => l)
        .map(([label, l]) => `<p class="help">${esc(label)}: ${esc(l)}</p>`).join("")}</section>`;

    const checks = r.checks ?? {};
    const lockCheck = flagLock(r, reach, "check");
    const list = `<section class="card"><h2>켜지는 조건</h2>
      ${r.override?.conditions ? `<p class="note info">앱에 든 조건 대신 관리자가 바꾼 조건이에요(${esc(r.override.by)} · ${esc(moment(r.override.at))}).</p>` : ""}
      ${r.checklist.length ? `<ul class="checklist">${r.checklist.map((c) => {
        const cls = c.met === true ? "met" : c.met === "phone" ? "phone" : "unmet";
        const mark = c.met === true ? "✓" : c.met === "phone" ? "~" : "○";
        const what = c.met === true ? "채움" : c.met === "phone" ? `휴대폰마다 달라요${share ? ` · 지금 켜진 ${share}` : ""}` : "남음";
        const manual = c.keys.map((k) => {
          const got = checks[k];
          return got ? `<small>${esc(r.manualNames?.[k] ?? k)}: ${esc(got.by || "관리자")} · ${esc(moment(got.at))}에 확인${got.note ? ` · ${esc(got.note)}` : ""}</small>
              ${lockedBtn("확인 취소", lockCheck, `data-act="flag-check" data-name="${esc(k)}" data-done=""`, "ghost")}`
            : lockedBtn("확인했어요", lockCheck, `data-act="flag-check" data-name="${esc(k)}" data-done="1"`, "primary");
        }).join("");
        return `<li class="${cls}"><span class="mark" aria-hidden="true">${mark}</span><span class="c-main">${esc(c.label)}<small>${what}</small></span>${manual}</li>`;
      }).join("")}</ul>` : `<p class="meta">조건이 없어요. ${r.default ? "기본으로 켜져 있어요." : "기본으로 꺼져 있고, 강제로 켜야 열려요."}</p>`}
      ${lockCheck && r.manualKeys.length ? `<p class="help">확인: ${esc(lockCheck)}</p>` : ""}
      ${flagRangeBlock(r, reach)}</section>`;

    const b = r.basis ?? {};
    const about = `<section class="card"><h2>무엇이고 왜 꺼 뒀나</h2>
      <dl class="kv"><dt>하는 일</dt><dd>${esc(r.what)}</dd><dt>꺼 둔 이유</dt><dd>${esc(r.whyOff)}</dd>
        <dt>근거</dt><dd>${[b.policy ? `<a href="${PRIVACY_URL}" target="_blank" rel="noopener">처리방침 ${esc(b.policy)}</a>` : "", b.law ? esc(b.law) : "", b.note ? esc(b.note) : "",
          b.decision ? `결정 ${esc(b.decision)}` : "", b.req ? esc(b.req) : ""].filter(Boolean).join(" · ") || "–"}</dd>
        <dt>담당·만든 판</dt><dd>${esc(r.owner)} · ${esc(r.since)}${r.sunset ? ` · 정리 예정 ${esc(r.sunset)}` : ""}</dd>
        ${r.switchKey ? `<dt>옛 이름</dt><dd>기능 스위치 ${esc(r.switchKey)}(1.27까지의 앱은 이 이름으로 읽어요)</dd>` : ""}</dl></section>`;

    const phones = `<section class="card"><h2>휴대폰</h2>
      ${r.statsPhones ? `<p class="lead">${share ? `지난 7일 ${share}에서 켜져 있었어요` : "아직 켜진 휴대폰이 없어요"}</p><p class="meta">'사용 통계 더 보내기'를 보낸 휴대폰 ${r.statsPhones}대 기준(휴대폰 구별 없음)</p>
        ${r.series.length ? L.barChart(r.series.map((x) => x.share ?? 0), { labels: r.series.map((x) => x.day.slice(5).replace("-", "/")), name: "날마다 켜진 휴대폰 비율(%)" }) : ""}
        ${r.versions.length ? `<ul class="rows">${r.versions.map((v) => `<li><div class="row"><span class="row-main"><b>${esc(v.app)}</b><small>${v.code ? `번호 ${v.code}` : "번호 모름"}</small></span>
          <span class="row-side"><b>${v.share ?? "–"}%</b><span class="meta">${v.phones}대</span></span></div></li>`).join("")}</ul>` : ""}`
        : '<p class="meta">아직 켜진 기능 목록을 보낸 휴대폰이 없어요. \'사용 통계 더 보내기\'(처리방침 3.2)가 시행되고 동의한 휴대폰부터 와요.</p>'}
      <h3 class="section">켜지기 전후 ${r.compare.days}일</h3>
      ${r.compare.measured ? `<dl class="kv"><dt>문제 신고</dt><dd>${r.compare.bugsBefore} → ${r.compare.bugsAfter}${r.compare.bugsAfter > r.compare.bugsBefore ? ' <span class="pill warn">늘었어요</span>' : ""}</dd>
        <dt>서버 오류</dt><dd>${r.compare.errorsBefore} → ${r.compare.errorsAfter}${r.compare.errorsAfter > r.compare.errorsBefore ? ' <span class="pill warn">늘었어요</span>' : ""}</dd></dl>
        <p class="help">${esc(moment(r.compare.pivot))}(서버가 마지막으로 켜짐·꺼짐을 본 때) 앞뒤예요. 켠 휴대폰과 안 켠 휴대폰을 나눠 세지는 않아요.</p>` : '<p class="meta">서버가 아직 이 기능의 켜짐·꺼짐을 기록하지 않았어요(매시 17분).</p>'}</section>`;

    const billing = r.key === "billing" ? `<section class="card"><h2>정식 출시</h2>
      <dl class="kv"><dt>정식 출시일</dt><dd>${r.launchedAt ? esc(moment(new Date(r.launchedAt).toISOString())) : "아직(처음 켜지는 순간 서버가 한 번 적어요)"}</dd></dl>
      <form data-form="flag-thanks"><div class="fieldset"><label for="flag-thanks">테스트 참여자 감사 보상(개월)</label>
        <input id="flag-thanks" name="thanks" type="number" min="0" max="12" value="${r.thanksMonths ?? 1}"${reach.owner ? "" : " disabled"}>
        <span class="help">정식 출시 전에 설치한 휴대폰에 출시일부터 드리는 프로 개월 수예요. ${reach.owner ? "" : "최고 관리자만 바꿔요."}</span></div>
        ${reach.owner ? '<div class="actions"><button class="btn" type="submit">저장</button></div>' : ""}</form></section>` : "";

    const sim = flagSimCard(r);
    const hist = `<section class="card"><h2>기록</h2>${r.history.length ? `<ul class="feed">${r.history.map((h) => `<li><span class="when">${esc(moment(h.at))}</span>
      <span><b>${esc(L.actionName(h.action))}</b> <span class="meta">${esc(flagSummary(h.before))} → ${esc(flagSummary(h.after))}</span>${h.why ? `<span class="meta">${esc(h.why)}</span>` : ""}</span>
      <span class="who">${esc(h.actor)}</span></li>`).join("")}</ul>` : '<p class="meta">아직 바꾼 적이 없어요.</p>'}</section>`;
    return `${top}<div class="grid two">${list}${about}${phones}${billing}${sim}</div>${hist}`;
  }

  /**
   * 1.28 통합 [FLAG-36] AL-D3 앱 판 범위: the versionCode range the flag works in (판단 2단계). Outside it a phone keeps it
   * off whatever else holds, so a feature that breaks on one version can be kept from it without a force for everyone.
   */
  function flagRangeBlock(r, reach) {
    const lo = r.override?.minApp ?? null;
    const hi = r.override?.maxApp ?? null;
    const lock = flagLock(r, reach, "conditions");
    return `<h3 class="section">앱 판 범위</h3>
      <p class="meta">${lo == null && hi == null ? "모든 앱 판에서 조건대로 켜져요." : `앱 번호 ${esc(L.appRange(lo, hi))}에서만 켜질 수 있어요. 그 밖의 판은 다른 조건과 상관없이 꺼져요.`}</p>
      <div class="actions start">${lockedBtn("앱 판 범위 고치기", lock, `data-act="flag-range" data-key="${esc(r.key)}"`)}</div>
      ${lock ? `<p class="help">앱 판 범위: ${esc(lock)}</p>` : ""}`;
  }

  function openFlagRange(key) {
    const r = S.data.flag;
    const lo = r.override?.minApp ?? "";
    const hi = r.override?.maxApp ?? "";
    openDialog(`<form data-form="flag-range" data-key="${esc(key)}"><h2>${esc(r.name)}의 앱 판 범위</h2>
      <p>앱 번호(versionCode)가 이 범위 밖인 휴대폰은 이 기능을 꺼요. 강제로 끈 것 다음으로 먼저 따져요. 비우면 그쪽 끝이 없어요.</p>
      <div class="fieldset"><label for="fr-min">이 번호부터</label><input id="fr-min" name="minApp" type="number" min="1" max="999999" inputmode="numeric" value="${esc(lo)}" placeholder="예: 39"></div>
      <div class="fieldset"><label for="fr-max">이 번호까지</label><input id="fr-max" name="maxApp" type="number" min="1" max="999999" inputmode="numeric" value="${esc(hi)}" placeholder="비우면 끝 없음"></div>
      <div class="fieldset"><label for="fr-why">이유(필수)</label><input id="fr-why" name="why" maxlength="300" required></div>
      <div class="actions">${lo !== "" || hi !== "" ? '<button class="btn ghost" type="submit" name="reset" value="1" formnovalidate>범위 없애기</button>' : ""}
        <button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
  }

  /** One side of a history line in words. */
  function flagSummary(o) {
    if (!o) return "없음";
    if (o.check) return `${S.data.flag?.manualNames?.[o.check] ?? o.check} 확인`;
    if (o.thanksMonths !== undefined) return `${o.thanksMonths}개월`;
    if (o.effective) return `게시 ${o.posted} · 시행 ${o.effective}${o.heldUntil ? ` · 동의 없이 ${o.heldUntil}` : ""}`;
    const parts = [];
    if (o.force) parts.push(`강제로 ${o.force === "on" ? "켬" : "끔"}`);
    if (o.conditions) parts.push(`조건 ${L.conditionText(o.conditions).replace(/\n/g, " · ")}`);
    if (o.minApp != null || o.maxApp != null) parts.push(`앱 판 ${L.appRange(o.minApp, o.maxApp)}`);
    return parts.join(", ") || "조건대로";
  }

  /** 2.0.0 [RVA-27] 검토 ADM-21: 미리 보기 휴대폰의 앱 번호 기본값은 지금 나간 판(서버 값), 모르면 2.0.0(54). 옛 값 39(1.28)는 굳은 숫자였어요. */
  const simApp = (r) => String(r?.versionCode ?? S.data.notices?.latestApp?.code ?? 54);

  /** [ADMUX-24] 미리 보기: one pretend phone through the same evaluator, to see what a change does before it is made. */
  function flagSimCard(r) {
    const sim = S.flagSim ?? {};
    const today = sim.day || L.flagDay(Date.now());
    const opt = (v, label, cur) => `<option value="${v}"${cur === v ? " selected" : ""}>${label}</option>`;
    return `<section class="card"><h2>미리 보기</h2><p class="hint">이런 휴대폰이면 어떻게 될지 서버와 같은 판단기로 봐요. 아무것도 바뀌지 않아요.</p>
      <form id="flag-sim" class="sim-grid" aria-describedby="flag-sim-out">
        <div class="fieldset"><label for="sim-day">날짜</label><input id="sim-day" type="date" data-change="flag-sim" name="day" value="${esc(today)}"></div>
        <div class="fieldset"><label for="sim-app">앱 번호</label><input id="sim-app" type="number" min="1" data-change="flag-sim" name="app" value="${esc(sim.app ?? simApp(r))}"></div>
        <div class="fieldset"><label for="sim-agreed">동의한 처리방침</label><input id="sim-agreed" data-change="flag-sim" name="agreed" value="${esc(sim.agreed ?? "")}" placeholder="예: 3.1"></div>
        <div class="fieldset"><label for="sim-installed">설치한 날</label><input id="sim-installed" type="date" data-change="flag-sim" name="installed" value="${esc(sim.installed ?? "")}"></div>
        ${Object.entries(L.CONSENT_NAMES).map(([k, label], i) => `<div class="fieldset"><label for="sim-c${i}">${esc(label)}</label><select id="sim-c${i}" data-change="flag-sim" name="consent:${k}">
          ${opt("", "아직 안 물음", sim[`consent:${k}`] ?? "")}${opt("yes", "동의", sim[`consent:${k}`] ?? "")}${opt("no", "거절", sim[`consent:${k}`] ?? "")}</select></div>`).join("")}
        <div class="fieldset"><label for="sim-type">근무 유형</label><select id="sim-type" data-change="flag-sim" name="workType">${L.WORK_TYPES.map((w) => opt(w, L.WORK_NAMES[w], sim.workType ?? "SHIFT")).join("")}</select></div>
        <div class="fieldset"><label for="sim-bucket">설치 자리(0~99)</label><input id="sim-bucket" type="number" min="0" max="99" data-change="flag-sim" name="bucket" value="${esc(sim.bucket ?? "50")}"></div>
        <label class="check-row"><input type="checkbox" data-change="flag-sim" name="preview"${sim.preview ? " checked" : ""}>관리자 휴대폰 미리 보기</label>
      </form><div id="flag-sim-out" class="cond-out" aria-live="polite">${flagSimText(r)}</div></section>`;
  }

  function flagSimText(r) {
    const s = S.flagSim ?? {};
    const day = s.day || L.flagDay(Date.now());
    const consents = {};
    for (const k of Object.keys(L.CONSENT_NAMES)) if (s[`consent:${k}`]) consents[k] = s[`consent:${k}`] === "yes";
    const phone = { app: Number(s.app ?? simApp(r)), agreed: s.agreed || null, installed: s.installed || null, consents, workType: s.workType ?? "SHIFT",
      profile: [], bucket: Number(s.bucket ?? 50), admin: Boolean(s.preview), preview: Boolean(s.preview) };
    const ev = L.evalFlag({ conditions: r.catalogConditions, default: r.default, risk: r.risk }, r.override,
      { now: Date.parse(`${day}T12:00:00+09:00`), policy: r.policy, checks: r.checks ?? {}, phone });
    return `<p class="lead"><span class="pill ${ev.on ? "ok" : "warn"}">${ev.on ? "켜짐" : "꺼짐"}</span> ${esc(ev.reason)}</p>
      <ul class="checklist">${ev.conditions.map((c) => `<li class="${c.met === true ? "met" : "unmet"}"><span class="mark" aria-hidden="true">${c.met === true ? "✓" : "○"}</span><span class="c-main">${esc(c.label)}</span></li>`).join("")}</ul>`;
  }

  // ---- dialogs ----

  function openFlagForce(key, force, why = "") {
    const r = S.data.flag?.key === key ? S.data.flag : S.data.flags?.flags.find((x) => x.key === key) ?? S.overview?.flagAlerts?.find((x) => x.key === key) ?? { key, name: key };
    const off = force === "off";
    const tomorrow = L.flagDay(Date.now() + 864e5);
    openDialog(`<form data-form="flag-force" data-key="${esc(key)}" data-force="${force}"><h2>${esc(r.name)}을(를) 강제로 ${off ? "끌까요" : "켤까요"}?</h2>
      <p>${off ? "모든 휴대폰에서 다음 확인 때(하루 안, 앱을 열 때) 꺼지고, 서버도 바로 그 기능을 받지 않아요. 조건이 다 차도 켜지지 않아요."
        : "조건과 상관없이 켜요. 법적 기능은 강제로 켜도 처리방침 조건이 남은 휴대폰에서는 꺼져 있어요."}</p>
      <div class="fieldset"><label for="ff-why">이유(필수, 기록에 남아요)</label><textarea id="ff-why" name="why" maxlength="300" required placeholder="${off ? "예: 결제 화면 오류 신고가 몰려요" : "예: Play 승인을 받았어요"}">${esc(why)}</textarea></div>
      ${off ? `<div class="fieldset"><label for="ff-review">다시 볼 날(선택)</label><input id="ff-review" name="reviewAt" type="date" min="${L.flagDay(Date.now())}">
        <span class="help">그날 목록과 메뉴 숫자로 다시 보라고 알려요. 저절로 켜지지는 않아요.</span></div>` : ""}
      <div class="fieldset"><label for="ff-until">이날 0시에 저절로 풀기(선택)</label><input id="ff-until" name="until" type="date" min="${tomorrow}">
        <span class="help">비우면 풀 때까지 그대로예요. 1년 안으로 골라요.</span></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn ${off ? "danger" : "primary"}" type="submit">강제로 ${off ? "끄기" : "켜기"}</button></div></form>`);
  }

  function openFlagConditions(key) {
    const r = S.data.flag;
    const text = L.conditionText(r.conditions);
    openDialog(`<form data-form="flag-cond" data-key="${esc(key)}"><h2>${esc(r.name)}의 켜지는 조건</h2>
      <p>한 줄에 조건 하나, 모두 채우면 켜져요. 'or'로 이으면 그중 하나면 돼요. 저장하면 휴대폰은 앱에 든 조건 대신 이 조건을 써요.</p>
      <div class="fieldset"><label for="flag-cond">조건</label><textarea id="flag-cond" name="conditions" spellcheck="false" aria-describedby="flag-cond-out">${esc(text)}</textarea>
        <span class="help">예: date >= 2026-11-12 · date >= policy 3.1 · policy 3.1 · consent ads? · profile pay.setup · app 39..45 · workType SHIFT,DAY · rollout 10% · manual play.production · admin</span></div>
      <div class="rollout-pick"><p class="label">단계 출시 (1.38 [ADM3-17])</p><p class="hint">휴대폰 몇 %에 먼저 켤지 누르면 조건에 한 줄로 들어가요(같은 휴대폰은 늘 같은 쪽). 문제 신고가 늘면 숫자 › 판 건강과 사고 화면이 먼저 알려 줘요.</p>
        <div class="chips" role="group" aria-label="단계 출시 비율">${[1, 10, 25, 50, 100].map((n) => `<button type="button" class="chip pick" data-act="flag-rollout" data-key="${n}">${n === 100 ? "모두(비율 빼기)" : `${n}%`}</button>`).join("")}
        <button type="button" class="chip pick" data-act="flag-minapp" aria-expanded="false" aria-controls="flag-minapp-row">이 앱 판 이상만</button></div>
        <div class="fieldset inline-ask" id="flag-minapp-row" hidden><label for="flag-minapp-n">이 앱 판부터 켜기 · 앱 판 번호(versionCode)</label>
          <div class="row-input"><input id="flag-minapp-n" inputmode="numeric" maxlength="6" autocomplete="off" data-enter="flag-minapp-put">
          <button type="button" class="btn small" data-act="flag-minapp-put">조건에 넣기</button></div>
          <span class="help">${r.versionCode ? `지금 나간 판은 ${L.eyo(esc(r.versionCode))}.` : "예: 54"} 넣으면 'app >= 번호' 한 줄이 조건에 들어가요.</span></div></div>
      <div id="flag-cond-out" class="cond-out" aria-live="polite">${flagCondPreview(text)}</div>
      <div class="fieldset"><label for="fc-why">이유(필수)</label><input id="fc-why" name="why" maxlength="300" required></div>
      <div class="actions">${r.override?.conditions ? '<button class="btn ghost" type="submit" name="reset" value="1" formnovalidate>앱 조건으로 되돌리기</button>' : ""}
        <button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`, "wide");
  }

  /** The editor's lines as the checklist will say them, or the first mistake. */
  function flagCondPreview(text) {
    const got = L.parseConditions(text);
    if (got.error) return `<p class="note bad" role="alert">${esc(got.error)}</p>`;
    const unknown = JSON.stringify(got.list).match(/"type":"manual","key":"([^"]+)"/g)?.map((m) => m.slice(23, -1)).filter((k) => !Object.hasOwn(L.MANUAL_NAMES, k)) ?? [];
    if (unknown.length) return `<p class="note bad" role="alert">모르는 확인 이름이에요: ${esc(unknown[0])}. ${Object.keys(L.MANUAL_NAMES).join(", ")} 가운데에서 골라요.</p>`;
    if (!got.list.length) return '<p class="note">조건이 없으면 기본값을 따라요.</p>';
    return `<ol class="lines">${got.list.map((c) => `<li>${esc(L.conditionLabel(c, S.data.flag?.policy ?? []))}</li>`).join("")}</ol>`;
  }

  function openFlagPolicy(version) {
    const p = (S.data.flags?.policy ?? []).find((x) => x.version === version) ?? null;
    openDialog(`<form data-form="flag-policy"><h2>${p ? `처리방침 ${esc(p.version)} 날짜` : "처리방침 판 더하기"}</h2>
      <p>게시일·시행일을 바꾸면 그 판에 기대는 기능(날짜 조건·동의)도 같이 움직여요. 휴대폰은 다음 확인 때 따라와요.</p>
      <div class="fieldset"><label for="fp-version">판</label><input id="fp-version" name="version" required pattern="\\d{1,2}\\.\\d{1,2}" value="${esc(p?.version ?? "")}"${p ? " readonly" : ""} placeholder="예: 3.3"></div>
      <div class="fieldset"><label for="fp-posted">게시일</label><input id="fp-posted" name="posted" type="date" required value="${esc(p?.posted ?? "")}"></div>
      <div class="fieldset"><label for="fp-effective">시행일</label><input id="fp-effective" name="effective" type="date" required value="${esc(p?.effective ?? "")}"></div>
      <div class="fieldset"><label for="fp-held">동의하지 않은 휴대폰에도 시행하는 날(선택)</label><input id="fp-held" name="heldUntil" type="date" value="${esc(p?.heldUntil ?? "")}"></div>
      <label class="check-row"><input type="checkbox" name="unfavourable"${p?.unfavourable ? " checked" : ""}>이용자에게 불리한 변경(30일 전에 알려요)</label>
      <div data-policy-confirm hidden><p class="note warn" role="status" data-policy-why></p>
        <label class="check-row"><input type="checkbox" name="confirm">알고 이대로 저장해요</label></div>
      <div class="fieldset"><label for="fp-why">이유(필수)</label><input id="fp-why" name="why" maxlength="300" required></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
  }

  /**
   * 2.0.0 [RVQ-15] 검토 SRV-17: 날짜를 오늘보다 앞이나 알림 기간 안으로 옮길 때만 까닭 한 줄과 확인 칸을 보여요(서버와 같은 규칙,
   * admin-lib policyEarly). 그렇지 않으면 칸이 없어 창이 짧아요. [force]면 서버가 409로 물었을 때 규칙과 상관없이 보여요.
   */
  function policyConfirmRow(form, force = false) {
    const row = form.querySelector("[data-policy-confirm]");
    if (!row) return;
    const v = (name) => form.elements[name]?.value ?? "";
    const before = (S.data.flags?.policy ?? []).find((x) => x.version === v("version")) ?? null;
    const early = L.policyEarly(before, { posted: v("posted"), effective: v("effective"), unfavourable: form.elements.unfavourable?.checked }, L.flagDay(Date.now()));
    row.hidden = !early && !force;
    const why = row.querySelector("[data-policy-why]");
    if (why) why.textContent = early ? L.policyEarlyText(early) : "서버가 한 번 더 확인해 달래요.";
    if (row.hidden) form.elements.confirm.checked = false;
  }

  // ---- 근무 유형 [ADMUX-26] ----

  function typesView() {
    const d = S.data.types;
    if (!d) return skeleton(2);
    const shown = d.types.filter((t) => t.on).length;
    const lock = d.reach.off ? null : LOCK.off;
    // 2.0.0 [RVA-24] 검토 HQ-03(조건 1·2·4): 설명 네 줄은 목록 아래 접힌 한 줄로, 목록 위는 '보이는 유형 n/n' 한 줄. 숨긴 유형이 여럿이어도
    // 채운 '보이기'가 줄지어 서지 않게 줄 단추는 모두 테두리 모양.
    return `<p class="meta list-meta">보이는 유형 ${shown}/${d.types.length}</p>
      <ul class="rows">${d.types.map((t) => {
        const last = t.on && shown <= 1;
        const button = t.on ? lockedBtn("숨기기", lock ?? (last ? "마지막 남은 유형은 숨길 수 없어요." : null), `data-act="type-hide" data-type="${t.style}"`)
          : lockedBtn("보이기", lock, `data-act="type-show" data-type="${t.style}"`);
        return `<li class="item"><span class="row-main"><b>${esc(t.label)}</b><small>${esc(t.key)}${t.override?.force === "off" ? ` · ${esc(t.override.by || "관리자")}: ${esc(t.override.why)}` : ""}</small></span>
          <span class="pill ${t.on ? "ok" : "warn"}">${t.on ? "보임" : "숨김"}</span>${button}<a class="btn small ghost" href="${flagLink(t.key)}">자세히</a></li>`;
      }).join("")}</ul>
      ${lock ? `<p class="note">${esc(lock)}</p>` : ""}
      <details class="tip" data-key="types-how"><summary>숨기면 어디서 빠지나요 · 하나는 늘 보여요</summary>
        <p class="hint">숨긴 유형은 첫 질문, 근무 형태 화면, 새 달력 만들기, 직업 찾기, 둘러보기 예시에서만 빠져요. 이미 그 유형을 쓰는 달력·백업 복원·회사 파일은 그대로 열리고, 최고 관리자로 연결한 휴대폰에는 '숨김'으로 보여요. 휴대폰은 하루 안에 따라와요. 숨기기·보이기는 모두 기록에 남아요.</p></details>`;
  }

  // ---- 분석 › 기능별 효과 (1.28 통합) [ADMUX-40][FLAG-30] ----

  /**
   * Each flag's share of phones (stats.more, last 7 days) and problem reports and server errors in the 7 days
   * before and after the server saw it turn on or off: the flag page's numbers for all flags side by side.
   */
  function effectsView() {
    const d = S.data.effects;
    if (!d) return skeleton(3);
    const rows = [...d.flags].sort((a, b) => (b.compare ? Date.parse(b.compare.pivot) : 0) - (a.compare ? Date.parse(a.compare.pivot) : 0)
      || (b.onShare ?? -1) - (a.onShare ?? -1));
    const up = (a, b) => (b > a ? ' <span class="pill warn">늘었어요</span>' : "");
    const line = (r) => {
      const c = r.compare;
      const share = r.onShare !== null && r.onShare !== undefined ? ` · 휴대폰 ${r.onShare}%에서 켜짐` : "";
      // AU 1.29 [NXT-07]: the same reports split by whether the flag was on for the phone (문의 시각·앱 판으로만).
      const sp = c?.split;
      const split = sp ? ` · 켜진 휴대폰 ${sp.on}${sp.maybe ? `(+휴대폰마다 다름 ${sp.maybe})` : ""} / 꺼진 휴대폰 ${sp.off}${sp.unknown + sp.unseen ? ` / 모름 ${sp.unknown + sp.unseen}` : ""}` : "";
      const nums = c ? `<span class="preview">${esc(moment(c.pivot))} 앞뒤 ${c.days}일 · 문제 신고 ${c.bugsBefore} → ${c.bugsAfter}${up(c.bugsBefore, c.bugsAfter)}${split} · 서버 오류 ${c.errorsBefore} → ${c.errorsAfter}${up(c.errorsBefore, c.errorsAfter)}</span>` : "";
      const inner = `<span class="row-main"><b>${esc(r.name)}</b><small>${esc(r.key)}${share}</small>${nums}</span><span class="row-side pills">${statePill(r)}${riskPill(r)}</span>`;
      return `<li>${d.flagsReach ? `<a class="row" href="${flagLink(r.key)}">${inner}${icon(P.chevron)}</a>` : `<div class="row">${inner}</div>`}</li>`;
    };
    const measured = rows.filter((r) => r.compare);
    // 2.0.0 [RVA-43] 검토 ADMX-22·T24-10: 볼 숫자가 하나도 없는 기능(켜짐·꺼짐을 본 적 없고 켜진 비율도 모름)은 줄마다 같은 문장을
    // 되풀이하지 않고 접힌 한 줄 '아직 볼 숫자가 없는 기능 N개'로(380에서 쪽 5,388px). 켜진 비율만 있는 기능은 줄로 남겨요.
    const rest = rows.filter((r) => !r.compare && r.onShare !== null && r.onShare !== undefined);
    const blank = rows.filter((r) => !r.compare && (r.onShare === null || r.onShare === undefined));
    const blankFold = blank.length ? `<details class="card fx-blank" data-key="fx-blank"${S.opened.get("fx-blank") ? " open" : ""}><summary><span class="lf-t"><b>아직 볼 숫자가 없는 기능 ${blank.length}개</b>
        <small>서버가 켜짐·꺼짐을 본 적 없고, 켜진 휴대폰 수도 아직 몰라요</small></span></summary>
        <ul class="fx-names">${blank.map((r) => { const t = `<b>${esc(r.name)}</b><small class="mono">${esc(r.key)}</small>`; return `<li>${d.flagsReach ? `<a href="${flagLink(r.key)}">${t}</a>` : `<span>${t}</span>`}</li>`; }).join("")}</ul></details>` : "";
    // 2.0.0 [RVA-43] 설명은 펴는 한 줄로(조건 4).
    return `<details class="tip" data-key="fx-how"${S.opened.get("fx-how") ? " open" : ""}><summary>기능마다 켜진 비율과 켜고 끈 앞뒤 7일의 문제 신고·서버 오류</summary><p class="hint">켜진 휴대폰 비율은 지난 7일 '사용 통계 더 보내기'를 보낸 휴대폰${d.statsPhones ? ` ${d.statsPhones}대` : ""} 기준이에요(휴대폰 구별 없음).
        서버가 켜짐·꺼짐을 본 때 앞뒤 7일의 문제 신고·서버 오류를 나란히 봐요. 문제 신고는 보낸 시각과 앱 판으로 그 기능이 켜진 휴대폰·꺼진 휴대폰으로도 나눠요(동의·근무 유형처럼 휴대폰만 아는 조건은 '휴대폰마다 다름').${d.flagsReach ? " 줄을 누르면 그 기능의 자세한 기록이 열려요." : ""}</p></details>
      ${d.statsPhones ? "" : '<p class="note">아직 켜진 기능 목록을 보낸 휴대폰이 없어요. 처리방침 3.2가 시행되고 동의한 휴대폰부터 와요.</p>'}
      ${measured.length ? `<h2 class="day-head">켜짐·꺼짐을 본 기능</h2><ul class="rows">${measured.map(line).join("")}</ul>` : ""}
      ${rest.length ? `<h2 class="day-head">켜진 비율만 있는 기능</h2><ul class="rows">${rest.map(line).join("")}</ul>` : ""}${blankFold}`;
  }

  const FLAG_ACTIONS = {
    "flag-show": ({ key }) => {
      S.flagShow = key;
      syncUrl();
      render({ keepScroll: true });
    },
    "flag-view": ({ key }) => {
      S.flagView = key;
      syncUrl();
      render({ keepScroll: true });
      load();
    },
    "flag-force": ({ key, force, why }) => openFlagForce(key, force, why ?? ""),
    "flag-clear": ({ key }) => {
      const r = S.data.flag;
      confirmBox("강제를 풀까요?", `${r?.name ?? key}이(가) 다시 조건대로 켜지고 꺼져요. 지금 조건이면 ${r?.checklist?.every((c) => c.met !== false) ? "켜져요" : "꺼진 채예요"}.`, "풀기", async () => {
        // 1.36 PC 재검토 ('강제 켬 풀기' 뒤에도 '강제로 켰어요'가 남아 다시 누르게 돼요): the answer is drawn at once and the toast
        // says the state it is in now; the page is read again past any kept copy.
        const d = await act(() => api(`/flags/${encodeURIComponent(key)}/force`, "POST", { force: null }),
          (r) => `강제를 풀었어요 · 지금 ${(FLAG_STATE[r?.state] ?? ["", "조건대로"])[1]}`);
        if (d) S.data.flag = d;
        render({ keepScroll: true });
        etags.clear();
        load({ quiet: true });
      }, "primary");
    },
    "flag-cond": ({ key }) => openFlagConditions(key),
    // 1.38 [ADM3-01] 패스키로 들어오기.
    "passkey-login": async () => {
      S.error = "";
      try {
        if (await V.passkeyAssert()) await enter();
      } catch (e) {
        S.error = e.message === "locked" ? "여러 번 틀려서 1시간 동안 잠겼어요. 다른 기기에 최고 관리자로 로그인해 있으면 관리 › 내 계정 › 로그인에서 바로 풀 수 있어요."
          : e.message === "suspended" ? "사용이 멈춘 계정이에요. 최고 관리자에게 물어봐 주세요."
          // 2.0.0 [RVA-14] 검토 ADM-27·U23-13: 창을 닫은 때와 이 기기에 패스키가 없는 때는 브라우저가 같은 오류라, 말없이 지나가지 않고 한 줄로 알려요.
          : e.name === "NotAllowedError" || e.name === "AbortError" ? "패스키 창이 닫혔어요. 이 기기에 패스키가 없으면 비밀번호로 들어와 주세요."
          : "패스키로 들어오지 못했어요. 비밀번호로 들어와 내 계정에서 패스키를 다시 만들어 주세요.";
        render();
      }
    },
    // 2.0.0 [RVA-19] 검토 ADM-15·U23-07: 허브의 모든 화면을 한눈에(자주 여는 것 먼저). 가로로 밀지 않아도 돼요.
    "hub-all": () => {
      const here = L.hubOf(S.route.name);
      const hub = hubs().find((h) => h.key === here);
      if (!hub) return;
      const v = visits();
      const often = [...hub.tabs].filter((t) => v[t] > 1).sort((a, b) => v[b] - v[a]).slice(0, 3);
      const cell = (t) => `<a class="chip pick" href="${href(t)}" data-act="hub-go" data-tab="${t}"${t === S.route.name ? ' aria-current="page"' : ""}>
        ${esc(tabName(t))}<i class="badge" data-badge="tab:${t}"></i></a>`;
      openDialog(`<div class="box"><h2>${esc(hub.label)}의 모든 화면</h2>
        ${often.length ? `<p class="label">자주 여는 화면</p><div class="chips">${often.map(cell).join("")}</div><p class="label">모두</p>` : ""}
        <div class="chips">${hub.tabs.map(cell).join("")}</div>
        <div class="actions"><button class="btn" type="button" data-act="close">닫기</button></div></div>`);
      updateBadges();
    },
    "hub-go": ({ tab }) => goFromDialog(href(tab)),
    // 1.38 [ADM3-17] 단계 출시: 'rollout N%' 한 줄을 넣거나 바꾸고(100이면 빼요), '이 앱 판 이상만'은 'app >= 번호' 한 줄.
    "flag-rollout": ({ key }) => {
      const box = document.getElementById("flag-cond");
      if (!box) return;
      const lines = box.value.split("\n").filter((l) => l.trim() && !/^\s*(?:rollout|비율)\s+\d+/i.test(l));
      if (Number(key) < 100) lines.push(`rollout ${Number(key)}%`);
      box.value = lines.join("\n");
      flagInput(box);
    },
    // 2.0.0 [RVA-02] 검토 ADM-13: 기본 입력 창 대신 조건 창 안의 한 줄 칸. 새 창을 띄우면 적던 조건·이유가 사라지니(조건 10) 같은 창에서 펴요.
    "flag-minapp": (_, el) => {
      const row = document.getElementById("flag-minapp-row");
      if (!row) return;
      row.hidden = !row.hidden;
      el?.setAttribute("aria-expanded", String(!row.hidden));
      if (!row.hidden) document.getElementById("flag-minapp-n")?.focus();
    },
    "flag-minapp-put": () => {
      const box = document.getElementById("flag-cond");
      const input = document.getElementById("flag-minapp-n");
      if (!box || !input) return;
      const n = input.value.trim();
      if (!/^\d{1,6}$/.test(n)) return fieldError(input, "숫자만 적어 주세요(예: 54)");
      const lines = box.value.split("\n").filter((l) => l.trim() && !/^\s*(?:app|앱)\s*>=\s*\d+/i.test(l));
      lines.push(`app >= ${n}`);
      box.value = lines.join("\n");
      flagInput(box);
      input.value = "";
      document.getElementById("flag-minapp-row").hidden = true;
      document.querySelector('[data-act="flag-minapp"]')?.setAttribute("aria-expanded", "false");
      box.focus();
    },
    "flag-range": ({ key }) => openFlagRange(key),
    "flag-check": ({ name, done }) => {
      const label = S.data.flag?.manualNames?.[name] ?? name;
      if (done) {
        openDialog(`<form data-form="flag-check" data-name="${esc(name)}"><h2>${esc(label)}, 확인했나요?</h2>
          <p>확인하면 이 조건을 기다리는 기능이 다른 조건을 채운 대로 바로 켜질 수 있어요. 누가 언제 확인했는지 남아요.</p>
          <div class="fieldset"><label for="fk-note">메모(선택)</label><input id="fk-note" name="note" maxlength="300" placeholder="예: Play Console 출시 개요에서 봄"></div>
          <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">확인했어요</button></div></form>`);
        return;
      }
      confirmBox(`${label} 확인을 취소할까요?`, "이 조건을 기다리는 기능이 다시 대기로 돌아가요.", "확인 취소", async () => {
        await act(() => api(`/checks/${encodeURIComponent(name)}`, "POST", { done: false }), "확인을 취소했어요");
        load();
      });
    },
    "flag-policy": ({ version }) => openFlagPolicy(version ?? null),
    "type-show": async ({ type }) => {
      const d = await act(() => api("/types", "POST", { type, show: true }), "다시 보여요. 휴대폰은 하루 안에 따라와요");
      if (d) S.data.types = d;
      render({ keepScroll: true });
    },
    "type-hide": ({ type }) => openDialog(`<form data-form="type-hide" data-type="${esc(type)}"><h2>${esc(L.WORK_NAMES[type] ?? type)} 유형을 숨길까요?</h2>
      <p>새로 고르는 곳에서만 빠지고, 이미 쓰는 달력은 그대로예요.</p>
      <div class="fieldset"><label for="th-why">이유(필수)</label><input id="th-why" name="why" maxlength="300" required placeholder="예: 화면을 다듬는 중이에요"></div>
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn danger" type="submit">숨기기</button></div></form>`),
  };

  const FLAG_FORMS = {
    "flag-force": async (form) => {
      const data = new FormData(form);
      const { key, force } = form.dataset;
      const body = { force, why: String(data.get("why") ?? "").trim(), until: data.get("until") || null, ...(force === "off" ? { reviewAt: data.get("reviewAt") || null } : {}) };
      const d = await act(() => api(`/flags/${encodeURIComponent(key)}/force`, "POST", body), force === "off" ? "강제로 껐어요. 휴대폰은 하루 안에 따라와요" : "강제로 켰어요");
      if (!d) return;
      dialog.close();
      if (S.route.name === "flags") S.data.flag = d;
      render({ keepScroll: true });
      load({ quiet: true });
    },
    "flag-cond": async (form, submitter) => {
      const reset = submitter?.name === "reset";
      const why = String(new FormData(form).get("why") ?? "").trim() || (reset ? "앱에 든 조건으로 되돌림" : "");
      let conditions = null;
      if (!reset) {
        const got = L.parseConditions(form.elements.conditions.value);
        if (got.error) return toast(got.error);
        conditions = got.list;
      }
      const d = await act(() => api(`/flags/${encodeURIComponent(form.dataset.key)}/conditions`, "POST", { conditions, why }), reset ? "앱 조건으로 되돌렸어요" : "조건을 바꿨어요");
      if (!d) return;
      dialog.close();
      S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-range": async (form, submitter) => {
      const reset = submitter?.name === "reset";
      const data = new FormData(form);
      const num = (k) => (reset || !String(data.get(k) ?? "").trim() ? null : Number(data.get(k)));
      const why = String(data.get("why") ?? "").trim() || (reset ? "앱 판 범위를 없앰" : "");
      const d = await act(() => api(`/flags/${encodeURIComponent(form.dataset.key)}/range`, "POST", { minApp: num("minApp"), maxApp: num("maxApp"), why }),
        reset ? "앱 판 범위를 없앴어요" : "앱 판 범위를 바꿨어요. 휴대폰은 하루 안에 따라와요");
      if (!d) return;
      dialog.close();
      S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-check": async (form) => {
      const r = await act(() => api(`/checks/${encodeURIComponent(form.dataset.name)}`, "POST", { done: true, note: String(new FormData(form).get("note") ?? "").trim() }), "확인했어요");
      if (!r) return;
      dialog.close();
      load();
    },
    "flag-thanks": async (form) => {
      const d = await act(() => api("/flags/billing/thanks", "POST", { thanksMonths: Number(new FormData(form).get("thanks")) }), "저장했어요");
      if (d) S.data.flag = d;
      render({ keepScroll: true });
    },
    "flag-policy": async (form) => {
      const data = new FormData(form);
      const body = { version: data.get("version"), posted: data.get("posted"), effective: data.get("effective"), heldUntil: data.get("heldUntil") || null,
        unfavourable: data.get("unfavourable") === "on", why: String(data.get("why") ?? "").trim(), confirm: data.get("confirm") === "on" };
      let code = null;
      const d = await act(() => api("/policy", "POST", body).catch((e) => { code = e.message; throw e; }), "처리방침 일정을 고쳤어요");
      if (!d) return code === "policy_confirm" ? policyConfirmRow(form, true) : undefined;
      dialog.close();
      S.data.flags = d;
      render({ keepScroll: true });
    },
    "type-hide": async (form) => {
      const d = await act(() => api("/types", "POST", { type: form.dataset.type, show: false, why: String(new FormData(form).get("why") ?? "").trim() }), "숨겼어요. 휴대폰은 하루 안에 따라와요");
      if (!d) return;
      dialog.close();
      S.data.types = d;
      render({ keepScroll: true });
    },
  };

  const FLAG_CHANGES = {
    "flag-sim": () => {
      const form = document.getElementById("flag-sim");
      const out = document.getElementById("flag-sim-out");
      if (!form || !out || !S.data.flag) return;
      const data = new FormData(form);
      S.flagSim = Object.fromEntries([...data.entries()].map(([k, v]) => [k, String(v)]));
      S.flagSim.preview = data.get("preview") === "on";
      out.innerHTML = flagSimText(S.data.flag);
    },
  };

  /** Typing in the condition editor: what it will say, or the first mistake, as the admin types. */
  function flagInput(t) {
    if (t.id !== "flag-cond") return;
    const out = document.getElementById("flag-cond-out");
    if (out) out.innerHTML = flagCondPreview(t.value);
  }

  return { flagsView, flagView, flagHeading, typesView, effectsView, flagInput, policyConfirmRow, actions: FLAG_ACTIONS, forms: FLAG_FORMS, changeHandlers: FLAG_CHANGES };
}
