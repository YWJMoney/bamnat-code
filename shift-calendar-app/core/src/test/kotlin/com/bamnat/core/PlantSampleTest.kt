package com.bamnat.core

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.OcrRows
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.PremiumTarget
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipHourly
import com.bamnat.core.pay.SlipLexicon
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipReading
import com.bamnat.core.pay.SlipVote
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ: 사용자가 보낸 두 장(화학 공장 현장직 명세서와 계산 방법)을 ML Kit가 돌려주는 모양([PlantSample])으로 읽고 정답과
 * 견줘요. 깨끗하게 읽힌 것, 잡음(0↔O, 쉼표 빠짐·점, 로마 숫자 Ⅰ·Ⅱ 오독, 워터마크 글자, 상자 흔들림)을 넣은 것 모두 100%여야 해요.
 */
class PlantSampleTest {
    private fun key(s: String) = PayslipReader.ocrFix(s.filter { it.isLetter() }).take(2)

    private fun hit(l: SlipLine, item: PlantSample.Item): Boolean {
        if (l.amount != item.amount || l.role !in item.role) return false
        if (item in PlantSample.TOTALS) return true
        val name = SlipLexicon.of(l.name)?.takeIf { it.distance > 0 }?.name ?: l.name
        return key(name) == key(item.name)
    }

    class Score { var items = 0; var hit = 0; var hours = 0; var hoursHit = 0; var silent = 0; var pages = 0; var allRight = 0 }

    private fun score(s: Score, res: SlipVote.Result, tag: String) {
        val lines = res.read.lines
        s.pages++
        val miss = PlantSample.ALL.filter { item -> lines.none { hit(it, item) } }
        s.items += PlantSample.ALL.size
        s.hit += PlantSample.ALL.size - miss.size
        for ((name, minutes) in PlantSample.HOURS) {
            s.hours++
            val item = PlantSample.ALL.first { it.name == name }
            if (lines.any { hit(it, item) && it.minutes == minutes }) s.hoursHit++
            else println("  HOURS MISS $tag $name want $minutes got ${lines.filter { hit(it, item) }.map { it.minutes }}")
        }
        lines.forEachIndexed { i, l ->
            if (l.amount == null || i in res.unsure) return@forEachIndexed
            if (PlantSample.ALL.none { hit(l, it) }) { s.silent++; println("  SILENT $tag ${l.role} ${l.name} ${l.amount}") }
        }
        if (miss.isEmpty()) s.allRight++
        else println("  MISS $tag ${miss.map { it.name }}\n    got ${lines.map { "${it.role}:${it.name}:${it.amount}:${it.minutes}" }}")
    }

    private val NOISY = PlantSample.Noise(digit = 0.06, comma = 0.15, roman = 0.5, mark = 0.7, jitter = 6, lose = 0.0)

    @Test fun payslipPhoto() {
        val clean = Score()
        val noisy = Score()
        val once = Score()
        for (lines in listOf(false, true)) {
            val r = Random(if (lines) 2 else 1)
            score(clean, SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN, r, lines))), "clean lines=$lines")
            score(clean, SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN.copy(mark = 1.0), r, lines))), "mark lines=$lines")
        }
        for (seed in 0 until 40) {
            val lines = seed % 2 == 1
            val reads = (0 until 3).map { w -> PlantSample.payslip(NOISY, Random(seed * 31 + w), lines) }
            score(noisy, SlipReading.read(reads), "noisy#$seed lines=$lines")
            score(once, SlipReading.read(reads.take(1)), "once#$seed")
        }
        fun pct(a: Int, b: Int) = "%.1f%%".format(100.0 * a / b.coerceAtLeast(1))
        for ((name, s) in listOf("깨끗함" to clean, "잡음 3번 읽기" to noisy, "잡음 1번 읽기" to once)) {
            println("표본 명세서 $name: 항목 ${s.hit}/${s.items} (${pct(s.hit, s.items)}), 시간 ${s.hoursHit}/${s.hours}, 다 맞은 장 ${s.allRight}/${s.pages}, 조용히 틀림 ${s.silent}")
        }
        assertEquals("깨끗한 사진 항목", clean.items, clean.hit)
        assertEquals("깨끗한 사진 시간", clean.hours, clean.hoursHit)
        assertEquals("잡음 사진 항목", noisy.items, noisy.hit)
        assertEquals("잡음 사진 시간", noisy.hours, noisy.hoursHit)
        assertEquals("조용히 틀린 줄", 0, noisy.silent + clean.silent)
    }

    /** The ten rows of the 계산 방법 table: what each must become. */
    private enum class Want { ENGINE, BY_HAND, EXPLAIN }

    private val ROWS = listOf(
        "휴일근로수당" to Want.ENGINE, "평일연장수당" to Want.ENGINE, "휴일연장수당" to Want.ENGINE, "야간근로수당" to Want.ENGINE,
        "보상휴가금액" to Want.EXPLAIN, "휴가보상" to Want.BY_HAND, "이전 미공제액" to Want.EXPLAIN, "다음 공제 예정액" to Want.EXPLAIN,
        "기준급" to Want.EXPLAIN, "통상임금" to Want.ENGINE,
    )

    private fun want(change: RuleChange): String? = when (change) {
        RuleChange.Premium(PremiumTarget.HOLIDAY, 0.5, false) -> "휴일근로수당"
        RuleChange.Premium(PremiumTarget.OVERTIME, 0.5, false) -> "평일연장수당"
        RuleChange.Premium(PremiumTarget.HOLIDAY, 1.0, true) -> "휴일연장수당"
        RuleChange.Premium(PremiumTarget.NIGHT, 0.5, false) -> "야간근로수당"
        is RuleChange.OrdinaryItems -> if (change.names.containsAll(listOf("기본급", "직책수당", "자격수당", "기술전수교육수당")) && change.names.size == 4) "통상임금" else null
        RuleChange.TaxFamily(3, 0) -> "세금"
        else -> null
    }

    @Test fun rulesPhoto() {
        var rows = 0
        var rowsRight = 0
        var engine = 0
        var family = 0
        var pages = 0
        val noisy = PlantSample.Noise(mark = 0.8, jitter = 6)
        for (seed in 0 until 20) {
            val noise = if (seed < 2) PlantSample.Noise.CLEAN else noisy
            val text = OcrRows.join(PlantSample.rules(noise, Random(seed), lines = seed % 2 == 0))
            val found = RuleText.read(text)
            val unread = RuleText.unread(text)
            pages++
            val got = found.map { want(it.change) }
            val wrong = found.filterIndexed { i, _ -> got[i] == null }
            if (wrong.isNotEmpty()) println("  WRONG #$seed ${wrong.map { "${it.change.summary} <- ${it.sourceLine}" }}")
            if ("세금" in got) family++ else println("  NO TAX FAMILY #$seed\n" + text.lines().take(12).joinToString("\n"))
            for ((row, w) in ROWS) {
                rows++
                val ok = when (w) {
                    Want.ENGINE -> row in got
                    Want.BY_HAND -> unread.size == 1 && unread.single().contains("해당일수")
                    Want.EXPLAIN -> true
                } && wrong.isEmpty() && unread.size == 1
                if (ok) rowsRight++ else println("  ROW $row #$seed found=${found.map { it.change.summary }} unread=$unread")
                if (w == Want.ENGINE && row in got) engine++
            }
            if (seed == 0) {
                println("표본 계산 방법 (깨끗함) 읽은 규칙:")
                found.forEach { println("  ${it.change.summary}  <- ${it.sourceLine}") }
                unread.forEach { println("  직접 넣기: $it") }
            }
        }
        println("표본 계산 방법 ${pages}장: 10줄 중 맞게 다룬 줄 $rowsRight/$rows, 엔진 설정이 된 줄 $engine/${pages * 5} (규칙 5줄), 세금 가족 $family/$pages")
        assertEquals(rows, rowsRight)
        assertEquals(pages, family)
    }

    @Test fun hourlyFromThePayslip() {
        val read = SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN, Random(1)))).read
        val fit = SlipHourly.of(read)!!
        assertEquals(21_618.04, fit.hourly, 1e-9)
        assertEquals(SlipHourly.Cut.UP, fit.cut)
        assertEquals(21_618, fit.won)
        assertEquals(3, fit.lines.size)
        val f = SlipHourly.finding(read)!!
        assertEquals("명세서로 보면 통상시급 21,618원 (원 미만 올림)", f.change.summary)
        // Next month worked out on it: the three lines to the won.
        assertEquals(2_626_592, PayCalculator.lineWon(81 * 60, fit.hourly, 1.5, up = true))
        assertEquals(1_383_555, PayCalculator.lineWon(32 * 60, fit.hourly, 2.0, up = true))
        assertEquals(691_778, PayCalculator.lineWon(64 * 60, fit.hourly, 0.5, up = true))
        // Rounded half up the night line is a won short: the payslip's own lines say 올림.
        assertEquals(691_777, PayCalculator.lineWon(64 * 60, fit.hourly, 0.5, up = false))
        // 시수 from 통상임금 when only the 시급 is known: 통상임금 ÷ 시급.
        println("표본 통상시급: ${fit.hourly}원 (${fit.cut.label}), 줄 ${fit.lines.map { it.name }}")
    }

    /** The month on the calendar: 81 hours of 휴일 work within 8 hours a day, 32 past 8, 64 at night, on 21,618.04원 올림. */
    @Test fun nextMonthReproducesThePayslip() {
        fun t(h: Int) = LocalTime.of(h, 0)
        val day = ShiftType(10, "주간", "주", 0xFF000000, t(9), t(18), 60, t(12))
        val long = ShiftType(14, "휴일 12시간", "H", 0xFF000000, t(8), t(21), 60, t(12))
        val one = ShiftType(15, "휴일 1시간", "h", 0xFF000000, t(9), t(10), 0)
        val night = ShiftType(13, "야간", "야", 0xFF000000, t(22), t(6), 0)
        val off = ShiftType(11, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
        fun d(n: Int) = LocalDate.of(2026, 11, n)
        // 일요일 1·8·15·22·29 and 토요일 7·14·21 are the 8 long holiday days; 28 (토) and 30 (월, 휴일로) 8시간, 27 (금, 휴일로) 1시간.
        val holidays = setOf(1, 7, 8, 14, 15, 21, 22, 29, 28, 30, 27).map(::d).toSet()
        val entries = listOf(1, 7, 8, 14, 15, 21, 22, 29).map { DayEntry(d(it), ShiftOverride.Shift(long.id)) } +
            listOf(28, 30).map { DayEntry(d(it), ShiftOverride.Shift(day.id)) } + DayEntry(d(27), ShiftOverride.Shift(one.id)) +
            listOf(2, 3, 4, 5, 9, 10, 11, 12).map { DayEntry(d(it), ShiftOverride.Shift(night.id)) }
        val resolver = ScheduleResolver(listOf(day, long, one, night, off),
            listOf(WorkPattern.Rotation(LocalDate.of(2026, 10, 26), List(5) { day.id } + List(2) { off.id })), entries)
        val read = SlipReading.read(listOf(PlantSample.payslip(PlantSample.Noise.CLEAN, Random(1)))).read
        val base = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2020, 1, 1), 2_555_830))))
        val profile = RuleText.apply(base, listOf(SlipHourly.finding(read)!!.change), YearMonth.of(2026, 11))
        val pay = PayCalculator().calculate(profile, YearMonth.of(2026, 11), resolver::resolve, isHoliday = { it in holidays })
        pay.lines.filter { it.kind in setOf(LineKind.HOLIDAY, LineKind.NIGHT, LineKind.OVERTIME) }.forEach { println("  ${it.label} ${it.minutes} ${it.multiplier} ${it.amount} ${it.formula}") }
        assertEquals(2_626_592 + 1_383_555, pay.lines.filter { it.kind == LineKind.HOLIDAY }.sumOf { it.amount })
        assertEquals(691_778, pay.lines.filter { it.kind == LineKind.NIGHT }.sumOf { it.amount })
    }
}
