#!/usr/bin/env python3
"""Signs and checks the remote data files (remote/v1/*.json at the top of the repository) [REMOTE-01].

The app trusts a file downloaded from GitHub Pages only when <name>.json.sig2 (or the old <name>.json.sig)
holds an Ed25519 signature of the file's exact bytes by a key listed in core RemoteKeys.TRUSTED (결정 D3).

[REMOTE-08] 1.27 (결정 D83): the 2022-10-22/23 keys were lost, so 'sign' writes <name>.json.sig2 with the
2026-10-26 key and never touches <name>.json.sig. Apps up to 1.26 only read .sig and only trust the old keys:
while a file is unchanged its old .sig still checks out for them; once a file changes, its old .sig no longer
matches and 'check' says so (those apps keep the data they shipped with, nothing breaks).

The private key is never in this repository. It lives in the project's private release-key/ folder and is
passed by path, either with --key or the BAMNAT_REMOTE_KEY environment variable. This script never prints it.

2.0.0 [RVR-09] (검토 TEST-17·U60-06): 'check' says which of 없음 / 깨짐 / 맞지 않음 each signature is (a .sig2 that does
not match used to read "없음"), a key in RemoteKeys with '//' inside it is no longer cut at the '//', a file outside the
repository is named by its path instead of failing after it was signed, and keygen can lock the key with a passphrase
(BAMNAT_REMOTE_KEY_PASS, read from the environment only; sign and pub read it the same way). CI runs 'check --need-sig2'.

Usage (from shift-calendar-app/):
  python3 tools/remote_sign.py keygen --out /path/outside/repo/bamnat-remote-ed25519.pem [--encrypt]
  python3 tools/remote_sign.py pub --key <pem>            # the base64 public key for RemoteKeys.TRUSTED
  python3 tools/remote_sign.py sign --key <pem> [files]   # default: every remote/v1/*.json -> <name>.json.sig2
  python3 tools/remote_sign.py check [--need-sig2] [files] # exit 1 when a signature is missing or wrong

Needs the 'cryptography' package (pip install cryptography).
"""
import argparse
import base64
import json
import os
import pathlib
import re
import sys

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

APP = pathlib.Path(__file__).resolve().parent.parent
REPO = APP.parent
REMOTE = REPO / "remote" / "v1"
KEYS_KT = APP / "core" / "src" / "main" / "kotlin" / "com" / "bamnat" / "core" / "remote" / "RemoteFile.kt"


def fail(msg):
    print(msg, file=sys.stderr)
    sys.exit(1)


def key_path(arg):
    p = arg or os.environ.get("BAMNAT_REMOTE_KEY")
    if not p:
        fail("No key: pass --key or set BAMNAT_REMOTE_KEY to the private key file (kept outside the repository).")
    p = pathlib.Path(p).resolve()
    try:
        p.relative_to(REPO)
        fail("Refusing a private key inside the repository: keep it in the project's release-key/ folder.")
    except ValueError:
        pass
    return p


def passphrase():
    """[RVR-09] The key's passphrase, from the environment only (never an argument, never printed)."""
    p = os.environ.get("BAMNAT_REMOTE_KEY_PASS")
    return p.encode("utf-8") if p else None


def load_private(path):
    try:
        return serialization.load_pem_private_key(path.read_bytes(), password=passphrase())
    except TypeError:
        fail("The key is locked: set BAMNAT_REMOTE_KEY_PASS to its passphrase (or the key is not locked and it is set).")
    except ValueError:
        fail("The key could not be opened: wrong passphrase in BAMNAT_REMOTE_KEY_PASS, or not an Ed25519 PEM.")


def rel(p):
    """[RVR-09] A file's name for the report: from the repository's top, or its full path when it is outside."""
    try:
        return str(p.relative_to(REPO))
    except ValueError:
        return str(p)


def raw_public(pub):
    return pub.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def trusted_keys():
    text = KEYS_KT.read_text(encoding="utf-8")
    block = text[text.index("val TRUSTED"):]
    block = block[: block.index("\n    )")]
    # [RVR-09] Drop comments but not a '//' inside a quoted key (base64 may hold one): strings are matched first.
    block = "\n".join(re.sub(r'"[^"]*"|//.*', lambda m: m.group(0) if m.group(0).startswith('"') else "", l) for l in block.splitlines())
    return re.findall(r'"([A-Za-z0-9+/=]{40,})"', block)


# [RVR-09] What a signature file says about a file: 맞음, 없음 (no such file), 깨짐 (not a base64 Ed25519 signature) or
# 맞지 않음 (a signature, but no trusted key signed these bytes: the file changed after it was signed, or the key is not in
# RemoteKeys.TRUSTED).
OK, MISSING, BROKEN, MISMATCH = "맞음", "없음", "깨짐", "맞지 않음"


def verdict(sig_path, data, keys):
    if not sig_path.exists():
        return MISSING
    try:
        sig = base64.b64decode(sig_path.read_text(encoding="ascii").strip(), validate=True)
    except Exception:
        return BROKEN
    if len(sig) != 64:
        return BROKEN
    for k in keys:
        try:
            k.verify(sig, data)
            return OK
        except InvalidSignature:
            pass
    return MISMATCH


def verifies(sig_path, data, keys):
    """True, False or None (no file), as before 2.0.0."""
    v = verdict(sig_path, data, keys)
    return None if v == MISSING else v == OK


def files(args):
    if args:
        return [pathlib.Path(a).resolve() for a in args]
    return sorted(REMOTE.glob("*.json"))


def check_shape(path):
    data = json.loads(path.read_bytes().decode("utf-8"))
    for k in ("format", "kind", "version", "issued", "minApp", "data"):
        if k not in data:
            fail(f"{path.name}: no '{k}'")
    if path.stem != data["kind"]:
        fail(f"{path.name}: kind is '{data['kind']}'")


def cmd_keygen(a):
    out = pathlib.Path(a.out).resolve()
    lock = passphrase()
    if a.encrypt and not lock:
        fail("--encrypt needs the passphrase in BAMNAT_REMOTE_KEY_PASS.")
    try:
        out.relative_to(REPO)
        fail("Refusing to write a private key inside the repository.")
    except ValueError:
        pass
    if out.exists():
        fail(f"{out} exists; not overwriting a key.")
    key = Ed25519PrivateKey.generate()
    enc = serialization.BestAvailableEncryption(lock) if a.encrypt else serialization.NoEncryption()
    pem = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, enc)
    fd = os.open(out, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(pem)
    print("public key (put in RemoteKeys.TRUSTED):", base64.b64encode(raw_public(key.public_key())).decode())


def cmd_pub(a):
    print(base64.b64encode(raw_public(load_private(key_path(a.key)).public_key())).decode())


def cmd_sign(a):
    key = load_private(key_path(a.key))
    pub = base64.b64encode(raw_public(key.public_key())).decode()
    if pub not in trusted_keys():
        fail("This key's public half is not in RemoteKeys.TRUSTED; the app would reject the files.")
    for p in files(a.files):
        check_shape(p)
        sig = base64.b64encode(key.sign(p.read_bytes())).decode()
        p.with_name(p.name + ".sig2").write_text(sig + "\n", encoding="ascii")
        print("signed", rel(p))


def cmd_check(a):
    keys = [Ed25519PublicKey.from_public_bytes(base64.b64decode(k)) for k in trusted_keys()]
    bad = 0
    for p in files(a.files):
        check_shape(p)
        data = p.read_bytes()
        new = verdict(p.with_name(p.name + ".sig2"), data, keys)
        old = verdict(p.with_name(p.name + ".sig"), data, keys)
        ok = new == OK or (new == MISSING and old == OK)
        if a.need_sig2 and new != OK:
            ok = False
        later = "1.26 이하 앱도 받음" if old == OK else "1.26 이하 앱은 앱 안 값"
        why = {MISMATCH: " (파일이 서명 뒤 바뀌었거나 RemoteKeys에 없는 열쇠)", BROKEN: " (서명 모양이 아님)"}.get(new, "")
        print(("ok   " if ok else "BAD  ") + rel(p) + f"  .sig2 {new}{why}" + f"  · old .sig {old} ({later})")
        bad += 0 if ok else 1
    if bad:
        fail(f"{bad} file(s) without a matching signature: run 'sign' with the release key (see each line for why).")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("keygen")
    g.add_argument("--out", required=True)
    g.add_argument("--encrypt", action="store_true", help="lock the key with the passphrase in BAMNAT_REMOTE_KEY_PASS")
    p = sub.add_parser("pub")
    p.add_argument("--key")
    s = sub.add_parser("sign")
    s.add_argument("--key")
    s.add_argument("files", nargs="*")
    c = sub.add_parser("check")
    c.add_argument("--need-sig2", action="store_true", help="every file must carry a .sig2 (1.27 and later)")
    c.add_argument("files", nargs="*")
    a = ap.parse_args()
    {"keygen": cmd_keygen, "pub": cmd_pub, "sign": cmd_sign, "check": cmd_check}[a.cmd](a)


if __name__ == "__main__":
    main()
