#!/usr/bin/env python3
"""2.0.0 [RVL-55] (검토 DATA-07·I12a-04) 출시판 멈춤 기록 풀기: 줄인 이름(a.b.c)을 원래 이름으로.

The release build's R8 renames the app's classes, so a 멈춤 기록 sent with "점검 내용과 함께 문의하기" reads
`at a.b.c.a(SourceFile:12)` and a stop is named `NullPointerException@a.b.c.a:12<d.e.b:40` (core AppFrames). The build's
mapping file turns those back: the mapping zip kept with each release in the project's release/<판>/앱/ (the report's
first line "app 2.0.0 (54)" says which), or app/build/outputs/mapping/release/mapping.txt of the same build.

Usage (from shift-calendar-app/):
  python3 tools/retrace.py <mapping.txt|mapping.zip> [report.txt]   # the report from a file, or from stdin
  python3 tools/retrace.py <mapping.zip> --check                    # release self-check: the mapping turns names back

Only names the mapping knows are changed; everything else in the report (dates, memory, the phone's own lines) stays.
A line the build inlined becomes its chain, innermost first: "at" lines one per line, other places joined with " < ".
Nothing is sent anywhere, and the report is not kept.
"""
import io
import pathlib
import re
import sys
import zipfile

CLASS = re.compile(r"^(\S+) -> (\S+):$")
# "    4:5:void com.x.Y.helper(int):30:31 -> a", "    void save() -> b", "    1:1:void save():212 -> a"
METHOD = re.compile(r"^\s+(?:(\d+):(\d+):)?(\S+) ([^\s(]+)\(([^)]*)\)(?::(\d+)(?::(\d+))?)? -> (\S+)$")
# A dotted name (class + method, split at the last dot) and its line: "(SourceFile:12)" or ":12".
TOKEN = re.compile(r"(?<![\w$.])([A-Za-z_$][\w$]*(?:\.(?:[\w$-]+|<c?l?init>))+)(?:\((?:[^:()]*:)?(\d+)\)|:(\d+))?")


class Mapping:
    def __init__(self, text):
        self.classes = {}  # renamed class -> original class
        self.methods = {}  # renamed class -> [(obf, start, end, orig_class, orig_method, orig_start, orig_end)]
        cur = None
        for raw in text.splitlines():
            if raw.startswith("#") or not raw.strip():
                continue
            m = CLASS.match(raw)
            if m:
                orig, obf = m.group(1), m.group(2)
                self.classes[obf] = orig
                self.methods.setdefault(obf, [])
                cur = obf
                continue
            if cur is None:
                continue
            m = METHOD.match(raw)
            if not m:
                continue  # a field, or a line this reader does not need
            s, e, _ret, name, _args, os_, oe, obf = m.groups()
            orig_class = self.classes[cur]
            if "." in name:
                orig_class, name = name.rsplit(".", 1)
            self.methods[cur].append((obf, int(s) if s else None, int(e) if e else None, orig_class, name,
                                      int(os_) if os_ else None, int(oe) if oe else None))

    def frames(self, cls, method, line):
        """The original frames for a renamed one, innermost first; None when the mapping does not know the class."""
        if cls not in self.classes:
            return None
        cands = [m for m in self.methods.get(cls, []) if m[0] == method]
        if line is not None:
            ranged = [m for m in cands if m[1] is not None and m[1] <= line <= m[2]]
            if ranged:
                # Entries for one renamed range in file order are an inline chain (innermost first); keep the first range only.
                first = (ranged[0][1], ranged[0][2])
                chain = [m for m in ranged if (m[1], m[2]) == first]
                out = []
                for _obf, s, e, oc, om, os_, oe in chain:
                    if os_ is None:
                        ol = line
                    elif oe is not None and oe - os_ == e - s:
                        ol = os_ + (line - s)
                    else:
                        ol = os_
                    out.append((oc, om, ol))
                return out
        names = []
        for m in cands:
            if (m[3], m[4]) not in [(c, n) for c, n, _ in names]:
                names.append((m[3], m[4], line))
        return names or [(self.classes[cls], method, line)]


def short(cls, method, line):
    return f"{cls}.{method}" + (f":{line}" if line is not None else "")


def retrace_line(mp, line):
    def look(m):
        if "." not in m.group(1):
            return None
        cls, method = m.group(1).rsplit(".", 1)
        num = m.group(2) or m.group(3)
        return mp.frames(cls, method, int(num) if num else None)

    at = re.match(r"^(\s*at\s+)(.*)$", line)
    if at:
        m = TOKEN.match(at.group(2))
        got = look(m) if m else None
        if got:
            rest = at.group(2)[m.end():]
            return "\n".join(at.group(1) + short(*f) + (rest if i == len(got) - 1 else "") for i, f in enumerate(got))

    def sub(m):
        got = look(m)
        return " < ".join(short(*f) for f in got) if got else m.group(0)

    return TOKEN.sub(sub, line)


def retrace(mapping_text, report):
    mp = Mapping(mapping_text)
    return "\n".join(retrace_line(mp, l) for l in report.splitlines())


def read_mapping(path):
    p = pathlib.Path(path)
    if p.suffix == ".zip":
        with zipfile.ZipFile(p) as z:
            names = [n for n in z.namelist() if n.endswith("mapping.txt")]
            if not names:
                sys.exit(f"{p}: no mapping.txt inside")
            # The phone app's mapping (not the watch's) when both are in one zip.
            names.sort(key=lambda n: ("wear" in n, len(n)))
            return z.read(names[0]).decode("utf-8")
    return p.read_text(encoding="utf-8")


def check(text):
    """The release self-check (CLAUDE.md 4항): the mapping is R8's, has its id, and turns the app's renamed classes back."""
    mid = re.search(r"^# pg_map_id: (\S+)", text, re.M)
    mp = Mapping(text)
    ours = sum(1 for o in mp.classes.values() if o.startswith("com.bamnat."))
    renamed = sum(1 for k, o in mp.classes.items() if o.startswith("com.bamnat.") and not k.startswith("com.bamnat."))
    print(f"pg_map_id {mid.group(1) if mid else '없음'} · com.bamnat 클래스 {ours} · 줄인 이름 {renamed}")
    return 0 if mid and renamed > 0 else 1


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return 0 if len(argv) >= 2 else 2
    text = read_mapping(argv[1])
    if len(argv) > 2 and argv[2] == "--check":
        return check(text)
    report = pathlib.Path(argv[2]).read_text(encoding="utf-8") if len(argv) > 2 else io.TextIOWrapper(sys.stdin.buffer, encoding="utf-8").read()
    print(retrace(text, report))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
