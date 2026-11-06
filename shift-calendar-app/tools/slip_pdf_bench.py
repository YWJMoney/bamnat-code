#!/usr/bin/env python3
"""[READ-34] 1.38 급여명세서 PDF 정답 묶음 (사용자 2026-11-06 "기술적인 소프트웨어적 부분은 만들어서 넣을 수 있는 거 아니야?").

요즘 명세서는 회사 ERP·급여 프로그램·노무 사이트가 PDF로 줘요. 사진처럼 OCR로 읽지 않고, PDF 속 글자를 그대로 읽으면 틀릴
일이 없어요. 이 도구는 흔한 모양의 명세서 PDF를 만들고(금액은 지어낸 것, 실제 사람·회사 아님) 정답을 옆에 적어요:
core/src/test/resources/slip-pdf/<case>.pdf + <case>.truth.tsv. SlipPdfBenchTest가 앱과 같은 길(PdfText → SlipReading)로 읽어 맞춰요.

Setup: pip install reportlab pymupdf; fonts in $ROSTER_FONTS (default /tmp/fonts, NanumGothic·NanumMyeongjo).
Run: python3 tools/slip_pdf_bench.py
"""
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "core", "src", "test", "resources", "slip-pdf")
FONTS = os.environ.get("ROSTER_FONTS", "/tmp/fonts")

EARN_NAMES = ["기본급", "연장근로수당", "야간근로수당", "휴일근로수당", "식대", "직책수당", "자격수당", "차량유지비", "상여금"]
DEDUCT_NAMES = ["국민연금", "건강보험", "장기요양보험", "고용보험", "소득세", "지방소득세"]


def won(n):
    return f"{n:,}"


def slip(rng, earn_n=5):
    base = rng.randrange(2_100_000, 3_600_000, 10)
    earn = [("기본급", base)] + [(n, rng.randrange(30_000, 420_000, 10)) for n in rng.sample(EARN_NAMES[1:], earn_n - 1)]
    gross = sum(a for _, a in earn)
    pension = gross * 45 // 1000 // 10 * 10
    health = gross * 3545 // 100000 // 10 * 10
    care = health * 12950 // 100000 // 10 * 10
    job = gross * 9 // 1000 // 10 * 10
    tax = rng.randrange(20_000, 160_000, 10)
    deduct = [("국민연금", pension), ("건강보험", health), ("장기요양보험", care), ("고용보험", job), ("소득세", tax), ("지방소득세", tax // 10 // 10 * 10)]
    dsum = sum(a for _, a in deduct)
    return earn, deduct, gross, dsum, gross - dsum


def truth(case, earn, deduct, gross, dsum, net, month):
    with open(os.path.join(OUT, case + ".truth.tsv"), "w", encoding="utf-8") as f:
        f.write(f"month\t{month}\n")
        for n, a in earn + deduct:
            f.write(f"item\t{n}\t{a}\n")
        f.write(f"total\t지급합계\t{gross}\ntotal\t공제합계\t{dsum}\ntotal\t실수령액\t{net}\n")


def rl_cases(rng):
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4, landscape
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.pdfgen import canvas
    from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
    from reportlab.lib.styles import ParagraphStyle
    pdfmetrics.registerFont(TTFont("Nanum", os.path.join(FONTS, "NanumGothic-Regular.ttf")))
    pdfmetrics.registerFont(TTFont("Myeongjo", os.path.join(FONTS, "NanumMyeongjo-Regular.ttf")))
    title = ParagraphStyle("t", fontName="Nanum", fontSize=15, leading=20)
    body = ParagraphStyle("b", fontName="Nanum", fontSize=9, leading=12)

    def styled(t, font="Nanum", size=9):
        t.setStyle(TableStyle([("FONT", (0, 0), (-1, -1), font, size), ("GRID", (0, 0), (-1, -1), 0.5, colors.grey),
                               ("ALIGN", (1, 0), (-1, -1), "RIGHT"), ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EEEEEE"))]))
        return t

    # s01: 지급 | 금액 | 공제 | 금액 side by side, totals under (the most common).
    earn, deduct, g, d, n = slip(rng)
    rows = [["지급 항목", "금액", "공제 항목", "금액"]]
    for i in range(max(len(earn), len(deduct))):
        e = earn[i] if i < len(earn) else ("", None)
        x = deduct[i] if i < len(deduct) else ("", None)
        rows.append([e[0], won(e[1]) if e[1] else "", x[0], won(x[1]) if x[1] else ""])
    rows.append(["지급합계", won(g), "공제합계", won(d)])
    rows.append(["", "", "실수령액", won(n)])
    doc = SimpleDocTemplate(os.path.join(OUT, "s01-side.pdf"), pagesize=A4)
    doc.build([Paragraph("2026년 10월 급여명세서", title), Paragraph("성명 한서연 · 소속 간호부 · 사번 20190412", body), Spacer(1, 10),
               styled(Table(rows, colWidths=[110, 90, 110, 90]))])
    truth("s01-side", earn, deduct, g, d, n, "2026-10")

    # s02: one list, 지급 then 공제, the way a small shop's program prints it.
    earn, deduct, g, d, n = slip(rng, 4)
    rows = [["항목", "금액"]] + [[a, won(b)] for a, b in earn] + [["지급액 계", won(g)]] + [[a, won(b)] for a, b in deduct] + \
        [["공제액 계", won(d)], ["차인지급액", won(n)]]
    doc = SimpleDocTemplate(os.path.join(OUT, "s02-list.pdf"), pagesize=A4)
    doc.build([Paragraph("급여 지급 명세서 (2026.10)", title), Spacer(1, 8), styled(Table(rows, colWidths=[160, 110]), "Myeongjo", 10)])
    truth("s02-list", earn, deduct, g, d, n, "2026-10")

    # s03: 항목 | 시간 | 금액 (hours beside the allowances), 공제 on its own table.
    earn, deduct, g, d, n = slip(rng, 4)
    hours = {"연장근로수당": "12.0", "야간근로수당": "16.0", "휴일근로수당": "8.0"}
    rows = [["지급 항목", "시간", "금액"]] + [[a, hours.get(a, ""), won(b)] for a, b in earn] + [["지급총액", "", won(g)]]
    rows2 = [["공제 항목", "금액"]] + [[a, won(b)] for a, b in deduct] + [["공제총액", won(d)], ["실지급액", won(n)]]
    doc = SimpleDocTemplate(os.path.join(OUT, "s03-hours.pdf"), pagesize=A4)
    doc.build([Paragraph("2026년 10월분 임금명세서", title), Paragraph("근로자 오지훈 / 지급일 2026-11-10", body), Spacer(1, 8),
               styled(Table(rows, colWidths=[130, 60, 100])), Spacer(1, 12), styled(Table(rows2, colWidths=[130, 100]))])
    truth("s03-hours", earn, deduct, g, d, n, "2026-10")

    # s04: drawn line by line with no table (an ERP's report writer): names left, amounts right-aligned, dotted rule.
    earn, deduct, g, d, n = slip(rng, 6)
    path = os.path.join(OUT, "s04-drawn.pdf")
    c = canvas.Canvas(path, pagesize=A4)
    c.setFont("Nanum", 16); c.drawString(60, 790, "(주)한빛물류  2026년 10월 급여명세서")
    c.setFont("Nanum", 9); c.drawString(60, 770, "성명: 배수아    부서: 물류2팀    직급: 주임")
    y = 740
    c.setFont("Nanum", 10); c.drawString(60, y, "[지급내역]"); c.drawString(320, y, "[공제내역]"); y -= 18
    for i in range(max(len(earn), len(deduct))):
        if i < len(earn):
            c.drawString(70, y, earn[i][0]); c.drawRightString(270, y, won(earn[i][1]))
        if i < len(deduct):
            c.drawString(330, y, deduct[i][0]); c.drawRightString(530, y, won(deduct[i][1]))
        y -= 16
    y -= 6; c.line(60, y + 10, 540, y + 10)
    c.drawString(70, y, "지급계"); c.drawRightString(270, y, won(g)); c.drawString(330, y, "공제계"); c.drawRightString(530, y, won(d))
    y -= 22; c.setFont("Nanum", 12); c.drawString(330, y, "실수령액"); c.drawRightString(530, y, won(n) + "원")
    c.save()
    truth("s04-drawn", earn, deduct, g, d, n, "2026-10")

    # s05: landscape, two blocks across with codes before names ("1001 기본급"), the way a 대기업 ERP prints.
    earn, deduct, g, d, n = slip(rng, 5)
    rows = [["코드", "지급항목", "지급액", "코드", "공제항목", "공제액"]]
    for i in range(max(len(earn), len(deduct))):
        e = earn[i] if i < len(earn) else ("", None)
        x = deduct[i] if i < len(deduct) else ("", None)
        rows.append([str(1001 + i) if e[1] else "", e[0], won(e[1]) if e[1] else "", str(2001 + i) if x[1] else "", x[0], won(x[1]) if x[1] else ""])
    rows.append(["", "지급합계", won(g), "", "공제합계", won(d)])
    rows.append(["", "", "", "", "차감지급액", won(n)])
    doc = SimpleDocTemplate(os.path.join(OUT, "s05-codes.pdf"), pagesize=landscape(A4))
    doc.build([Paragraph("급여명세서  귀속연월 2026-10", title), Spacer(1, 8), styled(Table(rows, colWidths=[40, 110, 90, 40, 110, 90]))])
    truth("s05-codes", earn, deduct, g, d, n, "2026-10")


def mupdf_case(rng):
    """s06: another PDF maker (MuPDF, a CID font), amounts with 원, the way a 노무 site's '명세서 저장' gives one."""
    import fitz
    earn, deduct, g, d, n = slip(rng, 5)
    doc = fitz.open()
    page = doc.new_page()
    page.insert_font(fontname="ng", fontfile=os.path.join(FONTS, "NanumGothic-Regular.ttf"))
    y = 60
    page.insert_text((50, y), "2026년 10월 임금명세서", fontname="ng", fontsize=16); y += 30
    page.insert_text((50, y), "성명 임도윤  |  사업장 밤낮카페 성수점", fontname="ng", fontsize=9); y += 26
    for label, items, total_name, total in (("지급", earn, "지급 합계", g), ("공제", deduct, "공제 합계", d)):
        page.insert_text((50, y), f"■ {label} 내역", fontname="ng", fontsize=11); y += 18
        for a, b in items:
            page.insert_text((60, y), a, fontname="ng", fontsize=10)
            t = won(b) + "원"
            page.insert_text((330 - fitz.get_text_length(t, fontname="helv", fontsize=10) * 1.1, y), t, fontname="ng", fontsize=10)
            y += 16
        page.insert_text((60, y), total_name, fontname="ng", fontsize=10)
        t = won(total) + "원"
        page.insert_text((330 - fitz.get_text_length(t, fontname="helv", fontsize=10) * 1.1, y), t, fontname="ng", fontsize=10)
        y += 26
    page.insert_text((60, y), "실 지급액", fontname="ng", fontsize=12)
    page.insert_text((250, y), won(n) + "원", fontname="ng", fontsize=12)
    doc.save(os.path.join(OUT, "s06-mupdf.pdf"))
    truth("s06-mupdf", earn, deduct, g, d, n, "2026-10")


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(1140)
    rl_cases(rng)
    mupdf_case(rng)
    print("slip-pdf:", sorted(os.listdir(OUT)))


if __name__ == "__main__":
    main()
