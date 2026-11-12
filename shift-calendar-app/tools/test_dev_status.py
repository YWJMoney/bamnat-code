#!/usr/bin/env python3
"""Tests for tools/dev_status.py [LOOP-05]. Run: python3 tools/test_dev_status.py"""
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import dev_status as ds  # noqa: E402

GOOD = {"track": "LOOP-17", "title": "알려진 문제판", "status": "done", "size": "feature", "route": "server",
        "news": "문의 화면에 '지금 알려진 문제'가 생겼어요", "shipReply": "말씀하신 문제를 고친 업데이트가 나왔어요."}


def errors(*xs):
    return ds.check_all(list(enumerate(xs, 1)))


class DevStatusTest(unittest.TestCase):
    def test_good_line_passes(self):
        self.assertEqual(errors(GOOD), [])

    def test_tier_is_only_the_users(self):
        self.assertTrue(any("tier" in e for e in errors({**GOOD, "tier": "pro"})))
        self.assertEqual(errors({**GOOD, "tierAsk": "pro", "tierWhy": "새 기능이라 프로로 시작을 제안"}), [])

    def test_done_needs_ship_reply_and_news(self):
        e = errors({k: v for k, v in GOOD.items() if k not in ("news", "shipReply")})
        self.assertEqual(len(e), 2)

    def test_fix_from_an_inquiry_needs_its_repro_test(self):
        # [LOOP-21] 문의로 생긴 고침(d 번호 + size fix)은 재현 시험 이름이 있어야 done이 돼요. 추적 번호만인 일은 묻지 않아요.
        fix = {**GOOD, "id": "dAbCdEfGhIjKlMnO", "size": "fix"}
        del fix["track"]
        self.assertTrue(any("test" in e for e in errors(fix)))
        self.assertEqual(errors({**fix, "test": "AlarmLateTest.ringsOnTimeAfterDoze"}), [])
        self.assertEqual(errors({**fix, "test": "PC: 잠금 화면에서 7번 울림"}), [])
        self.assertEqual(errors({**GOOD, "size": "fix"}), [])

    def test_news_is_one_short_line(self):
        self.assertTrue(any("80자" in e for e in errors({**GOOD, "news": "가" * 81})))

    def test_personal_info_never_goes_in(self):
        for bad in ("someone@example.com 님이", "010-1234-5678로 연락", "qAbCdEfGhIjKlMnOpQrStU 문의"):
            self.assertTrue(any("서버에만" in e for e in errors({**GOOD, "title": bad})), bad)

    def test_route_needs_what_it_waits_for(self):
        self.assertTrue(errors({**GOOD, "route": "remote"}))
        self.assertEqual(errors({**GOOD, "route": "remote", "remote": "cycles@12"}), [])
        self.assertTrue(errors({**GOOD, "route": "flag"}))

    def test_styles_are_work_types(self):
        self.assertEqual(errors({**GOOD, "styles": ["SHIFT", "DAY"]}), [])
        self.assertTrue(errors({**GOOD, "styles": ["교대"]}))

    def test_duplicates_and_unknown_keys(self):
        self.assertTrue(any("겹쳐요" in e for e in errors(GOOD, GOOD)))
        self.assertTrue(any("모르는 칸" in e for e in errors({**GOOD, "body": "문의 원문"})))

    def test_built_lists_done_server_lines(self):
        items = list(enumerate([GOOD, {**GOOD, "track": "LOOP-18", "route": "app"}, {"track": "LOOP-19", "status": "doing", "route": "server"}], 1))
        self.assertIn('["LOOP-17"]', ds.built_text(items))

    def test_payload_shape(self):
        body = ds.payload(list(enumerate([GOOD], 1)), "1.39.0", "abcdef1", 300)
        self.assertEqual(body["version"], "1.39.0")
        self.assertEqual(body["pr"], 300)
        self.assertEqual(body["items"][0]["track"], "LOOP-17")
        with self.assertRaises(SystemExit):
            ds.payload([], "1.39", "", 0)

    def test_read_reports_bad_json(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "s.jsonl"
            p.write_text(json.dumps(GOOD, ensure_ascii=False) + "\n{oops\n\n", encoding="utf-8")
            items, errs = ds.read(p)
            self.assertEqual(len(items), 1)
            self.assertEqual(len(errs), 1)

    def test_a_list_where_a_word_goes_is_an_error_not_a_crash(self):
        # 2.0.0 [RVR-10] (검토 TEST-15 다): status·size에 목록이 오면 TypeError로 죽었어요.
        self.assertTrue(any("status" in e for e in errors({**GOOD, "status": ["done"]})))
        self.assertTrue(any("size" in e for e in errors({**GOOD, "size": ["fix"]})))
        self.assertTrue(any("styles" in e for e in errors({**GOOD, "styles": [["SHIFT"]]})))

    def test_a_line_knows_its_release_from_the_tracker(self):
        # [RVR-10] (U60-05) 이 파일에는 판을 적지 않아요: 추적표 phase에서 읽고, 2.0.0으로 합쳐진 1.39.1·1.40은 2.0.0이에요.
        ph = {"A-1": ds.norm("1.39"), "B-1": ds.norm("1.39.1"), "C-1": ds.norm("1.40"), "D-1": ds.norm("2 (1.39.0)")}
        self.assertEqual({"A-1": "1.39.0", "B-1": "2.0.0", "C-1": "2.0.0", "D-1": "1.39.0"}, ph)
        self.assertIsNone(ds.version_of({"id": "dAbCdEfGhIjKlMnO"}, ph))

    def test_lines_of_a_release_that_went_out_and_still_wait_are_warned(self):
        # [RVR-10] (TEST-15) 나간 판의 줄이 check로 남으면 출시 공지·출시 답이 안 나가요. 경고로만(실패는 아니에요).
        ph = {"OLD-1": "1.39.0", "NEW-1": "2.0.0"}
        items = list(enumerate([{**GOOD, "track": "OLD-1", "status": "check", "check": "PC"}, {**GOOD, "track": "OLD-1"},
                                {**GOOD, "track": "NEW-1", "status": "check", "check": "PC"}], 1))
        warns = ds.stale(items, ph, "2.0.0")
        self.assertEqual(1, len(warns))
        self.assertIn("1줄 OLD-1: 나간 판 1.39.0", warns[0])
        self.assertEqual([], ds.stale(items, ph, None))

    def test_notes_and_pc_list_only_the_release_asked_for(self):
        import contextlib
        import io
        keep = ds.STATUS_FILE, ds.TRACKER
        with tempfile.TemporaryDirectory() as d:
            s, tr = pathlib.Path(d) / "s.jsonl", pathlib.Path(d) / "t.csv"
            lines = [{**GOOD, "route": "app", "track": "OLD-1", "news": "옛 판 소식"}, {**GOOD, "route": "app", "track": "NEW-1", "news": "새 판 소식", "check": "새 판 확인"},
                     {**GOOD, "track": "NEW-2", "status": "check", "check": "새 판 PC"}]
            s.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in lines) + "\n", encoding="utf-8")
            tr.write_text("id,requirement,phase,status,gate,evidence\nOLD-1,x,1.39,완료,,\nNEW-1,x,1.40,완료,,\nNEW-2,x,2.0.0,진행 중,,\n", encoding="utf-8")
            ds.STATUS_FILE, ds.TRACKER = s, tr
            try:
                out = io.StringIO()
                with contextlib.redirect_stdout(out):
                    ds.main(["notes", "2.0.0"])
                self.assertEqual("- 새 판 소식\n", out.getvalue())
                out = io.StringIO()
                with contextlib.redirect_stdout(out):
                    ds.main(["pc", "1.39.0"])
                self.assertNotIn("새 판", out.getvalue())
            finally:
                ds.STATUS_FILE, ds.TRACKER = keep

    def test_repository_file_is_clean(self):
        items, errs = ds.read()
        self.assertEqual(errs + ds.check_all(items), [])
        self.assertEqual(ds.BUILT_FILE.read_text(encoding="utf-8"), ds.built_text(items))


if __name__ == "__main__":
    unittest.main()
