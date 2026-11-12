#!/usr/bin/env python3
"""2.0.0 [RVR-19] (검토 TEST-20 ②) The size budgets: python3 tools/test_app_size.py (files made in a temporary folder)."""
import contextlib
import io
import os
import pathlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import app_size  # noqa: E402


class AppSizeTest(unittest.TestCase):
    def test_the_watch_has_its_own_budget(self):
        self.assertEqual(app_size.WATCH_BUDGET, app_size.budget_of("wear/build/outputs/bundle/release/wear-release.aab"))
        self.assertEqual(app_size.WATCH_BUDGET, app_size.budget_of("bamnat-wear-1.39.0-100053.aab"))
        self.assertEqual(app_size.BUDGETS[".aab"], app_size.budget_of("app/build/outputs/bundle/release/app-release.aab"))
        self.assertEqual(app_size.BUDGETS[".apk"], app_size.budget_of("app-release-unsigned.apk"))
        self.assertLess(app_size.WATCH_BUDGET, app_size.BUDGETS[".aab"] / 2)

    def test_a_watch_bundle_over_its_budget_fails(self):
        with tempfile.TemporaryDirectory() as d:
            f = os.path.join(d, "wear-release.aab")
            with zipfile.ZipFile(f, "w", zipfile.ZIP_STORED) as z:
                z.writestr("base/dex/classes.dex", os.urandom(app_size.WATCH_BUDGET + 10_000))
                z.writestr("BUNDLE-METADATA/map.txt", b"x" * 100)
            out = io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
                code = app_size.main([f])
            self.assertEqual(1, code)
            self.assertIn("over budget", out.getvalue())


if __name__ == "__main__":
    unittest.main()
