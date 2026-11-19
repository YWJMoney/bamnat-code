// 2.0.0 [RVQ-55] 검토 SVC-38: 스토어 리뷰 답은 누구나 보는 공개 글인데, 문의 답과 달리 답 글 검사(AI·자동 답 표현, 보안 정보)가 없었어요.
// 이제 초안을 적을 때 걸린 까닭을 알려 주고, Play로 보낼 때 걸리면 보내지 않아요(최고 관리자가 읽고 '그래도 보내기'를 눌러야 가요). `npm test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { server, signIn, TEST_NOW } from "./helpers.mjs";

const HOUR = 3600 * 1000;

/** Play 서비스 계정과 리뷰 답 주소를 흉내 내요. 보낸 답 글을 [sent]에 모아요. */
function fakePlay(t) {
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = JSON.stringify({ client_email: "bamnat@example.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) });
  const sent = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (url, init = {}) => {
    url = String(url);
    if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "tok", expires_in: 3600 }));
    if (url.endsWith(":reply") && init.method === "POST") {
      sent.push(JSON.parse(init.body).replyText);
      return new Response(JSON.stringify({ result: {} }));
    }
    return new Response("{}", { status: 404 });
  };
  t.after(() => { globalThis.fetch = real; });
  return { account, sent };
}

test("[RVQ-55] 스토어 리뷰 답: 초안의 AI·보안 표현을 알려 주고, 걸린 답은 확인 없이 Play로 가지 않아요", async (t) => {
  const play = fakePlay(t);
  const { call, DB } = server({ t, PLAY_SERVICE_ACCOUNT: play.account });
  const owner = await signIn(call);
  const add = DB.prepare(`INSERT INTO store_reviews (id, stars, text, app, lang, at, reply, reply_at, replied_by, draft, draft_by, status, fetched_at)
    VALUES (?1, ?2, ?3, '2.0.0', 'ko', ?4, '', NULL, '', '', '', 'new', ?4)`);
  await add.bind("gp:rvq55-risk-0001", 2, "알람이 늦게 울려요", TEST_NOW - 3 * HOUR).run();
  await add.bind("gp:rvq55-fine-0002", 5, "달력이 예뻐요", TEST_NOW - 2 * HOUR).run();
  const reply = (id, body) => call("POST", `/api/admin/reviews/${encodeURIComponent(id)}/reply`, body, owner);
  const risky = "불편을 드려 죄송해요. 저는 AI 상담원이라 자세한 건 몰라요. 밤낮 운영팀";

  // 초안은 남기되 걸린 까닭을 함께 돌려주고, 목록에도 보여요.
  const draft = await call("POST", "/api/admin/reviews/gp%3Arvq55-risk-0001/draft", { text: risky }, owner);
  assert.equal(draft.status, 200, draft.text);
  assert.equal(draft.data.risk, "답에 AI·자동 답 표현");
  const listed = (await call("GET", "/api/admin/reviews?status=open", undefined, owner)).data.items;
  assert.equal(listed.find((r) => r.id === "gp:rvq55-risk-0001").draftRisk, "답에 AI·자동 답 표현");
  assert.equal(listed.find((r) => r.id === "gp:rvq55-fine-0002").draftRisk, undefined);

  // 걸린 답은 확인 없이 보내지 않아요(Play에 아무것도 가지 않음). 보안 정보도 같아요.
  const held = await reply("gp:rvq55-risk-0001", { text: risky });
  assert.equal(held.status, 409);
  assert.deepEqual(held.data, { error: "reply_risk", risk: "답에 AI·자동 답 표현" });
  const secret = await reply("gp:rvq55-risk-0001", { text: "관리자 비밀번호를 바꾸면 돼요. 밤낮 운영팀" });
  assert.equal(secret.status, 409);
  assert.equal(secret.data.risk, "답에 보안 정보");
  assert.deepEqual(play.sent, []);
  assert.equal((await DB.prepare("SELECT status FROM store_reviews WHERE id = 'gp:rvq55-risk-0001'").first()).status, "new");

  // 최고 관리자가 읽고 '그래도 보내기'를 누르면 가고, 그 사실이 기록에 남아요.
  const forced = await reply("gp:rvq55-risk-0001", { text: risky, confirm: true });
  assert.equal(forced.status, 200, forced.text);
  assert.deepEqual(play.sent, [risky]);
  const log = await DB.prepare("SELECT detail FROM audit WHERE action = 'review_reply' ORDER BY id DESC LIMIT 1").first();
  assert.match(log.detail, /^\[확인하고 보냄: 답에 AI·자동 답 표현\] /);

  // 걸리지 않는 답은 그대로 한 번에 가요.
  const fine = await reply("gp:rvq55-fine-0002", { text: "예쁘게 봐 주셔서 고마워요. 더 편하게 다듬을게요. 밤낮 운영팀" });
  assert.equal(fine.status, 200, fine.text);
  assert.equal(play.sent.length, 2);
  assert.equal((await DB.prepare("SELECT status FROM store_reviews WHERE id = 'gp:rvq55-fine-0002'").first()).status, "replied");
});
