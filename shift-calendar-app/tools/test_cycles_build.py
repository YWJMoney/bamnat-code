#!/usr/bin/env python3
"""2.0.0 [RVR-08] (검토 TEST-16·U60-07) 근무주기 만들기 도구 시험: python3 tools/test_cycles_build.py

번호는 줄 순서가 아니라 CSV의 번호 칸, 판은 내용이 바뀔 때만 저절로, 출처 없는 줄·없는 날짜는 거절, 쓰지 못한 시간은 목록으로.
"""
import csv
import datetime
import io
import json
import pathlib
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import cycles_build as cb  # noqa: E402

ROW = ["삼교대 회사 [생산] · 화학", "삼교대 회사", "화학", "생산", "4조 2교대", "주·주·야·야·휴·휴", "주 07:00-19:00 / 야 19:00-07:00",
       "2026-01 공고", "공고", "https://example.com/a", "", "", ""]


class CyclesBuildTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        self.src, self.out = self.dir / "c.csv", self.dir / "cycles.json"
        self.keep = cb.SRC, cb.OUT
        cb.SRC, cb.OUT = self.src, self.out

    def tearDown(self):
        cb.SRC, cb.OUT = self.keep
        shutil.rmtree(self.dir)

    def write(self, rows, head=None):
        buf = io.StringIO()
        csv.writer(buf, lineterminator="\n").writerows([head or cb.HEAD] + rows)
        self.src.write_text(buf.getvalue(), encoding="utf-8")

    def row(self, name, **k):
        r = list(ROW)
        r[0], r[1] = f"{name} [생산] · 화학", name
        for i, v in k.items():
            r[int(i[1:])] = v
        return r

    def make(self, today=datetime.date(2026, 11, 12)):
        body, given = cb.read_rows()
        doc = cb.build(body, [], today)
        if given:
            cb.write_rows(body)
        self.out.write_text(cb.text(doc), encoding="utf-8")
        return doc

    def test_a_row_put_in_the_middle_keeps_every_other_id(self):
        self.write([self.row("가"), self.row("나")])
        first = {c["company"]: c["id"] for c in self.make()["data"]["cycles"]}
        self.assertEqual({"가": "c001", "나": "c002"}, first)
        rows = list(csv.reader(self.src.read_text(encoding="utf-8").splitlines()))
        self.assertEqual("c002", rows[2][12], "the number is written back to the CSV")
        body = rows[1:]
        body.insert(1, self.row("다"))
        self.write(body)
        after = {c["company"]: c["id"] for c in self.make()["data"]["cycles"]}
        self.assertEqual({"가": "c001", "다": "c003", "나": "c002"}, after)

    def test_an_old_csv_without_the_column_gets_the_ids_it_had_by_place(self):
        self.write([self.row("가")[:12], self.row("나")[:12]], head=cb.HEAD[:-1])
        self.assertEqual(["c001", "c002"], [c["id"] for c in self.make()["data"]["cycles"]])
        self.assertEqual(cb.HEAD, next(csv.reader(self.src.read_text(encoding="utf-8").splitlines())))

    def test_the_version_moves_only_when_the_cycles_change(self):
        self.write([self.row("가")])
        v1 = self.make(datetime.date(2026, 11, 12))
        self.assertEqual((2026111201, "2026-11-12"), (v1["version"], v1["issued"]))
        same = self.make(datetime.date(2026, 12, 1))
        self.assertEqual((2026111201, "2026-11-12"), (same["version"], same["issued"]), "nothing changed, same version")
        self.write([self.row("가", c6="주 06:00-18:00 / 야 18:00-06:00", c12="c001")])
        again = self.make(datetime.date(2026, 11, 12))
        self.assertEqual(2026111202, again["version"], "a second change on the same day is one more")
        later = self.make(datetime.date(2026, 11, 20))
        self.assertEqual(2026111202, later["version"])

    def test_a_row_without_a_source_link_is_refused_but_a_users_report_is_not(self):
        self.write([self.row("가", c9="")])
        with self.assertRaises(SystemExit) as e:
            self.make()
        self.assertIn("출처", str(e.exception))
        self.write([self.row("가", c5="순서:DDNNOO", c8="밤낮 제보 (관리자 확인)", c9="")])
        self.assertEqual("DDNNOO", self.make()["data"]["cycles"][0]["codes"])

    def test_a_team_day_that_is_not_a_real_day_is_refused(self):
        self.write([self.row("가", c10="1조|2조", c11="1조=2026-02-30")])
        with self.assertRaises(SystemExit) as e:
            self.make()
        self.assertIn("real day", str(e.exception))

    def test_a_number_twice_is_refused(self):
        self.write([self.row("가", c12="c001"), self.row("나", c12="c001")])
        with self.assertRaises(SystemExit):
            cb.read_rows()

    def test_times_it_could_not_place_are_listed(self):
        self.write([self.row("가", c5="", c6="06-14 / 14-22 / 22-06 / 06-18 / 18-06", c4="3조 3교대")])
        body, _ = cb.read_rows()
        lost = []
        cb.build(body, lost, datetime.date(2026, 11, 12))
        self.assertEqual(1, len(lost))
        self.assertEqual(["06:00-18:00", "18:00-06:00"], lost[0][3])

    def test_the_repository_file_is_what_the_csv_builds(self):
        cb.SRC, cb.OUT = self.keep
        body, given = cb.read_rows()
        self.assertFalse(given, "every row has its 번호")
        self.assertEqual(cb.OUT.read_text(encoding="utf-8"), cb.text(cb.build(body, [])))


if __name__ == "__main__":
    unittest.main()
