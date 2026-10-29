package com.bamnat.app.tax

import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.tier.Feature
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.YearEndPreview
import com.bamnat.core.tier.ProReason
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * F 1.32 [TAXP-03] 594 매주 다시 계산: from October to January, once a week at most, when the app opens the 연말정산
 * 미리보기 of the calendar on screen is worked out again with the 가계부 so far and told as a notice when the switch on its
 * card is on. A 프로 subscription's (조사 6장: a 하루 이용권 sees the result, the repeat is the subscription's). No
 * WorkManager: it runs with the app's waiting jobs, as the other notices do.
 */
object YearEndWeekly {
    const val ID = 7209
    private const val REQUEST = 2212

    /** October to January: the 연말정산 season. */
    fun inSeason(d: LocalDate) = d.monthValue >= 10 || d.monthValue == 1

    fun due(last: LocalDate?, today: LocalDate) = last == null || ChronoUnit.DAYS.between(last, today) >= 7

    suspend fun check(c: AppContainer, today: LocalDate = Today.date()) {
        if (!inSeason(today)) return
        val gate = c.featureGate.state.value
        if (!gate.allows(Feature.TAX_FORECAST) || gate.access.reason == ProReason.DAY_PASS) return
        val id = c.calendars.current().id
        val tax = c.tax(id)
        val s = tax.currentSettings()
        if (!s.yearEndWeekly || !due(s.yearEndLastAt, today)) return
        val profile = c.calendars.pay(id).profile.value ?: return
        if (profile.taxMode != TaxMode.INSURANCE_4) return
        val snapshot = runCatching { c.calendars.repository(id).snapshot.first() }.getOrNull() ?: return
        // In January the year just ended is the one being settled.
        val month = if (today.monthValue == 1) YearMonth.of(today.year - 1, 12) else YearMonth.from(today)
        val money = c.calendars.money(id)
        val r = withContext(Dispatchers.Default) {
            val extras = YearEndPreview.extrasFrom(money.allEntries(), money.allAccounts(), month.year, today, s.yearEndFor(month.year))
            runCatching { YearEndPreview.ofYear(profile, month, snapshot.resolver::resolve, extras = extras) }.getOrNull()
        } ?: return
        tax.saveSettings { it.copy(yearEndLastRefund = r.refund, yearEndLastAt = today) }
        val title = if (r.refund >= 0) "연말정산 약 ${"%,d".format(r.refund)}원 돌려받아요" else "연말정산 약 ${"%,d".format(-r.refund)}원 더 내요"
        val change = s.yearEndLastRefund?.let { r.refund - it }?.takeIf { it != 0L }
        val body = (change?.let { "지난주보다 ${"%,d".format(kotlin.math.abs(it))}원 " + if (it > 0) "늘었어요. " else "줄었어요. " } ?: "") +
            (r.tips.firstOrNull()?.text ?: "눌러서 자세히 봐요")
        val n = NotificationCompat.Builder(c.appContext, Notifications.MONEY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.PAY, REQUEST, calendar = id))
            .setAutoCancel(true)
            .build()
        Notifications.post(c.appContext, ID, n)
    }
}
