// 1.38 [DESK-02][DESK-03] 운영 장부: 정리함과 할 일함을 하나로 합친 한 화면. Sent to the browser as its own text (String(fn)) like the
// other screen groups and called with what the app shares (A); it may use no name from outside itself.
//   한 줄 = 한 건(문의·개발사항·스튜디오 요구·신고·사진·검토·운영). 번호(K-0001) · 들어온 날·종류 · 요약 · 겹침 · 묶음 · 단계 · 답 · 담당 · 기한 · 기록.
//   데스크톱은 칸이 맞는 표, 휴대폰은 카드 한 장(번호·요약 한 줄, 그 아래 단계·담당·기한). 줄을 누르면 그 자리에서 펼쳐
//   원문·겹침 후보 3개·정리함 답(고쳐서 보내기·묶기·개발사항으로·빼 두기, 자주 쓰는 답이면 '마을 Q&A로 올리기' [EASE-31])·상태(보는 중·기다림·끝내기, 이유는 꼭)·요약·기록(되돌리기).
//   위에는 보기(오늘 할 것 · Claude 차례 · 최고 관리자 확인 · 개발 중 · 출시 대기 · 기한 넘음 · 열린 것 · 닫힘)와 종류·판·찾기·CSV,
//   그리고 정리함에 있던 'Claude에게 넘기기·개발 결과 붙여 넣기·자동 대응'을 접어 둔 칸.
export function ledgerViews(A) {
  const { S, esc, can, href, seg, skeleton, empty, icon, P, api, act, load, render, toast, openDialog, dialog, ago, moment, syncUrl, handoffCard, inboxLink, listHead } = A;

  const STAGE_TONE = { intake: "info", sorted: "info", answered: "ok", devwait: "warn", handed: "warn", doing: "warn", built: "warn", notify: "bad", shipped: "ok",
    waiting: "", rejected: "", closed: "" };
  const TURN_TONE = { claude: "info", owner: "bad", admin: "", asker: "", ship: "warn" };
  const PILE_DO = { fixed: "답 보내기", known: "묶고 답하기", again: "다시 접수하고 답하기", declined: "답 보내기", answered: "이 답 보내기", faq: "이 답 보내기", new: "개발사항으로 넘기기" };
  const WHO = { claude: "Claude", person: "", auto: "자동", asker: "" };

  const rowOf = (key) => (S.data.ledger?.items ?? []).find((r) => r.key === key);
  const shortDay = (t) => t.slice(5, 10).replace("-", ".");

  function dueCell(r) {
    if (!r.due) return '<span class="meta">—</span>';
    const left = Math.round((Date.parse(r.due) - Date.parse(S.data.ledger.now)) / 60000);
    const abs = Math.abs(left);
    if (abs < 1) return `<span class="pill ${r.late ? "bad" : "warn"}">${r.late ? "방금 넘음" : "곧"}</span>`;
    const text = abs < 60 ? `${abs}분` : abs < 48 * 60 ? `${Math.round(abs / 60)}시간` : `${Math.round(abs / 1440)}일`;
    return `<span class="pill ${r.late ? "bad" : left < 6 * 60 ? "warn" : ""}">${r.late ? `${text} 넘음` : `${text} 남음`}</span>`;
  }

  function whoCell(r) {
    if (r.holder) return `<span class="pill">${esc(r.holder)}</span>`;
    if (!r.turn) return '<span class="meta">—</span>';
    return `<span class="pill ${TURN_TONE[r.turn]}">${esc(r.turn === "claude" ? "Claude" : r.turn === "owner" ? "최고 관리자" : r.turn === "admin" ? "관리자" : r.turn === "ship" ? "출시" : "문의한 분")}</span>`;
  }

  function answerCell(r) {
    const a = r.answer;
    if (!a) return '<span class="meta">—</span>';
    if (a.key === "sent") return `<span class="pill ok" title="${esc(a.text)}">보냄${a.byKind === "claude" ? " · Claude" : a.byKind === "auto" ? " · 자동" : ""}</span>`;
    if (a.key === "owner") return `<span class="pill bad" title="${esc(a.why)}">확인 필요</span>`;
    return `<span class="pill">${esc(a.name)}</span>`;
  }

  function overlapCell(r) {
    const o = r.overlap?.[0];
    if (!o) return r.sort && r.sort.pile !== "new" && r.sort.match ? `<small>${esc(r.sort.pileName)}</small>` : '<span class="meta">—</span>';
    return `<small title="${esc(r.overlap.map((x) => `${x.title} (${x.why})`).join("\n"))}">≈ ${esc(o.title)} <b>${Math.round(o.score * 100)}%</b>${r.overlap.length > 1 ? ` 외 ${r.overlap.length - 1}` : ""}</small>`;
  }

  function bundleCell(r) {
    const b = r.bundle;
    if (!b) return '<span class="meta">—</span>';
    if (r.type === "dev") return `<small>문의 ${b.asks}건</small>`;
    return `<a href="${href("dev", b.id)}" title="${esc(b.title)}"><small>${esc(b.id.slice(0, 7))} · ${b.asks}건</small></a>`;
  }

  function moveCell(r) {
    const m = r.lastMove;
    if (!m) return `<small>${esc(ago(r.at))}</small>`;
    return `<small title="${esc(moment(m.at))}">${m.byKind === "claude" ? '<span class="who-claude">Claude</span>' : esc(m.by || WHO[m.byKind] || "")} · ${esc(m.what)} · ${esc(ago(m.at))}</small>`;
  }

  /**
   * 2.0.0 [RVA-37] 검토 I24b-02: 화면에서도 한 줄을 그리다 오류가 나면 그 줄만 '그리지 못한 줄'로 두고 장부 전체는 그려요(그 오류는 A.screenError로 알려요).
   */
  function safeLine(r) {
    try {
      return line(r);
    } catch (e) {
      A.screenError?.(e, "ledger-line");
      return `<li class="lg-line"><p class="note warn">${esc(r?.no ?? "")} 이 줄은 그리지 못했어요. 나머지는 그대로예요.</p></li>`;
    }
  }

  /** 한 줄(데스크톱 칸 · 휴대폰 카드가 같은 마크업, CSS가 나눠 보여요). */
  function line(r) {
    const picking = S.ledgerPick;
    const flags = [r.reopened > 0 && r.state === "new" ? `<span class="pill bad">다시 열림${r.reopened > 1 ? ` ${r.reopened}` : ""}</span>` : "",
      r.urgent && !r.closed ? '<span class="pill bad">급함</span>' : "", r.skipped?.back ? '<span class="pill warn">빼 둔 뒤 돌아옴</span>' : "",
      r.state === "seeing" || r.state === "waiting" ? `<span class="pill">${esc(r.stateName)}${r.stateReasonName ? ` · ${esc(r.stateReasonName)}` : ""}</span>` : "",
      r.state === "done" ? `<span class="pill">끝 · ${esc(r.stateReasonName)}</span>` : "",
      // 2.0.0 [RVA-37] 검토 I24b-02: 서버가 읽지 못해 빼 둔 줄(장부는 계속 돌아요).
      r.unreadable ? '<span class="pill warn">읽지 못함</span>' : ""].join("");
    const title = r.summary || r.title || "(내용 없음)";
    const pick = picking ? `<label class="lg-pick"><input type="checkbox" data-act="ledger-pick" data-key="${esc(r.key)}"${picking.has(r.key) ? " checked" : ""}><span class="sr">고르기</span></label>` : "";
    return `<li class="lg-line${r.late ? " late" : ""}${r.closed ? " shut" : ""}" data-key="${esc(r.key)}"><details data-key="lg-${esc(r.key)}"${S.opened.get(`lg-${r.key}`) ? " open" : ""}>
      <summary class="row lg-row">${pick}
        <span class="c-no"><b>${esc(r.no ?? "")}</b></span>
        <span class="c-in"><small>${esc(shortDay(r.at))}</small> <span class="kind">${esc(r.kindName)}</span></span>
        <span class="c-sum"><b>${esc(title)}</b>${flags}<small class="sub">${[r.path ? `[${esc(r.path)}]` : "", r.app ? `앱 ${esc(r.app)}` : "", r.releasedIn && r.type === "dev" ? `${esc(r.releasedIn)}에 고침` : "",
          r.same ? `나도 그래요 ${r.same}` : "", r.n2 ? `${r.n2}${r.type === "studio_need" ? "명" : "건"}` : "", r.meta ? esc(r.meta) : ""].filter(Boolean).join(" · ")}</small></span>
        <span class="c-ov">${overlapCell(r)}</span>
        <span class="c-bu">${bundleCell(r)}</span>
        <span class="c-st"><span class="pill ${STAGE_TONE[r.stage] ?? ""}">${esc(r.stageName)}</span></span>
        <span class="c-an">${answerCell(r)}</span>
        <span class="c-who">${whoCell(r)}</span>
        <span class="c-due">${dueCell(r)}</span>
        <span class="c-log">${moveCell(r)}</span>
      </summary>
      <div class="lg-detail">${detail(r)}</div></details></li>`;
  }

  /** 펼친 줄: 원문, 겹침 후보, 묶음, 답, 정리함의 처리, 상태·요약·기록. */
  function detail(r) {
    const editAsk = can("inquiries", "edit");
    const parts = [];
    if (r.unreadable) {
      parts.push(`<p class="note warn">이 줄은 읽다가 오류가 나서 빼 두었어요. 나머지 줄은 그대로 돌아요.${S.me.owner ? ` <a href="${href("errors")}">오류 묶음에서 보기</a>` : ""}</p>
        <p class="meta">오류 원문: <code class="mono">${esc(r.unreadable)}</code></p>`);
    }
    if (r.text) parts.push(`<blockquote class="lg-text">${esc(r.text)}</blockquote>`);
    const meta = [r.kindName, r.topicName, r.path ? `화면 [${r.path}]` : "", r.app ? `앱 ${r.app}` : "", r.model, `${moment(r.at)}에 들어옴`, r.handedIn ? `넘김 묶음 ${r.handedIn}` : "",
      r.skipped ? `정리함에서 뺌 ${moment(r.skipped.at)}${r.skipped.back ? " · 7일이 지나 다시 왔어요" : " · 7일 뒤 아직 답이 없으면 다시 와요"}` : ""].filter(Boolean);
    parts.push(`<p class="meta">${meta.map(esc).join(" · ")}</p>`);
    if (r.summary) parts.push(`<p class="meta">요약: ${esc(r.summary)}${r.summaryBy ? ` (${esc(r.summaryBy)})` : ""} · 원문 첫 문장: ${esc(r.title)}</p>`);
    if (r.overlap?.length) {
      parts.push(`<div class="lg-block"><b>겹침 후보</b><ol class="mini">${r.overlap.map((o) => `<li>${o.type === "dev" ? `<a href="${href("dev", o.id)}">개발사항 ‘${esc(o.title)}’</a>`
        : o.type === "inquiry" ? `<a href="${href("inquiries", o.id)}">전에 답한 문의 ‘${esc(o.title)}’</a>` : `자주 쓰는 답 ‘${esc(o.title)}’`} <span class="pill">${Math.round(o.score * 100)}%</span>
        <small>${esc(o.why)}</small></li>`).join("")}</ol></div>`);
    }
    if (r.bundle && r.type === "inquiry") {
      parts.push(`<p class="lg-block"><b>묶음</b> <a href="${href("dev", r.bundle.id)}">${esc(r.bundle.title)}</a> · ${esc(r.bundle.statusName)}${r.bundle.releasedIn ? ` ${esc(r.bundle.releasedIn)}${r.bundle.out ? " (나감)" : " (출시 전)"}` : ""} · 묶인 문의 ${r.bundle.asks}건${r.bundle.told ? " · 나왔다고 알림" : ""}</p>`);
    }
    if (r.answer?.key === "owner") parts.push(`<p class="note bad">${esc(r.answer.why)}: Claude가 혼자 답하지 않아요. 최고 관리자가 정해 주세요.</p>`);
    if (r.answer?.text || r.answer?.before) parts.push(`<p class="lg-block"><b>보낸 답</b>${r.answer.by ? ` <small>${esc(r.answer.by)}</small>` : ""}<br>${esc(r.answer.text ?? r.answer.before)}</p>`);
    // 정리함의 처리 [DESK-03]: 서버가 미리 쓴 답을 고쳐 보내거나, 개발사항에 묶거나, 새 개발사항으로 넘기거나, 빼 두기.
    if (r.sort && editAsk && !r.closed) {
      const k = r.sort.pile;
      const group = k === "new" ? r.sort.group ?? [r.id] : null;
      parts.push(`<div class="lg-block lg-sort"><b>${esc(r.sort.pileName)}</b>${r.sort.match ? ` <small>≈ ${esc(r.sort.match.title)}</small>` : ""}
        ${k === "new" ? `<label class="sort-field"><span>개발사항 제목</span><input id="lg-title-${esc(r.id)}" value="${esc(r.sort.groupTitle ?? r.title)}" maxlength="100"></label>
          ${group.length > 1 ? `<p class="meta">비슷한 문의 ${group.length}건이 한 개발사항으로 함께 가요.</p>` : ""}` : ""}
        <label class="sort-field"><span>${k === "new" ? "함께 보낼 답 (비우면 답 없이 넘겨요)" : "보낼 답"}</span><textarea id="lg-reply-${esc(r.id)}" rows="3">${esc(r.sort.reply ?? "")}</textarea></label>
        <div class="actions start"><button class="btn small primary" data-act="ledger-sort" data-key="${esc(r.key)}">${esc(k === "new" && group.length > 1 ? `${group.length}건 개발사항으로 넘기기` : PILE_DO[k])}</button>
          ${k === "new" ? `<button class="btn small" data-act="ledger-reply" data-key="${esc(r.key)}">답만 보내기</button>` : ""}
          <button class="btn small ghost" data-act="ledger-skip" data-key="${esc(r.key)}">빼 두기 (7일)</button>
          ${k === "faq" && r.sort.match?.type === "template" ? `<button class="btn small" data-act="template-edit" data-id="${esc(r.sort.match.id)}" title="이 답을 마을 밤낮 Q&A에 공개해요">마을 Q&A로 올리기</button>` : ""}</div></div>`);
    }
    // 원래 칸(처리 자체는 그 화면에서): 할 일함과 같은 길.
    const open = r.link ?? inboxLink({ type: r.type, id: r.id });
    parts.push(`<div class="actions start lg-acts">
      ${r.state === "new" && !r.closed ? `<button class="btn small" data-act="ledger-set" data-key="${esc(r.key)}" data-state="seeing" title="s">보는 중</button>` : ""}
      ${r.state !== "waiting" && r.state !== "done" && !r.closed ? `<button class="btn small ghost" data-act="ledger-wait" data-key="${esc(r.key)}" title="w">기다림</button>` : ""}
      ${r.state !== "done" && !r.closed ? `<button class="btn small primary" data-act="ledger-done" data-key="${esc(r.key)}" title="e">끝내기</button>` : ""}
      ${r.state === "done" || r.state === "waiting" || r.state === "seeing" ? `<button class="btn small" data-act="ledger-set" data-key="${esc(r.key)}" data-state="new">다시 열기</button>` : ""}
      <button class="btn small ghost" data-act="ledger-summary" data-key="${esc(r.key)}">요약 ${r.summary ? "고치기" : "쓰기"}</button>
      <button class="btn small ghost" data-act="ledger-log" data-key="${esc(r.key)}" title="h">기록·되돌리기</button>
      ${open ? `<a class="btn small ghost" href="${esc(open)}">원래 화면${icon(P.chevron)}</a>` : ""}</div>`);
    return parts.join("");
  }

  function head() {
    return `<div class="lg-head" aria-hidden="true">${S.ledgerPick ? '<span class="lg-pick"></span>' : ""}<span class="c-no">번호</span><span class="c-in">들어온 날·종류</span><span class="c-sum">요약</span>
      <span class="c-ov">겹침</span><span class="c-bu">묶음</span><span class="c-st">단계</span><span class="c-an">답</span><span class="c-who">담당</span><span class="c-due">기한</span><span class="c-log">기록</span></div>`;
  }

  /** 정리함에 있던 도구: Claude에게 넘기기·개발 결과 붙여 넣기와 자동 대응(최고 관리자). 처음엔 접혀 있어요. */
  function tools(d) {
    if (!can("inquiries")) return "";
    const owner = S.me?.owner;
    const auto = d.auto ?? { fixed: false, known: false };
    const h = S.data.handoff;
    const n = (h?.items?.length ?? 0) + (h?.asks ?? 0);
    const openIt = S.opened.get("lg-tools") ?? Boolean(S.sortResult);
    return `<details class="card lg-tools" data-key="lg-tools" id="sort-tools"${openIt ? " open" : ""}><summary><b>Claude에게 넘기기 · 결과 붙여 넣기 · 자동 대응</b>
        <span class="meta">${n ? `넘길 것 ${n}` : "넘길 것 없음"}${d.shipped ? ` · 나간 판 ${esc(d.shipped.v)}` : " · 나간 판 아직 안 적음"}</span></summary>
      <div class="sort-top">${handoffCard()}<div class="card auto-card"><div class="auto-head"><b>자동 대응</b>
        <small>${owner ? "켜 두면 문의가 들어오는 순간 서버가 묶고 답해요. 답에는 ‘자동 정리’가 이름으로 붙어요." : "최고 관리자가 켜고 꺼요."}</small></div>
        ${[["fixed", "이미 고침"], ["known", "개발 목록에 있음"]].map(([k, label]) => `<label class="switch"><input type="checkbox" data-act="sort-auto" data-key="${k}"${auto[k] ? " checked" : ""}${owner ? "" : " disabled"}>
          <span class="track" aria-hidden="true"></span><span>${esc(label)}</span></label>`).join("")}</div></div></details>`;
  }

  function ledgerView(strip = "") {
    const d = S.data.ledger;
    if (!d) return `<div class="tiles core">${'<div class="tile skeleton"></div>'.repeat(5)}</div>${skeleton(3)}`;
    const c = d.counts;
    const view = S.deskView || "today";
    // 2.0.0 [RVA-24] 검토 HQ-03·ADM-08·U23-05·T24-07(조건 1): 보기 카드 다섯 장과 같은 숫자의 구간 줄이 겹쳐 있었어요. 구간 줄 하나(옆으로 밀림,
    // 기한 넘음·최고 관리자 확인 숫자는 빨강)와 찾기·CSV·고르기 한 줄만 위에, 종류·판은 '거르기' 안, 보기 설명·단축키·흐름 줄·Claude 도구는 목록 아래.
    const HOT = { owner: true, late: true };
    const views = `<div class="segmented" role="group" aria-label="보기">${Object.entries(d.names.views).map(([k, label]) => {
      const n = ["all", "closed"].includes(k) ? 0 : c[k];
      return `<button class="seg" data-act="ledger-view" data-key="${k}" aria-pressed="${view === k}">${esc(label)}${n ? `<span class="n${HOT[k] ? " hot" : ""}">${n}</span>` : ""}</button>`;
    }).join("")}</div>`;
    const gr = S.deskGroup || "";
    const groups = `<div class="chips" role="group" aria-label="종류">${[["", "모든 종류"], ...Object.entries(d.names.groups)].map(([k, label]) =>
      `<button type="button" class="chip pick" data-act="ledger-group" data-key="${k}" aria-pressed="${gr === k}">${esc(label)}${k && c.groups[k] ? ` <span class="n">${c.groups[k]}</span>` : ""}</button>`).join("")}</div>
      <p class="filter-row"><label for="lg-v">판</label><select id="lg-v" name="v" data-change="ledger-v"><option value="">모든 판</option>${d.versions.map((v) => `<option value="${esc(v)}"${S.deskV === v ? " selected" : ""}>${esc(v)}</option>`).join("")}</select></p>`;
    const toolHtml = `<form class="lg-bar" data-form="ledger-search" role="search"><input id="search" name="q" type="search" placeholder="번호(K-0012)·글·개발사항으로 찾기" value="${esc(S.deskQuery || "")}" autocomplete="off" enterkeyhint="search" aria-label="장부에서 찾기"></form>
        <button class="btn icon-wide" type="button" data-act="ledger-csv" aria-label="CSV로 받기" title="CSV로 받기"${d.total ? "" : " disabled"}>${icon(P.install)}<span class="lbl">CSV로 받기</span></button>
        ${S.ledgerPick ? '<button class="btn" type="button" data-act="ledger-pick-mode" aria-pressed="true">선택 마침</button>'
          : `<button class="btn icon-wide" type="button" data-act="ledger-pick-mode" aria-pressed="false" aria-label="여러 개 고르기" title="여러 개 고르기">${icon(P.picks)}<span class="lbl">여러 개 고르기</span></button>`}`;
    const top = listHead(views, toolHtml, { scroll: true, open: Boolean(S.ledgerPick), busy: Boolean(S.deskQuery), filters: groups, active: [gr, S.deskV].filter(Boolean).length });
    const picking = S.ledgerPick;
    const bulk = picking ? `<div class="bulkbar" role="toolbar" aria-label="고른 줄"><b>${picking.size}개 골랐어요</b>
        <button class="btn small ghost" data-act="ledger-pick-all">${picking.size && picking.size === d.items.length ? "모두 풀기" : "모두 고르기"}</button>
        <button class="btn small" data-act="ledger-bulk" data-state="seeing"${picking.size ? "" : " disabled"}>보는 중</button>
        <button class="btn small" data-act="ledger-wait"${picking.size ? "" : " disabled"}>기다림</button>
        <button class="btn small primary" data-act="ledger-done"${picking.size ? "" : " disabled"}>끝내기</button>
        <button class="btn small" data-act="ledger-sort-many"${sortable(picking).length ? "" : " disabled"} title="정리함이 미리 써 둔(또는 고친) 답을 고른 줄마다 보내요">적힌 답 보내기 ${sortable(picking).length || ""}</button></div>` : "";
    const hint = `<p class="hint lg-hint list-note">${esc({ today: "기한이 오늘인 것, 넘은 것, 다시 열린 것, 하루 안에 들어온 것, 최고 관리자가 정할 것이에요.", claude: "Claude가 가져갈 줄이에요. 프로젝트 채팅에 \"처리해\"라고 하면 Claude가 운영 열쇠로 이 목록을 읽어 처리해요.",
      owner: "Claude가 혼자 보내지 않는 것이에요(환불·결제·보상, 법·안전, 개인정보, 크게 화난 민원, 약관, 회사 방식). 답을 정해 주세요.", dev: "개발사항으로 묶여 만드는 중인 줄이에요.",
      ship: "고쳐 두었고 Play에 나가기를 기다려요. 올린 뒤 흐름에서 나간 판을 저장하면 묶인 문의에 한꺼번에 안내가 가요.", late: "기한(답 목표·검토 기한·기다림 7일)을 넘긴 줄이에요.",
      open: "끝나지 않은 모든 줄이에요.", closed: `끝낸 줄(최근 ${d.days}일)이에요. 끝낸 뒤 새 움직임이 생기거나, 처리되지 않고 7일 남아 있으면 다시 열려요.`, all: `모든 줄(닫힌 것은 최근 ${d.days}일)이에요.` }[view] ?? "")}
      <span class="meta">j/k 이동 · Enter 펼치기 · s 보는 중 · w 기다림 · e 끝내기 · h 기록</span></p>`;
    const list = d.items.length ? `<div class="lg-table${picking ? " picking" : ""}">${head()}<ul class="rows ledger">${d.items.map(safeLine).join("")}</ul></div>
        ${d.more ? `<p class="meta">${d.total}줄 중 ${d.items.length}줄을 보여요. 찾기나 종류로 좁혀 보세요.</p>` : ""}`
      : empty(view === "closed" ? "끝낸 줄이 없어요" : S.deskQuery ? "찾는 줄이 없어요" : "이 보기에는 할 일이 없어요",
        view === "today" ? "새로 들어오면 여기 와요. 열린 것 모두는 위 '열린 것 모두'에서 봐요." : "");
    return `<div class="ledger-view">${top}${bulk}${list}${hint}${strip}${tools(d)}</div>`;
  }

  // ---- 바꾸기 ----

  async function setState(keys, state, reason = "", note = "") {
    const names = S.data.ledger?.names.states ?? {};
    const r = await act(() => api("/ledger/state", "POST", { keys, state, reason, note }), `${keys.length > 1 ? `${keys.length}줄을 ` : ""}${names[state] ?? state}(으)로 바꿨어요`,
      () => act(() => Promise.all(keys.map((key) => api("/ledger/undo", "POST", { key }))), "되돌렸어요").then(() => load()));
    if (r) S.ledgerPick = null;
    load();
    return r;
  }

  function openDone(keys) {
    const d = S.data.ledger;
    if (!d || !keys.length) return;
    openDialog(`<form data-form="ledger-done" data-keys="${esc(keys.join(","))}"><h2>${keys.length > 1 ? `${keys.length}줄을 끝낼까요?` : "끝낼까요?"}</h2>
      <p>끝낸 이유를 꼭 골라요. 정리함과 할 일함이 같은 이유를 써요. 끝낸 뒤 새 움직임이 생기거나, 처리되지 않고 7일 남아 있으면 다시 열려요.</p>
      <div class="radios">${Object.entries(d.names.done).map(([k, v]) => `<label class="radio"><input type="radio" name="reason" value="${k}" required><span>${esc(v)}</span></label>`).join("")}</div>
      <label for="lg-note">메모 (선택)</label><input id="lg-note" name="note" maxlength="300" placeholder="예: K-0012와 같음, 1.38에서 고침">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">끝내기</button></div></form>`);
  }

  function openWait(keys) {
    const d = S.data.ledger;
    if (!d || !keys.length) return;
    openDialog(`<form data-form="ledger-wait" data-keys="${esc(keys.join(","))}"><h2>무엇을 기다리나요?</h2>
      <p>기다림은 7일이 지나면 기한 넘김으로 맨 위에 와요.</p>
      <div class="radios">${Object.entries(d.names.wait).map(([k, v]) => `<label class="radio"><input type="radio" name="reason" value="${k}" required><span>${esc(v)}</span></label>`).join("")}</div>
      <label for="lg-wnote">메모 (선택)</label><input id="lg-wnote" name="note" maxlength="300">
      <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">기다림으로</button></div></form>`);
  }

  async function openLog(key) {
    const d = await act(() => api(`/ledger/log?key=${encodeURIComponent(key)}`));
    if (!d) return;
    const r = rowOf(key);
    const kind = { claude: "Claude", auto: "자동", asker: "문의한 분", person: "" };
    openDialog(`<div><h2>${esc(r?.no ?? "")} 기록</h2>${r ? `<p class="meta">${esc(r.summary || r.title)}</p>` : ""}
      ${d.undo?.to ? `<p class="note">마지막 바꿈: ${esc(d.undo.what)} <button class="btn small" type="button" data-act="ledger-undo" data-key="${esc(key)}">되돌리기</button></p>` : ""}
      ${d.items.length ? `<ul class="feed">${d.items.map((e) => `<li><span class="when">${esc(ago(e.at))}</span><span>${esc(e.what)}</span>
        <span class="who${e.byKind === "claude" ? " who-claude" : ""}">${esc(kind[e.byKind] || e.by)}</span></li>`).join("")}</ul>` : '<p class="hint">아직 기록이 없어요.</p>'}
      <div class="actions"><button class="btn" type="button" data-act="close">닫기</button></div></div>`);
  }

  /** 고른 줄 중 정리함 답을 한 번에 보낼 수 있는 것(새 문제는 하나씩 개발사항으로). */
  const sortable = (picked) => [...(picked ?? [])].map(rowOf).filter((r) => r?.sort && r.sort.pile !== "new" && !r.closed);

  /** 정리함 동작 하나(서버 /inquiries/sort/apply가 받는 모양). */
  function sortAction(r, replyOnly = false) {
    const text = document.getElementById(`lg-reply-${r.id}`)?.value?.trim() ?? r.sort.reply;
    const k = r.sort.pile;
    if (replyOnly) return text ? { do: "reply", id: r.id, text } : null;
    if (k === "new") {
      const title = document.getElementById(`lg-title-${r.id}`)?.value?.trim() || r.sort.groupTitle || r.title;
      const ids = r.sort.group ?? [r.id];
      const texts = ids.map((id) => (rowOf(`inquiry:${id}`)?.text ?? "")).filter(Boolean);
      return { do: "dev", ids, title, body: (texts.length ? texts : [r.text]).map((t) => `- ${t}`).join("\n").slice(0, 4000), ...(text ? { text } : {}) };
    }
    if (k === "known") return { do: "reply", id: r.id, item: r.sort.match.id, text };
    if (k === "again") return { do: "reopen", id: r.id, item: r.sort.match.id, text };
    return { do: "reply", id: r.id, text };
  }

  async function sortApply(actions, done) {
    const r = await act(() => api("/inquiries/sort/apply", "POST", { actions }), (x) => [x.replied && `답 ${x.replied}건`, x.linked && `묶음 ${x.linked}건`,
      x.created?.length && `개발사항 ${x.created.length}개`, x.reopened && `다시 접수 ${x.reopened}개`, x.skipped && `빼 둠 ${x.skipped}건`].filter(Boolean).join(" · ") || done);
    if (r) { S.data.handoff = null; load(); }
  }

  function refilter() {
    S.ledgerPick = null;
    syncUrl();
    render({ keepScroll: true });
    load();
  }

  const actions = {
    "ledger-view": ({ key }) => { S.deskView = key; refilter(); },
    "ledger-group": ({ key }) => { S.deskGroup = key; refilter(); },
    "ledger-pick-mode": () => { S.ledgerPick = S.ledgerPick ? null : new Set(); render({ keepScroll: true }); },
    "ledger-pick": ({ key }) => {
      if (!S.ledgerPick) return;
      if (S.ledgerPick.has(key)) S.ledgerPick.delete(key);
      else S.ledgerPick.add(key);
      render({ keepScroll: true });
    },
    "ledger-pick-all": () => {
      const all = (S.data.ledger?.items ?? []).map((r) => r.key);
      S.ledgerPick = S.ledgerPick?.size === all.length ? new Set() : new Set(all);
      render({ keepScroll: true });
    },
    "ledger-bulk": ({ state }) => setState([...(S.ledgerPick ?? [])], state),
    "ledger-set": ({ key, state }) => setState([key], state),
    "ledger-wait": ({ key }) => openWait(key ? [key] : [...(S.ledgerPick ?? [])]),
    "ledger-done": ({ key }) => openDone(key ? [key] : [...(S.ledgerPick ?? [])]),
    "ledger-log": ({ key }) => openLog(key),
    "ledger-undo": async ({ key }) => {
      dialog.close();
      if (await act(() => api("/ledger/undo", "POST", { key }), "되돌렸어요")) load();
    },
    "ledger-summary": ({ key }) => {
      const r = rowOf(key);
      if (!r) return;
      openDialog(`<form data-form="ledger-summary" data-key="${esc(key)}"><h2>${esc(r.no ?? "")} 한 줄 요약</h2>
        <p>장부에 원문 대신 보이는 한 줄이에요. 비우면 원문 첫 문장이 보여요. 원문은 그대로예요.</p>
        <label for="lg-sum">요약</label><input id="lg-sum" name="text" maxlength="120" value="${esc(r.summary)}" placeholder="${esc(r.title)}">
        <div class="actions"><button class="btn" type="button" data-act="close">취소</button><button class="btn primary" type="submit">저장</button></div></form>`);
    },
    "ledger-sort": ({ key }) => {
      const r = rowOf(key);
      if (!r?.sort) return;
      const a = sortAction(r);
      return sortApply([a], "처리했어요");
    },
    // 정리함의 'N건 모두 답 보내기'를 고른 줄로: 줄마다 적힌(고친) 답이 그대로 가요.
    "ledger-sort-many": () => {
      const rows = sortable(S.ledgerPick);
      if (!rows.length) return toast("정리함 답이 있는 줄을 골라 주세요");
      const list = rows.map((r) => sortAction(r));
      A.confirmBox(`${rows.length}줄에 적힌 답 보내기`, "줄마다 적힌 답이 그대로 가요(펼쳐서 고친 것은 고친 대로). 보낸 답은 되돌릴 수 없어요.", "보내기",
        () => { S.ledgerPick = null; return sortApply(list, "처리했어요"); }, "primary");
    },
    "ledger-reply": ({ key }) => {
      const r = rowOf(key);
      const a = r?.sort ? sortAction(r, true) : null;
      if (!a) return toast("보낼 답을 적어 주세요");
      return sortApply([a], "답을 보냈어요");
    },
    "ledger-skip": ({ key }) => {
      const r = rowOf(key);
      if (r) return sortApply([{ do: "skip", id: r.id }], "정리함에서 7일 동안 뺐어요");
    },
    "ledger-v": (_, el) => { S.deskV = el?.value ?? ""; refilter(); },
    "ledger-csv": async () => {
      // 지금 보기 그대로 서버에서 모든 줄을 CSV로(엑셀에서 한글이 깨지지 않게 BOM 포함).
      const p = new URLSearchParams({ view: S.deskView || "today", ...(S.deskGroup ? { group: S.deskGroup } : {}), ...(S.deskV ? { v: S.deskV } : {}), ...(S.deskQuery ? { q: S.deskQuery } : {}) });
      try {
        // 2.0.0 [RVA-01] 검토 ADM-20: 기기에 남기지 않고(no-store, 서비스 워커도 남기지 않음), 로그인이 끝났으면 다시 로그인한 뒤 이어서 받아요.
        const res = await fetch(`/api/admin/ledger.csv?${p}`, { headers: { "x-admin": "1" }, credentials: "same-origin", cache: "no-store" });
        if (res.status === 401) return A.reloginThen(() => actions["ledger-csv"]());
        if (!res.ok) throw new Error("server");
        const url = URL.createObjectURL(await res.blob());
        const a = document.createElement("a");
        a.href = url;
        a.download = `밤낮-운영장부-${S.data.ledger?.now?.slice(0, 10) ?? "오늘"}.csv`;
        document.body.append(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        toast("CSV로 받았어요");
      } catch {
        toast("CSV를 받지 못했어요. 다시 해 주세요");
      }
    },
  };

  const forms = {
    "ledger-search": (form) => {
      S.deskQuery = String(new FormData(form).get("q") ?? "").trim();
      refilter();
    },
    "ledger-done": (form) => {
      const data = new FormData(form);
      if (!data.get("reason")) return toast("끝낸 이유를 골라 주세요");
      dialog.close();
      return setState(form.dataset.keys.split(","), "done", String(data.get("reason") ?? ""), String(data.get("note") ?? ""));
    },
    "ledger-wait": (form) => {
      const data = new FormData(form);
      if (!data.get("reason")) return toast("무엇을 기다리는지 골라 주세요");
      dialog.close();
      return setState(form.dataset.keys.split(","), "waiting", String(data.get("reason") ?? ""), String(data.get("note") ?? ""));
    },
    "ledger-summary": async (form) => {
      dialog.close();
      if (await act(() => api("/ledger/summary", "POST", { key: form.dataset.key, text: String(new FormData(form).get("text") ?? "") }), "요약을 저장했어요")) load();
    },
  };

  /** j/k로 고른 줄(없으면 첫 줄)의 key. */
  function cursorKey() {
    const rows = [...document.querySelectorAll("#main .rows.ledger > li")];
    const focused = document.activeElement?.closest?.(".rows.ledger > li");
    return (focused ?? rows[S.cursor] ?? rows[0])?.dataset?.key ?? null;
  }

  /** s, w, e, h on 장부. */
  function ledgerKey(what) {
    const key = cursorKey();
    if (!key) return false;
    if (what === "seeing") setState([key], "seeing");
    if (what === "wait") openWait([key]);
    if (what === "done") openDone([key]);
    if (what === "log") openLog(key);
    return true;
  }

  return { ledgerView, ledgerKey, actions, forms };
}
