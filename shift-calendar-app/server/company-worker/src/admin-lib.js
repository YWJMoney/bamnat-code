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
    // R131 1.31 [ADMX-16] 두 번째 확인
    code_needed: "인증 앱의 6자리 코드를 넣어 주세요",
    do: "할 수 없는 요청이에요. 새로 고친 뒤 다시 해 주세요",
    code_wrong: "코드가 맞지 않아요. 인증 앱의 지금 코드를 넣어 주세요",
    second_on: "두 번째 확인이 이미 켜져 있어요",
    second_off: "두 번째 확인이 이미 꺼져 있어요",
    second_not_started: "먼저 '켜기'를 눌러 인증 앱에 등록해 주세요",
    // AQ 1.29 [PHOTO-10]: the app's '사진이 달라요' counts (never sent by the developer app; ADMIN-18 lists every code).
    counts: "사진이 달라요 횟수가 비었거나 너무 많아요",
    screen: "사진이 달라요 화면 종류를 알 수 없어요",
    n: "사진이 달라요 횟수가 맞지 않아요",
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
    // AT 1.29 [ADMCP-07] 1.27's /overview and /inbox are gone: only a tab left open from then can get this.
    moved: "관리자 앱이 새로 나왔어요. 새로 고쳐 주세요",
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
    inquiries: { status: "open", who: "", topic: "", q: "", sort: "default", kind: "", copied: "", sender: "" /* AT 1.29 [ADMCP-02] */ },
    dev: { status: "open", who: "", q: "" },
    review: { show: "all" },
    community: { show: "flagged", q: "" },
    rewards: { status: "owed", q: "" },
    rates: { status: "review", q: "" },
    companies: { q: "", sort: "" /* AX 1.30 [ADMX-06] */ },
    metrics: { days: "28", from: "", to: "" },
    inbox: { view: "all" },
    versions: { show: "conflict" },
    audit: { actor: "", kind: "", from: "", to: "" },
    ops: { month: "" },
    // [ADMUX-33] 오늘 › 할 일 keeps 받은함's 보기; [ADMUX-32] 이달 할 일 keeps 운영's month.
    home: { view: "all" },
    chores: { month: "" },
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
    // [ADMUX-40][FLAG-27] 기능 관리 and 근무 유형 (AM): the flags area, which admins with 운영 보기 got when 1.28 came (SINCE.flags).
    flags: { label: "기능 관리", area: "flags", badge: (o) => o.flagsWaiting ?? 0, strip: "기능 확인", old: "운영 기능 스위치 정식 출시 다크머지 결제 광고 flags ops" },
    types: { label: "근무 유형", area: "flags", old: "근무 유형 노출 types" },
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
  function barChart(values, { labels = [], before = null, width = 640, height = 180, name = "", beforeName = "이전 기간", marks = [] } = {}) {
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
    // AX 1.30 [ADMX-07] A new app version's first day: a dashed line over that day, its name on hover and in the table.
    const marked = (marks ?? []).filter((m) => m.i >= 0 && m.i < values.length);
    const pins = marked.map((m) => {
      const x = (left + m.i * w + w / 2).toFixed(1);
      return `<g class="mark"><line x1="${x}" x2="${x}" y1="6" y2="${8 + plotH}"/><circle cx="${x}" cy="6" r="3"/><title>${esc(m.label)}</title></g>`;
    }).join("");
    const every = Math.max(1, Math.ceil(values.length / 7));
    const xs = labels.map((l, i) => (i % every === 0 ? `<text class="axis" x="${(left + i * w + w / 2).toFixed(1)}" y="${height - 4}" text-anchor="middle">${esc(l)}</text>` : "")).join("");
    const table = `<table class="sr"><caption>${esc(name)}</caption><thead><tr><th>날짜</th><th>값</th>${before ? `<th>${esc(beforeName)}</th>` : ""}</tr></thead><tbody>${values.map((v, i) => `<tr><td>${esc(labels[i] ?? "")}</td><td>${v}${marked.filter((m) => m.i === i).map((m) => ` (${esc(m.label)})`).join("")}</td>${before ? `<td>${before[i] ?? ""}</td>` : ""}</tr>`).join("")}</tbody></table>`;
    return `<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(name)}">${grid}${bars}${line}${pins}${xs}</svg>${table}`;
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
      // AT 1.29 [ADMCP-01][ADMCP-02] c copies (the open question, the picked ones or the row in focus); Space picks a row.
      c: { act: "copy" }, " ": { act: "pick" },
      // AX 1.30 [ADMX-05] n: the next question that waits for an answer, from the list or a question.
      n: { act: "next" },
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
    ["g m · g l", "지표·활동 기록으로"], ["g a · g w · g u · g s", "관리자·보상·내 계정·옮긴 설정 목록으로"],
    ["c", "문의 복사(연 문의, 고른 문의, 고른 줄)"], ["n", "다음 답 대기 문의로"], ["Space (검토)", "검토 줄 펼치기·접기"], ["Space · Shift+Space", "문의 줄 고르기 · 앞에 고른 줄부터 이어서 고르기"], ["?", "이 목록"],
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
  // The condition JSON is AL's (AL-D1): date {from | policy}, policy {version}, consent {key, unknownMeets}, profile {key},
  // app {min, max} (versionCode), workType {in}, rollout {percent}, manual {key}, admin, any {of}.

  const FLAG_KST = 9 * 3600e3;
  const flagDay = (ms) => new Date(ms + FLAG_KST).toISOString().slice(0, 10);
  const FLAG_DATE = /^20\d\d-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
  const WORK_TYPES = ["SHIFT", "DAY", "HOURLY", "FIELD", "BUSINESS", "INVEST"];
  const WORK_NAMES = { SHIFT: "교대", DAY: "일근", HOURLY: "시급", FIELD: "현장", BUSINESS: "자영업", INVEST: "투자" };
  /** What a 동의 (consent) condition can name, and how the screens say it (core FlagEval.CONSENTS). */
  const CONSENT_NAMES = { ads: "광고 동의(Google 동의 화면)", usage: "사용 통계 보내기 동의", "usage.stats.more": "사용 통계 더 보내기 동의(켜진 기능 목록·앱 시작 시간)",
    "usage.stats.lookup": "사용 통계 더 보내기 동의(조회 시간, 1.31부터)" };
  /** What a 정보 (profile) condition can name: the user gave the app what the feature needs (core FlagEval.PROFILE). */
  const PROFILE_NAMES = { "setup.done": "첫 설정을 마침", "pay.setup": "급여 설정을 마침", "company.linked": "회사를 연결함" };
  /** Facts only a person can confirm (manual): who confirmed and when is kept on the server (core FlagEval.MANUAL). */
  const MANUAL_NAMES = {
    "play.production": "Play 프로덕션 승인",
    "play.subscription": "정기 결제 상품 활성",
    "data.safety": "Play 데이터 보안 답 고침",
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
        if (c.policy != null) {
          if (c.from != null || !POLICY_VERSION.test(c.policy)) return { error: `${at}: 처리방침 시행일은 3.1처럼 판만 적어 주세요` };
          out.push({ type: "date", policy: c.policy });
        } else {
          if (!FLAG_DATE.test(c.from ?? "")) return { error: `${at}: 날짜는 2026-11-10처럼 적어 주세요` };
          out.push({ type: "date", from: c.from });
        }
      } else if (c.type === "policy") {
        if (!POLICY_VERSION.test(c.version ?? "")) return { error: `${at}: 처리방침 판은 3.1처럼 적어 주세요` };
        out.push({ type: "policy", version: c.version });
      } else if (c.type === "consent") {
        if (!Object.hasOwn(CONSENT_NAMES, c.key ?? "")) return { error: `${at}: 동의 종류는 ${Object.keys(CONSENT_NAMES).join(", ")} 가운데 하나예요` };
        out.push({ type: "consent", key: c.key, ...(c.unknownMeets === true ? { unknownMeets: true } : {}) });
      } else if (c.type === "profile") {
        if (!Object.hasOwn(PROFILE_NAMES, c.key ?? "")) return { error: `${at}: 정보는 ${Object.keys(PROFILE_NAMES).join(", ")} 가운데 하나예요` };
        out.push({ type: "profile", key: c.key });
      } else if (c.type === "app") {
        const ok = (v) => v === undefined || v === null || (Number.isInteger(v) && v > 0 && v < 1000000);
        if (!ok(c.min) || !ok(c.max) || (c.min == null && c.max == null)) return { error: `${at}: 앱 버전 번호(versionCode)를 숫자로 적어 주세요` };
        if (c.min != null && c.max != null && c.max < c.min) return { error: `${at}: 앱 버전 끝 번호가 시작보다 작아요` };
        out.push({ type: "app", min: c.min ?? null, max: c.max ?? null });
      } else if (c.type === "workType") {
        if (!Array.isArray(c.in) || !c.in.length || !c.in.every((w) => WORK_TYPES.includes(w))) return { error: `${at}: 근무 유형은 ${WORK_TYPES.join(", ")}에서 골라요` };
        out.push({ type: "workType", in: [...new Set(c.in)] });
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

  /** The day a date condition waits for: its own, or its policy version's effective day (null when not in the calendar). */
  const dateFrom = (c, policy) => c.from ?? (policy ?? []).find((x) => x.version === c.policy)?.effective ?? null;

  /** One condition in words, as the checklist and the editor say it. */
  function conditionLabel(c, policy = []) {
    const md = (d) => `${Number(d.slice(5, 7))}월 ${Number(d.slice(8, 10))}일`;
    const year = (d) => (d.slice(0, 4) === "2026" ? md(d) : `${d.slice(0, 4)}년 ${md(d)}`);
    if (c.type === "date") {
      const from = dateFrom(c, policy);
      if (c.policy) return `처리방침 ${c.policy} 시행일${from ? `(${year(from)})` : "(일정에 없음)"}부터`;
      return `${year(c.from)}(한국 시간 0시)부터`;
    }
    if (c.type === "policy") {
      const p = (policy ?? []).find((x) => x.version === c.version);
      const free = p ? p.heldUntil ?? p.effective : null;
      return `처리방침 ${c.version}에 동의했거나 ${free ? `${year(free)}이 지남` : "보류가 끝남"}`;
    }
    if (c.type === "consent") return (CONSENT_NAMES[c.key] ?? c.key) + (c.unknownMeets ? "(묻기 전에는 켬)" : "");
    if (c.type === "profile") return PROFILE_NAMES[c.key] ?? c.key;
    if (c.type === "app") return c.min != null && c.max != null ? `앱 번호 ${c.min}~${c.max}` : c.min != null ? `앱 번호 ${c.min} 이상` : `앱 번호 ${c.max} 이하`;
    if (c.type === "workType") return `근무 유형 ${c.in.map((w) => WORK_NAMES[w] ?? w).join("·")}`;
    if (c.type === "rollout") return `휴대폰 ${c.percent}%에 먼저`;
    if (c.type === "manual") return `${MANUAL_NAMES[c.key] ?? c.key}(관리자가 확인)`;
    if (c.type === "admin") return "관리자 휴대폰";
    if (c.type === "any") return c.of.map((x) => conditionLabel(x, policy)).join(" 또는 ");
    return c.type;
  }

  /** Conditions as the editor's lines ("date >= 2026-11-10", "date >= policy 3.1", "policy 3.1 or date >= 2026-11-24"). */
  function conditionText(list) {
    const one = (c) => ({
      date: () => (c.policy ? `date >= policy ${c.policy}` : `date >= ${c.from}`), policy: () => `policy ${c.version}`,
      consent: () => `consent ${c.key}${c.unknownMeets ? "?" : ""}`, profile: () => `profile ${c.key}`,
      app: () => (c.min != null && c.max != null ? `app ${c.min}..${c.max}` : c.min != null ? `app >= ${c.min}` : `app <= ${c.max}`),
      workType: () => `workType ${c.in.join(",")}`, rollout: () => `rollout ${c.percent}%`, manual: () => `manual ${c.key}`, admin: () => "admin",
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
      if ((m = t.match(/^(?:date|날짜)\s*>=\s*(?:policy|처리방침)\s+(\S+)$/i))) return { type: "date", policy: m[1] };
      if ((m = t.match(/^(?:date|날짜)\s*>=\s*(\S+)$/i))) return { type: "date", from: m[1] };
      if ((m = t.match(/^(?:policy|처리방침)\s*(?:>=\s*)?(\S+)$/i))) return { type: "policy", version: m[1] };
      if ((m = t.match(/^(?:consent|동의)\s+(\S+?)(\?)?$/i))) return { type: "consent", key: m[1], ...(m[2] ? { unknownMeets: true } : {}) };
      if ((m = t.match(/^(?:profile|정보)\s+(\S+)$/i))) return { type: "profile", key: m[1] };
      if ((m = t.match(/^(?:app|앱)\s*>=\s*(\d+)$/i))) return { type: "app", min: Number(m[1]) };
      if ((m = t.match(/^(?:app|앱)\s*<=\s*(\d+)$/i))) return { type: "app", max: Number(m[1]) };
      if ((m = t.match(/^(?:app|앱)\s+(\d+)\s*\.\.\s*(\d+)$/i))) return { type: "app", min: Number(m[1]), max: Number(m[2]) };
      if ((m = t.match(/^(?:worktype|유형)\s+([A-Za-z,\s]+)$/i))) return { type: "workType", in: m[1].split(/[,\s]+/).filter(Boolean).map((w) => w.toUpperCase()) };
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
   * [ctx] { now, policy, checks: {manualKey: …}, phone: null | {app, agreed, installed, consents: {key: true|false},
   * workType, profile: [keys], bucket, admin, preview} }. Unknown (not in the calendar, not asked) is not met, as in core.
   */
  function conditionMet(c, ctx) {
    const phone = ctx.phone ?? null;
    const today = flagDay(ctx.now);
    if (c.type === "date") {
      const from = dateFrom(c, ctx.policy);
      return Boolean(from) && today >= from;
    }
    if (c.type === "manual") return Boolean(ctx.checks?.[c.key]);
    if (c.type === "policy") {
      // Held: posted, not past its held-until day, installed before it was posted and not agreed (core PolicyCalendar.holds).
      const p = (ctx.policy ?? []).find((x) => x.version === c.version);
      if (!p) return false;
      if (today < p.posted || today >= (p.heldUntil ?? p.effective)) return true;
      if (!phone) return "phone";
      const agreed = Boolean(phone.agreed && POLICY_VERSION.test(phone.agreed) && versionCompare(phone.agreed, c.version) >= 0);
      return agreed || Boolean(phone.installed && phone.installed >= p.posted);
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
    if (c.type === "consent") {
      const v = phone.consents?.[c.key];
      return v === true || (v === undefined && Boolean(c.unknownMeets));
    }
    if (c.type === "profile") return (phone.profile ?? []).includes(c.key);
    if (c.type === "app") return Number.isInteger(phone.app) && (c.min == null || phone.app >= c.min) && (c.max == null || phone.app <= c.max);
    if (c.type === "workType") return c.in.includes(phone.workType);
    if (c.type === "admin") return Boolean(phone.admin);
    return false;
  }

  /** 1.28 통합 [FLAG-36] An app versionCode range in words ("39~45", "39 이상", "45 이하", "모든 판"). */
  const appRange = (lo, hi) => (lo != null && hi != null ? `${lo}~${hi}` : lo != null ? `${lo} 이상` : hi != null ? `${hi} 이하` : "모든 판");

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
    const walk = (c) => {
      if (c.type === "any") return c.of.forEach(walk);
      const from = c.type === "date" ? dateFrom(c, ctx.policy) : null;
      if (from && from > flagDay(now)) dates.push(from);
    };
    conds.forEach(walk);
    const next = dates.sort()[0] ?? null;
    const force = forceOf(override, now);
    const phone = ctx.phone ?? null;
    const base = { conditions, next };
    // [FLAG-04] A legal flag turned on by a preview or a force still needs its policy conditions (core policyOnly).
    const policyLeft = flag.risk === "LEGAL" ? conditions.find((x) => x.c.type === "policy" && x.met === false) : null;
    if (force === "off") return { ...base, on: false, state: "forced_off", source: "force", reason: `강제로 껐어요${override.why ? `: ${override.why}` : ""}` };
    const app = conditions.find((x) => x.c.type === "app");
    if (phone && app && app.met === false) return { ...base, on: false, state: "off", source: "app", reason: "이 앱 버전에서는 꺼져요(앱 버전 밖)" };
    // 1.28 통합 [FLAG-36] AL-D3: the override's own versionCode range, step 2 as in core FlagEval.
    const lo = override?.minApp ?? flag.minApp ?? null;
    const hi = override?.maxApp ?? flag.maxApp ?? null;
    if (phone && Number.isInteger(phone.app) && ((lo != null && phone.app < lo) || (hi != null && phone.app > hi))) {
      return { ...base, on: false, state: "off", source: "app", reason: `이 앱 판(${phone.app})에서는 꺼져요(앱 판 범위 ${appRange(lo, hi)} 밖)` };
    }
    if (phone && phone.admin && phone.preview) {
      if (policyLeft) return { ...base, on: false, state: "waiting", source: "admin", reason: `미리 보기를 켰지만 법적 조건이 남아 꺼져 있어요: ${policyLeft.label}` };
      return { ...base, on: true, state: "on", source: "admin", reason: "관리자 휴대폰의 미리 보기라 켜져요" };
    }
    if (force === "on") {
      if (policyLeft) return { ...base, on: false, state: "waiting", source: "force", reason: `강제로 켰지만 법적 기능이라 처리방침 조건을 기다려요: ${policyLeft.label}` };
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
    const early = (conds ?? []).filter((c) => c.type === "date" && c.from && c.from < p.effective).map((c) => c.from);
    if (early.length) out.push(`날짜 조건(${early[0]})이 근거 처리방침 ${p.version}의 시행일(${p.effective})보다 일러요`);
    return out;
  }
  // ---- AM 1.28 끝 ----

  // ---- AT 1.29 시작: 문의 복사 [ADMCP-01][ADMCP-02][ADMCP-05] ----
  // A question, or the ones picked, as Markdown to paste into a development task as it is: what it is about, the
  // app and phone it came from, what the asker wrote, a few lines of each check that came with it, the answers and
  // the admins' notes. Photos stay on the server: the text says how many and links to the question in the app.

  const COPY_KINDS = { bug: "문제 신고", question: "질문", idea: "건의·제안", company: "회사 방식", other: "기타" };
  const COPY_ORDER = ["bug", "question", "idea", "company", "other"];
  const COPY_STATUS = { open: "답 대기", answered: "답함", closed: "닫힘" };
  /** The checks an app sends with a question (the server's labels), summed up in a few lines each. */
  const COPY_CHECKS = ["알람 점검", "급여 계산", "회사 설정"];
  /** Labels of what came with it that are one short value, shown on the question's first lines. */
  const COPY_WORK = ["근무 유형", "일하는 방식"];

  /** "2026-10-27 13:05" in Korea's time whatever the computer's zone, so the same question always copies the same. */
  function kstTime(iso) {
    const ms = Date.parse(iso ?? "");
    return Number.isFinite(ms) ? new Date(ms + 9 * 3600e3).toISOString().slice(0, 16).replace("T", " ") : "";
  }

  /** [text] on one line, [max] letters at most. */
  function oneLine(text, max) {
    const s = String(text ?? "").replace(/\s+/g, " ").trim();
    return [...s].length > max ? `${[...s].slice(0, max).join("")}…` : s;
  }

  const askText = (q) => q.messages?.find((m) => m.from === "user")?.text ?? q.preview ?? "";

  /** A question's title: the first line the asker wrote, 60 letters at most. */
  function copyTitle(q) {
    return oneLine(askText(q).split("\n").find((l) => l.trim()) ?? "", 60) || "(내용 없음)";
  }

  /** The address of a question in the developer app (never a photo's own address), or its id without [base]. */
  const copyLink = (q, base) => (base ? `${base}/admin/#/inquiries/${q.id}` : `문의 ${q.id}`);

  /** 앱 판 · Android 판 · 기기 · 근무 유형, from what came with it (the list has only the app version). */
  function copyDevice(q) {
    const c = q.context ?? {};
    const work = COPY_WORK.map((k) => c[k]).find(Boolean);
    return [c["앱"] ?? q.appVersion ? `앱 ${c["앱"] ?? q.appVersion}` : "", c.Android ? `Android ${c.Android}` : "", c["기기"] ? `기기 ${c["기기"]}` : "",
      work ? `근무 유형 ${work}` : ""].filter(Boolean).join(" · ");
  }

  /** A long check in a few lines: its first [lines] lines that say something, and how many there were. */
  function digest(text, lines = 4, max = 160) {
    const all = String(text ?? "").split("\n").map((l) => l.trim()).filter(Boolean);
    const shown = all.slice(0, lines).map((l) => oneLine(l, max));
    return { lines: shown, more: all.length - shown.length, total: all.length };
  }

  /**
   * [ADMCP-02] 중복 제보 묶기: questions of one kind that say nearly the same (the same words, or half of them shared)
   * go together, in the order the first of each came; a question alike to none is a group of one.
   */
  function sameGroups(list) {
    const groups = [];
    for (const q of list) {
      const w = words(askText(q));
      const flat = askText(q).replace(/\s+/g, "");
      const hit = groups.find((g) => g.kind === q.kind && (g.flat === flat ||
        (w.size && g.words.size && [...w].filter((x) => g.words.has(x)).length * 2 >= Math.min(w.size, g.words.size) &&
          [...w].filter((x) => g.words.has(x)).length >= 2)));
      if (hit) hit.items.push(q);
      else groups.push({ kind: q.kind, words: w, flat, items: [q] });
    }
    return groups.map((g) => g.items);
  }

  /** The lines of one question: [short] is two lines (heading, then the words on one line), else the whole story. */
  function copyEntry(q, { style, base, head }) {
    const kind = COPY_KINDS[q.kind] ?? "문의";
    const tag = [kind, q.topicName].filter(Boolean).join(" · ");
    const device = copyDevice(q);
    const photos = q.photos ? `사진 ${q.photos}장` : "";
    if (style === "short") {
      const meta = [device, kstTime(q.createdAt).slice(5), photos, copyLink(q, base)].filter(Boolean).join(" · ");
      return [`${head}[${tag}] ${copyTitle(q)}${meta ? ` (${meta})` : ""}`, `   ${oneLine(askText(q), 200)}`];
    }
    const out = [`${head}[${tag}] ${copyTitle(q)}`];
    out.push(`- 상태: ${[COPY_STATUS[q.status] ?? q.status, q.urgency === "urgent" ? "급함" : "", `받은 때 ${kstTime(q.createdAt)} (한국 시간)`].filter(Boolean).join(" · ")}`);
    if (device || q.sender) out.push(`- ${[device, q.sender ? `보낸 기기 #${q.sender}` : ""].filter(Boolean).join(" · ")}`);
    if (photos) out.push(`- ${photos}: 관리자 앱에서 보기 ${copyLink(q, base)}`);
    else out.push(`- 관리자 앱: ${copyLink(q, base)}`);
    if (q.dev?.length) out.push(`- 개발사항: ${q.dev.map((d) => `${d.title} (${DEV_NAMES[d.status] ?? d.status})`).join(", ")}`);
    if (q.copied) out.push(`- 전에 복사함: ${q.copied.by} · ${kstTime(q.copied.at)}`);
    out.push("", "**내용**", ...askText(q).split("\n").map((l) => `> ${l}`.trimEnd()));
    const checks = COPY_CHECKS.filter((k) => q.context?.[k]);
    if (checks.length) {
      out.push("", "**진단 요약**");
      for (const k of checks) {
        const d = digest(q.context[k]);
        out.push(`- ${k} (${d.total}줄${d.more ? `, 앞 ${d.lines.length}줄만` : ""})`, ...d.lines.map((l) => `  - ${l}`));
      }
    }
    const rest = (q.messages ?? []).slice((q.messages ?? []).findIndex((m) => m.from === "user") + 1);
    if (rest.length) {
      out.push("", "**대화**");
      for (const m of rest) out.push(`- ${m.from === "user" ? "보낸 분" : m.by && m.by !== "admin" ? m.by : "개발자"} · ${kstTime(m.at).slice(5)}: ${oneLine(m.text, 400)}`);
    }
    if (q.notes?.length) {
      out.push("", "**관리자 메모**");
      for (const n of q.notes) out.push(`- ${n.by} · ${kstTime(n.at).slice(5)}: ${oneLine(n.text, 400)}`);
    }
    return out;
  }

  /**
   * [ADMCP-01][ADMCP-02] Questions as one Markdown text. [style] short (two lines each) or full; [group] puts questions
   * that say the same together (중복 제보 묶기); [byKind] sorts them by kind under a heading each (문제 신고 first);
   * [base] is the developer app's address for links; [today] the date in the title of a text of more than one.
   */
  function inquiryCopyText(list, { style = "full", group = false, byKind = false, base = "", today = kstTime(new Date().toISOString()).slice(0, 10) } = {}) {
    const items = list.filter(Boolean);
    if (!items.length) return "";
    const sorted = byKind ? COPY_ORDER.flatMap((k) => items.filter((q) => q.kind === k)).concat(items.filter((q) => !COPY_ORDER.includes(q.kind))) : items;
    const groups = group ? sameGroups(sorted) : sorted.map((q) => [q]);
    const single = items.length === 1;
    const out = [];
    if (!single) {
      const tally = COPY_ORDER.map((k) => [k, items.filter((q) => q.kind === k).length]).filter(([, n]) => n).map(([k, n]) => `${COPY_KINDS[k]} ${n}`);
      out.push(`# 문의 ${items.length}건 (${today})`, "", tally.join(" · ") + (group && groups.length < items.length ? ` · 같은 내용 묶어 ${groups.length}가지` : ""), "");
    }
    let kindNow = null;
    groups.forEach((g, i) => {
      const n = i + 1;
      if (byKind && !single && g[0].kind !== kindNow) {
        kindNow = g[0].kind;
        const inKind = groups.filter((x) => x[0].kind === kindNow).reduce((s, x) => s + x.length, 0);
        out.push(`## ${COPY_KINDS[kindNow] ?? "문의"} (${inKind})`, "");
      }
      const level = single ? "## " : style === "short" ? `${n}. ` : byKind ? `### ${n}. ` : `## ${n}. `;
      if (g.length === 1) {
        out.push(...copyEntry(g[0], { style, base, head: level }), "");
        return;
      }
      // 같은 제보: the first one whole, the others by where they came from (and their words when they differ).
      const first = g[0];
      out.push(...copyEntry(first, { style, base, head: level }).map((l, j) => (j === 0 ? `${l} · 같은 제보 ${g.length}건` : l)));
      if (style !== "short") out.push("", "**같은 제보**");
      for (const q of g.slice(1)) {
        const meta = [copyDevice(q), kstTime(q.createdAt).slice(5), q.photos ? `사진 ${q.photos}장` : "", copyLink(q, base)].filter(Boolean).join(" · ");
        const same = askText(q).replace(/\s+/g, "") === askText(first).replace(/\s+/g, "");
        out.push(`${style === "short" ? "   " : ""}- ${meta}${same ? "" : `: ${oneLine(askText(q), 200)}`}`);
      }
      out.push("");
    });
    return out.join("\n").trimEnd();
  }

  /** [ADMCP-03] A 개발사항 made from copied questions: a title and the short text (the item outlives the questions). */
  function copyDevItem(list, base = "") {
    const items = list.filter(Boolean);
    const lead = items[0] ? copyTitle(items[0]) : "";
    const title = items.length > 1 ? `${oneLine(lead, 70)} 외 ${items.length - 1}건` : oneLine(lead, 100);
    const body = inquiryCopyText(items, { style: "short", group: true, byKind: true, base });
    return { title: [...title].slice(0, 100).join(""), body: [...body].length > 4000 ? `${[...body].slice(0, 3990).join("")}…` : body };
  }
  // ---- AT 1.29 끝 ----

  // ---- AX 1.30 시작 ----

  /**
   * AX 1.30 [ADMX-01] What every activity code says, so 최근 활동 and 활동 기록 show words and never a code. An unknown
   * code still reads as words: its first part (reward_x → 보상) and "처리".
   */
  const ACTION_NAMES = {
    login: "로그인", login_failed: "로그인 실패", logout_all: "모든 기기에서 로그아웃", password: "비밀번호 바꿈",
    second_on: "두 번째 확인 켬", second_off: "두 번째 확인 끔", // R131 1.31 [ADMX-16]
    invite: "관리자 초대", invite_remove: "초대 취소", join: "관리자 계정 만듦", admin_update: "관리자 권한·상태 바꿈", admin_remove: "관리자 삭제",
    adopt: "제보 채택", reject: "제보 거절", answer: "앱에 없는 방식에 답함", company_create: "회사 만듦", company_update: "회사 고침",
    alias_add: "다른 이름 넣음", alias_remove: "다른 이름 지움", merge: "회사 합침", rule_withdraw: "규칙 내림", rule_restore: "규칙 다시 올림", rule_promote: "이전 판을 다시 대표로",
    post_bulk: "토론 글 한 번에 처리", rate_bulk: "임금표 한 번에 처리",
    reward_paid: "보상 지급", reward_void: "보상 취소", reward_owed: "보상 대기로 되돌림", reward_auto: "보상 저절로 지급", settings: "채택 설정 바꿈",
    post_autohide: "신고로 글이 가려짐", post_show: "글 다시 보이기", post_hide: "글 가림", post_delete: "글 지움", post_mute: "글쓴이 30일 쓰기 막음",
    inquiry_reply: "문의에 답함", inquiry_status: "문의 상태 바꿈", inquiry_close: "문의 닫음", inquiry_open: "문의 다시 엶",
    inquiry_hide: "모두의 문의 숨김", inquiry_show: "모두의 문의 다시 보이기", inquiry_assign: "문의 맡음", inquiry_autohide: "신고로 모두의 문의가 가려짐",
    dev_create: "개발사항 만듦", dev_edit: "개발사항 고침", dev_status: "개발사항 처리", dev_bulk: "개발사항 한 번에 처리",
    dev_delete: "개발사항 지움", dev_restore: "개발사항 되살림", dev_link: "문의를 개발사항에 연결", dev_unlink: "개발사항에서 문의 뗌",
    dev_reply: "개발사항의 문의에 한 번에 답함", template_add: "자주 쓰는 답 추가", template_remove: "자주 쓰는 답 지움",
    launch_on: "정식 출시 켬(결제·광고)", launch_off: "정식 출시 끔", launch_settings: "정식 출시 설정 바꿈",
    device_link: "관리자 휴대폰 연결", device_approve: "관리자 휴대폰 승인", device_remove: "관리자 휴대폰 끊음",
    pass_move: "이용권을 새 휴대폰으로 옮김", trial_stop: "동의 없는 무료 체험의 다음 결제 멈춤",
    purchase_rebind: "구매를 새 휴대폰에 다시 이음", purchase_rebind_refused: "구매 다시 잇기 거절",
    notice_add: "공지 올림", notice_edit: "공지 고침", notice_remove: "공지 내림", switch_off: "기능 끔", switch_on: "기능 다시 켬",
    ops_done: "이달 할 일 마침", ops_undo: "이달 할 일 되돌림", share_remove: "공유 링크 내림",
    inquiry_bulk: "문의 한 번에 처리", reject_undo: "거절 되돌림", template_edit: "자주 쓰는 답 고침", sla: "답 목표 바꿈",
    inquiry_copy: "문의 개발로 복사함", rate_set: "임금표 처리", rate_adopted: "임금표 채택", rate_hidden: "임금표 숨김", rate_pending: "임금표 다시 검토로",
    invite_credit: "친구 초대 보상 쌓임", invite_capped: "친구 초대 보상 한도에 닿음", invite_refused: "친구 초대 보상 거절", invite_burst: "친구 초대가 몰려 멈춤",
    invite_settings: "친구 초대 규칙 바꿈",
    flag_force_off: "기능 강제로 끔", flag_force_on: "기능 강제로 켬", flag_clear: "기능 강제 풀기", flag_conditions: "기능 조건 바꿈", flag_range: "기능 앱 판 범위 바꿈",
    flag_check: "확인했어요", flag_uncheck: "확인 취소", flag_auto_on: "기능이 조건대로 켜짐", flag_auto_off: "기능이 조건대로 꺼짐",
    flag_expire: "강제 기한이 끝남", flag_migrate: "1.27 스위치를 기능 관리로 옮김", policy_edit: "처리방침 일정 고침",
    launch_recorded: "정식 출시일 기록", launch_thanks: "감사 보상 개월 바꿈", type_hide: "근무 유형 숨김", type_show: "근무 유형 다시 보임",
    auto_rest: "사용량이 많아 기능 자동 쉼", auto_resume: "자동 쉼 끝",
  };
  const ACTION_AREAS = { login: "로그인", admin: "관리자", invite: "친구 초대", reward: "보상", post: "토론 글", rate: "임금표", inquiry: "문의", dev: "개발사항",
    template: "자주 쓰는 답", device: "관리자 휴대폰", notice: "공지", ops: "이달 할 일", flag: "기능", rule: "규칙", company: "회사", alias: "다른 이름",
    purchase: "구매", type: "근무 유형", launch: "정식 출시", share: "공유 링크" };

  /** [ADMX-01] An activity code in words; a code nobody named yet reads "<area> 처리", never the code itself. */
  function actionName(code) {
    const c = String(code ?? "");
    if (Object.hasOwn(ACTION_NAMES, c)) return ACTION_NAMES[c];
    const area = ACTION_AREAS[c.split("_")[0]];
    return area ? `${area} 처리` : "그 밖의 처리";
  }

  /** [ADMX-01][ADMX-07] Now against before in percent, or null when there is nothing before to compare to. */
  function change(now, before) {
    if (!Number.isFinite(now) || !Number.isFinite(before) || before <= 0) return null;
    return Math.round(((now - before) / before) * 100);
  }

  /**
   * [ADMX-01] A sparkline: one small line over the last days (no style attributes, the CSP allows none). The last
   * point is a dot; the numbers are in the label for a screen reader. It stretches to the tile's width.
   */
  function spark(values, { width = 240, height = 28, name = "" } = {}) {
    const list = (values ?? []).map((v) => Math.max(0, Number(v) || 0));
    if (list.length < 2) return "";
    const top = Math.max(1, ...list);
    const x = (i) => ((i / (list.length - 1)) * (width - 4) + 2).toFixed(1);
    const y = (v) => (height - 3 - (v / top) * (height - 6)).toFixed(1);
    const points = list.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    const last = list.length - 1;
    return `<svg class="spark" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="${esc(`${name} 최근 ${list.length}일: ${list.join(", ")}`)}">`
      + `<polyline points="${points}"/><circle cx="${x(last)}" cy="${y(list[last])}" r="2.5"/></svg>`;
  }

  /**
   * [ADMX-02] The tokens of a changed field (server diffFacts: [{ t, kind: same|del|add }]) as marked words: what went
   * is struck through, what came is marked. [side] "before" keeps only same and del, "after" same and add.
   */
  function tokenHtml(tokens, side = "both") {
    return (tokens ?? []).filter((x) => side === "both" || x.kind === "same" || (side === "before" ? x.kind === "del" : x.kind === "add"))
      .map((x) => (x.kind === "same" ? esc(x.t) : x.kind === "del" ? `<del>${esc(x.t)}</del>` : `<ins>${esc(x.t)}</ins>`)).join("");
  }

  /**
   * [ADMX-03] The rows of the 여러 판 table: every field label of every candidate in the order they first appear,
   * each with its value per candidate and whether it differs from the reference (the active one, or the first).
   */
  function compareRows(candidates, ref = 0) {
    const order = [];
    const maps = candidates.map((c) => {
      const m = new Map();
      for (const [label, text] of c.facts ?? []) {
        if (!m.has(label)) m.set(label, text);
        if (!order.includes(label)) order.push(label);
      }
      return m;
    });
    const base = maps[ref] ?? new Map();
    return order.map((label) => {
      const values = maps.map((m) => (m.has(label) ? m.get(label) : null));
      const differs = values.map((v, i) => i !== ref && v !== (base.has(label) ? base.get(label) : null));
      return { label, values, differs, same: !differs.some(Boolean) };
    });
  }

  /** [ADMX-01] How hot a number is against its last days: "up" past twice the week's mean (3 at least), else "". */
  function trendTone(list) {
    if (!Array.isArray(list) || list.length < 2) return "";
    const today = list[list.length - 1];
    const past = list.slice(0, -1);
    const mean = past.reduce((n, v) => n + v, 0) / past.length;
    return today >= 3 && today >= 2 * Math.max(1, mean) ? "up" : "";
  }
  // ---- AX 1.30 끝 ----

  return {
    esc, ERRORS, errorText, ago, slaText, can, waited, maskPersonal, reviewCount, setupDiff, waitReason, permSummary, FILTERS, parseRoute, routeHash, cleanFilters, likeFirst, words,
    HUBS, TABS, tabVisible, visibleHubs, hubOf, tabBadge, hubBadge, waiting, bottomHubs, redirect, migrateHash, migratePrefs,
    devCopyText, DEV_NAMES, strength, fillTemplate, sortTemplates, wordDiff, csv, ticks, barChart, contrast, keyAction, KEYS, GO,
    paletteFilter, sequencer, appVersion,
    // AM 1.28 [FLAG-21][ADMUX-23][ADMUX-24]
    evalFlag, checkConditions, parseConditions, conditionText, conditionLabel, conditionMet, versionCompare, daysLeft, noticeWarnings, forceOf, flagDay, dateFrom, appRange,
    WORK_TYPES, WORK_NAMES, CONSENT_NAMES, PROFILE_NAMES, MANUAL_NAMES,
    // AT 1.29 [ADMCP-01][ADMCP-02][ADMCP-03]
    inquiryCopyText, copyDevItem, copyTitle, sameGroups, kstTime, COPY_KINDS,
    // AX 1.30 [ADMX-01][ADMX-02][ADMX-03][ADMX-07]
    ACTION_NAMES, actionName, change, spark, tokenHtml, compareRows, trendTone,
  };
}
