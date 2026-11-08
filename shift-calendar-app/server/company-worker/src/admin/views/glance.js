// 1.39 [STAT2-01~05] 숫자 › 한눈에 #/glance (사용자 2026-11-08 "통계쪽 혁신 … 정상도 사용자 이탈 회복 진입 등 모든 것을 전반적으로,
// 레이아웃도 디자인 전문가 입장에서 최정상급"). 다른 화면 묶음처럼 글(String(fn))로 브라우저에 가고, 앱이 나눠 주는 것(A)으로 불려요.
// 위에서 아래로 한 번 읽으면 끝나게:
//   1. 정상도: 큰 신호 하나(정상·살펴볼 것·바로 볼 것)와 이유 칩(누르면 그 화면), 그 아래 네 숫자(비정상 종료·ANR·서버 오류·느린 5%)와 기준선.
//   2. 초기 모드: 하루 활성이 30대 아래면 '데이터 부족' 대신 Play가 본 사용자·리뷰·문의를 앞에.
//   3. 사용자 흐름: 진입 → 머묾(1·7·30일 고리) → 이탈 어림, 옆에 회복, 아래 날마다 활성.
//   4. 더 보기: 지표·판 건강·오류 묶음·돈·서버 상태로.
// style 속성 없이(CSP) 막대·고리는 SVG 속성으로 그려요. 폴드 접힘(380)·펼침(840)·웹(1440) 모두 한 줄 흐름이 접혀 내려와요.
export function glanceViews(A) {
  const { S, L, esc, href, skeleton } = A;

  const TONE = {
    ok: ["정상이에요", "지금 볼 문제가 없어요."],
    warn: ["살펴볼 것이 있어요", "급하지는 않지만 오늘 안에 한 번 보면 좋아요."],
    bad: ["바로 볼 것이 있어요", "사용자가 겪고 있을 수 있어요. 아래 빨간 칸부터 열어 보세요."],
  };
  const num = (v, unit = "") => (v === null || v === undefined ? "—" : `${v}${unit}`);
  const clock = (ms) => new Date(ms).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });

  /** 기준선이 있는 숫자 하나: 값, 기준, 그 기준까지 얼마나 찼는지 막대(SVG). */
  function gauge(label, value, unit, limit, tone, note, to) {
    const fill = value === null || value === undefined || !limit ? 0 : Math.max(2, Math.min(100, Math.round((value / limit) * 100)));
    const tag = to ? `a href="${to}"` : "div";
    return `<${tag} class="gl-gauge ${tone}"><span class="gl-k">${esc(label)}</span><span class="gl-v">${esc(num(value, unit))}</span>
      <svg class="gl-meter" viewBox="0 0 100 6" preserveAspectRatio="none" aria-hidden="true"><rect class="gl-track" width="100" height="6" rx="3"/>${fill ? `<rect class="gl-fill" width="${fill}" height="6" rx="3"/>` : ""}</svg>
      <span class="gl-d">${esc(note)}</span></${to ? "a" : "div"}>`;
  }

  const toneOf = (v, warn, bad) => (v === null || v === undefined ? "none" : v >= bad ? "bad" : v >= warn ? "warn" : "ok");

  function hero(d) {
    const h = d.health;
    const [title, sub] = TONE[h.tone] ?? TONE.ok;
    const count = h.signals.length;
    const v = h.vitals;
    const sv = h.server;
    const vNote = v?.error === "denied" ? "Play 권한 필요" : v ? (v.crash === null ? "Play 숫자 없음(사용자 적음)" : `기준 1.09% · ${v.day ?? ""}`) : "Play 서비스 계정 없음";
    return `<section class="gl-hero ${h.tone}" aria-labelledby="gl-title">
      <div class="gl-hero-top"><span class="gl-pulse" aria-hidden="true"><span></span></span>
        <div class="gl-hero-text"><p class="gl-eyebrow">앱 정상도 · ${esc(clock(d.at))} 기준</p><h2 id="gl-title">${esc(title)}${count && h.tone !== "ok" ? ` <span class="gl-count">${count}</span>` : ""}</h2>
          <p class="gl-sub">${esc(sub)}</p></div></div>
      ${count ? `<ul class="gl-signals">${h.signals.map((s) => `<li><a class="gl-chip ${s.tone}" href="${esc(s.to)}">${esc(s.text)}</a></li>`).join("")}</ul>` : ""}
      <div class="gl-gauges">
        ${gauge("비정상 종료", v?.crash ?? null, "%", 1.09, toneOf(v?.crash, 0.5, 1.09), vNote, href("health"))}
        ${gauge("ANR", v?.anr ?? null, "%", 0.47, toneOf(v?.anr, 0.2, 0.47), v && !v.error && v.anr !== null ? "기준 0.47%" : vNote, href("health"))}
        ${gauge("서버 오류", sv ? sv.rate : null, "%", 2, toneOf(sv?.rate, 0.5, 2), sv ? `24시간 ${sv.n}번 중 ${sv.err}번` : "24시간 요청 없음", S.me?.owner ? href("errors") : "")}
        ${gauge("느린 5%", sv ? sv.p95 : null, "ms", 3000, toneOf(sv?.p95, 1500, 3000), sv ? `중간 ${sv.p50}ms` : "—", S.me?.owner ? href("server") : "")}
      </div></section>`;
  }

  function early(d) {
    const e = d.early;
    if (!e) return "";
    const r = e.reviews;
    return `<section class="gl-early" aria-label="초기 모드"><div class="gl-early-head"><span class="pill info">초기 모드</span>
        <p>사용 통계를 보내는 휴대폰이 하루 ${e.daily}대라 비율은 아직 흔들려요. 지금은 아래 숫자가 더 믿을 만해요.</p></div>
      <div class="gl-early-grid">
        <div><span class="gl-k">Play가 본 하루 사용자</span><b>${esc(num(e.playUsers, "명"))}</b><small>${e.playDay ? esc(e.playDay) : "Play 숫자 없음"} · 모든 휴대폰</small></div>
        <a href="${href("reviews")}"><span class="gl-k">스토어 리뷰</span><b>${r.stars === null ? "—" : `★ ${r.stars}`}</b><small>${r.n}개 · 이번 주 ${r.week} · 답 기다림 ${r.open}</small></a>
        <a href="${href("inquiries")}"><span class="gl-k">7일 문의</span><b>${e.inquiries}건</b><small>사람이 직접 말해 준 것</small></a>
      </div></section>`;
  }

  /** 좋아짐·나빠짐 한 칩: 최근 7일이 그 전 7일보다 얼마나(%). [up]이 좋은 쪽이에요. */
  function trend(v, up = true) {
    if (v === null || v === undefined) return "";
    const flat = Math.abs(v) < 1;
    const good = flat ? "flat" : (v > 0) === up ? "up" : "down";
    const mark = flat ? "→" : v > 0 ? "▲" : "▼";
    return `<span class="gl-trend ${good}" title="최근 7일 평균, 그 전 7일과 비교">${mark} ${esc(`${Math.abs(v)}%`)}</span>`;
  }

  /** 주마다 머묾 표: 폭이 좁으면(폴드 접힘) 줄마다 카드로 바뀌어요(data-label). 칸 색은 비율 구간. */
  function cohortTable(rows) {
    if (!rows?.some((r) => r.new)) return "";
    const heat = (v) => (v === null || v === undefined ? "none" : v >= 60 ? "h4" : v >= 40 ? "h3" : v >= 20 ? "h2" : v > 0 ? "h1" : "h0");
    const cell = (label, v) => `<td data-label="${label}" class="${heat(v)}">${esc(num(v, "%"))}</td>`;
    const day = (x) => x.slice(5).replace("-", "/");
    return `<div class="gl-cohort-wrap"><div class="gl-active-head"><span class="gl-k">주마다 머묾 · 최근 8주</span><small>그 주에 처음 연 휴대폰이 며칠째 다시 열었는지 · 빈칸은 아직 그날이 안 옴</small></div>
      <table class="gl-cohort"><thead><tr><th scope="col">처음 연 주</th><th scope="col">진입</th><th scope="col">1일째</th><th scope="col">7일째</th><th scope="col">30일째</th></tr></thead>
      <tbody>${rows.map((r) => `<tr><th scope="row">${esc(`${day(r.from)}~${day(r.to)}`)}</th><td data-label="진입">${r.new}대</td>${cell("1일째", r.d1)}${cell("7일째", r.d7)}${cell("30일째", r.d30)}</tr>`).join("")}</tbody></table></div>`;
  }

  /** 머묾 고리: N일째 다시 연 비율(SVG 원의 stroke-dasharray). */
  function ring(label, k) {
    const rate = k?.rate;
    const c = 2 * Math.PI * 16;
    const on = rate === null || rate === undefined ? 0 : (Math.min(100, rate) / 100) * c;
    return `<div class="gl-ring"><svg viewBox="0 0 40 40" aria-hidden="true"><circle class="gl-track" cx="20" cy="20" r="16"/>${on ? `<circle class="gl-fill" cx="20" cy="20" r="16" stroke-dasharray="${on.toFixed(1)} ${c.toFixed(1)}" transform="rotate(-90 20 20)"/>` : ""}</svg>
      <b>${esc(num(rate, "%"))}</b><span>${esc(label)}</span><small>${k?.base ? `${k.kept}/${k.base}대` : "아직 없음"}</small></div>`;
  }

  function flow(d) {
    const f = d.flow;
    const labels = d.days.map((x) => x.slice(5).replace("-", "/"));
    const off = !f.lifeOn ? `<p class="note gl-off">진입·머묾·회복은 처리방침 3.12 시행일부터 모여요(기능 관리 › 일정의 3.12 날짜). 그 전에는 날마다 활성만 보여요.</p>` : "";
    return `<section class="card gl-flow" aria-labelledby="gl-flow-title"><div class="card-head"><h2 id="gl-flow-title">사용자 흐름</h2><span class="meta">${esc(d.from.slice(5))} ~ ${esc(d.to.slice(5))} · 사용 통계 켠 휴대폰</span></div>
      ${off}
      <ol class="gl-steps">
        <li class="gl-step in"><span class="gl-k">진입 ${trend(f.newTrend)}</span><b>${f.totals.new}</b><small>처음 연 휴대폰</small>${L.spark(f.newPhones, { name: "진입" })}</li>
        <li class="gl-step keep"><span class="gl-k">머묾</span><div class="gl-rings">${ring("1일째", f.keep.d1)}${ring("7일째", f.keep.d7)}${ring("30일째", f.keep.d30)}</div></li>
        <li class="gl-step out"><span class="gl-k">이탈 어림</span><b>${esc(num(f.churn, "%"))}</b><small>30일째 다시 열지 않은 비율</small></li>
        <li class="gl-step back"><span class="gl-k">회복</span><b>${f.totals.back7 + f.totals.back30}</b><small>7일 넘게 쉬다 ${f.totals.back7} · 30일 넘게 ${f.totals.back30}</small>${L.spark(f.back, { name: "회복" })}</li>
      </ol>
      <div class="gl-active"><div class="gl-active-head"><span class="gl-k">날마다 활성 ${trend(f.activeTrend)}</span><b>${f.daily}<small>대 · 최근 7일 평균</small></b></div>
        ${L.barChart(f.active, { labels, name: "날마다 활성 휴대폰", width: 800, height: 150 })}</div>
      ${f.lifeOn ? cohortTable(f.cohorts) : ""}
      <p class="meta">${esc(d.note)} Play Console › 통계의 활성 사용자는 모든 휴대폰이라 더 커요.</p></section>`;
  }

  function more() {
    const links = [["metrics", "지표 자세히", "기능·광장·문의·전환"], ["health", "판 건강", "판마다 문제·비정상 종료"], ["errors", "오류 묶음", "서버 500 묶음"],
      ["money", "돈", "구독·이용권·가격"], ["server", "서버 상태", "정기 작업·속도·설정"]].filter(([k]) => (k === "errors" || k === "server" || k === "money" ? S.me?.owner : true));
    return `<nav class="gl-more" aria-label="더 보기">${links.map(([k, t, s]) => `<a href="${href(k)}"><b>${esc(t)}</b><small>${esc(s)}</small></a>`).join("")}</nav>`;
  }

  function glanceView() {
    const d = S.data.glance;
    if (!d) return `<div class="gl-hero skeleton"></div>${skeleton(2)}`;
    return `<div class="glance">${hero(d)}${early(d)}${flow(d)}${more()}</div>`;
  }

  const loads = { glance: () => [["/glance", "glance"]] };
  const has = { glance: () => S.data.glance };
  return { glanceView, glanceLoads: loads, glanceHas: has };
}
