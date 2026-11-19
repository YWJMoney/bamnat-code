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

    def run_on(self, name, size):
        with tempfile.TemporaryDirectory() as d:
            f = os.path.join(d, name)
            with zipfile.ZipFile(f, "w", zipfile.ZIP_STORED) as z:
                z.writestr("base/dex/classes.dex", os.urandom(size))
                z.writestr("BUNDLE-METADATA/map.txt", b"x" * 100)
            out = io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
                code = app_size.main([f])
            return code, out.getvalue()

    def test_a_watch_bundle_over_its_budget_by_a_tenth_fails(self):
        code, said = self.run_on("wear-release.aab", int(app_size.WATCH_BUDGET * 1.2))
        self.assertEqual(1, code)
        self.assertIn("over budget by more than 10 %", said)

    def test_a_little_over_the_budget_is_a_warning_and_the_files_still_go(self):
        """2.0.0 [RVL-60] (검토 DATA-21) The release build stopped at 'Size' before its upload; now a warning, the build goes on."""
        code, said = self.run_on("wear-release.aab", app_size.WATCH_BUDGET + 10_000)
        self.assertEqual(0, code)
        self.assertIn("over budget (warning)", said)
        self.assertEqual(0, self.run_on("app-release.aab", app_size.BUDGETS[".aab"] - 10_000)[0])

    def test_dex_code_is_added_up_by_the_original_package(self):
        """2.0.0 [RVL-60] A one-class dex, made here: its method's code item (16 + 3 units × 2 + 1 try × 8 = 30 bytes) counts."""
        dex = make_dex("La/b;", insns=3, tries=1)
        self.assertEqual({"a.b": 30}, app_size.dex_code(dex))
        names = app_size.renamed("# compiler: R8\ncom.bamnat.app.ui.pay.PayViewModel -> a.b:\n    void save() -> a\n")
        self.assertEqual({"a.b": "com.bamnat.app.ui.pay.PayViewModel"}, names)
        with tempfile.TemporaryDirectory() as d:
            f = os.path.join(d, "app-release-unsigned.apk")
            with zipfile.ZipFile(f, "w") as z:
                z.writestr("classes.dex", dex)
                z.writestr("classes2.dex", make_dex("Lkotlinx/coroutines/Job;", insns=10, tries=0))
            self.assertEqual([("kotlinx.coroutines", 36), ("com.bamnat.app.ui.pay", 30)], app_size.by_package(f, names))

    def test_packages_are_ours_by_area_and_others_by_library(self):
        self.assertEqual("com.bamnat.app.ui.money", app_size.package("com.bamnat.app.ui.money.MoneyScreen"))
        self.assertEqual("com.bamnat.core.pay", app_size.package("com.bamnat.core.pay.PayCalc"))
        self.assertEqual("com.google.android.gms.ads", app_size.package("com.google.android.gms.ads.AdView"))
        self.assertEqual("androidx.compose", app_size.package("androidx.compose.ui.Modifier"))
        self.assertEqual("(no package)", app_size.package("R8$Lambda"))


def uleb(n):
    out = bytearray()
    while True:
        x = n & 0x7F
        n >>= 7
        out.append(x | (0x80 if n else 0))
        if not n:
            return bytes(out)


def make_dex(descriptor, insns, tries):
    """The least of a dex file app_size reads: one string, one type, one class with one direct method and its code item."""
    b = bytearray(0x200)
    put = lambda o, v, n=4: b.__setitem__(slice(o, o + n), v.to_bytes(n, "little"))
    put(0x38, 1); put(0x3C, 0x70); put(0x40, 1); put(0x44, 0x74); put(0x60, 1); put(0x64, 0x78)
    put(0x70, 0x100)  # string_ids[0] -> string data
    put(0x74, 0)  # type_ids[0] -> string 0
    put(0x78, 0)  # class_def: class_idx 0 ...
    put(0x78 + 24, 0x140)  # ... class_data_off
    data = uleb(len(descriptor)) + descriptor.encode() + b"\0"
    b[0x100:0x100 + len(data)] = data
    code = 0x180
    put(code + 6, tries, 2)
    put(code + 12, insns)
    cd = uleb(0) + uleb(0) + uleb(1) + uleb(0) + uleb(0) + uleb(1) + uleb(code)
    b[0x140:0x140 + len(cd)] = cd
    return bytes(b)


if __name__ == "__main__":
    unittest.main()
