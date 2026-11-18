#!/usr/bin/env python3
"""2.0.0 [RVR-09] (검토 TEST-17·U60-06) 공개 자료 서명 도구 시험: python3 tools/test_remote_sign.py

열쇠는 시험마다 임시 폴더(저장소 밖)에 새로 만들고 지워요. 저장소의 진짜 열쇠는 찾지도 읽지도 않아요.
"""
import argparse
import base64
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
import remote_sign as rs  # noqa: E402
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey  # noqa: E402


def keyfile(text):
    return f'object RemoteKeys {{\n    val TRUSTED = listOf(\n{text}\n    )\n}}\n'


class RemoteSignTest(unittest.TestCase):
    def setUp(self):
        self.dir = pathlib.Path(tempfile.mkdtemp())
        self.keep = rs.KEYS_KT, rs.REMOTE, os.environ.pop("BAMNAT_REMOTE_KEY_PASS", None)
        self.pem = self.dir / "k.pem"
        with contextlib.redirect_stdout(io.StringIO()):
            rs.cmd_keygen(argparse.Namespace(out=str(self.pem), encrypt=False))
        pub = base64.b64encode(rs.raw_public(rs.load_private(self.pem).public_key())).decode()
        rs.KEYS_KT = self.dir / "RemoteFile.kt"
        rs.KEYS_KT.write_text(keyfile(f'        // 2026-11-12 test key\n        "{pub}", // the one that signs'), encoding="utf-8")
        rs.REMOTE = self.dir / "remote"
        rs.REMOTE.mkdir()
        self.file = rs.REMOTE / "rates.json"
        self.file.write_text(json.dumps({"format": 1, "kind": "rates", "version": 1, "issued": "2026-11-12", "minApp": 1, "data": {}}), encoding="utf-8")

    def tearDown(self):
        rs.KEYS_KT, rs.REMOTE, p = self.keep
        if p is not None:
            os.environ["BAMNAT_REMOTE_KEY_PASS"] = p
        else:
            os.environ.pop("BAMNAT_REMOTE_KEY_PASS", None)
        shutil.rmtree(self.dir)

    def run_cmd(self, fn, **k):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            try:
                fn(argparse.Namespace(**k))
                code = 0
            except SystemExit as e:
                code = e.code
        return code, out.getvalue()

    def test_a_key_with_two_slashes_inside_is_kept_whole(self):
        rs.KEYS_KT.write_text(keyfile('        // old\n        "AAAA//BBBBccccDDDDeeeeFFFFggggHHHHiiiiJJJJkkk=", // spare\n        // "CCCC//notakeyatallnotakeyatallnotakeyatall="'), encoding="utf-8")
        self.assertEqual(["AAAA//BBBBccccDDDDeeeeFFFFggggHHHHiiiiJJJJkkk="], rs.trusted_keys())

    def test_check_says_missing_broken_and_not_matching_apart(self):
        code, out = self.run_cmd(rs.cmd_check, files=[str(self.file)], need_sig2=True)
        self.assertEqual(1, code)
        self.assertIn(".sig2 없음", out)
        self.file.with_name("rates.json.sig2").write_text("not base64!!\n", encoding="ascii")
        self.assertIn(".sig2 깨짐", self.run_cmd(rs.cmd_check, files=[str(self.file)], need_sig2=True)[1])
        self.run_cmd(rs.cmd_sign, key=str(self.pem), files=[str(self.file)])
        code, out = self.run_cmd(rs.cmd_check, files=[str(self.file)], need_sig2=True)
        self.assertEqual((0, True), (code, ".sig2 맞음" in out), out)
        self.file.write_text(self.file.read_text(encoding="utf-8").replace('"version": 1', '"version": 2'), encoding="utf-8")
        code, out = self.run_cmd(rs.cmd_check, files=[str(self.file)], need_sig2=True)
        self.assertEqual(1, code)
        self.assertIn(".sig2 맞지 않음 (파일이 서명 뒤 바뀌었거나 RemoteKeys에 없는 열쇠)", out)

    def test_a_file_outside_the_repository_is_named_by_its_path(self):
        code, out = self.run_cmd(rs.cmd_sign, key=str(self.pem), files=[str(self.file)])
        self.assertEqual(0, code, out)
        self.assertIn("signed " + str(self.file), out)

    def test_a_locked_key_opens_only_with_its_passphrase(self):
        locked = self.dir / "locked.pem"
        os.environ["BAMNAT_REMOTE_KEY_PASS"] = "a long test passphrase"
        self.assertEqual(0, self.run_cmd(rs.cmd_keygen, out=str(locked), encrypt=True)[0])
        self.assertIn(b"ENCRYPTED", locked.read_bytes())
        self.assertIsInstance(rs.load_private(locked), Ed25519PrivateKey)
        os.environ.pop("BAMNAT_REMOTE_KEY_PASS")
        with self.assertRaises(SystemExit):
            with contextlib.redirect_stderr(io.StringIO()):
                rs.load_private(locked)
        code, out = self.run_cmd(rs.cmd_keygen, out=str(self.dir / "x.pem"), encrypt=True)
        self.assertEqual(1, code)
        self.assertIn("BAMNAT_REMOTE_KEY_PASS", out)

    def test_a_key_inside_the_repository_is_refused(self):
        with self.assertRaises(SystemExit):
            with contextlib.redirect_stderr(io.StringIO()):
                rs.key_path(str(rs.REPO / "shift-calendar-app" / "k.pem"))

    def test_the_repository_files_carry_a_matching_sig2(self):
        rs.KEYS_KT, rs.REMOTE = self.keep[0], self.keep[1]
        code, out = self.run_cmd(rs.cmd_check, files=[], need_sig2=True)
        self.assertEqual(0, code, out)


if __name__ == "__main__":
    unittest.main()
