package com.bamnat.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth
import com.bamnat.core.time.Today

/**
 * [NAV-01] The month on screen, one for the calendar, 돈 and 근무·급여: turning the month in one of
 * them turns it in all, so each opens on the month the user was just looking at.
 */
class ShownMonth(start: YearMonth = Today.month()) {
    private val _month = MutableStateFlow(start)
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    /** The month today was in when [month] last followed it. */
    @Volatile private var todaySeen: YearMonth = Today.month()

    fun show(month: YearMonth) {
        _month.value = month
    }

    /**
     * [NAV-08] 1.36 빌드 8 (12/1인데 급여·공유가 10월로 열림): the app left open over the turn of a month. A month that was
     * today's moves on to the new today; one the user turned to on purpose stays.
     */
    fun followToday(now: YearMonth = Today.month()) {
        if (now == todaySeen) return
        if (_month.value == todaySeen) _month.value = now
        todaySeen = now
    }
}
