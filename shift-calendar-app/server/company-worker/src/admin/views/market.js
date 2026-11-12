// 2.0.0 [RVA-57] 검토 ADM-11·I23c-02: 사람과 글 › 마을 장터(나눔 판의 보기·출시 대기·판 정리·장터 건강·사진·요청·글꼴·이달의 제작자)와 밤낮 재료를
// 첫 묶음에서 떼어 낸 화면 묶음이에요. 그 화면을 열 때 받아요(src/admin-app.js adminFiles). 바깥 이름은 A로만 받아요.
export function marketViews(A) {
  const { S, V, act, ago, app, busy, can, copy, danger, empty, esc, filePick, go, health, icon, listHead, moreMenu, schedule, seg, serverVersion, skeleton, visible } = A;

  // [RCP-39] 1.34 나눔 판.
  // 1.35 [RCX-28] 첫 3개 검토 is gone: 신고됨 comes first; 검토 대기 holds only what waited before (and goes when empty).
  const RECIPE_TABS = [["flagged", "신고됨"], ["visible", "새로 올라온 것"], ["waiting", "출시 대기"], ["hidden", "가린 것"], ["retired", "판 정리로 내림"], ["health", "장터 건강"], ["queue", "전에 기다리던 것"], ["official", "공식·공개 자료"],
    ["showcase", "보여주기"], ["picks", "이번 주 고른 것"], ["images", "사진 검토"], ["requests", "요청 판"], ["tools", "기본 도구 후보"], ["makers", "프로필 신고"], ["month", "이달의 제작자"], ["fonts", "나눈 글꼴"]];
  const RECIPE_STYLES = [["ALL", "모두"], ["SHIFT", "교대"], ["DAY", "일근"], ["HOURLY", "알바"], ["FIELD", "현장"], ["BUSINESS", "자영업"], ["INVEST", "투자"]];
  const RECIPE_FLAGS = { personal: "개인 정보", fake: "사칭·거짓", harm: "해롭거나 불쾌", spam: "광고·도배", copy: "베낌", other: "그 밖" };
  /**
   * PC 확인 보안 6차 (신고 광고·도배 2가 세지 않은 것까지 합한 수): what counted toward hiding a recipe and all that came, with
   * reasons. "신고 2 (광고·도배 2)" when all counted, "센 신고 1 · 전체 2 (광고·도배 2)" when some went to the operator only.
   */
  function recipeFlagCount(flags) {
    const all = flags.reduce((a, f) => a + f.n, 0);
    const counted = flags.reduce((a, f) => a + (f.counted ?? f.n), 0);
    const why = flags.map((f) => `${esc(RECIPE_FLAGS[f.reason] ?? f.reason)} ${f.n}`).join(", ");
    return `${counted === all ? `신고 ${all}` : `센 신고 ${counted} · 전체 ${all}`} (${why})`;
  }
  const RECIPE_PIECES = { shiftLook: "근무 꾸미기", calTheme: "달력 테마", alarms: "알람 묶음", memoTpl: "메모 틀", hourly: "알바 근무지 규칙",
    field: "현장 단가표 틀", shop: "가게 영업 틀", invest: "투자 원칙·일지 틀", company: "근무 주기·급여 방식",
    bundle: "묶음", layout: "화면 배치", cards: "카드 순서", widget: "위젯 모양", photoLook: "그림 꾸미기", calView: "달력 모양", appFeel: "앱 동작",
    memoLook: "메모장 모양", moneyLook: "가계부 모양", screenLook: "화면 색·배경", ledger: "가계부 틀", parts: "부품 꾸미기", logic: "규칙·단추",
    decor: "꾸밈 조각", tabView: "탭 보기" };
  /** 1.37 PC 확인 (관리자 본문이 JSON이라 읽기 어려움): a 가계부 틀 said as the app's preview says it. */
  function ledgerWords(p) {
    const n = (v) => (Array.isArray(v) ? v.length : 0);
    const yours = (list, key) => (list ?? []).filter((x) => x?.[key]?.input).length;
    return [n(p.costs) && `지출 분류 ${n(p.costs)}개`, n(p.incomes) && `수입 분류 ${n(p.incomes)}개`,
      n(p.budgets) && `예산 ${n(p.budgets)}개${yours(p.budgets, "amount") ? `(받는 분이 넣음 ${yours(p.budgets, "amount")})` : ""}`,
      n(p.envelopes) && `빈 봉투 ${n(p.envelopes)}개`].filter(Boolean).join(", ");
  }
  const RECIPE_ACTS = { approve: "올림", return: "돌려줌", hide: "가림", show: "다시 보임", delete: "지움", official: "공식", showcase: "보여주기", auto: "자동 가림",
    // 1.36 [MKS-01]
    waiting: "출시 대기에 넣음", release: "출시 대기에서 공개", schedule: "공개 예약" };

  /** What a recipe holds, in words: its pieces and the 받는 사람이 정할 칸 (never run, only read). */
  function recipeParts(body) {
    try {
      const r = JSON.parse(body);
      return `${(r.pieces ?? []).map((p) => esc((RECIPE_PIECES[p.kind] ?? p.kind) + (p.kind === "ledger" && ledgerWords(p) ? ` (${ledgerWords(p)})` : ""))).join(" · ")}${r.inputs?.length ? ` · 정할 칸 ${r.inputs.length}개` : ""}`;
    } catch { return ""; }
  }

  // 1.36 장터 네 구간 (앱 마을 › 장터 칩과 같은 이름). 1.39 [MKC-01] 테마 · 탭별 작품 · 도구(부품·규칙·단추).
  const RECIPE_SECTIONS = { theme: "테마", tab: "탭별 작품", part: "도구 · 부품", logic: "도구 · 규칙·단추" };
  const RECIPE_TAB_WORDS = { calendar: "달력", memo: "메모", money: "가계부", pay: "근무·급여", app: "앱 전체", widget: "위젯", alarm: "알람" };

  /**
   * 2.0.0 [RVA-48] 검토 ADMX-08·I24a-04(도약 42): 장터 목록은 50개씩 이어 받아요. 찾기·구간은 서버가 걸러요(받은 것 안에서만 찾던 것).
   */
  const RECIPE_PAGE = 50;
  function recipeListPath(t, offset = 0) {
    const q = (S.recipeQuery ?? "").trim();
    const sec = S.recipeSection ?? "all";
    return `/recipes?tab=${encodeURIComponent(t)}&limit=${RECIPE_PAGE}${offset ? `&offset=${offset}` : ""}${q ? `&q=${encodeURIComponent(q)}` : ""}${sec !== "all" ? `&section=${encodeURIComponent(sec)}` : ""}`;
  }

  function recipesView() {
    const d = S.data.recipes;
    const m = S.data.recipeMonth;
    const edit = can("community", "edit");
    const tab = S.recipeTab ?? "flagged";
    // 2.0.0 [RVA-24] 검토 U24-06·T24-08·HQ-03(조건 1·2): 보기 15개가 칩 다섯 줄로 첫 화면을 채웠어요. 한 줄 옆으로 밀리는 구간(지금 보기는 가운데,
    // 끝 흐림)과 찾기 칸 하나로, 구간 칩은 '거르기' 뒤로. 줄 단추는 주된 것 하나 + ⋯, 709개를 한 번에 그리지 않고 40개씩.
    const sec = S.recipeSection ?? "all";
    const listTab = !["picks", "images", "tools", "requests", "makers", "month", "fonts", "health", "one"].includes(tab);
    const ready = d && (d.tab ?? tab) === tab && !(tab === "one" && d.code !== S.recipeCode);
    const paged = ready && d.total !== undefined;
    const secCounts = ready && listTab && (paged ? Object.keys(d.sections ?? {}).length > 1 : d.items?.some((r) => r.section))
      ? [["all", "모든 구간", paged ? d.sections.all : d.items.length], ...Object.entries(RECIPE_SECTIONS).map(([k, label]) => [k, label, paged ? d.sections[k] ?? 0 : d.items.filter((r) => r.section === k).length])] : null;
    const secChips = secCounts ? `<div class="chips" role="group" aria-label="구간">${secCounts.map(([k, label, n]) =>
      `<button type="button" class="chip pick" data-act="recipe-section" data-key="${k}" aria-pressed="${sec === k}">${esc(label)} ${n}</button>`).join("")}</div>` : "";
    const chips = listHead(`<div class="segmented" role="group" aria-label="보기">${RECIPE_TABS.map(([k, label]) =>
      `<button type="button" class="seg" data-act="recipe-tab" data-key="${k}" aria-pressed="${tab === k}">${esc(label)}</button>`).join("")}</div>`,
      listTab && tab !== "one" ? `<input id="search" type="search" placeholder="이름·코드·만든 분으로 찾기" value="${esc(S.recipeQuery ?? "")}" autocomplete="off" enterkeyhint="search" aria-label="레시피 찾기">` : "",
      { scroll: true, filters: secChips, active: sec !== "all" ? 1 : 0, busy: Boolean(S.recipeQuery) });
    const month = m ? `<p class="list-note">${esc(m.month)} · 새 레시피 ${m.made}개 · ${Object.entries(m.actions ?? {}).map(([k, n]) => `${esc(RECIPE_ACTS[k] ?? k)} ${n}`).join(" · ") || "처리 없음"}</p>` : "";
    if (!ready) return chips + skeleton(2);
    if (tab === "picks") return chips + recipePicksView(d, edit);
    if (tab === "images") return chips + recipeImagesView(d, edit);
    // 1.36 [GRW-05] 기본 도구 후보·모두의 도구 (문의 › 스튜디오 요구에도 같은 목록).
    if (tab === "tools") return chips + V.toolsBlock(d, edit);
    if (tab === "requests") return chips + recipeRequestsView(d, edit);
    if (tab === "makers") return chips + recipeMakersView(d, edit);
    if (tab === "month") return chips + recipeMonthView(d, edit);
    if (tab === "fonts") return chips + recipeFontsView(d, edit);
    // 2.0.0 [RVA-56] 검토 SX-04: 빌드마다 작품 전체를 넣어 찍고 옛 앱으로 열어 본 결과.
    if (tab === "health") return chips + recipeHealthView(d, edit);
    const help = {
      queue: "1.35부터 레시피는 바로 광장에 올라가요. 그 전에 검토를 기다리던 것만 여기 남아 있어요. 올리거나 고칠 점을 적어 돌려줘요.",
      flagged: "신고가 들어온 레시피예요. 다른 회선의 신고가 3건 쌓이면 저절로 가려지고 여기서 다시 보일지 정해요. 새 기기(3일 안)의 신고와 같은 회선의 두 번째 신고는 여기에만 보이고 가리기에는 세지 않아요.",
      hidden: "가린 레시피는 90일 뒤 지워져요.",
      // 1.39 판 정리
      retired: "새 판이 나와 장터에서 내린 운영자 작품이에요. 목록·찾기·추천에는 안 나오지만 이미 넣은 분은 그대로 쓰고 코드로 열 수 있어요. 지우지 않아요. 아래에 정리 계획(market-…-refresh-plan.json)을 붙여 넣어 몇 개인지 먼저 보고 숨기거나, 잘못 숨긴 것을 한 번에 되돌려요.",
      visible: "광장에 보이는 레시피예요. 새로 올라온 것이 위에 와요. 이상한 것은 가리기로 내려요.",
      official: "운영자가 올린 시작 레시피와 공공 자료 레시피예요.",
      // [RCX-32]
      one: `작품 ${S.recipeCode ?? ""} 하나만 보고 있어요. 가리기·지우기도 여기서 바로 할 수 있어요.`,
      // 1.36 [MKS-01]
      waiting: "앱 업데이트와 함께 장터에 나올 운영자 작품이에요. 아직 아무에게도 안 보여요. 묶음마다 ‘지금 공개’하거나 공개할 때를 예약해요. ‘이 판부터’를 적은 작품은 공개한 뒤에도 그 판 이상 앱에만 보여서, 업데이트한 분부터 바로 보고 옛 앱에는 안 보여요.",
      showcase: "장터로 이런 것까지 된다는 걸 보여 주는 레시피예요. 근무 유형마다 마을 › 장터 맨 위 ‘이런 것도 만들 수 있어요’에 크게 보여요. 아래에서 showcase-recipes.json을 붙여 넣거나, 장터의 좋은 레시피를 ‘보여주기에 넣기’로 더해요.",
    }[tab];
    const q = (S.recipeQuery ?? "").trim().toLowerCase();
    const found = paged ? d.items : (sec === "all" ? d.items : d.items.filter((r) => r.section === sec))
      .filter((r) => !q || [r.title, r.code, r.nick, r.summary, r.batch].some((x) => String(x ?? "").toLowerCase().includes(q)));
    const limit = S.recipeLimit ?? 40;
    const shown = paged ? d.items : found.slice(0, limit);
    const foundN = paged ? d.total : found.length;
    // ⋯ 안의 단추: 주된 것(상태마다 하나) 말고 나머지.
    const rowMore = (r) => {
      const items = [
        r.state === "pending" ? `<button class="btn small" data-act="recipe-return" data-id="${r.code}">돌려주기</button>` : "",
        r.official ? `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="unofficial">공식 빼기</button>` : `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="official">공식 표시</button>`,
        r.state === "visible" && r.scope === "public" ? `<button class="btn small" data-act="recipe-showcase" data-id="${r.code}" data-key="${r.showcase ? "off" : "on"}">${r.showcase ? "보여주기에서 빼기" : "보여주기에 넣기"}</button>` : "",
        r.maker && r.maker !== "operator" ? `<button class="btn small" data-act="recipe-helper" data-id="${esc(r.maker)}">도우미 배지</button>` : "",
        `<button class="btn small danger" data-act="recipe-delete" data-id="${r.code}">지우기</button>`,
      ].filter(Boolean).join("");
      return moreMenu(r.title, items);
    };
    const mainAct = (r) => r.state === "pending" ? `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="approve">올리기</button>`
      : r.state === "waiting" ? `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="show">이것만 지금 공개</button>`
        : r.state === "hidden" ? `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="show">다시 보이기</button>`
          : `<button class="btn small" data-act="recipe-do" data-id="${r.code}" data-key="hide">가리기</button>`;
    const rows = shown.length ? `<ul class="rows recipe-rows">${shown.map((r) => `<li><div class="row"><span class="row-main">
        <b>${esc(r.cover?.emoji ?? "")} ${esc(r.title)}</b>${r.section ? ` <span class="pill">${esc(RECIPE_SECTIONS[r.section] ?? r.section)}</span>` : ""} <small>${r.tabs?.length ? `${r.tabs.map((t) => esc(RECIPE_TAB_WORDS[t] ?? t)).join("·")} · ` : ""}${esc(r.code)} · v${r.version} · ${esc(r.nick ?? "이름 없음")}${r.late ? ' · <span class="pill warn">하루 넘음</span>' : ""}</small>
        ${r.summary ? `<small>${esc(r.summary)}</small>` : ""}<small>${recipeParts(r.body)}</small>
        ${r.parent ? `<small>고친 판 · 원본 <button type="button" class="linkish" data-act="recipe-find" data-id="${esc(r.parent)}">${esc(r.parent)}</button></small>` : ""}
        <small>좋아요 ${r.likes} · 안좋아요 ${r.dislikes} · 쓰는 분 ${r.users}${r.flags?.length ? ` · ${recipeFlagCount(r.flags)}` : ""}</small>
        ${r.batch || r.releaseAt || r.minApp ? `<small>${[r.batch ? `묶음 ${esc(r.batch)}` : "", r.releaseAt ? `공개 예약 ${esc(new Date(r.releaseAt).toLocaleString("ko-KR"))}` : "", r.minApp ? `앱 판 ${r.minApp} 이상만` : ""].filter(Boolean).join(" · ")}</small>` : ""}
        ${r.quiet ? `<small data-quiet>운영자에게만 온 신고: ${[r.quiet.fresh ? `새 기기 ${r.quiet.fresh}` : "", r.quiet.net ? `같은 회선 ${r.quiet.net}` : ""].filter(Boolean).join(" · ")} (가리기에 세지 않아요)</small>` : ""}
        ${r.note ? `<small>돌려준 메모: ${esc(r.note)}</small>` : ""}${r.hidden ? `<small>가린 이유: ${esc({ admin: "운영자가 가림", flags: "신고가 쌓여 저절로 가림", retired: "판 정리로 내림 · 넣은 분은 계속 써요" }[r.hidden] ?? r.hidden)}${r.retiredBelow ? ` · 앱 판 ${r.retiredBelow} 미만에는 계속 보여요` : ""}</small>` : ""}
        <details><summary>본문 보기</summary><pre class="mono">${esc(JSON.stringify(JSON.parse(r.body), null, 1))}</pre></details></span>
        ${edit ? `<span class="row-side row-acts">${mainAct(r)}${rowMore(r)}</span>` : ""}</div></li>`).join("")}</ul>
      ${foundN > shown.length ? `<div class="more"><button class="btn" data-act="recipe-more">더 보기 <span class="meta">${shown.length}/${foundN}</span></button></div>` : ""}`
      : q ? empty("찾는 레시피가 없어요", "이름·코드·만든 분 이름으로 찾아요.") : empty("여기 있는 레시피가 없어요", tab === "queue" ? "검토를 기다리는 레시피가 없어요." : "");
    const release = tab === "waiting" ? recipeReleaseView(S.data.recipeRelease, edit) + materialsView(S.data.materials, edit)
      : tab === "retired" && edit ? recipeRetireView(S.recipeRetire) : "";
    const upload = edit && (tab === "official" || tab === "showcase" || tab === "waiting") ? `<form data-form="recipe-upload" class="card"><h3>${tab === "showcase" ? "보여주기 레시피 올리기" : tab === "waiting" ? "출시 작품 올리기" : "시작 레시피·공개 자료 올리기"}</h3>
      <p class="hint">앱의 ‘올라갈 내용 전부 보기’에 나오는 레시피 글(JSON)이나, 시작 레시피 파일(starter-recipes.json) 전체를 붙여 넣어요. 공공 자료라면 설명에 출처(기관·자료 이름·날짜)를 꼭 적어요. 공공누리 4유형 자료는 쓰지 않아요.</p>
      <textarea name="recipe" rows="8"${tab === "waiting" ? "" : " required"}></textarea>
      ${tab === "waiting" ? `<p class="label">또는 파일로 <span class="meta">(docs/recipes/market-….json, 휴대폰에서 긴 글을 붙여 넣기 어려울 때)</span></p>${filePick("file", ".json,application/json")}` : ""}
      ${tab === "showcase" ? `<p class="hint">showcase-recipes.json 전체를 붙여 넣으면 30개가 한 번에 올라가요. 하나만 붙여 넣어도 보여주기로 올라가요.</p><input type="hidden" name="showcase" value="on">`
        : tab === "waiting" ? "" : `<label class="switch"><input type="checkbox" name="publicData"><span class="track" aria-hidden="true"></span><span>공공 자료 레시피 (공개 자료 탭)</span></label>`}
      ${recipeHoldFields(tab === "waiting")}
      <div class="actions"><button class="btn primary" type="submit">올리기</button></div></form>` : "";
    const log = S.recipeLog ? (S.data.recipeLog ? `<h3>처리 기록</h3><ul class="rows">${S.data.recipeLog.items.slice(0, 100).map((a) =>
      `<li><div class="row"><span class="row-main"><b>${esc(RECIPE_ACTS[a.action] ?? a.action)}</b> <small>${esc(a.code)} · ${esc(a.actor ?? "")} · ${esc(ago(a.at))}${a.reason ? ` · ${esc(a.reason)}` : ""}</small></span></div></li>`).join("")}</ul>` : skeleton(1))
      : `<button class="btn small ghost" data-act="recipe-log">처리 기록 보기</button>`;
    // 1.36 [GRW-12] 작품 하나를 연 곳(사진 검토·고친 판)으로 돌아가기.
    const back = tab === "one" ? `<p><button type="button" class="btn small" data-act="recipe-tab" data-key="${esc(S.recipeBack ?? "flagged")}">← 돌아가기 · ${esc(RECIPE_TABS.find(([k]) => k === (S.recipeBack ?? "flagged"))?.[1] ?? "목록")}</button></p>` : "";
    const ph = tab === "one" ? S.recipePhoto : null;
    const photo = ph ? `<div class="card"><div class="row"><span class="row-main">
        <button type="button" class="linkish" data-act="photo-big" aria-label="사진 크게 보기"><img src="data:image/webp;base64,${esc(ph.data)}" alt="보러 온 사진" width="128" height="128" class="cover-img"></button>
        <b>보러 온 사진${ph.whyName ? ` <span class="pill ${ph.state === "held" ? "bad" : "warn"}">${esc(ph.whyName)}</span>` : ""}</b>
        ${ph.done ? `<small>${ph.done === "ok" ? "보이게 했어요" : "가렸어요"}</small>` : ""}</span>
        ${edit && !ph.done ? `<span class="row-side"><button class="btn small primary" data-act="recipe-image" data-id="${esc(ph.id)}" data-key="ok">보이기</button>
          <button class="btn small danger" data-act="recipe-image" data-id="${esc(ph.id)}" data-key="hide">가리기</button></span>` : ""}</div></div>` : "";
    // 설명은 목록 아래 접힌 한 줄(첫 문장), 이달 숫자와 처리 기록도 아래로.
    const [lead, ...restHelp] = String(help ?? "").split(/(?<=요\.) /);
    const tip = restHelp.length ? `<details class="tip" data-key="recipe-how-${tab}"><summary>${esc(lead)}</summary><p class="hint">${esc(restHelp.join(" "))}</p></details>` : lead ? `<p class="list-note">${esc(lead)}</p>` : "";
    return `${chips}${back}${photo}${tab === "one" ? "" : release}${rows}<div class="list-foot">${tip}${tab === "one" ? "" : month}</div>${upload}${tab === "one" ? "" : log}`;
  }

  /**
   * 2.0.0 [RVA-56] 검토 SX-04 장터 건강: 빌드가 바뀔 때마다 CI가 작품 전체를 넣어 찍고 옛 앱 core로 열어 본 결과 한 장.
   * 깨진 작품(넘어짐·옛 앱에서 안 열림·서버가 거절·되돌린 뒤 달력이 다름)이 먼저, 빨강은 그 머리 하나(조건 6). 달라진 작품은 실패가 아니라
   * 사람이 한 번 보는 목록이라 접어 둬요. 결과가 없거나 14일이 넘으면 그 말이 먼저예요.
   */
  function recipeHealthView(d, edit) {
    const last = d?.last;
    const kinds = d?.kinds ?? {};
    const form = edit ? `<details class="card" data-key="market-health-upload"${last ? "" : " open"}><summary><b>결과 올리기</b> <span class="meta">CI market-shots의 market-health.json</span></summary>
      <form data-form="market-health" class="mh-form"><p class="hint">GitHub Actions › 실행 › market-shots 묶음에 든 market-health.json을 골라요. 작품 이름·제목·까닭 한 줄·빌드뿐이고 사람 정보는 없어요.</p>
      ${filePick("file", ".json,application/json", { required: true })}
      <div class="actions"><button class="btn primary" type="submit">올리기</button></div></form></details>` : "";
    if (!last) return empty("아직 장터 건강 결과가 없어요", "빌드가 바뀌면 CI가 장터 작품 전체를 넣어 찍고 옛 앱으로 열어 봐요. 그 결과를 올리면 여기와 운영 장부에 보여요.") + form;
    const n = last.broken?.length ?? 0;
    const when = `${esc(ago(last.at))}${last.by ? ` · ${esc(last.by)}` : ""}`;
    // 깨진 수는 아래 빨간 머리 하나가 말해요(조건 6). 위 카드는 무엇을 얼마나 봤는지.
    const head = `<div class="card mh-head"><p class="mh-sum"><b>작품 ${last.works}개를 넣어 봤어요</b>${n ? "" : ` <span class="pill ok">깨진 작품 없음</span>`}</p>
      <p class="meta">옛 앱 목록 ${last.listed}개 중 ${last.opened}개 열림 · 달라짐 ${last.changed?.length ?? 0}개</p>
      <p class="meta">빌드 <code>${esc(String(last.build).slice(0, 12))}</code> · ${when}${d.stale ? ` · <span class="pill warn">14일 넘게 새 결과 없음</span>` : ""}</p></div>`;
    const broken = n ? `<ul class="rows mh-rows"><li class="lg-group"><span class="pill bad">깨진 작품 ${n}</span><small>다음 출시 전에 고쳐요 · 운영 장부에도 같은 줄이 있어요</small></li>${last.broken.map((b) =>
      `<li><div class="row"><span class="row-main"><b>${esc(b.title || b.key)}</b> <span class="pill">${esc(kinds[b.kind] ?? b.kind)}</span>
        <small>${esc(b.key)}${b.app ? ` · 옛 앱 ${b.app}` : ""}</small>${b.why ? `<small>${esc(b.why)}</small>` : ""}</span></div></li>`).join("")}</ul>` : "";
    const list = (rows, title, note, line) => (rows?.length ? `<details class="card" data-key="mh-${title}"><summary><b>${esc(title)} ${rows.length}</b> <span class="meta">${esc(note)}</span></summary>
      <ul class="rows">${rows.map(line).join("")}</ul></details>` : "");
    const changed = list(last.changed, "달라진 작품", "실패는 아니에요 · 일부러 바꾼 화면인지 한 번 봐요", (c) =>
      `<li><div class="row"><span class="row-main"><b>${esc(c.title || c.key)}</b> <small>${esc(c.key)} · 차이 ${c.d}</small></span></div></li>`);
    const same = list(last.unchanged, "효과가 사라졌을 수 있는 작품", "넣기 전과 사진이 같아요 · 작품이 아무것도 안 바꾸는지 봐요", (c) =>
      `<li><div class="row"><span class="row-main"><b>${esc(c.title || c.key)}</b> <small>${esc(c.key)}</small></span></div></li>`);
    const runs = (d.runs ?? []).length > 1 ? `<details class="card" data-key="mh-runs"><summary><b>지난 결과 ${d.runs.length}</b></summary><ul class="rows">${d.runs.map((r) =>
      `<li><div class="row"><span class="row-main"><b><code>${esc(String(r.build).slice(0, 12))}</code></b> <small>${esc(ago(r.at))} · 작품 ${r.works} · 깨짐 ${r.broken} · 달라짐 ${r.changed}</small></span></div></li>`).join("")}</ul></details>` : "";
    return `<div class="mh">${head}${broken}${changed}${same}${runs}${form}</div>`;
  }

  /** 1.36 [MKS-01] 출시 대기로 넣기: hold, 이 판부터 (versionCode), 묶음 이름, 같은 이름은 새 판으로. */
  function recipeHoldFields(on) {
    return `<fieldset class="card"><legend>출시 대기</legend>
      <label class="switch"><input type="checkbox" name="hold"${on ? " checked" : ""}><span class="track" aria-hidden="true"></span><span>출시 대기로 넣기 (공개 전까지 아무에게도 안 보여요)</span></label>
      <label>묶음 이름 <input name="batch" maxlength="40" placeholder="예: ${esc(serverVersion() || "2.0.0")} 출시 작품" value="${on ? `${esc(serverVersion())} 출시 작품`.trim() : ""}"></label>
      <label>이 앱 판부터 보이기 (versionCode, 비우면 모든 앱) <input name="minApp" inputmode="numeric" pattern="[0-9]*" maxlength="6"></label>
      <label class="switch"><input type="checkbox" name="replace"${on ? " checked" : ""}><span class="track" aria-hidden="true"></span><span>같은 이름의 운영자 작품은 새 판으로 고치기 (다시 넣어도 두 개가 되지 않아요)</span></label>
      <p class="hint">파일 안의 항목에 hold·batch·minApp·replace가 있으면 그것이 먼저예요.</p></fieldset>`;
  }

  /** 1.38 [STM-10] 밤낮 재료 묶음: 몇 개·공개 상태·무료 수, 공개·숨기기, 무료·광고 나누기(광고로 연 묶음은 30일, 프로는 모두). */
  function materialsView(d, edit, bare = false) {
    // [RVA-24] 재료 화면에서는 화면 이름이 '스튜디오 재료'라 '밤낮 재료' 제목 줄을 빼요(레시피 › 출시 대기 안에서는 그대로).
    const h3 = bare ? "" : "<h3>밤낮 재료</h3>";
    if (!d) return "";
    if (!d.items.length) return `${h3}<p class="hint">넣은 재료가 없어요. 아래 ‘재료 파일 올리기’로 넣어요.</p>${edit ? materialUploadForm() : ""}`;
    const packs = new Map();
    for (const m of d.items) {
      const p = packs.get(m.pack) ?? { kind: m.kind, n: 0, ok: 0, waiting: 0, hidden: 0, free: 0 };
      p.n++; p[m.state] = (p[m.state] ?? 0) + 1; if (m.free) p.free++;
      packs.set(m.pack, p);
    }
    const c = d.counts ?? {};
    const freeOf = (p) => (p.free === p.n ? "all" : p.free === 0 ? "none" : "third");
    const row = ([name, p]) => `<li><form data-form="material-free" data-key="${esc(name)}" class="row"><span class="row-main"><b>${esc(name)}</b>
        <small>${p.n}개 · 공개 ${p.ok}${p.waiting ? ` · 대기 ${p.waiting}` : ""}${p.hidden ? ` · 숨김 ${p.hidden}` : ""} · 무료 ${p.free}</small></span>
        ${edit ? `<span class="row-side inline"><select name="mode" aria-label="${esc(name)} 무료·광고">${[["all", "모두 무료"], ["third", "3분의 1 무료"], ["none", "모두 광고·프로"]]
          .map(([k, l]) => `<option value="${k}"${freeOf(p) === k ? " selected" : ""}>${l}</option>`).join("")}</select>
          <button class="btn small" type="submit">바꾸기</button>
          ${p.ok < p.n ? `<button class="btn small primary" type="button" data-act="material-state" data-id="${esc(name)}" data-key="ok">공개</button>`
            : `<button class="btn small" type="button" data-act="material-state" data-id="${esc(name)}" data-key="hidden">숨기기</button>`}</span>` : ""}</form></li>`;
    const group = (kind, label) => {
      const list = [...packs].filter(([, p]) => p.kind === kind);
      return list.length ? `<details data-key="materials-${kind}"><summary>${label} ${list.length}묶음 · ${list.reduce((n, [, p]) => n + p.n, 0)}개</summary><ul class="rows">${list.map(row).join("")}</ul></details>` : "";
    };
    return `${h3}<p class="meta">공개 ${c.ok ?? 0} · 대기 ${c.waiting ?? 0} · 숨김 ${c.hidden ?? 0}</p>
      ${group("icon", "색 아이콘")}${group("bg", "배경")}${edit ? materialUploadForm() : ""}`;
  }

  /**
   * 1.39 E11 재료 파일 올리기: the files `tools/materials-seed.mjs --files` writes (재료-01.json …), picked here with this login, so the real
   * server gets its materials without a password in any tool. They go in as 대기 (공개는 위 묶음마다); the same picture again only renames.
   */
  function materialUploadForm() {
    return `<form data-form="material-upload" class="card"><h3>재료 파일 올리기</h3>
      <p class="hint">tools/materials-seed.mjs --files로 만든 재료-01.json … 을 모두 골라요. 대기로 들어가고, 공개는 위 묶음마다 해요. 같은 그림을 다시 올리면 이름·순서만 바뀌어요.</p>
      ${filePick("files", ".json,application/json", { multiple: true, required: true })}
      <div class="actions"><button class="btn primary" type="submit">올리기</button></div></form>`;
  }


  /** 1.36 [MKS-01] 출시 대기 묶음: 몇 개, 예약, 지금 공개. */
  /**
   * 1.39 판 정리: 계획 파일({"hide":[{title, why}]}, 제목 목록 줄마다, 코드)을 붙여 넣거나 골라 '몇 개인지 보기'(dry) → 숨기기, 또는 되돌리기.
   * [last] 마지막으로 본 결과(찾은 수·없는 제목)를 보여 줘요.
   */
  function recipeRetireView(last) {
    const said = last ? `<p class="hint" data-retire-said>${esc(last.say)}${last.missing?.length ? ` · 못 찾은 것 ${last.missing.length}개: ${esc(last.missing.slice(0, 20).join(", "))}${last.missing.length > 20 ? " …" : ""}` : ""}</p>` : "";
    return `<form data-form="recipe-retire" class="card"><h3>판 정리 붙여 넣기</h3>
      <p class="hint">정리 계획 JSON 전체, 또는 제목(이나 작품 코드)을 한 줄에 하나씩. 운영자 작품만 찾고 사용자 작품은 건드리지 않아요. 한 번에 1,200개까지.</p>
      <textarea name="plan" rows="6" required></textarea>
      <p class="label">또는 파일로</p>${filePick("file", ".json,.txt,application/json,text/plain")}
      <label>메모 <input name="note" maxlength="200" placeholder="1.39 장터 정리"></label>
      <label>옛 앱에는 남기기: 이 앱 판 미만에는 계속 보여요(계획 파일의 belowApp이 먼저) <input name="below" inputmode="numeric" pattern="\\d{1,6}" placeholder="53"></label>
      <div class="actions"><button class="btn" type="submit" name="go" value="dry">몇 개인지 보기</button>
        <button class="btn primary" type="submit" name="go" value="hide">숨기기</button>
        <button class="btn" type="submit" name="go" value="show">되돌리기</button></div>${said}</form>`;
  }

  /**
   * The titles and codes in what was pasted (a plan file {hide:[{title, code?, belowApp?}]}, a list of strings, or lines), grouped by
   * belowApp (작품 갈래: '그 판 미만 앱에는 계속 보임'); [below] is for those that say none.
   */
  function retireGroups(text, below) {
    const groups = new Map();
    const add = (x, b) => {
      const t = String(x ?? "").trim();
      if (!t) return;
      const k = Number.isInteger(b) && b > 0 ? b : below;
      if (!groups.has(k)) groups.set(k, { belowApp: k, titles: [], codes: [] });
      (/^[2-9A-HJ-NP-Z]{8}$/.test(t) ? groups.get(k).codes : groups.get(k).titles).push(t);
    };
    let j = null;
    try { j = JSON.parse(text); } catch { j = null; }
    const list = Array.isArray(j) ? j : Array.isArray(j?.hide) ? j.hide : null;
    if (list) for (const x of list) { if (typeof x === "string") add(x); else add(x?.code || x?.title, x?.belowApp); }
    else for (const line of String(text).split(/\r?\n/)) add(line);
    return [...groups.values()];
  }

  function recipeReleaseView(d, edit) {
    if (!d) return skeleton(1);
    if (!d.items.length) return `<p class="hint">출시 대기 중인 작품이 없어요.</p>`;
    return `<ul class="rows">${d.items.map((b) => `<li><form data-form="recipe-release" data-key="${esc(b.batch ?? "")}" data-n="${b.n}" class="row">
      <span class="row-main"><b>${esc(b.batch ?? "묶음 없음")}</b><small>${b.n}개 기다림${b.minApp ? ` · 앱 판 ${b.minApp} 이상만` : ""}${b.releaseAt ? ` · 공개 예약 ${esc(new Date(b.releaseAt).toLocaleString("ko-KR"))}` : " · 예약 없음"}</small>
      ${edit ? `<label>공개할 때 <input type="datetime-local" name="at"></label>` : ""}</span>
      ${edit ? `<span class="row-side"><button class="btn small" type="submit" name="do" value="schedule">예약</button>
        ${b.releaseAt ? `<button class="btn small" type="submit" name="do" value="unschedule">예약 풀기</button>` : ""}
        <button class="btn small primary" type="submit" name="do" value="now">지금 공개</button></span>` : ""}</form></li>`).join("")}</ul>`;
  }

  /** [RCX-08] 1.35 이번 주 고른 것: up to three codes for each kind of work; last week's stay until a new pick. */
  function recipePicksView(d, edit) {
    const by = (k) => d.items.filter((i) => i.style === k).map((i) => i.code);
    return `<p class="hint">${esc(d.week)} 주. 근무 유형마다 레시피 3개까지 골라 광장 맨 위에 보여요. 새로 고르기 전까지 지난주 것이 그대로 보여요. 광장에 보이는 레시피의 코드(8자)를 넣어요.</p>
      <ul class="rows">${RECIPE_STYLES.map(([k, label]) => `<li><form data-form="recipe-picks" data-key="${k}" class="row">
        <span class="row-main"><b>${esc(label)}</b><small>${by(k).length ? esc(by(k).join(", ")) : "이번 주 고른 것 없음"}</small>
        ${edit ? `<input name="codes" aria-label="${esc(label)} 레시피 코드" placeholder="코드 3개까지, 쉼표로" value="${esc(by(k).join(", "))}">` : ""}</span>
        ${edit ? `<span class="row-side"><button class="btn small primary" type="submit">저장</button></span>` : ""}</form></li>`).join("")}</ul>`;
  }

  /** [RCX-15] 1.35 그림 표지: shown to others only after a look here. */
  // 1.36 [GRW-08] 사진 세 겹: 운영자가 보는 것은 새싹 첫 사진·자동 검사에 걸린 것·신고로 먼저 가린 것뿐이에요.
  const PHOTO_FLAG_NAMES = { nude: "성적인 사진", violence: "폭력·잔인", personal: "개인정보", copy: "남의 저작물", other: "그 밖" };
  function recipeImagesView(d, edit) {
    const c = d.counts ?? {};
    const rules = d.rules ?? { lookFirst: 3, flagsToHold: 2 };
    const head = `<p class="hint">세 겹으로 봐요. ① 올릴 때 자동 검사(설정되어 있으면) ② 새싹은 첫 ${rules.lookFirst}장만 여기서 보고, 그 뒤와 이웃·단골은 바로 보여요 ③ 신고 ${rules.flagsToHold}번이면 먼저 가려져 여기로 와요.
      얼굴·개인정보·성적인 사진·남의 저작물이면 가리기(쓰기 제한 한 번), 괜찮으면 보이기(신고는 비워요).</p>
      <p class="meta">새싹 첫 사진 ${c.pending ?? 0} · 먼저 가린 것 ${c.held ?? 0}</p>`;
    if (!d.items.length) return `${head}${empty("볼 사진이 없어요", "새싹의 첫 사진, 자동 검사에 걸린 사진, 신고로 먼저 가린 사진이 여기 와요.")}`;
    return `${head}<ul class="rows">${d.items.map((i) => `<li><div class="row"><span class="row-main">
        <img src="data:image/webp;base64,${esc(i.data)}" alt="올린 사진" width="128" height="128" class="cover-img">
        <b>${i.whyName ? `<span class="pill ${i.state === "held" ? "bad" : "warn"}">${esc(i.whyName)}</span>` : ""}${i.state === "held" ? ' <span class="pill">다른 분께 가려짐</span>' : ""}</b>
        <small>${i.uses?.length ? `${i.uses.map((u, k) => `${k ? "" : "작품 "}${esc(u.title)} (<button type="button" class="linkish" data-act="recipe-open" data-id="${esc(u.code)}" data-key="${esc(i.id)}" aria-label="작품 ${esc(u.title)} 열기">${esc(u.code)} 열기</button>)`).join(" · ")}${i.uses.length > 1 ? ` · 같은 사진 ${i.uses.length}곳` : ""}` : "아직 작품에 쓰이지 않음"} · 올린 분 ${esc(i.trustName ?? "새싹")}</small>
        <small>${i.flags ? `신고 ${i.flags}번 (${Object.entries(i.reasons ?? {}).map(([k, n]) => `${esc(PHOTO_FLAG_NAMES[k] ?? k)} ${n}`).join(", ")}) · ` : ""}자동 검사 ${esc({ pass: "통과", block: "걸림" }[i.checked] ?? (rules.checkOn ? "안 함(늦거나 답이 분명하지 않음)" : "안 함(검사 모델 설정 전)"))} · ${Math.round(i.bytes / 1024)}KB · ${esc(ago(i.at))}</small></span>
        ${edit ? `<span class="row-side"><button class="btn small primary" data-act="recipe-image" data-id="${i.id}" data-key="ok">보이기</button>
          <button class="btn small danger" data-act="recipe-image" data-id="${i.id}" data-key="hide">가리기</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }

  /** [PRF-06] 1.36 프로필 신고: 사칭·나쁜 소개. 3건이면 소개가 먼저 가려져요. Who reported is never shown. */
  function recipeMakersView(d, edit) {
    if (!d.items.length) return empty("신고된 프로필이 없어요", "앱의 프로필 › ⋮ › 신고하기로 들어온 것이 여기 와요.");
    const why = { impersonate: "사칭", bio: "나쁜 소개", spam: "광고·도배", other: "그 밖의 것" };
    return `<p class="hint">소개만 문제면 소개를 가리고, 사칭이면 프로필을 닫아요. 닫힌 프로필은 다른 분이 볼 수도 팔로우할 수도 없고, 작품은 장터에 남아요(작품은 레시피 탭에서 따로 가려요). 문제없으면 신고를 비워요.</p>
      <ul class="rows">${d.items.map((m) => `<li><div class="row"><span class="row-main"><b>${esc(m.face ?? "🙂")} ${esc(m.nick ?? "이름 없음")}</b>
        <small>${esc(m.maker)} · 신고 ${m.flags}건 · ${Object.entries(m.reasons ?? {}).map(([k, n]) => `${esc(why[k] ?? k)} ${n}`).join(", ")} · ${esc(ago(m.at))}</small>
        <small>소개: ${m.bio ? esc(m.bio) : "없음"}${m.bioHidden ? ` <span class="pill warn">${m.bioHiddenBy === "operator" ? "운영자가 소개 가림" : "신고로 소개 가림"}</span>` : ""}${m.closed ? ' <span class="pill warn">프로필 닫힘</span>' : ""}</small></span>
        ${edit ? `<span class="row-side">
          <button class="btn small" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="${m.bioHidden ? "bio-show" : "bio-hide"}">${m.bioHidden ? "소개 다시 보이기" : "소개 가리기"}</button>
          <button class="btn small ${m.closed ? "" : "danger"}" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="${m.closed ? "open" : "close"}">${m.closed ? "프로필 다시 열기" : "프로필 닫기"}</button>
          <button class="btn small" data-act="recipe-maker" data-id="${esc(m.maker)}" data-key="clear">신고 비우기</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }

  /** [PRF-10] 1.36 이달의 제작자: up to three maker ids for each kind of work; with none, the most put-on makers of the month show. */
  function recipeMonthView(d, edit) {
    const by = (k) => d.items.filter((i) => i.style === k).map((i) => i.maker);
    return `<p class="hint">${esc(d.month)}. 근무 유형마다 3명까지 골라 마을 맨 위(팔로우한 분이 없는 분)에 보여요. 비워 두면 이달에 가장 많이 넣은 작품의 만든 분이 저절로 보여요. 프로필 id(12자)는 레시피 탭의 ‘도우미 배지’ 옆이나 앱의 프로필 링크 끝에 있어요.</p>
      <ul class="rows">${RECIPE_STYLES.map(([k, label]) => `<li><form data-form="recipe-month" data-key="${k}" class="row">
        <span class="row-main"><b>${esc(label)}</b><small>${by(k).length ? esc(by(k).join(", ")) : "고른 분 없음 (저절로)"}</small>
        ${edit ? `<input name="makers" aria-label="${esc(label)} 제작자 id" placeholder="프로필 id 3개까지, 쉼표로" value="${esc(by(k).join(", "))}">` : ""}</span>
        ${edit ? `<span class="row-side"><button class="btn small primary" type="submit">저장</button></span>` : ""}</form></li>`).join("")}</ul>`;
  }

  /**
   * [MAT-06] 1.36 나눈 글꼴: 신고 셋이면 저절로 내려가요. 라이선스가 맞으면 다시 올리고, 아니면 내린 채로 둬요.
   * 1.36 [SEC-10] 보안 점검 H3·M7·M8: 나누는 글꼴은 모두 여기서 본 뒤에 다른 분께 가요. 글꼴이 스스로 밝힌 이름·만든 곳·라이선스 글을 함께 보여요.
   */
  function recipeFontsView(d, edit) {
    const state = d.state ?? "pending";
    const names = { pending: "확인 기다림", reported: "신고로 내려감", hidden: "내린 것", ok: "나누는 중" };
    const whys = { license: "라이선스가 의심돼요", broken: "글자가 깨져요", bad: "불쾌한 모양" };
    const sub = `<div class="chips" role="group" aria-label="글꼴 상태">${Object.entries(names).map(([k, label]) =>
      `<button type="button" class="chip pick" data-act="font-state" data-key="${k}" aria-pressed="${state === k}">${esc(label)} ${d.counts?.[k] ?? 0}</button>`).join("")}</div>`;
    if (!d.items.length) return sub + empty({ pending: "확인을 기다리는 글꼴이 없어요", reported: "신고로 내려간 글꼴이 없어요" }[state] ?? "글꼴이 없어요", "앱에서 공개 글꼴(OFL 등)이라고 밝히고 레시피와 함께 나눈 글꼴이 여기 보여요.");
    const says = { ofl: "글꼴 안에 OFL이라고 적혀 있어요", apache: "글꼴 안에 Apache라고 적혀 있어요" };
    // 1.36 PC 12차 ('신고 0 · 글자가 깨져요 1'로 숫자가 맞지 않음): every report, its reasons in brackets, and how many count toward taking
    // it down when that differs (새로 깐 휴대폰·같은 회선의 둘째 신고는 내림에 세지 않아요, 보안 점검 M3).
    const reportLine = (f) => {
      const all = Object.values(f.whys ?? {}).reduce((a, n) => a + n, 0);
      const reasons = Object.entries(f.whys ?? {}).map(([k, n]) => `${esc(whys[k] ?? k)} ${n}`).join(" · ");
      return `신고 ${Math.max(all, f.reports)}${reasons ? `(${reasons})` : ""}${all > f.reports ? ` · 내림에 세는 것 ${f.reports}` : ""}`;
    };
    const own = (f) => {
      const i = f.info ?? {};
      const bits = [i.family && `이름 ${i.family}`, i.maker && `만든 곳 ${i.maker}`, i.designer && `디자이너 ${i.designer}`, i.vendor && `회사 코드 ${i.vendor}`].filter(Boolean);
      return `<small>${bits.length ? esc(bits.join(" · ")) : "글꼴 안에 이름 정보가 없어요"}</small>
        <small>${f.says ? `<span class="pill ok">${esc(says[f.says])}</span>` : '<span class="pill warn">글꼴 안에 OFL·Apache 글이 없어요</span>'}${i.license ? ` ${esc(i.license.slice(0, 160))}` : ""}${i.licenseUrl ? ` · ${esc(i.licenseUrl)}` : ""}</small>
        ${i.copyright ? `<small>${esc(i.copyright.slice(0, 160))}</small>` : ""}
        ${like(f)}`;
    };
    // 1.36 [SEC-10] M8 (PC 7차): 이미 나누는 글꼴과 같은 글자 모양이거나 같은 이름·저작권이면 나란히 보여요. 크기 차이가 작으면 이름만 바꾼 복사본일 수 있어요.
    const likeWhy = { glyphs: "글자 모양이 같은", family: "글꼴 이름이 같은", copyright: "저작권 글이 같은" };
    const bytesDiff = (n) => n === 0 ? "크기 같음" : `${n > 0 ? "+" : "−"}${Math.abs(n) < 1024 ? `${Math.abs(n)}B` : `${(Math.abs(n) / 1024).toFixed(1)}KB`}`;
    const like = (f) => (f.like ?? []).length ? `<small class="like">${f.like.map((l) =>
      `<span class="pill ${l.why === "glyphs" ? "ok" : "warn"}">${esc(likeWhy[l.why] ?? "비슷한")} 글꼴을 나누는 중: ${esc(l.name)} · ${esc(bytesDiff(l.diff))}</span>`).join(" ")}</small>` : "";
    return `${sub}<p class="hint">${state === "pending" ? "다른 분께 가기 전에 보는 글꼴이에요. 글꼴 안의 이름·만든 곳·라이선스 글이 보낸 분이 고른 라이선스와 맞는지 봐요. 맞으면 나누기, 유료 글꼴이거나 알 수 없으면 내리기를 눌러요." : "글꼴 이름으로 찾아 라이선스(OFL·Apache·무료 공개)가 맞는지 봐요. 맞으면 다시 올리기, 상용 글꼴이면 내리기를 눌러요."}</p>
      <ul class="rows">${d.items.map((f) => `<li><div class="row"><span class="row-main"><b>${esc(f.name)}</b>
        <small>보낸 분이 고른 것: ${esc(f.licenseName ?? f.license)} · ${(f.bytes / 1048576).toFixed(1)}MB · ${reportLine(f)} · ${esc(ago(f.at))}</small>
        ${own(f)}
        <small class="mono">${esc(f.id)}</small></span>
        ${edit ? `<span class="row-side">${f.state !== "ok" ? `<button class="btn small primary" data-act="recipe-font" data-id="${f.id}" data-key="ok">${f.state === "pending" ? "나누기" : "다시 올리기"}</button>` : ""}
          ${f.state !== "hidden" ? `<button class="btn small danger" data-act="recipe-font" data-id="${f.id}" data-key="hide">내리기</button>` : ""}</span>` : ""}</div></li>`).join("")}</ul>`;
  }

  /** [RCX-04] 1.35 요청 판: what people asked to be made; hide one that breaks the rules. Who wrote it is never shown. */
  function recipeRequestsView(d, edit) {
    if (!d.items.length) return empty("요청이 없어요", "앱의 ‘이런 레시피 있었으면’에서 올린 요청이 여기 보여요.");
    const states = { waiting: "기다리는 중", making: "만드는 중", done: "완성" };
    const style = Object.fromEntries(RECIPE_STYLES);
    return `<ul class="rows">${d.items.map((q) => `<li><div class="row"><span class="row-main"><b>${esc(q.body)}</b>
        <small>${q.style === "ALL" ? "모든 유형" : esc(style[q.style] ?? q.style)} · ${esc(states[q.state] ?? q.state)}${q.askers ? ` · 스튜디오 요구 ${q.askers}명` : ""} · 좋아요 ${q.likes}${q.code ? ` · ${esc(q.code)}` : ""} · ${esc(ago(q.at))}${q.hidden ? ' · <span class="pill warn">가림</span>' : ""}</small></span>
        ${edit ? `<span class="row-side"><button class="btn small" data-act="recipe-request" data-id="${q.id}" data-key="${q.hidden ? "show" : "hide"}">${q.hidden ? "다시 보이기" : "가리기"}</button></span>` : ""}</div></li>`).join("")}</ul>`;
  }


  return { recipesView, materialsView, recipeListPath, retireGroups };
}
