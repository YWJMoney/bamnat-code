#!/usr/bin/env python3
"""2.0.0 [RVR-46] tools/bundle_check.py 시험: python3 tools/test_bundle_check.py"""
import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bundle_check as b  # noqa: E402


def run(tests, failed=(), skipped=(), counts=None, seconds=600):
    return {"tests": tests, "seconds": seconds, "skipped": list(skipped), "failed": [{"name": n, "at": ""} for n in failed],
            "classes": {k: 1.0 for k in (counts or {})}, "counts": counts or {}}


def all_runs():
    return {n: run(10, counts={f"{n}Test": [10, 0, 0]}) for n in b.EXPECT}


GOOD = {"core": {"result": "success"}, "app": {"result": "success"}, "lint": {"result": "success"}, "servers": {"result": "success"}}


class BundleCheckTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_a_passing_shard_is_not_called_failed_because_another_shard_failed(self):
        runs = all_runs()
        runs["shots-e"] = run(10, failed=["RvjSetupDraftTest.theWizardKeptDarkBig"], counts={"RvjSetupDraftTest": [10, 1, 0]})
        r = b.build(runs, {**GOOD, "app": {"result": "failure"}})
        by = {row[0]: row[1] for row in r["jobs"]}
        self.assertEqual("failure", by["shots-e"])
        self.assertEqual("success", by["shots-a"])
        self.assertEqual("통과하지 못함", r["verdict"])

    def test_everything_passed_says_so_and_lists_the_classes(self):
        r = b.build(all_runs(), GOOD, "abcdef1234567")
        self.assertEqual("모두 통과", r["verdict"], r["problems"])
        self.assertEqual(["shots-aTest"], r["passed"]["shots-a"][0])
        md = b.markdown(r)
        self.assertIn("# 묶음 확인 — abcdef123", md)
        self.assertIn("### shots-e: 1개", md)

    def test_a_failed_test_is_named_and_its_class_is_not_listed_as_passed(self):
        runs = all_runs()
        runs["shots-b"] = run(10, failed=["NtcScreenshotTest.x"], counts={"NtcScreenshotTest": [7, 1, 0], "OkTest": [3, 0, 0]})
        r = b.build(runs, GOOD)
        self.assertEqual("통과하지 못함", r["verdict"])
        self.assertTrue(any("NtcScreenshotTest.x" in p for p in r["problems"]))
        self.assertEqual((["OkTest"], []), r["passed"]["shots-b"])

    def test_a_missing_shard_is_not_passed_over(self):
        runs = all_runs()
        del runs["shots-d"]
        r = b.build(runs, GOOD)
        self.assertEqual("통과하지 못함", r["verdict"])
        self.assertTrue(any(p.startswith("shots-d: 결과 파일이 없어요") for p in r["problems"]))

    def test_a_cancelled_job_fails_the_bundle_even_with_every_file_there(self):
        needs = dict(GOOD, lint={"result": "cancelled"})
        r = b.build(all_runs(), needs)
        self.assertEqual("통과하지 못함", r["verdict"])
        self.assertIn("lint: cancelled", r["problems"])

    def test_a_class_with_a_skipped_test_is_listed_apart(self):
        runs = all_runs()
        runs["unit"] = run(5, skipped=["ATest.later"], counts={"ATest": [4, 0, 1], "BTest": [1, 0, 0]})
        r = b.build(runs, GOOD)
        self.assertEqual((["BTest"], ["ATest"]), r["passed"]["unit"])
        # said, but not a failure: some tests are skipped on purpose
        self.assertTrue(any("건너뜀 1개" in p for p in r["notes"]))
        self.assertEqual("모두 통과", r["verdict"])
        self.assertIn("## 건너뛴 시험", b.markdown(r))

    def test_read_finds_the_files_gh_downloads_into_a_folder_each(self):
        for sub, name in (("tests-core", "tests-core.json"), ("tests-app-shots-b", "tests-app-shots-b.json")):
            (self.dir / sub).mkdir()
            (self.dir / sub / name).write_text(json.dumps(run(1)), encoding="utf-8")
        self.assertEqual(["core", "shots-b"], sorted(b.read(self.dir)))

    def test_main_writes_the_markdown_and_json_and_exits_by_the_verdict(self):
        for n, r in all_runs().items():
            (self.dir / f"tests-{'core' if n == 'core' else 'app-' + n}.json").write_text(json.dumps(r), encoding="utf-8")
        needs = self.dir / "needs.json"
        needs.write_text(json.dumps(GOOD), encoding="utf-8")
        out = self.dir / "out"
        with contextlib.redirect_stdout(io.StringIO()):
            code = b.main([str(self.dir), "--needs", str(needs), "--sha", "abc", "--out", str(out)])
        self.assertEqual(0, code)
        self.assertTrue((out / "묶음-확인.md").exists() and (out / "bundle-check.json").exists())


if __name__ == "__main__":
    unittest.main()
