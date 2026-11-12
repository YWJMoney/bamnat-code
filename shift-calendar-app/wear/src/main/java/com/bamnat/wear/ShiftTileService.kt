package com.bamnat.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bamnat.core.work.WatchDays
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * [SHFT-08] 오늘 근무 tile: today's shift big in its colour with its times, tomorrow's under it, and the next
 * alarm. Drawn from the watch's own copy, so it works away from the phone; it moves on at midnight.
 */
class ShiftTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { done ->
            // [WEAR-03] 오늘 알람 끄기 tapped on the tile: the phone turns it off and sends the days back.
            // 2.0.0 [RVI-11] (검토 ALM-10) Two taps: the first asks '한 번 더 누르면 꺼요' for a few seconds, the second sends, and the
            // tile then says what happened ('끄는 중…', '껐어요 · 되돌리기', or that the phone did not answer).
            TileAlarmOff.onClick(applicationContext, requestParams.currentState.lastClickableId)
            val zone = Today.zone()
            val nowMs = Today.millis()
            val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            val untilMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - nowMs + 5_000
            // [SHFT-08] Once the alarm shown has gone off, the tile moves on to the next one.
            val untilAlarm = WatchStore.load(this)?.let { WatchDays.alarmAhead(it, now) }
                ?.let { it.atZone(zone).toInstant().toEpochMilli() - nowMs + 5_000 } ?: Long.MAX_VALUE
            // 2.0.0 [RVI-11] An armed '한 번 더 누르면 꺼요' goes back by itself.
            val untilDisarm = if (TileAlarmOff.armed(this, nowMs)) TileAlarmOff.ARM_MILLIS + 500 else Long.MAX_VALUE
            done.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES)
                    .setFreshnessIntervalMillis(minOf(untilMidnight, untilAlarm, untilDisarm).coerceAtLeast(if (untilDisarm < Long.MAX_VALUE) 1_000 else 60_000))
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(now)))
                    .build(),
            )
            "tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { done ->
            done.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())
            "resources"
        }

    private fun layout(now: LocalDateTime): LayoutElementBuilders.LayoutElement {
        val p = WatchStore.load(this)
        val col = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        val today = now.toLocalDate()
        col.addContent(text("오늘 · ${WatchText.date(today)}", 13f, GREY))
        val d = p?.let { WatchDays.on(it.days, today) }
        if (p == null || d == null) {
            col.addContent(space(6f))
            col.addContent(text(if (p == null) WatchText.NO_DATA else WatchText.notSent(p), 15f, WHITE, lines = 3))
        } else {
            col.addContent(space(4f))
            col.addContent(text(d.name, 24f, readable(d.colorArgb), bold = true))
            if (d.times.isNotEmpty()) col.addContent(text(d.times, 16f, WHITE))
            WatchDays.on(p.days, today.plusDays(1))?.let { t ->
                col.addContent(space(6f))
                col.addContent(text("내일 ${t.line}", 13f, LIGHT))
            }
            WatchText.alarm(p, now)?.let {
                col.addContent(space(4f))
                col.addContent(text(it, 12f, AMBER))
            }
            // 1.38 [SLEEP-14] 오늘 잘 시간, as the phone's 오늘의 흐름 says it. 2.0.0 [RVI-08] Not last night's in the day.
            WatchDays.sleepLineNow(p, now).takeIf { it.isNotBlank() }?.let {
                col.addContent(space(2f))
                col.addContent(text(it, 12f, LIGHT, lines = 1))
            }
            WatchDays.weekText(p, today)?.let {
                col.addContent(space(2f))
                col.addContent(text(it, 11f, GREY))
            }
            // [WEAR-03] Turn today's alarm off from the wrist, before bed after a night (I 리뷰 N7 c).
            // 2.0.0 [RVI-08] Whether today has one is worked out on the watch (after midnight the phone's word is yesterday's).
            // 2.0.0 [RVI-11] Two taps, then what happened, with 되돌리기.
            val said = TileAlarmOff.said(this, today)
            when {
                said != null -> {
                    col.addContent(space(6f))
                    col.addContent(text(said.first, 12f, if (said.second) AMBER else LIGHT, lines = 2))
                    if (said.second) {
                        col.addContent(space(4f))
                        col.addContent(button("되돌리기", TileAlarmOff.UNDO))
                    }
                }
                WatchDays.alarmTodayNow(p, now) -> {
                    col.addContent(space(6f))
                    col.addContent(button(if (TileAlarmOff.armed(this, Today.millis())) "한 번 더 누르면 꺼요" else "오늘 알람 끄기", TileAlarmOff.OFF))
                }
            }
        }
        // 1.32 [WEAR-04] When the days came from the phone, small, so an old copy is seen as old.
        if (p != null) {
            col.addContent(space(4f))
            col.addContent(text(WatchText.syncedAt(p), 10f, GREY, lines = 1))
        }
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build())
                .build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(col.build())
            .build()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 2) =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(lines)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build())
            .build()

    private fun space(h: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    /** A pill the size of a finger that asks the tile again with [id] as the tapped clickable. */
    private fun button(label: String, id: String) = LayoutElementBuilders.Box.Builder()
        .setHeight(dp(40f))
        .setWidth(androidx.wear.protolayout.DimensionBuilders.wrap())
        .setModifiers(ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(ActionBuilders.LoadAction.Builder().build()).build())
            .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(BUTTON))
                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(20f)).build()).build())
            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).build())
            .build())
        .addContent(text(label, 13f, WHITE, lines = 1))
        .build()

    companion object {
        private const val RESOURCES = "1"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val LIGHT = 0xFFD6DAE3.toInt()
        const val GREY = 0xFF9AA3B5.toInt()
        const val AMBER = 0xFFFFC266.toInt()

        const val BUTTON = 0xFF2B3140.toInt()

        /** [WEAR-02] The shift's colour lifted so it reads on the watch's black: the shared rule in core, tested there. */
        fun readable(argbLong: Long): Int = com.bamnat.core.layout.Contrast.onBlack(argbLong).toInt()
    }
}

/**
 * 2.0.0 [RVI-11] (검토 ALM-10) The tile's 오늘 알람 끄기 as two taps and a result. The first tap arms it for [ARM_MILLIS]; the second
 * sends it; what came of it is kept for the day ([said]): '끄는 중…', '오늘 알람을 껐어요' with 되돌리기, or that no phone answered.
 */
internal object TileAlarmOff {
    const val OFF = "alarm_off"
    const val UNDO = "alarm_undo"
    const val ARM_MILLIS = 5_000L
    private const val PREFS = "tile_alarm"
    private const val K_ARMED = "armed"
    private const val K_DAY = "day"
    private const val K_STATE = "state"
    private const val SENDING = "sending"
    private const val DONE = "done"
    private const val FAILED = "failed"

    private fun prefs(context: android.content.Context) = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun armed(context: android.content.Context, now: Long): Boolean = now - prefs(context).getLong(K_ARMED, 0L) in 0..ARM_MILLIS

    /** What to say on the tile today, and whether 되돌리기 goes with it; null when nothing was pressed today. */
    fun said(context: android.content.Context, today: java.time.LocalDate): Pair<String, Boolean>? {
        val p = prefs(context)
        if (p.getString(K_DAY, null) != today.toString()) return null
        return when (p.getString(K_STATE, null)) {
            SENDING -> "끄는 중…" to false
            DONE -> "오늘 알람을 껐어요" to true
            FAILED -> "휴대폰과 연결되어 있지 않아 못 껐어요" to false
            else -> null
        }
    }

    fun onClick(context: android.content.Context, id: String) {
        val now = Today.millis()
        val today = Today.date().toString()
        when (id) {
            OFF -> if (!armed(context, now)) prefs(context).edit().putLong(K_ARMED, now).apply() else {
                prefs(context).edit().putLong(K_ARMED, 0L).putString(K_DAY, today).putString(K_STATE, SENDING).apply()
                send(context, WatchDays.ALARM_OFF_PATH, today) { ok -> prefs(context).edit().putString(K_STATE, if (ok) DONE else FAILED).apply() }
            }
            UNDO -> {
                prefs(context).edit().remove(K_DAY).remove(K_STATE).apply()
                send(context, WatchDays.ALARM_ON_PATH, today) { _ -> }
            }
        }
    }

    private fun send(context: android.content.Context, path: String, text: String, then: (Boolean) -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val ok = runCatching { WatchStore.send(context, path, text) }.getOrDefault(false)
            then(ok)
            runCatching { TileService.getUpdater(context).requestUpdate(ShiftTileService::class.java) }
        }
    }
}
