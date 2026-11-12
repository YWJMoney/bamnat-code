#!/usr/bin/env python3
"""2.0.0 [RVR-21] tools/market_works.py와 tools/old_core_check.py의 판단 부분 시험(옛 core 빌드는 old_core_check.py 자체가 해요)."""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import base64  # noqa: E402
import market_shots  # noqa: E402
import market_works  # noqa: E402
import old_core_check  # noqa: E402


def work(title, **extra):
    return {"recipe": {"format": "bamnat-recipe", "v": 1, "title": title, "styles": ["SHIFT"], "pieces": []}, **extra}


class MarketWorksTest(unittest.TestCase):
    def folder(self, files):
        d = tempfile.mkdtemp()
        for name, data in files.items():
            with open(os.path.join(d, name), "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False)
        return d

    def test_keys_edge_hidden_and_replaced(self):
        d = self.folder({
            "market-136.json": [work("하나"), work("둘"), work("셋")],
            "market-138-refresh.json": [work("둘", replace=True)],
            "market-138-refresh-plan.json": {"hide": [{"title": "하나"}]},
            "market-139.json": [work("넷", minApp=53)],
            "market-139-refresh-plan.json": {"hide": [{"title": "셋", "from": "136", "belowApp": 53}, {"title": "둘", "from": "138-refresh", "belowApp": 53}]},
            "edge-200.json": [work("시험 E1 긴 이름"), work("시험 E12 아주 작은 글자")],
            "starter-recipes.json": [work("처음")],
        })
        ws = {w["key"]: w for w in market_works.load(d)}
        self.assertEqual(sorted(ws), sorted(["market-136#1", "market-136#2", "market-136#3", "market-138-refresh#1", "market-139#1",
                                            "edge:E1", "edge:E12", "starter-recipes#1"]))
        self.assertTrue(ws["edge:E12"]["edge"])
        self.assertFalse(ws["market-136#1"]["edge"])
        # A plan line without from hides the title in the market files older than the plan; with from, only in that file.
        self.assertEqual(ws["market-136#1"]["hiddenFrom"], 0)
        self.assertEqual(ws["market-136#3"]["hiddenFrom"], 53)
        self.assertEqual(ws["market-138-refresh#1"]["hiddenFrom"], 53)
        self.assertIsNone(ws["market-136#2"]["hiddenFrom"], "the plan said 138-refresh, not 136")
        # The refresh file's work is the same code with a new body: the old body is no longer served.
        self.assertEqual(ws["market-136#2"]["replacedBy"], "market-138-refresh#1")
        self.assertIsNone(ws["market-138-refresh#1"]["replacedBy"])

        shown = lambda app: sorted(k for k, w in ws.items() if market_works.shown_to(w, app))
        self.assertEqual(shown(52), ["edge:E1", "edge:E12", "market-136#3", "market-138-refresh#1", "starter-recipes#1"])
        self.assertEqual(shown(53), ["edge:E1", "edge:E12", "market-139#1", "starter-recipes#1"])

    def test_two_works_with_one_name_fail(self):
        d = self.folder({"edge-200.json": [work("시험 E1 하나"), work("시험 E1 둘")]})
        with self.assertRaisesRegex(ValueError, "edge:E1"):
            market_works.load(d)

    def test_a_file_that_is_not_works_fails(self):
        d = self.folder({"market-140.json": [{"title": "recipe 없이"}]})
        with self.assertRaisesRegex(ValueError, "recipe가 없어요"):
            market_works.load(d)


class OldCoreCheckTest(unittest.TestCase):
    served = {"apps": [52, 53], "works": [
        {"key": "a#1", "minApp": None, "partsMinApp": None, "text": "{A}", "hash": "ha", "apps": {"52": {"listed": True, "hash": "ha"}, "53": {"listed": True, "hash": "ha"}}},
        {"key": "a#2", "minApp": 53, "partsMinApp": 53, "text": "{B}", "hash": "hb", "apps": {"52": {"listed": False, "body": "{B'}", "hash": "hb2"}, "53": {"listed": True, "hash": "hb"}}},
        {"key": "a#3", "minApp": None, "partsMinApp": None, "text": "{C}", "hash": "hc", "apps": {"52": {"listed": True, "hash": "hc"}, "53": {"listed": True, "hash": "hc"}}},
        {"key": "a#4", "refused": "json"},
    ]}
    works = [{"key": "a#1", "title": "하나", "minApp": None, "hiddenFrom": None, "replacedBy": None},
             {"key": "a#2", "title": "둘", "minApp": None, "hiddenFrom": None, "replacedBy": None},
             {"key": "a#3", "title": "셋", "minApp": None, "hiddenFrom": 0, "replacedBy": None},
             {"key": "a#4", "title": "넷", "minApp": None, "hiddenFrom": None, "replacedBy": None}]

    def test_probe_lines_take_the_body_the_app_receives(self):
        lines = old_core_check.probe_lines(self.served, 52)
        self.assertEqual(lines, [("a#1", "ha", "{A}"), ("a#2", "hb2", "{B'}"), ("a#3", "hc", "{C}")])
        text = old_core_check.probe_text(lines)
        self.assertEqual(len(text.splitlines()), 3)
        self.assertEqual(text.splitlines()[0].split("\t")[:2], ["a#1", "ha"])

    def test_judge(self):
        res = old_core_check.parse_results("a#1\tok\na#2\tok\na#3\tread:pick at pieces.0\n")
        r = old_core_check.judge(self.works, self.served, 52, res)
        self.assertEqual((r["listed"], r["opened"]), (2, 1))
        self.assertEqual(r["broken"], [], "a#3 is hidden from every app: only noted")
        self.assertEqual([x["key"] for x in r["brokenHidden"]], ["a#3"])
        self.assertEqual(r["tooStrict"], ["a#2"], "the table kept a#2 from 52, yet 52 reads it")
        self.assertEqual([x["key"] for x in r["refused"]], ["a#4"])
        self.assertTrue(old_core_check.failed([r]), "a work the server refuses fails")

        res = old_core_check.parse_results("a#1\thash\na#2\tok\na#3\tok\n")
        r = old_core_check.judge(self.works[:3], {"works": self.served["works"][:3]}, 53, res)
        self.assertEqual([(x["key"], x["why"]) for x in r["broken"]], [("a#1", "hash")])
        self.assertTrue(old_core_check.failed([r]))
        md = old_core_check.markdown([r], {53: "1.39.0"})
        self.assertIn("1.39.0에서 안 열림** `a#1`", md)

        r = old_core_check.judge(self.works[:3], {"works": self.served["works"][:3]}, 53, old_core_check.parse_results("a#1\tok\na#2\tok\n"))
        self.assertEqual(r["missing"], ["a#3"])
        self.assertTrue(old_core_check.failed([r]), "a work the probe never answered fails")

    def test_release_commits_are_whole(self):
        rels = old_core_check.releases()
        self.assertEqual([r["versionCode"] for r in rels], sorted(r["versionCode"] for r in rels))
        self.assertGreaterEqual(len(rels), 4)


class MarketShotsTest(unittest.TestCase):
    @staticmethod
    def fp(v):
        return base64.b64encode(bytes([v] * 12)).decode()

    def test_only_big_changes_are_named_and_only_errors_fail(self):
        prev = {"all": True, "works": {"a#1": {"title": "하나", "fp": self.fp(100)}, "a#2": {"title": "둘", "fp": self.fp(100)},
                                       "a#3": {"title": "셋", "fp": self.fp(100)}}}
        cur = {"all": True, "drift": [], "works": {"a#1": {"title": "하나", "fp": self.fp(103)}, "a#2": {"title": "둘", "fp": self.fp(140)},
                                                   "a#4": {"title": "넷", "fp": self.fp(1)}}}
        res = market_shots.compare(prev, cur, ["a#1", "a#2", "a#4"])
        self.assertEqual([(k, round(d)) for k, _, d in res["changed"]], [("a#2", 40)], "3 is under the threshold")
        self.assertEqual(res["new"], [("a#4", "넷")])
        self.assertEqual(res["gone"], [("a#3", "셋")])
        self.assertFalse(market_shots.failed(res), "changes are only told")
        self.assertIn("| `a#2` | 둘 | 40.0 |", market_shots.markdown(res, True))

        cur["works"]["a#4"] = {"title": "넷", "error": "RecipeException: pick"}
        res = market_shots.compare(prev, cur, ["a#1", "a#2", "a#4"])
        self.assertTrue(market_shots.failed(res))
        self.assertIn("**넘어짐** `a#4`", market_shots.markdown(res, True))

    def test_a_calendar_work_that_changed_nothing_is_told_and_drift_fails(self):
        works = [{"key": "a#1", "recipe": {"pieces": [{"kind": "parts"}]}}, {"key": "a#2", "recipe": {"pieces": [{"kind": "alarms"}]}},
                 {"key": "a#3", "recipe": {"pieces": [{"kind": "calTheme"}, {"kind": "logic"}]}}]
        self.assertEqual(market_shots.visible_keys(works), {"a#1", "a#3"})
        cur = {"all": False, "drift": [], "works": {"a#1": {"title": "하나", "fp": self.fp(1), "px": 0},
                                                    "a#2": {"title": "둘", "fp": self.fp(1), "px": 0},
                                                    "a#3": {"title": "셋", "fp": self.fp(1), "px": 900}}}
        res = market_shots.compare(None, cur, visible=market_shots.visible_keys(works))
        self.assertEqual(res["unchanged"], [("a#1", "하나")], "an alarm work leaves the calendar as it was")
        self.assertFalse(market_shots.failed(res))
        self.assertIn("넣어도 달력이 그대로인 작품 1개", market_shots.markdown(res, False))
        cur["drift"] = ["a#3 셋 (40px)"]
        self.assertTrue(market_shots.failed(market_shots.compare(None, cur)))

    def test_names_must_match_the_works_list_when_all_were_taken(self):
        cur = {"all": True, "works": {"a#1": {"fp": self.fp(1)}, "edge:E1": {"fp": self.fp(1)}}}
        res = market_shots.compare(None, cur, ["a#1", "a#2"])
        self.assertTrue(market_shots.failed(res))
        self.assertEqual(len(res["keyProblems"]), 2)
        self.assertIn("기준", market_shots.markdown(res, False))
        # A sample (not all) is not held to the whole list.
        self.assertFalse(market_shots.failed(market_shots.compare(None, dict(cur, all=False), ["a#1", "a#2"])))

    def test_png_names_match_the_test(self):
        # MarketWorksShotsTest: key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
        self.assertEqual(market_shots.png_name("market-139#12"), "market-139_12.png")
        self.assertEqual(market_shots.png_name("edge:E1"), "edge_E1.png")


if __name__ == "__main__":
    unittest.main()
