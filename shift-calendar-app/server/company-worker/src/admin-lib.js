// [ADMIN-18] The developer app's pure helpers: no page, no fetch, no storage, so node:test checks them
// as they are. adminFiles() serializes this function next to the app and hands its result to the app
// (`(adminApp)((adminLib)(), build)`), so nothing here may use a name from outside the function.

export function adminLib() {
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

  /**
   * [ADMIN-18] Every error code the server sends, in plain words. test/admin-lib.test.mjs reads the
   * server's source and fails when a code is missing here, so a new one is never shown as English.
   */
  const ERRORS = {
    action: "이 항목에는 할 수 없는 일이에요",
    // AE 1.26: the app's invite report (never sent by the developer app; ADMIN-18 lists every code).
    source: "초대를 어디서 받았는지 알 수 없어요",
    // L 1.23: the notice form's audience, and a question the asker hid the writer of (public side).
    audience: "보일 휴대폰 조건이 맞지 않아요. 제조사·안드로이드 판·일하는 방식·기능을 다시 골라 주세요",
    hidden_by_you: "이 휴대폰에서 가린 사람의 글이에요",
    active: "이 부서에는 지금 쓰는 규칙이 있어요. 그 규칙을 먼저 내려 주세요",
    adoptAgree: "맞았어요 비율은 50~100% 사이로 정해 주세요",
    adoptPersons: "채택에 필요한 사람은 2~10명 사이로 정해 주세요",
    adoptPoints: "채택 점수는 2~20 사이로 정해 주세요",
    adopted: "이미 채택된 항목이에요",
    amendPersons: "규칙 고치기에 필요한 사람은 2~10명 사이로 정해 주세요",
    amends: "고칠 규칙을 다시 골라 주세요",
    amount: "금액은 적을 수 없어요",
    answer: "답을 골라 주세요",
    app: "앱 버전 번호(versionCode)를 숫자로 적어 주세요. 끝 번호는 시작보다 크거나 같아야 해요",
    ask: "이미 답한 요청이에요",
    assignee: "다른 관리자가 맡은 문의예요",
    autoReward: "보상 자동 지급을 켬이나 끔으로 골라 주세요",
    bad_json: "보낸 내용을 읽지 못했어요. 새로 고친 뒤 다시 해 주세요",
    billingLive: "정식 출시 값을 다시 골라 주세요",
    body: "내용이 너무 길어요. 공지는 500자, 개발사항은 4000자까지예요",
    busy: "다른 일을 처리하는 중이에요. 잠시 뒤 다시 해 주세요",
    change: "바꿀 내용을 다시 확인해 주세요",
    changed: "다른 관리자가 먼저 고쳤어요. 바뀐 내용을 불러왔어요",
    code: "확인 코드가 맞지 않아요",
    code_expired: "확인 코드가 만료됐어요. 새 코드를 받아 주세요",
    company: "회사 이름을 두 글자 이상, 40자 안으로 적어 주세요",
    content_type: "보낸 내용을 읽지 못했어요. 새로 고친 뒤 다시 해 주세요",
    count: "숫자를 다시 확인해 주세요",
    day: "날짜를 다시 확인해 주세요",
    days: "기간을 다시 골라 주세요",
    deleted: "지운 항목이에요. 최고 관리자가 되살린 뒤 고칠 수 있어요",
    dept: "부서 이름을 다시 확인해 주세요",
    detail: "내용을 다시 확인해 주세요",
    device: "기기 정보를 읽지 못했어요",
    done: "이미 처리했어요",
    email: "메일 주소를 다시 확인해 주세요",
    events: "보낸 기록을 읽지 못했어요",
    exists: "이미 있어요",
    forbidden: "이 화면을 새로 고친 뒤 다시 해 주세요",
    freeFeatures: "기능 이름은 영어 대문자와 _로 적어 주세요(예: MONEY_STATS)",
    from: "시작 날짜를 확인해 주세요",
    good: "도움이 됐는지를 다시 골라 주세요",
    hidden: "가려진 항목이에요",
    ids: "고른 항목이 없거나 100개를 넘었어요",
    // AA 1.25: 이전 판이 아닌 규칙을 다시 대표로 하려 할 때.
    not_previous: "이전 판만 다시 대표로 할 수 있어요. 화면을 새로 고쳐 주세요",
    info: "함께 보낸 정보를 읽지 못했어요",
    inquiry: "문의를 다시 골라 주세요",
    invite: "초대 링크가 만료됐거나 이미 쓰였어요. 최고 관리자에게 새 링크를 부탁해 주세요",
    job: "직군을 다시 골라 주세요",
    key: "없는 항목이에요",
    kind: "종류를 다시 골라 주세요",
    label: "이름을 1~20자로 적어 주세요",
    level: "공지 종류를 골라 주세요",
    locked: "여러 번 틀려서 잠시 잠겼어요. 한 시간 뒤 다시 해 주세요",
    login: "로그인이 끝났어요. 다시 로그인해 주세요",
    mail_failed: "메일을 보내지 못했어요. 잠시 뒤 다시 해 주세요",
    merge: "합칠 수 없는 회사예요",
    month: "달을 다시 골라 주세요",
    months: "보상은 1~12개월로 골라 주세요",
    muted: "쓰기가 막힌 기기예요",
    name: "이름을 1~30자로 적어 주세요",
    nick: "별명을 다시 확인해 주세요",
    no_database: "데이터베이스가 연결되지 않았어요",
    no_mail: "메일이 연결되지 않은 서버예요. 초대 링크로 확인해 주세요",
    no_permission: "이 일을 할 권한이 없어요. 최고 관리자에게 부탁해 주세요",
    not_answered: "아직 답하지 않은 문의예요",
    not_found: "이미 처리됐거나 없는 항목이에요",
    not_set: "서버에 관리자 비밀번호가 아직 없어요. 설정 안내를 따라 넣어 주세요",
    note: "메모가 너무 길어요. 1000자까지예요",
    off: "꺼 둔 기능이에요",
    offline: "인터넷에 연결되지 않았어요",
    ok: "값을 다시 골라 주세요",
    own: "내 항목에는 할 수 없어요",
    owner_password: "최고 관리자 비밀번호는 Cloudflare의 ADMIN_PASSWORD에서 바꿔요",
    parent: "답글을 달 글을 다시 골라 주세요",
    password: "비밀번호는 10자 이상이어야 해요",
    paused: "지금은 꺼 둔 기능이에요",
    perms: "권한을 다시 골라 주세요",
    phone: "전화번호는 적을 수 없어요",
    photo: "사진을 읽지 못했어요",
    photos: "사진은 두 장까지예요",
    play: "Play 구매 정보를 다시 확인해 주세요",
    play_auth: "Play 연결 정보(PLAY_SERVICE_ACCOUNT)를 확인해 주세요",
    play_off: "Play 구매 확인이 꺼져 있어요",
    product: "상품을 다시 골라 주세요",
    public: "모두의 문의가 아니에요",
    purchase: "구매 정보를 다시 확인해 주세요",
    rate_limited: "잠시 뒤 다시 해 주세요",
    // [PLAZA-07][PLAZA-10] 1.26 AF: 회사 참여와 새 정보 보상.
    member_wait: "이 달력은 회사를 바꾼 지 30일이 지나야 다른 회사로 바꿀 수 있어요",
    member_limit: "한 휴대폰은 회사 두 곳까지 참여할 수 있어요",
    member: "이 회사에 참여한 휴대폰만 볼 수 있어요",
    firstMonths: "새 정보 보상은 0~12개월 사이로 정해 주세요",
    firstCapYear: "새 정보 보상 한 해 상한은 0~12번 사이로 정해 주세요",
    reason: "이유를 적어 주세요",
    recent: "너무 최근 항목이에요. 잠시 뒤 다시 해 주세요",
    releasedIn: "반영된 앱 버전을 1.23.0처럼 적어 주세요",
    rewardCapDays: "상한을 세는 기간은 30~365일 사이로 정해 주세요",
    rewardCapMonths: "한 사람 상한은 1~24개월 사이로 정해 주세요",
    rewardMonths: "보상 개월은 0~24 사이로 정해 주세요",
    same: "이미 같은 것을 보냈어요",
    server: "서버에 문제가 생겼어요. 잠시 뒤 다시 해 주세요",
    setup: "설정 파일을 읽지 못했어요",
    setup_version: "앱을 최신 버전으로 업데이트해 주세요",
    share_server: "공유 서버에 닿지 않았어요. SHARE_BASE_URL과 SHARE_ADMIN_TOKEN을 확인해 주세요",
    sla: "답 목표는 1~168시간 사이로 정해 주세요",
    status: "상태를 다시 골라 주세요",
    taken: "이미 쓰인 항목이에요",
    text: "답을 적어 주세요",
    thanksMonths: "감사 보상은 0~12개월로 정해 주세요",
    title: "제목을 적어 주세요. 공지는 40자, 개발사항은 100자까지예요",
    to: "맡길 사람을 다시 골라 주세요",
    too_late: "되돌릴 수 있는 10분이 지났어요",
    token: "링크가 맞지 않아요. 받은 링크를 다시 열어 주세요",
    too_large: "내용이 너무 길어요",
    too_many: "더 넣을 수 없어요. 쓰지 않는 것을 먼저 지워 주세요",
    topic: "주제를 다시 골라 주세요",
    trade: "직종 이름을 다시 확인해 주세요",
    until: "끝 날짜는 시작보다 뒤, 오늘 이후, 90일 안으로 정해 주세요",
    verified: "확인 여부를 다시 골라 주세요",
    // AI 1.27 [PLAZA-30][PLAZA-33]
    proof: "명세서로 맞춘 달을 다시 확인해 주세요",
    invest_advice: "종목 추천·리딩·수익 보장 글은 올릴 수 없어요",
    version: "앱 버전을 다시 확인해 주세요",
    weak_password: "더 다양한 글자로 비밀번호를 만들어 주세요. 메일 주소와 같으면 안 돼요",
    withdrawn: "이미 내린 규칙이에요",
    wrong: "비밀번호가 맞지 않아요",
    push: "웹 푸시가 연결되지 않은 서버예요",
    subscription: "알림 구독 정보를 읽지 못했어요. 알림을 껐다 다시 켜 주세요",
    quiet: "조용한 시간을 0~23시로 골라 주세요",
    // AM 1.28 [FLAG-21]~[FLAG-33] 기능 관리.
    retired: "정식 출시 스위치는 은퇴했어요. 기능 관리 › 결제(프로 구독)에서 조건을 확인해 주세요",
    flag: "모르는 기능이에요. 새로 고친 뒤 다시 골라 주세요",
    last_type: "근무 유형을 모두 숨길 수는 없어요. 적어도 하나는 보여야 해요",
    conditions: "켜지는 조건을 다시 확인해 주세요",
    manual: "모르는 확인 항목이에요",
    flagsOn: "켜진 기능 목록을 읽지 못했어요",
    force: "강제로 켜기·끄기·풀기 가운데 하나를 골라 주세요",
    review_at: "다시 볼 날은 오늘부터 1년 안의 날짜로 골라 주세요",
    flag_until: "강제 기한은 지금부터 1년 안으로 정해 주세요",
    policy: "처리방침 판과 날짜(게시 ≤ 시행 ≤ 동의 없이 시행)를 다시 확인해 주세요",
    type: "근무 유형을 다시 골라 주세요",
    show: "보이기·숨기기 가운데 하나를 골라 주세요",
    why: "이유를 적어 주세요(300자까지). 나중에 다른 관리자가 이 기록으로 판단해요",
  };
  const errorText = (code) => ERRORS[code] ?? "처리하지 못했어요. 잠시 뒤 다시 해 주세요";

  function ago(iso, now = Date.now()) {
    if (!iso) return "";
    const s = (now - new Date(iso).getTime()) / 1000;
    if (s < 60) return "방금";
    if (s < 3600) return `${Math.floor(s / 60)}분 전`;
    if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
    if (s < 7 * 86400) return `${Math.floor(s / 86400)}일 전`;
    const d = new Date(iso);
    return `${d.getMonth() + 1}월 ${d.getDate()}일`;
  }

  /** [ADMUX-06] Minutes left before an answer target, as a pill's words and tone. */
  function slaText(minutes) {
    if (minutes === null || minutes === undefined) return null;
    const span = (m) => (m < 60 ? `${m}분` : m < 48 * 60 ? `${Math.floor(m / 60)}시간` : `${Math.floor(m / 1440)}일`);
    if (minutes < 0) return { text: `목표 ${span(-minutes)} 넘김`, tone: "bad" };
    return { text: `${span(minutes)} 남음`, tone: minutes < 120 ? "warn" : "" };
  }

  const LEVELS = ["none", "view", "edit"];
  const can = (me, area, level = "view") => Boolean(me && (me.owner || LEVELS.indexOf(me.perms?.[area] ?? "none") >= LEVELS.indexOf(level)));

  /** [ADMIN-15] An admin's permissions in one line, with yes-or-no areas (the server's toggles) as such. */
  function permSummary(perms, areas, toggles = []) {
    const parts = [];
    for (const [area, name] of Object.entries(areas)) {
      const level = perms?.[area] ?? "none";
      if (level === "none") continue;
      parts.push(toggles.includes(area) ? name : `${name} ${level === "edit" ? "고치기" : "보기"}`);
    }
    return parts.length ? parts.join(" · ") : "권한 없음";
  }

  // ---- [ADMUX-02] where the app is, in the address ----

  /** The filters each screen keeps in the address, with what they are when left out. */
  const FILTERS = {
    inquiries: { status: "open", who: "", topic: "", q: "", sort: "default" },
    dev: { status: "open", who: "", q: "" },
    review: { show: "all" },
    community: { show: "flagged", q: "" },
    rewards: { status: "owed", q: "" },
    rates: { status: "review", q: "" },
    companies: { q: "" },
    metrics: { days: "28", from: "", to: "" },
    inbox: { view: "all" },
    versions: { show: "conflict" },
    audit: { actor: "", kind: "", from: "", to: "" },
    ops: { month: "" },
    // AM 1.28 [ADMUX-21][ADMUX-25] 기능 관리: which state, words, list or timeline.
    flags: { show: "all", q: "", view: "" },
  };

  /** "#/inquiries/abc?status=open&q=알람" as { name, id, params }; params hold only known filters. */
  function parseRoute(hash) {
    const raw = String(hash ?? "").replace(/^#\/?/, "");
    const [path, query = ""] = raw.split("?");
    const [name, id] = path.split("/").map((p) => {
      try {
        return decodeURIComponent(p);
      } catch {
        return "";
      }
    });
    const known = FILTERS[name] ?? {};
    const params = { ...known };
    for (const pair of query.split("&")) {
      if (!pair) continue;
      const [k, v = ""] = pair.split("=");
      let key;
      let value;
      try {
        key = decodeURIComponent(k);
        value = decodeURIComponent(v.replace(/\+/g, " "));
      } catch {
        continue;
      }
      if (Object.hasOwn(known, key)) params[key] = value.slice(0, 60);
    }
    return { name: name || "home", id: id || null, params: cleanFilters(name, params) };
  }

  /** The address for a screen; filters at their default are left out, so links stay short. */
  function routeHash(name, id = null, params = {}) {
    const known = FILTERS[name] ?? {};
    const clean = cleanFilters(name, { ...known, ...params });
    const query = Object.entries(clean).filter(([k, v]) => Object.hasOwn(known, k) && v !== "" && v !== known[k])
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
    return `#/${name}${id ? `/${encodeURIComponent(id)}` : ""}${query ? `?${query}` : ""}`;
  }

  /** [ADMUX-02] Filters that cannot go together are put right: 놓친 것 is only ever a waiting question. */
  function cleanFilters(name, params) {
    const p = { ...params };
    if (name === "inquiries" && p.who === "late") p.status = "open";
    return p;
  }

  // ---- [ADMUX-03] the bottom tabs ----

  /**
   * The four tabs at the bottom of a phone: the ones the admin chose if they can still see them, else
   * the first visible screens; the fifth is 더 보기. [current] is which tab lights up, so a screen not in
   * the tabs lights 더 보기, and [back] says whether that screen shows a back button.
   */
  function bottomTabs(visible, chosen = [], current = "home") {
    const keys = visible.map((n) => n.key);
    let tabs = (Array.isArray(chosen) ? chosen : []).filter((k) => keys.includes(k));
    if (!tabs.includes("home")) tabs.unshift("home");
    for (const k of keys) if (tabs.length < 4 && !tabs.includes(k)) tabs.push(k);
    tabs = tabs.slice(0, 4);
    const inTabs = tabs.includes(current);
    return { tabs, lit: inTabs ? current : "more", back: !inTabs && current !== "more" };
  }

  // ---- 개발사항 ----

  const words = (t) => new Set(String(t).toLowerCase().split(/[^\p{L}\p{N}]+/u).filter((w) => w.length >= 2));

  /** Items most like [text] first: how many words they share, then how many asked for them. */
  function likeFirst(items, text) {
    const mine = words(text);
    const score = (d) => [...words(`${d.title} ${d.body ?? ""}`)].filter((w) => mine.has(w) || [...mine].some((m) => m.length >= 3 && (w.includes(m) || m.includes(w)))).length;
    return items.map((d) => [score(d), d]).sort((a, b) => b[0] - a[0] || b[1].links - a[1].links).map(([n, d]) => ({ ...d, like: n }));
  }

  const DEV_NAMES = { todo: "접수", doing: "개발중", done: "완료", rejected: "반려" };

  /** [ADMIN-10] 일괄 복사: plain text that reads as a Markdown list, to hand to developers as it is. */
  function devCopyText(items, today = new Date().toLocaleDateString("ko-KR")) {
    const out = [`# 개발사항 ${items.length}건 (${today})`, ""];
    items.forEach((x, i) => {
      out.push(`${i + 1}. [${DEV_NAMES[x.status]}] ${x.title} — 문의 ${x.links}건`);
      if (x.status === "rejected" && x.reason) out.push(`   반려 이유: ${x.reason}`);
      for (const line of (x.body ?? "").split("\n")) if (line.trim()) out.push(`   ${line}`);
      out.push("");
    });
    return out.join("\n").trimEnd();
  }

  /** 0 to 4: length and the kinds of letters used; only a hint, the server decides. */
  function strength(pw) {
    if (!pw) return 0;
    let n = 0;
    if (pw.length >= 10) n++;
    if (pw.length >= 14) n++;
    const kinds = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((r) => r.test(pw)).length;
    if (kinds >= 2) n++;
    if (kinds >= 3) n++;
    return pw.length < 10 ? Math.min(n, 1) : n;
  }

  // ---- [ADMIN-16] saved answers ----

  /** {앱버전}, {주제} and {오늘} in a saved answer, filled from the question being answered. */
  function fillTemplate(text, { app = "", topic = "", today = "" } = {}) {
    const version = String(app ?? "").replace(/\s*\(\d+\)\s*$/, "");
    return String(text).replace(/\{앱버전\}/g, version || "최신").replace(/\{주제\}/g, topic || "말씀하신 내용").replace(/\{오늘\}/g, today);
  }

  /** The question's topic first, then the most used, then the oldest. */
  const sortTemplates = (list, topic) => [...list].sort((a, b) => ((b.topic === topic) - (a.topic === topic)) || (b.uses ?? 0) - (a.uses ?? 0) || a.id - b.id);

  // ---- [ADMUX-07] before and after, CSV, charts ----

  /**
   * Word by word what changed from [a] to [b], as HTML with <del> and <ins> (escaped). Spaces stay with
   * their word; past 2000 words each side it gives the two whole texts instead of a slow table.
   */
  function wordDiff(a, b) {
    const x = String(a ?? "").split(/(\s+)/).filter((t) => t !== "");
    const y = String(b ?? "").split(/(\s+)/).filter((t) => t !== "");
    if (x.length > 2000 || y.length > 2000) return `<del>${esc(a)}</del><ins>${esc(b)}</ins>`;
    const n = x.length;
    const m = y.length;
    const t = Array.from({ length: n + 1 }, () => new Uint16Array(m + 1));
    for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) t[i][j] = x[i] === y[j] ? t[i + 1][j + 1] + 1 : Math.max(t[i + 1][j], t[i][j + 1]);
    const out = [];
    const push = (kind, s) => {
      const last = out[out.length - 1];
      if (last && last[0] === kind) last[1] += s;
      else out.push([kind, s]);
    };
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
      if (x[i] === y[j]) {
        push("=", x[i]);
        i++;
        j++;
      } else if (t[i + 1][j] >= t[i][j + 1]) push("-", x[i++]);
      else push("+", y[j++]);
    }
    while (i < n) push("-", x[i++]);
    while (j < m) push("+", y[j++]);
    return out.map(([k, s]) => (k === "=" ? esc(s) : k === "-" ? `<del>${esc(s)}</del>` : `<ins>${esc(s)}</ins>`)).join("");
  }

  /**
   * Rows as CSV for a spreadsheet: [columns] is [header, (row) => value]. A UTF-8 mark goes first so Excel
   * reads Korean, and a cell that starts like a formula gets a ' in front so it is never run.
   */
  function csv(rows, columns) {
    const cell = (v) => {
      let s = v === null || v === undefined ? "" : String(v);
      if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
      return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const lines = [columns.map(([h]) => cell(h)).join(","), ...rows.map((r) => columns.map(([, f]) => cell(f(r))).join(","))];
    return `﻿${lines.join("\r\n")}\r\n`;
  }

  /** Round steps for a chart's axis up to [max]: 0 and 3 to 5 more, each 1, 2 or 5 times a power of ten. */
  function ticks(max, want = 4) {
    if (!(max > 0)) return [0, 1];
    const raw = max / want;
    const pow = 10 ** Math.floor(Math.log10(raw));
    const step = [1, 2, 5, 10].map((f) => f * pow).find((s) => s >= raw);
    const out = [];
    for (let v = 0; v < max + step / 2; v += step) out.push(Math.round(v * 1e6) / 1e6);
    if (out[out.length - 1] < max) out.push(out[out.length - 1] + step);
    return out;
  }

  /**
   * [ADMUX-07] A bar chart as SVG with no style attributes (the page's CSP allows none): bars for
   * [values], a dashed line for [before] (the period before), round steps on the axis and every bar's
   * label and number in <title> for a pointer. The numbers also go in a table for screen readers.
   */
  function barChart(values, { labels = [], before = null, width = 640, height = 180, name = "", beforeName = "이전 기간" } = {}) {
    const top = Math.max(1, ...values, ...(before ?? []));
    const steps = ticks(top);
    const max = steps[steps.length - 1];
    const left = 34;
    const bottom = 20;
    const plotH = height - bottom - 8;
    const w = (width - left) / Math.max(1, values.length);
    const y = (v) => 8 + plotH - (v / max) * plotH;
    const grid = steps.map((s) => `<line class="grid" x1="${left}" x2="${width}" y1="${y(s).toFixed(1)}" y2="${y(s).toFixed(1)}"/><text class="axis" x="${left - 6}" y="${(y(s) + 4).toFixed(1)}" text-anchor="end">${s}</text>`).join("");
    const bars = values.map((v, i) => {
      const h = Math.max(v > 0 ? 1 : 0, (v / max) * plotH);
      return `<rect class="bar" x="${(left + i * w + w * 0.15).toFixed(1)}" y="${(8 + plotH - h).toFixed(1)}" width="${Math.max(1, w * 0.7).toFixed(1)}" height="${h.toFixed(1)}" rx="2"><title>${esc(labels[i] ?? "")} ${v}</title></rect>`;
    }).join("");
    const line = before && before.length === values.length
      ? `<polyline class="before" fill="none" points="${before.map((v, i) => `${(left + i * w + w / 2).toFixed(1)},${y(v).toFixed(1)}`).join(" ")}"/>` : "";
    const every = Math.max(1, Math.ceil(values.length / 7));
    const xs = labels.map((l, i) => (i % every === 0 ? `<text class="axis" x="${(left + i * w + w / 2).toFixed(1)}" y="${height - 4}" text-anchor="middle">${esc(l)}</text>` : "")).join("");
    const table = `<table class="sr"><caption>${esc(name)}</caption><thead><tr><th>날짜</th><th>값</th>${before ? `<th>${esc(beforeName)}</th>` : ""}</tr></thead><tbody>${values.map((v, i) => `<tr><td>${esc(labels[i] ?? "")}</td><td>${v}</td>${before ? `<td>${before[i] ?? ""}</td>` : ""}</tr>`).join("")}</tbody></table>`;
    return `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(name)}">${grid}${bars}${line}${xs}</svg>${table}`;
  }

  // ---- [ADMUX-05] colour ----

  /** WCAG contrast of two #RRGGBB (or #RGB) colours, 1 to 21. */
  function contrast(a, b) {
    const lum = (short) => {
      const hex = short.length === 4 ? `#${[...short.slice(1)].map((c) => c + c).join("")}` : short;
      const [r, g, bl] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
      return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
    };
    const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
  }

  // ---- [ADMUX-04] keys ----

  /** Where g and a letter go. */
  const GO = { b: "inbox", v: "versions", h: "home", i: "inquiries", d: "dev", r: "review", c: "companies", t: "community", w: "rewards", o: "ops", m: "metrics", a: "admins", l: "audit", s: "settings", f: "flags" };

  /**
   * What a key press means, or null: [key] as KeyboardEvent.key, [mods] {ctrl, meta, alt, shift},
   * [typing] true in a text box, [pending] "g" after g was pressed. Typing in a box only Ctrl+Enter,
   * Ctrl+K and Escape count.
   */
  function keyAction(key, mods = {}, typing = false, pending = "") {
    const ctrl = Boolean(mods.ctrl || mods.meta);
    if (ctrl && (key === "k" || key === "K")) return { act: "palette" };
    if (ctrl && key === "Enter") return { act: "send" };
    if (key === "Escape") return { act: "escape" };
    if (typing || ctrl || mods.alt) return null;
    if (pending === "g") return GO[key] ? { act: "go", to: GO[key] } : null;
    const plain = {
      g: { act: "pending", key: "g" }, j: { act: "move", by: 1 }, k: { act: "move", by: -1 }, Enter: { act: "open" },
      Backspace: { act: "back" }, "[": { act: "step", by: -1 }, "]": { act: "step", by: 1 }, "/": { act: "search" }, "?": { act: "keys" },
      a: { act: "assign" }, r: { act: "reply" }, e: { act: "close" }, x: { act: "reject" }, s: { act: "skip" }, q: { act: "queue" },
    };
    if (/^[1-9]$/.test(key)) return { act: "template", n: Number(key) };
    return plain[key] ?? null;
  }

  /** The list of keys for the ? screen, as [keys, what]. */
  const KEYS = [
    ["Ctrl+K", "찾기·이동(명령 팔레트)"], ["/", "검색칸으로"], ["j / k", "다음·이전 줄"], ["Enter", "고른 줄 열기"],
    ["Esc · Backspace", "뒤로"], ["[ / ]", "상세에서 이전·다음 항목"], ["a", "문의 맡기 · 검토에서 채택"], ["r", "답 칸으로"], ["e", "문의 닫기"],
    ["1~9", "자주 쓰는 답 넣기"], ["Ctrl+Enter", "답 보내기(큐 모드면 보내고 다음)"], ["q", "큐 모드 시작·끝"],
    ["x · s", "검토에서 거절·다음 카드, 큐 모드에서 닫기·건너뛰기"], ["g h · g i · g d · g r", "홈·문의·개발사항·검토로"], ["g c · g t · g w · g o", "회사·토론·보상·운영으로"],
    ["g m · g a · g l · g s", "지표·관리자·활동 기록·설정으로"], ["g f", "기능 관리로"], ["?", "이 목록"],
  ];

  // ---- [ADMUX-04] the command palette ----

  /** Entries that hold every word of [q] (any order, any case) first, by where the first word starts. */
  function paletteFilter(entries, q, max = 12) {
    const terms = String(q ?? "").toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return entries.slice(0, max);
    return entries.map((e) => [String(`${e.label} ${e.hint ?? ""}`).toLowerCase(), e])
      .filter(([t]) => terms.every((w) => t.includes(w)))
      .map(([t, e]) => [t.indexOf(terms[0]), e])
      .sort((a, b) => a[0] - b[0])
      .slice(0, max)
      .map(([, e]) => e);
  }

  // ---- [ADMUX-01] requests ----

  /**
   * A number per kind of request: [next] starts one and [current] says whether an answer is still the
   * newest of its kind, so a slow answer for a screen the admin left does not draw over the new one.
   */
  function sequencer() {
    const last = {};
    return {
      next: (kind) => (last[kind] = (last[kind] ?? 0) + 1),
      current: (kind, n) => last[kind] === n,
    };
  }

  /** "1.22.0 (33)" and "1.22.0" as [versionCode or null, name]. */
  function appVersion(s) {
    const m = String(s ?? "").match(/^\s*([\d.]+)(?:\s*\((\d+)\))?/);
    return m ? [m[2] ? Number(m[2]) : null, m[1]] : [null, ""];
  }

  /**
   * [ADMUX-11] How long an open question has waited, in the same words as [ago] ("3시간 전부터"), so the list
   * and the pills no longer say "방금 기다림" next to "2분 전" (점검 보고서 하 '관리자 웹앱').
   */
  function waited(hours) {
    if (hours === null || hours === undefined) return "";
    if (hours < 1) return "1시간 안 기다림";
    if (hours < 24) return `${hours}시간째 기다림`;
    return `${Math.floor(hours / 24)}일째 기다림`;
  }

  /** [ADMUX-11] A question's text for the CSV: mail addresses, phone numbers and amounts become •••. */
  function maskPersonal(text) {
    return String(text ?? "")
      .replace(/[^\s@]+@[^\s@]+\.[a-z]{2,}/gi, "•••")
      .replace(/(\+?82[\s.-]?0?|0)\d{1,2}[\s.-]?\d{3,4}[\s.-]?\d{4}/g, "•••")
      .replace(/\d[\d,.]*\s*(원|만\s*원|천\s*원|억\s*원)|\d{1,3}(,\d{3})+|\d{5,}/g, "•••")
      .replace(/\s+/g, " ").trim();
  }

  /** [ADMUX-10] What waits on 검토: the sidebar badge and the home tile count the same three things. */
  function reviewCount(o) {
    return (o?.pending ?? 0) + (o?.asks ?? 0) + (o?.flagged ?? 0);
  }

  /**
   * [ADMUX-09] Groups of one company and department side by side (점검 보고서 중-11): each one's name (제보 1·2, by
   * its first report) and the lines only it has, as the app says them (core SetupDiff).
   */
  function setupDiff(groups, max = 2) {
    const out = new Map();
    const byDept = new Map();
    for (const g of groups) {
      const k = `${g.company?.id ?? g.companyId}|${g.dept ?? ""}`;
      byDept.set(k, [...(byDept.get(k) ?? []), g]);
    }
    for (const same of byDept.values()) {
      if (same.length < 2) continue;
      const first = (g) => Math.min(...(g.reports ?? [{ at: g.at }]).map((r) => new Date(r.at).getTime()));
      [...same].sort((a, b) => first(a) - first(b)).forEach((g, i) => {
        const others = same.filter((x) => x !== g).map((x) => new Set((x.lines ?? []).map(([l, t]) => `${l}\u0000${t}`)));
        const own = (g.lines ?? []).filter(([l, t]) => others.every((o) => !o.has(`${l}\u0000${t}`)));
        out.set(g, {
          label: `제보 ${i + 1}`,
          diff: own.length ? `다른 점: ${own.slice(0, max).map(([l, t]) => `${l} ${t}`).join(" · ")}${own.length > max ? ` 외 ${own.length - max}개` : ""}` : "",
        });
      });
    }
    return out;
  }

  /** [ADMUX-09] Points all in and still waiting: why, in the app's words (core SetupDiff.waitReason). */
  function waitReason(g, agreeNeeded = 75) {
    if (g.points < g.threshold) return "";
    if (g.turnedDown) return "전에 거절된 설정이라 개발자 확인 대기";
    if ((g.agree ?? 100) < agreeNeeded || (g.doubted && g.bad)) return "달라요가 많아 개발자 확인 대기";
    return "개발자 확인 대기";
  }

  // ---- AM 1.28 기능 관리(다크머지) 판단기 [FLAG-21][ADMUX-23][ADMUX-24] ----
  // The server (index.js) and the developer app's 미리 보기 use this one evaluator, so what the screen says a phone
  // would do is what the server computes. The phone app has its own copy in core (com.bamnat.core.flags.FlagEval,
  // 묶음 AL) with the same order: 1 강제 끔 → 2 앱 버전 밖 → 3 관리자 휴대폰 미리 보기 → 4 강제 켬 → 5 조건.

  const FLAG_KST = 9 * 3600e3;
  const flagDay = (ms) => new Date(ms + FLAG_KST).toISOString().slice(0, 10);
  const FLAG_DATE = /^20\d\d-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
  const WORK_TYPES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
  const WORK_NAMES = { SHIFT: "교대", DAY: "일근", HOURLY: "시급", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" };
  /** What a 동의 (consent) condition can name, and how the screens say it. */
  const CONSENT_NAMES = { stats: "사용 통계 보내기 동의", ads: "광고 동의(Google 동의 화면)", "usage.stats.more": "사용 통계 더 보내기 동의", plaza: "회사 광장 나누기 동의" };
  /** What a 정보 (profile) condition can name: the user gave the app what the feature needs. */
  const PROFILE_NAMES = { pay: "급여 설정을 마침", company: "회사를 연결함", leave: "연차 정보를 넣음", invest: "투자 달력이 있음", business: "가게 정보를 넣음" };
  /** Facts only a person can confirm (manual): who confirmed and when is kept on the server. */
  const MANUAL_NAMES = {
    "play.production": "Play 프로덕션 출시 승인",
    "play.subscription": "Play 정기 결제 상품 활성",
    "datasafety.billing": "Play 데이터 보안 답(결제) 고침",
    "datasafety.ads": "Play 데이터 보안·광고 포함 선언 고침",
    "datasafety.stats": "Play 데이터 보안 답(사용 통계 더 보내기) 고침",
    "legal.review": "법 검토 마침",
  };
  const MANUAL_KEY = /^[a-z][a-z0-9]{1,19}(\.[a-z0-9]{1,30}){1,2}$/;
  const POLICY_VERSION = /^\d{1,2}\.\d{1,2}$/;

  /** -1, 0, 1 for two policy or app version names ("3.1" < "3.10", "1.27.0" < "1.28.0"). */
  function versionCompare(a, b) {
    const x = String(a).split(".").map(Number);
    const y = String(b).split(".").map(Number);
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
      const d = (x[i] ?? 0) - (y[i] ?? 0);
      if (d) return d < 0 ? -1 : 1;
    }
    return 0;
  }

  /**
   * A condition list as stored, checked: { list } or { error } in words. Kinds: date, policy, consent, profile, app,
   * workType, rollout, manual, admin, and any (one of several, one level deep).
   */
  function checkConditions(list, depth = 0) {
    if (!Array.isArray(list) || list.length > 12) return { error: "조건은 12개까지 목록으로 적어 주세요" };
    const out = [];
    for (const [i, c] of list.entries()) {
      const at = `${i + 1}번째 조건`;
      if (!c || typeof c !== "object") return { error: `${at}을 읽지 못했어요` };
      if (c.type === "date") {
        if (!FLAG_DATE.test(c.from ?? "")) return { error: `${at}: 날짜는 2026-11-10처럼 적어 주세요` };
        out.push({ type: "date", from: c.from });
      } else if (c.type === "policy") {
        if (!POLICY_VERSION.test(c.version ?? "")) return { error: `${at}: 처리방침 판은 3.1처럼 적어 주세요` };
        out.push({ type: "policy", version: c.version });
      } else if (c.type === "consent") {
        if (!Object.hasOwn(CONSENT_NAMES, c.kind ?? "")) return { error: `${at}: 동의 종류는 ${Object.keys(CONSENT_NAMES).join(", ")} 가운데 하나예요` };
        out.push({ type: "consent", kind: c.kind });
      } else if (c.type === "profile") {
        if (!Object.hasOwn(PROFILE_NAMES, c.need ?? "")) return { error: `${at}: 정보는 ${Object.keys(PROFILE_NAMES).join(", ")} 가운데 하나예요` };
        out.push({ type: "profile", need: c.need });
      } else if (c.type === "app") {
        const ok = (v) => v === undefined || v === null || (Number.isInteger(v) && v > 0 && v < 1000000);
        if (!ok(c.min) || !ok(c.max) || (c.min == null && c.max == null)) return { error: `${at}: 앱 버전 번호(versionCode)를 숫자로 적어 주세요` };
        if (c.min != null && c.max != null && c.max < c.min) return { error: `${at}: 앱 버전 끝 번호가 시작보다 작아요` };
        out.push({ type: "app", ...(c.min != null ? { min: c.min } : {}), ...(c.max != null ? { max: c.max } : {}) });
      } else if (c.type === "workType") {
        if (!Array.isArray(c.any) || !c.any.length || !c.any.every((w) => WORK_TYPES.includes(w))) return { error: `${at}: 근무 유형은 ${WORK_TYPES.join(", ")}에서 골라요` };
        out.push({ type: "workType", any: [...new Set(c.any)] });
      } else if (c.type === "rollout") {
        if (!Number.isInteger(c.percent) || c.percent < 0 || c.percent > 100) return { error: `${at}: 비율은 0~100 사이 숫자예요` };
        out.push({ type: "rollout", percent: c.percent });
      } else if (c.type === "manual") {
        if (!MANUAL_KEY.test(c.key ?? "")) return { error: `${at}: 확인 이름은 play.production처럼 적어요` };
        out.push({ type: "manual", key: c.key });
      } else if (c.type === "admin") {
        out.push({ type: "admin" });
      } else if (c.type === "any" && depth === 0) {
        const inner = checkConditions(c.of, 1);
        if (inner.error) return { error: `${at}(또는): ${inner.error}` };
        if (inner.list.length < 2) return { error: `${at}: '또는'은 두 개 이상을 묶어요` };
        out.push({ type: "any", of: inner.list });
      } else {
        return { error: `${at}: 모르는 종류예요` };
      }
    }
    return { list: out };
  }

  /** One condition in words, as the checklist and the editor say it. */
  function conditionLabel(c, policy = []) {
    const md = (d) => `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일`;
    const year = (d) => (d.slice(0, 4) === "2026" ? md(d) : `${d.slice(0, 4)}년 ${md(d)}`);
    if (c.type === "date") return `${year(c.from)}(한국 시간 0시)부터`;
    if (c.type === "policy") {
      const p = (policy ?? []).find((x) => x.version === c.version);
      const free = p ? p.heldUntil ?? p.effective : null;
      return `처리방침 ${c.version}에 동의${free ? `, 또는 ${year(free)}부터 모두` : ""}`;
    }
    if (c.type === "consent") return CONSENT_NAMES[c.kind] ?? c.kind;
    if (c.type === "profile") return PROFILE_NAMES[c.need] ?? c.need;
    if (c.type === "app") return c.min != null && c.max != null ? `앱 번호 ${c.min}~${c.max}` : c.min != null ? `앱 번호 ${c.min} 이상` : `앱 번호 ${c.max} 이하`;
    if (c.type === "workType") return `근무 유형 ${c.any.map((w) => WORK_NAMES[w] ?? w).join("·")}`;
    if (c.type === "rollout") return `휴대폰 ${c.percent}%에만`;
    if (c.type === "manual") return `${MANUAL_NAMES[c.key] ?? c.key} (관리자가 확인)`;
    if (c.type === "admin") return "관리자 휴대폰";
    if (c.type === "any") return c.of.map((x) => conditionLabel(x, policy)).join(" 또는 ");
    return c.type;
  }

  /** Conditions as the editor's lines ("date >= 2026-11-10", "policy >= 3.1 or date >= 2026-11-24"). */
  function conditionText(list) {
    const one = (c) => ({
      date: () => `date >= ${c.from}`, policy: () => `policy >= ${c.version}`, consent: () => `consent ${c.kind}`, profile: () => `profile ${c.need}`,
      app: () => (c.min != null && c.max != null ? `app ${c.min}..${c.max}` : c.min != null ? `app >= ${c.min}` : `app <= ${c.max}`),
      workType: () => `workType ${c.any.join(",")}`, rollout: () => `rollout ${c.percent}%`, manual: () => `manual ${c.key}`, admin: () => "admin",
      any: () => c.of.map(one).join(" or "),
    })[c.type]?.() ?? "";
    return (list ?? []).map(one).join("\n");
  }

  /** [ADMUX-23] The editor's lines back to conditions, with the first mistake by line. */
  function parseConditions(text) {
    const lines = String(text ?? "").split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#"));
    const one = (part) => {
      const t = part.trim();
      let m;
      if ((m = t.match(/^(?:date|날짜)\s*>=\s*(\S+)$/i))) return { type: "date", from: m[1] };
      if ((m = t.match(/^(?:policy|처리방침)\s*>=\s*(\S+)$/i))) return { type: "policy", version: m[1] };
      if ((m = t.match(/^(?:consent|동의)\s+(\S+)$/i))) return { type: "consent", kind: m[1] };
      if ((m = t.match(/^(?:profile|정보)\s+(\S+)$/i))) return { type: "profile", need: m[1] };
      if ((m = t.match(/^(?:app|앱)\s*>=\s*(\d+)$/i))) return { type: "app", min: Number(m[1]) };
      if ((m = t.match(/^(?:app|앱)\s*<=\s*(\d+)$/i))) return { type: "app", max: Number(m[1]) };
      if ((m = t.match(/^(?:app|앱)\s+(\d+)\s*\.\.\s*(\d+)$/i))) return { type: "app", min: Number(m[1]), max: Number(m[2]) };
      if ((m = t.match(/^(?:worktype|유형)\s+([A-Za-z,\s]+)$/i))) return { type: "workType", any: m[1].split(/[,\s]+/).filter(Boolean).map((w) => w.toUpperCase()) };
      if ((m = t.match(/^(?:rollout|비율)\s+(\d+)\s*%?$/i))) return { type: "rollout", percent: Number(m[1]) };
      if ((m = t.match(/^(?:manual|확인)\s+(\S+)$/i))) return { type: "manual", key: m[1] };
      if (/^(?:admin|관리자)$/i.test(t)) return { type: "admin" };
      return null;
    };
    const list = [];
    for (const [i, line] of lines.entries()) {
      const got = line.split(/\s+(?:or|또는)\s+/i).map(one);
      if (got.some((g) => !g)) return { error: `${i + 1}째 줄을 읽지 못했어요: "${line}"` };
      list.push(got.length > 1 ? { type: "any", of: got } : got[0]);
    }
    return checkConditions(list);
  }

  /**
   * Whether one condition holds: true, false, or "phone" (it depends on each phone, and no phone was given).
   * [ctx] { now, policy, checks: {manualKey: …}, phone: null | {app, agreed, consents, workType, profile, bucket, admin, preview} }.
   */
  function conditionMet(c, ctx) {
    const phone = ctx.phone ?? null;
    const today = flagDay(ctx.now);
    if (c.type === "date") return today >= c.from;
    if (c.type === "manual") return Boolean(ctx.checks?.[c.key]);
    if (c.type === "policy") {
      const agreed = Boolean(phone?.agreed && POLICY_VERSION.test(phone.agreed) && versionCompare(phone.agreed, c.version) >= 0);
      const p = (ctx.policy ?? []).find((x) => x.version === c.version);
      if (!p) return phone ? agreed : "phone";
      if (today >= (p.heldUntil ?? p.effective)) return true;
      if (today < p.posted) return false;
      return phone ? agreed : "phone";
    }
    if (c.type === "rollout") {
      if (c.percent >= 100) return true;
      if (c.percent <= 0) return false;
      return phone ? Number(phone.bucket ?? 0) < c.percent : "phone";
    }
    if (c.type === "any") {
      const got = c.of.map((x) => conditionMet(x, ctx));
      return got.includes(true) ? true : got.includes("phone") ? "phone" : false;
    }
    if (!phone) return "phone";
    if (c.type === "consent") return (phone.consents ?? []).includes(c.kind);
    if (c.type === "profile") return (phone.profile ?? []).includes(c.need);
    if (c.type === "app") return Number.isInteger(phone.app) && (c.min == null || phone.app >= c.min) && (c.max == null || phone.app <= c.max);
    if (c.type === "workType") return c.any.includes(phone.workType);
    if (c.type === "admin") return Boolean(phone.admin);
    return false;
  }

  /** A force (on or off) still in effect at [now]: an until that passed means it ended. */
  const forceOf = (o, now) => (o && (o.force === "on" || o.force === "off") && !(o.until && Date.parse(o.until) <= now) ? o.force : null);

  /**
   * [FLAG-21] One flag's state: { on, state, source, reason, conditions: [{c, label, met}], next }. state is one of
   * forced_off, forced_on, on, waiting (conditions left), off. With no phone (the server's view), conditions that
   * depend on the phone are "phone" and do not hold the flag back: on means "on for the phones that meet them".
   */
  function evalFlag(flag, override, ctx) {
    const now = ctx.now ?? Date.now();
    const c2 = { ...ctx, now };
    const conds = Array.isArray(override?.conditions) ? override.conditions : flag.conditions ?? [];
    const conditions = conds.map((c) => ({ c, label: conditionLabel(c, ctx.policy), met: conditionMet(c, c2) }));
    const dates = [];
    const walk = (c) => (c.type === "any" ? c.of.forEach(walk) : c.type === "date" && c.from > flagDay(now) ? dates.push(c.from) : null);
    conds.forEach(walk);
    const next = dates.sort()[0] ?? null;
    const force = forceOf(override, now);
    const phone = ctx.phone ?? null;
    const base = { conditions, next };
    if (force === "off") return { ...base, on: false, state: "forced_off", source: "force", reason: `강제로 껐어요${override.why ? `: ${override.why}` : ""}` };
    const app = conditions.find((x) => x.c.type === "app");
    if (phone && app && app.met === false) return { ...base, on: false, state: "off", source: "app", reason: "이 앱 버전에서는 꺼져요(앱 버전 밖)" };
    if (phone && phone.admin && phone.preview) return { ...base, on: true, state: "on", source: "admin", reason: "관리자 휴대폰의 미리 보기라 켜져요" };
    if (force === "on") {
      // A legal flag forced on still waits for its policy condition (계약 2-1).
      const policy = flag.risk === "LEGAL" ? conditions.filter((x) => x.c.type === "policy" || (x.c.type === "any" && x.c.of.some((y) => y.type === "policy"))) : [];
      if (policy.some((x) => x.met === false)) return { ...base, on: false, state: "waiting", source: "force", reason: "강제로 켰지만 법적 기능이라 처리방침 조건을 기다려요" };
      return { ...base, on: true, state: "forced_on", source: "force", reason: `강제로 켰어요${override.why ? `: ${override.why}` : ""}` };
    }
    if (!conds.length) {
      return { ...base, on: Boolean(flag.default), state: flag.default ? "on" : "off", source: "default", reason: flag.default ? "기본으로 켜져 있어요" : "기본으로 꺼져 있어요" };
    }
    const left = conditions.filter((x) => x.met === false);
    if (!left.length) {
      const varies = conditions.filter((x) => x.met === "phone");
      return { ...base, on: true, state: "on", source: "conditions",
        reason: varies.length ? `조건을 채워 켜졌어요. ${varies.map((x) => x.label).join(", ")}은(는) 휴대폰마다 달라요` : "조건을 모두 채워 켜졌어요" };
    }
    const words = left.map((x) => x.label).join(", ");
    if (flag.default) return { ...base, on: true, state: "on", source: "default", reason: `기본으로 켜져 있어요(남은 조건: ${words})` };
    const tail = left.every((x) => x.c.type === "manual") ? " 확인하면 바로 켜져요." : left.every((x) => x.c.type === "date") ? " 그날 저절로 켜져요." : "";
    return { ...base, on: false, state: "waiting", source: "conditions", reason: `대기 중이에요. ${words}이(가) 남았어요.${tail}` };
  }

  /** Whole days from [now] to the KST start of [day] ("15일 남음"), or null. */
  function daysLeft(day, now = Date.now()) {
    if (!day || !FLAG_DATE.test(day)) return null;
    return Math.ceil((Date.parse(`${day}T00:00:00+09:00`) - now) / 86400000);
  }

  /**
   * [FLAG-29] 처리방침 9항: a change is announced 7 days before it applies, 30 days when it is unfavourable to users.
   * Warnings for a flag: its basis version gives too little notice, or a date condition comes before that version applies.
   */
  function noticeWarnings(flag, conds, policy = []) {
    const out = [];
    const v = String(flag.basis?.policy ?? "").split(" ")[0];
    const p = v ? (policy ?? []).find((x) => x.version === v) : null;
    if (!p) return out;
    const gap = Math.round((Date.parse(`${p.effective}T00:00:00Z`) - Date.parse(`${p.posted}T00:00:00Z`)) / 86400000);
    const need = p.unfavourable ? 30 : 7;
    if (gap < need) out.push(`처리방침 ${p.version}는 게시(${p.posted})에서 시행(${p.effective})까지 ${gap}일이에요. ${p.unfavourable ? "불리한 변경은 30일" : "7일"} 전에 알려야 해요(처리방침 9항)`);
    const early = (conds ?? []).filter((c) => c.type === "date" && c.from < p.effective).map((c) => c.from);
    if (early.length) out.push(`날짜 조건(${early[0]})이 근거 처리방침 ${p.version}의 시행일(${p.effective})보다 일러요`);
    return out;
  }
  // ---- AM 1.28 끝 ----

  return {
    esc, ERRORS, errorText, ago, slaText, can, waited, maskPersonal, reviewCount, setupDiff, waitReason, permSummary, FILTERS, parseRoute, routeHash, cleanFilters, bottomTabs, likeFirst, words,
    devCopyText, DEV_NAMES, strength, fillTemplate, sortTemplates, wordDiff, csv, ticks, barChart, contrast, keyAction, KEYS, GO,
    paletteFilter, sequencer, appVersion,
    // AM 1.28 [FLAG-21][ADMUX-23][ADMUX-24]
    evalFlag, checkConditions, parseConditions, conditionText, conditionLabel, conditionMet, versionCompare, daysLeft, noticeWarnings, forceOf, flagDay,
    WORK_TYPES, WORK_NAMES, CONSENT_NAMES, PROFILE_NAMES, MANUAL_NAMES,
  };
}
