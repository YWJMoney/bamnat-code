// AX 1.30 [ADMX-12] 분석 › 지표 and 출시 › 출시 관문 out of src/admin-app.js. Sent to the browser as its own text
// (String(fn)) and called by the app with what it shares (A); it may use no name from outside itself.
// [ADMX-07] The period sits on top and stays while scrolling; under it the key numbers with how they moved against
// the period before; then the panels in a fixed order (활성 → 관문 → 문의 → 광장 → 사진 → 오류 → 시작 시간 · 원격 ·
// 전환 · 링크), and a new app version's first day is marked on the daily chart.
export function insightViews(A) {
  const { S, L, esc, seg, skeleton, href, icon, P, ago, KIND_NAMES } = A;

  const GATE_STATE = { pass: ["ok", "통과"], fail: ["bad", "못 미침"], few: ["warn", "데이터 부족"], play: ["", "Play Console에서 확인"],
    // [ADMUX-16] The service account is set but Play has not given it 앱 정보 보기 yet.
    denied: ["warn", "Play 권한 필요"] };
  const pct = (v) => (v === null || v === undefined ? "–" : `${v}%`);
  const dayLabels = (d) => (d.dayList ?? []).map((x) => x.slice(5).replace("-", "/"));
  const mins = (m) => (m === null || m === undefined ? "–" : m < 60 ? `${m}분` : m < 48 * 60 ? `${Math.round(m / 6) / 10}시간` : `${Math.round(m / 144) / 10}일`);

  /** One key number: now, and the change against the period before ([bad] up means worse). */
  function kpi(label, now, before, { bad = false, unit = "" } = {}) {
    const c = L.change(now, before);
    const tone = c === null || c === 0 ? "" : (c > 0) === bad ? "bad" : "ok";
    return `<div class="tile kpi"><span class="k">${esc(label)}</span><span class="v">${now ?? 0}${unit}</span>
      <span class="d">${c === null ? (before === 0 && now ? "이전 기간 0" : "이전 기간과 비교 없음") : `<span class="chg ${tone}">${c > 0 ? "▲" : c < 0 ? "▼" : ""}${Math.abs(c)}%</span> 이전 ${before ?? 0}${unit}`}</span></div>`;
  }

  function metricsView() {
    const d = S.data.metrics;
    // [ADMUX-13] 7·28·90일, or two dates (180 days at most, up to yesterday).
    const custom = Boolean(S.metricFrom && S.metricTo);
    const head = `<div class="timebar"><div class="toolbar"><div class="segmented" role="group" aria-label="기간">${[7, 28, 90].map((n) => seg("metric-days", String(n), custom ? "" : String(S.metricDays), `${n}일`)).join("")}</div>
      <label class="sr" for="mt-from">시작 날짜</label><input id="mt-from" type="date" value="${esc(S.metricFrom || d?.from || "")}" data-change="metric-from" aria-label="이날부터">
      <label class="sr" for="mt-to">끝 날짜</label><input id="mt-to" type="date" value="${esc(S.metricTo || d?.to || "")}" data-change="metric-to" aria-label="이날까지">
      ${custom ? '<button class="btn small ghost" data-act="metric-clear">날짜 지우기</button>' : ""}
      ${d ? `<p class="meta">${esc(d.from)} ~ ${esc(d.to)} (${d.days}일) · 이전 ${d.days}일과 견줘요</p>` : ""}</div></div>`;
    if (!d) return head + `<div class="tiles core">${'<div class="tile skeleton"></div>'.repeat(4)}</div>${skeleton(3)}`;
    const cmp = d.compare ?? {};
    const sum = (list) => list.reduce((n, v) => n + v, 0);
    const before = Array.isArray(d.before) && d.before.length === d.series.length ? d.before : null;
    const phoneDays = sum(d.series.map((x) => x.phones));
    const kpis = [
      d.active ? kpi("하루 평균 활성", d.active.daily, before ? Math.round(sum(before) / Math.max(1, before.length)) : null, { unit: "대" }) : "",
      cmp.reports ? kpi("받은 제보", cmp.reports.now, cmp.reports.before) : "",
      cmp.applied ? kpi("규칙 적용", cmp.applied.now, cmp.applied.before) : "",
      cmp.inquiries ? kpi("받은 문의", cmp.inquiries.now, cmp.inquiries.before, { bad: true }) : "",
      cmp.bugs ? kpi("문제 신고", cmp.bugs.now, cmp.bugs.before, { bad: true }) : "",
      cmp.errors ? kpi("서버 오류", cmp.errors.now, cmp.errors.before, { bad: true, unit: "번" }) : "",
    ].filter(Boolean);
    const releases = (d.releases ?? []).filter((r) => r.day);
    const marks = releases.map((r) => ({ i: d.series.findIndex((x) => x.day === r.day), label: `${r.app} 첫날` })).filter((m) => m.i >= 0);
    const change = before && sum(before) ? Math.round(((phoneDays - sum(before)) / sum(before)) * 100) : null;
    const series = `<section class="card wide"><div class="card-head"><h2>날마다 보낸 휴대폰</h2>${change === null ? "" : `<span class="pill ${change >= 0 ? "ok" : "warn"}">이전 ${d.days}일보다 ${change >= 0 ? "+" : ""}${change}%</span>`}</div>
      ${d.series.length ? `${L.barChart(d.series.map((x) => x.phones), { labels: d.series.map((x) => x.day.slice(5).replace("-", "/")), before, name: "날마다 보낸 휴대폰 수", marks })}
        <p class="legend"><span><i class="sw" aria-hidden="true"></i>이 기간</span>${before ? '<span><i class="sw before" aria-hidden="true"></i>이전 기간</span>' : ""}${marks.length ? '<span><i class="sw mark" aria-hidden="true"></i>새 앱 판 첫날</span>' : ""}</p>
        ${releases.length ? `<p class="meta">새 판: ${releases.map((r) => `${esc(r.app)} (${esc(r.day.slice(5).replace("-", "/"))})`).join(" · ")}</p>` : ""}
        <p class="meta">${esc(d.from)} ~ ${esc(d.to)} · 휴대폰-날 ${d.phoneDays}</p>` : '<p class="meta">아직 받은 통계가 없어요.</p>'}</section>`;
    const f = d.funnel;
    const funnel = `<section class="card"><h2>프로 전환</h2>
      <dl class="kv"><dt>프로 화면을 본 휴대폰(날 합)</dt><dd>${f.paywall}</dd><dt>무료 체험 시작</dt><dd>${f.trial}</dd><dt>구독 시작</dt><dd>${f.subscribe}</dd>
        <dt>지금 체험 중(서버 확인)</dt><dd>${f.trials}</dd><dt>지금 유료 구독(서버 확인)</dt><dd>${f.paid}</dd><dt>자동 갱신 끈 구독</dt><dd>${f.ending}</dd></dl>
      <p class="hint">위 셋은 사용 통계, 아래 셋은 Play 구매 확인으로 센 숫자라 서로 다를 수 있어요.</p></section>`;
    const sh = d.shares;
    // [STAT-04] The share server counts views roughly (approximate), so the numbers say 대략.
    const about = (v) => (sh?.approximate !== false ? `대략 ${v}` : v);
    const shares = `<section class="card"><h2>근무표 링크</h2>${sh ? `<dl class="kv"><dt>만든 링크</dt><dd>${about(sh.created)}</dd><dt>페이지 열람</dt><dd>${about(sh.views)}</dd>
        <dt>신고</dt><dd>${sh.reports ?? 0}</dd><dt>내린 링크</dt><dd>${sh.removed ?? 0}</dd></dl><p class="hint">공유 서버가 5분마다 모아 세는 대략의 숫자예요.</p>`
      : '<p class="meta">공유 서버가 연결되지 않았어요. 관리 › 서버 상태를 봐 주세요.</p>'}</section>`;
    const events = `<details class="card events" data-key="metrics:events"${S.opened.get("metrics:events") ? " open" : ""}><summary><h2>기능별 <span class="meta">${d.events.length}개</span></h2></summary>
      <p class="hint">비율은 최근 28일 안에 한 번이라도 한 휴대폰의 비율이에요.</p>
      <ul class="rows">${d.events.map((e) => `<li><div class="row"><span class="row-main"><b>${esc(e.label)}</b><small>${esc(e.event)} · ${e.total}번</small></span>
        <span class="row-side"><b>${pct(e.share)}</b></span></div></li>`).join("")}</ul></details>`;
    return head + (kpis.length ? `<div class="tiles core metric-kpis">${kpis.join("")}</div>` : "")
      + `<div class="grid two">${series}${activeCard(d)}${gatesCard(d)}${inquiryCard(d)}${plazaCard(d)}${photoWrongCard(d) /* AQ 1.29 */}${errorCard(d)}${startupCard(d) /* AO 1.28 */}${remoteCard()}${funnel}${shares}</div>${events}`;
  }

  /** [STAT-03] 출시 관문, on 지표 and (AN 1.28 [ADMUX-32]) on 출시 › 출시 관문. */
  function gatesCard(d) {
    return `<section class="card"><h2>출시 관문</h2>
      <p class="hint">사용 통계 보내기에 동의한 분들만 세요. 하루 평균 30대 미만이면 숫자가 흔들려서 "데이터 부족"으로 보여요. 지금 하루 평균 ${d.daily}대예요.</p>
      <ul class="rows">${d.gates.map((g) => {
        const [tone, label] = GATE_STATE[g.status] ?? ["", g.status];
        return `<li><div class="row"><span class="row-main"><b>${esc(g.gate)} · ${esc(g.name)}</b><small>기준 ${esc(g.rule)}</small></span>
          <span class="row-side"><b>${g.value === null ? "" : pct(g.value)}</b><span class="pill ${tone}">${label}</span></span></div></li>`;
      }).join("")}</ul></section>`;
  }

  /** AN 1.28 [ADMUX-32] 출시 › 출시 관문: the gates over the last 28 days and the remote files, also on 지표. */
  function gatesView() {
    const d = S.data.gates;
    if (!d) return skeleton(2);
    return `<p class="hint">출시 전에 보는 두 가지예요. 기간을 바꾸거나 다른 숫자를 보려면 <a href="${href("metrics")}">분석 › 지표</a>로 가요.</p>
      <div class="grid two">${gatesCard(d)}${remoteCard()}</div>`;
  }

  // AO 1.28 시작 [PERF-18] 앱 시작 시간: what consenting phones (사용 통계 + 처리방침 3.2) sent, per app version.
  const startupSec = (ms) => (ms >= 20000 ? "15초 넘음" : `${Math.round(ms / 100) / 10}초`);
  function startupCard(d) {
    const st = d.startup;
    if (!st) return "";
    const line = (label, part) => (part ? `${label} ${part.phones}대 · 가운데 ${startupSec(part.p50)} · 90% ${startupSec(part.p90)}` : "");
    const rows = st.versions.map((v) => {
      const month = v.cold?.firstMonth;
      const few = !month || month.phones < st.few;
      const small = [line("준비 기다림", v.cold?.container), line("화면까지", v.cold?.activity), line("다시 열기 첫 달", v.warm?.firstMonth)].filter(Boolean).join(" · ");
      return `<li><div class="row"><span class="row-main"><b>${esc(v.app)}</b><small>${esc(small || "새로 열기 값 없음")}</small></span>
        <span class="row-side"><b>${month ? `${startupSec(month.p50)} · 90% ${startupSec(month.p90)}` : "–"}</b>${few ? '<span class="pill">데이터 부족</span>' : ""}<span class="meta">${month ? `${month.phones}대` : ""}</span></span></div></li>`;
    }).join("");
    return `<section class="card" id="startup-card"><h2>앱 시작 시간</h2>
      <p class="hint">새로 열었을 때 달력의 첫 달이 보이기까지 걸린 시간이에요(가운데 값 · 10대 중 9대가 그 안). 사용 통계와 처리방침 3.2에 동의한 휴대폰만, 휴대폰을 구별하지 않고 구간으로만 받아요. 판마다 ${st.few}대 미만이면 숫자가 흔들려요. Play Console › Android vitals의 시작 시간과 같이 보세요.</p>
      ${rows ? `<ul class="rows">${rows}</ul>` : '<p class="meta">아직 받은 시작 시간이 없어요. 처리방침 3.2가 시행되고 동의한 휴대폰부터 와요.</p>'}</section>`;
  }
  // AO 1.28 끝

  // AQ 1.29 시작 [PHOTO-10] 사진이 달라요: how often people said a photo was read wrong, per day and per screen, with the reasons.
  function photoWrongCard(d) {
    const pw = d.photoWrong;
    if (!pw) return "";
    const reasons = pw.reasons ?? {};
    const rows = pw.screens.map((s) => {
      const why = Object.entries(s.reasons).sort((a, b) => b[1] - a[1]).map(([k, n]) => `${reasons[k] ?? k} ${n}`).join(" · ");
      return `<li><div class="row"><span class="row-main"><b>${esc(s.label)}</b><small>${esc(why)}</small></span>
        <span class="row-side"><b>${s.total}번</b></span></div></li>`;
    }).join("");
    const chart = pw.days.length > 1
      ? L.barChart(pw.days.map((x) => x.total), { labels: pw.days.map((x) => x.day.slice(5).replace("-", "/")), name: "날마다 사진이 달라요 횟수" }) : "";
    return `<section class="card" id="photo-wrong-card"><div class="card-head"><h2>사진이 달라요</h2><span class="pill">${pw.total}번</span></div>
      <p class="hint">사진을 읽은 결과가 사진과 다르다고 누른 횟수예요. 날짜·화면·고른 이유·앱 버전만 받고 사진과 글은 받지 않아요. 처리방침 3.3이 시행된 뒤부터 와요.</p>
      ${pw.total ? `${chart}<ul class="rows">${rows}</ul>${pw.apps.length ? `<p class="meta">${pw.apps.map((a) => `${esc(a.app)} ${a.total}번`).join(" · ")}</p>` : ""}`
        : '<p class="meta">이 기간에 받은 횟수가 없어요.</p>'}</section>`;
  }
  // AQ 1.29 끝

  function activeCard(d) {
    const a = d.active;
    if (!a) return "";
    return `<section class="card"><h2>활성 휴대폰</h2>
      <dl class="kv"><dt>하루 평균</dt><dd>${a.daily}대</dd><dt>마지막 7일 하루 평균</dt><dd>${a.week}대</dd>
        <dt>가장 많은 날</dt><dd>${a.most ? `${esc(a.most.day)} · ${a.most.phones}대` : "–"}</dd></dl>
      <p class="hint">사용 통계에 동의한 휴대폰이 보낸 날을 세요. 같은 휴대폰을 구별하지 않아서 '주 활성'은 7일 하루 평균으로 봐요. 설치·삭제 수는 Play Console › 통계에 있어요.</p>
      ${a.versions.length ? `<h3 class="section">앱 판(마지막 7일)</h3><ul class="rows">${a.versions.map((v) => `<li><div class="row"><span class="row-main"><b>${esc(v.app)}</b>
        <small>${v.code ? `번호 ${v.code}` : "번호 모름"}</small></span><span class="row-side"><b>${v.share}%</b><span class="meta">${v.phones}</span></span></div></li>`).join("")}</ul>` : ""}</section>`;
  }

  function plazaCard(d) {
    const p = d.plaza;
    if (!p) return "";
    return `<section class="card"><h2>광장</h2>
      <dl class="kv"><dt>받은 제보</dt><dd>${p.reports}건 · ${p.people}명</dd><dt>채택된 규칙</dt><dd>${p.adopted}</dd><dt>규칙을 내 달력에 적용</dt><dd>${p.applied}</dd>
        <dt>토론 글</dt><dd>${p.posts}</dd><dt>신고</dt><dd>${p.flags}</dd><dt>신고로 가려진 글</dt><dd>${p.hidden}</dd><dt>임금표 시세</dt><dd>${p.rates}(채택 ${p.ratesAdopted})</dd></dl>
      ${L.barChart(p.series.reports.map((v, i) => v + p.series.posts[i]), { labels: dayLabels(d), before: p.series.applied, beforeName: "규칙 적용", name: "날마다 받은 제보·토론 글과 규칙 적용" })}
      <p class="legend"><span><i class="sw" aria-hidden="true"></i>제보 + 토론 글</span><span><i class="sw before" aria-hidden="true"></i>규칙 적용</span></p></section>`;
  }

  function inquiryCard(d) {
    const q = d.inquiries;
    if (!q) return "";
    return `<section class="card"><h2>문의 처리</h2>
      <dl class="kv"><dt>받은 문의</dt><dd>${q.received}건</dd><dt>첫 답을 한 문의</dt><dd>${q.answered}건</dd>
        <dt>첫 답까지(가운데 값)</dt><dd>${mins(q.median)}</dd><dt>첫 답까지(10건 중 9건 안)</dt><dd>${mins(q.p90)}</dd>
        <dt>답 목표 지킴</dt><dd>${q.kept === null ? "–" : `${q.kept}%`}${q.due ? ` <span class="meta">${q.due}건 중</span>` : ""}</dd>
        <dt>도움 됐어요</dt><dd>${q.rated === null ? "–" : `${q.rated}%`} <span class="meta">좋아요 ${q.good} · 아쉬워요 ${q.bad}</span></dd></dl>
      ${q.kinds.length ? `<ul class="rows">${q.kinds.map((k) => `<li><div class="row"><span class="row-main"><b>${esc(KIND_NAMES[k.kind] ?? k.kind)}</b>
        <small>${k.n}건 · 답 ${k.answered}건 · 목표 ${k.target}시간</small></span><span class="row-side"><b>${mins(k.median)}</b></span></div></li>`).join("")}</ul>` : ""}
      ${L.barChart(q.series.received, { labels: dayLabels(d), before: q.series.answered, beforeName: "첫 답", name: "날마다 받은 문의와 첫 답" })}
      <p class="legend"><span><i class="sw" aria-hidden="true"></i>받은 문의</span><span><i class="sw before" aria-hidden="true"></i>첫 답</span></p></section>`;
  }

  function errorCard(d) {
    const e = d.errors;
    if (!e) return "";
    const play = e.play;
    const playLine = !play ? "Play 비정상 종료·ANR 비율은 서버에 PLAY_SERVICE_ACCOUNT가 있으면 위 출시 관문에 바로 보여요."
      : play.error === "denied" ? "Play가 아직 통계를 주지 않아요. Play Console › 사용자 및 권한에서 서비스 계정에 '앱 정보 보기'를 주고, Google Cloud에서 Play Developer Reporting API를 켜 주세요."
        : play.error ? "Play 통계에 닿지 못했어요. 한 시간 뒤 다시 물어요."
          : `Play 기준 ${esc(play.day ?? "")}까지 28일, 사용자 ${play.users ?? 0}명이에요(Play는 하루이틀 늦게 세요).`;
    return `<section class="card"><h2>오류</h2>
      <dl class="kv"><dt>서버가 답하지 못한 요청</dt><dd>${e.serverTotal}번</dd></dl>
      ${e.serverTotal ? L.barChart(e.server, { labels: dayLabels(d), name: "날마다 서버 오류" }) : ""}
      <p class="hint">${playLine}</p>
      <h3 class="section">앱 판별 문제 신고</h3>
      ${e.bugs.length ? `<ul class="rows">${e.bugs.map((b) => `<li><div class="row"><span class="row-main"><b>${esc(b.app)}</b></span><span class="row-side"><b>${b.n}건</b>
        <a class="btn small ghost" href="${L.routeHash("inquiries", null, { status: "all", q: b.app === "모름" ? "" : b.app.split(" ")[0] })}">보기</a></span></div></li>`).join("")}</ul>`
        : '<p class="meta">이 기간에 문제 신고가 없어요.</p>'}</section>`;
  }

  const REMOTE_NAMES = { presets: "직군 프리셋", rates: "최저임금·4대 보험 요율", wages: "시중노임단가", taxtable: "간이세액표", publicpay: "공무원 보수",
    holidays: "공휴일", taxes: "세금", "alarm-guide": "알람 기종별 안내", cycles: "회사 근무주기" };

  function remoteCard() {
    const r = S.data.remote;
    if (!r) return '<section class="card"><h2>원격 갱신</h2><div class="card skeleton short"></div></section>';
    const ok = r.files.filter((f) => f.found && f.valid).length;
    return `<section class="card"><div class="card-head"><h2>원격 갱신</h2><button class="btn small ghost" data-act="remote-refresh">${icon(P.refresh)}다시 읽기</button></div>
      <p class="hint">앱이 하루에 한 번 받는 서명된 계산 자료예요. ${ok}/${r.files.length}개 서명 확인. '받을 수 있는 휴대폰'은 마지막 7일 앱 판 가운데 최소 앱 번호 이상의 비율이에요(앱은 어느 판을 쓰는지 보내지 않아요).</p>
      <ul class="rows">${r.files.map((f) => `<li><div class="row"><span class="row-main"><b>${esc(REMOTE_NAMES[f.kind] ?? f.kind)}</b>
        <small>${f.found ? `판 ${esc(f.version ?? "?")} · ${esc(f.issued ?? "")} · 앱 ${esc(f.minApp ?? "모두")}부터${f.valid && f.oldApps === false ? " · 1.26 이하 앱은 앱 안 값" : ""}` : "사이트에 없어요"}</small></span>
        <span class="row-side">${f.found ? `<span class="pill ${f.valid ? "ok" : "bad"}">${f.valid ? "서명 맞음" : f.signed ? "서명 틀림" : "서명 없음"}</span>
          <b title="받을 수 있는 휴대폰">${f.reach === null ? "–" : `${f.reach}%`}</b>` : '<span class="pill bad">없음</span>'}</span></div></li>`).join("")}</ul>
      <p class="meta">${esc(r.base)} · ${esc(ago(r.at))} 읽음</p></section>`;
  }

  return { metricsView, gatesView, gatesCard, remoteCard };
}
