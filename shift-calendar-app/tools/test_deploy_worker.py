#!/usr/bin/env python3
"""1.39 [DEPLOY-01] tools/deploy_worker.py의 안전장치 시험(바깥과 닿지 않는 부분만).  python3 tools/test_deploy_worker.py"""
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

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

    # ---------- 2.0.0 [RVD-33] Durable Object 첫 배포 ----------

    SHARE_OLD = [
        {"type": "plain_text", "name": "ANDROID_CERT_SHA256", "text": "AA:BB"},
        {"type": "version_metadata", "name": "CF_VERSION_METADATA"},
        {"type": "secret_text", "name": "RATE_SALT"},
        {"type": "kv_namespace", "name": "SHARES", "namespace_id": "abc123"},
        {"type": "secret_text", "name": "SHARE_ADMIN_TOKEN"},
    ]
    TEAM_ROUND = (
        'name = "bamnat-share"\nmain = "src/index.js"\nkeep_vars = true\n'
        '[version_metadata]\nbinding = "CF_VERSION_METADATA"\n'
        '[[kv_namespaces]]\nbinding = "SHARES"\nid = "abc123"\n'
        '[[durable_objects.bindings]]\nname = "TEAM_ROUND"\nclass_name = "TeamRound"\n'
        '[[migrations]]\ntag = "v1-team-round"\nnew_sqlite_classes = ["TeamRound"]\n'
    )

    def test_do_lifecycle_and_pending(self):
        lc = d.do_lifecycle(self.TEAM_ROUND)
        self.assertEqual(lc["bindings"], {"TEAM_ROUND": "TeamRound"})
        self.assertEqual(lc["tags"], ["v1-team-round"])
        # 실서버에 태그가 없으면 전부, 마지막 태그면 없음, 설정에 없는 태그면 None(멈춤).
        self.assertEqual([m["tag"] for m in d.pending_migrations(lc, None)], ["v1-team-round"])
        self.assertEqual(d.pending_migrations(lc, "v1-team-round"), [])
        self.assertIsNone(d.pending_migrations(lc, "v0-gone"))
        two = d.do_lifecycle(self.TEAM_ROUND + '[[migrations]]\ntag = "v2"\nnew_sqlite_classes = ["X"]\n')
        self.assertEqual([m["tag"] for m in d.pending_migrations(two, "v1-team-round")], ["v2"])
        # 다른 Worker의 DO를 쓰는 바인딩은 이 Worker의 클래스가 아니에요.
        other = d.do_lifecycle('[[durable_objects.bindings]]\nname = "R"\nclass_name = "R"\nscript_name = "x"\n')
        self.assertEqual(other["bindings"], {})

    def test_script_migration_tag_shape(self):
        self.assertEqual(d.script_migration_tag({"result": {"default_environment": {"script": {"migration_tag": "v1-team-round"}}}}), "v1-team-round")
        self.assertIsNone(d.script_migration_tag({"result": {"default_environment": {"script": {"tag": "abc"}}}}))
        with self.assertRaises(ValueError):
            d.script_migration_tag({"result": {}})

    def test_real_share_config_ready_for_migrate(self):
        # 지금 실서버 바인딩으로 TeamRound 바닥 판을 올릴 때 멈출 까닭이 없어야 해요.
        self.assertEqual(d.migrate_problems(self.TEAM_ROUND, self.SHARE_OLD, d.pending_migrations(d.do_lifecycle(self.TEAM_ROUND), None)), [])
        names = d.config_binding_names(self.TEAM_ROUND)
        self.assertEqual(names, {"CF_VERSION_METADATA", "SHARES", "TEAM_ROUND"})

    def test_migrate_problems_stop_before_unrecoverable_deploy(self):
        lc = d.do_lifecycle(self.TEAM_ROUND)
        # 이미 적용됨 → 보통 deploy, migration 없음 → 보통 deploy, 실서버 태그 모름 → 멈춤.
        self.assertIn("이미 모두", " ".join(d.migrate_problems(self.TEAM_ROUND, self.SHARE_OLD, [])))
        self.assertIn("[[migrations]]가 없어요", " ".join(d.migrate_problems('name = "x"\n', [], [])))
        self.assertIn("태그가 설정에 없어요", " ".join(d.migrate_problems(self.TEAM_ROUND, self.SHARE_OLD, None)))
        # 지우기·이름 바꾸기·옮기기는 데이터가 사라지거나 옮겨져요.
        gone = self.TEAM_ROUND + '[[migrations]]\ntag = "v2"\ndeleted_classes = ["TeamRound"]\n'
        why = " ".join(d.migrate_problems(gone, self.SHARE_OLD, d.pending_migrations(d.do_lifecycle(gone), "v1-team-round")))
        self.assertIn("deleted_classes", why)
        self.assertIn("만드는 migration이 없어요", why)  # 지운 클래스를 바인딩이 아직 써요
        # 바인딩 클래스를 만드는 migration이 없으면 멈춰요.
        typo = self.TEAM_ROUND.replace('new_sqlite_classes = ["TeamRound"]', 'new_sqlite_classes = ["TeamRoom"]')
        self.assertIn("TeamRound를 만드는", " ".join(d.migrate_problems(typo, self.SHARE_OLD, d.do_lifecycle(typo)["migrations"])))
        # 앞 판 KV가 설정에 없으면 사라져요(비밀값·일반 변수는 남아요).
        no_kv = self.TEAM_ROUND.replace('[[kv_namespaces]]\nbinding = "SHARES"\nid = "abc123"\n', "")
        why = " ".join(d.migrate_problems(no_kv, self.SHARE_OLD, lc["migrations"]))
        self.assertIn("SHARES(kv_namespace)", why)
        self.assertNotIn("RATE_SALT", why)
        self.assertNotIn("ANDROID_CERT_SHA256", why)
        # 설정의 변수 이름이 비밀값을 덮어쓰면 멈춰요.
        clash = self.TEAM_ROUND + '[vars]\nRATE_SALT = "x"\n'
        self.assertIn("비밀값을 덮어써요: RATE_SALT", " ".join(d.migrate_problems(clash, self.SHARE_OLD, lc["migrations"])))

    def test_deploy_version_id(self):
        nd = '{"type":"wrangler-session"}\n{"type":"deploy","version_id":"0f0f0f0f-1111-2222-3333-444444444444"}\n'
        self.assertEqual(d.deploy_version_id(nd, ""), "0f0f0f0f-1111-2222-3333-444444444444")
        self.assertEqual(d.deploy_version_id("", "Current Version ID: 0f0f0f0f-1111-2222-3333-555555555555"), "0f0f0f0f-1111-2222-3333-555555555555")
        self.assertIsNone(d.deploy_version_id('{"type":"version-upload","version_id":"x"}', "nothing"))

    def test_normal_deploy_stops_on_pending_migration(self):
        # 보통 deploy는 설정에 실서버보다 새 migration이 있으면 올리기 전에 migrate로 보내요(바깥은 가짜로).
        calls = []
        real = (d.wrangler_json, d.run, d.cf, d.live_version, d.SERVER)
        tmp = Path(tempfile.mkdtemp())
        env = mock.patch.dict(os.environ, {"CLOUDFLARE_API_TOKEN": "test", "CLOUDFLARE_ACCOUNT_ID": "acc"})
        env.start()
        os.environ.pop("GITHUB_STEP_SUMMARY", None)  # 배포 job에서 돌아도 가짜 줄이 배포 요약에 남지 않게
        try:
            (tmp / "share-worker/src").mkdir(parents=True, exist_ok=True)
            (tmp / "share-worker/wrangler.toml").write_text(self.TEAM_ROUND.replace('id = "abc123"', 'id = "REPLACE_WITH_KV_NAMESPACE_ID"'), encoding="utf-8")
            (tmp / "share-worker/src/version.js").write_text('export const SERVER_VERSION = "2.0.0";\n', encoding="utf-8")
            (tmp / "share-worker/dist").mkdir(exist_ok=True)
            (tmp / "share-worker/dist/worker.js").write_text("export default {}\n", encoding="utf-8")
            d.SERVER = tmp

            def wj(args, cwd):
                calls.append(args[:2])
                if args[0] == "deployments":
                    return {"versions": [{"version_id": "old", "percentage": 100}]}
                return {"resources": {"bindings": self.SHARE_OLD}}
            d.wrangler_json = wj
            d.run = lambda cmd, cwd, extra_env=None, check=True: calls.append(cmd[:3]) or type("P", (), {"returncode": 0, "stdout": "", "stderr": ""})()
            d.cf = lambda method, path, body=None: {"result": {"default_environment": {"script": {}}}}
            d.live_version = lambda worker, base, view=None: "2.0.0"
            with self.assertRaises(d.Stop) as e:
                d.deploy("share", "check", "0123456789abcdef", "시험", False)
            self.assertIn("migrate", str(e.exception))
            self.assertFalse(any(c[:2] == ["npx", "--yes"] and "upload" in c for c in calls))
            # 실서버에 이미 적용됐으면 보통 deploy로 올라가요(versions upload까지 감).
            d.cf = lambda method, path, body=None: {"result": {"default_environment": {"script": {"migration_tag": "v1-team-round"}}}}
            with self.assertRaises(d.Stop) as e:
                d.deploy("share", "check", "0123456789abcdef", "시험", False)
            self.assertIn("판 번호를 읽지 못했어요", str(e.exception))  # 가짜 run은 판 번호를 주지 않아요: upload까지 간 것
        finally:
            d.wrangler_json, d.run, d.cf, d.live_version, d.SERVER = real
            env.stop()
            shutil.rmtree(tmp, ignore_errors=True)

    def run_migrate(self, mode, deploy_rc=0, deploy_goes_live=True, healthy=True, drops_domain=False, routes_readable=True):
        """가짜 Cloudflare로 migrate를 끝까지 돌려요. (예외, 부른 명령들, 말한 줄들, 배포용 설정)."""
        state = {"serving": "old", "tag": None, "domains": [{"hostname": "s.bamnat.app", "service": "bamnat-share", "enabled": True}]}
        seen = {}
        new_id = "0f0f0f0f-1111-2222-3333-444444444444"
        cmds, said = [], []
        real = (d.wrangler_json, d.run, d.cf, d.live_version, d.healthy, d.say, d.SERVER, d.time.sleep)
        tmp = Path(tempfile.mkdtemp())
        env = mock.patch.dict(os.environ, {"CLOUDFLARE_API_TOKEN": "test", "CLOUDFLARE_ACCOUNT_ID": "acc"})
        env.start()
        try:
            wd = tmp / "share-worker"
            (wd / "src").mkdir(parents=True)
            (wd / "dist").mkdir()
            (wd / "wrangler.toml").write_text(self.TEAM_ROUND.replace('id = "abc123"', 'id = "REPLACE_WITH_KV_NAMESPACE_ID"'), encoding="utf-8")
            (wd / "src/version.js").write_text('export const SERVER_VERSION = "2.0.0";\n', encoding="utf-8")
            (wd / "dist/worker.js").write_text("export default {}\n", encoding="utf-8")
            d.SERVER = tmp

            def wj(args, cwd):
                if args[0] == "deployments":
                    return {"versions": [{"version_id": state["serving"], "percentage": 100}]}
                if args[2] == "old":
                    return {"resources": {"bindings": self.SHARE_OLD}}
                return {"resources": {"bindings": self.SHARE_OLD + [{"type": "durable_object_namespace", "name": "TEAM_ROUND", "class_name": "TeamRound"}]}}

            def fake_run(cmd, cwd, extra_env=None, check=True):
                cmds.append(cmd)
                rc, out = 0, ""
                if cmd[:4] == ["npx", "--yes", "wrangler@4.150.0", "deploy"]:
                    seen["config"] = (wd / "wrangler.deploy.toml").read_text(encoding="utf-8")
                if cmd[:4] == ["npx", "--yes", "wrangler@4.150.0", "deploy"] and "--dry-run" not in cmd:
                    rc = deploy_rc
                    if deploy_goes_live:
                        state["serving"], state["tag"] = new_id, "v1-team-round"
                        out = f"Current Version ID: {new_id}"
                        if drops_domain:
                            state["domains"] = []
                if check and rc:
                    raise d.Stop(f"fail {rc}")
                return type("P", (), {"returncode": rc, "stdout": out, "stderr": ""})()

            d.wrangler_json = wj
            d.run = fake_run
            def fake_cf(method, path, body=None):
                if path.endswith("/subdomain"):
                    return {"result": {"enabled": True, "previews_enabled": False}}
                if "/workers/domains?service=bamnat-share" in path:
                    return {"result": list(state["domains"])}
                if path.endswith("/environments/production/routes"):
                    if not routes_readable:
                        raise RuntimeError("403")
                    return {"result": []}
                return {"result": {"default_environment": {"script": {"migration_tag": state["tag"]}}}}
            d.cf = fake_cf
            d.live_version = lambda worker, base, view=None: "2.0.0"
            d.healthy = lambda worker, base, want, deep=True: (healthy, "health 200" if healthy else "health 500")
            d.say = said.append
            d.time.sleep = lambda s: None
            err = None
            try:
                d.deploy("share", mode, "0123456789abcdef", "DO 바닥", False)
            except (d.Stop, SystemExit) as e:
                err = e
            return err, cmds, said, seen.get("config", "")
        finally:
            d.wrangler_json, d.run, d.cf, d.live_version, d.healthy, d.say, d.SERVER, d.time.sleep = real
            env.stop()
            shutil.rmtree(tmp, ignore_errors=True)

    @staticmethod
    def wrangler_deploys(cmds):
        return [c for c in cmds if c[3:4] == ["deploy"]]

    def test_migrate_check_only_dry_runs(self):
        err, cmds, said, config = self.run_migrate("migrate-check")
        self.assertIsNone(err)
        # 지금 붙은 연결을 요약에 남기고, workers.dev·미리보기 URL은 지금 값으로 적어 dry-run해요.
        self.assertIn("사용자 도메인 s.bamnat.app · workers.dev 켜짐 · 미리보기 URL 꺼짐 · route 없음", " ".join(said))
        self.assertIn("workers_dev = true\npreview_urls = false\n", config)
        self.assertEqual([("--dry-run" in c) for c in self.wrangler_deploys(cmds)], [True])
        self.assertFalse(any("upload" in c or c[3:4] == ["versions"] for c in cmds))
        self.assertIn("새 되돌리기 바닥", " ".join(said))

    def test_migrate_deploys_once_and_keeps_vars(self):
        err, cmds, said, config = self.run_migrate("migrate")
        self.assertIsNone(err, said)
        self.assertIn("preview_urls = false", config)
        real = [c for c in self.wrangler_deploys(cmds) if "--dry-run" not in c]
        self.assertEqual(len(real), 1)
        self.assertIn("--keep-vars", real[0])
        self.assertIn("ANDROID_CERT_SHA256:AA:BB", real[0])  # 대시보드 일반 변수는 값까지 넘겨요
        self.assertNotIn("RATE_SALT", " ".join(real[0]))  # 비밀값은 건드리지 않아요
        self.assertFalse(any(c[3:5] == ["versions", "upload"] for c in cmds))
        self.assertIn("Durable Object 첫 배포 끝", " ".join(said))

    def test_migrate_failure_never_rolls_back(self):
        # health가 실패해도 앞 판으로 versions deploy 하지 않아요(Cloudflare가 막고, 시도하면 헷갈려요).
        err, cmds, said, _ = self.run_migrate("migrate", healthy=False)
        self.assertIsInstance(err, SystemExit)
        self.assertFalse(any(c[3:5] == ["versions", "deploy"] for c in cmds))
        self.assertIn("되돌리지 않아요", " ".join(said))
        # wrangler deploy가 올리기 전에 실패했으면(손님은 앞 판) 멈춤이에요.
        err, cmds, said, _ = self.run_migrate("migrate", deploy_rc=1, deploy_goes_live=False)
        self.assertIsInstance(err, d.Stop)
        # 올린 뒤 실패(트리거 등)면 앞 판이 아니라 새 판을 확인하고 실패로 알려요.
        err, cmds, said, _ = self.run_migrate("migrate", deploy_rc=1, deploy_goes_live=True)
        self.assertIsInstance(err, SystemExit)
        self.assertIn("코드 1", " ".join(said))

    def test_migrate_says_loudly_when_a_domain_falls_off(self):
        # 올린 뒤 사용자 도메인(s.bamnat.app)이 떨어지면 health 전에 무엇을 다시 붙여야 하는지 알려요.
        err, cmds, said, _ = self.run_migrate("migrate", drops_domain=True)
        self.assertIsInstance(err, SystemExit)
        self.assertIn("사용자 도메인: ['s.bamnat.app'] → []", " ".join(said))
        self.assertIn("다시 붙여요", " ".join(said))
        # route를 못 읽어도(zone 권한 없음) 멈추지 않고 '읽지 못함'으로 남겨요.
        err, cmds, said, _ = self.run_migrate("migrate", routes_readable=False)
        self.assertIsNone(err, said)
        self.assertIn("route 읽지 못함", " ".join(said))

    def test_pin_subdomain_and_attachments(self):
        pinned = d.pin_subdomain(self.TEAM_ROUND.replace('main = "src/index.js"\n', 'main = "src/index.js"\nworkers_dev = true\n'), False, True)
        c = d.tomllib.loads(pinned)
        self.assertIs(c["workers_dev"], False)
        self.assertIs(c["preview_urls"], True)
        self.assertEqual(pinned.count("workers_dev ="), 1)
        self.assertEqual(c["durable_objects"]["bindings"][0]["name"], "TEAM_ROUND")  # 표는 그대로
        a = d.attachment_state({"enabled": True}, [{"hostname": "s.bamnat.app", "service": "bamnat-share"},
                                                    {"hostname": "x.example", "service": "other"}], None, "bamnat-share")
        self.assertEqual(a, {"workers_dev": True, "previews": False, "domains": ["s.bamnat.app"], "routes": None})
        with self.assertRaises(ValueError):
            d.attachment_state({}, [], [], "bamnat-share")
        b = dict(a, routes=["s.bamnat.app/*"])
        self.assertEqual(d.attachment_diff(a, b), [])  # 앞에서 route를 못 읽었으면 견주지 않아요
        self.assertEqual(d.attachment_diff(dict(a, routes=[]), b), ["route: [] → ['s.bamnat.app/*']"])
        self.assertEqual(d.attachment_diff(a, dict(a, previews=True)), ["미리보기 URL: False → True"])


if __name__ == "__main__":
    unittest.main()
