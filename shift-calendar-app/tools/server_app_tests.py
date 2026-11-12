#!/usr/bin/env python3
"""2.0.0 [RVR-14] (총괄 HQ-37) The app test classes that run against a server: they start server/company-worker's
test/local-server.mjs or write the payloads server/share-worker checks (share-payload*.json), or the 바깥 길 bodies both
servers check (build/outside-bodies, 2.0.0 [RVR-22]). A draft pull request that
changes a server runs these (android.yml server-app), read from the test sources so a new one joins by itself.

  python3 tools/server_app_tests.py           # one class per line
  python3 tools/server_app_tests.py --gradle  # --tests <class> … for Gradle
"""
import os
import re
import sys

TESTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "test", "java")
# 2.0.0 [RVR-22] and the 바깥 길 시험 (OutsideBodiesTest, SD-09): the bodies both servers' validators check (build/outside-bodies).
MARKS = ("local-server.mjs", "share-payload", "outside-bodies")


def classes(root=TESTS):
    out = []
    for d, _, files in os.walk(root):
        for f in sorted(files):
            if not f.endswith(".kt"):
                continue
            with open(os.path.join(d, f), encoding="utf-8") as fh:
                text = fh.read()
            if any(m in text for m in MARKS):
                pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
                out.append(f"{pkg.group(1)}.{f[:-3]}" if pkg else f[:-3])
    return sorted(out)


def main(argv):
    found = classes()
    if not found:
        print("no app test runs against a server: did the marks change?", file=sys.stderr)
        return 1
    print(" ".join(f"--tests {c}" for c in found) if "--gradle" in argv else "\n".join(found))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
