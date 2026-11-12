// 1.36 [GRW-04][GRW-05][GRW-06] 스튜디오 요구·기본 도구 후보·손잡이 지도. (1.36 [GRW-07] 할 일함 화면은 1.38 [DESK-03]부터 운영 장부
// src/admin/views/ledger.js 안이에요. 서버 /work는 그대로.) Sent to the browser as its own text
// (String(fn)) like the other screen groups and called with what the app shares (A); it may use no name from outside itself.
//   스튜디오 요구: 묶음 카드(같은 요구 N명, 예시 글), 장터에 올리기·정식으로 만들기·판에 나옴·하지 않기·합치기·제목 고치기·되돌리기.
export function workViews(A) {
  const { S, esc, can, href, seg, skeleton, empty, icon, P, api, act, load, render, toast, openDialog, dialog, ago, danger } = A;

  // ---- 스튜디오 요구·기본 도구 후보·손잡이 지도 ----

  const GROWTH_TABS = [["needs", "스튜디오 요구"], ["unsolved", "장터에서도 못 푼 것"], ["tools", "기본 도구 후보"], ["map", "손잡이 지도"], ["ended", "끝난 요구"]];

  function needCard(n, edit, others) {
    const st = { open: "info", market: "warn", building: "warn", solved: "ok", official: "ok", declined: "" }[n.state];
    const live = ["open", "market", "building"].includes(n.state);
    return `<li class="item"><div class="card-head"><h3>${esc(n.title)}</h3><span class="pill ${st}">${esc(n.stateName)}</span></div>
      <p class="meta"><b>${n.askers}명</b>이 부탁 (${n.asks}건)${n.area ? ` · ${esc(n.area)}${n.tool ? ` › ${esc(n.tool)}` : ""}` : ""}${n.style ? ` · ${esc(({ SHIFT: "교대", DAY: "일근", HOURLY: "알바", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자", ALL: "모든 유형" })[n.style] ?? n.style)}` : ""} · ${esc(ago(n.at))}에 처음${n.code ? ` · 풀어 준 작품 ${esc(n.code)}` : ""}${n.version ? ` · ${esc(n.version)} 판` : ""}</p>
      ${n.note ? `<p class="hint">${esc(n.note)}</p>` : ""}
      <ul class="samples">${n.samples.map((x) => `<li><span class="meta">${x.src === "req" ? "요청 판" : "스튜디오"}</span> ${esc(x.text)}</li>`).join("")}</ul>
      ${edit ? `<div class="actions start">
        ${n.state === "open" ? `<button class="btn small" data-act="need" data-id="${n.id}" data-key="market">장터에 올리기</button>`
          : n.state === "market" ? `<button class="btn small ghost" data-act="need" data-id="${n.id}" data-key="unmarket">장터에서 내리기</button>` : ""}
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
      ${d.lines?.length ? `<pre class="copy-box">${esc(d.lines.join("\n"))}</pre><button class="btn small" data-act="growth-copy">복사</button>` : v ? '<p class="hint">이 판에 나온 요구·채택한 도구가 없어요.</p>' : ""}
      <h3>찾아도 없던 말 (지난 4주)</h3>
      <p class="hint">설정·스튜디오·기능 찾기에서 결과가 없을 때 사용자가 '없다고 알리기'를 누른 말이에요. 누가 보냈는지는 없고, 2번 이상 모인 말만 보여요.</p>
      ${d.misses?.length ? `<div class="table-wrap"><table class="table"><thead><tr><th scope="col">말</th><th scope="col">어디서</th><th scope="col">횟수</th></tr></thead>
      <tbody>${d.misses.map((m) => `<tr><td>${esc(m.word)}</td><td>${esc(m.placeName ?? m.place)}</td><td><b>${m.n}</b></td></tr>`).join("")}</tbody></table></div>`
        : '<p class="hint">아직 모인 말이 없어요.</p>'}`;
  }

  function growthView() {
    const t = S.growthTab ?? "needs";
    const d = S.data.growth;
    // 2.0.0 [RVA-24] 검토 HQ-03(조건 1·4): 보기 칩 줄을 목록 머리의 구간 한 줄로, 설명 문단은 목록 아래 접힌 한 줄로.
    const tabs = A.listHead(`<div class="segmented" role="group" aria-label="보기">${GROWTH_TABS.filter(([k]) => (k === "tools" ? can("community") : true))
      .map(([k, label]) => `<button type="button" class="seg" data-act="growth-tab" data-key="${k}" aria-pressed="${t === k}">${esc(label)}${k === "needs" && d?.counts ? `<span class="n">${(d.counts.open ?? 0) + (d.counts.market ?? 0) + (d.counts.building ?? 0)}</span>` : k === "unsolved" && d?.counts?.unsolved ? `<span class="n">${d.counts.unsolved}</span>` : ""}</button>`).join("")}</div>`, "", { scroll: true });
    if (!d || d.tab !== t) return `${tabs}${skeleton(2)}`;
    if (t === "tools") return `${tabs}${toolsBlock(d, can("community", "edit"))}`;
    if (t === "map") return `${tabs}${mapBlock(d)}`;
    const edit = can("inquiries", "edit");
    const intro = t === "unsolved" ? `<details class="tip" data-key="growth-unsolved-how"><summary>올린 지 ${d.rules.staleDays}일 넘게 아무도 못 푼 요구예요</summary><p class="hint">장터 요청 판에 올린 지 ${d.rules.staleDays}일이 지나도 아무도 사용자 부품으로 못 푼 요구예요. 지금 부품으로는 못 만드는 것일 수 있어요. 정식 손잡이 후보로 보세요.</p></details>`
      : t === "needs" ? `<details class="tip" data-key="growth-needs-how"><summary>비슷한 요청이 저절로 묶여요 · ${d.rules.marketAskers}명이면 장터 요청 판에</summary><p class="hint">스튜디오 "원하는 꾸미기가 없나요?"와 요청 판에서 ${d.rules.staleDays}일 동안 아무도 못 만든 요청이 비슷한 것끼리 저절로 묶여요. ${d.rules.marketAskers}명이 모이면 장터 요청 판에 저절로 올라가고, 누가 만들어 완성하면 부탁한 분 모두에게 답이 가요.</p></details>` : "";
    if (!d.items.length) return `${tabs}${empty("요구가 없어요", "")}<div class="list-foot">${intro}</div>`;
    return `${tabs}<ul class="rows needs">${d.items.map((n) => needCard(n, edit, d.items.filter((o) => o.id !== n.id))).join("")}</ul><div class="list-foot">${intro}</div>`;
  }

  async function needAct(id, body, done, undo) {
    const r = await act(() => api(`/growth/needs/${id}`, "POST", body), done, undo);
    load();
    return r;
  }

  const actions = {
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
    // 2.0.0 [RVA-04] 검토 ADM-05: 장터에 올리기는 모든 사용자 요청 판에 보이니 한 번 묻고, 15초 안에 내릴 수 있어요. 내리는 단추도 있어요.
    need: ({ id, key }) => {
      if (key !== "market") return needAct(id, { action: key }, key === "unmarket" ? "장터 요청 판에서 내렸어요" : "되돌렸어요");
      const n = (S.data.growth?.items ?? []).find((x) => x.id === id);
      danger({ title: "장터 요청 판에 올릴까요?", text: "모든 유형의 앱 장터 요청 판에 '여러 분이 부탁했어요'로 보여요. 나중에 내릴 수 있어요.",
        rows: [["무엇을", n?.title ?? ""], ["부탁한 분", n ? `${n.askers}명` : ""]], label: "올리기", tone: "primary",
        run: () => needAct(id, { action: "market" }, "장터 요청 판에 올렸어요", () => needAct(id, { action: "unmarket" }, "내렸어요")) });
    },
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

  return { growthView, toolsBlock, actions, forms };
}
