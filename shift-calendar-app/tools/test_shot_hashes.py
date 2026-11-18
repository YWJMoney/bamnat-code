#!/usr/bin/env python3
"""2.0.0 [RVR-11] (검토 TEST-12 ②③·U60-04) 화면 사진 비교 도구 시험: python3 tools/test_shot_hashes.py

시험 소스·사진·JUnit 결과는 모두 임시 폴더에 만들어요. 저장소의 골든 파일은 읽기만 해요(이름 검사).
"""
import contextlib
import io
import json
import os
import pathlib
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import shot_hashes as sh  # noqa: E402
from PIL import Image  # noqa: E402

SRC = '''package com.bamnat.app.screenshots

class {cls} : ScreenshotBase() {{
    @Test fun a() = shoot("{a}") {{ }}
    @Test fun b() = shoot("{b}", PHONE) {{ }}
}}
'''


class ShotHashesTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        self.keep = sh.GOLDEN, sh.TESTS
        sh.TESTS = str(self.dir / "java")
        sh.GOLDEN = str(self.dir / "shots.json")
        self.shots = self.dir / "shots"
        self.results = self.dir / "results"
        for d in (self.shots, self.results):
            d.mkdir()

    def tearDown(self):
        sh.GOLDEN, sh.TESTS = self.keep
        shutil.rmtree(self.dir)

    def source(self, cls, a, b):
        f = pathlib.Path(sh.TESTS) / "com" / "bamnat" / "app" / "screenshots" / f"{cls}.kt"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(SRC.format(cls=cls, a=a, b=b), encoding="utf-8")

    def picture(self, name, shade):
        Image.new("L", (64, 96), shade).save(self.shots / f"{name}.png")

    def golden(self, **names):
        pathlib.Path(sh.GOLDEN).write_text(json.dumps({k: bytes([v]).hex() * (sh.SIZE[0] * sh.SIZE[1]) for k, v in names.items()}), encoding="utf-8")

    def ran(self, *classes):
        for c in classes:
            (self.results / f"TEST-com.bamnat.app.screenshots.{c}.xml").write_text("<testsuite/>", encoding="utf-8")

    def check(self, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = sh.main(["check", str(self.shots), *extra])
        return code, out.getvalue()

    def test_the_names_read_from_the_test_sources(self):
        self.source("OneScreenshotTest", "1_one", "2_two")
        names = sh.shot_names()
        self.assertEqual({"com.bamnat.app.screenshots.OneScreenshotTest"}, sh.classes_of(names, "1_one"))
        self.assertEqual([], sh.source_problems(names, ["1_one", "2_two"]))

    def test_a_key_shot_no_test_takes_any_more_is_named(self):
        self.source("OneScreenshotTest", "1_one_renamed", "2_two")
        bad = sh.source_problems(sh.shot_names(), ["1_one", "2_two"])
        self.assertEqual(1, len(bad))
        self.assertIn("'1_one'", bad[0])

    def test_a_name_two_tests_take_is_named(self):
        self.source("OneScreenshotTest", "1_one", "2_two")
        self.source("TwoScreenshotTest", "1_one", "3_three")
        bad = sh.source_problems(sh.shot_names(), ["2_two"])
        self.assertEqual(1, len(bad))
        self.assertIn("OneScreenshotTest.kt, TwoScreenshotTest.kt", bad[0])

    def test_a_shot_its_test_ran_for_but_did_not_draw_fails(self):
        self.source("OneScreenshotTest", "1_one", "2_two")
        self.golden(**{"1_one": 200, "2_two": 200})
        self.picture("1_one", 200)
        self.ran("OneScreenshotTest")
        code, out = self.check("--results", str(self.results))
        self.assertEqual(1, code, out)
        self.assertIn("2_two | **비교 못 함**", out)

    def test_the_other_shards_shots_are_counted_not_failed(self):
        self.source("OneScreenshotTest", "1_one", "2_two")
        self.source("TwoScreenshotTest", "3_three", "4_four")
        self.golden(**{"1_one": 200, "3_three": 200})
        self.picture("1_one", 200)
        self.ran("OneScreenshotTest")
        code, out = self.check("--results", str(self.results))
        self.assertEqual(0, code, out)
        self.assertIn("1 key shots belong to the other shard", out)

    def test_a_changed_picture_fails_and_a_same_one_passes(self):
        self.source("OneScreenshotTest", "1_one", "2_two")
        self.golden(**{"1_one": 200, "2_two": 200})
        self.picture("1_one", 200)
        self.picture("2_two", 120)
        code, out = self.check()
        self.assertEqual(1, code)
        self.assertIn("changed: 2_two", out)
        self.assertNotIn("changed: 1_one", out)

    def scene(self, folder, name, card=(30, 30), colour=(250, 248, 240), letter=False, fill=(40, 40, 60)):
        """A light screen with one dark card; letter adds a 2x3 dot (a changed letter)."""
        from PIL import ImageDraw
        folder.mkdir(exist_ok=True)
        im = Image.new("RGB", (320, 480), colour)
        d = ImageDraw.Draw(im)
        d.rectangle([card[0], card[1], card[0] + 200, card[1] + 120], fill=fill)
        if letter:
            d.rectangle([250, 400, 251, 402], fill=(0, 0, 0))
        im.save(folder / f"{name}.png")

    def test_yesterday_report_names_every_changed_shot_and_its_kind(self):
        """[RVR-39] (TEST-12 ①·I60b-03) one changed letter is caught; a moved card is 배치, a new background 색."""
        old, new = self.dir / "old", self.dir / "new"
        for f in (old, new):
            self.scene(f, "same")
        self.scene(old, "letter"); self.scene(new, "letter", letter=True)
        self.scene(old, "moved"); self.scene(new, "moved", card=(80, 200))
        self.scene(old, "colour"); self.scene(new, "colour", fill=(220, 60, 50))
        self.scene(old, "dark"); self.scene(new, "dark", colour=(20, 22, 30), fill=(230, 230, 240))
        self.scene(old, "gone"); self.scene(new, "added")
        a, b = sh.prints(str(old)), sh.prints(str(new))
        changed, added, gone, same = sh.compare_prints(a, b)
        kinds = {n: k for n, g, c, k in changed}
        self.assertEqual(kinds, {"letter": "작은 바뀜", "moved": "배치", "colour": "색", "dark": "색"})
        self.assertEqual((added, gone, same), (["added"], ["gone"], 1))
        self.assertEqual(changed[-1][0], "letter", "biggest first")
        (self.dir / "a.json").write_text(json.dumps(a)); (self.dir / "b.json").write_text(json.dumps(b))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = sh.main(["diff", str(self.dir / "a.json"), str(self.dir / "b.json"), "--shots", str(new), "--sheet", str(self.dir / "sheet.png")])
        self.assertEqual(0, code)
        self.assertIn("어제와 다른 화면 사진: 4장 (같음 1, 새로 1, 없어짐 1)", out.getvalue())
        self.assertIn("| letter | 작은 바뀜 |", out.getvalue())
        self.assertEqual(Image.open(self.dir / "sheet.png").size[1], 4 * (180 + 24) + 10)

    def test_prints_command_writes_every_shot(self):
        self.scene(self.shots, "1_one"); self.scene(self.shots, "2_two", letter=True)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, sh.main(["prints", str(self.shots), "--out", str(self.dir / "p" / "prints.json")]))
        p = json.loads((self.dir / "p" / "prints.json").read_text())
        self.assertEqual(sorted(p), ["1_one", "2_two"])
        self.assertNotEqual(p["1_one"]["px"], p["2_two"]["px"])
        self.assertEqual(len(p["1_one"]["g"]), 2 * sh.SIZE[0] * sh.SIZE[1])

    def test_the_repository_key_shots_are_each_taken_by_one_test(self):
        sh.GOLDEN, sh.TESTS = self.keep
        self.assertEqual([], sh.source_problems(sh.shot_names(), sh.SHOTS))
        self.assertEqual(sorted(sh.SHOTS), sorted(json.loads(pathlib.Path(sh.GOLDEN).read_text(encoding="utf-8"))))


if __name__ == "__main__":
    unittest.main()
