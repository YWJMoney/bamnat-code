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
}
