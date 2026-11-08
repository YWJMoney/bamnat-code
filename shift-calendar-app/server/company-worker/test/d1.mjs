// [COMM-04] The D1 calls the worker makes, on Node's built-in SQLite, so tests run without Cloudflare.
import { DatabaseSync } from "node:sqlite";

export function d1(path = ":memory:") {
  const db = new DatabaseSync(path);
  const check = (params) => {
    // 1.36: D1 takes at most 100 bound values in one statement (node's SQLite takes thousands), so a list that grew past it
    // failed only on the real server. Refused here too, in tests and the local server alike.
    if (params.length > 100) throw new RangeError(`D1: too many SQL variables (${params.length} > 100)`);
    return params.map((p) => {
    // D1 refuses undefined; booleans become 1 and 0 there, so the worker binds numbers.
    if (p === undefined) throw new TypeError("D1_TYPE_ERROR: undefined is not supported");
    if (typeof p === "boolean") throw new TypeError("bind 0/1, not a boolean");
    return p;
    });
  };
  const statement = (sql, params = []) => ({
    sql,
    bind: (...values) => statement(sql, check(values)),
    async first(column) {
      const row = db.prepare(sql).get(...params);
      if (row === undefined) return null;
      return column ? row[column] : { ...row };
    },
    async all() {
      return { success: true, results: db.prepare(sql).all(...params).map((r) => ({ ...r })), meta: {} };
    },
    async run() {
      const r = db.prepare(sql).run(...params);
      if (Number(r.changes)) writes++;
      return { success: true, results: [], meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
    },
  });
  // SP1 1.30 [SPD-02] D1 Sessions as a database without copies has them: the same rows, a bookmark that counts the writes so far
  // and where it answered from. [sessions] keeps how each session started, for the tests that check it.
  let writes = 0;
  const sessions = [];
  function withSession(start = "first-unconstrained") {
    // Through the database it was asked of (a test's counting copy too) and its own prepare, so a test that breaks or counts
    // the database's calls sees the session's.
    const self = this ?? out;
    sessions.push(start);
    const primary = start === "first-primary";
    const served = (r) => ({ ...r, meta: { ...r.meta, served_by_region: "APAC", served_by_primary: primary } });
    const wrap = (s) => ({ ...s, bind: (...v) => wrap(s.bind(...v)), all: async () => served(await s.all()), run: async () => served(await s.run()) });
    return {
      prepare: (sql) => wrap(self.prepare(sql)),
      batch: async (list) => (await self.batch(list)).map(served),
      getBookmark: () => `bm-${writes}`,
    };
  }
  let turn = Promise.resolve();
  const out = {
    db,
    sessions,
    withSession,
    prepare: (sql) => statement(sql),
    // 1.39 [ADM3-43] D1 runs batches sent at the same time one after another, each in its own transaction; here too, so
    // reads the worker now sends together (지표) do not start a transaction inside another.
    batch(list) {
      const next = turn.then(async () => {
        db.exec("BEGIN");
        try {
          const out = [];
          for (const s of list) out.push(/^\s*(SELECT|WITH)/i.test(s.sql) ? await s.all() : await s.run());
          db.exec("COMMIT");
          return out;
        } catch (e) {
          db.exec("ROLLBACK");
          throw e;
        }
      });
      turn = next.catch(() => {});
      return next;
    },
    /** Every text stored anywhere, for checks that something is never kept. */
    dump() {
      const tables = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all().map((t) => t.name);
      return tables.map((t) => JSON.stringify(db.prepare(`SELECT * FROM "${t}"`).all())).join("\n");
    },
  };
  return out;
}
