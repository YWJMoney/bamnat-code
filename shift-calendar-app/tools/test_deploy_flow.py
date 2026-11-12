#!/usr/bin/env python3
"""2.0.0 [RVR-17] (검토 TEST-14·I60a-03) 배포 도구의 흐름 시험: python3 tools/test_deploy_flow.py

진짜 wrangler·Cloudflare·서버에는 닿지 않아요. deploy_worker의 run·cf·get·time을 가짜로 바꿔 끼우고, 서버 폴더는 임시 폴더에
만들어요. build(토큰 없이 시험·번들)와 ship(토큰, build가 만든 파일만)을 차례로 끝까지 돌려요(2.0.0 [RVQ-01]부터 둘로 나뉨).
시험하는 길: 판이 나뉘어 돎, check 모드, 대시보드에만 있던 변수 다시 올리기, 바인딩 빠짐, 판 낮음, 시험 실패, build에 토큰,
카나리 뒤 배포, 카나리에서 나쁘면 되돌리기, 100% 뒤 health 실패면 앞 판·정기 작업 되돌리기, 날짜에 낡은 시험 넘기기.
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


if __name__ == "__main__":
    unittest.main()
