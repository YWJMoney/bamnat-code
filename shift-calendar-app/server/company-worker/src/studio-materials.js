// 1.38 [STM-01..04] 밤낮 재료: 스튜디오에서 바로 얹는 운영자 그림(색 아이콘·배경). 그림은 이 저장소의 스크립트로 직접 그린 것만
// 넣어요(shift-calendar-app/tools/materials/, 라이선스는 밤낮). 그림 바이트는 작품 그림과 같은 recipe_images에 owner 'operator'로
// 'ok' 상태로 두어서, 재료를 쓴 작품은 1.36 앱에서도 그림 받기(/api/recipes/images/:id)로 그대로 보여요.
// 목록(studio_materials)은 운영자가 출시 대기(waiting)로 넣었다가 앱 판과 함께 공개(ok)하고, 숨길(hidden) 수 있어요.

import { rcWebpSize } from "./recipe-community.js";

export const MATERIAL_KINDS = ["icon", "bg"];
export const MATERIAL_STATES = ["waiting", "ok", "hidden"];
export const MATERIAL_LIMITS = { bytes: 160 * 1024, side: 2048, name: 20, pack: 20, batch: 25 };

export const MATERIAL_SCHEMA = [
  `CREATE TABLE IF NOT EXISTS studio_materials (id TEXT PRIMARY KEY, kind TEXT NOT NULL, pack TEXT NOT NULL, name TEXT NOT NULL,
    ord INTEGER NOT NULL DEFAULT 0, thumb TEXT, w INTEGER NOT NULL, h INTEGER NOT NULL, light REAL, free INTEGER NOT NULL DEFAULT 1,
    state TEXT NOT NULL DEFAULT 'waiting', min_app INTEGER, at INTEGER NOT NULL)`,
  "CREATE INDEX IF NOT EXISTS studio_materials_list ON studio_materials (state, kind, pack, ord)",
];

const SM_ID = /^[0-9a-f]{16}$/;

function smB64(s) {
  try {
    const bin = atob(s);
    return Uint8Array.from(bin, (c) => c.charCodeAt(0));
  } catch {
    return null;
  }
}

/** 크기는 작품 그림과 같은 읽기로(애니메이션 WebP는 거절). */
export const webpSize = (b) => rcWebpSize(b);

async function smId(bytes) {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return Array.from(h.slice(0, 8), (x) => x.toString(16).padStart(2, "0")).join("");
}

const smText = (s, max) => (typeof s === "string" && s.trim().length > 0 && [...s.trim()].length <= max ? s.trim() : null);

/** One picture into recipe_images as the operator's, shown to all; its id is the first 16 hex of its SHA-256 (as the phone names its own). */
async function smImage(d, env, b64, now) {
  const bytes = typeof b64 === "string" ? smB64(b64) : null;
  if (!bytes) return { error: "image" };
  if (bytes.length > MATERIAL_LIMITS.bytes) return { error: "too_big" };
  const size = webpSize(bytes);
  if (!size) return { error: "image" };
  if (size.w < 1 || size.h < 1 || size.w > MATERIAL_LIMITS.side || size.h > MATERIAL_LIMITS.side) return { error: "image_size" };
  const id = await smId(bytes);
  await d.run(env, `INSERT INTO recipe_images (id, owner, data, bytes, at, state, checked, why, decided_at) VALUES (?, 'operator', ?, ?, ?, 'ok', 'operator', NULL, ?)
    ON CONFLICT(id) DO UPDATE SET state = 'ok', decided_at = excluded.decided_at`, id, b64, bytes.length, now, now);
  return { id, ...size };
}

/** GET /api/recipes/materials?app=<versionCode>: 공개된 재료, 묶음과 순서대로. 휴대폰은 그림을 누를 때만 받아요. */
async function smList(d, env, url) {
  const app = Number.parseInt(url.searchParams.get("app") ?? "", 10);
  const rows = await d.all(env, `SELECT id, kind, pack, name, thumb, w, h, light, free FROM studio_materials
    WHERE state = 'ok' AND (min_app IS NULL OR min_app <= ?) ORDER BY kind, pack, ord, id`, Number.isFinite(app) ? app : 0);
  return d.json({ items: rows.map((r) => ({ ...r, free: r.free === 1 })) }, 200, { "cache-control": "public, max-age=600" });
}

export async function materialRoute(d, request, env, path, url) {
  if (path === "/api/recipes/materials" && request.method === "GET") return smList(d, env, url);
  return null;
}

/**
 * 운영자: GET /recipes/materials (모든 상태와 수), POST /recipes/materials {items:[{kind, pack, name, ord, webp, thumb?, light?, free?, minApp?}]}
 * (새로 넣으면 출시 대기), POST /recipes/materials/state {state, ids? | pack? | all?}.
 */
export async function materialAdmin(d, request, env, path, url, now) {
  const method = request.method;
  if (path === "/recipes/materials" && method === "GET") {
    const rows = await d.all(env, "SELECT id, kind, pack, name, ord, thumb, w, h, light, free, state, min_app, at FROM studio_materials ORDER BY kind, pack, ord, id");
    const counts = {};
    for (const r of rows) counts[r.state] = (counts[r.state] ?? 0) + 1;
    return d.json({ items: rows.map((r) => ({ ...r, free: r.free === 1 })), counts });
  }
  if (path === "/recipes/materials" && method === "POST") {
    const body = await d.readJson(request, 6 * 1024 * 1024);
    const items = Array.isArray(body?.items) ? body.items : null;
    if (!items || items.length === 0 || items.length > MATERIAL_LIMITS.batch) return d.json({ error: "items" }, 400);
    // 2.0.0 [RVN-15] 검토 SVC-99: 모두 검사한 뒤 한 번에 넣어요(중간에 틀린 줄이 있으면 앞 줄만 들어가 묶음이 반쪽이 되던 것).
    const rows = [];
    for (const [i, it] of items.entries()) {
      const kind = MATERIAL_KINDS.includes(it?.kind) ? it.kind : null;
      const pack = smText(it?.pack, MATERIAL_LIMITS.pack);
      const name = smText(it?.name, MATERIAL_LIMITS.name);
      if (!kind || !pack || !name) return d.json({ error: "item", at: i }, 400);
      const img = await smImage(d, env, it.webp, now);
      if (img.error) return d.json({ error: img.error, at: i }, 400);
      let thumb = null;
      if (it.thumb !== undefined && it.thumb !== null) {
        const t = await smImage(d, env, it.thumb, now);
        if (t.error) return d.json({ error: t.error, at: i, field: "thumb" }, 400);
        thumb = t.id;
      }
      const light = Number.isFinite(it.light) ? Math.max(0, Math.min(1, it.light)) : null;
      const minApp = Number.isInteger(it.minApp) && it.minApp > 0 ? it.minApp : null;
      const ord = Number.isInteger(it.ord) ? it.ord : 0;
      // 같은 그림을 다시 넣으면 이름·묶음·순서만 바뀌고 공개 상태는 그대로예요.
      rows.push({ pack, stmt: d.stmt(env, `INSERT INTO studio_materials (id, kind, pack, name, ord, thumb, w, h, light, free, state, min_app, at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'waiting', ?, ?)
        ON CONFLICT(id) DO UPDATE SET kind = excluded.kind, pack = excluded.pack, name = excluded.name, ord = excluded.ord,
          thumb = excluded.thumb, light = excluded.light, free = excluded.free, min_app = excluded.min_app`,
        img.id, kind, pack, name, ord, thumb, img.w, img.h, light, it.free === false ? 0 : 1, minApp, now), id: img.id });
    }
    await d.batch(env, rows.map((r) => r.stmt));
    const packs = [...new Set(rows.map((r) => r.pack))];
    await d.audit?.(env, now, "material_add", packs.join(", ").slice(0, 80), `${rows.length}개 · 출시 대기`);
    return d.json({ ids: rows.map((r) => r.id) }, 201);
  }
  // 1.38 [STM-10] 무료·광고 나누기: 묶음마다 all(모두 무료)·none(모두 광고·프로)·third(앞쪽 3분의 1만 무료, 나머지 광고·프로).
  if (path === "/recipes/materials/free" && method === "POST") {
    const body = await d.readJson(request, 4 * 1024);
    const pack = smText(body?.pack, MATERIAL_LIMITS.pack);
    const mode = ["all", "none", "third"].includes(body?.mode) ? body.mode : null;
    if (!pack || !mode) return d.json({ error: "which" }, 400);
    const rows = await d.all(env, "SELECT id FROM studio_materials WHERE pack = ? ORDER BY ord, id", pack.trim());
    const cut = mode === "all" ? rows.length : mode === "none" ? 0 : Math.ceil(rows.length / 3);
    if (rows.length) {
      await d.batch(env, rows.map((r, i) => d.stmt(env, "UPDATE studio_materials SET free = ? WHERE id = ?", i < cut ? 1 : 0, r.id)));
    }
    await d.audit?.(env, now, "material_free", pack.trim(), `무료 ${cut} · 광고·프로 ${rows.length - cut}`);
    return d.json({ pack: pack.trim(), free: cut, paid: rows.length - cut });
  }
  if (path === "/recipes/materials/state" && method === "POST") {
    const body = await d.readJson(request, 64 * 1024);
    const state = MATERIAL_STATES.includes(body?.state) ? body.state : null;
    if (!state) return d.json({ error: "state" }, 400);
    let res;
    let which = "";
    // 2.0.0 [RVN-15] 검토 SVC-99: '모두'는 출시 대기 → 공개만이에요(숨긴 것까지 되살리거나 모두 숨기던 것). 숨기기는 묶음·번호로.
    if (body.all === true) {
      if (state !== "ok") return d.json({ error: "which" }, 400);
      res = await d.run(env, "UPDATE studio_materials SET state = 'ok' WHERE state = 'waiting'");
      which = "출시 대기 모두";
    }
    else if (smText(body.pack, MATERIAL_LIMITS.pack)) {
      res = await d.run(env, "UPDATE studio_materials SET state = ? WHERE pack = ?", state, body.pack.trim());
      which = body.pack.trim();
    } else if (Array.isArray(body.ids) && body.ids.length > 0 && body.ids.length <= 300 && body.ids.every((x) => SM_ID.test(x))) {
      res = await d.run(env, `UPDATE studio_materials SET state = ? WHERE id IN (${body.ids.map(() => "?").join(",")})`, state, ...body.ids);
      which = `재료 ${body.ids.length}개`;
    } else return d.json({ error: "which" }, 400);
    const changed = res?.meta?.changes ?? null;
    await d.audit?.(env, now, "material_state", which.slice(0, 80), `${{ waiting: "출시 대기", ok: "공개", hidden: "숨김" }[state]}${changed === null ? "" : ` · ${changed}개`}`);
    return d.json({ state, changed });
  }
  return null;
}
