package com.bamnat.app.ui.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind

/**
 * [CNV-12] 1.37 내 실제 모습 (사용자 2026-11-04 "입어보기사용시 실제의 현재 모습을 보여주게 … 체감이 안돼"): on my own phone the studio's
 * example and 입어 보기 draw my own month, money and memos, so a look is tried on what I really have. False only while the 올리기 표지 is
 * taken ([StudioShot]): that picture goes to 장터 for others to see, so it is the made-up example, never my data.
 */
internal val LocalRealLook = staticCompositionLocalOf { true }

/** This month of the main calendar's 가계부: money in and out, and the latest lines (newest first). */
internal data class MyMoney(val income: Long, val expense: Long, val recent: List<MoneyEntry>)

/** [CNV-12] My money this month, or null when it is not to be shown ([LocalRealLook] off) or there is nothing in it yet. */
@Composable
internal fun rememberMyMoney(): MyMoney? {
    val real = LocalRealLook.current
    val c = LocalContext.current.appContainer
    val flow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).money.entries }
    val entries by flow.collectAsStateWithLifecycle(emptyList())
    return remember(entries, real) {
        if (!real) return@remember null
        val month = java.time.YearMonth.from(com.bamnat.core.time.Today.date())
        val now = entries.filter { java.time.YearMonth.from(it.date) == month }
        if (now.isEmpty()) return@remember null
        MyMoney(now.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amount }, now.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amount },
            now.filter { it.kind != MoneyKind.TRANSFER }.sortedWith(compareByDescending<MoneyEntry> { it.date }.thenByDescending { it.time }).take(4))
    }
}

/** [CNV-12] My memos as the memo list starts them (pinned first, newest next), at most [n]; null when not real or none yet. */
@Composable
internal fun rememberMyMemos(n: Int = 4): List<Memo>? {
    val real = LocalRealLook.current
    val c = LocalContext.current.appContainer
    val memos by c.memoRepository.memos.collectAsStateWithLifecycle(emptyList())
    return remember(memos, real, n) {
        if (!real) return@remember null
        memos.filter { it.deletedAtMillis == null }.sortedWith(compareByDescending<Memo> { it.pinned }.thenByDescending { it.updatedAtMillis })
            .take(n).ifEmpty { null }
    }
}

/**
 * [CELL-02] 1.39 (사용자 2026-11-07, 입어 보기에서 내 달력에 적은 것이 안 보임): my memos by their date, each by its title's first line, for
 * the month 입어 보기 and the studio draw on my phone; null elsewhere (the 올리기 표지 keeps the example).
 */
@Composable
internal fun rememberMyDayMemos(): Map<java.time.LocalDate, List<String>>? {
    val real = LocalRealLook.current
    val c = LocalContext.current.appContainer
    val memos by c.memoRepository.memos.collectAsStateWithLifecycle(emptyList())
    return remember(memos, real) {
        if (!real) return@remember null
        memos.filter { it.deletedAtMillis == null && it.date != null }.groupBy { it.date!! }
            .mapValues { (_, l) -> l.map { m -> m.title.ifBlank { m.preview }.trim().lineSequence().firstOrNull().orEmpty() }.filter { it.isNotBlank() } }
    }
}

/** [CNV-12] An amount as the 가계부 writes it: 1,234,000원. */
internal fun won(v: Long): String = "%,d원".format(v)

/** [CNV-12] What a 가계부 card shows from [m] (my money); null for a card whose numbers are not in [m] (it keeps the example). */
internal fun myMoneyBody(id: String, m: MyMoney): StudioExamples.Body? = when (id) {
    "hero" -> StudioExamples.Body(listOf("수입" to won(m.income), "지출" to won(m.expense), "남은 돈" to won(m.income - m.expense)),
        meter = if (m.income > 0) (m.expense.toFloat() / m.income).coerceIn(0f, 1f) else null)
    "recent" -> StudioExamples.Body(m.recent.map { e ->
        e.merchant.ifBlank { e.note.ifBlank { e.category } }.take(14) to (if (e.kind == MoneyKind.INCOME) "+" else "-") + won(e.amount)
    })
    "income" -> StudioExamples.Body(listOf("이번 달 수입" to won(m.income)))
    else -> null
}

/** [CNV-12] PC 확인 1.37 A11 (현장 일감 2건인데 스튜디오는 0일·빈 칸): this month's 일감 of the main calendar; null when not real. */
@Composable
internal fun rememberMyJobs(): List<com.bamnat.core.money.Job>? {
    val real = LocalRealLook.current
    val c = LocalContext.current.appContainer
    val flow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).money.jobs }
    val jobs by flow.collectAsStateWithLifecycle(emptyList())
    return remember(jobs, real) { if (!real) null else jobs.filter { !it.cancelled } }
}

/** A 일감's word in a day cell: its 공수 (1공, 1.5, as the calendar writes it), a platform day's 건수 (5건), or the start of its title. */
internal fun jobCell(j: com.bamnat.core.money.Job): String = when {
    com.bamnat.core.money.PlatformWork.isPlatform(j) -> "${com.bamnat.core.money.PlatformWork.count(j)}건"
    else -> j.units?.let { com.bamnat.core.money.DayRate.cell(it) } ?: j.title.take(4)
}

/**
 * This month's 일감: days worked, 공수 (null when none were 공수형), 건수 (건별·배달) and the money to come, as the 현장 summary card says
 * them. PC 확인 1.37 A12 (플랫폼형 배민 5건인데 요약이 0일·0건·0원): a platform day counts its 건수 and what comes after the platform's
 * 수수료 and the day's own costs, as 배달 정산 does.
 */
internal data class MyJobMonth(val days: Int, val units: Double?, val count: Int, val amount: Long)

internal fun jobMonth(jobs: List<com.bamnat.core.money.Job>, @Suppress("UNUSED_PARAMETER") rules: com.bamnat.core.money.FieldRules = com.bamnat.core.money.FieldRules(),
    month: java.time.YearMonth = java.time.YearMonth.from(com.bamnat.core.time.Today.date())): MyJobMonth {
    val now = jobs.filter { java.time.YearMonth.from(it.date) == month && !it.cancelled }
    val (platform, rest) = now.partition { com.bamnat.core.money.PlatformWork.isPlatform(it) }
    val units = rest.mapNotNull { it.units }.takeIf { it.isNotEmpty() }?.sum()
    val count = platform.sumOf { com.bamnat.core.money.PlatformWork.count(it) } + rest.count { it.units == null }
    // PC 확인 1.37 A14: 받을 돈 is what the calendar and the day sheet call it, each 일감's own (Job.unpaid: after 3.3% and what came in
    // already), one sum for 공수·건별·배달 alike, so the three places say the same number. [rules] stays for when the calendar takes
    // the platform's 수수료 off too.
    return MyJobMonth(now.map { it.date }.distinct().size, units, count, now.sumOf { it.unpaid })
}

/** The main calendar's 현장 rules (일당·플랫폼 수수료), for [jobMonth]. */
@Composable
internal fun rememberFieldRules(): com.bamnat.core.money.FieldRules {
    val c = LocalContext.current.appContainer
    // PC 확인 1.37 A13 (받을 돈이 수수료 전 362,000원): read as the calendar does, from the calendar list as it loads, not once at the
    // start (the list was still the default then, without its 수수료).
    val cal by c.calendars.activeCalendar.collectAsStateWithLifecycle(c.calendars.current())
    return cal.field ?: com.bamnat.core.money.FieldRules()
}
