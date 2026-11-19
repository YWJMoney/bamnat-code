package com.bamnat.core

import com.bamnat.core.pay.RateTable
import com.bamnat.core.remote.RemoteFiles
import com.bamnat.core.tax.TaxPlus
import com.bamnat.core.time.Today
import com.bamnat.core.work.JobOffer
import com.bamnat.core.work.LaborCost
import com.bamnat.core.work.OfferCompare
import com.bamnat.core.work.Staff
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 [RVJ-20] 뿌리 고침 11 '새해 전환 점검' (검토 PAYC-04·BOSS-62, 갈래 M·R이 함께 써요): on 1 January every year-bound value must come
 * from the rates table (built-in years or a signed `rates.json`) or a law's schedule, never from a year's number written in code.
 *
 * - [theComingYearsCarriedValuesAreKnown]: what 2027 still takes from 2026 or a built-in default with only the built-in table. Before
 *   1 January the signed `remote/v1/rates.json` should bring each of these whose 고시 is out (healthCapShare: 보건복지부 상한·하한 고시,
 *   mealFreeLimit: 소득세법 시행령, durunuriUnder: 두루누리 공고, accident…: 고용노동부 산재보험료율 고시). When one is added to the built-in
 *   table, take it off the list here.
 * - [noYearsNumberIsWrittenInCode]: a 최저임금 or 4대보험 rate written as a number outside `pay/Rates.kt`. A line another 갈래 still owns
 *   is listed in [KNOWN] with its 검토 번호; when it is fixed, the test says to take it off.
 */
class NewYearSwitchTest {
    @After fun reset() {
        RateTable.use(null)
        Today.reset()
    }

    private val jan1 = LocalDate.of(2027, 1, 1)

    @Test fun theComingYearsCarriedValuesAreKnown() {
        assertEquals(
            listOf("healthCapShare", "healthMinShare", "mealFreeLimit", "durunuriUnder", "accidentPermille", "commutePermille",
                "wageClaimPermille", "asbestosPermille"),
            RateTable.carried(jan1),
        )
        // 최저임금·국민연금·건강·장기요양은 2027 표가 있어요.
        assertEquals(10_700L, RateTable.on(jan1).minimumHourlyWage)
        // A full signed file for the year leaves nothing carried.
        RateTable.use(RemoteFiles.parse(
            ("""{"format":1,"kind":"rates","version":2027010101,"issued":"2026-12-20","minApp":33,"data":{"rates":[{"from":"2027-01-01",""" +
                """"version":"2027.2","source":"시험","minimumHourlyWage":10700,"pensionRate":0.10,"healthRate":0.0719,"longTermCareRate":0.009994,""" +
                """"healthCapShare":4700000,"healthMinShare":10000,"mealFreeLimit":200000,"durunuriUnder":2800000,"accidentPermille":{"retail_food":9.0},""" +
                """"commutePermille":0.6,"wageClaimPermille":0.6,"asbestosPermille":0.03}]}}""").toByteArray(),
        ))
        assertEquals(emptyList<String>(), RateTable.carried(jan1))
        // 2028 with no row yet: the 최저임금 and the 고시 values are carried, 국민연금 follows 국민연금법 제88조 (10.5%).
        RateTable.use(null)
        val y2028 = RateTable.carried(LocalDate.of(2028, 1, 1))
        assertTrue(y2028.toString(), "minimumHourlyWage" in y2028 && "healthRate" in y2028 && "pensionRate" !in y2028)
    }

    @Test fun theWordsFollowTheYear() {
        assertEquals("국민연금 4.75%, 건강보험 3.595%, 장기요양, 고용보험 1.15%(150명 미만)", LaborCost.employerRatesText(LocalDate.of(2026, 6, 1)))
        // 국민연금법 제88조: 2027년 10% (사업주 5%).
        assertEquals("국민연금 5%, 건강보험 3.595%, 장기요양, 고용보험 1.15%(150명 미만)", LaborCost.employerRatesText(jan1))
        val s26 = TaxPlus.sources(LocalDate.of(2026, 6, 1)).associateBy { it.name }
        assertEquals("9.5% (근로자 4.75%)", s26.getValue("국민연금 보험료율").value)
        assertEquals("0.9448% (건강보험료의 13.14%)", s26.getValue("장기요양보험료율").value)
        val s27 = TaxPlus.sources(jan1).associateBy { it.name }
        assertEquals("10% (근로자 5%)", s27.getValue("국민연금 보험료율").value)
        assertEquals("0.9994% (건강보험료의 13.9%)", s27.getValue("장기요양보험료율").value)
        assertTrue(s27.values.take(4).all { it.checked })
    }

    @Test fun firstValuesTakeTheYearsMinimumWage() {
        Today.fix(ZonedDateTime.of(2027, 1, 1, 9, 0, 0, 0, ZoneId.of("Asia/Seoul")))
        assertEquals(10_700L, RateTable.minimumWage())
        assertEquals(10_700L, Staff("s", "새 직원").hourlyWage)
        assertTrue(OfferCompare.of(JobOffer(hourlyWage = 10_500, hoursPerWeek = 20.0)).belowMinimum)
        Today.fix(ZonedDateTime.of(2026, 12, 31, 9, 0, 0, 0, ZoneId.of("Asia/Seoul")))
        assertEquals(10_320L, Staff("s", "새 직원").hourlyWage)
    }

    @Test fun noYearsNumberIsWrittenInCode() {
        val roots = listOf(File("src/main/kotlin"), File("../app/src/main/java")).filter { it.isDirectory }
        assertTrue("source roots", roots.size == 2)
        val wage = Regex("""(?<![\d_.,])10[_,]?(030|320|700)(?![\d_])""")
        val rate = Regex("""(4\.75|4\.5|5\.0|3\.545|3\.595|9\.5|7\.09|7\.19|1\.15|12\.95|13\.14|0\.9448|0\.9182|0\.9994)%""")
        val found = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Rates.kt" }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.isEmpty() || code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                    if (wage.containsMatchIn(code) || rate.containsMatchIn(code)) "${f.name}:${code.take(80)}" else null
                }
            }
        }
        val known = KNOWN.keys
        val fresh = found.filter { hit -> known.none { hit.startsWith(it) } }
        assertEquals("새해 값이 코드에 박혔어요. RateTable.minimumWage(날짜)·TakeHome.sharesOn(날짜)·RateTable.pct로 바꿔 주세요", emptyList<String>(), fresh)
        val gone = known.filter { k -> found.none { it.startsWith(k) } }
        assertEquals("고쳐진 줄이에요. KNOWN에서 지워 주세요", emptyList<String>(), gone)
    }

    private companion object {
        /** Lines another 갈래 owns, by file and the start of the code, with the 검토 번호 that fixes them. */
        val KNOWN = mapOf(
            "RecipeMakeScreen.kt:const val MIN_WAGE = 10_320L" to "MKT-21 (갈래 M): 만들기의 '시급 처음 값'",
        )
    }
}
