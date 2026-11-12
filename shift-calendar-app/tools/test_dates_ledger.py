#!/usr/bin/env python3
"""2.0.0 [RVR-38] tools/dates_ledger.py 시험: python3 tools/test_dates_ledger.py"""
import contextlib
import datetime as dt
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dates_ledger as L  # noqa: E402

KT = """object PolicyConsent {
    val HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 29)
    val REVISION_POSTED: LocalDate = LocalDate.of(2026, 11, 12)
}
object RateTable {
    val builtIn = listOf(Rates(effectiveFrom = LocalDate.of(2026, 1, 1)), Rates(effectiveFrom = LocalDate.of(2027, 1, 1)))
    private val builtInYears: IntRange = 2025..2027
}
"""


class LedgerTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / "Code.kt").write_text(KT, encoding="utf-8")
        (self.dir / "loop.js").write_text('readSetting(env, "loop_train", { start: "2026-11-23", days: 7 });', encoding="utf-8")

    def ledger(self, rows):
        p = self.dir / "dates.json"
        p.write_text(json.dumps({"dates": rows}, ensure_ascii=False), encoding="utf-8")
        return L.load(p, self.dir)

    def row(self, rid, **kw):
        return {"id": rid, "what": "무엇", "area": "앱", "before": "미리", **kw}

    def test_reads_dates_from_code(self):
        rows, problems = self.ledger([
            self.row("held", **{"from": {"file": "Code.kt", "re": r"val HELD_UNTIL: LocalDate = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)"}}),
            self.row("p30", **{"from": {"file": "Code.kt", "re": r"val REVISION_POSTED: LocalDate = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)", "plus_days": 30}}),
            self.row("rates", **{"from": {"file": "Code.kt", "re": r"effectiveFrom = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)", "pick": "last"}}),
            self.row("train", **{"from": {"file": "loop.js", "re": r'loop_train", \{ start: "(\d{4}-\d{2}-\d{2})"'}}),
            self.row("hol", **{"from": {"file": "Code.kt", "re": r"builtInYears: IntRange = \d{4}\.\.(\d{4})", "md": "12-31"}}),
            self.row("year-end", date="2026-12-20", source="사용자"),
        ])
        self.assertEqual(problems, [])
        got = {r["id"]: r["date"].isoformat() for r in rows}
        self.assertEqual(got, {"held": "2026-11-29", "p30": "2026-12-12", "rates": "2027-01-01", "train": "2026-11-23",
                               "hol": "2027-12-31", "year-end": "2026-12-20"})
        self.assertEqual([r["id"] for r in rows][:2], ["train", "held"], "날짜 순")

    def test_code_moves_ledger_follows(self):
        """코드가 날을 옮기면 장부는 그대로 따라가요(두 곳을 맞출 일이 없음)."""
        spec = [self.row("held", **{"from": {"file": "Code.kt", "re": r"val HELD_UNTIL: LocalDate = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)"}})]
        (self.dir / "Code.kt").write_text(KT.replace("LocalDate.of(2026, 11, 29)", "LocalDate.of(2026, 12, 3)"), encoding="utf-8")
        rows, problems = self.ledger(spec)
        self.assertEqual((problems, rows[0]["date"]), ([], dt.date(2026, 12, 3)))

    def test_problems(self):
        rows, problems = self.ledger([
            self.row("gone", **{"from": {"file": "Code.kt", "re": r"val GONE: LocalDate = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)"}}),
            self.row("two", **{"from": {"file": "Code.kt", "re": r"effectiveFrom = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)"}}),
            self.row("nofile", **{"from": {"file": "No.kt", "re": "x"}}),
            self.row("both", date="2026-12-20", source="s", **{"from": {"file": "Code.kt", "re": "x"}}),
            self.row("nosource", date="2026-12-20"),
            self.row("twice", date="2026-12-20", source="s"),
            self.row("twice", date="2026-12-21", source="s"),
            {"id": "empty", "date": "2026-12-20", "source": "s"},
        ])
        text = "\n".join(problems)
        for want in ("gone: ", "못 찾았어요", "two: ", "2개예요", "nofile: 파일이 없어요", "both: from과 date", "nosource: ", "두 번이에요: 'twice'", "empty: what·area·before이 비었어요"):
            self.assertIn(want, text)
        self.assertEqual([r["id"] for r in rows], ["twice"])

    def test_gaps_warn_before_next_year_is_empty(self):
        rows, _ = self.ledger([
            self.row("rates", **{"from": {"file": "Code.kt", "re": r"effectiveFrom = LocalDate\.of\((\d{4}), (\d{1,2}), (\d{1,2})\)", "pick": "last"}},
                     next={"plus_years": 1, "what": "그해 요율 없음", "warn_days": 120}),
            self.row("hol", **{"from": {"file": "Code.kt", "re": r"builtInYears: IntRange = \d{4}\.\.(\d{4})", "md": "12-31"}},
                     next={"what": "공휴일 표 끝", "warn_days": 180}),
        ])
        self.assertEqual(L.gaps(rows, dt.date(2026, 11, 12)), [])
        g = L.gaps(rows, dt.date(2027, 9, 10))
        self.assertEqual([(x["id"], x["date"].isoformat(), x["left"]) for x in g], [("rates", "2028-01-01", 113), ("hol", "2028-01-01", 113)])

    def test_shifts_add_turn_on_days(self):
        rows, _ = self.ledger([self.row("held", date="2026-11-29", source="s"), self.row("far", date="2027-05-01", source="s")])
        self.assertEqual(L.shifts(rows, dt.date(2026, 11, 22), 10, [0, 30]), [0, 7, 8, 30])
        self.assertEqual(L.shifts(rows, dt.date(2026, 11, 12), 10, [0, 30]), [0, 30])

    def test_real_ledger_is_clean(self):
        rows, problems = L.load()
        self.assertEqual(problems, [])
        self.assertGreaterEqual(len(rows), 10)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(L.main(["json", "--today", "2026-11-12"]), 0)
        j = json.loads(out.getvalue())
        self.assertIn("policy-3.13-p30", [r["id"] for r in j["upcoming"]])
        self.assertTrue(all(r["left"] >= -7 for r in j["upcoming"]))
        # 서버 사본(dates-built.js, 갈래 Q2 카드)이 장부·코드와 같아요.
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(L.main(["check"]), 0, "python3 tools/dates_ledger.py build")

    def test_built_module_keeps_gap(self):
        rows, _ = self.ledger([self.row("rates", date="2027-01-01", source="s", next={"plus_years": 1, "what": "그해 요율 없음", "warn_days": 120})])
        text = L.built_text(rows)
        self.assertTrue(text.rstrip().endswith("];"))
        self.assertIn('"gap": {"from": "2028-01-01", "warnDays": 120, "what": "그해 요율 없음"}', text)


if __name__ == "__main__":
    unittest.main()
