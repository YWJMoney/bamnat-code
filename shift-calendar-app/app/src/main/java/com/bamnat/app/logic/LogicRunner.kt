package com.bamnat.app.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.app.appContainer
import com.bamnat.core.formula.DayFactsBook
import com.bamnat.core.formula.FormulaScope
import com.bamnat.core.formula.FormulaText
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.RuleTrigger
import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [RUL-03][RUL-04] 1.36 883·884 버튼과 규칙을 돌리는 곳. A rule's 조건 is worked out on the day's values ([DayFactsBook]); its act is one
 * of the safe verbs. What writes data (메모 만들기·체크 다시·가계부 넣기) keeps one step to take back ([undo]). Each rule's runs in the
 * last 7 days are counted (사용성 제안: "지난 7일 동안 몇 번 돌았는지"), and each can be switched off ([LogicStore]).
 */
object LogicRunner {
    private val _message = MutableStateFlow<String?>(null)
    /** A line to show after a button or a tap rule acted ("가계부에 ‘식비 8,000원’ 넣었어요"), with 되돌리기 when it wrote. */
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun logic(c: AppContainer): Logic = Logic.decode(c.settingsRepository.current.logic)

    /** The day values outside a screen (a rule's time came, a memo was saved): the calendar on screen, read once. */
    suspend fun facts(c: AppContainer): DayFactsBook {
        val cal = c.calendars.active.value
        val scope = c.scopeOf(cal)
        return dayFactsOf(
            scope.schedule.snapshot.first(), c.memoRepository.memos.first().filter { it.showsOn(cal) }, scope.money.book.first(),
            c.settingsRepository.current.workStyle, Today.date(),
        )
    }

    /** Does [act] for [date]; [who] names the rule or the button (for its count and its line). */
    suspend fun run(c: AppContainer, act: LogicAct, date: LocalDate, scope: FormulaScope, who: String, quiet: Boolean = false) {
        val ctx = c.appContext
        val store = LogicStore(ctx)
        val words = FormulaText.render(act.arg, scope)
        when (act.verb) {
            LogicVerb.NOTIFY -> notify(ctx, who, words.ifBlank { who })
            LogicVerb.MEMO_TEMPLATE -> {
                LogicLinks.template = act.arg
                NoticeLinks.open(NoticeLinks.Target.LOGIC_MEMO, payload = date.toString())
            }
            LogicVerb.SCREEN -> when (act.arg) {
                "calendar" -> NoticeLinks.open(NoticeLinks.Target.TODAY)
                "day" -> NoticeLinks.open(NoticeLinks.Target.DAY, payload = date.toString())
                "memo" -> NoticeLinks.open(NoticeLinks.Target.MEMOS)
                "money" -> NoticeLinks.open(NoticeLinks.Target.MONEY)
                "pay", "stats" -> NoticeLinks.open(NoticeLinks.Target.PAY)
                "alarm" -> NoticeLinks.open(NoticeLinks.Target.ALARM_CHECK)
            }
            LogicVerb.NEW_MEMO -> {
                val t = c.memoTemplates.items.value.firstOrNull { it.title == act.arg }
                val base = Memo(calendars = setOf(c.calendars.active.value))
                val memo = t?.start(date, Today.date(), base) ?: base.copy(date = date, title = words.ifBlank { act.arg }.take(60))
                val saved = c.memoRepository.save(memo)
                store.undo = JSONObject().put("memo", saved.id).put("say", act.say()).toString()
            }
            LogicVerb.CHECK_RESET -> {
                val memos = c.memoRepository.memos.first().filter { it.date == date && !it.inTrash && it.isChecklist && (act.arg.isEmpty() || it.title.contains(act.arg)) }
                val before = JSONArray()
                memos.forEach { m ->
                    if (m.checklist.none { it.checked }) return@forEach
                    before.put(JSONObject().put("id", m.id).put("checked", JSONArray(m.checklist.map { it.checked })))
                    c.memoRepository.save(m.copy(checklist = m.checklist.map { it.copy(checked = false) }))
                }
                if (before.length() > 0) store.undo = JSONObject().put("checks", before).put("say", act.say()).toString()
            }
            LogicVerb.ADD_MONEY -> {
                val amount = act.amount ?: return
                val entry = c.scopeOf(c.calendars.active.value).money.save(
                    MoneyEntry(date = date, kind = MoneyKind.EXPENSE, amount = amount, category = act.arg.ifBlank { "기타" }, memo = "규칙·단추: $who"),
                )
                store.undo = JSONObject().put("money", entry.id).put("cal", c.calendars.active.value).put("say", act.say()).toString()
            }
        }
        store.ran(who)
        if (!quiet && act.verb != LogicVerb.SCREEN && act.verb != LogicVerb.MEMO_TEMPLATE) {
            _message.value = when (act.verb) {
                LogicVerb.NOTIFY -> words.ifBlank { who }
                LogicVerb.NEW_MEMO -> "‘${act.arg.ifEmpty { "메모" }}’ 메모를 만들었어요"
                LogicVerb.CHECK_RESET -> "‘${act.arg.ifEmpty { "체크 목록" }}’ 체크를 다시 비웠어요"
                LogicVerb.ADD_MONEY -> "가계부에 ‘${act.arg.ifEmpty { "기타" }}’ ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                else -> act.say()
            }
        }
    }

    /** Takes back the last thing a button or rule wrote; false when there is nothing to take back. */
    suspend fun undo(c: AppContainer): Boolean {
        val store = LogicStore(c.appContext)
        val o = store.undo?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        store.undo = null
        when {
            o.has("memo") -> c.memoRepository.get(o.getLong("memo"))?.let { c.memoRepository.delete(it, offerUndo = false) }
            o.has("money") -> c.scopeOf(o.optString("cal", c.calendars.active.value)).money.book.first().entries
                .firstOrNull { it.id == o.getLong("money") }?.let { c.scopeOf(o.optString("cal")).money.delete(it) }
            o.has("checks") -> {
                val a = o.getJSONArray("checks")
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    val m = c.memoRepository.get(x.getLong("id")) ?: continue
                    val checked = x.getJSONArray("checked")
                    c.memoRepository.save(m.copy(checklist = m.checklist.mapIndexed { j, it -> it.copy(checked = j < checked.length() && checked.getBoolean(j)) }))
                }
            }
        }
        return true
    }

    /** The rules for [on] that are switched on and whose 조건 holds on [date]. */
    fun due(c: AppContainer, on: RuleTrigger, date: LocalDate, facts: DayFactsBook): List<LogicRule> {
        val store = LogicStore(c.appContext)
        val scope = facts.scope(date)
        return logic(c).rules.filter { r ->
            r.on == on && !store.isOff(r.key) && (r.cond.isEmpty() || com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy == true)
        }
    }

    /** 날짜를 누르면: the tap rules that hold run (a screen or 메모 틀 the first only, so one tap opens one thing). */
    fun onTap(c: AppContainer, date: LocalDate, facts: DayFactsBook?) {
        facts ?: return
        val rules = due(c, RuleTrigger.TAP, date, facts)
        if (rules.isEmpty()) return
        c.appScope.launch {
            var opened = false
            for (r in rules) {
                val opens = r.act.verb == LogicVerb.SCREEN || r.act.verb == LogicVerb.MEMO_TEMPLATE
                if (opens && opened) continue
                opened = opened || opens
                runCatching { run(c, r.act, date, facts.scope(date), r.name) }
            }
        }
    }

    /** 메모를 저장하면 (the memo's own day) and 하루 처음 앱을 열면 (today, once a day). */
    fun onMemoSaved(c: AppContainer, date: LocalDate?) {
        if (logic(c).rules.none { it.on == RuleTrigger.MEMO }) return
        c.appScope.launch {
            val f = facts(c)
            val d = date ?: Today.date()
            due(c, RuleTrigger.MEMO, d, f).forEach { runCatching { run(c, it.act, d, f.scope(d), it.name, quiet = true) } }
        }
    }

    fun onOpen(c: AppContainer) {
        val store = LogicStore(c.appContext)
        val today = Today.date()
        if (store.lastOpen == today.toString() || logic(c).rules.none { it.on == RuleTrigger.OPEN }) return
        store.lastOpen = today.toString()
        c.appScope.launch {
            val f = facts(c)
            due(c, RuleTrigger.OPEN, today, f).forEach { runCatching { run(c, it.act, today, f.scope(today), it.name) } }
        }
    }

    // ---- timed rules: 근무 시작·끝 전후, 매일 몇 시 ----

    /** When [r] next comes after [after], looking two days ahead; null when it does not come. */
    fun nextTime(r: LogicRule, after: LocalDateTime, facts: DayFactsBook): Pair<LocalDateTime, LocalDate>? {
        for (k in -1..2) {
            val day = after.toLocalDate().plusDays(k.toLong())
            val at: LocalDateTime? = when (r.on) {
                RuleTrigger.DAILY -> runCatching { LocalTime.parse(r.at ?: "09:00") }.getOrNull()?.let { day.atTime(it) }
                RuleTrigger.START, RuleTrigger.END -> {
                    val v = facts.values(day)
                    val kind = v[com.bamnat.core.formula.FormulaName.ROLE.ko] as? String
                    val off = v[com.bamnat.core.formula.FormulaName.OFF.ko] == true
                    val wanted = r.role?.let { DayFactsBook.ROLE_WORDS[it] }
                    if (off && r.role != "off" || wanted != null && kind != wanted) null else {
                        val start = (v[com.bamnat.core.formula.FormulaName.START.ko] as? String)?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse)
                        val end = (v[com.bamnat.core.formula.FormulaName.END.ko] as? String)?.takeIf { it.isNotEmpty() }?.let(LocalTime::parse)
                        if (r.on == RuleTrigger.START) start?.let { day.atTime(it) }
                        else end?.let { e -> if (start != null && !e.isAfter(start)) day.plusDays(1).atTime(e) else day.atTime(e) }
                    }
                }
                else -> null
            }?.plusMinutes(r.min.toLong())
            if (at != null && at.isAfter(after)) return at to day
        }
        return null
    }

    /** Registers the next timed rule (one alarm; when it comes, the one after is registered). */
    fun schedule(c: AppContainer) {
        c.appScope.launch {
            val ctx = c.appContext
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return@launch
            val store = LogicStore(ctx)
            val timed = logic(c).rules.filter { (it.on == RuleTrigger.DAILY || it.on == RuleTrigger.START || it.on == RuleTrigger.END) && !store.isOff(it.key) }
            val op = PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, LogicReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (timed.isEmpty()) { am.cancel(op); return@launch }
            val f = runCatching { facts(c) }.getOrNull() ?: return@launch
            val now = Today.dateTime()
            val next = timed.mapNotNull { r -> nextTime(r, now, f)?.first }.minOrNull()
            if (next == null) { am.cancel(op); return@launch }
            val at = next.atZone(Today.zone()).toInstant().toEpochMilli()
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op) }
        }
    }

    /** The registered time came: the timed rules due within this minute run, then the next is registered. */
    internal suspend fun fire(c: AppContainer) {
        val f = facts(c)
        val now = Today.dateTime()
        val store = LogicStore(c.appContext)
        for (r in logic(c).rules) {
            if (store.isOff(r.key) || r.on !in setOf(RuleTrigger.DAILY, RuleTrigger.START, RuleTrigger.END)) continue
            val (at, day) = nextTime(r, now.minusMinutes(2), f) ?: continue
            if (at.isAfter(now.plusMinutes(1))) continue
            if (store.firedAt(r.key) == at.toString()) continue
            store.fired(r.key, at.toString())
            val scope = f.scope(day)
            if (r.cond.isNotEmpty() && com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy != true) continue
            runCatching { run(c, r.act, day, scope, r.name, quiet = true) }
        }
        schedule(c)
    }

    private fun notify(ctx: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, Notifications.RULES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.open(ctx, NoticeLinks.Target.TODAY, REQ + 1))
            .setAutoCancel(true)
            .build()
        Notifications.post(ctx, NOTICE_ID, n, tag = title)
    }

    private const val REQ = 2601
    private const val NOTICE_ID = 7601
}

/** A 메모 틀 a button or rule asked to open; the new memo's editor takes it once. */
object LogicLinks {
    @Volatile var template: String? = null
    fun takeTemplate(): String? = template.also { template = null }
}

class LogicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.appContainer
        val done = goAsync()
        c.appScope.launch {
            try { runCatching { LogicRunner.fire(c) } } finally { done.finish() }
        }
    }
}

/** Each rule's switch, its runs in the last 7 days, the last thing written (되돌리기) and the day the app was last opened. */
class LogicStore(context: Context) {
    private val p = context.getSharedPreferences("logic_rules", Context.MODE_PRIVATE)

    fun isOff(key: String): Boolean = key in p.getStringSet("off", emptySet()).orEmpty()
    fun setOff(key: String, off: Boolean) {
        val s = p.getStringSet("off", emptySet()).orEmpty().toMutableSet()
        if (off) s += key else s -= key
        p.edit().putStringSet("off", s).apply()
    }

    /** Runs of [who] in the last 7 days. */
    fun runs(who: String): Int {
        val since = Today.millis() - 7 * 86_400_000L
        return times(who).count { it >= since }
    }

    fun ran(who: String) {
        val since = Today.millis() - 7 * 86_400_000L
        val list = (times(who).filter { it >= since } + Today.millis()).takeLast(200)
        p.edit().putString("runs.$who", list.joinToString(",")).apply()
    }

    private fun times(who: String): List<Long> = p.getString("runs.$who", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun firedAt(key: String): String? = p.getString("fired.$key", null)
    fun fired(key: String, at: String) = p.edit().putString("fired.$key", at).apply()

    var undo: String?
        get() = p.getString("undo", null)
        set(v) { p.edit().putString("undo", v).apply() }

    var lastOpen: String?
        get() = p.getString("open", null)
        set(v) { p.edit().putString("open", v).apply() }
}
