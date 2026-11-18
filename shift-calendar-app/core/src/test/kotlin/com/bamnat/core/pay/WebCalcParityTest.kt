package com.bamnat.core.pay

import com.bamnat.core.leave.LeaveAccrual
import com.bamnat.core.leave.LeaveBasis
import com.bamnat.core.leave.LeavePolicy
import com.bamnat.core.leave.LotKind
import com.bamnat.core.money.DailyWorkerTax
import com.bamnat.core.money.Withholding
import com.bamnat.core.remote.Json
import com.bamnat.core.schedule.DaySource
import com.bamnat.core.schedule.LegalBreak
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.core.work.HourlyWeek
import com.bamnat.core.work.LaborCost
import com.bamnat.core.work.QuitCheck
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffShift
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.0.0 [RVR-01] The public web calculators (`tools/site/static/calc/calc.js`) promise the app's numbers. The cases in
 * `tools/site/calc-cases.json` are checked here with the app's own code and by `tools/site/test/calc.test.mjs` with the web's,
 * so a rate file, a table or a formula that moves one side and not the other fails one of the two.
 */
@Suppress("UNCHECKED_CAST")
class WebCalcParityTest {
    private val all = Json.parse(File("../tools/site/calc-cases.json").readText()) as Map<String, Any?>
    private fun list(key: String) = all[key] as List<Map<String, Any?>>
    private fun Map<String, Any?>.long(k: String) = (this[k] as Number).toLong()
    private fun Map<String, Any?>.int(k: String) = (this[k] as Number).toInt()
    private fun Map<String, Any?>.day(k: String) = LocalDate.parse(this[k] as String)
    private fun Map<String, Any?>.expect() = this["expect"] as Map<String, Any?>
    private fun Map<String, Any?>.name() = (this["name"] ?: this.toString()) as String

    @Test fun takeHome() {
        for (c in list("takeHome")) {
            val r = TakeHome.estimate(c.long("gross"), TaxMode.INSURANCE_4, c.int("family"), c.day("day"), c.int("kids"), c.long("free"))
            val by = r.deductions.toMap()
            val got = mapOf(
                "pension" to (by["국민연금"] ?: 0L), "health" to (by["건강보험"] ?: 0L), "care" to (by["장기요양"] ?: 0L),
                "employment" to (by["고용보험"] ?: 0L), "tax" to (by[TakeHome.TAX_LABEL] ?: 0L), "local" to (by[TakeHome.LOCAL_TAX_LABEL] ?: 0L),
                "net" to r.net,
            )
            for ((k, v) in c.expect()) assertEquals("${c.name()} $k", (v as Number).toLong(), got.getValue(k))
        }
    }

    @Test fun withholding() {
        for (c in list("withholding")) {
            val total = Withholding.of(c.long("amount"))
            val e = c.expect()
            assertEquals("${c["amount"]}", (e["income"] as Number).toLong() + (e["local"] as Number).toLong(), total)
        }
    }

    @Test fun dailyWorkerTax() {
        for (c in list("daily")) {
            val days = List(c.int("days")) { c.long("pay") }
            val t = if (c["paid"] == "together") DailyWorkerTax.paidTogether(days) else DailyWorkerTax.paidDaily(days)
            assertEquals("${c.name()} income", (c.expect()["income"] as Number).toLong(), t.income)
            assertEquals("${c.name()} local", (c.expect()["local"] as Number).toLong(), t.local)
        }
    }

    @Test fun holidayPay() {
        for (c in list("juhyu")) {
            val w = HourlyWeek(LocalDate.of(2026, 11, 2), c.int("week"), 0)
            assertEquals("${c["week"]}", (c.expect()["holMin"] as Number).toInt(), w.holidayPayMinutes)
        }
    }

    @Test fun severance() {
        for (c in list("severance")) {
            val r = QuitCheck.severance(c.day("hire"), c.day("last"), 40.0, 10_320, c.long("threeMonthPay"))
            val got = mapOf("eligible" to r.eligible, "averageDaily" to r.averageDaily, "amount" to r.amount)
            for ((k, v) in c.expect()) {
                val want: Any = if (v is Number) v.toLong() else v as Boolean
                assertEquals("${c.name()} $k", want, got.getValue(k))
            }
        }
    }

    @Test fun leave() {
        for (c in list("leave")) {
            val fiscal = c["basis"] == "fiscal"
            val policy = LeavePolicy(basis = if (fiscal) LeaveBasis.FISCAL else LeaveBasis.HIRE, fiscalStartMonth = (c["fiscalMonth"] as Number?)?.toInt() ?: 1)
            var lots = LeaveAccrual.lots(c.day("hire"), policy, c.day("until")).map {
                val kind = when (it.kind) { LotKind.MONTHLY -> "month"; LotKind.PRORATED -> "part"; else -> "year" }
                Triple(kind, it.days, it.on.toString())
            }
            when (c["only"]) {
                "year" -> lots = lots.filter { it.first == "year" }
                "fiscal" -> lots = lots.filter { it.first != "month" }
            }
            if (c["last"] == true) lots = lots.takeLast(1)
            val want = (c["expect"] as List<List<Any?>>).map { Triple(it[0] as String, (it[1] as Number).toDouble(), it[2] as String) }
            assertEquals(c.name(), want, lots)
        }
    }

    /** 2.0.0 [I50b-01·T50-09] 사장님 인건비 (`calc/boss/`, F.labor): the app's [LaborCost.line] for one member of staff. */
    @Test fun labor() {
        for (c in list("labor")) {
            val shifts = (c["days"] as List<List<Any?>>).associate { d ->
                DayOfWeek.of((d[0] as Number).toInt()) to StaffShift(LocalTime.parse(d[1] as String), LocalTime.parse(d[2] as String))
            }
            val staff = Staff("s", "직원", hourlyWage = c.long("wage"), holidayPay = true, insured = c["insured"] as Boolean, shifts = shifts)
            val line = LaborCost.line(staff, YearMonth.parse(c["month"] as String), c["breaks"] as Boolean)
            val got = mapOf("minutes" to line.workMinutes.toLong(), "base" to line.base, "holiday" to line.holidayPay, "insurance" to line.insurance, "total" to line.total)
            for ((k, v) in c.expect()) assertEquals("${c.name()} $k", (v as Number).toLong(), got.getValue(k))
        }
    }

    /**
     * 2.0.0 [I50b-03] 한 주 연장·야간·휴일 (`calc/week/`, F.week): the app's [MinuteClassifier] with its default options (하루 8시간·주 40시간,
     * 휴일 근로도 40시간을 채움, 휴일은 그날 0~24시), one week from Monday with no break start (taken out evenly).
     */
    @Test fun week() {
        val monday = LocalDate.of(2026, 11, 9)
        val classifier = MinuteClassifier({ RateTable.on(it) })
        for (c in list("week")) {
            val holiday = (c["holidays"] as List<Any?>).map { (it as Number).toInt() == 1 }
            val days = (c["days"] as List<Any?>).mapIndexedNotNull { i, d ->
                val row = d as List<Any?>? ?: return@mapIndexedNotNull null
                val start = LocalTime.parse(row[0] as String)
                val end = LocalTime.parse(row[1] as String)
                val span = StaffShift(start, end).spanMinutes
                val rest = (row.getOrNull(2) as Number?)?.toInt() ?: LegalBreak.minutesFor(span)
                ResolvedDay(
                    date = monday.plusDays(i.toLong()), shiftType = null, source = DaySource.OVERRIDE, start = start, end = end,
                    breakMinutes = rest, scheduledMinutes = span - rest, alarmSkipped = false, entry = null,
                )
            }
            val sum = classifier.classify(days) { d -> ChronoUnit.DAYS.between(monday, d).toInt().let { it in 0..6 && holiday[it] } }
                .fold(WorkMinutes()) { a, d -> a + d.total }
            val got = mapOf(
                "regular" to sum.regular, "overtime" to sum.overtime, "hWithin" to sum.holidayWithin8h, "hOver" to sum.holidayOver8h,
                "night" to sum.night, "worked" to sum.worked,
            )
            for ((k, v) in c.expect()) assertEquals("${c.name()} $k", (v as Number).toInt(), got.getValue(k))
        }
    }
}
