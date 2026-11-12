package com.bamnat.core

import com.bamnat.core.imports.AttendanceImport
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterSheet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 2.0.0 갈래 L, 검토 07b장(근무표·명세서 인식): 만든 표(실제 사람 없음)로 본 고침. */
class ReviewLScanTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)),
        DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true),
        DutyType(4, "오프", "O", true),
    ))

    /** SCAN-02: a 근무표 is days to come; a date without its year is the nearest either way, 근태 (days gone by) keeps its rule. */
    @Test fun `a roster's dates without a year are the coming ones`() {
        val nov1 = LocalDate.of(2026, 11, 1)
        assertEquals(LocalDate.of(2026, 12, 2), AttendanceImport.parseDate("12/2", 2026, nov1, ahead = true))
        assertEquals(LocalDate.of(2026, 12, 31), AttendanceImport.parseDate("12월 31일", 2026, nov1, ahead = true))
        assertEquals(LocalDate.of(2027, 1, 5), AttendanceImport.parseDate("1/5", 2026, nov1, ahead = true))
        assertEquals(LocalDate.of(2027, 1, 3), AttendanceImport.parseDate("1/3", 2026, LocalDate.of(2026, 12, 20), ahead = true))
        // 근태: a record of a day gone by is never far ahead.
        assertEquals(LocalDate.of(2025, 12, 31), AttendanceImport.parseDate("12/31", 2026, nov1))
    }

    /** SCAN-02 재현 1: on 11월 10일 (the screen on November) a December file with 12/1~12/31 in its head row goes in this December. */
    @Test fun `a December file read in November is this December`() {
        val head = listOf("이름") + (1..31).map { "12/$it" }
        val kim = listOf("김간호") + (1..31).map { listOf("D", "E", "N", "O")[it % 4] }
        val read = RosterSheet.read(listOf(head, kim), codes, near = LocalDate.of(2026, 11, 1))!!
        val days = read.people.single().days
        assertEquals(31, days.size)
        assertEquals(LocalDate.of(2026, 12, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 12, 31), days.last().date)
        assertEquals(DutyCell.Shift(2), days.first().cell)
    }

    /** SCAN-02 재현 2: a 세로형 list of December read on 11월 20일 at the first setup, 12/21~12/31 too. */
    @Test fun `a list down the page keeps the whole month in one year`() {
        val rows = listOf(listOf("날짜", "근무")) + (1..31).map { listOf("12/$it(화)", "D") }
        val read = RosterSheet.read(rows, codes, near = LocalDate.of(2026, 11, 20))!!
        assertEquals((1..31).map { LocalDate.of(2026, 12, it) }, read.people.single().days.map { it.date })
    }

    /** SCAN-08: a scanned PDF of two pages (1~15일, 16~31일): one line a name with both halves; a day read twice differently is looked at. */
    @Test fun `the two halves of a scanned roster make one line`() {
        val first = listOf(
            RosterRow("김간호", (1..15).associateWith { DutyCell.Shift(1) }),
            RosterRow("이간호", (1..15).associateWith { DutyCell.Shift(2) }),
        )
        val second = listOf(
            RosterRow("김 간호", (15..31).associateWith { DutyCell.Shift(3) }, guessed = setOf(20)),
            RosterRow("박간호", (16..31).associateWith { DutyCell.Shift(4) }),
            RosterRow("", mapOf(1 to DutyCell.Shift(1))),
            RosterRow("", mapOf(2 to DutyCell.Shift(1))),
        )
        val rows = DutyRoster.joinPages(first + second)
        assertEquals(listOf("김간호", "이간호", "박간호", "", ""), rows.map { it.label })
        val kim = rows.first()
        assertEquals((1..31).toList(), kim.cells.keys.sorted())
        assertEquals("겹친 날은 첫 쪽 것", DutyCell.Shift(1), kim.cells[15])
        assertEquals(DutyCell.Shift(3), kim.cells[16])
        assertEquals("겹쳐 다르게 읽은 날은 노랗게", setOf(15), kim.contested)
        assertEquals(setOf(20), kim.guessed)
    }

    /** SCAN-30·T07b-09: a sentence that starts with the pay's name but is about something else is not read as the pay. */
    @Test fun `only the pay's own amount is read as the pay`() {
        fun bases(text: String) = com.bamnat.core.pay.RuleText.read(text).map { it.change }.filterIsInstance<com.bamnat.core.pay.RuleChange.Base>()
        assertEquals(emptyList<Any>(), bases("기본급에는 고정연장수당 300,000원이 포함된다"))
        assertEquals(emptyList<Any>(), bases("기본급 외에 식대 200,000원을 지급한다"))
        assertEquals(emptyList<Any>(), bases("시급제 직원은 식대 5,000원을 받는다"))
        assertEquals(emptyList<Any>(), bases("일당직은 교통비 10,000원"))
        assertEquals(listOf(com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.MONTHLY, 2_508_000)), bases("기본급은 월 2,508,000원"))
        assertEquals(listOf(com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.HOURLY, 10_320)), bases("시급: 10,320원"))
        assertEquals(listOf(com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.DAILY, 150_000)), bases("일당(1일) 150,000원"))
    }

    /** SCAN-30·U07b-12: a pay that changes its kind or falls below half is said against today's, and starts off. */
    @Test fun `a big change of the pay is said and left off`() {
        val now = com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.MONTHLY, 2_508_000)
        val hourly = com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.HOURLY, 10_320)
        val small = com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.MONTHLY, 300_000)
        assertEquals("지금 월급 2,508,000원 → 시급 10,320원으로 바뀌어요. 맞을 때만 켜 주세요", com.bamnat.core.pay.RuleSheet.caution(hourly, now))
        assertEquals("지금 월급 2,508,000원 → 300,000원으로 크게 줄어요. 맞을 때만 켜 주세요", com.bamnat.core.pay.RuleSheet.caution(small, now))
        assertEquals(null, com.bamnat.core.pay.RuleSheet.caution(com.bamnat.core.pay.RuleChange.Base(com.bamnat.core.pay.WageType.MONTHLY, 2_600_000), now))
        assertEquals("처음 넣는 급여는 견줄 것이 없어요", null, com.bamnat.core.pay.RuleSheet.caution(hourly, null))
        val sheet = com.bamnat.core.pay.RuleSheet.read(listOf(com.bamnat.core.pay.RuleFinding("시급 10,320원", hourly, 0.9)), current = now)
        val cell = sheet.cells.single()
        assertEquals(false, cell.on)
        assertEquals(com.bamnat.core.pay.RuleSheet.caution(hourly, now), cell.caution)
    }

    /** SCAN-06·T07b-04: AM·PM·V·MS·반·생 are asked once, the leave each most often is offered first; written out they are leave. */
    @Test fun `short leave words are asked once`() {
        listOf("AM", "PM", "V", "MS", "HD", "AL", "반", "생", "년", "am").forEach { w ->
            assertEquals(w, false, (codes.match(w) as DutyCell.Unknown).leave)
            assertEquals(w, true, DutyCodes.guessOf(w)?.second is DutyCell.Leave)
        }
        assertEquals("오전 반차" to DutyCell.Leave(quarters = 2, fromStart = true), DutyCodes.guessOf("AM"))
        assertEquals(DutyCell.Leave(), codes.match("연차"))
        assertEquals(DutyCell.Leave(quarters = 2, fromStart = true), codes.match("오전반차"))
        // A calendar whose own kind is called AM keeps it; an answer once given is kept.
        val own = DutyCodes(listOf(DutyType(9, "AM", "AM", false, LocalTime.of(7, 0))), learned = mapOf("V" to DutyCell.Off))
        assertEquals(DutyCell.Shift(9), own.match("AM"))
        assertEquals(DutyCell.Off, own.match("V"))
        // On a roster read as text, the day with V waits for the answer instead of going in as 연차.
        val row = DutyRoster.readText("이름 1 2 3\n김간호 D V N", codes).first()
        assertEquals(DutyCell.Unknown("V"), row.cells[2])
    }

    private fun photo(header: List<String>, lines: List<Pair<String, List<String>>>): List<com.bamnat.core.imports.RosterWord> {
        val w = mutableListOf<com.bamnat.core.imports.RosterWord>()
        val x0 = 260; val cw = 60; val rh = 50; val y0 = 150
        header.forEachIndexed { i, d -> w += com.bamnat.core.imports.RosterWord(d, x0 + i * cw + 18, y0 + 12, x0 + i * cw + 42, y0 + 40) }
        lines.forEachIndexed { r, (name, cells) ->
            val y = y0 + rh * (r + 1)
            w += com.bamnat.core.imports.RosterWord(name, 60, y + 12, 140, y + 40)
            cells.forEachIndexed { i, c -> if (c.isNotEmpty()) w += com.bamnat.core.imports.RosterWord(c, x0 + i * cw + 18, y + 12, x0 + i * cw + 42, y + 40) }
        }
        return w
    }

    /** SCAN-07: two people of one name on a photo stay two ("김민지", "김민지 (2)"), and my line is asked, never taken by name. */
    @Test fun `two people of one name stay two`() {
        val days = (1..14).map { "$it" }
        val a = (1..14).map { listOf("D", "E", "N", "O")[it % 4] }
        val b = (1..14).map { listOf("N", "O", "D", "E")[it % 4] }
        val rows = DutyRoster.read(photo(days, listOf("김민지" to a, "이간호" to a, "김민지" to b)), codes)
        assertEquals(listOf("김민지", "김민지 (2)", "이간호"), rows.map { it.label }.sorted())
        assertEquals(codes.match(b[0]), rows.first { it.label == "김민지 (2)" }.cells[1])
        assertEquals(true, DutyRoster.twins(rows, "김민지"))
        assertEquals(false, DutyRoster.twins(rows, "이간호"))
        // A file too: two lines of one name whose days meet are two people; halves that do not are one.
        val file = RosterSheet.read(listOf(listOf("이름") + days, listOf("김민지") + a, listOf("김민지") + b), codes, java.time.YearMonth.of(2026, 11))!!
        assertEquals(listOf("김민지", "김민지 (2)"), file.people.map { it.name })
    }

    /** SCAN-09: a file in two blocks (1~15일 above, 16~31일 below) reads each block under its own row of days, one line a name. */
    @Test fun `a sheet of two blocks reads each under its own days`() {
        val top = listOf("이름") + (1..15).map { "$it" }
        val bottom = listOf("이름") + (16..30).map { "$it" } + ""
        val sheet = listOf(
            listOf("2026년 11월 근무표"),
            top, listOf("김간호") + (1..15).map { "D" }, listOf("이간호") + (1..15).map { "E" },
            listOf("합계") + (1..15).map { listOf("1", "2", "1", "2")[it % 4] },
            bottom, listOf("김간호") + (16..30).map { "N" } + "", listOf("이간호") + (16..30).map { "O" } + "",
        )
        val read = RosterSheet.read(sheet, codes, java.time.YearMonth.of(2026, 11))!!
        val kim = read.people.single { it.name == "김간호" }
        assertEquals((1..30).map { LocalDate.of(2026, 11, it) }, kim.days.map { it.date })
        assertEquals(codes.match("N"), kim.days.last().cell)
        assertEquals(codes.match("D"), kim.days.first().cell)
        assertEquals(listOf("김간호", "이간호"), read.people.map { it.name })
    }

    /** SCAN-10·T07b-05: a 4주 듀티 photo from 10/26: the days before the 1st are October's, never part of the name. */
    @Test fun `a four week photo keeps last month's days apart`() {
        val header = (26..31).map { "$it" } + (1..22).map { "$it" }
        val kim = (26..31).map { "N" } + (1..22).map { listOf("D", "E", "N", "O")[it % 4] }
        val row = DutyRoster.read(photo(header, listOf("김간호" to kim, "이간호" to kim.reversed())), codes).first { it.label.startsWith("김") }
        assertEquals("김간호", row.label)
        assertEquals((1..22).toList(), row.cells.keys.sorted())
        val placed = row.placed(java.time.YearMonth.of(2026, 11))
        assertEquals((26..31).map { LocalDate.of(2026, 10, it) }, placed.outside.keys.sorted())
        assertEquals(codes.match("N"), placed.outside[LocalDate.of(2026, 10, 26)])
        // One code beside a name (a 조 mark) is not a day of the month before.
        val one = DutyRoster.read(photo(listOf("") + (1..14).map { "$it" }, listOf("박간호" to (listOf("D") + (1..14).map { "E" }))), codes).single()
        assertEquals(emptyMap<Int, DutyCell>(), one.before)
    }

    /** SCAN-11: the day it was written (작성일 2026.10.25) is no month; "11월 근무표" is, and a span's most days. */
    @Test fun `the roster's month is its title's, not the day it was written`() {
        val near = LocalDate.of(2026, 10, 28)
        val nov = java.time.YearMonth.of(2026, 11)
        assertEquals(nov, DutyRoster.monthIn("작성일 2026.10.25\n11월 근무표\n이름 1 2 3", near))
        assertEquals(nov, DutyRoster.monthIn("2026년 10월 25일 작성 · 7병동 11월 듀티", near))
        assertEquals(nov, DutyRoster.monthIn("2026-10-25 2026.11", near))
        assertEquals(nov, DutyRoster.monthIn("근무 기간 2026.10.26 ~ 2026.11.22", near))
        assertEquals(nov, DutyRoster.monthIn("2026년 11월", near))
        assertEquals(null, DutyRoster.monthIn("작성일 2026.10.25", near))
        assertEquals(nov, RosterSheet.monthIn(listOf(listOf("작성일", "2026.10.25"), listOf("11월 근무표")), near))
    }

    /** SCAN-14·T07b-06: a 출퇴근 file of many people is read for the one picked; a day of several rows is one. */
    @Test fun `an attendance file of many people is read for one`() {
        val rows = listOf(
            listOf("일자", "성명", "출근", "퇴근", "근무시간", "구분"),
            listOf("2026-11-02", "김사장", "09:00", "18:00", "8", ""),
            listOf("2026-11-02", "이알바", "10:00", "15:00", "5", ""),
            listOf("2026-11-03", "이알바", "", "", "", "연차"),
            listOf("2026-11-03", "김사장", "09:00", "12:00", "3", ""),
            listOf("2026-11-03", "김사장", "13:00", "19:30", "6.5", ""),
            listOf("2026-11-04", "김사장", "22:00", "07:00", "8", ""),
        )
        val map = AttendanceImport.guess(rows)!!
        assertEquals(1, map.columns[com.bamnat.core.imports.AttendanceField.NAME])
        assertEquals(listOf("김사장", "이알바"), AttendanceImport.people(rows, map))
        val kim = AttendanceImport.read(rows, map, 2026, who = "김사장")
        assertEquals(3, kim.size)
        val third = kim.single { it.date == LocalDate.of(2026, 11, 3) }
        assertEquals(LocalTime.of(9, 0), third.start)
        assertEquals(LocalTime.of(19, 30), third.end)
        assertEquals(9 * 60 + 30, third.workMinutes)
        assertEquals("이알바의 연차는 김사장 달력에 없음", null, third.leave)
        assertEquals(LocalTime.of(22, 0), kim.last().start)
        assertEquals(1, AttendanceImport.sharedDays(rows, map, 2026, who = "김사장"))
        assertEquals(com.bamnat.core.model.Leave(com.bamnat.core.model.LeaveKind.ANNUAL), AttendanceImport.read(rows, map, 2026, who = "이알바").last().leave)
        // A night shift split in two keeps its 출근 at night.
        val night = AttendanceImport.oneDay(listOf(
            com.bamnat.core.imports.AttendanceRow(LocalDate.of(2026, 11, 5), LocalTime.of(3, 0), LocalTime.of(7, 0)),
            com.bamnat.core.imports.AttendanceRow(LocalDate.of(2026, 11, 5), LocalTime.of(22, 0), LocalTime.of(2, 0)),
        ))
        assertEquals(LocalTime.of(22, 0), night.start)
        assertEquals(LocalTime.of(7, 0), night.end)
    }

    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> parts.forEach { (n, t) -> z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    /** SCAN-16·T07b-10·I07bb-03: a roster made in Word or 한글 (.docx, .hwpx) is read from its table, merged cells filled. */
    @Test fun `a roster in a Word or 한글 document is read from its table`() {
        fun wc(t: String, span: Int = 1) = "<w:tc><w:tcPr>" + (if (span > 1) "<w:gridSpan w:val=\"$span\"/>" else "") + "</w:tcPr><w:p><w:r><w:t>$t</w:t></w:r></w:p></w:tc>"
        val docx = zip("[Content_Types].xml" to "<Types/>", "word/document.xml" to
            "<w:document><w:body><w:p><w:r><w:t>11월 근무표</w:t></w:r></w:p><w:tbl><w:tblPr/>" +
            "<w:tr>" + wc("이름") + (1..7).joinToString("") { wc("$it") } + "</w:tr>" +
            "<w:tr>" + wc("김간호") + wc("D") + wc("D") + wc("E") + wc("N", 2) + wc("O") + wc("O") + "</w:tr>" +
            "</w:tbl></w:body></w:document>")
        assertEquals(com.bamnat.core.imports.SheetFormats.ZipKind.DOCX, com.bamnat.core.imports.SheetFormats.zipKind(docx))
        val book = com.bamnat.core.imports.Sheets.book(docx)
        assertEquals(listOf("김간호", "D", "D", "E", "N", "N", "O", "O"), book.single().rows[1])
        val kim = RosterSheet.readBook(book, codes, java.time.YearMonth.of(2026, 11))!!.people.single()
        assertEquals(codes.match("N"), kim.days[4].cell)

        fun hc(c: Int, r: Int, t: String, cs: Int = 1) =
            "<hp:tc><hp:subList><hp:p><hp:run><hp:t>$t</hp:t></hp:run></hp:p></hp:subList><hp:cellAddr colAddr=\"$c\" rowAddr=\"$r\"/><hp:cellSpan colSpan=\"$cs\" rowSpan=\"1\"/></hp:tc>"
        val hwpx = zip("mimetype" to "application/hwp+zip", "Contents/section0.xml" to
            "<hs:sec><hp:p><hp:run><hp:tbl rowCnt=\"2\"><hp:tr>" + hc(0, 0, "이름") + (1..7).joinToString("") { hc(it, 0, "$it") } + "</hp:tr><hp:tr>" +
            hc(0, 1, "이간호") + hc(1, 1, "N") + hc(2, 1, "N") + hc(3, 1, "O", 2) + hc(5, 1, "D") + hc(6, 1, "D") + hc(7, 1, "E") + "</hp:tr></hp:tbl></hp:run></hp:p></hs:sec>")
        assertEquals(com.bamnat.core.imports.SheetFormats.ZipKind.HWPX, com.bamnat.core.imports.SheetFormats.zipKind(hwpx))
        assertEquals(listOf("이간호", "N", "N", "O", "O", "D", "D", "E"), com.bamnat.core.imports.Sheets.book(hwpx).single().rows[1])
        // A document with no table says so, not "엑셀 파일".
        val none = runCatching { com.bamnat.core.imports.Sheets.book(zip("word/document.xml" to "<w:document><w:body><w:p/></w:body></w:document>")) }
        assertEquals(true, none.exceptionOrNull()?.message?.contains("표를 찾지 못했어요"))
    }

    /** SCAN-17: a zip whose parts unpack past the limit stops with "너무 커요", whatever it says it is. */
    @Test fun `a zip bomb stops`() {
        val big = "<x>" + "a".repeat(13 * 1024 * 1024) + "</x>"
        val bomb = zip(*Array(4) { "xl/worksheets/sheet$it.xml" to big })
        val e = runCatching { com.bamnat.core.imports.Sheets.book(bomb) }.exceptionOrNull()
        assertEquals("파일이 너무 커요", e?.message)
    }
}
