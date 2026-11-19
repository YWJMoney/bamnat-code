#!/usr/bin/env python3
"""2.0.0 [RVR-17] (검토 TEST-14·I60a-03) 배포 도구의 흐름 시험: python3 tools/test_deploy_flow.py

진짜 wrangler·Cloudflare·서버에는 닿지 않아요. deploy_worker의 run·cf·get·time을 가짜로 바꿔 끼우고, 서버 폴더는 임시 폴더에
만들어요. build(토큰 없이 시험·번들)와 ship(토큰, build가 만든 파일만)을 차례로 끝까지 돌려요(2.0.0 [RVQ-01]부터 둘로 나뉨).
시험하는 길: 판이 나뉘어 돎, check 모드, 대시보드에만 있던 변수 다시 올리기, 바인딩 빠짐, 판 낮음, 시험 실패, build에 토큰,
카나리 뒤 배포, 카나리에서 나쁘면 되돌리기, 100% 뒤 health 실패면 앞 판·정기 작업 되돌리기, 날짜에 낡은 시험 넘기기.
2.0.0 [RVD-33] Durable Object 첫 배포(mode migrate-check·migrate): MigrateFlowTest가 공유 서버로 끝까지 돌려 봐요.
"""
import contextlib
import datetime
import io
import json
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import deploy_worker as d  # noqa: E402

DB_ID = "11111111-2222-3333-4444-555555555555"
OLD_ID = "aaaaaaaa-0000-0000-0000-000000000001"
NEW_ID = "bbbbbbbb-0000-0000-0000-000000000002"
OLD_BINDINGS = [
    {"type": "d1", "name": "DB", "id": DB_ID},
    {"type": "secret_text", "name": "ADMIN_PASSWORD"},
    {"type": "plain_text", "name": "SHARE_BASE_URL", "text": "https://s.example"},
]
TOML = '''name = "bamnat-company"
main = "src/index.js"
[[d1_databases]]
binding = "DB"
database_id = "REPLACE_WITH_D1_DATABASE_ID"
[triggers]
crons = ["*/10 * * * *", "0 * * * *"]
'''
PIN = "Cloudflare-Workers-Version-Overrides"


class FakeCloud:
    """wrangler와 Cloudflare API와 서버 health를 흉내 내요. 무엇을 불렀는지 calls에 남아요."""

    def __init__(self, wd):
        self.wd = wd
        self.serving = [(OLD_ID, 100.0)]
        self.views = {OLD_ID: OLD_BINDINGS}
        self.new_bindings = list(OLD_BINDINGS)  # 새 판이 받을 바인딩(시험마다 바꿈)
        self.uploads = []
        self.configs = []  # 올릴 때 쓴 배포용 설정
        self.deploys = []  # versions deploy의 판 목록(카나리는 두 판)
        self.schedules = ["0 * * * *"]
        self.new_healthy = True  # 새 판이 health에 맞게 답하는지
        self.sick_when_full = False  # 새 판이 100%가 되면 아파요(카나리는 지나감)
        self.live = "1.39.0"
        self.calls = []  # (명령, clean_env)
        self.tests_fail = False

    def run(self, cmd, cwd, extra_env=None, check=True, stage="", clean_env=False):
        self.calls.append((cmd, clean_env))
        out, code = "", 0
        if cmd[:3] == ["npm", "run", "bundle"]:
            (self.wd / "dist").mkdir(exist_ok=True)
            (self.wd / "dist/worker.js").write_text("export default {}", encoding="utf-8")
        elif cmd[:2] == ["npm", "test"] or cmd[:2] == ["node", "--test"]:
            code = 1 if self.tests_fail else 0
            out = "not ok 1 - 새해 첫날 시험\n" if self.tests_fail else "ok\n"
        elif cmd[:2] == ["npm", "ci"]:
            pass
        else:
            args = cmd[len(d.WRANGLER):]
            if args[:2] == ["deployments", "status"]:
                out = json.dumps({"versions": [{"version_id": v, "percentage": p} for v, p in self.serving]})
            elif args[:2] == ["versions", "view"]:
                vid = args[2]
                out = json.dumps({"id": vid, "resources": {"bindings": self.views.get(vid, [])},
                                  "annotations": {"workers/message": f"{self.live} abcdef12 앞 판"}})
            elif args[:2] == ["versions", "upload"]:
                given = {a.split(":", 1)[0]: a.split(":", 1)[1] for i, a in enumerate(args) if i and args[i - 1] == "--var"}
                self.uploads.append(given)
                self.configs.append((Path(cwd) / "wrangler.deploy.toml").read_text(encoding="utf-8"))
                vid = NEW_ID if len(self.uploads) == 1 else NEW_ID[:-1] + str(len(self.uploads) + 1)
                self.views[vid] = list(self.new_bindings) + [{"type": "plain_text", "name": k, "text": v} for k, v in given.items()]
                Path(extra_env["WRANGLER_OUTPUT_FILE_PATH"]).write_text(json.dumps({"type": "version-upload", "version_id": vid}) + "\n",
                                                                         encoding="utf-8")
            elif args[:2] == ["versions", "deploy"]:
                split = [a.split("@") for a in args[2:] if "@" in a and a.endswith("%")]
                self.serving = [(v, float(p.rstrip("%"))) for v, p in split]
                self.deploys.append([v for v, _ in split])
        p = subprocess.CompletedProcess(cmd, code, out, "")
        if check and code:
            raise d.Stop(f"`{' '.join(cmd[:4])} …` 실패 (코드 {code})", stage=stage,
                         tests=d.failing_tests(out) if stage == "시험" else None)
        return p

    def cf(self, method, path, body=None):
        if path.startswith("/accounts?"):
            return {"result": [{"id": "acc"}]}
        if path.endswith("/schedules"):
            if method == "PUT":
                self.schedules = [x["cron"] for x in body]
                return {"result": {}}
            return {"result": {"schedules": [{"cron": c} for c in self.schedules]}}
        raise AssertionError(path)

    def get(self, url, headers=None):
        pinned = (headers or {}).get(PIN, "")
        new = NEW_ID in pinned or (not pinned and self.serving == [(NEW_ID, 100.0)])
        sick = new and (not self.new_healthy or (self.sick_when_full and not pinned))
        if url.endswith("/admin/") or url.endswith("/api/config"):
            return (503, "") if sick else (200, "{}")
        if "health" in url:
            if sick:
                return 503, json.dumps({"database": False})
            return 200, json.dumps({"database": True, "password": True, "salt": True, "schema": True, "config": True,
                                    "version": "2.0.0" if new else self.live})
        return 404, ""

    def commands(self, head):
        return [c for c, _ in self.calls if c[:len(head)] == head]


class DeployFlowTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.wd = self.dir / "company-worker"
        (self.wd / "src").mkdir(parents=True)
        (self.wd / "test").mkdir()
        (self.wd / "test" / "a.test.mjs").write_text("", encoding="utf-8")
        (self.wd / "wrangler.toml").write_text(TOML, encoding="utf-8")
        (self.wd / "src/version.js").write_text('export const SERVER_VERSION = "2.0.0";\n', encoding="utf-8")
        self.out = self.dir / "ship"
        self.cloud = FakeCloud(self.wd)
        self.keep = d.SERVER, d.run, d.cf, d.get, d.time
        d.SERVER, d.run, d.cf, d.get = self.dir, self.cloud.run, self.cloud.cf, self.cloud.get
        d.time = types.SimpleNamespace(sleep=lambda s: None)
        self.env = {k: d.os.environ.get(k) for k in ("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "GITHUB_STEP_SUMMARY", "GITHUB_OUTPUT")}
        for k in self.env:
            d.os.environ.pop(k, None)

    def tearDown(self):
        d.SERVER, d.run, d.cf, d.get, d.time = self.keep
        for k, v in self.env.items():
            if v is None:
                d.os.environ.pop(k, None)
            else:
                d.os.environ[k] = v
        shutil.rmtree(self.dir)

    def build(self, skips=()):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            d.build(["company"], self.out, None, skips)
        return out.getvalue()

    def ship(self, mode="deploy", allow_older=False):
        out = io.StringIO()
        d.os.environ["CLOUDFLARE_API_TOKEN"] = "test-token-not-real"
        try:
            with contextlib.redirect_stdout(out):
                d.ship(["company"], self.out, mode, "0123456789abcdef", "시험", allow_older)
        finally:
            d.os.environ.pop("CLOUDFLARE_API_TOKEN", None)
            self.printed = out.getvalue()
        return self.printed

    def go(self, mode="deploy", allow_older=False):
        self.build()
        return self.ship(mode, allow_older)

    # ---------- build ----------

    def test_build_runs_the_branch_code_without_keys_and_hands_over_files(self):
        out = self.build()
        for name in ("worker.js", "wrangler.toml", "version.txt", "notes.txt"):
            self.assertTrue((self.out / "company" / name).exists(), name)
        self.assertEqual("2.0.0", (self.out / "company/version.txt").read_text(encoding="utf-8"))
        self.assertIn("REPLACE_WITH_D1_DATABASE_ID", (self.out / "company/wrangler.toml").read_text(encoding="utf-8"))
        self.assertEqual([["npm", "test"]], self.cloud.commands(["npm", "test"]))
        self.assertTrue(all(clean for cmd, clean in self.cloud.calls), "고른 브랜치의 코드는 깨끗한 환경 변수로만")
        self.assertIn("서버 시험 통과", out)
        self.assertIn("스키마 호환: 앞 판 커밋을 몰라 건너뜀", out)

    def test_build_refuses_a_token(self):
        d.os.environ["CLOUDFLARE_API_TOKEN"] = "test-token-not-real"
        with self.assertRaisesRegex(d.Stop, "토큰"):
            self.build()
        self.assertEqual([], self.cloud.calls)

    def test_failing_server_tests_stop_before_anything_is_handed_over(self):
        self.cloud.tests_fail = True
        with self.assertRaises(d.Stop) as e:
            self.build()
        self.assertEqual("시험", e.exception.stage)
        self.assertEqual(["새해 첫날 시험"], e.exception.tests)
        self.assertFalse((self.out / "company/worker.js").exists())
        self.assertEqual([], self.cloud.commands(["npm", "run", "bundle"]))

    def test_a_stale_test_is_skipped_by_name_and_written_down(self):
        today = datetime.date(2027, 1, 1)
        skips = d.parse_skips(["새해 시험|2027-01-01 날짜 폭탄, 시험 고침 PR 진행 중|2027-01-05"], today)
        out = self.build(skips)
        tests = self.cloud.commands(["node", "--test"])
        self.assertEqual(1, len(tests))
        self.assertIn("--test-skip-pattern=^새해\\ 시험$", tests[0])
        self.assertIn("test/a.test.mjs", tests[0])
        self.assertEqual([], self.cloud.commands(["npm", "test"]))
        self.assertIn("넘긴 시험", out)
        self.assertIn("2027-01-05", (self.out / "company/notes.txt").read_text(encoding="utf-8"))

    def test_a_skip_needs_a_reason_and_a_near_deadline(self):
        today = datetime.date(2027, 1, 1)
        for bad in ["새해 시험||2027-01-05", "새해 시험|까닭|2026-12-31", "새해 시험|까닭|2027-02-01", "새해 시험|까닭|내일", "새해 시험|까닭"]:
            with self.assertRaises(d.Stop, msg=bad):
                d.parse_skips([bad], today)
        self.assertEqual(2, len(d.parse_skips(["a|b|2027-01-02; c|d|2027-01-03"], today)))
        self.assertEqual([], d.parse_skips([], today))

    # ---------- ship ----------

    def test_two_versions_serving_stops_before_anything(self):
        self.cloud.serving = [(OLD_ID, 60.0), (NEW_ID, 40.0)]
        with self.assertRaisesRegex(d.Stop, "나뉘어"):
            self.go()
        self.assertEqual([], self.cloud.uploads)

    def test_check_mode_uploads_and_compares_but_does_not_serve(self):
        out = self.go("check")
        self.assertIn("check 모드", out)
        self.assertEqual(1, len(self.cloud.uploads))
        self.assertEqual([], self.cloud.deploys)
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)
        # 자리표시자는 앞 판의 DB 번호로 채워 배포용 설정에만(저장소 파일·build가 넘긴 파일은 그대로).
        self.assertIn(DB_ID, self.cloud.configs[0])
        self.assertIn("REPLACE_WITH_D1_DATABASE_ID", (self.wd / "wrangler.toml").read_text(encoding="utf-8"))
        self.assertIn("REPLACE_WITH_D1_DATABASE_ID", (self.out / "company/wrangler.toml").read_text(encoding="utf-8"))
        # 올리는 판의 메시지는 판 이름으로 시작해요(다음 배포가 지금 판으로 읽어요, RVD-19).
        upload = self.cloud.commands(d.WRANGLER + ["versions", "upload"])[0]
        self.assertTrue(upload[upload.index("--message") + 1].startswith("2.0.0 01234567 "))

    def test_ship_refuses_a_config_that_runs_commands(self):
        self.build()
        (self.out / "company/wrangler.toml").write_text(TOML + '[build]\ncommand = "curl x | sh"\n', encoding="utf-8")
        with self.assertRaisesRegex(d.Stop, "받지 않는 칸"):
            self.ship("check")
        self.assertEqual([], self.cloud.uploads)

    def test_a_variable_set_only_on_the_dashboard_is_uploaded_again_with_its_old_value(self):
        self.cloud.new_bindings = [b for b in OLD_BINDINGS if b["name"] != "SHARE_BASE_URL"]
        out = self.go("check")
        self.assertEqual([{}, {"SHARE_BASE_URL": "https://s.example"}], self.cloud.uploads)
        self.assertIn("다시 올려요", out)

    def test_a_missing_secret_or_database_stops(self):
        self.cloud.new_bindings = [b for b in OLD_BINDINGS if b["name"] != "ADMIN_PASSWORD"]
        with self.assertRaisesRegex(d.Stop, "빠져요"):
            self.go("check")
        self.assertEqual([], self.cloud.deploys)

    def test_an_older_version_stops_unless_allowed(self):
        self.cloud.live = "2.1.0"
        with self.assertRaisesRegex(d.Stop, "낮아요"):
            self.go("check")
        self.assertIn("--allow-older", self.ship("check", allow_older=True))

    def test_deploy_goes_through_the_canary_then_serves_the_new_version(self):
        out = self.go()
        self.assertEqual([[NEW_ID, OLD_ID], [NEW_ID]], self.cloud.deploys)
        deploy = self.cloud.commands(d.WRANGLER + ["versions", "deploy"])
        at = len(d.WRANGLER) + 2
        self.assertEqual([f"{NEW_ID}@10%", f"{OLD_ID}@90%"], deploy[0][at:at + 2])
        self.assertEqual([(NEW_ID, 100.0)], self.cloud.serving)
        self.assertEqual(["*/10 * * * *", "0 * * * *"], self.cloud.schedules)
        self.assertIn("카나리 끝", out)
        self.assertIn("배포 끝", out)

    def test_a_new_version_that_fails_in_the_canary_goes_back(self):
        self.cloud.new_healthy = False
        with self.assertRaises(d.Stop) as e:
            self.go()
        self.assertEqual("카나리", e.exception.stage)
        self.assertEqual([[NEW_ID, OLD_ID], [OLD_ID]], self.cloud.deploys)
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)
        self.assertEqual(["0 * * * *"], self.cloud.schedules)  # 정기 작업은 카나리에서 건드리지 않아요
        self.assertIn("앞 판", self.printed)

    def test_a_failing_health_after_full_goes_back_with_the_old_schedules(self):
        self.cloud.sick_when_full = True
        with self.assertRaises(d.Stop) as e:
            self.go()
        self.assertEqual("확인", e.exception.stage)
        self.assertEqual([[NEW_ID, OLD_ID], [NEW_ID], [OLD_ID]], self.cloud.deploys)
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)
        self.assertEqual(["0 * * * *"], self.cloud.schedules)
        self.assertIn("되돌림", self.printed)


SHARE_OLD = [
    {"type": "plain_text", "name": "ANDROID_CERT_SHA256", "text": "AA:BB"},
    {"type": "version_metadata", "name": "CF_VERSION_METADATA"},
    {"type": "secret_text", "name": "RATE_SALT"},
    {"type": "kv_namespace", "name": "SHARES", "namespace_id": "abc123"},
    {"type": "secret_text", "name": "SHARE_ADMIN_TOKEN"},
]
DO_BINDING = {"type": "durable_object_namespace", "name": "TEAM_ROUND", "class_name": "TeamRound"}
SHARE_TOML = (
    'name = "bamnat-share"\nmain = "src/index.js"\nkeep_vars = true\n'
    '[version_metadata]\nbinding = "CF_VERSION_METADATA"\n'
    '[[kv_namespaces]]\nbinding = "SHARES"\nid = "REPLACE_WITH_KV_NAMESPACE_ID"\n'
    '[[durable_objects.bindings]]\nname = "TEAM_ROUND"\nclass_name = "TeamRound"\n'
    '[[migrations]]\ntag = "v1-team-round"\nnew_sqlite_classes = ["TeamRound"]\n'
)


class FakeShare(FakeCloud):
    """공유 서버: 실서버 바인딩 다섯, migration 태그 없음, 사용자 도메인 s.bamnat.app, workers.dev 켜짐·미리보기 꺼짐."""

    def __init__(self, wd):
        super().__init__(wd)
        self.views = {OLD_ID: SHARE_OLD}
        self.live = "2.0.0"
        self.tag = None
        self.domains = [{"hostname": "s.bamnat.app", "service": "bamnat-share", "enabled": True}]
        self.routes_readable = True
        self.deploy_rc = 0
        self.deploy_goes_live = True
        self.drops_domain = False
        self.wrangler_deploys = []  # wrangler deploy 명령(dry-run 포함)
        self.deploy_configs = []

    def run(self, cmd, cwd, extra_env=None, check=True, stage="", clean_env=False):
        args = cmd[len(d.WRANGLER):] if cmd[:len(d.WRANGLER)] == d.WRANGLER else []
        if args[:1] != ["deploy"]:
            return super().run(cmd, cwd, extra_env, check, stage, clean_env)
        self.calls.append((cmd, clean_env))
        self.wrangler_deploys.append(cmd)
        self.deploy_configs.append((Path(cwd) / "wrangler.deploy.toml").read_text(encoding="utf-8"))
        rc, out = 0, ""
        if "--dry-run" not in args:
            rc = self.deploy_rc
            if self.deploy_goes_live:
                self.serving, self.tag = [(NEW_ID, 100.0)], "v1-team-round"
                self.views[NEW_ID] = SHARE_OLD + [DO_BINDING]
                out = f"Current Version ID: {NEW_ID}"
                if self.drops_domain:
                    self.domains = []
        if check and rc:
            raise d.Stop(f"`wrangler deploy …` 실패 (코드 {rc})", stage=stage)
        return subprocess.CompletedProcess(cmd, rc, out, "")

    def cf(self, method, path, body=None):
        if path.endswith("/subdomain"):
            return {"result": {"enabled": True, "previews_enabled": False}}
        if "/workers/domains?service=bamnat-share" in path:
            return {"result": list(self.domains)}
        if path.endswith("/environments/production/routes"):
            if not self.routes_readable:
                raise RuntimeError("403")
            return {"result": []}
        if path.endswith("/workers/services/bamnat-share"):
            return {"result": {"default_environment": {"script": {"migration_tag": self.tag}}}}
        raise AssertionError(path)

    def get(self, url, headers=None):
        new = self.serving == [(NEW_ID, 100.0)]
        if url.endswith("/api/health"):
            return (503, "") if new and not self.new_healthy else (200, json.dumps({"ok": True, "version": "2.0.0" if new else self.live}))
        return 404, ""


class MigrateFlowTest(unittest.TestCase):
    """2.0.0 [RVD-33] DO 첫 배포를 ship 일에서 끝까지(가짜 wrangler·Cloudflare)."""

    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.out = self.dir / "ship"
        (self.out / "share").mkdir(parents=True)
        (self.out / "share/worker.js").write_text("export default {}", encoding="utf-8")
        (self.out / "share/wrangler.toml").write_text(SHARE_TOML, encoding="utf-8")
        (self.out / "share/version.txt").write_text("2.0.0", encoding="utf-8")
        self.cloud = FakeShare(self.dir)
        self.keep = d.run, d.cf, d.get, d.time
        d.run, d.cf, d.get = self.cloud.run, self.cloud.cf, self.cloud.get
        d.time = types.SimpleNamespace(sleep=lambda s: None)
        self.env = {k: d.os.environ.get(k) for k in ("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "GITHUB_STEP_SUMMARY", "GITHUB_OUTPUT")}
        for k in self.env:
            d.os.environ.pop(k, None)
        d.os.environ["CLOUDFLARE_ACCOUNT_ID"] = "acc"
        self.printed = ""

    def tearDown(self):
        d.run, d.cf, d.get, d.time = self.keep
        for k, v in self.env.items():
            if v is None:
                d.os.environ.pop(k, None)
            else:
                d.os.environ[k] = v
        shutil.rmtree(self.dir)

    def ship(self, mode, workers=("share",)):
        out = io.StringIO()
        d.os.environ["CLOUDFLARE_API_TOKEN"] = "test-token-not-real"
        try:
            with contextlib.redirect_stdout(out):
                d.ship(list(workers), self.out, mode, "0123456789abcdef", "DO 바닥", False)
        finally:
            d.os.environ.pop("CLOUDFLARE_API_TOKEN", None)
            self.printed = out.getvalue()
        return self.printed

    def real_deploys(self):
        return [c for c in self.cloud.wrangler_deploys if "--dry-run" not in c]

    def test_migrate_check_only_dry_runs(self):
        out = self.ship("migrate-check")
        # 지금 붙은 연결을 요약에 남기고, workers.dev·미리보기 URL은 지금 값으로 적어 dry-run해요. 손님은 앞 판 그대로.
        self.assertIn("사용자 도메인 s.bamnat.app · workers.dev 켜짐 · 미리보기 URL 꺼짐 · route 없음", out)
        self.assertIn("workers_dev = true\npreview_urls = false\n", self.cloud.deploy_configs[0])
        self.assertEqual([("--dry-run" in c) for c in self.cloud.wrangler_deploys], [True])
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)
        self.assertEqual([], self.cloud.commands(d.WRANGLER + ["versions", "upload"]) + self.cloud.commands(d.WRANGLER + ["versions", "deploy"]))
        self.assertIn("새 되돌리기 바닥", out)
        self.assertIn(f"앞 판 `{OLD_ID[:8]}`", out)

    def test_migrate_deploys_once_keeps_vars_and_has_no_canary(self):
        out = self.ship("migrate")
        real = self.real_deploys()
        self.assertEqual(len(real), 1)
        self.assertIn("--keep-vars", real[0])
        self.assertIn("ANDROID_CERT_SHA256:AA:BB", real[0])  # 대시보드 일반 변수는 값까지 넘겨요
        self.assertNotIn("RATE_SALT", " ".join(real[0]))  # 비밀값은 건드리지 않아요
        # upload도 카나리(versions deploy)도 없어요: 올리는 것은 wrangler deploy 한 번뿐.
        self.assertEqual([], self.cloud.commands(d.WRANGLER + ["versions", "upload"]) + self.cloud.commands(d.WRANGLER + ["versions", "deploy"]))
        self.assertEqual([(NEW_ID, 100.0)], self.cloud.serving)
        self.assertIn("preview_urls = false", self.cloud.deploy_configs[-1])
        self.assertIn("Durable Object 첫 배포 끝", out)
        self.assertIn(f"새 되돌리기 바닥 `{NEW_ID[:8]}`", out)

    def test_a_failing_health_never_rolls_back(self):
        # health가 실패해도 앞 판으로 versions deploy 하지 않아요(Cloudflare가 막고, 시도하면 헷갈려요).
        self.cloud.new_healthy = False
        with self.assertRaises(d.Stop) as e:
            self.ship("migrate")
        self.assertEqual("DO 첫 배포 확인", e.exception.stage)
        self.assertEqual([], self.cloud.commands(d.WRANGLER + ["versions", "deploy"]))
        self.assertIn("되돌리지 않아요", str(e.exception))
        self.assertIn("되돌릴 수 없어요", "\n".join(d.summary_lines(e.exception.stage)))
        # wrangler deploy가 올리기 전에 실패했으면(손님은 앞 판) 앞 단계 멈춤이에요.
        self.cloud.new_healthy, self.cloud.deploy_rc, self.cloud.deploy_goes_live = True, 1, False
        with self.assertRaises(d.Stop) as e:
            self.ship("migrate")
        self.assertEqual("DO 첫 배포", e.exception.stage)
        # 올린 뒤 실패(트리거 등)면 앞 판이 아니라 새 판을 확인하고 실패로 알려요.
        self.cloud.deploy_goes_live = True
        self.cloud.serving, self.cloud.tag = [(OLD_ID, 100.0)], None
        with self.assertRaises(d.Stop) as e:
            self.ship("migrate")
        self.assertEqual("DO 첫 배포 확인", e.exception.stage)
        self.assertIn("코드 1", str(e.exception))

    def test_a_domain_that_falls_off_is_said_loudly(self):
        # 올린 뒤 사용자 도메인(s.bamnat.app)이 떨어지면 무엇을 다시 붙여야 하는지 알려요.
        self.cloud.drops_domain = True
        with self.assertRaises(d.Stop) as e:
            self.ship("migrate")
        self.assertIn("사용자 도메인: ['s.bamnat.app'] → []", str(e.exception))
        self.assertIn("다시 붙여요", str(e.exception))

    def test_unreadable_routes_do_not_stop(self):
        # route를 못 읽어도(zone 권한 없음) 멈추지 않고 '읽지 못함'으로 남겨요.
        self.cloud.routes_readable = False
        out = self.ship("migrate")
        self.assertIn("route 읽지 못함", out)
        self.assertIn("Durable Object 첫 배포 끝", out)

    def test_migrate_is_one_server_at_a_time(self):
        with self.assertRaisesRegex(d.Stop, "한 서버씩"):
            self.ship("migrate", workers=("company", "share"))
        self.assertEqual([], self.cloud.calls)
        with self.assertRaisesRegex(d.Stop, "한 서버씩"):
            self.ship("migrate-check", workers=("company", "share"))

    def test_migrate_stops_when_there_is_nothing_to_apply(self):
        self.cloud.tag = "v1-team-round"  # 실서버에 이미 적용됨 → 보통 deploy로
        with self.assertRaisesRegex(d.Stop, "이미 모두"):
            self.ship("migrate-check")
        self.assertEqual([], self.cloud.wrangler_deploys)

    def test_normal_ship_stops_on_a_pending_migration_and_goes_on_when_applied(self):
        # 보통 ship은 설정에 실서버보다 새 migration이 있으면 올리기 전에 migrate로 보내요(versions upload까지 가지 않아요).
        with self.assertRaises(d.Stop) as e:
            self.ship("check")
        self.assertEqual("올리기", e.exception.stage)
        self.assertIn("migrate", e.exception.next_step)
        self.assertEqual([], self.cloud.commands(d.WRANGLER + ["versions", "upload"]))
        # 실서버에 이미 적용됐으면 보통 ship으로 올라가요(check는 올리기·대조까지).
        self.cloud.tag = "v1-team-round"
        self.cloud.views[OLD_ID] = SHARE_OLD + [DO_BINDING]
        self.cloud.new_bindings = SHARE_OLD + [DO_BINDING]
        out = self.ship("check")
        self.assertEqual(1, len(self.cloud.uploads))
        self.assertIn("check 모드", out)
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)

    def test_a_share_bundle_without_the_room_class_stops_on_the_lost_binding(self):
        # 바닥 판이 올라간 뒤 TeamRound 바인딩이 없는 공유 묶음은 보통 ship이 '앞 판 바인딩이 빠져요'로 멈춰요(손님은 앞 판 그대로).
        self.cloud.tag = "v1-team-round"
        self.cloud.views[OLD_ID] = SHARE_OLD + [DO_BINDING]
        self.cloud.new_bindings = list(SHARE_OLD)
        (self.out / "share/wrangler.toml").write_text(SHARE_TOML.split("[[durable_objects.bindings]]")[0], encoding="utf-8")
        with self.assertRaisesRegex(d.Stop, "앞 판 바인딩이 빠져요"):
            self.ship("check")
        self.assertEqual([], self.cloud.deploys)


if __name__ == "__main__":
    unittest.main()
