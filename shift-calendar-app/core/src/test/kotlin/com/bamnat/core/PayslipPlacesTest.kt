package com.bamnat.core

import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipRole
import org.junit.Test
import org.junit.Assert.assertEquals

/** 2.0.0 [RVJ-26] 검토 PAYU-52: 명세서에서 읽은 수당은 몇 개든 모두 맞춰 보기에 들어가고, '몇 줄을 채웠어요'는 넣은 줄 수예요. */
class PayslipPlacesTest {
    private fun allowance(name: String, won: Long) = SlipLine(name, SlipRole.ALLOWANCE, won)

    @Test
    fun `the seventh allowance the app does not have is kept, and the tenth`() {
        val names = listOf("자격수당", "가족수당", "직책수당", "위험수당", "교통비", "통신비", "근속수당", "벽지수당", "정근수당", "특수업무수당")
        val read = PayslipRead(listOf(SlipLine("기본급", SlipRole.BASE, 2_500_000)) + names.mapIndexed { i, n -> allowance(n, 10_000L * (i + 1)) })
        val places = read.places(items = listOf("meal" to "식대"))
        assertEquals(names, places.others.map { it.first })
        assertEquals(550_000L, places.others.sumOf { it.second })
        assertEquals(emptyMap<String, Long>(), places.known)
    }

    @Test
    fun `an allowance the app has fills that one, spaces and case aside`() {
        val read = PayslipRead(listOf(allowance("식 대", 200_000), allowance("자격수당", 50_000), SlipLine("명절상여", SlipRole.BONUS, 300_000)))
        val places = read.places(items = listOf("meal" to "식대", "night" to "야간 간호 수당"))
        assertEquals(mapOf("meal" to 200_000L), places.known)
        assertEquals(listOf("자격수당" to 50_000L, "명절상여" to 300_000L), places.others)
    }

    @Test
    fun `two long names that read the same once cut add up instead of one writing over the other`() {
        val long = "아주아주아주아주아주아주아주아주아주긴이름의수당"
        val read = PayslipRead(listOf(allowance(long + "가", 30_000), allowance(long + "나", 40_000)))
        val places = read.places(items = emptyList(), nameMax = 20)
        assertEquals(listOf(long.take(20) to 70_000L), places.others)
    }

    @Test
    fun `the count is of the lines that went into a field, and what has no field is named`() {
        val read = PayslipRead(listOf(
            SlipLine("기본급", SlipRole.BASE, 2_500_000),
            SlipLine("연장근로수당", SlipRole.OVERTIME, 288_000, minutes = 960),
            SlipLine("주휴수당", SlipRole.WEEKLY_HOLIDAY, 400_000),
            SlipLine("특근수당", SlipRole.EXTRA_WORK, 120_000),
            allowance("식대", 200_000),
            SlipLine("국민연금", SlipRole.DEDUCTION, 112_500),
            SlipLine("지급총액", SlipRole.GROSS, 3_508_000),
            SlipLine("실수령액", SlipRole.NET, 3_100_000),
        ))
        // 기본급, 연장, 식대 and the 지급 총액 itself.
        assertEquals(4, read.placedLines)
        assertEquals(listOf("주휴수당", "특근수당"), read.unplaced.map { it.name })
    }

    @Test
    fun `a total that only adds the lines up is not a line put in`() {
        val read = PayslipRead(listOf(SlipLine("기본급", SlipRole.BASE, 2_500_000), allowance("식대", 200_000)))
        assertEquals(2_700_000L, read.gross)
        assertEquals(2, read.placedLines)
    }
}
