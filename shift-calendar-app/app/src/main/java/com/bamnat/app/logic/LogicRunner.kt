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
    /** A line for the screen that just acted (모두 되돌리기). */
    fun say(line: String) { _message.value = line }

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

    /**
     * 1.36 [SEC-12] 보안 점검 M2: 안전 모드 (the app stopped three times in a row) runs no rule and no button, so a received rule cannot be
     * what keeps it from opening; 평소대로 brings them back.
     */
    private val resting: Boolean get() = com.bamnat.app.diag.StartGuard.safe.value

    /** Does [act] for [date]; [who] names the rule or the button (for its count and its line). */
    suspend fun run(c: AppContainer, act: LogicAct, date: LocalDate, scope: FormulaScope, who: String, quiet: Boolean = false) {
        if (resting) { if (!quiet) _message.value = "안전 모드라 규칙과 단추는 쉬어요. 달력 위 ‘평소대로’를 누르면 다시 돌아요"; return }
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
                val saved = c.memoRepository.save(fillMemo(c, memo))
                store.wrote(who, JSONObject().put("memo", saved.id).put("say", act.say()))
            }
            LogicVerb.CHECK_RESET -> {
                val memos = c.memoRepository.memos.first().filter { it.date == date && !it.inTrash && it.isChecklist && (act.arg.isEmpty() || it.title.contains(act.arg)) }
                val before = JSONArray()
                memos.forEach { m ->
                    if (m.checklist.none { it.checked }) return@forEach
                    before.put(JSONObject().put("id", m.id).put("checked", JSONArray(m.checklist.map { it.checked })))
                    c.memoRepository.save(m.copy(checklist = m.checklist.map { it.copy(checked = false) }))
                }
                if (before.length() > 0) store.wrote(who, JSONObject().put("checks", before).put("say", act.say()))
            }
            LogicVerb.ADD_MONEY, LogicVerb.ADD_INCOME -> {
                val amount = act.amount ?: return
                val entry = c.scopeOf(c.calendars.active.value).money.save(
                    MoneyEntry(date = date, kind = if (act.verb == LogicVerb.ADD_INCOME) MoneyKind.INCOME else MoneyKind.EXPENSE, amount = amount,
                        category = act.arg.ifBlank { "기타" }, memo = "규칙·단추: $who"),
                )
                store.wrote(who, JSONObject().put("money", entry.id).put("cal", c.calendars.active.value).put("say", act.say()))
            }
            // 사용자 2026-11-01 "메모와 달력을 연동": the day's first memo gets a line made from its values; none, a new one.
            LogicVerb.MEMO_LINE -> {
                val line = words.ifBlank { return }.take(200)
                val m = c.memoRepository.memos.first().firstOrNull { it.date == date && !it.inTrash && !it.isChecklist && it.showsOn(c.calendars.active.value) }
                if (m != null) {
                    c.memoRepository.save(m.copy(body = listOf(m.body, line).filter { it.isNotEmpty() }.joinToString("\n")))
                    store.wrote(who, JSONObject().put("line", m.id).put("body", m.body).put("text", line).put("say", act.say()))
                } else {
                    val saved = c.memoRepository.save(Memo(date = date, title = line.take(60), body = if (line.length > 60) line else "",
                        calendars = setOf(c.calendars.active.value)))
                    store.wrote(who, JSONObject().put("memo", saved.id).put("say", act.say()))
                }
            }
        }
        store.ran(who)
        // 1.36 [SEC-12] 보안 점검 M4: a rule that wrote while nobody looked says so in a quiet notice (one per rule, the last).
        if (quiet && act.verb.writes) wroteQuietly(ctx, who, act)
        if (!quiet && act.verb != LogicVerb.SCREEN && act.verb != LogicVerb.MEMO_TEMPLATE) {
            _message.value = when (act.verb) {
                LogicVerb.NOTIFY -> words.ifBlank { who }
                LogicVerb.NEW_MEMO -> "‘${act.arg.ifEmpty { "메모" }}’ 메모를 만들었어요"
                LogicVerb.CHECK_RESET -> "‘${act.arg.ifEmpty { "체크 목록" }}’ 체크를 다시 비웠어요"
                LogicVerb.ADD_MONEY -> "가계부에 ‘${act.arg.ifEmpty { "기타" }}’ ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                LogicVerb.ADD_INCOME -> "가계부 수입에 ‘${act.arg.ifEmpty { "기타" }}’ ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                LogicVerb.MEMO_LINE -> "그날 메모에 ‘${words.take(20)}’ 줄을 더했어요"
                else -> act.say()
            }
        }
    }

    /** Takes back the last thing a button or rule wrote; false when there is nothing to take back. */
    suspend fun undo(c: AppContainer): Boolean {
        val store = LogicStore(c.appContext)
        val o = store.undo?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        store.undo = null
        store.forgetWritten(o)
        takeBack(c, o)
        return true
    }

    /**
     * 1.36 [SEC-12] 보안 점검 M4 '규칙이 쓴 것 모두 되돌리기': everything the rules and buttons wrote in the last 7 days, newest first
     * (a memo line comes out of the memo, an entry out of the 가계부). Answers how many.
     */
    suspend fun undoAll(c: AppContainer): Int {
        val store = LogicStore(c.appContext)
        val all = store.written()
        store.undo = null
        store.clearWritten()
        var n = 0
        for (o in all.asReversed()) if (runCatching { takeBack(c, o) }.isSuccess) n++
        return n
    }

    private suspend fun takeBack(c: AppContainer, o: JSONObject) {
        when {
            // 1.36 [SEC-12] Only the line the rule added comes out (what was written after it stays), and the words before if it is not found.
            o.has("line") -> c.memoRepository.get(o.getLong("line"))?.let { m ->
                val text = o.optString("text")
                val lines = m.body.lines()
                val i = if (text.isEmpty()) -1 else lines.lastIndexOf(text)
                c.memoRepository.save(m.copy(body = if (i >= 0) lines.filterIndexed { j, _ -> j != i }.joinToString("\n") else o.optString("body")))
            }
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
        if (resting) return
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

    /**
     * 누락 점검 N8 날짜를 길게 누르면: the hold rules that hold run, as [onTap]. True when one of them opens a screen or a 메모 틀, so
     * the calendar leaves its own 빠른 설정 closed (one long press opens one thing).
     */
    fun onHold(c: AppContainer, date: LocalDate, facts: DayFactsBook?): Boolean {
        facts ?: return false
        if (resting) return false
        val rules = due(c, RuleTrigger.HOLD, date, facts)
        if (rules.isEmpty()) return false
        val opens = rules.any { it.act.verb == LogicVerb.SCREEN || it.act.verb == LogicVerb.MEMO_TEMPLATE }
        c.appScope.launch {
            var opened = false
            for (r in rules) {
                val o = r.act.verb == LogicVerb.SCREEN || r.act.verb == LogicVerb.MEMO_TEMPLATE
                if (o && opened) continue
                opened = opened || o
                runCatching { run(c, r.act, date, facts.scope(date), r.name) }
            }
        }
        return opens
    }

    /**
     * 누락 점검 N8 꾸밈 층 단추: the button [id] ([com.bamnat.core.formula.LogicButton.id], a 꾸밈 항목's `tap`) does its one thing on
     * [date]. False when there is no such button or its 보이기 조건 is false that day (the 꾸밈 stays a picture). Not suspending: 갈래 C
     * calls it from a tap.
     */
    fun press(c: AppContainer, id: String, date: LocalDate, facts: DayFactsBook? = null): Boolean {
        val b = logic(c).button(id) ?: return false
        fun holds(f: DayFactsBook) = b.show.isEmpty() || com.bamnat.core.formula.Formula.eval(b.show, f.scope(date))?.truthy == true
        if (facts != null && !holds(facts)) return false
        c.appScope.launch {
            val f = facts ?: runCatching { facts(c) }.getOrNull() ?: return@launch
            if (!holds(f)) { _message.value = "‘${b.label}’ 단추는 이 날 조건에 맞지 않아요"; return@launch }
            runCatching { run(c, b.act, date, f.scope(date), b.label) }
        }
        return true
    }

    /** 메모를 저장하면 (the memo's own day) and 하루 처음 앱을 열면 (today, once a day). */
    fun onMemoSaved(c: AppContainer, date: LocalDate?) {
        if (resting || logic(c).rules.none { it.on == RuleTrigger.MEMO }) return
        c.appScope.launch {
            val f = facts(c)
            val d = date ?: Today.date()
            due(c, RuleTrigger.MEMO, d, f).forEach { runCatching { run(c, it.act, d, f.scope(d), it.name, quiet = true) } }
        }
    }

    /** A memo from a 메모 틀: {수식} in its title, words and list worked out on its day (오늘 when it has none). */
    suspend fun fillMemo(c: AppContainer, m: Memo): Memo {
        if ('{' !in m.title && '{' !in m.body && m.checklist.none { '{' in it.text }) return m
        val scope = runCatching { facts(c).scope(m.date ?: Today.date()) }.getOrNull() ?: return m
        return m.copy(
            title = FormulaText.fill(m.title, scope), body = FormulaText.fill(m.body, scope),
            checklist = m.checklist.map { it.copy(text = FormulaText.fill(it.text, scope)) },
        )
    }

    /** 가계부에 적으면 (사용자 2026-11-01 "가계부와 연동"): the entry's day. */
    fun onMoneySaved(c: AppContainer, date: LocalDate) {
        if (resting || logic(c).rules.none { it.on == RuleTrigger.MONEY }) return
        c.appScope.launch {
            val f = facts(c)
            due(c, RuleTrigger.MONEY, date, f).forEach { runCatching { run(c, it.act, date, f.scope(date), it.name, quiet = true) } }
        }
    }

    fun onOpen(c: AppContainer) {
        val store = LogicStore(c.appContext)
        val today = Today.date()
        if (resting || store.lastOpen == today.toString() || logic(c).rules.none { it.on == RuleTrigger.OPEN }) return
        store.lastOpen = today.toString()
        c.appScope.launch {
            val f = facts(c)
            due(c, RuleTrigger.OPEN, today, f).forEach { runCatching { run(c, it.act, today, f.scope(today), it.name) } }
        }
    }

    // ---- timed rules: 근무 시작·끝 전후, 매일 몇 시 ----

    /** When [r] next comes after [after], looking two weeks ahead; null when it does not come in them. */
    fun nextTime(r: LogicRule, after: LocalDateTime, facts: DayFactsBook): Pair<LocalDateTime, LocalDate>? {
        for (k in -1..14) {
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
            // PC D9: 몇 분 전·뒤는 근무 시작·끝에만 (근무 시작에서 '매일'로 바꾼 규칙이 07:00인데 06:00에 오지 않게).
            }?.plusMinutes(if (r.on == RuleTrigger.START || r.on == RuleTrigger.END) r.min.toLong() else 0L)
            if (at != null && at.isAfter(after)) return at to day
        }
        return null
    }

    /** The app started: what came while it was away (phone off, clock moved) is run or said as missed, then the next is registered. */
    fun catchUp(c: AppContainer) {
        c.appScope.launch { runCatching { fire(c) }.onFailure { schedule(c) } }
    }

    /** The rules were changed: only times from now on count, then the next is registered. */
    fun rulesChanged(c: AppContainer) {
        LogicStore(c.appContext).checked = Today.dateTime().toString()
        schedule(c)
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
            // Nothing in two weeks (no night shift yet): look again in a day, so a 근무표 filled in later is not missed.
            val next = timed.mapNotNull { r -> nextTime(r, now, f)?.first }.minOrNull() ?: now.plusDays(1)
            val at = next.atZone(Today.zone()).toInstant().toEpochMilli()
            // PC 1차 확인: '몇 분 전' 규칙은 근무 알람처럼 제시각에 (an inexact alarm may come an hour late).
            runCatching {
                if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
            }
            store.scheduled = next.toString()
        }
    }

    /**
     * The registered time came (or the clock moved, or the app woke late): each timed rule whose time fell between the last check and
     * now runs once; one that came more than [LATE_MINUTES] ago is said as missed instead (the phone was off, the clock jumped). Then the
     * next is registered.
     */
    internal suspend fun fire(c: AppContainer) {
        // 안전 모드: the times that come are let pass (not run late after 평소대로), and the next is registered.
        if (resting) { LogicStore(c.appContext).checked = Today.dateTime().toString(); schedule(c); return }
        val f = facts(c)
        val now = Today.dateTime()
        val store = LogicStore(c.appContext)
        val since = store.checked?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?.takeIf { !it.isAfter(now) && it.isAfter(now.minusDays(2)) } ?: now.minusMinutes(2)
        for (r in logic(c).rules) {
            if (store.isOff(r.key) || r.on !in TIMED) continue
            val (at, day) = nextTime(r, since.minusSeconds(1), f) ?: continue
            if (at.isAfter(now.plusMinutes(1))) continue
            if (store.firedAt(r.key) == at.toString()) continue
            store.fired(r.key, at.toString())
            if (at.isBefore(now.minusMinutes(LATE_MINUTES))) { store.result(r.key, at, "휴대폰이 꺼져 있었거나 시계가 바뀌어 놓쳤어요"); continue }
            val scope = f.scope(day)
            if (r.cond.isNotEmpty() && com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy != true) {
                store.result(r.key, at, "조건이 맞지 않아 건너뛰었어요"); continue
            }
            val ok = runCatching { run(c, r.act, day, scope, r.name, quiet = true) }
            store.result(r.key, at, when {
                ok.isFailure -> "하다가 멈췄어요"
                r.act.verb == LogicVerb.NOTIFY && !Notifications.canPost(c.appContext) -> "알림이 꺼져 있어 못 띄웠어요(설정 › 알림과 잠)"
                else -> "했어요"
            })
        }
        store.checked = now.toString()
        schedule(c)
    }

    private val TIMED = setOf(RuleTrigger.DAILY, RuleTrigger.START, RuleTrigger.END)
    private const val LATE_MINUTES = 60L

    private fun notify(ctx: Context, title: String, text: String, quietly: Boolean = false) {
        // 1.36 [SEC-12] 보안 점검 M5: on the lock screen only "규칙 알림" (the words may hold the 가계부's numbers).
        val public = NotificationCompat.Builder(ctx, Notifications.RULES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("밤낮 규칙 알림")
            .setContentText("휴대폰을 열면 보여요")
            .build()
        val n = NotificationCompat.Builder(ctx, Notifications.RULES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notifications.open(ctx, NoticeLinks.Target.TODAY, REQ + 1))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setSilent(quietly)
            .setAutoCancel(true)
            .build()
        Notifications.post(ctx, NOTICE_ID, n, tag = title)
    }

    /** 1.36 [SEC-12] "규칙 ‘월급날 저축’: 가계부에 ‘저축 100,000원’ 넣기를 했어요" without a sound, with where to take it back. */
    private fun wroteQuietly(ctx: Context, who: String, act: LogicAct) =
        notify(ctx, "규칙 ‘$who’", "${act.say()}를 했어요. 설정 › 알람 › 수식·규칙·버튼의 ‘규칙이 쓴 것’에서 되돌릴 수 있어요", quietly = true)

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
        val got = p.getStringSet("gotOff", emptySet()).orEmpty().toMutableSet()
        if (!off) got -= key
        p.edit().putStringSet("off", s).putStringSet("gotOff", got).apply()
    }

    /** [SEC-12] Someone else's rule that writes, come in switched off; PC D8: its line says so, with 켜기, until it is switched on. */
    fun gotOff(key: String) {
        setOff(key, true)
        p.edit().putStringSet("gotOff", p.getStringSet("gotOff", emptySet()).orEmpty() + key).apply()
    }
    fun cameOff(key: String): Boolean = key in p.getStringSet("gotOff", emptySet()).orEmpty() && isOff(key)

    /**
     * PC D7 (사용성 제안): a received rule or button kept once because one of mine does the same ([Logic.plus]), its name on my line
     * ("받은 ‘A 야간 수당’과 같음") so the one that came in is not lost from sight.
     */
    fun noteSame(mine: Logic, got: Logic) {
        val e = p.edit()
        got.rules.forEach { r -> mine.rules.firstOrNull { it.key == r.key }?.let { e.putString("same.r.${it.key}", r.name) } }
        got.buttons.forEach { b -> mine.buttons.firstOrNull { it.toMap() == b.toMap() }?.let { e.putString("same.b.${it.id}", b.label) } }
        e.apply()
    }
    fun sameRule(key: String): String? = p.getString("same.r.$key", null)
    fun sameButton(id: String): String? = p.getString("same.b.$id", null)

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

    /** "11/13 18:00 · 했어요": the last time [key] came and what happened (규칙 줄에 보여요). */
    fun result(key: String, at: LocalDateTime, what: String) =
        p.edit().putString("result.$key", "${at.monthValue}/${at.dayOfMonth} ${"%02d:%02d".format(at.hour, at.minute)} · $what").apply()
    fun lastResult(key: String): String? = p.getString("result.$key", null)

    /** The last time the timed rules were looked at, and the next registered. */
    var checked: String?
        get() = p.getString("checked", null)
        set(v) { p.edit().putString("checked", v).apply() }
    var scheduled: String?
        get() = p.getString("scheduled", null)
        set(v) { p.edit().putString("scheduled", v).apply() }

    fun firedAt(key: String): String? = p.getString("fired.$key", null)
    fun fired(key: String, at: String) = p.edit().putString("fired.$key", at).apply()

    var undo: String?
        get() = p.getString("undo", null)
        set(v) { p.edit().putString("undo", v).apply() }

    /**
     * 1.36 [SEC-12] 보안 점검 M4·M5: what [who] wrote ([o], as [undo] keeps it), kept for '규칙이 쓴 것' (the last 7 days, at most
     * [WRITTEN_MAX]) and for share links, which leave out the memos and lines the rules made.
     */
    fun wrote(who: String, o: JSONObject) {
        o.put("who", who).put("at", Today.millis())
        undo = o.toString()
        val keep = written().filter { it.optLong("at") >= Today.millis() - 7 * 86_400_000L }.takeLast(WRITTEN_MAX - 1) + o
        p.edit().putString("written", JSONArray(keep.map { it.toString() }).toString()).apply()
        o.optLong("memo", -1L).takeIf { it >= 0 }?.let { made(it, null) }
        if (o.has("line")) made(o.getLong("line"), o.optString("text"))
    }

    fun written(): List<JSONObject> = runCatching {
        val a = JSONArray(p.getString("written", "[]"))
        (0 until a.length()).mapNotNull { runCatching { JSONObject(a.getString(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun forgetWritten(o: JSONObject) {
        val at = o.optLong("at", -1L)
        p.edit().putString("written", JSONArray(written().filterNot { it.optLong("at") == at && it.optString("who") == o.optString("who") }.map { it.toString() }).toString()).apply()
    }

    fun clearWritten() = p.edit().remove("written").apply()

    /** A memo a rule made ([line] null), or a line a rule added to one. */
    private fun made(memo: Long, line: String?) {
        val o = runCatching { JSONObject(p.getString("made", "{}")) }.getOrDefault(JSONObject())
        val key = memo.toString()
        val a = o.optJSONArray(key) ?: JSONArray()
        a.put(line ?: "*")
        // At most the last 20 a memo, and 300 memos.
        o.put(key, JSONArray((0 until a.length()).map { a.getString(it) }.takeLast(20)))
        val keys = o.keys().asSequence().toList()
        if (keys.size > 300) keys.take(keys.size - 300).forEach { o.remove(it) }
        p.edit().putString("made", o.toString()).apply()
    }

    /** Whether a rule made memo [id], and the lines rules added to it (for share links). */
    fun madeMemo(id: Long): Boolean = madeOf(id).contains("*")
    fun madeLines(id: Long): Set<String> = madeOf(id).filter { it != "*" }.toSet()
    private fun madeOf(id: Long): List<String> = runCatching {
        val a = JSONObject(p.getString("made", "{}")).optJSONArray(id.toString()) ?: return emptyList()
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        const val WRITTEN_MAX = 60
    }

    var lastOpen: String?
        get() = p.getString("open", null)
        set(v) { p.edit().putString("open", v).apply() }
}
