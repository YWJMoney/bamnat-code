package com.bamnat.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.bamnat.core.time.Today
import com.bamnat.core.work.WatchDay
import com.bamnat.core.work.WatchDays
import java.time.LocalTime

/**
 * 1.32 [WEAR-04] 조사 대조 C16: 다음 근무까지 on a watch face. Before a shift the face counts down to its start ("5h"); on the
 * shift, to its end with 근무 중 over it. The watch draws the count itself (a countdown text), and a timeline of the next
 * shifts moves it on at each start and end, so it stays right away from the phone.
 */
class NextShiftComplicationService : SuspendingTimelineComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val now = Today.dateTime()
        val d = WatchDay(now.toLocalDate().plusDays(1), "주간", "주", 0xFF2F80ED, LocalTime.of(9, 0), LocalTime.of(18, 0))
        return WatchDays.countdowns(listOf(d), now).firstOrNull()?.let { data(type, it) }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val days = WatchStore.load(this)?.days ?: return null
        val zone = Today.zone()
        val list = WatchDays.countdowns(days, Today.dateTime())
        val first = list.firstOrNull() ?: return ComplicationDataTimeline(empty(request.complicationType) ?: return null, emptyList())
        val now = data(request.complicationType, first) ?: return null
        val ahead = list.drop(1).mapNotNull { c ->
            data(request.complicationType, c)?.let { TimelineEntry(TimeInterval(c.from.atZone(zone).toInstant(), c.to.atZone(zone).toInstant()), it) }
        }
        return ComplicationDataTimeline(now, ahead)
    }

    private fun data(type: ComplicationType, c: WatchDays.Countdown): ComplicationData? {
        val tap = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun t(s: String) = PlainComplicationText.Builder(s).build()
        val at = c.to.atZone(Today.zone()).toInstant()
        val count = TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_SINGLE_UNIT, CountDownTimeReference(at)).build()
        val title = if (c.onShift) "근무 중" else c.name.take(4)
        val said = t(if (c.onShift) "${c.name} 근무 중, 끝까지 남은 시간" else "다음 근무 ${c.name}까지 남은 시간")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(count, said).setTitle(t(title)).setTapAction(tap).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(count, said)
                .setTitle(t(if (c.onShift) "${c.name} 끝까지" else "다음 근무 ${c.name}까지")).setTapAction(tap).build()
            else -> null
        }
    }

    /** No shift ahead in the days the phone sent. */
    private fun empty(type: ComplicationType): ComplicationData? {
        fun t(s: String) = PlainComplicationText.Builder(s).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(t("-"), t(WatchText.NOT_SENT)).setTitle(t("다음 근무")).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(t(WatchText.NOT_SENT), t(WatchText.NOT_SENT)).build()
            else -> null
        }
    }
}
