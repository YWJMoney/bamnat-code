package com.bamnat.wear

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bamnat.core.time.Today
import com.bamnat.core.work.WatchDays
import com.bamnat.core.work.WatchPayload
import com.bamnat.core.work.WatchSleep
import com.bamnat.core.work.WatchTask
import com.google.common.util.concurrent.ListenableFuture
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVI-94] (검토 I03b-04, 대기열 G5-M3) 잠 타일: 잠들었어요 and 깼어요 from the wrist, into the phone's sleep log, without picking the
 * phone up in the dark. One button, the line the phone's 오늘의 흐름 says (잘 시간 08:00–15:00), and since when asleep. A press shows at
 * once on the wrist; the phone writes it at the time pressed and sends the days back.
 */
class SleepTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { done ->
            TileSleep.onClick(applicationContext, requestParams.currentState.lastClickableId)
            val now = Today.dateTime()
            done.set(TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setFreshnessIntervalMillis(60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(TileUi.frame(packageName, layout(now))))
                .build())
            "sleep_tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { done ->
            done.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())
            "resources"
        }

    private fun layout(now: LocalDateTime): LayoutElementBuilders.LayoutElement {
        val p = WatchStore.load(this)
        val col = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        col.addContent(TileUi.text("잠", 13f, ShiftTileService.GREY))
        col.addContent(TileUi.space(4f))
        val said = TileSleep.said(this, p)
        when {
            p == null -> col.addContent(TileUi.text(WatchText.NO_DATA, 14f, ShiftTileService.WHITE, lines = 3))
            !p.sleepOn -> col.addContent(TileUi.text("휴대폰에서 오늘의 흐름을 켜면 여기서 잠을 적어요", 13f, ShiftTileService.LIGHT, lines = 3))
            else -> {
                val since = TileSleep.asleepSince(this, p)
                if (since != null) {
                    col.addContent(TileUi.text("자는 중", 22f, ShiftTileService.WHITE, bold = true))
                    col.addContent(TileUi.text(TileSleep.sinceLine(since, now), 13f, ShiftTileService.LIGHT, lines = 1))
                } else {
                    col.addContent(TileUi.text(WatchDays.sleepLineNow(p, now).ifBlank { "잘 때 눌러 주세요" }, 14f, ShiftTileService.WHITE, lines = 2))
                }
                said?.let {
                    col.addContent(TileUi.space(2f))
                    col.addContent(TileUi.text(it, 11f, ShiftTileService.AMBER, lines = 2))
                }
                col.addContent(TileUi.space(8f))
                col.addContent(TileUi.button(if (since != null) "깼어요" else "잠들었어요", if (since != null) TileSleep.AWAKE else TileSleep.ASLEEP))
            }
        }
        return col.build()
    }

    private companion object {
        const val RESOURCES = "1"
    }
}

/**
 * 2.0.0 [RVI-94] The 잠 tile's press: kept on the wrist ([PREFS]) until a copy from the phone sent after it comes, so the tile says
 * it at once; sent to the phone with the time pressed. No phone answering is said on the tile, and the press is let go.
 */
internal object TileSleep {
    const val ASLEEP = "sleep_asleep"
    const val AWAKE = "sleep_awake"
    private const val PREFS = "tile_sleep"
    private const val K_PRESSED = "pressed"
    private const val K_AT = "at"
    private const val K_FAILED = "failed"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The press still waiting for the phone's copy: asleep (true) or awake (false), and when. */
    private fun pressed(context: Context, p: WatchPayload?): Pair<Boolean, Long>? {
        val pr = prefs(context)
        if (!pr.contains(K_PRESSED)) return null
        val at = pr.getLong(K_AT, 0L)
        // The phone sent the days after the press: its word is the one now.
        if (p != null && p.sentAtMillis > at) {
            pr.edit().remove(K_PRESSED).remove(K_AT).apply()
            return null
        }
        return pr.getBoolean(K_PRESSED, false) to at
    }

    fun asleepSince(context: Context, p: WatchPayload): LocalDateTime? {
        val press = pressed(context, p)
        return WatchSleep.asleepNow(p, press?.first, press?.second?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), Today.zone()) })
    }

    /** "01:20부터 · 6시간 40분". */
    fun sinceLine(since: LocalDateTime, now: LocalDateTime): String {
        val m = Duration.between(since, now).toMinutes().coerceAtLeast(0)
        return "%02d:%02d부터".format(since.hour, since.minute) + if (m >= 60) " · ${m / 60}시간" + (if (m % 60 != 0L) " ${m % 60}분" else "") else " · ${m}분"
    }

    /** No phone took the last press: said once, until the next press. */
    fun said(context: Context, p: WatchPayload?): String? =
        if (prefs(context).getBoolean(K_FAILED, false)) "휴대폰과 연결되어 있지 않아 못 적었어요" else null

    fun onClick(context: Context, id: String) {
        val asleep = when (id) { ASLEEP -> true; AWAKE -> false; else -> return }
        val at = Today.millis()
        prefs(context).edit().putBoolean(K_PRESSED, asleep).putLong(K_AT, at).putBoolean(K_FAILED, false).apply()
        val text = WatchSleep.encode(WatchSleep.Press(asleep, at, "w$at"))
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching { WatchStore.send(context, WatchDays.SLEEP_PATH, text) }.getOrDefault(false)
            if (!ok) prefs(context).edit().remove(K_PRESSED).remove(K_AT).putBoolean(K_FAILED, true).apply()
            runCatching { TileService.getUpdater(context).requestUpdate(SleepTileService::class.java) }
        }
    }
}

/**
 * 2.0.0 [RVI-95] (검토 I03b-04·I07ab-04, 대기열 TASK-35) 오늘 할 일 타일: the phone's 할 일 위젯 on the wrist. The first three open to-dos
 * (late ones in amber), each a row the size of a finger that finishes it on its day; how many more; and 되돌리기 for a little while
 * after one was finished. Drawn from the watch's copy, so it works away from the phone; a finish waits on the wrist until sent.
 */
class TaskTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { done ->
            TileTasks.onClick(applicationContext, requestParams.currentState.lastClickableId)
            val undo = TileTasks.undoLeft(this, Today.millis())
            done.set(TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setFreshnessIntervalMillis(if (undo > 0) undo + 500 else 15 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(TileUi.frame(packageName, layout())))
                .build())
            "task_tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { done ->
            done.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())
            "resources"
        }

    private fun layout(): LayoutElementBuilders.LayoutElement {
        val p = WatchStore.load(this)
        val col = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (p == null) {
            col.addContent(TileUi.text("오늘 할 일", 13f, ShiftTileService.GREY))
            col.addContent(TileUi.space(6f))
            col.addContent(TileUi.text(WatchText.NO_DATA, 14f, ShiftTileService.WHITE, lines = 3))
            return col.build()
        }
        val (rows, more) = WatchTask.tile(p.tasks, TileTasks.pressed(this, p))
        col.addContent(TileUi.text(p.taskHead.ifBlank { "오늘 할 일" }, 13f, ShiftTileService.GREY, lines = 1))
        col.addContent(TileUi.space(4f))
        if (rows.isEmpty()) {
            col.addContent(TileUi.text(if (p.tasks.any { it.done }) "오늘 할 일을 다 끝냈어요" else "오늘은 할 일이 없어요", 14f, ShiftTileService.WHITE, lines = 2))
        }
        rows.forEach { t -> col.addContent(TileUi.button("○ " + t.text, TileTasks.DONE_PREFIX + t.id, if (t.late) ShiftTileService.AMBER else ShiftTileService.WHITE)) }
        if (more > 0) {
            col.addContent(TileUi.space(2f))
            col.addContent(TileUi.text("${more}개 더", 11f, ShiftTileService.GREY, lines = 1))
        }
        TileTasks.lastDone(this, Today.millis())?.let { text ->
            col.addContent(TileUi.space(4f))
            col.addContent(TileUi.button("되돌리기 · $text", TileTasks.UNDO, ShiftTileService.LIGHT))
        }
        return col.build()
    }

    private companion object {
        const val RESOURCES = "1"
    }
}

/** 2.0.0 [RVI-95] The 할 일 tile's taps: finished ids kept on the wrist until a copy from the phone sent after them comes. */
internal object TileTasks {
    const val DONE_PREFIX = "task_done_"
    const val UNDO = "task_undo"
    const val UNDO_MILLIS = 8_000L
    private const val PREFS = "tile_tasks"
    private const val K_PRESSED = "pressed"
    private const val K_LAST = "last"
    private const val K_LAST_TEXT = "last_text"
    private const val K_LAST_AT = "last_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ids finished on the wrist ("id@millis") that the phone has not sent back yet. */
    fun pressed(context: Context, p: WatchPayload): Set<Long> {
        val pr = prefs(context)
        val all = pr.getStringSet(K_PRESSED, emptySet()).orEmpty()
        val waiting = all.filter { it.substringAfter('@').toLongOrNull()?.let { at -> at >= p.sentAtMillis } == true }.toSet()
        if (waiting.size != all.size) pr.edit().putStringSet(K_PRESSED, waiting).apply()
        return waiting.mapNotNull { it.substringBefore('@').toLongOrNull() }.toSet()
    }

    fun undoLeft(context: Context, now: Long): Long = (prefs(context).getLong(K_LAST_AT, 0L) + UNDO_MILLIS - now).coerceAtLeast(0L)

    /** The one finished last, for 되돌리기, while [UNDO_MILLIS] has not passed. */
    fun lastDone(context: Context, now: Long): String? =
        if (undoLeft(context, now) > 0) prefs(context).getString(K_LAST_TEXT, null)?.take(10) else null

    fun onClick(context: Context, id: String) {
        val p = WatchStore.load(context) ?: return
        val pr = prefs(context)
        val now = Today.millis()
        when {
            id.startsWith(DONE_PREFIX) -> {
                val task = p.tasks.firstOrNull { it.id == id.removePrefix(DONE_PREFIX).toLongOrNull() } ?: return
                pr.edit().putStringSet(K_PRESSED, pr.getStringSet(K_PRESSED, emptySet()).orEmpty() + "${task.id}@$now")
                    .putLong(K_LAST, task.id).putString(K_LAST_TEXT, task.text).putLong(K_LAST_AT, now).apply()
                send(context, WatchTask.doneMessage(task, true))
            }
            id == UNDO -> {
                val last = pr.getLong(K_LAST, 0L)
                val task = p.tasks.firstOrNull { it.id == last } ?: return
                pr.edit().putStringSet(K_PRESSED, pr.getStringSet(K_PRESSED, emptySet()).orEmpty().filterNot { it.startsWith("$last@") }.toSet())
                    .putLong(K_LAST_AT, 0L).apply()
                send(context, WatchTask.doneMessage(task, false))
            }
        }
    }

    private fun send(context: Context, text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { WatchStore.send(context, WatchDays.TASK_PATH, text) }
            runCatching { TileService.getUpdater(context).requestUpdate(TaskTileService::class.java) }
        }
    }
}
