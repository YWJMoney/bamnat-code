// [SITE-01] bamnat.app: the public pages (이용약관, 처리방침, 광장 운영 규칙, …) under the app's own name.
//
// GitHub Pages (https://ywjmoney.github.io/-/) stays where the pages live and where the app reads its
// remote files: the app reads those with redirects off (RemoteFilesStore), so the Pages site must never
// move to a custom domain. This Worker shows the same pages at bamnat.app without moving them.

const ORIGIN = "https://ywjmoney.github.io/-";
const HOME = "https://bamnat.app";

/** privacy.html at the Pages root is another app's policy, not 밤낮's. */
const ELSEWHERE = { "/privacy.html": "/privacy/" };

/**
 * AdMob reads bamnat.app/app-ads.txt. The share server writes that line (APP_ADS_LINE); a custom domain may win over the
 * share server's bamnat.app/app-ads.txt route, so this Worker passes the request on to it rather than to Pages (404 there).
 */
const ADS = "https://s.bamnat.app/app-ads.txt";

/**
 * 2.0.0 [RVQ-55] 검토 SVC-39: 모든 답에 붙는 보안 머리. 공개 페이지라 다른 사이트 안에 넣어 보이지 않게(가로채기 막기) 하고,
 * https만 쓰고, 카메라·마이크·위치는 쓰지 않아요. 페이지마다 다른 스크립트(계산기·광고)가 있어 스크립트 허용 목록(CSP 전체)은 두지 않아요.
 */
export const SECURITY = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "strict-origin-when-cross-origin",
  "strict-transport-security": "max-age=31536000",
  "x-frame-options": "DENY",
  "content-security-policy": "frame-ancestors 'none'",
  "permissions-policy": "camera=(), microphone=(), geolocation=(), payment=()",
  "cross-origin-opener-policy": "same-origin",
};

function secure(response) {
  for (const [k, v] of Object.entries(SECURITY)) response.headers.set(k, v);
  return response;
}

/**
 * 2.0.0 [RVQ-55] 검토 SVC-39·U22-10: 없는 주소는 GitHub의 영어 404 대신 밤낮의 말로. 칸이와 한 줄, 처음으로 가는 단추 하나,
 * 자주 찾는 곳(이용약관·처리방침·광장 운영 규칙) 글자 링크. 밝은·어두운 모드, 글자 200%·좁은 폭에서도 감겨요.
 */
export function notFoundPage() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>찾는 페이지가 없어요 · 밤낮</title><style>
:root{--fg:#15192B;--muted:#5b5f73;--line:#e6e2d9;--bg:#F7F4EE;--card:#fff;--accent:#C2A36B}
@media (prefers-color-scheme:dark){:root{--fg:#F2ECE1;--muted:#a7a9b8;--line:#2a2f45;--bg:#15192B;--card:#1d2236}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 -apple-system,"Noto Sans KR","Apple SD Gothic Neo",sans-serif;word-break:keep-all;overflow-wrap:break-word}
main{max-width:640px;margin:0 auto;padding:48px 16px;text-align:center}h1{font-size:1.4rem;margin:16px 0 8px}p{margin:0 0 24px;color:var(--muted)}
.kani{display:block;margin:0 auto;width:120px;height:120px}.kani.dark{display:none}
@media (prefers-color-scheme:dark){.kani.light{display:none}.kani.dark{display:block}}
.go{display:inline-flex;align-items:center;justify-content:center;min-height:48px;padding:10px 24px;border-radius:999px;background:var(--accent);color:#15192B;font-weight:700;text-decoration:none}
.go:focus-visible,nav a:focus-visible{outline:3px solid var(--fg);outline-offset:3px}
nav{margin-top:32px;display:flex;flex-wrap:wrap;justify-content:center;gap:4px 8px}nav a{display:inline-flex;align-items:center;min-height:44px;padding:0 8px;color:var(--fg)}
</style></head><body><main>${KANI_LIGHT}${KANI_DARK}<h1>찾는 페이지가 없어요</h1><p>주소가 바뀌었거나 없어진 페이지예요. 처음 화면에서 다시 찾아 주세요.</p>
<a class="go" href="/">밤낮 처음으로</a>
<nav aria-label="자주 찾는 곳"><a href="/terms/">이용약관</a><a href="/privacy/">개인정보 처리방침</a><a href="/community/">광장 운영 규칙</a></nav></main></body></html>`;
}

/** 캐릭터 '말랑 칸이'(docs/brand/kani/kani-empty*.svg). 꾸밈이라 읽어 주지 않아요. */
const KANI_LIGHT = `<svg class="kani light" width="120" height="120" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130 130"><defs><clipPath id="bday148"><rect x="21" y="24" width="88" height="88" rx="24"/></clipPath></defs><g fill="none" stroke="#15192B" stroke-width="4" stroke-linecap="round"><path d="M20 80q-9 6-7 15" /><path d="M110 82q10 4 14 14" /></g><ellipse cx="49" cy="116" rx="11" ry="5" fill="#15192B"/><ellipse cx="81" cy="116" rx="11" ry="5" fill="#15192B"/><rect x="21" y="24" width="88" height="88" rx="24" fill="#F7F1E6"/><g clip-path="url(#bday148)"><rect x="21" y="24" width="88" height="27" fill="#E9B95A"/></g><circle cx="88" cy="37" r="5.5" fill="#FFF3C4"/><rect x="21" y="24" width="88" height="88" rx="24" fill="none" stroke="#15192B" stroke-width="3.5"/><rect x="44" y="14" width="9" height="20" rx="4.5" fill="#15192B"/><rect x="77" y="14" width="9" height="20" rx="4.5" fill="#15192B"/><ellipse cx="38" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/><ellipse cx="92" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/><g fill="#15192B" stroke="#15192B" stroke-linecap="round" stroke-linejoin="round"><circle cx="54" cy="77" r="5" stroke="none"/><circle cx="82" cy="77" r="5" stroke="none"/><path d="M60 87q7 5 14 0" stroke-width="3.5" fill="none"/></g></svg>`;
const KANI_DARK = `<svg class="kani dark" width="120" height="120" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130 130"><defs><clipPath id="bday148d"><rect x="21" y="24" width="88" height="88" rx="24"/></clipPath></defs><g fill="none" stroke="#C9D1F5" stroke-width="4" stroke-linecap="round"><path d="M20 80q-9 6-7 15" /><path d="M110 82q10 4 14 14" /></g><ellipse cx="49" cy="116" rx="11" ry="5" fill="#C9D1F5"/><ellipse cx="81" cy="116" rx="11" ry="5" fill="#C9D1F5"/><rect x="21" y="24" width="88" height="88" rx="24" fill="#F7F1E6"/><g clip-path="url(#bday148d)"><rect x="21" y="24" width="88" height="27" fill="#E9B95A"/></g><circle cx="88" cy="37" r="5.5" fill="#FFF3C4"/><rect x="21" y="24" width="88" height="88" rx="24" fill="none" stroke="#C9D1F5" stroke-width="3.5"/><rect x="44" y="14" width="9" height="20" rx="4.5" fill="#C9D1F5"/><rect x="77" y="14" width="9" height="20" rx="4.5" fill="#C9D1F5"/><ellipse cx="38" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/><ellipse cx="92" cy="88" rx="6.5" ry="3.8" fill="#FF9EA0" opacity=".7"/><g fill="#15192B" stroke="#15192B" stroke-linecap="round" stroke-linejoin="round"><circle cx="54" cy="77" r="5" stroke="none"/><circle cx="82" cy="77" r="5" stroke="none"/><path d="M60 87q7 5 14 0" stroke-width="3.5" fill="none"/></g></svg>`;

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.hostname !== "bamnat.app") return Response.redirect(HOME + url.pathname + url.search, 301);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return secure(new Response("허용되지 않은 요청이에요.", { status: 405, headers: { allow: "GET, HEAD", "content-type": "text/plain; charset=utf-8" } }));
    }
    if (ELSEWHERE[url.pathname]) return Response.redirect(HOME + ELSEWHERE[url.pathname], 301);
    if (url.pathname === "/app-ads.txt") {
      const ads = await fetch(ADS, { method: request.method, redirect: "manual", cf: { cacheEverything: true, cacheTtl: 300 } });
      return secure(new Response(ads.body, { status: ads.status, headers: { "content-type": "text/plain; charset=utf-8" } }));
    }
    const upstream = await fetch(ORIGIN + url.pathname + url.search, {
      method: request.method,
      redirect: "manual",
      cf: { cacheEverything: true, cacheTtl: 300 },
    });
    if (upstream.status === 404) {
      // GitHub의 404 본문은 버리고 밤낮의 안내로. HEAD는 본문 없이 같은 머리만.
      await upstream.body?.cancel?.();
      const body = request.method === "HEAD" ? null : notFoundPage();
      return secure(new Response(body, { status: 404, headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=300" } }));
    }
    const response = new Response(upstream.body, upstream);
    // GitHub sends /terms to /terms/ on its own host; keep the visitor on bamnat.app.
    const location = upstream.headers.get("location");
    if (location) response.headers.set("location", location.replace(/^https?:\/\/ywjmoney\.github\.io\/-/, HOME));
    return secure(response);
  },
};
