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
