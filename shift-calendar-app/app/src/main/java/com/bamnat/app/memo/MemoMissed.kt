package com.bamnat.app.memo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2.0.0 [RVI-99] (검토 I07aa-02) 놓친 일정 한 장. What went by unsaid while the phone was off or the alarm came late ([RVI-22]) was told
 * one by one in the schedule notice, the rest as '남은 일정 2개'. Two or more now come as one quiet card, "놓친 일정 3개", each on its
 * line with when, and three answers for all of them: 모두 끝내기, 쉬는 날로 (each to its calendar's next day off, as 다른 때… does) and
 * a tap that opens 메모. Finished one by one elsewhere, it leaves the card.
 */
object MemoMissed {
    /**
     * The two cards: 놓친 일정 ([RVI-99]) and 2.0.0 [RVIB-05] (검토 I07ac-02, 대기열 TASK-22) 근무 중에 온 일정 — a notice-only schedule due
     * at work waits, and those of that work come in one card when it ends (퇴근 때 한 번), with the same two answers.
     */
    enum class Card(val notice: Int, val key: String, val open: Int, val done: Int, val rest: Int) {
        MISSED(7398, "list", 3210, 3211, 3212),
        AT_WORK(7397, "work", 3213, 3214, 3215),
    }

    const val NOTICE_ID = 7398
    /** From this many the missed ones are one card; one alone stays in the schedule notice. */
    const val CARD_FROM = 2
    const val ACTION_DONE = "com.bamnat.app.memo.MISSED_DONE"
    const val ACTION_REST = "com.bamnat.app.memo.MISSED_REST"
    /** 2.0.0 [RVIB-05] The end of the work the held ones wait for has come. */
    const val ACTION_RELEASE = "com.bamnat.app.memo.MISSED_RELEASE"
    internal const val EXTRA_CARD = "card"
    private const val PREFS = "memo_missed"
    private const val K_UNTIL = "work_until"
    private const val REQ_RELEASE = 3217
    private val time = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    data class Item(val memo: Long, val title: String, val at: LocalDateTime, val day: LocalDate)

    fun all(context: Context, card: Card = Card.MISSED): List<Item> = runCatching {
        val a = JSONArray(prefs(context).getString(card.key, "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { Item(it.getLong("m"), it.getString("t"), LocalDateTime.parse(it.getString("a")), LocalDate.parse(it.getString("d"))) }
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, items: List<Item>, card: Card) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("m", it.memo).put("t", it.title).put("a", it.at.toString()).put("d", it.day.toString())) }
        prefs(context).edit().putString(card.key, a.toString()).apply()
    }

    private fun joined(context: Context, card: Card, told: List<MemoScheduleAlarm.Told>): List<Item> =
        (all(context, card) + told.map { Item(it.memo.id, MemoScheduleAlarm.label(it.memo), it.at, it.day) })
            .distinctBy { it.memo to it.day }.sortedBy { it.at }.takeLast(20)

    /** [told] missed ones join those already on the card (the same memo and day once); the card is shown again, quietly. */
    fun add(context: Context, told: List<MemoScheduleAlarm.Told>) {
        val items = joined(context, Card.MISSED, told)
        save(context, items, Card.MISSED)
        show(context, items, Card.MISSED, quiet = true)
    }

    /**
     * 2.0.0 [RVIB-05] [told] came due at work, which ends [until]: they wait, with any others of that work, and come in one card
     * then ([release]).
     */
    fun hold(context: Context, told: List<MemoScheduleAlarm.Told>, until: LocalDateTime) {
        if (told.isEmpty()) return
        save(context, joined(context, Card.AT_WORK, told), Card.AT_WORK)
        val at = heldUntil(context)?.let { maxOf(it, until) } ?: until
        prefs(context).edit().putString(K_UNTIL, at.toString()).apply()
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        // A few minutes late is fine for these, and needs no exact alarm.
        am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at.atZone(Today.zone()).toInstant().toEpochMilli(), releaser(context))
    }

    /** 2.0.0 [RVIB-05] When the held ones come, or null when none wait. */
    internal fun heldUntil(context: Context): LocalDateTime? =
        prefs(context).getString(K_UNTIL, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    /**
     * 2.0.0 [RVIB-05] The work has ended ([now] at or past when they wait for; also after a restart, which drops the alarm): the
     * held ones come in one card, with one sound — "근무 중에 온 일정 3개".
     */
    fun release(context: Context, now: LocalDateTime = Today.dateTime()) {
        val until = heldUntil(context) ?: return
        if (now.isBefore(until)) return
        prefs(context).edit().remove(K_UNTIL).apply()
        val items = all(context, Card.AT_WORK)
        // One alone comes as the schedule notice it would have been, with its own buttons.
        if (items.size == 1) {
            save(context, emptyList(), Card.AT_WORK)
            val c = context.appContainer
            c.appScope.launch {
                val memo = c.memoRepository.get(items[0].memo) ?: return@launch
                MemoScheduleAlarm.postAll(context, listOf(MemoScheduleAlarm.Told(memo, items[0].at, MemoSnoozeReceiver.nextRest(c, memo), items[0].day)))
            }
            return
        }
        show(context, items, Card.AT_WORK, quiet = false)
    }

    private fun releaser(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_RELEASE, Intent(context, MemoMissedReceiver::class.java).setAction(ACTION_RELEASE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Memos finished, put away or no longer schedules ([over]) leave the cards. */
    fun settle(context: Context, over: (Long, LocalDate) -> Boolean) {
        for (card in Card.entries) {
            val items = all(context, card)
            if (items.isEmpty()) continue
            val left = items.filterNot { over(it.memo, it.day) }
            if (left.size == items.size) continue
            save(context, left, card)
            // The held ones are not shown before the work ends.
            if (card == Card.MISSED || heldUntil(context) == null) show(context, left, card, quiet = true)
        }
    }

    fun clear(context: Context, card: Card = Card.MISSED) {
        save(context, emptyList(), card)
        Notifications.cancel(context, card.notice)
    }

    /** "어제 오후 2:00", "오후 2:00", "11/9 오전 9:00". */
    internal fun whenOf(at: LocalDateTime, today: LocalDate = Today.date()): String = when (at.toLocalDate()) {
        today -> at.format(time)
        today.minusDays(1) -> "어제 ${at.format(time)}"
        else -> "${at.monthValue}/${at.dayOfMonth} ${at.format(time)}"
    }

    /** "놓친 일정 3개" (or "근무 중에 온 일정 3개") over "약 받기 · 장보기 · 택배 찾기". */
    internal fun lines(items: List<Item>, card: Card = Card.MISSED): Pair<String, String> =
        (if (card == Card.AT_WORK) com.bamnat.core.memo.MemoBacklog.workTitle(items.size) else "놓친 일정 ${items.size}개") to
            items.joinToString(" · ") { it.title }

    private fun show(context: Context, items: List<Item>, card: Card, quiet: Boolean) {
        if (items.isEmpty()) {
            Notifications.cancel(context, card.notice)
            return
        }
        val (title, text) = lines(items, card)
        val n = NotificationCompat.Builder(context, Notifications.MEMO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(quiet)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(items.joinToString("\n") { "${it.title} · ${whenOf(it.at)}" }))
            .setNumber(items.size)
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.MEMOS, card.open))
            .setAutoCancel(true)
            .addAction(0, "모두 끝내기", button(context, ACTION_DONE, card, card.done))
            .addAction(0, "쉬는 날로", button(context, ACTION_REST, card, card.rest))
            .build()
        Notifications.post(context, card.notice, n)
    }

    private fun button(context: Context, action: String, card: Card, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req, Intent(context, MemoMissedReceiver::class.java).setAction(action).putExtra(EXTRA_CARD, card.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 2.0.0 [RVI-99] 모두 끝내기 and 쉬는 날로 on the 놓친 일정 card, from the lock screen too. */
class MemoMissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val pending = goAsync()
        c.appScope.launch {
            try {
                if (intent.action == MemoMissed.ACTION_RELEASE) {
                    MemoMissed.release(context)
                    return@launch
                }
                // 2.0.0 [RVIB-05] The card it came from (근무 중에 온 일정, or 놓친 일정 as before).
                val card = MemoMissed.Card.entries.firstOrNull { it.name == intent.getStringExtra(MemoMissed.EXTRA_CARD) } ?: MemoMissed.Card.MISSED
                val items = MemoMissed.all(context, card)
                MemoMissed.clear(context, card)
                when (intent.action) {
                    MemoMissed.ACTION_DONE -> items.forEach { runCatching { MemoDoneReceiver.finish(context, it.memo, it.day) } }
                    MemoMissed.ACTION_REST -> items.forEach { item ->
                        runCatching {
                            val memo = c.memoRepository.get(item.memo) ?: return@runCatching
                            val rest = MemoSnoozeReceiver.nextRest(c, memo)
                            MemoSnoozeReceiver.put(context, item.memo, com.bamnat.core.memo.MemoSnooze.Way.REST, rest, item.day)
                        }
                    }
                }
                c.telemetry.event("memo_missed", "${intent.action?.substringAfterLast('_')?.lowercase()} n=${items.size} card=${card.name.lowercase()}")
            } finally {
                pending.finish()
            }
        }
    }
}
