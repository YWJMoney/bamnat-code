#!/usr/bin/env python3
"""2.0.0 [RVL-41][RVL-42][RVL-44] 명세서 PDF 정답 묶음 더하기: 암호 걸린 PDF, 옛 한글 글꼴 PDF, 돌린 쪽, 여러 장 든 PDF.

검토 U07b-10·I07bb-01(회사가 메일로 주는 명세서는 생년월일 암호가 흔해요), SCAN-15(옛 한글 글꼴은 깨진 글을 '읽었다'고 쳤어요,
/Rotate를 안 봤어요), SCAN-28(여러 장 든 PDF를 한 장으로 합쳤어요)을 시험하는 파일을 1.38 정답 묶음(slip_pdf_bench.py)의 명세서로
만들어요(금액은 지어낸 것, 실제 사람·회사 아님). 결과: core/src/test/resources/slip-pdf-more/.

- l-*.pdf: s01-side를 표준 잠금 다섯 가지로(사용자 암호 900101, 소유자 암호 owner-secret), l-owner-only는 사용자 암호 없음(인쇄만 막음),
  l-scan-aes-128은 글자 없는(스캔한) 명세서를 잠근 것.
- k-uhc.pdf(KSCms-UHC-H), k-ucs2.pdf(UniKS-UCS2-H): ToUnicode 없이 옛 방식 한글 글꼴로 쓴 s01 명세서. k-latin.pdf: 같은 한글
  바이트를 라틴 글꼴로 적어 깨진 글이 나오는 파일(글자 없음으로 보고 사진 읽기로 가야 해요).
- r-rotate90.pdf, r-rotate270.pdf: s01을 /Rotate로 돌려 보이게 저장한 파일(보이는 모양은 s01 그대로).
- m-three.pdf: s01·s02·s03 세 사람의 10월 명세서가 든 한 파일.

Setup: pip install pypdf cryptography pillow; poppler(pdftoppm) for the scan.
Run: python3 tools/slip_pdf_more.py
"""
import io
import os
import subprocess
import tempfile

from pypdf import PdfReader, PdfWriter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "core", "src", "test", "resources", "slip-pdf")
OUT = os.path.join(HERE, "..", "core", "src", "test", "resources", "slip-pdf-more")

USER = "900101"
OWNER = "owner-secret"


def lock(src, dst, algorithm, user=USER):
    w = PdfWriter(clone_from=PdfReader(src))
    w.encrypt(user_password=user, owner_password=OWNER, algorithm=algorithm)
    with open(dst, "wb") as f:
        w.write(f)


def scanned(src):
    """s01 as a scan: its page drawn at 110 dpi in grey, saved as a picture-only PDF."""
    with tempfile.TemporaryDirectory() as d:
        subprocess.run(["pdftoppm", "-r", "110", "-gray", "-png", src, os.path.join(d, "p")], check=True)
        from PIL import Image
        png = sorted(f for f in os.listdir(d) if f.endswith(".png"))[0]
        img = Image.open(os.path.join(d, png)).convert("L")
        buf = io.BytesIO()
        img.save(buf, "PDF", resolution=110.0)
        return buf.getvalue()


def rotated(src, dst, shown):
    """The page's content turned so that, with /Rotate [shown], it looks as the original does."""
    r = PdfReader(src)
    w = PdfWriter()
    for p in r.pages:
        p.rotate((360 - shown) % 360)
        p.transfer_rotation_to_content()
        p.rotate(shown)
        w.add_page(p)
    with open(dst, "wb") as f:
        w.write(f)


def truth_rows(case):
    rows = [l.rstrip("\n").split("\t") for l in open(os.path.join(SRC, f"{case}.truth.tsv"), encoding="utf-8")]
    month = next(r[1] for r in rows if r[0] == "month")
    items = [(r[1], int(r[2])) for r in rows if r[0] in ("item", "total")]
    return month, items


def old_font_pdf(dst, kind):
    """s01's lines in a Type0 font with a predefined Korean CMap and no ToUnicode (kind uhc·ucs2), or Korean bytes in a Latin font."""
    month, items = truth_rows("s01-side")
    y, m = month.split("-")
    lines = [(72, 780, f"{y}년 {int(m)}월 급여명세서")]
    at = 740
    for name, amount in items:
        lines.append((72, at, name))
        lines.append((300, at, f"{amount:,}"))
        at -= 22

    def enc(t):
        if kind == "ucs2":
            return t.encode("utf-16-be")
        return t.encode("cp949")

    body = "".join(f"BT /F1 11 Tf 1 0 0 1 {x} {yy} Tm <{enc(t).hex()}> Tj ET\n" for x, yy, t in lines).encode("latin-1")
    if kind == "latin":
        fonts = [b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"]
    else:
        cmap = "KSCms-UHC-H" if kind == "uhc" else "UniKS-UCS2-H"
        fonts = [
            f"<< /Type /Font /Subtype /Type0 /BaseFont /HYSMyeongJo-Medium-{cmap} /Encoding /{cmap} /DescendantFonts [6 0 R] >>".encode(),
            b"<< /Type /Font /Subtype /CIDFontType0 /BaseFont /HYSMyeongJo-Medium /CIDSystemInfo << /Registry (Adobe) /Ordering (Korea1) /Supplement 2 >> "
            b"/FontDescriptor 7 0 R /DW 1000 /W [1 95 500] >>",
            b"<< /Type /FontDescriptor /FontName /HYSMyeongJo-Medium /Flags 6 /FontBBox [-28 -148 1001 880] /ItalicAngle 0 /Ascent 880 "
            b"/Descent -120 /CapHeight 880 /StemV 93 >>",
        ]
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(body)).encode() + b" >>\nstream\n" + body + b"\nendstream",
    ] + fonts
    out = io.BytesIO()
    out.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, o in enumerate(objs, start=1):
        offsets.append(out.tell())
        out.write(f"{i} 0 obj\n".encode() + o + b"\nendobj\n")
    xref = out.tell()
    out.write(f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode())
    for off in offsets:
        out.write(f"{off:010d} 00000 n \n".encode())
    out.write(f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    with open(dst, "wb") as f:
        f.write(out.getvalue())


def main():
    os.makedirs(OUT, exist_ok=True)
    s01 = os.path.join(SRC, "s01-side.pdf")
    for name, alg in [("l-rc4-40", "RC4-40"), ("l-rc4-128", "RC4-128"), ("l-aes-128", "AES-128"), ("l-aes-256-r5", "AES-256-R5"), ("l-aes-256", "AES-256")]:
        lock(s01, os.path.join(OUT, f"{name}.pdf"), alg)
    lock(s01, os.path.join(OUT, "l-owner-only.pdf"), "AES-128", user="")
    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as t:
        t.write(scanned(s01))
    try:
        lock(t.name, os.path.join(OUT, "l-scan-aes-128.pdf"), "AES-128")
    finally:
        os.unlink(t.name)
    for kind in ("uhc", "ucs2", "latin"):
        old_font_pdf(os.path.join(OUT, f"k-{kind}.pdf"), kind)
    for shown in (90, 270):
        rotated(s01, os.path.join(OUT, f"r-rotate{shown}.pdf"), shown)
    w = PdfWriter()
    for case in ("s01-side", "s02-list", "s03-hours"):
        for p in PdfReader(os.path.join(SRC, f"{case}.pdf")).pages:
            w.add_page(p)
    with open(os.path.join(OUT, "m-three.pdf"), "wb") as f:
        w.write(f)
    print("written:", ", ".join(sorted(os.listdir(OUT))))


if __name__ == "__main__":
    main()
