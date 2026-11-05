#!/usr/bin/env python3
"""[READ-30] 근무표 파일 정답 묶음 (1.38, 사용자 2026-11-05 "사진·표·엑셀·PDF 어떤 형식이든 완벽하게 인식").

Writes the rosters a 병동·공장·가게 actually hands out, in the file shapes they come in, with the truth of every cell:

  xlsx  첫 시트가 안내문인 통합 문서, 병합 칸(연차 3일·이름 두 줄), 달을 넘는 4주 듀티(10/26~11/25), 날짜가 엑셀 날짜인 머리,
        사람이 열인 세로 표, 직급 열·이름 뒤 직급, 한 사람 두 줄(근무·시간)
  xls   진짜 옛 엑셀(BIFF8, xlwt), 근태·ERP가 '엑셀 내려받기'로 주는 HTML 표(.xls), 엑셀 2003 XML(.xls)
  csv   한글 윈도(CP949)
  ods   LibreOffice·한셀이 저장하는 OpenDocument
  pdf   엑셀에서 PDF로 저장한 근무표(pdf/<case>.pdf + 쪽마다 OCR 단어 pdf/<case>.p<n>.tsv, RapidOCR로 읽음)

Every name is made up. Each case is <case>.<ext> and <case>.truth.tsv (person, name, then date=code pairs; a staff case
has staff rows). FileBenchTest reads each with the app's own readers and counts cells right, cells quietly wrong (read as
another code without a doubt mark) and cells missed, before and after (the 1.38 report has both numbers).

Setup: pip install openpyxl xlwt reportlab pymupdf rapidocr_onnxruntime==1.2.3 (models as tools/roster_fixtures.py).
Run: python3 tools/file_bench.py [--no-ocr]
"""
import argparse
import calendar
import datetime as dt
import html
import io
import os
import random
import sys
import zipfile

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "src", "test", "resources", "file-bench")
NAMES = ["김민지", "이수진", "박하늘", "최유리", "정다은", "한지우", "오세영", "윤가람", "장미소", "임도연", "서하준", "문채원"]
ROLES = ["책임", "주임", "", "", "", "신규"]
CODES = ["D", "D", "E", "E", "N", "O", "O"]
WEEK = "월화수목금토일"


def month_days(y, m):
    return [dt.date(y, m, d) for d in range(1, calendar.monthrange(y, m)[1] + 1)]


def people(rng, days, n, codes=CODES):
    out = []
    for i in range(n):
        cells = {d: rng.choice(codes) for d in days}
        out.append((NAMES[i % len(NAMES)], ROLES[i % len(ROLES)], cells))
    return out


def write_truth(case, team, extra=None):
    with open(os.path.join(OUT, case + ".truth.tsv"), "w", encoding="utf-8") as f:
        f.write("# person\tname\tdate=code,...\n")
        for name, _, cells in team:
            f.write("person\t%s\t%s\n" % (name, ",".join("%s=%s" % (d.isoformat(), c) for d, c in sorted(cells.items()))))
        for line in extra or []:
            f.write(line + "\n")


def wide_rows(title, days, team, role_col=False, weekday=True, day_label=lambda d: d.day):
    rows = [[title]]
    head = ["이름"] + (["직급"] if role_col else []) + [day_label(d) for d in days]
    rows.append(head)
    if weekday:
        rows.append([""] + ([""] if role_col else []) + [WEEK[d.weekday()] for d in days])
    for name, role, cells in team:
        rows.append([name] + ([role] if role_col else []) + [cells[d] for d in days])
    return rows


# ---------------------------------------------------------------- xlsx

def xlsx_cases(rng):
    import openpyxl
    from openpyxl.utils import get_column_letter

    def save(wb, case):
        wb.save(os.path.join(OUT, case + ".xlsx"))

    nov = month_days(2026, 11)

    # x01 the plain ward roster
    team = people(rng, nov, 10)
    wb = openpyxl.Workbook(); ws = wb.active; ws.title = "11월"
    for r in wide_rows("2026년 11월 근무표", nov, team):
        ws.append(r)
    save(wb, "x01-basic"); write_truth("x01-basic", team)

    # x02 a cover sheet first
    team = people(rng, nov, 8)
    wb = openpyxl.Workbook(); ws = wb.active; ws.title = "안내"
    ws.append(["근무표 작성 안내"]); ws.append(["1. 휴가 신청은 전달 20일까지"]); ws.append(["2. 바꾸기는 수간호사에게"])
    ws2 = wb.create_sheet("11월 듀티")
    for r in wide_rows("2026년 11월", nov, team):
        ws2.append(r)
    save(wb, "x02-cover-first"); write_truth("x02-cover-first", team)

    # x03 merged cells: 연차 written once over three days, a name over two rows (duty row + time row)
    team = people(rng, nov, 8)
    wb = openpyxl.Workbook(); ws = wb.active
    rows = wide_rows("2026년 11월 근무표", nov, team)
    lead = 1
    for r in rows:
        ws.append(r)
    # person 2 takes 연차 on 10~12 written once, merged over the three cells
    name, role, cells = team[1]
    for d in (10, 11, 12):
        cells[dt.date(2026, 11, d)] = "연차"
    r = 3 + 1 + 1  # title, head, weekday, person 1, person 2
    c0 = lead + 10
    ws.cell(row=r, column=c0, value="연차")
    ws.cell(row=r, column=c0 + 1, value=None); ws.cell(row=r, column=c0 + 2, value=None)
    ws.merge_cells(start_row=r, start_column=c0, end_row=r, end_column=c0 + 2)
    # person 5 on 20~21 휴 merged
    name, role, cells = team[4]
    for d in (20, 21):
        cells[dt.date(2026, 11, d)] = "O"
    r5 = 3 + 5
    ws.cell(row=r5, column=lead + 20, value="O"); ws.cell(row=r5, column=lead + 21, value=None)
    ws.merge_cells(start_row=r5, start_column=lead + 20, end_row=r5, end_column=lead + 21)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=10)
    save(wb, "x03-merged"); write_truth("x03-merged", team)

    # x04 a four-week roster across two months: 10/26 ~ 11/25 (병동 4주 듀티), headed "11월"
    span = [dt.date(2026, 10, 26) + dt.timedelta(days=i) for i in range(31)]
    team = people(rng, span, 9)
    wb = openpyxl.Workbook(); ws = wb.active
    for r in wide_rows("2026년 11월 근무표 (10/26~11/25)", span, team):
        ws.append(r)
    save(wb, "x04-cross-month"); write_truth("x04-cross-month", team)

    # x05 people as columns, one day a row (편의점·알바 단톡에 올라오는 표)
    team = people(rng, nov, 5, ["오픈", "미들", "마감", "휴"])
    wb = openpyxl.Workbook(); ws = wb.active
    ws.append(["11월 스케줄"]); ws.append(["날짜", "요일"] + [n for n, _, _ in team])
    for d in nov:
        ws.append([d, WEEK[d.weekday()]] + [c[d] for _, _, c in team])
        ws.cell(row=ws.max_row, column=1).number_format = "m/d"
    save(wb, "x05-transposed"); write_truth("x05-transposed", team)

    # x06 the row of days as real Excel dates shown as "d", formulas for the next day
    team = people(rng, nov, 7)
    wb = openpyxl.Workbook(); ws = wb.active
    ws.append(["근무표"]); ws.append(["성명"] + [None] * len(nov))
    for i, d in enumerate(nov):
        c = ws.cell(row=2, column=2 + i, value=d if i == 0 else "=%s2+1" % get_column_letter(1 + i))
        c.number_format = "d"
    for name, _, cells in team:
        ws.append([name] + [cells[d] for d in nov])
    save(wb, "x06-date-header")
    # openpyxl writes no cached value for formulas: write it the way Excel saves (value cached) by hand below.
    _cache_formulas(os.path.join(OUT, "x06-date-header.xlsx"), nov)
    write_truth("x06-date-header", team)

    # x07 a role column and roles stuck to names
    team = people(rng, nov, 9)
    wb = openpyxl.Workbook(); ws = wb.active
    rows = wide_rows("2026년 11월 근무표", nov, team, role_col=True)
    for r in rows:
        ws.append(r)
    for i, (name, role, _) in enumerate(team):
        if i % 3 == 0 and role:
            ws.cell(row=4 + i, column=1, value="%s(%s)" % (name, role))
    save(wb, "x07-roles"); write_truth("x07-roles", team)

    # x08 one person over two rows: codes, then the hours under them; a total column at the end
    team = people(rng, nov, 6)
    wb = openpyxl.Workbook(); ws = wb.active
    ws.append(["2026년 11월 근무표"]); ws.append(["이름", "구분"] + [d.day for d in nov] + ["합계"])
    hours = {"D": "07-15", "E": "15-23", "N": "23-07", "O": ""}
    for name, _, cells in team:
        ws.append([name, "근무"] + [cells[d] for d in nov] + [sum(1 for v in cells.values() if v != "O")])
        ws.append(["", "시간"] + [hours[cells[d]] for d in nov] + [""])
        ws.merge_cells(start_row=ws.max_row - 1, start_column=1, end_row=ws.max_row, end_column=1)
    save(wb, "x08-two-rows"); write_truth("x08-two-rows", team)

    # x09 staff list for 사장님 급여 대장: 이름·형태·시급·요일별 시간, a phone column that is never read
    staff = [("김민지", "시급", 10320, "월화수", "09:00", "15:00", "홀"), ("이수진", "월급", 2300000, "월화수목금", "10:00", "19:00", "주방"),
             ("박하늘", "시급", 11000, "토일", "11:00", "20:00", "홀"), ("최유리", "일급", 120000, "금토", "17:00", "23:00", "마감")]
    wb = openpyxl.Workbook(); ws = wb.active
    ws.append(["직원 명단"]); ws.append(["성명", "연락처", "급여형태", "시급/월급", "근무요일", "출근", "퇴근", "담당"])
    for n, f, b, days, s, e, job in staff:
        ws.append([n, "010-0000-%04d" % rng.randint(0, 9999), f, b, days, s, e, job])
    save(wb, "s01-staff")
    with open(os.path.join(OUT, "s01-staff.truth.tsv"), "w", encoding="utf-8") as f:
        f.write("# staff\tname\tform\tbase\tdays\tstart\tend\tjob\n")
        for n, form, b, days, s, e, job in staff:
            f.write("staff\t%s\t%s\t%d\t%s\t%s\t%s\t%s\n" % (n, form, b, days, s, e, job))


def _cache_formulas(path, days):
    """Puts Excel's cached result (<v>) on the formula cells openpyxl wrote, as a workbook saved by Excel has."""
    src = zipfile.ZipFile(path)
    items = {n: src.read(n) for n in src.namelist()}
    src.close()
    sheet = items["xl/worksheets/sheet1.xml"].decode("utf-8")
    import re
    base = dt.date(1899, 12, 30)

    def fix(m):
        ref, body = m.group(1), m.group(2)
        col = re.match(r"([A-Z]+)", ref).group(1)
        n = 0
        for ch in col:
            n = n * 26 + ord(ch) - 64
        d = days[n - 2]
        return '<c r="%s"%s><f>%s</f><v>%d</v></c>' % (ref, m.group(3), body, (d - base).days)
    sheet = re.sub(r'<c r="([A-Z]+2)"([^>]*)><f>([^<]*)</f><v\s*/?>(?:</v>)?</c>', lambda m: fix_order(m, fix), sheet)
    items["xl/worksheets/sheet1.xml"] = sheet.encode("utf-8")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for n, b in items.items():
            z.writestr(n, b)


def fix_order(m, fix):
    class M:
        def group(self, i):
            return {1: m.group(1), 2: m.group(3), 3: m.group(2)}[i]
    return fix(M())


# ---------------------------------------------------------------- xls, html, xml 2003, csv, ods

def other_cases(rng):
    nov = month_days(2026, 11)

    # b01 a real old Excel file (BIFF8)
    import xlwt
    team = people(rng, nov, 8)
    wb = xlwt.Workbook(encoding="utf-8"); ws = wb.add_sheet("근무표")
    for r, row in enumerate(wide_rows("2026년 11월 근무표", nov, team)):
        if r == 0:
            ws.write_merge(0, 0, 0, 5, row[0])
            continue
        for c, v in enumerate(row):
            ws.write(r, c, v)
    wb.save(os.path.join(OUT, "b01-biff.xls")); write_truth("b01-biff", team)

    # h01 an HTML table saved as .xls (근태·ERP '엑셀 내려받기')
    team = people(rng, nov, 8)
    rows = wide_rows("2026년 11월 근무표", nov, team)
    out = ['<html xmlns:x="urn:schemas-microsoft-com:office:excel"><head><meta http-equiv="Content-Type" content="text/html; charset=euc-kr">'
           '<style>td{mso-number-format:"\\@";}</style></head><body><table border=1>']
    out.append('<tr><td colspan="31" align=center><b>%s</b></td></tr>' % rows[0][0])
    for row in rows[1:]:
        out.append("<tr>" + "".join("<td>%s</td>" % html.escape(str(v)) for v in row) + "</tr>")
    out.append("</table></body></html>")
    with open(os.path.join(OUT, "h01-html.xls"), "wb") as f:
        f.write("\n".join(out).encode("cp949"))
    write_truth("h01-html", team)

    # m01 Excel 2003 XML spreadsheet saved as .xls
    team = people(rng, nov, 7)
    rows = wide_rows("2026년 11월 근무표", nov, team)
    x = ['<?xml version="1.0"?>', '<?mso-application progid="Excel.Sheet"?>',
         '<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">',
         '<Worksheet ss:Name="근무"><Table>']
    for i, row in enumerate(rows):
        cells = []
        for j, v in enumerate(row):
            if i == 1 and j == 3:  # a skipped cell: the next one says where it stands
                continue
            t = "Number" if isinstance(v, int) else "String"
            idx = ' ss:Index="%d"' % (j + 1) if (i == 1 and j == 4) else ""
            cells.append('<Cell%s><Data ss:Type="%s">%s</Data></Cell>' % (idx, t, html.escape(str(v))))
        x.append("<Row>" + "".join(cells) + "</Row>")
    x.append("</Table></Worksheet></Workbook>")
    # day 3 is missing from the row of days on purpose: the column is still there (ss:Index), and the reader fills it.
    with open(os.path.join(OUT, "m01-xml2003.xls"), "w", encoding="utf-8") as f:
        f.write("\n".join(x))
    write_truth("m01-xml2003", team)

    # c01 CP949 CSV
    team = people(rng, nov, 8)
    with open(os.path.join(OUT, "c01-cp949.csv"), "wb") as f:
        for row in wide_rows("2026년 11월 근무표", nov, team):
            f.write((",".join(str(v) for v in row) + "\r\n").encode("cp949"))
    write_truth("c01-cp949", team)

    # o01 OpenDocument spreadsheet
    team = people(rng, nov, 6)
    rows = wide_rows("2026년 11월 근무표", nov, team)
    body = []
    for row in rows:
        cells = []
        for v in row:
            if isinstance(v, int):
                cells.append('<table:table-cell office:value-type="float" office:value="%d"><text:p>%d</text:p></table:table-cell>' % (v, v))
            else:
                cells.append('<table:table-cell office:value-type="string"><text:p>%s</text:p></table:table-cell>' % html.escape(v))
        body.append("<table:table-row>" + "".join(cells) + "</table:table-row>")
    # an empty run written once (number-columns-repeated), as LibreOffice does
    body.insert(1, '<table:table-row><table:table-cell table:number-columns-repeated="40"/></table:table-row>')
    content = ('<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" '
               'xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">'
               '<office:body><office:spreadsheet><table:table table:name="근무">%s</table:table></office:spreadsheet></office:body></office:document-content>') % "".join(body)
    with zipfile.ZipFile(os.path.join(OUT, "o01-ods.ods"), "w") as z:
        z.writestr("mimetype", "application/vnd.oasis.opendocument.spreadsheet", compress_type=zipfile.ZIP_STORED)
        z.writestr("content.xml", content)
    write_truth("o01-ods", team)


# ---------------------------------------------------------------- pdf

def truth_of(case):
    with open(os.path.join(OUT, case + ".truth.tsv"), encoding="utf-8") as f:
        return f.read()


def pdf_cases(rng, ocr):
    from reportlab.lib.pagesizes import A4, landscape
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, PageBreak
    from reportlab.lib import colors
    from reportlab.lib.styles import ParagraphStyle
    font = os.path.join(os.environ.get("ROSTER_FONTS", "/tmp/fonts"), "NanumGothic-Regular.ttf")
    pdfmetrics.registerFont(TTFont("Nanum", font))
    nov = month_days(2026, 11)

    def table(rows):
        t = Table([[str(v) for v in r] for r in rows], repeatRows=0)
        t.setStyle(TableStyle([("FONT", (0, 0), (-1, -1), "Nanum", 7), ("GRID", (0, 0), (-1, -1), 0.4, colors.grey),
                               ("ALIGN", (1, 0), (-1, -1), "CENTER"), ("LEFTPADDING", (0, 0), (-1, -1), 2), ("RIGHTPADDING", (0, 0), (-1, -1), 2)]))
        return t

    cases = []
    # p01 one page, 12 people
    team = people(rng, nov, 12)
    rows = wide_rows("", nov, team)[1:]
    doc = SimpleDocTemplate(os.path.join(OUT, "p01-pdf.pdf"), pagesize=landscape(A4), leftMargin=20, rightMargin=20)
    doc.build([Paragraph("2026년 11월 근무표", ParagraphStyle("t", fontName="Nanum", fontSize=12)), table(rows)])
    write_truth("p01-pdf", team); cases.append("p01-pdf")
    # p02 two pages: the team split in two, the row of days on each page
    team = people(rng, nov, 12)
    rows = wide_rows("", nov, team)
    doc = SimpleDocTemplate(os.path.join(OUT, "p02-pdf-pages.pdf"), pagesize=landscape(A4), leftMargin=20, rightMargin=20)
    doc.build([Paragraph("2026년 11월 근무표 (1/2)", ParagraphStyle("t", fontName="Nanum", fontSize=12)), table(rows[1:3] + rows[3:9]), PageBreak(),
               Paragraph("2026년 11월 근무표 (2/2)", ParagraphStyle("t", fontName="Nanum", fontSize=12)), table(rows[1:3] + rows[9:])])
    write_truth("p02-pdf-pages", team); cases.append("p02-pdf-pages")

    if not ocr:
        return
    # p03 a scanned PDF: the page of p01 as a picture only (no text in the file), read by OCR as the app does.
    import fitz
    team = truth_of("p01-pdf")
    src = fitz.open(os.path.join(OUT, "p01-pdf.pdf"))
    pix = src[0].get_pixmap(matrix=fitz.Matrix(2, 2))
    scan = fitz.open()
    page = scan.new_page(width=src[0].rect.width, height=src[0].rect.height)
    page.insert_image(page.rect, stream=pix.tobytes("jpeg"))
    scan.save(os.path.join(OUT, "p03-scan.pdf"))
    with open(os.path.join(OUT, "p03-scan.truth.tsv"), "w", encoding="utf-8") as f:
        f.write(team)
    cases = ["p03-scan"]
    import fitz
    import numpy as np
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import roster_fixtures as rf
    engine = rf.Ocr()
    for case in cases:
        pdf = fitz.open(os.path.join(OUT, case + ".pdf"))
        for i, page in enumerate(pdf):
            # The app renders a page at up to 4× its points (about 288 dpi), the long side kept under 4,000 px (AnyFile.render).
            zoom = min(4.0, 4000.0 / max(page.rect.width, page.rect.height))
            pix = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom))
            img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)[:, :, :3][:, :, ::-1].copy()
            words = rf.ocr_words(engine, img)
            with open(os.path.join(OUT, "%s.p%d.tsv" % (case, i + 1)), "w", encoding="utf-8") as f:
                f.write("# word left top right bottom angle text (RapidOCR on the page as the app renders it)\n")
                for (l, t, r, b, a, text) in words:
                    f.write("word\t%d\t%d\t%d\t%d\t%.1f\t%s\n" % (l, t, r, b, a, text))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-ocr", action="store_true")
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(1138)
    xlsx_cases(rng)
    other_cases(rng)
    pdf_cases(rng, not a.no_ocr)
    print("wrote", sorted(os.listdir(OUT)))


if __name__ == "__main__":
    main()
