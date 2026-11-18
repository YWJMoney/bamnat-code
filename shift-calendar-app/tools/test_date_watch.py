#!/usr/bin/env python3
"""2.0.0 [I60b-01][U60-01] tools/date_watch.py 시험: python3 tools/test_date_watch.py"""
import contextlib
import datetime
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import date_watch as w  # noqa: E402

GRADLE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.bamnat.core.CalendarI123Test" tests="3" skipped="1" failures="1" errors="0">
  <testcase name="holidayTableWarns" classname="com.bamnat.core.CalendarI123Test" time="0.01">
    <failure message="java.lang.AssertionError: 2027 table missing">java.lang.AssertionError: 2027 table missing
\tat org.junit.Assert.fail(Assert.java:89)
\tat com.bamnat.core.holiday.KoreanHolidays.check(KoreanHolidays.kt:120)
\tat com.bamnat.core.CalendarI123Test.holidayTableWarns(CalendarI123Test.kt:305)
</failure>
  </testcase>
  <testcase name="fine" classname="com.bamnat.core.CalendarI123Test" time="0.01"/>
  <testcase name="later" classname="com.bamnat.core.CalendarI123Test" time="0"><skipped/></testcase>
</testsuite>
"""
NODE = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testcase name="good one" time="0.001" classname="test"/>
  <testcase name="새해 첫날 시험" time="0.001" classname="test" failure="x">
    <failure type="testCodeFailure" message="Expected values to be strictly equal:1 !== 2">
      at TestContext.&lt;anonymous> (file:///w/server/company-worker/test/village-r.test.mjs:41:33)
      at Test.runInAsyncScope (node:async_hooks:214:14)
    </failure>
  </testcase>
  <testsuite name="묶음">
    <testcase name="속 시험" classname="test"><failure message="boom">no location here</failure></testcase>
  </testsuite>
</testsuites>
"""


class DateWatchTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "core").mkdir()
        (self.dir / "core/TEST-a.xml").write_text(GRADLE, encoding="utf-8")
        (self.dir / "node.xml").write_text(NODE, encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_reads_gradle_and_node_failures_with_their_line(self):
        g = w.read([str(self.dir / "core")])
        self.assertEqual((3, 1), (g["tests"], g["skipped"]))
        self.assertEqual([{"name": "CalendarI123Test.holidayTableWarns", "at": "CalendarI123Test.kt:305"}], g["failed"])
        n = w.read([str(self.dir / "node.xml")])
        self.assertEqual(3, n["tests"])
        self.assertEqual(["village-r.test.mjs › 새해 첫날 시험", "속 시험"], [f["name"] for f in n["failed"]])
        self.assertEqual("village-r.test.mjs:41", n["failed"][0]["at"])

    def test_report_names_the_day_it_will_break(self):
        today = datetime.date(2026, 11, 12)
        lines = w.report("core", 30, w.read([str(self.dir / "core")]), today)
        self.assertEqual("**core · 12월 12일(+30일)에 깨질 시험 1개** (3개 중) — 그날이 오기 전에 고쳐요", lines[0])
        self.assertIn("`CalendarI123Test.holidayTableWarns` (CalendarI123Test.kt:305)", lines[1])
        self.assertIn("깨질 시험 없음", w.report("core", 365, {"tests": 9, "skipped": 0, "failed": []}, today)[0])
        self.assertEqual("**서버 · 11월 12일(오늘) 깨진 시험 1개** (3개 중)", w.report("서버", 0, w.read([str(self.dir / "core")]), today)[0])
        self.assertEqual("11월 12일(+365일)", w.day_label(today, 365))
        self.assertEqual("11월 12일(오늘)", w.day_label(today, 0))

    def test_report_writes_json_for_the_next_day(self):
        out = self.dir / "o/core+30.json"
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            w.main(["report", "--label", "core", "--days", "30", "--today", "2026-11-12", "--out", str(out), str(self.dir / "core")])
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertEqual(("core", 30, "2026-12-12", "2026-11-12"), (data["label"], data["days"], data["date"], data["ran"]))
        self.assertEqual(1, len(data["failed"]))
        self.assertIn("12월 12일", buf.getvalue())

    def test_compare_says_new_today_and_whether_the_code_changed(self):
        prev = {"sha": "aaaa1111", "tests": 3, "failed": [{"name": "A.old", "at": ""}]}
        cur = {"sha": "aaaa1111", "tests": 3, "failed": [{"name": "A.new", "at": ""}]}
        lines = w.compare(prev, cur)
        self.assertIn("오늘 처음 깨짐 1개", lines[0])
        self.assertIn("코드 변경 없음", lines[0])
        self.assertIn("`A.new`", lines[1])
        self.assertIn("오늘 통과 1개", lines[2])
        moved = w.compare({**prev, "sha": "bbbb2222"}, cur)
        self.assertIn("main이 바뀜", moved[0])
        self.assertEqual(["어제와 같아요(새로 깨진 시험 없음)"], w.compare(cur, cur))

    def test_against_tells_this_change_from_the_base(self):
        base = {"tests": 10, "failed": [{"name": "A.date", "at": ""}]}
        self.assertEqual([("A.date", "기준에서도 깨짐"), ("A.mine", "기준에서는 통과")], w.against(base, ["A.date", "A.mine"]))
        self.assertEqual([("A.x", "기준 결과 없음")], w.against(None, ["A.x"]))
        self.assertEqual([("A.x", "기준에 없음")], w.against({"tests": 0, "failed": []}, ["A.x"]))

    def test_explain_says_what_to_do_after_a_deploy_stopped_at_the_tests(self):
        fail = {"worker": "company", "failed": [{"name": "a.test.mjs › 새해", "at": "a.test.mjs:4"}, {"name": "b.test.mjs › 내 것", "at": ""}]}
        main = {"tests": 700, "failed": [{"name": "a.test.mjs › 새해", "at": "a.test.mjs:4"}]}
        lines = w.explain(fail, main)
        self.assertEqual("**회사 서버: 실패한 시험 2개**", lines[0])
        self.assertIn("`a.test.mjs › 새해` (a.test.mjs:4): 어제 main에서도 깨짐 → 이 브랜치 탓이 아니에요", lines[1])
        self.assertIn("skip_tests", lines[1])
        self.assertIn("`b.test.mjs › 내 것`: 어제 main에서는 통과 → 이 브랜치의 바뀜을 먼저 봐요", lines[2])
        self.assertIn("어제 main 결과 없음", w.explain(fail, None)[1])
        self.assertIn("읽지 못했어요", w.explain({"worker": "share", "failed": []}, main)[0])

    def test_missing_files_are_not_an_error(self):
        self.assertEqual({"tests": 0, "skipped": 0, "failed": []}, w.read([str(self.dir / "nope")]))
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            self.assertEqual(0, w.main(["compare", str(self.dir / "nope.json"), str(self.dir / "nope2.json")]))
        self.assertIn("못 읽었어요", buf.getvalue())


if __name__ == "__main__":
    unittest.main()
