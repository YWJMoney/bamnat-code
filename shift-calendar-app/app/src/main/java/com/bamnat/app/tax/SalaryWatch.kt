package com.bamnat.app.tax

import com.bamnat.app.AppContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.TaxRepository
import com.bamnat.core.journal.JournalKind
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.SalaryDeposits
import com.bamnat.core.tax.TaxLearner
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * [TAX-01] Pay that came into the account becomes 월급 관측 (사용자 U9-다, 결정 W-D1): after a payment notification is
 * written and whenever the money tab opens, the recent income lines are checked with [SalaryDeposits]; a line that is
 * pay is filed as 급여 (so it is no longer added on top of the estimate as 부수입) and kept as that pay month's
 * observation. No work of its own at start-up (통합계획 규칙 9).
 */
object SalaryWatch {
    /** How far back a check looks. */
    const val DAYS = 45L

    /** What one check did. */
    data class Result(val seen: List<SalaryDeposits.Seen>, val refiled: List<MoneyEntry>)

    suspend fun check(c: AppContainer, calendarId: String, today: LocalDate = Today.date()): Result {
        val money = c.calendars.money(calendarId)
        val tax = c.tax(calendarId)
        val profile = c.calendars.pay(calendarId).profile.value
        val snapshot = runCatching { c.calendars.repository(calendarId).snapshot.first() }.getOrNull()
        return check(money.allEntries(), tax, profile, snapshot, today) { e -> money.save(e) }
            .also { r -> if (r.seen.isNotEmpty()) c.journal.note(JournalKind.PAY_OBSERVED, count = r.seen.size) }
    }

    /** The check itself, apart from where the data lives, for tests. */
    suspend fun check(
        entries: List<MoneyEntry>, tax: TaxRepository, profile: PayProfile?, snapshot: ScheduleSnapshot?, today: LocalDate,
        save: suspend (MoneyEntry) -> Unit,
    ): Result {
        val settings = tax.currentSettings()
        val observations = tax.allObservations()
        val byEntry = observations.filter { it.entryId != null }.associateBy { it.entryId!! }
        // A line deleted, or no longer income, takes its observation with it.
        val ids = entries.map { it.id }.toSet()
        observations.filter { it.source == PayObservation.Source.DEPOSIT && it.entryId != null && it.entryId !in ids }.forEach { tax.delete(it.id) }
        val from = today.minusDays(DAYS)
        val recent = entries.filter { it.kind == MoneyKind.INCOME && !it.date.isBefore(from) && !it.date.isAfter(today) && it.id !in settings.notPay }
        if (recent.isEmpty()) return Result(emptyList(), emptyList())
        val expected = expected(profile, snapshot, observations, today)
        val seen = recent.mapNotNull { SalaryDeposits.seen(it, expected, settings.payers) }
        val refiled = ArrayList<MoneyEntry>()
        for (s in seen) {
            val e = s.entry
            // Filed as 급여 so the money tab counts it once; a split line keeps its parts.
            if (e.shareOf(MoneyCategories.SALARY) == 0L && e.parts.isEmpty()) {
                val moved = e.copy(category = MoneyCategories.SALARY)
                save(moved)
                refiled += moved
            }
            val known = byEntry[e.id]
            if (known == null || known.net != e.amount || known.payMonth != s.payMonth) tax.observeDeposit(e, s.payMonth)
        }
        val newPayers = seen.map { it.entry.merchant.trim() }.filter { it.isNotEmpty() }.toSet() - settings.payers
        if (newPayers.isNotEmpty()) tax.saveSettings { it.copy(payers = it.payers + newPayers) }
        return Result(seen.filter { byEntry[it.entry.id] == null }, refiled)
    }

    /** [TAX-01] The user said this line is not pay: it goes back to [category] and is never taken again. */
    suspend fun notPay(c: AppContainer, calendarId: String, entry: MoneyEntry, category: String = "부수입") {
        val money = c.calendars.money(calendarId)
        val tax = c.tax(calendarId)
        tax.allObservations().filter { it.entryId == entry.id }.forEach { tax.delete(it.id) }
        tax.saveSettings { s ->
            val payer = SalaryDeposits.key(entry.merchant)
            s.copy(notPay = s.notPay + entry.id, payers = s.payers.filter { SalaryDeposits.key(it) != payer || payer.isEmpty() }.toSet())
        }
        if (entry.category == MoneyCategories.SALARY) money.save(entry.copy(category = category))
    }

    /**
     * The pay months around [today] with their pay day and expected 실수령: the pay engine's 세전 through [TaxLearner]
     * with the records so far.
     */
    fun expected(profile: PayProfile?, snapshot: ScheduleSnapshot?, observations: List<PayObservation>, today: LocalDate): List<SalaryDeposits.Expected> {
        if (profile == null) return emptyList()
        val table = TaxLearner.Table.of(profile)
        val gross = grossOf(profile, snapshot)
        val fit = TaxLearner.fit(observations, table, gross)
        val now = YearMonth.from(today)
        return (-3..0).map { now.plusMonths(it.toLong()) }.map { m ->
            val g = gross(m)
            SalaryDeposits.Expected(m, profile.period.payDate(m), g?.let { TaxLearner.forecast(m, it, fit, table).net })
        }
    }

    /** The pay engine's 세전 of a pay month; null without a schedule or when it cannot be worked out. */
    fun grossOf(profile: PayProfile, snapshot: ScheduleSnapshot?): (YearMonth) -> Long? {
        if (snapshot == null) return { null }
        val calc = PayCalculator()
        val cache = HashMap<YearMonth, Long?>()
        return { m -> cache.getOrPut(m) { runCatching { calc.calculate(profile, m, snapshot.resolver::resolve, checks = false).total }.getOrNull() } }
    }
}
