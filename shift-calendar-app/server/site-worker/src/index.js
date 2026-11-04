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

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.hostname !== "bamnat.app") return Response.redirect(HOME + url.pathname + url.search, 301);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("허용되지 않은 요청이에요.", { status: 405, headers: { allow: "GET, HEAD" } });
    }
    if (ELSEWHERE[url.pathname]) return Response.redirect(HOME + ELSEWHERE[url.pathname], 301);
    if (url.pathname === "/app-ads.txt") {
      const ads = await fetch(ADS, { method: request.method, redirect: "manual", cf: { cacheEverything: true, cacheTtl: 300 } });
      return new Response(ads.body, { status: ads.status, headers: { "content-type": "text/plain; charset=utf-8", "x-content-type-options": "nosniff" } });
    }
    const upstream = await fetch(ORIGIN + url.pathname + url.search, {
      method: request.method,
      redirect: "manual",
      cf: { cacheEverything: true, cacheTtl: 300 },
    });
    const response = new Response(upstream.body, upstream);
    // GitHub sends /terms to /terms/ on its own host; keep the visitor on bamnat.app.
    const location = upstream.headers.get("location");
    if (location) response.headers.set("location", location.replace(/^https?:\/\/ywjmoney\.github\.io\/-/, HOME));
    response.headers.set("x-content-type-options", "nosniff");
    response.headers.set("referrer-policy", "strict-origin-when-cross-origin");
    return response;
  },
};
