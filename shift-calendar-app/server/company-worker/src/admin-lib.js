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
    // [ADMUX-33] 오늘 › 할 일 keeps 받은함's 보기; [ADMUX-32] 이달 할 일 keeps 운영's month.
    home: { view: "all" },
    chores: { month: "" },
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

  // ---- [ADMUX-30] six hubs (1.28 통합계획 2-2) ----

  /** The six hubs and their tabs (each tab is a route), in the order they show; 1~6 on a keyboard. */
  const HUBS = [
    { key: "today", label: "오늘", icon: "home", tabs: ["home"] },
    { key: "ask", label: "문의", icon: "inquiries", tabs: ["inquiries", "dev", "templates", "sla"] },
    { key: "plaza", label: "광장", icon: "community", tabs: ["review", "versions", "community", "companies", "shares", "adopt"] },
    { key: "release", label: "출시", icon: "release", tabs: ["flags", "types", "notices", "chores", "gates"] },
    { key: "insight", label: "분석", icon: "metrics", tabs: ["metrics", "effects", "audit"] },
    { key: "manage", label: "관리", icon: "admins", tabs: ["admins", "rewards", "server", "account"] },
  ];

  /**
   * Each tab: its name, who sees it ([area] one area or any of several, [owner] the owner only), what its number
   * counts — only what this admin must do — and [old], the menu names it had before 1.28, so the palette still
   * finds it. A number with [count] false shows on its tab but is not added to the hub's (여러 판 are the same
   * setups 검토 counts). [strip] is the word the '지금 기다리는 것' line uses.
   */
  const TABS = {
    home: { label: "오늘", old: "홈 받은함 할 일 지금 할 일 inbox home" },
    inquiries: { label: "문의", area: "inquiries", badge: (o) => o.inquiries ?? 0, strip: "답 대기", old: "문의 inquiries" },
    // [ADMIN-10] 접수 waits for whoever may set 개발중·완료·반려; for the rest it is not a count to clear.
    dev: { label: "개발사항", area: "dev", badge: (o, me) => (can(me, "devStatus", "edit") ? o.devTodo ?? 0 : 0), strip: "개발사항 접수", old: "dev" },
    templates: { label: "자주 쓰는 답", area: "inquiries", old: "답 관리 템플릿 templates" },
    sla: { label: "답 목표", area: "settings", old: "설정 답 목표 SLA" },
    review: { label: "검토", area: "review", badge: (o) => reviewCount(o), old: "제보 앱에 없는 방식 달라요 review" },
    versions: { label: "여러 판", area: "companies", badge: (o) => o.versionConflicts ?? 0, count: false, old: "versions" },
    community: { label: "토론·임금표", area: "community", badge: (o) => o.flaggedPosts ?? 0, strip: "신고 글", old: "토론 임금표 신고된 글 community" },
    companies: { label: "회사", area: "companies", old: "회사·규칙 companies" },
    shares: { label: "공유 링크", area: "shares", old: "공유 링크 신고 shares" },
    adopt: { label: "채택 규칙", area: "settings", old: "설정 자동 채택 맞았어요 비율 채택 보상 상한 settings" },
    // [ADMUX-40] 기능 관리 and 근무 유형 are AM's screens; until the server has the flags area, 운영 may see them.
    flags: { label: "기능 관리", area: ["flags", "ops"], badge: (o) => o.flagsWaiting ?? 0, strip: "기능 확인", old: "운영 기능 스위치 정식 출시 다크머지 결제 광고 flags ops" },
    types: { label: "근무 유형", area: ["flags", "ops"], old: "근무 유형 노출 types" },
    notices: { label: "앱 공지", area: "ops", old: "공지 notices 운영" },
    chores: { label: "이달 할 일", area: "ops", badge: (o) => o.opsLeft ?? 0, old: "이달 할 일 chores 운영" },
    gates: { label: "출시 관문", area: "metrics", old: "지표 출시 관문 원격 갱신 gates" },
    metrics: { label: "지표", area: "metrics", old: "통계 metrics" },
    effects: { label: "기능별 효과", area: "metrics", old: "플래그 효과 effects" },
    audit: { label: "활동 기록", area: "audit", old: "audit" },
    admins: { label: "관리자", owner: true, badge: (o) => (o.admins?.waiting ?? 0) + (o.admins?.phones ?? 0), strip: "승인 대기", old: "관리자 휴대폰 admins" },
    // [ADMUX-32] 친구 초대 규칙 moved here from 설정; it is the settings area, so either opens the tab.
    rewards: { label: "보상", area: ["rewards", "settings"], badge: (o, me) => (can(me, "rewards") ? o.owed ?? 0 : 0), strip: "드릴 보상", old: "설정 친구 초대 rewards" },
    server: { label: "서버 상태", owner: true, old: "설정 서버 상태 server" },
    account: { label: "내 계정·이 기기", old: "설정 이 기기 알림 화면 색 테마 글자 크기 아래 탭 소리 저장한 보기 앱 설치 단축키 비밀번호 로그아웃 account settings" },
  };

  /** Whether [me] sees tab [key]. */
  function tabVisible(key, me) {
    const t = TABS[key];
    if (!t || !me) return false;
    if (t.owner) return Boolean(me.owner);
    if (!t.area) return true;
    return (Array.isArray(t.area) ? t.area : [t.area]).some((a) => can(me, a));
  }

  /** The hubs [me] sees, each with only the tabs [me] sees; a hub with none is left out. */
  const visibleHubs = (me) => HUBS.map((h) => ({ ...h, tabs: h.tabs.filter((t) => tabVisible(t, me)) })).filter((h) => h.tabs.length);

  /** Routes that are not a tab of their own, and the hub each belongs to. */
  const EXTRA_HUB = { settings: "manage", inbox: "today", ops: "release", more: null };

  /** The hub a route belongs to (null for 더 보기). */
  function hubOf(name) {
    if (Object.hasOwn(EXTRA_HUB, name)) return EXTRA_HUB[name];
    return HUBS.find((h) => h.tabs.includes(name))?.key ?? null;
  }

  /** [ADMUX-34] A tab's number: what [me] must do there. */
  const tabBadge = (key, o, me) => (TABS[key]?.badge && tabVisible(key, me) ? TABS[key].badge(o ?? {}, me) || 0 : 0);

  /** [ADMUX-34] A hub's number: its tabs' numbers, each thing once (a tab with count: false is not added). */
  function hubBadge(hub, o, me) {
    const h = HUBS.find((x) => x.key === (hub?.key ?? hub));
    return h ? h.tabs.filter((t) => TABS[t].count !== false).reduce((n, t) => n + tabBadge(t, o, me), 0) : 0;
  }

  /**
   * [ADMUX-31] '지금 기다리는 것' on a hub's first tab: its tabs with something waiting and their number. On 오늘
   * it is one item per other hub.
   */
  function waiting(hubKey, o, me) {
    if (hubKey === "today") {
      return visibleHubs(me).filter((h) => h.key !== "today")
        .map((h) => ({ hub: h.key, tab: h.tabs[0], label: h.label, n: hubBadge(h, o, me) })).filter((x) => x.n);
    }
    const h = visibleHubs(me).find((x) => x.key === hubKey);
    return (h?.tabs ?? []).map((t) => ({ hub: hubKey, tab: t, label: TABS[t].strip ?? TABS[t].label, n: tabBadge(t, o, me) })).filter((x) => x.n);
  }

  /**
   * [ADMUX-30] The phone's bottom tabs: 오늘 and three more hubs (the admin's choice if they still see them, else
   * in hub order), then 더 보기 for the rest. When every hub fits there is no 더 보기. [current] is the route
   * shown, so the right one lights up; [back] says the screen sits under 더 보기.
   */
  function bottomHubs(hubs, chosen = [], current = "home") {
    const keys = hubs.map((h) => h.key);
    const here = hubOf(current);
    if (keys.length <= 5) return { tabs: keys, lit: here ?? keys[0], back: false, more: false };
    let tabs = (Array.isArray(chosen) ? chosen : []).filter((k) => keys.includes(k) && k !== "today");
    tabs.unshift("today");
    for (const k of keys) if (tabs.length < 4 && !tabs.includes(k)) tabs.push(k);
    tabs = tabs.slice(0, 4);
    const inTabs = here !== null && tabs.includes(here);
    return { tabs, lit: inTabs ? here : "more", back: !inTabs && current !== "more", more: true };
  }

  // ---- [ADMUX-35] old addresses ----

  /**
   * Where a pre-1.28 address goes now, filters kept: 받은함 is 오늘's 할 일, 운영 is the 출시 hub (이달 할 일 when
   * a month was in it). Every other screen kept its name; #/settings lists where its cards went.
   */
  function redirect(route) {
    const p = route.params ?? {};
    if (route.name === "inbox") return { name: "home", id: null, params: { view: p.view ?? "all" } };
    if (route.name === "ops") return p.month ? { name: "chores", id: null, params: { month: p.month } } : { name: "flags", id: null, params: {} };
    return route;
  }

  /** A saved address as it is written now. */
  function migrateHash(hash) {
    if (!String(hash ?? "").startsWith("#/")) return hash;
    const r = redirect(parseRoute(hash));
    return routeHash(r.name, r.id, r.params);
  }

  /**
   * [ADMUX-35] This device's choices from before 1.28: saved views get their new addresses, and bottom tabs
   * chosen as screens become the hubs those screens are in. Done once (v 2).
   */
  function migratePrefs(prefs) {
    if (!prefs || prefs.v === 2) return prefs;
    const keys = HUBS.map((h) => h.key);
    const tabs = [];
    for (const k of Array.isArray(prefs.tabs) ? prefs.tabs : []) {
      const hub = keys.includes(k) ? k : hubOf(k);
      if (hub && hub !== "today" && !tabs.includes(hub)) tabs.push(hub);
    }
    const views = (Array.isArray(prefs.views) ? prefs.views : []).map((v) => ({ ...v, hash: migrateHash(v.hash) }));
    return { ...prefs, tabs: tabs.slice(0, 3), views, v: 2 };
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

  /**
   * Where g and a letter go. [ADMUX-36] The letters from before 1.28 still work (g b is 오늘's 할 일, g o the 출시
   * hub, g s the list of where 설정 went); f, n, p and u are new.
   */
  const GO = { b: "home", v: "versions", h: "home", i: "inquiries", d: "dev", r: "review", c: "companies", t: "community", w: "rewards", o: "flags", m: "metrics",
    a: "admins", l: "audit", s: "settings", f: "flags", n: "notices", p: "adopt", u: "account" };

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
    ["Esc · Backspace", "뒤로"], ["1~6", "허브: 오늘·문의·광장·출시·분석·관리"], ["[ / ]", "허브 안 이전·다음 탭 (문의를 연 동안은 이전·다음 문의)"],
    ["a", "문의 맡기 · 검토에서 채택"], ["r", "답 칸으로"], ["e", "문의 닫기"],
    ["1~9 (문의 답 칸)", "자주 쓰는 답 넣기"], ["Ctrl+Enter", "답 보내기(큐 모드면 보내고 다음)"], ["q", "큐 모드 시작·끝"],
    ["x · s", "검토에서 거절·다음 카드, 큐 모드에서 닫기·건너뛰기"], ["g h · g b", "오늘(할 일)로"], ["g i · g d", "문의·개발사항으로"],
    ["g r · g v · g t · g c · g p", "검토·여러 판·토론·회사·채택 규칙으로"], ["g f · g o · g n", "기능 관리·출시·앱 공지로"],
    ["g m · g l", "지표·활동 기록으로"], ["g a · g w · g u · g s", "관리자·보상·내 계정·옮긴 설정 목록으로"], ["?", "이 목록"],
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

  return {
    esc, ERRORS, errorText, ago, slaText, can, waited, maskPersonal, reviewCount, setupDiff, waitReason, permSummary, FILTERS, parseRoute, routeHash, cleanFilters, likeFirst, words,
    HUBS, TABS, tabVisible, visibleHubs, hubOf, tabBadge, hubBadge, waiting, bottomHubs, redirect, migrateHash, migratePrefs,
    devCopyText, DEV_NAMES, strength, fillTemplate, sortTemplates, wordDiff, csv, ticks, barChart, contrast, keyAction, KEYS, GO,
    paletteFilter, sequencer, appVersion,
  };
}
