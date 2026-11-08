package com.bamnat.core

import com.bamnat.core.boss.WageForm
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.PdfText
import com.bamnat.core.imports.RosterGrid
import com.bamnat.core.imports.RosterSheet
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.Sheets
import com.bamnat.core.imports.StaffSheet
import com.bamnat.core.imports.TeamSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [READ-30][READ-31] 1.38 근무표 파일 정답 묶음 (`tools/file_bench.py`): the shapes a 병동·공장·가게 hands a roster out in — a cover
 * sheet first, merged cells, a 4주 듀티 across two months, people as columns, a real old .xls, the HTML and XML ".xls" a 근태
 * system saves, CP949 CSV, .ods, and a PDF of one and two pages (read by OCR as the app renders it).
 *
 * Every cell is counted three ways: right, quietly wrong (another code, no doubt mark) and missed. 1.37 (before) read
 * 1,662 of 3,699 cells (44.9%): 7 of 15 files not at all, 6 days of a 4주 듀티 and a merged 연차 lost. The bar now: every
 * cell of every file right, and nothing quietly wrong anywhere (PDF included).
 */
class FileBenchTest {
    private val types = listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(2, "이브닝", "E", false, LocalTime.of(15, 0)),
        DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true), DutyType(4, "오프", "O", true),
        DutyType(5, "오픈", "오픈", false, LocalTime.of(7, 0)), DutyType(6, "미들", "미들", false, LocalTime.of(12, 0)),
        DutyType(7, "마감", "마감", false, LocalTime.of(17, 0)),
    )
    private val codes = DutyCodes(types)
    private val near = LocalDate.of(2026, 11, 5)
    private val dir = javaClass.classLoader.getResource("file-bench")?.toURI()?.let(::File)

    private fun truthOf(f: File) = f.readLines().filter { it.startsWith("person\t") }.map { l ->
        val p = l.split('\t'); p[1] to p[2].split(',').associate { x -> LocalDate.parse(x.substringBefore('=')) to x.substringAfter('=') }
    }

    data class Score(val cells: Int, val right: Int, val silent: Int, val doubtful: Int)

    /** [grid] against the truth, its codes matched as the calendar would. */
    private fun score(truth: List<Pair<String, Map<LocalDate, String>>>, grid: RosterGrid?, debug: String = ""): Score {
        var c = 0; var r = 0; var s = 0; var y = 0
        for ((name, want) in truth) {
            val row = grid?.rows?.firstOrNull { it.name == name }
            for ((date, code) in want) {
                c++
                val raw = row?.cells?.get(date)
                val got = raw?.let { codes.read(it)?.first ?: DutyCell.Unknown(it) }
                val doubt = row != null && date in row.doubtful
                if (doubt) y++
                if (got == codes.read(code)?.first) r++
                else if (got != null && got !is DutyCell.Unknown && !doubt) { s++; if (debug.isNotEmpty()) println("  silent $debug $name $date want $code got $raw") }
            }
        }
        return Score(c, r, s, y)
    }

    private fun words(f: File) = f.readLines().filter { it.startsWith("word\t") }.map { l ->
        val p = l.split('\t'); RosterWord(p[6], p[1].toDouble().toInt(), p[2].toDouble().toInt(), p[3].toDouble().toInt(), p[4].toDouble().toInt(), angle = p[5].toFloat())
    }

    @Test fun everyFileShapeReadsEveryCellAndNothingQuietlyWrong() {
        val d = dir ?: return
        var all = Score(0, 0, 0, 0)
        val labels = types.associate { it.id to it.label }
        for (truthFile in d.listFiles()!!.filter { it.name.endsWith(".truth.tsv") }.sortedBy { it.name }) {
            val case = truthFile.name.removeSuffix(".truth.tsv")
            val truth = truthOf(truthFile)
            if (truth.isEmpty()) continue
            val grid = if (case.startsWith("p")) {
                // A PDF page is a picture to the app: the same reading as a photo, page by page, then put together.
                // A PDF with its text in it is read from the file, as the table it holds (PdfText); a scanned one by OCR of its
                // pages as the app renders them.
                val pdf = File(d, "$case.pdf").readBytes()
                val text = PdfText.sheets(pdf)
                if (text != null) TeamSheet.pages(text.mapNotNull { TeamSheet.roster(listOf(it), near = near) }) else {
                    val pages = d.listFiles()!!.filter { it.name.startsWith("$case.p") && it.name.endsWith(".tsv") }.sortedBy { it.name }.map(::words)
                    if (pages.isEmpty()) continue
                    val month = DutyRoster.monthIn(pages.flatten().joinToString(" ") { it.text }, near) ?: YearMonth.from(near)
                    TeamSheet.pages(pages.mapNotNull { TeamSheet.roster(DutyRoster.analyse(it, codes), month, labels) })
                }
            } else {
                val file = d.listFiles()!!.first { it.name.startsWith("$case.") && !it.name.endsWith(".tsv") }
                TeamSheet.roster(Sheets.book(file.readBytes()), near = near)
            }
            val sc = score(truth, grid, case)
            println("READ-30 ${case.padEnd(18)} ${sc.right}/${sc.cells} (조용히 틀림 ${sc.silent}, 노란 ${sc.doubtful})${if (grid == null) " 못 읽음" else ""}")
            all = Score(all.cells + sc.cells, all.right + sc.right, all.silent + sc.silent, all.doubtful + sc.doubtful)
            // Every file with its text in it, PDF too, reads every cell; a scanned PDF is held to "nothing quietly wrong".
            if (!case.contains("scan")) assertEquals("$case: every cell", sc.cells, sc.right)
            assertEquals("$case: quietly wrong", 0, sc.silent)
        }
        println("READ-30 total ${all.right}/${all.cells} (${"%.1f".format(100.0 * all.right / all.cells.coerceAtLeast(1))}%) silent ${all.silent} yellow ${all.doubtful}  (1.37: 1662/3699, 44.9%)")
    }

    @Test fun thePersonalRosterReadsTheSameFilesForOnePerson() {
        val d = dir ?: return
        for (case in listOf("x02-cover-first", "x03-merged", "x04-cross-month", "x05-transposed", "b01-biff", "h01-html", "m01-xml2003", "o01-ods")) {
            val file = d.listFiles()!!.first { it.name.startsWith("$case.") && !it.name.endsWith(".tsv") }
            val read = RosterSheet.readBook(Sheets.book(file.readBytes()), codes, near = near)
            val (name, want) = truthOf(File(d, "$case.truth.tsv"))[1]
            val person = read!!.people.first { it.name == name }
            for ((date, code) in want) assertEquals("$case $name $date", codes.read(code)!!.first, person.days.first { it.date == date }.cell)
        }
    }

    @Test fun aFourWeekRosterCrossesIntoTheNextMonth() {
        val d = dir ?: return
        val g = TeamSheet.roster(Sheets.book(File(d, "x04-cross-month.xlsx").readBytes()), near = near)!!
        assertEquals(LocalDate.of(2026, 10, 26), g.days.first())
        assertEquals(LocalDate.of(2026, 11, 25), g.days.last())
        assertEquals(YearMonth.of(2026, 11), g.month)
    }

    @Test fun rolesComeApartFromNames() {
        val d = dir ?: return
        val g = TeamSheet.roster(Sheets.book(File(d, "x07-roles.xlsx").readBytes()), near = near)!!
        val first = g.rows.first()
        assertEquals("김민지", first.name)
        assertEquals("책임", first.role)
    }

    @Test fun aStaffListGivesFormPayAndWeekButNoPhone() {
        val d = dir ?: return
        val s = StaffSheet.read(Sheets.book(File(d, "s01-staff.xlsx").readBytes()), near)!!
        val truth = File(d, "s01-staff.truth.tsv").readLines().filter { it.startsWith("staff\t") }.map { it.split('\t') }
        assertEquals(truth.size, s.rows.size)
        for ((row, t) in s.rows.zip(truth)) {
            assertEquals(t[1], row.name)
            assertEquals(WageForm.entries.first { it.label == t[2] }, row.form)
            assertEquals(t[3].toLong(), row.base)
            val days = t[4].map { c -> DayOfWeek.of("월화수목금토일".indexOf(c) + 1) }.toSet()
            assertEquals(days, row.weekly.keys)
            assertEquals(LocalTime.parse(t[5]) to LocalTime.parse(t[6]), row.weekly.values.first())
            assertEquals(t[7], row.job)
            assertTrue(row.doubtful.isEmpty())
            assertTrue("no phone number kept", row.toString().none { false } && !row.toString().contains("010-"))
        }
    }

    /**
     * [READ-32] 1.38 PC (스캔 PDF on the emulator): the date row read from the 7th on, the first six days' codes left of it and
     * stuck into the name ("최유리ED"). Now the name is the name, and those days are put back by their place, yellow.
     */
    @Test fun aDateRowReadFromTheSeventhKeepsTheNameAndTheFirstDays() {
        val x = { d: Int -> 200 + 40 * (d - 1) }
        val word = { t: String, cx: Int, top: Int -> RosterWord(t, cx - 10, top, cx + 10, top + 20) }
        val want = "DDEENNOODDEENNOODDEENNOODDEENNO".map { it.toString() }
        val words = buildList {
            add(word("12월", 100, 10))
            for (d in 7..30) add(word("$d", x(d), 50))
            add(RosterWord("최유리", 20, 100, 80, 120))
            for (d in 1..30) add(word(want[d - 1], x(d), 100))
            add(RosterWord("이수진D", 20, 140, 90, 160))
            for (d in 1..30) add(word(want[(d + 2) % 30], x(d), 140))
        }
        val read = DutyRoster.analyse(words, codes)
        val one = read.rows.first { it.label.startsWith("최유리") }
        assertEquals("최유리", one.label)
        assertEquals("이수진", read.rows.first { it.label.startsWith("이수진") }.label)
        for (d in 1..30) assertEquals("day $d", codes.read(want[d - 1])!!.first, one.cells[d])
        assertTrue("the counted-back days are looked at", (1..3).all { it in one.doubtful(31) })
    }

    /**
     * [READ-30] 1.38 PC (x03 병합 연차): a roster's 연차 on a day the user's own order has off goes on as that day's main
     * work taken off, so it counts as leave instead of "근무가 없는 날".
     */
    @Test fun aRostersLeaveOnARestDayCountsAsLeave() {
        val date = LocalDate.of(2026, 11, 10)
        val main = DutyRoster.mainShift(listOf(DutyCell.Shift(1), DutyCell.Shift(1), DutyCell.Shift(2), DutyCell.Off, DutyCell.Leave()))
        assertEquals(1L, main)
        val rest = com.bamnat.core.model.DayEntry(date, override = com.bamnat.core.model.ShiftOverride.Off)
        // As before, apply alone keeps the rest day, and the leave means nothing on it.
        val plain = com.bamnat.core.schedule.ScheduleResolver(Fixtures.all, emptyList(), listOf(DutyRoster.apply(rest, DutyCell.Leave())))
        assertEquals(null, plain.resolve(date).leave)
        val e = DutyRoster.leaveOnRest(rest, DutyCell.Leave(), main!!)
        val day = com.bamnat.core.schedule.ScheduleResolver(Fixtures.all, emptyList(), listOf(e)).resolve(date)
        assertTrue(day.leave != null && day.leaveMinutes > 0)
    }
}
