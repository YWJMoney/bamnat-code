#!/usr/bin/env python3
"""2.0.0 [RVL-55] (검토 DATA-07·I12a-04) 출시판 멈춤 기록 풀기 시험: python3 tools/test_retrace.py"""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import retrace as rt  # noqa: E402

# The shape R8 writes (mapping 2.x): a class line, its source file, fields, ranged methods, an inline chain.
MAPPING = """# compiler: R8
# pg_map_id: 1a2b3c
com.bamnat.app.ui.pay.PayViewModel -> a.b.c:
# {"id":"sourceFile","fileName":"PayViewModel.kt"}
    java.lang.String name -> a
    1:3:void <init>():10:12 -> <init>
    4:4:int com.bamnat.core.pay.PayCalc.hours(int):30:30 -> a
    4:4:void save():212 -> a
    5:9:void save():213:217 -> a
    void load() -> b
com.bamnat.app.ui.pay.PayScreen -> d.e:
    1:1:void click():40:40 -> b
"""

REPORT = """app 2.0.0 (54)
android 36 · Google Pixel 9
java.lang.NullPointerException
  at java.util.Objects.requireNonNull(Objects.java:207)
  at a.b.c.a(SourceFile:4)
  at a.b.c.a(SourceFile:7)
  at d.e.b(SourceFile:1)
sig=NullPointerException@a.b.c.a:7<d.e.b:1 memory 120/512MB"""


class RetraceTest(unittest.TestCase):
    def test_renamed_lines_come_back_and_the_rest_stays(self):
        out = rt.retrace(MAPPING, REPORT).splitlines()
        self.assertEqual("app 2.0.0 (54)", out[0])
        self.assertEqual("  at java.util.Objects.requireNonNull(Objects.java:207)", out[3])
        # An inlined line is its chain, innermost first, one "at" line each.
        self.assertEqual("  at com.bamnat.core.pay.PayCalc.hours:30", out[4])
        self.assertEqual("  at com.bamnat.app.ui.pay.PayViewModel.save:212", out[5])
        # A ranged line keeps its place in the range.
        self.assertEqual("  at com.bamnat.app.ui.pay.PayViewModel.save:215", out[6])
        self.assertEqual("  at com.bamnat.app.ui.pay.PayScreen.click:40", out[7])
        # The stop's name (CrashLog.signature) too.
        self.assertIn("NullPointerException@com.bamnat.app.ui.pay.PayViewModel.save:215<com.bamnat.app.ui.pay.PayScreen.click:40", out[8])
        self.assertTrue(out[8].endswith("memory 120/512MB"))

    def test_a_stall_note_and_an_anr_line(self):
        self.assertEqual("  at com.bamnat.app.ui.pay.PayViewModel.save:213", rt.retrace(MAPPING, "  at a.b.c.a:5"))
        # No line (ANR traces cut at '('): the method by name.
        self.assertEqual("    at com.bamnat.app.ui.pay.PayViewModel.load", rt.retrace(MAPPING, "    at a.b.c.b"))

    def test_names_the_mapping_does_not_know_are_left(self):
        for line in ("  at android.os.Looper.loop(Looper.java:1)", "  at x.y.z(SourceFile:3)", "11-12 22:04 · 3.1초 · 알 수 없음"):
            self.assertEqual(line, rt.retrace(MAPPING, line))


class CheckTest(unittest.TestCase):
    def test_the_release_self_check_wants_an_id_and_renamed_classes(self):
        import contextlib
        import io
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, rt.check(MAPPING))
            self.assertEqual(1, rt.check(MAPPING.replace("# pg_map_id: 1a2b3c\n", "")))
            self.assertEqual(1, rt.check("# pg_map_id: x\ncom.bamnat.app.Keep -> com.bamnat.app.Keep:\n"))


if __name__ == "__main__":
    unittest.main()
