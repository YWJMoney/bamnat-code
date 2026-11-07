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
import com.bamnat.core.formula.pro
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
import kotlinx.coroutines.sync.withLock
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

    /**
     * The buttons and rules that run now. 1.39 [FX-01]: without 프로 or a running 하루 써 보기 only [com.bamnat.core.formula.FxPro.free];
     * [all] is everything kept (the 스튜디오, the 값 칸 definitions).
     */
    fun logic(c: AppContainer, all: Boolean = false): Logic = Logic.decode(c.settingsRepository.current.logic).let { if (all) it else FxAccess.runnable(c, it) }

    /** The day values outside a screen (a rule's time came, a memo was saved): the calendar on screen, read once. */
    suspend fun facts(c: AppContainer): DayFactsBook {
        val cal = c.calendars.active.value
        val scope = c.scopeOf(cal)
        // [FXR-01][FXR-02] With the 받는 칸 answers and the calendar's pay, as the screens have them.
        return dayFactsOf(
            scope.schedule.snapshot.first(), c.memoRepository.memos.first().filter { it.showsOn(cal) }, scope.money.book.first(),
            c.settingsRepository.current.workStyle, Today.date(), c.formulaExtras(cal).first(),
        )
    }

    /**
     * 1.36 [SEC-12] 보안 점검 M2: 안전 모드 (the app stopped three times in a row) runs no rule and no button, so a received rule cannot be
     * what keeps it from opening; 평소대로 brings them back.
     */
    private val resting: Boolean get() = com.bamnat.app.diag.StartGuard.safe.value

    /** 1.37 PC 4차: the app is on screen (its message line can show 되돌리기), set by [LogicMessageHost]. */
    @Volatile var onScreen: Boolean = false

    /** 1.39 [FX-01] [act] is one of what runs now ([logic]: 1.38 things and the free 맛보기), its 알림 단추 and wait aside. */
    private fun runsNow(c: AppContainer, act: LogicAct): Boolean {
        fun k(a: LogicAct) = a.copy(then = emptyList(), after = null)
        val l = logic(c)
        val want = k(act)
        return l.buttons.any { b -> b.acts.any { k(it) == want } } || l.rules.any { r -> r.acts.any { k(it) == want } }
    }

    /** Does [act] for [date]; [who] names the rule or the button (for its count and its line). */
    suspend fun run(c: AppContainer, act: LogicAct, date: LocalDate, scope: FormulaScope, who: String, quiet: Boolean = false, depth: Int = 0,
                    trusted: Boolean = false) {
        if (resting) {
            LogicStore(c.appContext).log(who, "안전 모드라 쉬었어요")
            if (!quiet) _message.value = "안전 모드라 규칙과 단추는 쉬어요. ‘평소대로’를 누르면 다시 돌아요"; return
        }
        val ctx = c.appContext
        val store = LogicStore(ctx)
        val words = FormulaText.render(act.arg, scope)
        // 1.37 PC 5차: 이름을 비운 봉투 규칙은 "‘첫’ 봉투"라고 했어요. 실제로 넣은 봉투 이름으로 말해요.
        var envName: String? = null
        // 1.39 [FX-01] A 기능 장치 act (값 더하기·정하기, 알림 단추) needs 프로 or a running 하루 써 보기 (a press from an old widget too).
        if (!trusted && !FxAccess.on(c) && !runsNow(c, act)) {
            store.log(who, PRO_REST)
            if (!quiet || onScreen) _message.value = "‘$who’은 프로 기능 장치라 지금은 쉬어요. 작품마다 하루 써 볼 수 있어요"
            return
        }
        // 1.39 [FX-15] 기다렸다 하기: kept and done at its time, the app open or not.
        act.after?.let { m ->
            later(c, act, date, who)
            store.log(who, "${LogicAct.afterWords(m)} 뒤에 ‘${act.now().say()}’를 해요")
            if (!quiet || onScreen) _message.value = "${LogicAct.afterWords(m)} 뒤에 ${act.now().say()}를 해요"
            return
        }
        if (act.verb.pro || act.then.isNotEmpty()) FxAccess.note(c, act.verb)
        var valueAfter: String? = null
        var changed: Triple<String, Double?, Double>? = null
        when (act.verb) {
            LogicVerb.NOTIFY -> notify(ctx, who, words.ifBlank { who }, buttons = act.then.mapNotNull { id -> logic(c).button(id) }, date = date,
                again = act.then.filter { it in LogicAct.AGAIN })
            // 1.39 [FX-03][FX-13] 값 칸: the number (and words) before are kept for 되돌리기; then 값이 바뀌면 rules ([valueChanged]).
            LogicVerb.VAL_ADD, LogicVerb.VAL_SET, LogicVerb.VAL_STAMP, LogicVerb.VAL_TEXT -> {
                val before = FxValues.kept(c, act.arg)
                val def = logic(c, all = true).value(act.arg)
                val was = def?.let { com.bamnat.core.formula.ValueBook.now(before, it, Today.date()) }
                val after = when (act.verb) {
                    LogicVerb.VAL_SET -> FxValues.change(c, act.arg, to = act.by ?: 0.0)
                    LogicVerb.VAL_STAMP -> FxValues.change(c, act.arg, stamp = true)
                    LogicVerb.VAL_TEXT -> FxValues.change(c, act.arg, text = FormulaText.render(act.text, scope))
                    else -> FxValues.change(c, act.arg, by = act.by ?: 1.0)
                }
                if (after == null) { store.log(who, "‘${act.arg}’ 값 칸이 없어 못 했어요"); if (!quiet || onScreen) _message.value = "‘${act.arg}’ 값 칸이 없어요. 스튜디오 › 규칙·단추 › 값 칸에서 만들어요"; return }
                store.wrote(who, JSONObject().put("val", act.arg).put("v", before?.v ?: Double.NaN).put("p", before?.period.orEmpty())
                    .put("t", before?.t.orEmpty()).put("say", act.say()))
                valueAfter = FxValues.show(c, act.arg)
                FxOutside.changed(c)
                changed = Triple(act.arg, was, after)
            }
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
            // 1.37 [LEDH-12] 봉투에 넣기: the envelope by name, or the first open one; none, nothing happens (said in the message).
            LogicVerb.ENVELOPE -> {
                val amount = act.amount?.takeIf { it > 0 } ?: return
                val cal = c.calendars.active.value
                val money = c.scopeOf(cal).money
                val open = money.envelopes.first().first.filter { !it.closed }.sortedWith(compareBy({ it.order }, { it.id }))
                val e = open.firstOrNull { it.name == act.arg.trim() } ?: open.firstOrNull()
                if (e == null) { store.log(who, "열린 봉투가 없어 못 했어요"); if (!quiet || onScreen) _message.value = "열린 봉투가 없어 넣지 못했어요. 가계부 › 봉투에서 하나 만들어요"; return }
                envName = e.name
                val id = money.moveEnvelope(com.bamnat.core.money.EnvelopeMove(envelopeId = e.id, date = date, amount = amount))
                store.wrote(who, JSONObject().put("envMove", id).put("cal", cal).put("say", act.say()))
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
        store.log(who, "${act.say()}를 했어요")
        // 1.36 [SEC-12] 보안 점검 M4: a rule that wrote while nobody looked says so in a quiet notice (one per rule, the last).
        // 1.37 PC 4차: 시각 규칙은 늘 조용히 돌아 앱을 보고 있어도 되돌리기가 안 나왔어요. 화면이 보이면 쓴 것은 되돌리기 줄로 말해요.
        val seen = !quiet || (act.verb.writes && onScreen)
        if (quiet && act.verb.writes && !seen) wroteQuietly(ctx, who, act)
        if (seen && act.verb != LogicVerb.SCREEN && act.verb != LogicVerb.MEMO_TEMPLATE) {
            _message.value = when (act.verb) {
                LogicVerb.NOTIFY -> words.ifBlank { who }
                LogicVerb.NEW_MEMO -> "‘${act.arg.ifEmpty { "메모" }}’ 메모를 만들었어요"
                LogicVerb.CHECK_RESET -> "‘${act.arg.ifEmpty { "체크 목록" }}’ 체크를 다시 비웠어요"
                LogicVerb.ADD_MONEY -> "가계부에 ‘${act.arg.ifEmpty { "기타" }}’ ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                LogicVerb.ADD_INCOME -> "가계부 수입에 ‘${act.arg.ifEmpty { "기타" }}’ ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                LogicVerb.MEMO_LINE -> "그날 메모에 ‘${words.take(20)}’ 줄을 더했어요"
                LogicVerb.ENVELOPE -> "‘${envName ?: act.arg}’ 봉투에 ${"%,d".format(act.amount ?: 0L)}원을 넣었어요"
                LogicVerb.VAL_ADD, LogicVerb.VAL_SET, LogicVerb.VAL_STAMP, LogicVerb.VAL_TEXT -> "‘${act.arg}’ ${valueAfter ?: ""}"
                else -> act.say()
            }
        }
        changed?.let { (name, was, now) -> valueChanged(c, name, was, now, date, depth) }
    }

    /**
     * 1.39 [FX-11] 동작 줄: [acts] one after another (a later one reads what an earlier one counted), a screen or a 메모 틀 opened once.
     */
    suspend fun runSteps(c: AppContainer, acts: List<LogicAct>, date: LocalDate, scope: FormulaScope, who: String, quiet: Boolean = false, depth: Int = 0) {
        var opened = false
        for (a in acts.take(1 + com.bamnat.core.formula.MAX_MORE)) {
            val opens = a.after == null && (a.verb == LogicVerb.SCREEN || a.verb == LogicVerb.MEMO_TEMPLATE)
            if (opens && opened) continue
            opened = opened || opens
            run(c, a, date, scope, who, quiet, depth)
        }
    }

    /** 1.39 [FX-11] A button pressed: its 조건 picks the 동작 줄 or the '아니면'. */
    suspend fun pressButton(c: AppContainer, b: com.bamnat.core.formula.LogicButton, date: LocalDate, scope: FormulaScope, quiet: Boolean = false) {
        val holds = b.cond.isEmpty() || com.bamnat.core.formula.Formula.eval(b.cond, scope)?.truthy == true
        val steps = b.steps(holds)
        if (steps.isEmpty()) {
            LogicStore(c.appContext).log(b.label, "조건(${b.cond.take(30)})이 맞지 않고 ‘아니면’이 없어 안 했어요")
            if (!quiet || onScreen) _message.value = "‘${b.label}’은 지금 조건에 맞지 않아요"
            return
        }
        runSteps(c, steps, date, scope, b.label, quiet)
    }

    /**
     * 1.39 [FX-12] 값 칸 [name] went from [was] to [now]: its 값이 바뀌면 rules run (목표 rules only when it crosses the goal), at most
     * [CHAIN_MAX] deep so two rules cannot keep each other going.
     */
    private suspend fun valueChanged(c: AppContainer, name: String, was: Double?, now: Double, date: LocalDate, depth: Int) {
        val all = logic(c, all = true)
        val rules = all.rules.filter { it.on == RuleTrigger.VALUE && it.of == name }
        if (rules.isEmpty()) return
        val store = LogicStore(c.appContext)
        if (depth >= CHAIN_MAX) { rules.forEach { store.log(it.name, "값이 바뀌면 규칙이 ${CHAIN_MAX}번 넘게 이어져 멈췄어요") }; return }
        val runs = logic(c).rules.associateBy { it.key }
        val def = all.value(name)
        val f = runCatching { facts(c) }.getOrNull() ?: return
        val scope = f.scope(date)
        for (r in rules) {
            if (store.isOff(r.key)) continue
            val run = runs[r.key] ?: run { store.log(r.name, PRO_REST); null } ?: continue
            if (r.goal) {
                val goal = def?.goal ?: continue
                if (!((was ?: Double.NEGATIVE_INFINITY) < goal && now >= goal)) continue
            }
            val holds = r.cond.isEmpty() || com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy == true
            val steps = run.steps(holds)
            if (steps.isEmpty()) { store.log(r.name, "조건(${r.cond.take(30)})이 맞지 않아 건너뛰었어요"); continue }
            runCatching { runSteps(c, steps, date, scope, r.name, quiet = true, depth = depth + 1) }
        }
    }

    // ---- 1.39 [FX-15] 기다렸다 하기 ----

    /** Keeps [act] to be done [LogicAct.after] minutes from now (at most [LATER_MAX] waiting), and registers the alarm. */
    internal fun later(c: AppContainer, act: LogicAct, date: LocalDate, who: String, again: Boolean = false) {
        val m = act.after ?: return
        val at = Today.millis() + m * 60_000L
        val o = JSONObject().put("at", at).put("who", who).put("date", date.toString()).put("act", JSONObject(act.toMap())).put("again", again)
        LogicStore(c.appContext).addLater(o, LATER_MAX)
        schedule(c)
    }

    @Suppress("UNCHECKED_CAST")
    private fun actOf(o: JSONObject): LogicAct? = runCatching {
        fun plain(x: Any?): Any? = when (x) {
            is JSONObject -> x.keys().asSequence().associateWith { plain(x.get(it)) }
            is JSONArray -> (0 until x.length()).map { plain(x.get(it)) }
            JSONObject.NULL -> null
            else -> x
        }
        LogicAct.from(plain(o.getJSONObject("act")) as Map<String, Any?>)
    }.getOrNull()

    private const val CHAIN_MAX = 3
    private const val LATER_MAX = 20

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

    /**
     * [TRY-06] 1.39 기능까지 써 보기: everything the rules and buttons wrote from [since] on, taken back newest first (and gone from
     * '규칙이 쓴 것' and 되돌리기). Answers how many.
     */
    suspend fun takeBackSince(c: AppContainer, since: Long): Int {
        val store = LogicStore(c.appContext)
        val all = store.written()
        val mine = all.filter { it.optLong("at", -1L) >= since }
        if (mine.isEmpty()) return 0
        store.keepWritten(all.filter { it.optLong("at", -1L) < since })
        if (store.undo?.let { runCatching { JSONObject(it).optLong("at", -1L) }.getOrNull() }?.let { it >= since } == true) store.undo = null
        var n = 0
        for (o in mine.asReversed()) if (runCatching { takeBack(c, o) }.isSuccess) n++
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
            // 1.39 [FX-03] 값 칸: back to the number before (none kept before: its start, by forgetting the period).
            o.has("val") -> {
                val v = o.optDouble("v")
                FxValues.restore(c, o.getString("val"), if (v.isNaN()) 0.0 else v, if (v.isNaN()) "" else o.optString("p"), o.optString("t").ifEmpty { null })
                FxOutside.changed(c)
            }
            o.has("envMove") -> c.scopeOf(o.optString("cal", c.calendars.active.value)).money.deleteEnvelopeMove(o.getLong("envMove"))
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

    /**
     * The rules for [on] that are switched on, each with what it does on [date]: its 동작 줄 when its 조건 holds, its '아니면' when not
     * (1.39 [FX-11]); one with neither is left out (and said in the log).
     */
    fun due(c: AppContainer, on: RuleTrigger, date: LocalDate, facts: DayFactsBook): List<Pair<LogicRule, List<LogicAct>>> {
        val store = LogicStore(c.appContext)
        val scope = facts.scope(date)
        // As it runs now (an 알림 without its 알림 단추 when they rest); the key does not change with them.
        val runs = logic(c).rules.associateBy { it.key }
        // 1.39 [FX-06] '왜 안 됐지': a rule for [on] that does not run says why in the log.
        return logic(c, all = true).rules.filter { it.on == on }.mapNotNull { r ->
            val run = runs[r.key]
            val holds = r.cond.isEmpty() || com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy == true
            val why = when {
                store.isOff(r.key) -> "꺼 둬서 쉬었어요"
                run == null -> PRO_REST
                !holds && run.otherwise.isEmpty() -> "${date.monthValue}/${date.dayOfMonth} 조건(${r.cond.take(30)})이 맞지 않아 건너뛰었어요"
                else -> null
            }
            why?.let { store.log(r.name, it) }
            if (why == null && run != null) run to run.steps(holds) else null
        }
    }

    private fun opens(acts: List<LogicAct>) = acts.any { it.after == null && (it.verb == LogicVerb.SCREEN || it.verb == LogicVerb.MEMO_TEMPLATE) }

    /** 날짜를 누르면: the tap rules that hold run (a screen or 메모 틀 the first only, so one tap opens one thing). */
    fun onTap(c: AppContainer, date: LocalDate, facts: DayFactsBook?) {
        facts ?: return
        if (resting) return
        val rules = due(c, RuleTrigger.TAP, date, facts)
        if (rules.isEmpty()) return
        c.appScope.launch {
            var opened = false
            for ((r, acts) in rules) {
                val o = opens(acts)
                if (o && opened) continue
                opened = opened || o
                runCatching { runSteps(c, acts, date, facts.scope(date), r.name) }
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
        val any = rules.any { opens(it.second) }
        c.appScope.launch {
            var opened = false
            for ((r, acts) in rules) {
                val o = opens(acts)
                if (o && opened) continue
                opened = opened || o
                runCatching { runSteps(c, acts, date, facts.scope(date), r.name) }
            }
        }
        return any
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
            if (!holds(f)) { LogicStore(c.appContext).log(b.label, "이 날 보이기 조건이 맞지 않아 안 눌렀어요"); _message.value = "‘${b.label}’ 단추는 이 날 조건에 맞지 않아요"; return@launch }
            runCatching { pressButton(c, b, date, f.scope(date)) }
        }
        return true
    }

    /** 메모를 저장하면 (the memo's own day) and 하루 처음 앱을 열면 (today, once a day). */
    fun onMemoSaved(c: AppContainer, date: LocalDate?) {
        if (resting || logic(c).rules.none { it.on == RuleTrigger.MEMO }) return
        c.appScope.launch {
            val f = facts(c)
            val d = date ?: Today.date()
            due(c, RuleTrigger.MEMO, d, f).forEach { (r, acts) -> runCatching { runSteps(c, acts, d, f.scope(d), r.name, quiet = true) } }
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
            due(c, RuleTrigger.MONEY, date, f).forEach { (r, acts) -> runCatching { runSteps(c, acts, date, f.scope(date), r.name, quiet = true) } }
        }
    }

    fun onOpen(c: AppContainer) {
        // 1.39 [FX-02] C-3: a 하루 써 보기 that ended says what it did, once, and where 프로 keeps it on.
        runCatching { FxAccess.endedCard(c) }.getOrNull()?.let { (line, code) ->
            val n = NotificationCompat.Builder(c.appContext, Notifications.RULES_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("오늘 이 작품이 한 일")
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                // 1.39 [FX-24] 그 작품의 장터 자세히(한 일·프로 카드)로, 내가 만든 것은 프로 화면으로.
                .setContentIntent(Notifications.open(c.appContext, NoticeLinks.Target.PRO, REQ + 3, payload = code?.takeIf { it != com.bamnat.core.formula.FxTaste.OWN }))
                .setSilent(true)
                .setAutoCancel(true)
                .build()
            Notifications.post(c.appContext, NOTICE_ID + 1, n)
        }
        val store = LogicStore(c.appContext)
        val today = Today.date()
        if (resting || store.lastOpen == today.toString() || logic(c).rules.none { it.on == RuleTrigger.OPEN }) return
        store.lastOpen = today.toString()
        c.appScope.launch {
            val f = facts(c)
            due(c, RuleTrigger.OPEN, today, f).forEach { (r, acts) -> runCatching { runSteps(c, acts, today, f.scope(today), r.name) } }
        }
    }

    // ---- timed rules: 근무 시작·끝 전후, 매일 몇 시 ----

    /** When [r] next comes after [after], looking two weeks ahead; null when it does not come in them. */
    fun nextTime(r: LogicRule, after: LocalDateTime, facts: DayFactsBook): Pair<LocalDateTime, LocalDate>? {
        if (r.on == RuleTrigger.EVERY) return nextEvery(r, after, facts)
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

    /** 1.39 [FX-15] 몇 분마다: the next time after [after], in its 시간대 or while a shift is on, at most [LogicRule.EVERY_DAY_MAX] a day. */
    private fun nextEvery(r: LogicRule, after: LocalDateTime, facts: DayFactsBook): Pair<LocalDateTime, LocalDate>? {
        // 1.39 [FX-18] the same times the 일주일 미리 돌려 보기 shows (core LogicWeek.everyTimes).
        for (k in -1..14) {
            val day = after.toLocalDate().plusDays(k.toLong())
            com.bamnat.core.formula.LogicWeek.everyTimes(r, day, facts.values(day)).firstOrNull { it.isAfter(after) }?.let { return it to day }
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
            val timed = logic(c).rules.filter { it.on in TIMED && !store.isOff(it.key) }
            val laters = store.laters().map { it.optLong("at") }
            val op = PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, LogicReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (timed.isEmpty() && laters.isEmpty()) { am.cancel(op); return@launch }
            val f = runCatching { facts(c) }.getOrNull() ?: return@launch
            val now = Today.dateTime()
            // 1.39 [FX-15] The first of a 기다렸다 하기 waiting and the timed rules.
            val soonest = laters.minOrNull()?.let { java.time.Instant.ofEpochMilli(it).atZone(Today.zone()).toLocalDateTime() }
            // Nothing in two weeks (no night shift yet): look again in a day, so a 근무표 filled in later is not missed.
            val next = (timed.mapNotNull { r -> nextTime(r, now, f)?.first } + listOfNotNull(soonest)).minOrNull() ?: now.plusDays(1)
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
    /** PC D14: the alarm and the 시계 바뀜 broadcast can come together; one look at a time, so a time is never run twice. */
    private val firing = kotlinx.coroutines.sync.Mutex()

    internal suspend fun fire(c: AppContainer): Unit = firing.withLock { fireNow(c) }

    private suspend fun fireNow(c: AppContainer) {
        // 안전 모드: the times that come are let pass (not run late after 평소대로), and the next is registered.
        if (resting) { LogicStore(c.appContext).checked = Today.dateTime().toString(); schedule(c); return }
        val f = facts(c)
        val now = Today.dateTime()
        val store = LogicStore(c.appContext)
        val last = store.checked?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        // PC D12 (중): the clock went back (시계를 앞으로 옮겼다가 되돌림). What was marked done or missed in that later time has not
        // happened yet: it is forgotten, so 07:00 runs when 07:00 comes again (전에는 '놓쳤어요'만 남고 돌지 않았어요).
        if (last != null && last.isAfter(now)) store.forgetAfter(logic(c).rules.map { it.key }, now)
        val since = last?.takeIf { !it.isAfter(now) && it.isAfter(now.minusDays(2)) } ?: now.minusMinutes(2)
        // 1.39 [FX-15] 기다렸다 하기 that came due: done (too late, said as missed); the rest keep waiting.
        val nowMs = Today.millis()
        val (dueLater, waiting) = store.laters().partition { it.optLong("at") <= nowMs + 60_000L }
        if (dueLater.isNotEmpty()) {
            store.keepLaters(waiting)
            for (o in dueLater) {
                val who = o.optString("who")
                val act = actOf(o) ?: continue
                if (o.optLong("at") < nowMs - LATE_MINUTES * 60_000L) { store.log(who, "‘${act.now().say()}’ 시각을 놓쳤어요 (꺼짐·시계 바뀜)"); continue }
                val day = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: Today.date()
                // The gate looks at the act as the rule or button holds it (with its wait); a '다시 알림' was let through when it was asked.
                val trusted = o.optBoolean("again")
                if (!trusted && !FxAccess.on(c) && !runsNow(c, act)) { store.log(who, PRO_REST); continue }
                runCatching { run(c, act.now(), day, f.scope(day), who, quiet = true, trusted = true) }.onFailure { store.log(who, "하다가 멈췄어요") }
            }
        }
        val runs = logic(c).rules.associateBy { it.key }
        for (all in logic(c, all = true).rules) {
            val r = runs[all.key] ?: all
            if (store.isOff(r.key) || r.on !in TIMED) continue
            // PC D13 (낮): every time between the last look and now, not only the first (하루 넘게 건너뛰면 그날 07:00이 아무 표시 없이
            // 사라졌어요: 첫 시각 11/13 07:00은 이미 했으니 건너뛰고 끝났어요). The latest one not yet done is the one run or said missed.
            var probe = since.minusSeconds(1)
            var hit: Pair<LocalDateTime, LocalDate>? = null
            for (n in 0 until 40) {
                val next = nextTime(r, probe, f) ?: break
                if (next.first.isAfter(now.plusMinutes(1))) break
                if (store.firedAt(r.key) != next.first.toString()) hit = next
                probe = next.first
            }
            val (at, day) = hit ?: continue
            store.fired(r.key, at.toString())
            // 1.39 [FX-06] A 프로 기능 장치 rule's time came while it rests: said in the log, not run (nor run late after).
            if (r.key !in runs) { store.result(r.key, at, "프로 기능 장치라 쉬었어요"); store.log(r.name, PRO_REST); continue }
            if (at.isBefore(now.minusMinutes(LATE_MINUTES))) { store.result(r.key, at, LogicStore.MISSED); store.log(r.name, "${at.monthValue}/${at.dayOfMonth} ${"%02d:%02d".format(at.hour, at.minute)} 휴대폰이 꺼져 있었거나 시계가 바뀌어 놓쳤어요"); continue }
            val scope = f.scope(day)
            val holds = r.cond.isEmpty() || com.bamnat.core.formula.Formula.eval(r.cond, scope)?.truthy == true
            if (!holds && r.otherwise.isEmpty()) {
                store.result(r.key, at, "조건이 맞지 않아 건너뛰었어요"); store.log(r.name, "${at.monthValue}/${at.dayOfMonth} 조건(${r.cond.take(30)})이 맞지 않아 건너뛰었어요"); continue
            }
            val ok = runCatching { runSteps(c, r.steps(holds), day, scope, r.name, quiet = true) }
            if (ok.isFailure) store.log(r.name, "하다가 멈췄어요")
            store.result(r.key, at, when {
                ok.isFailure -> "하다가 멈췄어요"
                r.act.verb == LogicVerb.NOTIFY && !Notifications.canPost(c.appContext) -> "알림이 꺼져 있어 못 띄웠어요(설정 › 알림과 잠)"
                else -> "했어요"
            })
        }
        store.checked = now.toString()
        schedule(c)
    }

    /** PC D12 (사용성 중): a missed rule's '지금 한 번 하기': what it does, now, for today (its 조건 asked by the person, not checked). */
    suspend fun runNow(c: AppContainer, r: LogicRule) {
        val f = facts(c)
        val day = Today.date()
        val ok = runCatching { runSteps(c, listOf(r.act) + r.more, day, f.scope(day), r.name) }
        LogicStore(c.appContext).result(r.key, Today.dateTime(), if (ok.isSuccess) "놓친 것을 지금 했어요" else "하다가 멈췄어요")
    }

    private val TIMED = setOf(RuleTrigger.DAILY, RuleTrigger.START, RuleTrigger.END, RuleTrigger.EVERY)
    internal const val PRO_REST = "프로 기능 장치라 쉬었어요 (하루 써 보기·프로에서 돌아요)"
    private const val LATE_MINUTES = 60L

    private fun notify(ctx: Context, title: String, text: String, quietly: Boolean = false,
        buttons: List<com.bamnat.core.formula.LogicButton> = emptyList(), date: LocalDate = Today.date(), again: List<String> = emptyList()) {
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
            // 1.39 [FX-05] 알림 단추: each presses that button for the day, without opening the app.
            .apply { buttons.forEachIndexed { i, b -> addAction(0, listOfNotNull(b.emoji, b.label).joinToString(" "), FxPressReceiver.pending(ctx, b.id, date, title, REQ + 10 + i)) } }
            // 1.39 [FX-15] '10분 뒤 다시'·'1시간 뒤 다시' (free: 메모 일정 알림 has them too).
            .apply { again.forEachIndexed { i, id -> addAction(0, "${LogicAct.afterWords(LogicAct.AGAIN[id] ?: 10)} 뒤 다시", FxPressReceiver.again(ctx, id, date, title, text, buttons.map { it.id } + again, REQ + 20 + i)) } }
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
        val now = Today.millis()
        val since = now - 7 * 86_400_000L
        // PC D13 (낮): runs stamped in a later time the clock has since come back from are not counted (한 번 돌았는데 2번).
        return times(who).count { it in since..now }
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

    /** PC D12: the clock went back: marks (and their 지난번 lines) from after [now] are dropped, as not happened yet. */
    fun forgetAfter(keys: List<String>, now: LocalDateTime) {
        val e = p.edit()
        keys.forEach { k ->
            val at = firedAt(k)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            if (at != null && at.isAfter(now)) e.remove("fired.$k").remove("result.$k")
        }
        val cut = now.atZone(Today.zone()).toInstant().toEpochMilli()
        p.all.keys.filter { it.startsWith("runs.") }.forEach { k ->
            val kept = p.getString(k, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { it <= cut }
            e.putString(k, kept.joinToString(","))
        }
        e.apply()
    }

    /** PC D12: the last line said the time was missed (phone off, clock moved), so the line offers 지금 한 번 하기. */
    fun missed(key: String): Boolean = lastResult(key)?.let { it.endsWith(MISSED) || it.endsWith(MISSED_OLD) } == true

    /** PC D12 (사용성 낮): rules that came from someone else (장터·보관함), marked small on their line. */
    fun markGot(keys: Collection<String>) {
        if (keys.isEmpty()) return
        p.edit().putStringSet("got", p.getStringSet("got", emptySet()).orEmpty() + keys).apply()
    }
    fun got(key: String): Boolean = key in p.getStringSet("got", emptySet()).orEmpty()

    /** 1.39 [FX-23] Where received rules ([LogicRule.key]) and buttons ([LogicButton.id]) came from: the 장터 code, for 작품마다 하루 써 보기. */
    fun markFrom(code: String, rules: Collection<String>, buttons: Collection<String>) {
        if (code.isBlank() || (rules.isEmpty() && buttons.isEmpty())) return
        val e = p.edit()
        rules.forEach { e.putString("from.r.$it", code) }
        buttons.forEach { e.putString("from.b.$it", code) }
        e.apply()
    }
    fun fromRule(key: String): String? = p.getString("from.r.$key", null)
    fun fromButton(id: String): String? = p.getString("from.b.$id", null)
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

    /** [TRY-06] Only [list] kept in '규칙이 쓴 것'. */
    fun keepWritten(list: List<JSONObject>) = p.edit().putString("written", JSONArray(list.map { it.toString() }).toString()).apply()

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
        /** PC D15 (사용성 낮): short, so the line is not cut ("휴대폰이 꺼져 있었거나 시계가 바뀌어 놓쳤어요" was). */
        const val MISSED = "놓쳤어요 (꺼짐·시계 바뀜)"
        private const val MISSED_OLD = "휴대폰이 꺼져 있었거나 시계가 바뀌어 놓쳤어요"
        const val WRITTEN_MAX = 60
    }

    var lastOpen: String?
        get() = p.getString("open", null)
        set(v) { p.edit().putString("open", v).apply() }

    /**
     * 1.39 [FX-06] B-3 '왜 안 됐지': each run and each skip of a rule or button with why (조건, 꺼 둠, 프로 기능 장치, 안전 모드, 놓침),
     * newest last, at most [LOG_MAX]; the same line again within a minute is kept once (a calendar tapped many times).
     */
    fun log(who: String, what: String) {
        val now = Today.millis()
        val list = log()
        val last = list.lastOrNull()
        if (last != null && last.who == who && last.what == what && now - last.at < 60_000L) return
        val keep = (list + Line(now, who.take(40), what.take(80))).takeLast(LOG_MAX)
        p.edit().putString("log", JSONArray(keep.map { JSONObject().put("at", it.at).put("who", it.who).put("what", it.what) }).toString()).apply()
    }

    fun log(): List<Line> = runCatching {
        val a = JSONArray(p.getString("log", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Line(o.optLong("at"), o.optString("who"), o.optString("what")) } }
    }.getOrDefault(emptyList())

    fun clearLog() = p.edit().remove("log").apply()

    data class Line(val at: Long, val who: String, val what: String)

    /** 1.39 [FX-15] 기다렸다 하기 waiting: {at, who, date, act, again}. */
    fun laters(): List<JSONObject> = runCatching {
        val a = JSONArray(p.getString("later", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    fun keepLaters(list: List<JSONObject>) = p.edit().putString("later", JSONArray(list).toString()).apply()

    /** Adds [o]; past [max], the soonest are kept. */
    fun addLater(o: JSONObject, max: Int) = keepLaters((laters() + o).sortedBy { it.optLong("at") }.take(max))
}

private const val LOG_MAX = 50
