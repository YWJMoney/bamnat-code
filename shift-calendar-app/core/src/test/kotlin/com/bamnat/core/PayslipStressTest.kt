package com.bamnat.core

import com.bamnat.core.pay.*
import kotlin.random.Random
import org.junit.Test

/**
 * [READ-07][PAYS-05] 1.25 통합 직접 시험 (사용자 2026-10-25 "명세서를 보내도 제대로 인식하지 못함"): 명세서 글 600가지 —
 * 줄마다·쌍점·이름 다음 금액 칸(세로로 읽힘)·지급과 공제 두 칸 나란히·시간 칸, 머리말 있음/없음, 쉼표가 점으로 읽힘, '원' 없음.
 * 모든 지급 줄·지급 총액·실수령이 맞아야 해요.
 */
class PayslipStressTest {
    data class Item(val name: String, val role: SlipRole, val amount: Long, val hours: Int? = null)

    @Test fun stress() {
        val r = Random(11)
        val tally = sortedMapOf<String, IntArray>()
        var silent = 0
        repeat(600) { n ->
            val earnNames = listOf(
                Item("기본급", SlipRole.BASE, 2_000_000L + r.nextInt(20) * 50_000),
                Item(listOf("연장근로수당", "연장수당", "시간외수당")[r.nextInt(3)], SlipRole.OVERTIME, 100_000L + r.nextInt(900) * 1000, 10 + r.nextInt(40)),
                Item(listOf("야간근로수당", "야간수당")[r.nextInt(2)], SlipRole.NIGHT, 50_000L + r.nextInt(500) * 1000, 20 + r.nextInt(60)),
                Item(listOf("휴일근로수당", "휴일수당", "특근수당")[r.nextInt(3)], SlipRole.HOLIDAY, 80_000L + r.nextInt(300) * 1000, 8 + r.nextInt(16)),
                Item("식대", SlipRole.ALLOWANCE, 200_000),
                Item(listOf("교대수당", "자격수당", "직책수당")[r.nextInt(3)], SlipRole.ALLOWANCE, 50_000L + r.nextInt(20) * 10_000),
            ).filter { it.role == SlipRole.BASE || r.nextInt(10) < 8 }
            val gross = earnNames.sumOf { it.amount }
            val ded = listOf(Item("국민연금", SlipRole.DEDUCTION, gross * 45 / 1000), Item("건강보험", SlipRole.DEDUCTION, gross * 3545 / 100000),
                Item("고용보험", SlipRole.DEDUCTION, gross * 9 / 1000), Item("소득세", SlipRole.DEDUCTION, 40_000L + r.nextInt(100) * 1000),
                Item("지방소득세", SlipRole.DEDUCTION, 4_000L + r.nextInt(10) * 100))
            val dedSum = ded.sumOf { it.amount }
            val layout = listOf("lines", "colon", "columns", "side", "hours")[n % 5]
            val commaNoise = n / 5 % 3 == 1; val noWon = n / 5 % 3 == 2; val header = n % 2 == 0
            fun money(v: Long): String { val s = "%,d".format(v).let { if (commaNoise) it.replace(',', '.') else it }; return if (noWon || layout == "columns") s else s + "원" }
            val text = buildString {
                if (header) { appendLine("2026년 9월분 급여명세서"); appendLine("성명 홍길동   사번 20231234"); appendLine("부서 생산1팀  지급일 2026.10.10") }
                when (layout) {
                    "lines" -> { appendLine("지급 항목"); earnNames.forEach { appendLine("${it.name} ${money(it.amount)}") }; appendLine("지급총액 ${money(gross)}")
                        appendLine("공제 항목"); ded.forEach { appendLine("${it.name} ${money(it.amount)}") }; appendLine("공제총액 ${money(dedSum)}"); appendLine("실수령액 ${money(gross - dedSum)}") }
                    "colon" -> { earnNames.forEach { appendLine("${it.name} : ${money(it.amount)}") }; appendLine("지급합계 : ${money(gross)}")
                        ded.forEach { appendLine("${it.name} : ${money(it.amount)}") }; appendLine("공제합계 : ${money(dedSum)}"); appendLine("차인지급액 : ${money(gross - dedSum)}") }
                    "columns" -> { earnNames.forEach { appendLine(it.name) }; appendLine("지급총액"); earnNames.forEach { appendLine(money(it.amount)) }; appendLine(money(gross))
                        ded.forEach { appendLine(it.name) }; appendLine("공제총액"); ded.forEach { appendLine(money(it.amount)) }; appendLine(money(dedSum)); appendLine("실지급액"); appendLine(money(gross - dedSum)) }
                    "side" -> { val m = maxOf(earnNames.size, ded.size); appendLine("지급내역 공제내역")
                        for (i in 0 until m) appendLine(listOfNotNull(earnNames.getOrNull(i)?.let { "${it.name} ${money(it.amount)}" }, ded.getOrNull(i)?.let { "${it.name} ${money(it.amount)}" }).joinToString("   "))
                        appendLine("지급총액 ${money(gross)}   공제총액 ${money(dedSum)}"); appendLine("실수령액 ${money(gross - dedSum)}") }
                    "hours" -> { earnNames.forEach { appendLine(if (it.hours != null) "${it.name} ${it.hours}시간 ${money(it.amount)}" else "${it.name} ${money(it.amount)}") }
                        appendLine("지급액계 ${money(gross)}"); ded.forEach { appendLine("${it.name} ${money(it.amount)}") }; appendLine("공제액계 ${money(dedSum)}"); appendLine("실수령액 ${money(gross - dedSum)}") }
                }
            }
            val read = PayslipReader.read(text)
            val linesOk = earnNames.all { e -> read.lines.any { (it.role == e.role || e.name == "특근수당" && it.role == SlipRole.EXTRA_WORK) && it.amount == e.amount } }
            val ok = read.gross == gross && linesOk && read.net == gross - dedSum
            for (f in listOf("layout=$layout", "comma=$commaNoise", "noWon=$noWon", "header=$header")) { val t = tally.getOrPut(f) { intArrayOf(0, 0) }; t[1]++; if (ok) t[0]++ }
            if (!ok && read.consistent && read.gross != gross) silent++
        }
        tally.forEach { (k, v) -> org.junit.Assert.assertEquals(k, v[1], v[0]) }
        org.junit.Assert.assertEquals(0, silent)
    }

    /**
     * [READ-16] 1.27 AJ 순번 446: 600 payslips read 4 ways each, every way with its own misreads — a last digit lost
     * (2,508,000 → 250,800), a digit like another (3↔8, 1↔7, 0↔6), a name misread (아간·공재·보형·기본려·한게), a line lost.
     * Put together ([SlipVote]): read clean every way, every line and the total are right and sure; with misreads, no wrong
     * amount is left without a sign — a yellow line, or lines that do not add up to the stated 지급 총액 (조용히 틀린 값 0).
     */
    @Test fun fourReadingsPutTogether() {
        var cleanSure = 0; var cleanCases = 0; var silent = 0; var sureWrong = 0; var onceOk = 0; var voteOk = 0
        repeat(600) { n ->
            val r = Random(5000 + n)
            val earn = listOf(
                Item("기본급", SlipRole.BASE, 2_000_000L + r.nextInt(20) * 50_000),
                Item("연장근로수당", SlipRole.OVERTIME, 100_000L + r.nextInt(900) * 1000),
                Item("야간근로수당", SlipRole.NIGHT, 50_000L + r.nextInt(500) * 1000),
                Item("휴일근로수당", SlipRole.HOLIDAY, 80_000L + r.nextInt(300) * 1000),
                Item("식대", SlipRole.ALLOWANCE, 200_000),
            ).filter { it.role == SlipRole.BASE || r.nextInt(10) < 8 }
            val gross = earn.sumOf { it.amount }
            val ded = listOf(Item("국민연금", SlipRole.DEDUCTION, gross * 45 / 1000), Item("건강보험", SlipRole.DEDUCTION, gross * 3545 / 100000),
                Item("소득세", SlipRole.DEDUCTION, 40_000L + r.nextInt(100) * 1000))
            val dedSum = ded.sumOf { it.amount }
            val side = n % 2 == 1
            fun text(rr: Random?, noisy: Boolean): String = buildString {
                fun m(v: Long): String {
                    var t = "%,d".format(v)
                    if (noisy && rr!!.nextInt(100) < 5) t = t.dropLast(1)
                    if (noisy && rr!!.nextInt(100) < 5) { val i = rr.nextInt(t.length); val c = t[i]; val to = mapOf('3' to '8', '8' to '3', '1' to '7', '7' to '1', '0' to '6', '6' to '0')[c]; if (to != null) t = t.substring(0, i) + to + t.substring(i + 1) }
                    return t + "원"
                }
                fun nm(s: String): String = if (noisy && rr!!.nextInt(100) < 8) mapOf("야간근로수당" to "아간근로수당", "기본급" to "기본려", "건강보험" to "건강보형", "공제총액" to "공재총액", "지급합계" to "지급한게")[s] ?: s else s
                fun keep(): Boolean = !noisy || rr!!.nextInt(100) >= 4
                appendLine("2026년 9월분 급여명세서")
                if (!side) {
                    earn.forEach { if (keep()) appendLine("${nm(it.name)} ${m(it.amount)}") }
                    appendLine("${nm("지급합계")} ${m(gross)}")
                    ded.forEach { if (keep()) appendLine("${nm(it.name)} ${m(it.amount)}") }
                    appendLine("${nm("공제총액")} ${m(dedSum)}"); appendLine("실수령액 ${m(gross - dedSum)}")
                } else {
                    for (i in 0 until maxOf(earn.size, ded.size)) appendLine(listOfNotNull(earn.getOrNull(i)?.takeIf { keep() }?.let { "${nm(it.name)} ${m(it.amount)}" },
                        ded.getOrNull(i)?.let { "${nm(it.name)} ${m(it.amount)}" }).joinToString("   "))
                    appendLine("${nm("지급합계")} ${m(gross)}   ${nm("공제총액")} ${m(dedSum)}"); appendLine("실수령액 ${m(gross - dedSum)}")
                }
            }
            val noisy = n % 3 != 0
            val texts = (0 until 4).map { w -> text(Random(n * 7 + w), noisy) }
            val res = SlipVote.merge(texts)
            fun linesRight(read: PayslipRead) = read.gross == gross && earn.all { e -> read.lines.any { it.role == e.role && it.amount == e.amount } }
            if (linesRight(PayslipReader.read(texts[0]))) onceOk++
            if (linesRight(res.read)) voteOk++
            if (!noisy) { cleanCases++; if (res.sure && linesRight(res.read)) cleanSure++ }
            if (res.sure && !linesRight(res.read)) { sureWrong++; if (sureWrong <= 4) println("#$n sure but wrong: ${res.read.lines}\n${texts.joinToString("\n--\n")}") }
            // A wrong amount with no sign at all: not yellow, and the lines add up to a stated 지급 총액.
            val signless = res.read.consistent && SlipVote.stated(res.read)
            res.read.lines.forEachIndexed { i, l ->
                if (i in res.unsure || !signless) return@forEachIndexed
                val truth = (earn + ded).firstOrNull { it.role == l.role && SlipNames.key(it.name).take(2) == SlipNames.key(l.name).take(2) } ?: return@forEachIndexed
                if (l.amount != truth.amount) silent++
            }
            if (signless && res.read.gross != gross) silent++
        }
        println("payslip 4-way stress: once=$onceOk vote=$voteOk of 600, clean sure $cleanSure/$cleanCases, sureWrong=$sureWrong silent=$silent")
        org.junit.Assert.assertEquals("clean payslips read 4 ways: all right and sure", cleanCases, cleanSure)
        org.junit.Assert.assertEquals("a sure payslip is always right", 0, sureWrong)
        org.junit.Assert.assertEquals("no wrong amount without a sign", 0, silent)
        org.junit.Assert.assertTrue("putting together reads more payslips right", voteOk > onceOk)
    }
}
