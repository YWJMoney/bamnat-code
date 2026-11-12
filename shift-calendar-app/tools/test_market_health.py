#!/usr/bin/env python3
"""2.0.0 [RVA-56] tools/market_health.py: 작품 사진 대조와 옛 앱 열기를 관리자 앱이 읽는 한 장으로 묶는지."""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import market_health  # noqa: E402


SHOTS = {"count": 3, "changed": [["a#1", "하나", 12.34]], "new": [], "gone": [], "errors": [["a#2", "둘", "넣다 넘어짐"]],
         "drift": ["a#3 셋 (40px)"], "keyProblems": [], "unchanged": [["a#4", "넷"]]}
OLD = {"releases": [], "reports": [
    {"app": 53, "listed": 3, "opened": 2, "broken": [{"key": "a#5", "title": "다섯", "minApp": 50, "why": "unknown piece"}],
     "brokenHidden": [{"key": "h#1", "title": "내린 것", "why": "x"}], "tooStrict": [], "refused": [], "missing": []},
    {"app": 52, "listed": 4, "opened": 4, "broken": [], "brokenHidden": [], "tooStrict": [],
     "refused": [{"key": "a#6", "title": "여섯", "why": "too big"}], "missing": []}]}


class MarketHealthTest(unittest.TestCase):
    def test_both_tools_make_one_report(self):
        r = market_health.build_report(SHOTS, OLD, "abc123", at="2026-11-12T00:00:00Z")
        self.assertEqual((r["format"], r["v"], r["build"], r["works"], r["listed"], r["opened"]),
                         ("bamnat-market-health", 1, "abc123", 3, 7, 6))
        kinds = {(b["key"], b["kind"]) for b in r["broken"]}
        self.assertEqual(kinds, {("a#2", "error"), ("a#3", "drift"), ("a#5", "old"), ("a#6", "refused")})
        self.assertEqual(next(b for b in r["broken"] if b["kind"] == "drift")["why"], "셋 (40px)")
        old = next(b for b in r["broken"] if b["kind"] == "old")
        self.assertEqual(old["app"], 53)
        # Works the shop already took down are not broken lines.
        self.assertNotIn("h#1", {b["key"] for b in r["broken"]})
        self.assertEqual(r["changed"], [{"key": "a#1", "title": "하나", "d": 12.3}])
        self.assertEqual(r["unchanged"], [{"key": "a#4", "title": "넷"}])

    def test_either_may_be_missing_and_lines_are_short(self):
        r = market_health.build_report(None, {"reports": [{"app": 53, "broken": [{"key": "k", "why": "x" * 999}]}]}, "b")
        self.assertEqual(r["works"], 0)
        self.assertEqual(len(r["broken"][0]["why"]), 160)
        same = market_health.build_report({"count": 1, "errors": [["k", "", "a"], ["k", "", "b"]]}, None, "b")
        self.assertEqual(len(same["broken"]), 1)

    def test_main_writes_the_file(self):
        d = tempfile.mkdtemp()
        shots, out = os.path.join(d, "s.json"), os.path.join(d, "out", "h.json")
        with open(shots, "w", encoding="utf-8") as f:
            json.dump(SHOTS, f)
        self.assertEqual(market_health.main(["--shots", shots, "--old", os.path.join(d, "none.json"), "--build", "x", "--out", out]), 0)
        with open(out, encoding="utf-8") as f:
            self.assertEqual(json.load(f)["works"], 3)
        # Nothing to bundle: no file, still passes (the CI step must not fail the build).
        self.assertEqual(market_health.main(["--build", "x", "--out", os.path.join(d, "none", "h.json")]), 0)
        self.assertFalse(os.path.exists(os.path.join(d, "none", "h.json")))


if __name__ == "__main__":
    unittest.main()
