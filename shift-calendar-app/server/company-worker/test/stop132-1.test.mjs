// 1.32.1 [STAB-14] (사용자 2026-10-29 "멈출 때마다 알람 멈춤이라고 뜨는데 실제로는 다른 원인 같아"): a question about the
// app stopping is sorted under 앱 동작, not 알람. Texts are made up.
import { test } from "node:test";
import assert from "node:assert/strict";
import { triage } from "../src/inquiries.js";

test("[STAB-14] the words of a freeze or a close are 앱 동작", () => {
  assert.equal(triage("bug", "[앱이 갑자기 닫힘] 렉걸림", { diag: "[알람 상태]\n알람 사용: 켜짐" }).topic, "app");
  assert.equal(triage("bug", "화면이 먹통이 돼요", {}).topic, "app");
  assert.equal(triage("bug", "잠금 풀면 응답 없음", {}).topic, "app");
});

test("[STAB-14] a check that starts with 앱 멈춤 is 앱 동작 even with no word", () => {
  assert.equal(triage("bug", "[앱이 멈췄어요] 이상해요", { diag: "[앱 멈춤]\n최근 30일: 멈춤 1회" }).topic, "app");
});

test("[STAB-14] an alarm's question with its check stays 알람", () => {
  assert.equal(triage("bug", "알람이 안 울려요", { diag: "[알람 상태]\n알람 사용: 켜짐" }).topic, "alarm");
  assert.equal(triage("bug", "이상해요", { diag: "[알람 상태]\n알람 사용: 켜짐" }).topic, "alarm");
});
