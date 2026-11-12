#!/usr/bin/env python3
"""2.0.0 [RVR-17] (검토 TEST-14·I60a-03) 배포 도구 deploy()의 흐름 시험: python3 tools/test_deploy_flow.py

진짜 wrangler·Cloudflare·서버에는 닿지 않아요. deploy_worker의 run·cf·get·time을 가짜로 바꿔 끼우고, 서버 폴더는 임시 폴더에
만들어요. 시험하는 길: 판이 나뉘어 돎, check 모드, 대시보드에만 있던 변수 다시 올리기, 바인딩 빠짐, 판 낮음, 배포 끝, health
실패면 앞 판으로 되돌리기, 날짜에 낡은 시험 넘기기.
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
TOML = f'''name = "bamnat-company"
main = "src/index.js"
[[d1_databases]]
binding = "DB"
database_id = "REPLACE_WITH_D1_DATABASE_ID"
[triggers]
crons = ["*/10 * * * *", "0 * * * *"]
'''


class FakeCloud:
    """wrangler와 Cloudflare API와 서버 health를 흉내 내요. 무엇을 불렀는지 calls에 남아요."""

    def __init__(self, wd):
        self.wd = wd
        self.serving = [(OLD_ID, 100.0)]
        self.views = {OLD_ID: OLD_BINDINGS}
        self.new_bindings = [b for b in OLD_BINDINGS]  # 새 판이 받을 바인딩(시험마다 바꿈)
        self.uploads = []
        self.deploys = []
        self.schedules = ["0 * * * *"]
        self.health_ok = True
        self.live = "1.39.0"
        self.calls = []
        self.tests_fail = False

    def run(self, cmd, cwd, extra_env=None, check=True):
        self.calls.append(cmd)
        out = ""
        code = 0
        if cmd[:2] == ["npm", "run"]:
            (self.wd / "dist").mkdir(exist_ok=True)
            (self.wd / "dist/worker.js").write_text("export default {}", encoding="utf-8")
        elif cmd[:2] == ["npm", "test"] or cmd[:2] == ["node", "--test"]:
            code = 1 if self.tests_fail else 0
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
                vid = NEW_ID if len(self.uploads) == 1 else NEW_ID[:-1] + str(len(self.uploads) + 1)
                bindings = list(self.new_bindings) + [{"type": "plain_text", "name": k, "text": v} for k, v in given.items()]
                self.views[vid] = bindings
                Path(extra_env["WRANGLER_OUTPUT_FILE_PATH"]).write_text(json.dumps({"type": "version-upload", "version_id": vid}) + "\n", encoding="utf-8")
            elif args[:2] == ["versions", "deploy"]:
                vid = args[2].split("@")[0]
                self.deploys.append(vid)
                self.serving = [(vid, 100.0)]
        p = subprocess.CompletedProcess(cmd, code, out, "")
        if check and code:
            raise d.Stop(f"`{' '.join(cmd[:4])} …` 실패 (코드 {code})")
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

    def get(self, url):
        if url.endswith("/admin/"):
            return 200, "<html>"
        version = self.version_of_serving()
        if "health" in url:
            if not self.health_ok and self.serving[0][0] != OLD_ID:
                return 503, json.dumps({"database": False})
            return 200, json.dumps({"database": True, "password": True, "salt": True, "schema": True, "config": True, "version": version})
        return 404, ""

    def version_of_serving(self):
        return "2.0.0" if self.serving[0][0] != OLD_ID else self.live


class DeployFlowTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.wd = self.dir / "company-worker"
        (self.wd / "src").mkdir(parents=True)
        (self.wd / "test").mkdir()
        (self.wd / "test" / "a.test.mjs").write_text("", encoding="utf-8")
        (self.wd / "wrangler.toml").write_text(TOML, encoding="utf-8")
        (self.wd / "src/version.js").write_text('export const SERVER_VERSION = "2.0.0";\n', encoding="utf-8")
        self.cloud = FakeCloud(self.wd)
        self.keep = d.SERVER, d.run, d.cf, d.get, d.time
        d.SERVER, d.run, d.cf, d.get = self.dir, self.cloud.run, self.cloud.cf, self.cloud.get
        d.time = types.SimpleNamespace(sleep=lambda s: None)
        self.env = {k: d.os.environ.get(k) for k in ("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID", "GITHUB_STEP_SUMMARY")}
        d.os.environ["CLOUDFLARE_API_TOKEN"] = "test-token-not-real"
        d.os.environ.pop("CLOUDFLARE_ACCOUNT_ID", None)
        d.os.environ.pop("GITHUB_STEP_SUMMARY", None)

    def tearDown(self):
        d.SERVER, d.run, d.cf, d.get, d.time = self.keep
        for k, v in self.env.items():
            if v is None:
                d.os.environ.pop(k, None)
            else:
                d.os.environ[k] = v
        shutil.rmtree(self.dir)

    def go(self, mode="deploy", allow_older=False, skips=()):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            d.deploy("company", mode, "0123456789abcdef", "시험", allow_older, skips)
        return out.getvalue()

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
        # 자리표시자는 앞 판의 DB 번호로 채워 배포용 설정에만(저장소 파일은 그대로).
        self.assertIn(DB_ID, (self.wd / "wrangler.deploy.toml").read_text(encoding="utf-8"))
        self.assertIn("REPLACE_WITH_D1_DATABASE_ID", (self.wd / "wrangler.toml").read_text(encoding="utf-8"))

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
        self.assertIn("--allow-older", self.go("check", allow_older=True))

    def test_failing_server_tests_stop_before_upload(self):
        self.cloud.tests_fail = True
        with self.assertRaisesRegex(d.Stop, "npm test"):
            self.go()
        self.assertEqual([], self.cloud.uploads)

    def test_deploy_serves_the_new_version_and_matches_the_schedules(self):
        out = self.go()
        self.assertEqual([NEW_ID], self.cloud.deploys)
        self.assertEqual([(NEW_ID, 100.0)], self.cloud.serving)
        self.assertEqual(["*/10 * * * *", "0 * * * *"], self.cloud.schedules)
        self.assertIn("배포 끝", out)

    def test_a_failing_health_goes_back_to_the_old_version(self):
        self.cloud.health_ok = False
        out = io.StringIO()
        with self.assertRaises(SystemExit), contextlib.redirect_stdout(out):
            d.deploy("company", "deploy", "0123456789abcdef", "시험", False, ())
        self.assertEqual([NEW_ID, OLD_ID], self.cloud.deploys)
        self.assertEqual([(OLD_ID, 100.0)], self.cloud.serving)
        self.assertIn("앞 판", out.getvalue())

    def test_a_stale_test_is_skipped_by_name_and_written_down(self):
        today = datetime.date(2027, 1, 1)
        skips = d.parse_skips(["새해 시험|2027-01-01 날짜 폭탄, 시험 고침 PR 진행 중|2027-01-05"], today)
        out = self.go("check", skips=skips)
        tests = [c for c in self.cloud.calls if c[:2] == ["node", "--test"]]
        self.assertEqual(1, len(tests))
        self.assertIn("--test-skip-pattern=^새해\\ 시험$", tests[0])
        self.assertIn("넘긴 시험", out)
        self.assertNotIn(["npm", "test"], self.cloud.calls)

    def test_a_skip_needs_a_reason_and_a_near_deadline(self):
        today = datetime.date(2027, 1, 1)
        for bad in ["새해 시험||2027-01-05", "새해 시험|까닭|2026-12-31", "새해 시험|까닭|2027-02-01", "새해 시험|까닭|내일"]:
            with self.assertRaises(d.Stop, msg=bad):
                d.parse_skips([bad], today)
        self.assertEqual(2, len(d.parse_skips(["a|b|2027-01-02; c|d|2027-01-03"], today)))
        self.assertEqual([], d.parse_skips([], today))


if __name__ == "__main__":
    unittest.main()
