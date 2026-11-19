/**
 * 2.0.0 [RVA-63] HQ-124: 되풀이되는 '앱이 닫혀요' 문의를 한 묶음으로.
 *
 * 1.39.0에서 같은 보낸 분·같은 기기·안드로이드 17로 '앱이 갑자기 닫힘'이 나흘 동안 네 건 왔는데 따로따로 보여 되풀이를 늦게 알았고,
 * 네 건 모두 멈춤 기록이 없다는 것도 하나씩 열어야 알았어요. 그래서
 *  ① 문의마다 함께 온 점검 기록에서 '앱 멈춤' 칸을 읽어 '멈춤 기록 있음·없음·점검 기록 없이 옴'을 알려 주고,
 *  ② 같은 보낸 분, 또는 같은 기기 이름·안드로이드 판·앱 판에서 7일 안에 닫힘 문의가 2건 넘게 오면 한 묶음으로 올려요.
 *  ③ 그 묶음은 숫자 › 개선 신호('같은 판에서 닫힘 되풀이')로도 가요(observe.js).
 * 보낸 분 번호(device)는 화면으로 보내지 않고 짧은 표지(같은 값이면 같은 표지)로만 써요.
 */

export const CRASH = { days: 7, min: 2 };

/** 앱이 닫히거나 튕긴다는 말. 앱 동작 주제(INQUIRY_TOPICS app)보다 좁게: 느려요·위젯·업데이트는 아니에요. */
export const CLOSE_WORDS = /닫혀|닫힘|닫혔|닫히|튕|팅겨|팅김|강제\s*종료|응답\s*없|(앱|밤낮|화면|자꾸|갑자기|저절로|혼자|계속|그냥).{0,12}(꺼져|꺼짐|꺼졌|꺼지|꺼집|종료|멈춰|멈췄|멈춤|나가)/;

/** 오류를 알리는 문의 종류(아이디어·회사 방식·결제·스튜디오 요구는 아니에요). */
const CLOSE_KINDS = new Set(["bug", "question", "other"]);

/** 앱이 점검 기록에 쓰는 칸 이름(DiagnosticsScreen.kt STOP_SECTION)과 비었을 때의 말. */
const STOP_SECTION = "[앱 멈춤]";
const EMPTY = "기록 없음";
const UNREAD = "(못 읽음)";

export const CRASH_RECORD_NAMES = { has: "멈춤 기록 있음", none: "멈춤 기록 없음", unread: "멈춤 기록 못 읽음", nostop: "멈춤 칸 없음", unsent: "점검 기록 없이 옴" };

/** 닫힘 문의인지: 오류 종류이고, 말에 닫힘이 있거나 앱이 '앱 멈춤'을 앞에 둔 점검 기록을 붙였을 때. */
export function isCloseAsk(kind, text, info) {
  if (!CLOSE_KINDS.has(kind)) return false;
  return CLOSE_WORDS.test(String(text ?? "")) || String(info?.diag ?? "").startsWith(STOP_SECTION);
}

/**
 * 점검 기록의 '앱 멈춤' 칸: has(멈춘 기록이 있음) · none(멈춤 없음) · unread(앱이 그 칸을 읽지 못함) · nostop(점검 기록은 왔지만 그 칸이 없음,
 * 2.0.0 [RVA-64]) · unsent(점검 기록을 안 보냄).
 * 칸은 `[제목]` 줄로 시작해 빈 줄에서 끝나요.
 */
export function crashRecord(diag) {
  const d = String(diag ?? "");
  const at = d.startsWith(STOP_SECTION) ? 0 : d.indexOf(`\n${STOP_SECTION}`);
  if (at < 0) return d.trim() ? "nostop" : "unsent";
  const body = d.slice(d.indexOf(STOP_SECTION, at) + STOP_SECTION.length).split(/\n\s*\n/)[0];
  const all_ = body.split("\n").map((l) => l.trim()).filter(Boolean);
  // 2.0.0 L [RVL-84]: 앱이 읽지 못한 것은 '(못 읽음) '으로 시작해요. 멈춘 줄로 세지 않아요.
  const lines = all_.filter((l) => !l.startsWith(UNREAD));
  if (!lines.length) return all_.length ? "unread" : "unsent";
  // 2.0.0 모양: '가장 최근 기록:' 덩어리(있으면 맨 앞), '남은 기록: N개', 안드로이드 11 이상은 '최근 30일: 멈춤 N회 · …'가 늘 있어요.
  // 멈춤이 없다는 것은 '가장 최근 기록:'이 없고 남은 기록이 0개이며 최근 30일 숫자가 모두 0일 때예요.
  const left = lines.find((l) => /^남은 기록:/.test(l));
  if (left !== undefined || lines.some((l) => /^가장 최근 기록:/.test(l))) {
    const counts = lines.filter((l) => /^최근 \d+일:/.test(l)).flatMap((l) => [...l.slice(l.indexOf(":") + 1).matchAll(/(\d+)\s*(?:회|번)/g)].map((m) => Number(m[1])));
    const kept = Number(left?.match(/(\d+)/)?.[1] ?? 0);
    return lines.some((l) => /^가장 최근 기록:/.test(l)) || kept > 0 || counts.some((n) => n > 0) ? "has" : "none";
  }
  // 1.39까지의 모양: 멈춘 줄들이거나 '기록 없음' 한 줄.
  return lines.every((l) => l === EMPTY) ? "none" : "has";
}

/** 문의 한 줄에 붙일 것: 닫힘 문의일 때만 { record, recordName }. */
export function crashOf(kind, text, info) {
  if (!isCloseAsk(kind, text, info)) return null;
  const record = crashRecord(info?.diag);
  return { record, recordName: CRASH_RECORD_NAMES[record] };
}

/**
 * 2.0.0 [RVA-64] HQ-132: 열쇠로 읽을 때 점검 기록 자리에 남기는 표식. 내용은 가리고 줄 수와 '앱 멈춤' 칸의 셈(위 crashRecord)만 알려요.
 * 기록이 안 왔으면 null(열쇠로도 '안 옴'을 알 수 있게 자리는 비워 둬요). 멈춘 곳 줄(예외 종류@코드 자리)은 싣지 않아요(사용자 결정 전).
 */
export function diagMark(diag) {
  const d = String(diag ?? "");
  if (!d.trim()) return null;
  const n = d.split("\n").filter((l) => l.trim()).length;
  return `가려짐: 운영자만 봐요 · ${n}줄 · ${CRASH_RECORD_NAMES[crashRecord(d)]}`;
}

/** "1.39.0 (53)" → "1.39.0". */
export const appOf = (v) => String(v ?? "").match(/^\s*(\d{1,3}(?:\.\d{1,3}){0,3})/)?.[1] ?? null;

/** "17 (API 37)" → "17". */
export const androidMajor = (v) => String(v ?? "").match(/^\s*(\d{1,3})/)?.[1] ?? null;

/** 짧은 표지(FNV-1a 32비트). 같은 보낸 분이면 같은 표지, 번호 자체는 남지 않아요. */
export function tag(s) {
  let h = 0x811c9dc5;
  for (const c of String(s)) {
    h ^= c.codePointAt(0);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(36);
}

const sameDevice = (d) => typeof d === "string" && d.length > 0 && !d.startsWith("gone");

/**
 * 7일 안 닫힘 문의 묶음. items: { id, device, model, android, app, at(ms 또는 ISO), record }.
 * 같은 보낸 분 묶음이 먼저. 같은 기기·안드로이드·앱 판 묶음은 같은 보낸 분 묶음 밖의 문의가 [min]건 넘을 때만(한 분의 되풀이에 다른 분 한 건이
 * 붙은 것은 같은 이야기라 두 번 보이지 않게).
 * 결과는 마지막 날이 늦은 것부터: { key, by, label, model, android, app, people, n, first, last, ids(오래된 것부터), noRecord }.
 */
export function crashRepeats(items, now, { days = CRASH.days, min = CRASH.min } = {}) {
  const since = now - days * 86_400_000;
  const ms = (t) => (typeof t === "number" ? t : Date.parse(t));
  items = items.map((x) => ({ ...x, app: appOf(x.app) }));
  const recent = items.filter((x) => ms(x.at) >= since && ms(x.at) <= now).sort((a, b) => ms(a.at) - ms(b.at) || String(a.id).localeCompare(String(b.id)));
  const byDevice = new Map();
  const byModel = new Map();
  for (const x of recent) {
    if (sameDevice(x.device)) {
      const k = `same:${tag(x.device)}`;
      if (!byDevice.has(k)) byDevice.set(k, []);
      byDevice.get(k).push(x);
    }
    const major = androidMajor(x.android);
    if (x.model && major && x.app) {
      const k = `model:${tag(`${x.model}|${major}|${x.app}`)}`;
      if (!byModel.has(k)) byModel.set(k, []);
      byModel.get(k).push(x);
    }
  }
  const out = [];
  const devSets = [];
  for (const [key, xs] of byDevice) {
    if (xs.length < min) continue;
    devSets.push(new Set(xs.map((x) => x.id)));
    out.push(group(key, "same", xs));
  }
  for (const [key, xs] of byModel) {
    if (xs.length < min) continue;
    if (xs.filter((x) => !devSets.some((s) => s.has(x.id))).length < min) continue;
    out.push(group(key, "model", xs));
  }
  return out.sort((a, b) => ms(b.last) - ms(a.last) || b.n - a.n);

  function group(key, by, xs) {
    const last = xs[xs.length - 1];
    const apps = [...new Set(xs.map((x) => x.app).filter(Boolean))];
    const people = new Set(xs.map((x) => (sameDevice(x.device) ? x.device : `?${x.id}`))).size;
    const major = androidMajor(last.android);
    const where = [last.model, major ? `안드로이드 ${major}` : null, apps.length === 1 ? apps[0] : apps.length ? `${apps.length}개 판` : null].filter(Boolean).join(" · ");
    return {
      key, by, label: by === "same" ? `같은 보낸 분${where ? ` · ${where}` : ""}` : `${where} · 보낸 분 ${people}명`,
      model: last.model ?? null, android: major, app: apps.length === 1 ? apps[0] : null, people, n: xs.length,
      first: xs[0].at, last: last.at, ids: xs.map((x) => x.id), noRecord: xs.filter((x) => x.record !== "has").length,
    };
  }
}

/** 문의마다 몇 번째인지: 같은 보낸 분 묶음을 먼저 봐요. Map(id → { key, nth, n }). */
export function repeatPlaces(groups) {
  const out = new Map();
  for (const g of [...groups].sort((a, b) => (a.by === b.by ? 0 : a.by === "same" ? -1 : 1))) {
    g.ids.forEach((id, i) => { if (!out.has(id)) out.set(id, { key: g.key, nth: i + 1, n: g.n }); });
  }
  return out;
}

/**
 * 멈춤 기록이 없을 때 넣는 답 초안. 답장 규칙(CLAUDE.md 3-2): 상담원 말투, '밤낮 운영팀', 보안 정보·자동 답이라는 말 없음.
 * 앱 길: 설정 › 검색 '알람 점검' › ⋮ 더 보기 › 보고서 복사(DiagnosticsScreen).
 */
export const CRASH_ASK_DRAFT = [
  "불편을 드려 정말 죄송해요. 앱이 갑자기 닫히는 일이 되풀이되었다니 많이 답답하셨을 것 같아요.",
  "",
  "원인을 정확히 찾으려면 휴대폰에 남은 '앱 멈춤' 기록이 꼭 필요해요. 번거로우시겠지만 아래 순서로 한 번만 보내 주실 수 있을까요?",
  "",
  "1. 밤낮 › 설정에서 위쪽 검색에 '알람 점검'을 입력해 열어 주세요.",
  "2. 오른쪽 위 ⋮(더 보기) › '보고서 복사'를 눌러 주세요.",
  "3. 이 문의에 답장으로 붙여 넣어 보내 주세요.",
  "",
  "앱이 닫힌 직후에 복사해 주시면 가장 많은 단서가 남아 있어요. 받는 대로 바로 살펴보고 다시 말씀드릴게요.",
  "",
  "밤낮 운영팀",
].join("\n");
