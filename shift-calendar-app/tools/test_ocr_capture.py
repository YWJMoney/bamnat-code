#!/usr/bin/env python3
"""2.0.0 [RVL-68] (검토 SCAN-34) 실기기 판독 기록 → 시험 표본: python3 tools/test_ocr_capture.py"""
import contextlib
import io
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import ocr_capture as oc  # noqa: E402

LOG = """--------- beginning of main
11-12 10:00:00.000  4321  4400 D BamnatOcr: # read 0 of 2 (anchor)
11-12 10:00:00.001  4321  4400 D BamnatOcr: word\t10\t20\t40\t44\t0.0\t1\t1
11-12 10:00:00.001  4321  4400 D BamnatOcr: word\t50\t20\t80\t44\t0.0\tNOFF\tNOFF
11-12 10:00:00.002  4321  4400 D BamnatOcr: # read 1 of 2
11-12 10:00:00.003  4321  4400 D BamnatOcr: word\t11\t21\t41\t45\t1.5\t1\t1
D/BamnatOcr( 4321): # slip read 0 of 1
D/BamnatOcr( 4321): word\t1\t2\t3\t4\t0.50\t기본급
11-12 10:01:00.000  4321  4400 D BamnatOcr: # read 0 of 1
11-12 10:01:00.000  4321  4400 D BamnatOcr: word\t7\t8\t9\t10\t0.0\tD\tD
11-12 10:01:00.000  4321  4400 W BamnatOcr: read failed
"""


class CaptureTest(unittest.TestCase):
    def run_tool(self, *args, log=LOG, encoding="utf-8"):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d, "ocr.txt")
            p.write_bytes(log.encode(encoding) if encoding != "utf-16" else log.encode("utf-16"))
            out = pathlib.Path(d, "out")
            with contextlib.redirect_stdout(io.StringIO()):
                oc.main([str(p), *args, "--out", str(out)])
            return {f.name: f.read_text(encoding="utf-8") for f in sorted(out.iterdir())}

    def test_the_last_reading_of_a_made_up_roster_with_its_truth(self):
        files = self.run_tool("--fixture", "05-nurse-tilt5", "--build", "abc123")
        text = files["05-nurse-tilt5-device.tsv"]
        self.assertTrue(text.startswith("# 05-nurse-tilt5 as ML Kit read it on the emulator (abc123), 1 reads"))
        self.assertIn("truth\t", text)
        self.assertIn("# read 0 of 1\nword\t7\t8\t9\t10\t0.0\tD\n", text)
        self.assertNotIn("NOFF", text)

    def test_all_readings_and_powershell_utf16(self):
        files = self.run_tool("--fixture", "05-nurse-tilt5", "--all", encoding="utf-16")
        self.assertEqual(["05-nurse-tilt5-device-1.tsv", "05-nurse-tilt5-device-2.tsv"], list(files))
        first = files["05-nurse-tilt5-device-1.tsv"]
        self.assertIn("# read 0 of 2 (anchor)\nword\t10\t20\t40\t44\t0.0\t1\nword\t50\t20\t80\t44\t0.0\tNOFF\n# read 1 of 2\n", first)

    def test_a_slip_needs_made_up_and_keeps_its_own_reads(self):
        with self.assertRaises(SystemExit):
            self.run_tool("--slip", "s09")
        files = self.run_tool("--slip", "s09", "--made-up")
        self.assertIn("# slip read 0 of 1\nword\t1\t2\t3\t4\t0.50\t기본급\n", files["s09.tsv"])

    def test_only_fixture_photos(self):
        with self.assertRaises(SystemExit):
            self.run_tool("--fixture", "real-ward-photo")
        # A PC photo whose reads file has no truth yet (duty11) is not taken either.
        with self.assertRaises(SystemExit):
            self.run_tool("--fixture", "duty11")

    def test_a_pc_photo_takes_the_truth_of_its_reads_file(self):
        files = self.run_tool("--fixture", "roster1", "--build", "abc123")
        text = files["roster1-device.tsv"]
        self.assertIn("truth\t김간호\tD,D,N,N,O,O", text)
        self.assertEqual(4, text.count("\ntruth\t"))


if __name__ == "__main__":
    unittest.main()
