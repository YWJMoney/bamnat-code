#!/usr/bin/env python3
"""2.0.0 [U60-01] tools/slow_tests.py 시험: python3 tools/test_slow_tests.py"""
import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import slow_tests as s  # noqa: E402

XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.bamnat.app.A" tests="4" skipped="1" failures="2" errors="0" time="3">
  <testcase name="dateBomb" classname="com.bamnat.app.A" time="1"><failure message="x">boom
\tat com.bamnat.app.A.dateBomb(A.kt:12)</failure></testcase>
  <testcase name="mine" classname="com.bamnat.app.A" time="1"><failure message="y">boom
\tat com.bamnat.app.A.mine(A.kt:40)</failure></testcase>
  <testcase name="fine" classname="com.bamnat.app.A" time="1"/>
  <testcase name="later" classname="com.bamnat.app.A" time="0"><skipped message="no node"/></testcase>
</testsuite>
"""


class SlowTestsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "r").mkdir()
        (self.dir / "r/TEST-A.xml").write_text(XML, encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.dir)

    def run_it(self, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            s.main([str(self.dir / "r"), *extra])
        return out.getvalue()

    def test_json_keeps_failures_with_their_line(self):
        self.run_it("--json", str(self.dir / "t.json"))
        data = json.loads((self.dir / "t.json").read_text(encoding="utf-8"))
        self.assertEqual([{"name": "A.dateBomb", "at": "A.kt:12"}, {"name": "A.mine", "at": "A.kt:40"}], data["failed"])
        self.assertEqual(["A.later"], data["skipped"])

    def test_failures_are_told_against_the_base(self):
        (self.dir / "base.json").write_text(json.dumps({"tests": 4, "failed": [{"name": "A.dateBomb", "at": "A.kt:12"}]}), encoding="utf-8")
        out = self.run_it("--base", str(self.dir / "base.json"))
        first = out.splitlines()
        self.assertEqual("**실패 2개**", first[0])
        self.assertIn("이 PR에서 처음 깨짐 1개: `A.mine` (A.kt:40)", first[1])
        self.assertIn("기준에서도 깨짐", first[2])
        self.assertIn("`A.dateBomb` (A.kt:12)", first[2])
        self.assertLess(out.index("실패 2개"), out.index("건너뜀 1개"), "실패가 맨 위")

    def test_without_a_base_it_says_so(self):
        out = self.run_it("--base", str(self.dir / "missing.json"))
        self.assertIn("기준 브랜치 결과를 못 받아 견주지 않았어요", out.splitlines()[0])
        self.assertIn("새 시험이거나 기준 결과 없음 2개", out)

    def test_no_failures_no_lines(self):
        (self.dir / "r/TEST-A.xml").write_text(XML.replace('<failure message="x">', "<!--").replace('(A.kt:12)</failure>', "-->")
                                              .replace('<failure message="y">', "<!--").replace('(A.kt:40)</failure>', "-->"), encoding="utf-8")
        self.assertTrue(self.run_it().startswith("**건너뜀 1개**"))


if __name__ == "__main__":
    unittest.main()
