// 1.39 [OBS2-01~05] 관측(사용자 11-08 '사용자 유입 이탈 등 모든 상황을 관측', '통계를 통한 완전한 자가 발전'): 정상도 아래에 전체 사용자(Play·서버),
// 개선 신호(숫자 → 개발사항 → 고친 뒤 확인), 관측 빈틈(답 못 하는 질문·더 모을 데이터)을 더해 두 칸으로(src/observe.js).
// 1.39 [STAT2-01~05] 숫자 › 한눈에 #/glance (사용자 2026-11-08 "통계쪽 혁신 … 정상도 사용자 이탈 회복 진입 등 모든 것을 전반적으로,
// 레이아웃도 디자인 전문가 입장에서 최정상급"). 다른 화면 묶음처럼 글(String(fn))로 브라우저에 가고, 앱이 나눠 주는 것(A)으로 불려요.
// 위에서 아래로 한 번 읽으면 끝나게:
//   1. 정상도: 큰 신호 하나(정상·살펴볼 것·바로 볼 것)와 이유 칩(누르면 그 화면), 그 아래 네 숫자(비정상 종료·ANR·서버 오류·느린 5%)와 기준선.
//   2. 초기 모드: 하루 활성이 30대 아래면 '데이터 부족' 대신 Play가 본 사용자·리뷰·문의를 앞에.
//   3. 사용자 흐름: 진입 → 머묾(1·7·30일 고리) → 이탈 어림, 옆에 회복, 아래 날마다 활성.
//   4. 더 보기: 지표·판 건강·오류 묶음·돈·서버 상태로.
// style 속성 없이(CSP) 막대·고리는 SVG 속성으로 그려요. 폴드 접힘(380)·펼침(840)·웹(1440) 모두 한 줄 흐름이 접혀 내려와요.
export function glanceViews(A) {
  const { S, L, esc, href, skeleton, api, act, load, can, confirmBox } = A;

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


  // ---- 1.39 [OBS2-01~05] 관측: 전체 사용자(Play·서버), 개선 신호, 관측 빈틈 ----

  const PLAY_STATE = { off: "Play 서비스 계정 없음", nobucket: "Play 보고서 저장소 연결 필요", denied: "Play 보고서 권한 필요", unreachable: "Play에 닿지 못했어요" };
  const SOURCE_NAMES = { "utm:bamnat_web": "웹 계산기·달력", "utm:bamnat_share": "받은 근무표 링크", "utm:bamnat_invite": "친구 초대" };
  const sourceName = (s) => SOURCE_NAMES[s] ?? (/search/i.test(s) ? "Play 검색" : /explore|browse/i.test(s) ? "Play 둘러보기"
    : /referr|third/i.test(s) ? "외부 링크" : s.startsWith("utm:") ? `링크 ${s.slice(4)}` : s);
  const md = (x) => (x ? x.slice(5).replace("-", "/") : "—");
  const signed = (n) => (n > 0 ? `+${n}` : String(n));

  /** 전체 사용자 칸 하나: 무엇, 큰 숫자, 풀이, 작은 줄 그림, 출처(어디서 왔고 언제까지). */
  function tile(cls, label, big, note, extra, source) {
    return `<div class="ob-tile ${cls}"><span class="gl-k">${esc(label)}</span><b>${big}</b><small>${note}</small>${extra}<span class="ob-src">${esc(source)}</span></div>`;
  }

  function everyone(o) {
    const p = o.play;
    const rows = p.days ?? [];
    const on = p.state === "ok" || p.stale;
    const sum = (k) => rows.reduce((n, r) => n + (r[k] ?? 0), 0);
    const has = (k) => rows.some((r) => r[k] !== null && r[k] !== undefined);
    const playSrc = on ? `Play 보고서 · ${md(p.lastDay)}까지(1~2일 늦음)${p.stale ? " · 지난 값" : ""}` : PLAY_STATE[p.state] ?? "Play 숫자 없음";
    const need = o.gaps.find((g) => g.key.startsWith("play-"));
    const missing = (label, cls) => tile(`${cls} need`, label, "—", esc(need ? need.how : "아직 숫자가 없어요"), "", playSrc);
    const tiles = [];
    // Play 보고서가 아예 없으면 빈 칸 넷 대신 한 줄로: 무엇이 비었고 무엇을 하면 채워지는지.
    if (!on) tiles.push(`<div class="ob-tile need wide"><span class="gl-k">유입 · 설치 − 삭제 · 판별 설치 기기 · 별점</span><b>${need?.state === "wait" ? "권한 퍼지는 중" : "연결 필요"}</b>
      <small>${esc(need ? `${need.title}. ${need.how}` : "Play 보고서를 아직 받지 못했어요.")}</small><span class="ob-src">${esc(playSrc)}</span></div>`);
    // 유입: 스토어 방문 → 설치와 출처.
    if (on && has("visitors")) {
      const vis = sum("visitors");
      const acq = sum("acq");
      const conv = vis ? Math.round((acq / vis) * 1000) / 10 : null;
      const top = (p.sources ?? []).slice(0, 4);
      const src = top.length ? `<ul class="ob-sources">${top.map((x) => `<li><span>${esc(sourceName(x.source))}</span><b>${x.acq}</b><small>방문 ${x.visitors}</small></li>`).join("")}</ul>` : "";
      tiles.push(tile("in", "유입 · 스토어 방문 → 설치", `${acq}<small>대</small>`, `방문 ${vis} · 전환 ${conv === null ? "—" : `${conv}%`} · 28일`,
        L.spark(rows.map((r) => r.acq), { name: "스토어 설치" }) + src, playSrc));
    } else if (on) tiles.push(missing("유입 · 스토어 방문 → 설치", "in"));
    // 설치·삭제: 늘고 있는지 줄고 있는지.
    if (on && has("installs")) {
      const ins = sum("installs");
      const uns = sum("uninstalls");
      const base = [...rows].reverse().find((r) => r.base !== null && r.base !== undefined)?.base ?? null;
      tiles.push(tile(`out ${ins - uns < 0 ? "minus" : "plus"}`, "설치 − 삭제", `${signed(ins - uns)}<small>대</small>`,
        `설치 ${ins} · 삭제 ${uns}${base === null ? "" : ` · 지금 설치된 기기 ${base}`} · 28일`,
        `${L.spark(rows.map((r) => r.uninstalls), { name: "하루 삭제" })}<small class="ob-cap">선은 하루 삭제</small>`, playSrc));
    } else if (on) tiles.push(missing("설치 − 삭제", "out"));
    // 하루 사용자: Play가 본 사용자(늦음)와 서버 요청(늦지 않음).
    const pu = o.active.play ?? [];
    const cf = o.active.config ?? [];
    const lastUsers = pu.at(-1)?.n ?? null;
    const lastCfg = cf.at(-1)?.n ?? null;
    tiles.push(tile("keep", "하루 사용자", `${lastUsers === null ? "—" : lastUsers}<small>명</small>`,
      `Play가 본 사용자(Android 진단 켠 휴대폰)${pu.at(-1)?.day ? ` · ${md(pu.at(-1).day)}` : ""}`,
      `${L.spark(pu.map((x) => x.n), { name: "하루 사용자" })}<div class="ob-sub"><span class="gl-k">앱 → 서버 요청</span><b>${lastCfg === null ? "—" : lastCfg}</b><small>어제 · 휴대폰 구별 없이 센 요청 수(14일)</small>${L.spark(cf.map((x) => x.n), { name: "서버 요청" })}</div>`,
      o.quality.vitals?.error ? "Play vitals 권한 필요" : "Play vitals · 서버"));
    // 판: 어느 판에 몇 대가 있는지.
    const v = p.versions;
    if (on && v?.rows?.length) {
      const total = v.rows.reduce((n, r) => n + r.n, 0) || 1;
      const bars = v.rows.slice(0, 4).map((r, i) => {
        const share = Math.round((r.n / total) * 1000) / 10;
        // [OBS2-09] 그 판에서 최근 7일 지운 휴대폰(지운 이유 대신 '어느 판에서').
        return `<li class="${i ? "" : "top"}"><span>${esc(String(r.code))}</span><svg class="gl-meter" viewBox="0 0 100 6" preserveAspectRatio="none" aria-hidden="true"><rect class="gl-track" width="100" height="6" rx="3"/><rect class="gl-fill" width="${Math.max(2, share)}" height="6" rx="3"/></svg><b>${share}%</b>${r.out7 ? `<small>7일 삭제 ${r.out7}</small>` : ""}</li>`;
      }).join("");
      tiles.push(tile("ver", "판별 설치 기기", `${Math.round((v.rows[0].n / total) * 100)}<small>% 최신 판</small>`, `${esc(String(v.rows[0].code))} · ${md(v.day)}`, `<ul class="ob-vers">${bars}</ul>`, playSrc));
    } else if (on) tiles.push(missing("판별 설치 기기", "ver"));
    // 별점.
    const rated = rows.filter((r) => r.rating);
    const total = [...rows].reverse().find((r) => r.total)?.total ?? null;
    if (on && (rated.length || total)) {
      const week = rated.slice(-7);
      const avg = week.length ? Math.round((week.reduce((n, r) => n + r.rating, 0) / week.length) * 10) / 10 : null;
      tiles.push(tile("star", "별점", `${total === null ? "—" : `★ ${Math.round(total * 100) / 100}`}`, `최근 7일 평균 ${avg === null ? "—" : `★ ${avg}`} · 리뷰 ${o.voice.reviews.n}개`, "", playSrc));
    } else tiles.push(tile("star", "별점", o.voice.reviews.stars === null ? "—" : `★ ${o.voice.reviews.stars}`, `받은 리뷰 ${o.voice.reviews.n}개 · 이번 주 ${o.voice.reviews.week}`, "", "스토어 리뷰(답글용으로 받은 것)"));
    // 목소리: 사람이 직접 말해 준 것(문의·리뷰)과 찾다가 못 찾은 말. 숫자로 안 보이는 불편이 여기서 먼저 보여요.
    const vo = o.voice;
    const miss = (vo.misses ?? []).slice(0, 3);
    tiles.push(tile("voice", "목소리", `${vo.inquiries.week}<small>건 문의 · 7일</small>`, `28일 문의 ${vo.inquiries.n} · 이번 주 리뷰 ${vo.reviews.week}`,
      miss.length ? `<ul class="ob-sources">${miss.map((m) => `<li><span>'${esc(m.word)}' 못 찾음</span><b>${m.n}</b><small>${esc(m.placeName ?? "")}</small></li>`).join("")}</ul>` : "",
      "문의·스토어 리뷰·찾았는데 없던 말"));
    return `<section class="card ob-all" aria-labelledby="ob-all-title"><div class="card-head"><h2 id="ob-all-title">전체 사용자</h2><span class="meta">동의와 상관없이 모든 휴대폰 · Play와 서버</span></div>
      <div class="ob-tiles">${tiles.join("")}</div></section>`;
  }

  const SIGNAL_STATE = { new: ["새 신호", "warn"], watch: ["지켜보는 중", "info"], dev: ["개발사항", "ok"], dismiss: ["아님", ""] };

  /** [OBS2-04] 개선 신호: 숫자에서 찾은 문제 → 개발사항 → 고친 뒤 숫자로 확인. */
  function signalsCard(o) {
    const list = o.signals ?? [];
    const open = list.filter((s) => !s.cleared && s.state !== "dismiss");
    const better = list.filter((s) => s.cleared);
    const devOk = can("dev", "edit");
    const row = (s) => {
      const [label, tone] = SIGNAL_STATE[s.state] ?? SIGNAL_STATE.new;
      const btns = s.state === "dev"
        ? `<a class="btn small" href="${href("dev", s.devId)}">개발사항 보기</a>`
        : `${devOk ? `<button class="btn small primary" data-act="obs-signal" data-key="${esc(s.key)}" data-do="dev">개발사항으로</button>` : ""}
           ${s.state === "watch" ? "" : `<button class="btn small" data-act="obs-signal" data-key="${esc(s.key)}" data-do="watch">지켜보기</button>`}
           <button class="btn small ghost" data-act="obs-signal" data-key="${esc(s.key)}" data-do="dismiss">아님</button>`;
      return `<li class="ob-signal ${esc(s.tone)}"><div class="ob-signal-head"><span class="pill ${tone}">${esc(label)}</span>${s.back ? `<span class="pill bad">다시 나타남 ${s.back}</span>` : ""}<a href="${esc(s.to ?? "#/glance")}">${esc(s.title)}</a></div>
        <p>${esc(s.why)}</p><p class="ob-metric">지금 <b>${esc(String(s.now ?? s.value ?? "—"))}${esc(s.unit ?? "")}</b> · 기준 ${esc(String(s.base ?? "—"))}${esc(s.unit ?? "")}${s.state === "dev" ? ` · 고치면 사흘 뒤 '좋아짐'으로 옮겨요` : ""}</p>
        <div class="actions start">${btns}</div></li>`;
    };
    const done = better.length ? `<details class="ob-better" data-key="obs:better"${S.opened.get("obs:better") ? " open" : ""}><summary>좋아진 신호 ${better.length}개</summary><ul>${better.map((s) =>
      `<li><b>${esc(s.title)}</b><small>${s.devId ? `개발사항 ${esc(s.devId)} 뒤 ` : ""}${esc(String(s.value ?? "—"))}${esc(s.unit ?? "")} → ${esc(String(s.after ?? "—"))}${esc(s.unit ?? "")}</small></li>`).join("")}</ul></details>` : "";
    return `<section class="card ob-signals" aria-labelledby="ob-sig-title"><div class="card-head"><h2 id="ob-sig-title">개선 신호</h2>${open.length ? `<span class="pill warn">${open.length}</span>` : ""}</div>
      <p class="hint">숫자에서 찾은 문제예요. 개발사항으로 넘기면 운영 장부·자동 순환으로 이어지고, 고친 뒤 그 숫자가 돌아오면 '좋아진 신호'로 옮겨요.</p>
      ${open.length ? `<ul class="ob-signal-list">${open.map(row).join("")}</ul>` : `<p class="empty-line">지금 찾은 문제가 없어요. 매시 ${o.found?.length ?? 0}가지 숫자를 기준과 견줘요${o.play.state === "ok" ? "" : "(Play 보고서가 연결되면 늘어요)"}.</p>`}
      ${done}</section>`;
  }

  /** [OBS2-05] 관측 빈틈: 아직 답하지 못하는 질문과 그 답에 필요한 것. */
  /**
   * 관측 빈틈 [OBS2-05][OBS2-09]: 할 일만 펼쳐 보이고, 기다리면 저절로 풀리는 것(권한 퍼짐·3.12 시행·1.39 출시)과 사용자가 이미 정한 것은
   * 한 줄로 접어요(사용자 2026-11-08 '이미 해결된 것도 보이네').
   */
  function gapsCard(o) {
    const g = o.gaps ?? [];
    if (!g.length) return "";
    const todo = g.filter((x) => (x.state ?? "todo") === "todo");
    const rest = g.filter((x) => (x.state ?? "todo") !== "todo");
    const item = (x) => `<li class="${x.state === "decided" ? "policy decided" : x.state === "wait" ? "wait" : x.policy ? "policy" : ""}"><b>${esc(x.title)}</b><small>필요: ${esc(x.need)}</small><small>${esc(x.how)}</small>${
      x.state === "decided" ? '<span class="pill">사용자 결정</span>' : x.state === "wait" ? '<span class="pill">기다리는 중</span>' : x.policy ? '<span class="pill">처리방침 · 사용자 결정</span>' : ""}${
      x.key === "play-denied" && (can("metrics", "edit") || can("dev", "edit")) ? '<div class="actions start"><button class="btn small" data-act="obs-refresh">지금 다시 확인</button></div>' : ""}</li>`;
    const waits = rest.filter((x) => x.state === "wait").length;
    const decided = rest.length - waits;
    const restLine = [waits ? `기다리면 풀리는 것 ${waits}` : "", decided ? `정한 것 ${decided}` : ""].filter(Boolean).join(" · ");
    const folded = rest.length ? `<details class="ob-gap-rest" data-key="obs:gaprest"${S.opened.get("obs:gaprest") ? " open" : ""}><summary>${esc(restLine)}</summary><ul class="ob-gap-list">${rest.map(item).join("")}</ul></details>` : "";
    return `<section class="card ob-gaps" aria-labelledby="ob-gap-title"><div class="card-head"><h2 id="ob-gap-title">관측 빈틈</h2>${todo.length ? `<span class="pill warn">할 일 ${todo.length}</span>` : '<span class="meta">할 일 없음</span>'}</div>
      <p class="hint">지금 숫자로 답하지 못하는 질문이에요. 할 일만 펼쳐 두고, 기다리면 저절로 풀리는 것과 이미 정한 것은 접어 둬요.</p>
      ${todo.length ? `<ul class="ob-gap-list">${todo.map(item).join("")}</ul>` : '<p class="empty-line">지금 할 일은 없어요.</p>'}${folded}</section>`;
  }

  /**
   * 1.39 [OBS2-08] 유형별 흐름: 일하는 방식마다 처음 연 휴대폰과 1·7·30일째 머묾(처리방침 3.12, 깃발 stats.kind). 주마다 머묾 표와 같은 색 구간이고,
   * 좁은 폭에서는 줄마다 카드로 바뀌어요.
   */
  function kindsCard(o) {
    const k = o.kinds;
    if (!k?.rows?.length) return "";
    const heat = (v) => (v === null || v === undefined ? "none" : v >= 60 ? "h4" : v >= 40 ? "h3" : v >= 20 ? "h2" : v > 0 ? "h1" : "h0");
    const cell = (label, x) => `<td data-label="${label}" class="${heat(x?.rate)}">${esc(num(x?.rate, "%"))}${x?.base ? `<small>${x.kept}/${x.base}</small>` : ""}</td>`;
    const row = (r, all = false) => `<tr${all ? ' class="ob-all-row"' : ""}><th scope="row">${esc(r.name)}</th><td data-label="진입">${r.new}대</td>${cell("1일째", r.d1)}${cell("7일째", r.d7)}${cell("30일째", r.d30)}</tr>`;
    return `<section class="card ob-kinds" aria-labelledby="ob-kinds-title"><div class="card-head"><h2 id="ob-kinds-title">유형별 흐름</h2><span class="meta">최근 61일 · 사용 통계 켠 휴대폰</span></div>
      <p class="hint">일하는 방식마다 처음 연 휴대폰이 며칠째 다시 열었는지예요. 한 유형만 낮으면 그 유형의 첫 설정·첫 주를 먼저 봐요.</p>
      <table class="gl-cohort ob-kind-table"><thead><tr><th scope="col">유형</th><th scope="col">진입</th><th scope="col">1일째</th><th scope="col">7일째</th><th scope="col">30일째</th></tr></thead>
      <tbody>${k.rows.map((r) => row(r)).join("")}${row(k.all, true)}</tbody></table></section>`;
  }

  /**
   * 1.39 [OBS2-08] 기능과 머묾: 그날 그 기능을 쓴 휴대폰이 7일 안에 다시 온 비율을, 그날 연 휴대폰 전체(기준)와 견줘요(처리방침 3.12, 깃발 stats.keep).
   * 높은 것부터. 같이 간다고 그 기능 덕분이라는 뜻은 아니어서 그렇게 적어 둬요.
   */
  function keepCard(o) {
    const k = o.keep;
    if (!k?.rows?.length) return "";
    const day = (x) => (x ? x.slice(5).replace("-", "/") : "");
    const lift = (v) => (v === null || v === undefined ? '<td data-label="기준과" class="none">–</td>'
      : `<td data-label="기준과" class="${v > 0 ? "up" : v < 0 ? "down" : ""}">${v > 0 ? "+" : ""}${esc(`${v}%p`)}</td>`);
    return `<section class="card ob-keep" aria-labelledby="ob-keep-title"><div class="card-head"><h2 id="ob-keep-title">기능과 머묾</h2><span class="meta">${esc(`${day(k.from)} ~ ${day(k.to)}에 쓴 날`)}</span></div>
      <p class="hint">그 기능을 쓴 휴대폰이 7일 안에 다시 온 비율이에요. 기준은 그날 연 휴대폰 전체 <b>${esc(num(k.base?.rate7, "%"))}</b>. 함께 높다고 그 기능 덕분이라는 뜻은 아니에요.</p>
      <table class="gl-cohort ob-keep-table"><thead><tr><th scope="col">기능</th><th scope="col">쓴 휴대폰</th><th scope="col">7일 안에</th><th scope="col">기준과</th><th scope="col">30일 안에</th></tr></thead>
      <tbody>${k.rows.map((r) => `<tr><th scope="row">${esc(r.name)}</th><td data-label="쓴 휴대폰">${r.users}대</td><td data-label="7일 안에">${esc(num(r.rate7, "%"))}</td>${lift(r.lift7)}<td data-label="30일 안에">${esc(num(r.rate30, "%"))}</td></tr>`).join("")}</tbody></table>
      ${k.small ? `<p class="meta">쓴 휴대폰이 ${OBS_KEEP_MIN}대보다 적은 기능 ${k.small}개는 빼고 보여요.</p>` : ""}</section>`;
  }
  const OBS_KEEP_MIN = 20;

  const actions = {
    // [OBS2-09] Play 권한을 막 저장했을 때: 1시간을 기다리지 않고 바로 다시 읽어요.
    "obs-refresh": async () => {
      if (await act(() => api("/observe/refresh", "POST", {}), (r) => (r.state === "ok" ? "Play 보고서를 받았어요" : r.again ? "방금 확인했어요. 잠시 뒤 다시 눌러 주세요" : "아직 권한이 퍼지는 중이에요"))) {
        await load({ quiet: true });
      }
    },
    "obs-signal": ({ key, do: what }) => {
      const go = async () => {
        if (await act(() => api("/observe/signals", "POST", { key, act: what }), what === "dev" ? (r) => `개발사항 ${r.signal.devId}을 만들었어요` : what === "watch" ? "지켜볼게요" : "아님으로 두었어요")) {
          await load({ quiet: true });
        }
      };
      if (what === "dismiss") return confirmBox("이 신호를 '아님'으로 둘까요?", "30일 동안 같은 신호를 다시 띄우지 않아요. 활동 기록에 남아요.", "아님으로", go, "primary");
      return go();
    },
  };

  /**
   * 1.39 [OBS2] 한 장: 정상도 아래를 두 칸으로. 왼쪽(넓은 칸)은 전체 사용자 → 사용 통계 표본 흐름, 오른쪽은 개선 신호 → 초기 모드 →
   * 관측 빈틈. 넓은 화면(1100 이상)에서 반이 비던 것을 채우고, 좁으면 위에서 아래로 그 순서로 내려와요.
   */
  function glanceView() {
    const d = S.data.glance;
    const o = S.data.observe;
    if (!d) return `<div class="gl-hero skeleton"></div>${skeleton(2)}`;
    return `<div class="glance">${hero(d)}<div class="ob-cols"><div class="ob-main">${o ? everyone(o) : skeleton(1)}${flow(d)}${o ? kindsCard(o) + keepCard(o) : ""}</div>
      <div class="ob-side">${o ? signalsCard(o) : ""}${early(d)}${o ? gapsCard(o) : ""}</div></div>${more()}</div>`;
  }

  const loads = { glance: () => [["/glance", "glance"], ["/observe", "observe"]] };
  const has = { glance: () => S.data.glance };
  return { glanceView, glanceLoads: loads, glanceHas: has, actions };
}
