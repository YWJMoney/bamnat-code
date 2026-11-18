// 1.40 [RVD-07][RVD-05] 사람이 먼저 봐야 하는 문의(askOwner)와 사람 없이 나가면 안 되는 답(replyRisk)을 가리는 한 곳.
// 검토 2026-11-12 SVC-02·ADMX-03·TEST-11: 띄어쓰기·폭 없는 글자·전각·자모 풀어쓰기·영어로 빠져나갔고, 교대 근무자의 흔한 말
// (경찰 교대, 달력 구독, 알람 해지, '더해지는', 직원이 쓴)이 붙잡혔어요. 글을 고른 뒤(plainAsk) 견주고, 흔한 말은 짝으로 좁혀요.
// 예문 장부: test/ask-corpus.json. 실제 문의 원문은 넣지 않아요. 바깥 모듈을 부르지 않아 어디서든 불러요.

const AC_CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
const AC_JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
const AC_JONG = ["", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"];
const AC_PAIR = { ㅗㅏ: "ㅘ", ㅗㅐ: "ㅙ", ㅗㅣ: "ㅚ", ㅜㅓ: "ㅝ", ㅜㅔ: "ㅞ", ㅜㅣ: "ㅟ", ㅡㅣ: "ㅢ" };

/** 풀어 쓴 자모(ㅎㅘㄴㅂㅜㄹ)를 글자(환불)로. 모음이 없는 자모(ㅅㅂ, ㅋㅋ)는 그대로예요. */
function acComposeJamo(t) {
  if (!/[ㄱ-ㅣ]/.test(t)) return t;
  const c = [...t];
  let out = "";
  for (let i = 0; i < c.length; i++) {
    const cho = AC_CHO.indexOf(c[i]);
    let v = c[i + 1];
    if (cho < 0 || !v || AC_JUNG.indexOf(v) < 0) {
      out += c[i];
      continue;
    }
    let j = i + 2;
    if (c[j] && AC_PAIR[v + c[j]]) v = AC_PAIR[v + c[j++]];
    let jong = 0;
    const k = AC_JONG.indexOf(c[j]);
    if (k > 0 && !(c[j + 1] && AC_JUNG.indexOf(c[j + 1]) >= 0)) {
      jong = k;
      j++;
    }
    out += String.fromCharCode(0xac00 + (cho * 21 + AC_JUNG.indexOf(v)) * 28 + jong);
    i = j - 1;
  }
  return out;
}

/** 견주기 전에 고른 글: 전각·호환 글자(NFKC), 작은 글자, 폭 없는 글자 지움, 자모 합침. spaced는 기호를 빈칸 하나로, tight는 빈칸까지 지운 것. */
export function plainAsk(text) {
  // NFKC turns ㅎ·ㅅ (호환 자모) into 첫소리 자모, so 자모 are put together first and kept out of it.
  const raw = String(text ?? "").replace(/[­​-‏⁠-⁤﻿]/g, "").replace(/[ﾠ-ￜ]/g, (c) => c.normalize("NFKC"));
  const t = acComposeJamo(raw).replace(/[^ㄱ-ㆎ]+/g, (m) => m.normalize("NFKC")).toLowerCase().normalize("NFC");
  const spaced = t.replace(/[^0-9a-z가-힣ㄱ-ㅣ?]+/g, " ").replace(/\s+/g, " ").trim();
  return { spaced, tight: spaced.replace(/[\s?]+/g, "") };
}

// 빈칸이 있는 글에 견주는 식. 흔한 말(구독·해지·요금·경찰·청소년·탈퇴·사기·토큰·인증서·우회·무시)은 짝이 있을 때만이에요.
const AC_MONEY = new RegExp([
  "환불", "결제", "청약\\s*철회", "구독\\s*(료|비|취소|해지|결제|환불|끊)", "(프로|이용권|정기)\\s*(구독|결제|해지|취소|환불)", "이용권", "구매", "주문\\s*번호", "영수증",
  "과금", "청구", "요금\\S*\\s*.{0,6}(두\\s*번|이중|잘못|더\\s*나|환불|빠져|나갔)", "해지\\S*\\s*.{0,6}(구독|결제|이용권|프로)",
  "돈\\S*\\s*.{0,8}(돌려|빠져|나갔|취소)", "두\\s*번\\s*(빠져|나갔)", "보상\\s*(해|받|요구|지급|을|좀)", "배상",
  "refund", "charged?\\b", "payment", "\\bpaid\\b", "billing", "subscription",
].join("|"));
const AC_LAW = new RegExp([
  "불법\\s*촬영", "몰카", "명예\\s*훼손", "고소", "고발", "경찰\\s*(에|서|한테|을|를)?\\s*(신고|부르|부를|불러|연락|출동)", "경찰서", "변호사", "소송", "소비자\\s*(원|보호|센터)", "공정\\s*위",
  "법적\\s*(대응|조치|책임)", "성희롱", "성추행", "협박", "스토킹", "자해", "자살", "죽고\\s*싶", "(청소년|미성년자?)\\S*\\s*.{0,12}(성|협박|피해|신고|유해|만남|사진)",
  "\\bsue\\b", "lawsuit", "lawyer", "police",
].join("|"));
const AC_PRIVACY = new RegExp([
  "개인\\s*정보", "(회원|계정|서비스|앱|밤낮)\\s*탈퇴", "계정\\s*(삭제|지워|없애)", "(내|제|저의|나의)\\s*(개인\\s*)?(정보|데이터|기록|자료)\\S*\\s*.{0,10}(지워|삭제|없애|열람|보여|달라|파기)",
  "열람\\s*(요청|청구)", "delete\\s+(my|all)", "my\\s+(data|account|information)", "privacy", "gdpr",
].join("|"));
const AC_ANGRY = new RegExp([
  "화가\\s*(나|납)", "개\\s*짜증", "짜증\\s*(나\\s*죽|폭발|지대)", "최악", "(^|[^가-힣])사기(꾼|\\s*(아니|야|네|다|치|잖|같|당|극))", "어이\\s*(가\\s*)?없", "열\\s*받", "빡치",
  "별\\s*(점\\s*)?(1|하나|한\\s*개)\\s*(점|줍|준|드|남|박)", "별점\\s*1", "신고\\s*하겠", "가만\\s*안", "씨발", "시발", "ㅅㅂ", "ㅆㅂ", "좆", "병신", "개같", "미친\\s*(앱|거|놈)", "fuck", "shit",
].join("|"));
const AC_TERMS = /약관|처리\s*방침|개인정보\s*처리/;
const AC_SECURITY = new RegExp([
  "소스\\s*코드", "코드\\s*(를|좀|도)?\\s*(보여|알려|공개|보내|줘|주세요|달라)", "api\\s*(키|key)", "(api|access|접근|인증|관리자|서버|운영|bearer)\\s*토큰", "\\btoken", "비밀\\s*(값|키)",
  "(관리자|서버|운영|db)\\s*(비밀\\s*번호|비번)", "암호\\s*키", "관리자\\s*(페이지|화면|앱|주소|계정|권한|비번|비밀|로그인|모드)", "admin", "password", "해킹", "해커", "\\bhack",
  "(결제|프로|광고|보안|제한|인증|잠금|관리자|검사|필터)\\S*\\s*.{0,6}우회", "우회\\S*\\s*.{0,6}(결제|프로|광고|보안|제한|인증|잠금|검사|필터)", "bypass", "취약", "탈옥", "jailbreak",
  "프롬프트", "prompt", "시스템\\s*(지시|명령|메시지|설정)", "내부\\s*(설정|규칙|지침|정보|구조)", "(지시|명령|규칙|지침)\\S*\\s*.{0,10}(무시|잊|출력)", "(무시|잊)\\S*\\s*.{0,6}(지시|명령|규칙|지침)",
  "지침", "개발자\\s*모드", "developer\\s*mode", "ignore\\s+(all|previous|the|your|above)", "instructions", "서버\\s*(주소|구조|코드|정보|위치|ip)", "데이터\\s*베이스", "database",
  "db\\s*(주소|구조|접속|내용)", "\\bsql\\b", "디컴파일", "리버스\\s*엔지", "백도어", "난독화", "(서버|ssl|tls|서명)\\s*인증서", "서명\\s*키",
].join("|"));
// 묻는 꼴일 때만: '직원이에요'(자기소개)·'직원이 쓴'·'기계가 고장'·'AI가 잘못 읽어요'는 그냥 문의예요.
// 2.0.0 [RVR-16] (검토 TEST-11) 예문 장부를 늘리다 찾은 틈: '경찰 부를 거예요', '자동 답장이에요?', '기계가 답하는 건가요', 'claude 맞지?'.
const AC_ASKING = "(세요|신가요|십니까|인가요|인가|인지|입니까|이야|야\\s*\\?|냐|맞나|맞아|맞죠|맞지\\s*\\?|맞나요|이죠|죠|임|이지|이니|이에요\\s*\\?|예요\\s*\\?|에요\\s*\\?|이세요)";
const AC_HUMAN = new RegExp([
  `(사람|상담원|상담사|직원|운영자)\\s*(이|인)?\\s*${AC_ASKING}`, "(실제|진짜)?\\s*사람이\\s*(답|쓰|쓴|적|하는|보는)",
  `(ai|에이\\s*아이|인공\\s*지능|챗\\s*봇|gpt|llm|클로드|claude|로봇|기계|봇)\\s*(이|가)?\\s*${AC_ASKING}`, "(ai|에이\\s*아이|인공\\s*지능|챗\\s*봇|gpt|llm|클로드|claude|로봇|기계|봇)\\s*(가|이|로)\\s*(답|쓴|쓰|적)",
  "자동\\s*(답|응답|답장|답변)\\s*(이|인|이에|이야|이세|맞|인가|이죠)", "자동\\s*응답", "are\\s+you\\s+(a\\s+)?(human|bot|robot|ai|real|person)", "(human|real\\s+person)\\s+or\\s+(a\\s+)?(bot|ai)", "chat\\s*gpt",
].join("|"));
// 빈칸을 다 지운 글에 견주는 낱말: 띄어 쓰거나 기호를 끼워도 뜻이 하나뿐인 말만(다른 낱말에 섞여 우연히 생기지 않는 것).
const AC_TIGHT = {
  money: /환불|청약철회|refund/,
  law: /소비자원|변호사|법적대응|성희롱|불법촬영/,
  privacy: /개인정보|회원탈퇴/,
  security: /소스코드|프롬프트|개발자모드|jailbreak|탈옥/,
  human: /사람이세요|사람인가요|사람입니까|사람이죠|자동응답|상담원이세요|챗봇이세요/,
};

const AC_RULES = [
  ["money", "환불·결제·보상", AC_MONEY],
  ["law", "법·안전 신고", AC_LAW],
  ["privacy", "개인정보 요청", AC_PRIVACY],
  ["angry", "크게 화난 민원", AC_ANGRY],
  ["terms", "약관·처리방침 질문", AC_TERMS],
  // 1.39 [LOOP-38] 사용자 11-09 '코드 등 보안을 침해하는 답은 절대 금지, 우회하는 문의가 와도 답하지 않고 관리자 확인으로 남겨 답을 미룰 것'.
  ["security", "보안·코드·우회 요청", AC_SECURITY],
  // 사용자 11-08·11-09 '사람이냐고 묻는 문의는 답하지 말고 알림', 'AI라는 것을 밝히지 않기'.
  ["human", "사람·AI인지 묻는 문의", AC_HUMAN],
];

/** 한국어가 거의 없는 문의: 낱말 표가 다 담지 못하니 사람이 먼저 봐요(검토 SVC-02 (나)). */
function acForeign(spaced) {
  const letters = spaced.replace(/[\s0-9?]+/g, "");
  if ([...letters].length < 8) return false;
  const hangul = (letters.match(/[가-힣ㄱ-ㅣ]/g) ?? []).length;
  return hangul / [...letters].length < 0.2;
}

// 2.0.0 [RVR-16] (검토 T60-04) 근로기준법의 '보상 휴가'(제57조)·보상 시간을 묻는 글에서는 '보상'이 돈 요구가 아니에요:
// "보상 휴가는 어디에 적나요? 보상을 받았는데…". 그 글에서는 '보상'만 빼고 견줘요(환불·결제 같은 다른 돈 말은 그대로 잡아요).
const AC_LABOR_COMP = /보상\s*(휴가|휴무|시간|근무|휴일)/;

/** 2.0.0 [RVA-41] 검토 U24-05·I24a-05: 무슨 말 때문에 걸렸는지 한 마디(관리자 화면의 "'해지'라는 말 때문에"). */
const acWord = (m) => (m ? m[0].trim().replace(/\s+/g, " ").slice(0, 24) || null : null);

/** 문의 글이 사람이 먼저 봐야 하는지: { key, name, word } 또는 null. word는 걸린 말(관리자에게 이유로 보여요). */
export function askOwnerOf(text) {
  const raw = String(text ?? "");
  const { spaced, tight } = plainAsk(raw);
  const labor = AC_LABOR_COMP.test(spaced);
  for (const [key, name, re] of AC_RULES) {
    const s = key === "money" && labor ? spaced.replace(/보상/g, " ") : spaced;
    const m = re.exec(s) ?? AC_TIGHT[key]?.exec(tight);
    if (m) return { key, name, word: acWord(m) };
  }
  // 낱말 사이에 다른 글자(폭 없는 글자·전각)가 아닌 다른 문자(일본어 등)만 있으면 spaced가 비어요.
  if (acForeign(spaced) || (!spaced && raw.replace(/\s+/g, "").length >= 4)) return { key: "foreign", name: "한국어가 아닌 문의" };
  return null;
}

/**
 * 1.36 [INQ-07] 결제·환불 기록(3년 보관)인지. 돈 이야기 가운데 분쟁이 될 것만이에요(보상·배상은 아니에요). [RVD-05] '더해지는'의 '해지'는 아니에요.
 * 2.0.0 [RVQ-30] 검토 SVC-21: 낱말 하나로 정하니 빠뜨리기도(프로 샀는데·광고 제거권·돈만 나가고·Play 주문 번호 GPA.…), 지나치게 잡기도(휴가 결제
 * 올렸어요·수당 청구·영수증 사진·카드 결제일·장보기 구매) 했어요. 이제 ① 틀림없는 말(환불·주문 번호·이중 결제·우리 상품과 돈이 함께)은 바로,
 * ② 흔한 낱말(결제·구매·영수증·청구·과금)은 회사 결재·수당 청구·가계부처럼 다른 뜻으로 쓰인 자리를 뺀 뒤에도 남을 때만이에요.
 * 문의 종류(앱의 '결제·환불', kind billing)와 관리자 표시는 inquiries.js billingRecordOf가 함께 봐요.
 */
export function isBillingRecord(text) {
  const { spaced, tight } = plainAsk(text);
  if (AC_BILL_SURE.test(spaced) || AC_TIGHT.money.test(tight) || /gpa\d{4}/.test(tight)) return true;
  return AC_BILL_WORD.test(spaced.replace(AC_BILL_NOT, " "));
}
const AC_BILL_PRODUCT = "(프로(?!필|그램|젝트|세스|모션|포즈)|이용권|광고\\s*(제거|없애|없는|안\\s*보)|프리미엄|유료|1\\s*년\\s*권|연간\\s*권)";
const AC_BILL_SURE = new RegExp([
  "환불", "청약\\s*철회", "주문\\s*번호", "gpa\\s*\\d{4}", "이중\\s*(결제|청구|과금)", "(두|2)\\s*번\\s*(결제|청구|과금|빠져|빠졌|나갔)",
  "돈\\S*\\s*.{0,4}(돌려|빠져|빠졌|나갔|나가고|날렸|냈는데|내고)", "구독\\s*(료|비|취소|해지|결제|환불|끊)", "(프로|이용권|정기)\\s*(구독|결제|해지|취소|환불)",
  "해지\\S*\\s*.{0,6}(구독|결제|이용권|프로)", `${AC_BILL_PRODUCT}\\S*\\s*.{0,12}(결제|구매|구입|샀|사서|사고|산\\s|과금|청구|영수증)`,
  `(결제|구매|구입|샀|사서|돈\\s*(을\\s*)?(내|냈))\\S*\\s*.{0,12}${AC_BILL_PRODUCT}`, "인앱\\s*(결제|구매)",
  "(구글\\s*)?(play|플레이)\\s*(스토어)?\\s*.{0,6}(결제|구매|주문|영수증|환불|청구)", "요금\\S*\\s*.{0,6}(두\\s*번|이중|잘못|더\\s*나|환불|빠져|나갔)",
  "refund", "charged?\\b", "payment", "subscription", "\\bpaid\\b", "billing",
].join("|"));
const AC_BILL_WORD = /결제|구매|구입|영수증|과금|청구/;
// 같은 낱말이지만 다른 뜻: 회사 결재(흔한 오타 '결제'), 수당·비용 청구, 가계부의 영수증·카드 결제일·장보기 구매.
const AC_BILL_NOT = new RegExp([
  "(휴가|연차|반차|월차|품의|기안|상신|서류|문서|전자|팀장|부장|과장|차장|실장|상사|윗선|사장님)\\S*\\s*.{0,6}결제",
  "결제\\s*(라인|권자|올려|올렸|올리|받아|받았|받고|받으|서류|문서|판|요청\\s*드|대기\\s*중인\\s*(휴가|서류))",
  "(수당|비용|경비|출장비|식대|교통비|임금|급여|월급|퇴직금|실업\\s*급여|보험금|보험|연차비|치료비|산재)\\S*\\s*.{0,6}청구",
  "영수증\\S*\\s*.{0,8}(사진|찍|인식|스캔|읽|넣|등록|첨부|가계부|지출)", "(가계부|지출|장부)\\S*\\s*.{0,10}(영수증|구매|구입|결제)",
  "카드\\s*(값|대금)?\\s*.{0,4}결제", "결제\\s*(일|예정|대금|계좌|수단을\\s*가계부)", "(장보기|마트|쇼핑|물건|식료품)\\S*\\s*.{0,8}(구매|구입)", "(구매|구입)\\s*(목록|품목|리스트)",
].join("|"), "g");

/** 2.0.0 [RVA-41] 결제·환불 기록으로 잡은 말(없으면 null). [RVQ-30] isBillingRecord와 같은 잣대예요. */
export function billingWordOf(text) {
  const { spaced, tight } = plainAsk(text);
  return acWord(AC_BILL_SURE.exec(spaced)) ?? acWord(AC_TIGHT.money.exec(tight)) ?? (/gpa\d{4}/.test(tight) ? "주문 번호" : null)
    ?? acWord(AC_BILL_WORD.exec(spaced.replace(AC_BILL_NOT, " ")));
}

// 1.39 [LOOP-38] 사람 없이 나갈 답에 들어가면 안 되는 말. [RVD-07] 기능 이름의 'AI 근무표 읽기', 근무 이름 'D1', '앱 잠금 비밀번호' 안내는 괜찮아요.
const AC_REPLY_AI = new RegExp([
  "(^|\\s)a\\s?i\\s*(상담|답|응답|도우미|어시스턴트|비서|챗|가\\s*(답|쓴|도와|드)|입니다|예요|이에요|라서)", "(저는|제가|저희는|이\\s*답은?|이\\s*글은?)\\s*.{0,15}(\\bai\\b|에이\\s*아이|인공\\s*지능|챗\\s*봇|봇|로봇|프로그램|기계|자동)",
  "에이\\s*아이", "인공\\s*지능", "챗\\s*봇", "gpt", "\\bllm\\b", "claude", "클로드", "anthropic", "언어\\s*모델", "자동\\s*(으로\\s*)?(생성된|작성된|만든)\\s*(답|글|메시지)", "자동\\s*(답변|응답)", "사람이\\s*아니",
].join("|"));
const AC_REPLY_SECRET = new RegExp([
  "bamnat_", "bearer\\s", "sk-[a-z0-9]", "ghp_", "wrangler", "cloudflare", "\\bd1\\s*(데이터\\s*베이스|database|db|바인딩|binding)", "\\b(kv|r2)\\s*(저장소|버킷|bucket|namespace|네임스페이스)",
  "/api/", "workers\\.dev", "pages\\.dev", "api\\.bamnat", "admin\\.", "/admin", "소스\\s*코드", "\\.js\\b", "\\.kt\\b", "github", "깃허브", "(api|access|bearer|관리자|인증|운영)\\s*토큰",
  "비밀\\s*(값|키)", "(관리자|서버|운영|db)\\s*(비밀\\s*번호|비번)", "api\\s*(키|key)", "서버\\s*(주소|구조|코드)", "데이터\\s*베이스", "database", "\\bsql\\b", "관리자\\s*(페이지|계정|주소|화면|앱|모드)",
  // 열쇠 값처럼 생긴 것: 숫자와 영문이 섞인 24자 이상.
  "(?<![a-z0-9])(?=[a-z0-9_-]*\\d)(?=[a-z0-9_-]*[a-z])[a-z0-9_-]{24,}(?![a-z0-9])",
].join("|"));

/** 사람 없이 나가면 안 되는 답이면 그 이유 이름, 아니면 null. */
export function replyRiskOf(text) {
  const t = String(text ?? "").normalize("NFKC").toLowerCase().replace(/[­​-‏⁠-⁤﻿]/g, "");
  if (AC_REPLY_AI.test(t)) return "답에 AI·자동 답 표현";
  if (AC_REPLY_SECRET.test(t)) return "답에 보안 정보";
  return null;
}
