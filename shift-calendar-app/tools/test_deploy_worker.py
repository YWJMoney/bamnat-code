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

    def test_upload_version_id(self):
        nd = '{"type":"wrangler-session"}\n{"type":"version-upload","version_id":"0f0f0f0f-1111-2222-3333-444444444444"}\n'
        self.assertEqual(d.upload_version_id(nd, ""), "0f0f0f0f-1111-2222-3333-444444444444")
        self.assertEqual(d.upload_version_id("", "Worker Version ID: 0f0f0f0f-1111-2222-3333-555555555555"), "0f0f0f0f-1111-2222-3333-555555555555")
        self.assertIsNone(d.upload_version_id("", "nothing"))


if __name__ == "__main__":
    unittest.main()
