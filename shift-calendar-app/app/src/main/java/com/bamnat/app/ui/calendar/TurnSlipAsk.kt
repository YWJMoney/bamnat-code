package com.bamnat.app.ui.calendar

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.data.TurnSlipCheck
import com.bamnat.app.ui.Fmt
import com.bamnat.core.schedule.TurnSlip
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.words
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

/**
 * 2.0.0 [RVH-64] 검토 PC-72 '이 날 맞아요?' (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md 2·4절): on the first runs of 2.0.0 on a
 * phone that came from an earlier version, the lines [TurnSlipCheck] finds, asked once. A phone whose month turn could have done it now
 * (스르륵·책장·움직이지 않기, 움직임 끄기, the phone's animations off) gets a card over the calendar; every phone gets the line in the
 * update notice. Nothing is asked of a new phone, a phone with nothing found, or once it was answered.
 */
object TurnSlipAsk {
    /** One line of the sheet: what the day holds now and whether its 출근 알람 is off by it. */
    data class Row(
        val found: TurnSlipCheck.Found,
        val what: String,
        val alarmOff: Boolean,
        /** What the day meant holds already, said before it is replaced (조건 10); null when it holds nothing. */
        val meantHolds: String?,
    ) {
        val line: TurnSlip.Line get() = found.line
        val isDay: Boolean get() = found.kind == TurnSlip.Kind.DAY
    }

    /** What to ask: [rows] in the sheet's order, and whether the card was folded away (X). */
    data class Ask(val rows: List<Row>, val folded: Boolean)

    /** The month turns that laid the next month over the one in sight before 2.0.0 ([com.bamnat.app.ui.theme.monthTurn]). */
    val RISKY_TURNS = setOf("fade", "flip", "none")

    private val _ask = MutableStateFlow<Ask?>(null)
    /** Null while there is nothing to ask (not looked yet, nothing found, answered and closed). */
    val ask: StateFlow<Ask?> = _ask.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once this run has looked (the update notice waits for it, so the line is in it the first time). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _showing = MutableStateFlow(false)
    /** The sheet is asked for (the card's 확인, the update notice's line). */
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    /**
     * 조건 10: what was picked in the sheet stays while the app runs (closed by X or back and opened again, the screen turned),
     * and what was done stays shown until 닫기.
     */
    val picks = mutableStateMapOf<TurnSlipCheck.Found, TurnSlipCheck.Choice>()
    private val _finished = MutableStateFlow<TurnSlipCheck.Done?>(null)
    val finished: StateFlow<TurnSlipCheck.Done?> = _finished.asStateFlow()
    private val _undone = MutableStateFlow(false)
    val undone: StateFlow<Boolean> = _undone.asStateFlow()

    @Volatile private var looked = false

    fun show() { if (_ask.value != null) _showing.value = true }
    fun closed() { _showing.value = false }

    private val _toldNow = MutableStateFlow(false)
    /** [told] as the app's screens see it: read when this run looks, true once the update notice said it. */
    val toldNow: StateFlow<Boolean> = _toldNow.asStateFlow()

    /** Whether the update notice already said it (once, whichever way it was closed). */
    fun told(context: Context): Boolean = prefs(context).getBoolean(K_TOLD, false)
    fun markTold(context: Context) {
        prefs(context).edit().putBoolean(K_TOLD, true).apply()
        _toldNow.value = true
    }

    /**
     * Looks once a run. [updatedAt] is when this version was put on over an older one (null: a fresh install), and the changes asked
     * about are the ones written before it; it is kept the first time, so a later update does not move it.
     */
    suspend fun load(
        c: AppContainer,
        updatedAt: Instant? = c.updatedAt,
        setupDone: Boolean = c.settingsRepository.current.setupDone,
        today: LocalDate = Today.date(),
    ) {
        if (looked) return
        looked = true
        try {
            val prefs = prefs(c.appContext)
            _toldNow.value = prefs.getBoolean(K_TOLD, false)
            if (prefs.getString(K_STATE, "") == DONE) return
            var until = prefs.getLong(K_UNTIL, 0L)
            if (until == 0L) {
                // A new phone, one not set up, or one updated before the month turn went over the month (1.36.1): nothing to ask.
                if (updatedAt == null || !setupDone || updatedAt.isBefore(TurnSlip.SINCE.atStartOfDay(Today.zone()).toInstant())) {
                    prefs.edit().putString(K_STATE, DONE).apply()
                    return
                }
                until = updatedAt.toEpochMilli()
                prefs.edit().putLong(K_UNTIL, until).apply()
            }
            val rows = runCatching { withContext(Dispatchers.IO) { rows(c, c.turnSlip.find(Instant.ofEpochMilli(until)), today) } }
                .getOrDefault(emptyList())
            if (rows.isEmpty()) { prefs.edit().putString(K_STATE, DONE).apply(); return }
            _ask.value = Ask(rows, folded = prefs.getString(K_STATE, "") == FOLDED)
        } finally {
            _loaded.value = true
        }
    }

    /** The card's X: the card goes, the update notice's line stays (설계 4절). */
    fun fold(context: Context) {
        prefs(context).edit().putString(K_STATE, FOLDED).apply()
        _ask.value = _ask.value?.copy(folded = true)
    }

    /** 이대로 할게요: done, never asked again (written at once, so a phone closed now is not asked twice); the sheet says so. */
    fun answered(context: Context, done: TurnSlipCheck.Done) {
        prefs(context).edit().putString(K_STATE, DONE).apply()
        _finished.value = done
    }

    /** 되돌리기 on the sheet or the bar under it: once. */
    fun markUndone(): Boolean {
        if (_undone.value) return false
        _undone.value = true
        return true
    }

    /** 닫기 after it: the card, the line and the sheet go. */
    fun done(context: Context) {
        prefs(context).edit().putString(K_STATE, DONE).apply()
        _ask.value = null
        _showing.value = false
        _finished.value = null
        picks.clear()
    }

    /** The rows in the sheet's order (총괄 11-12: days to come first, a day whose alarm went off first among them). */
    internal suspend fun rows(c: AppContainer, found: List<TurnSlipCheck.Found>, today: LocalDate): List<Row> {
        val style = c.settingsRepository.current.workStyle
        val snaps = found.map { it.calendar }.distinct().associateWith { c.calendars.repository(it).snapshot.first() }
        val rows = found.map { f ->
            val snap = snaps.getValue(f.calendar)
            when (f.kind) {
                TurnSlip.Kind.DAY -> {
                    val day = snap.resolver.resolve(f.line.date)
                    val off = alarmOff(day, snap.shiftTypes.firstOrNull { it.id == snap.resolver.patternShiftIdOn(f.line.date) })
                    Row(f, dayWhat(day, style, off), off, f.meantHeld?.let { dayWhat(snap.resolver.resolve(f.line.meant), style, false) })
                }
                TurnSlip.Kind.MEMO -> Row(f, "메모 · ${f.memo?.title?.ifBlank { null } ?: f.memo?.body?.lineSequence()?.firstOrNull()?.take(20) ?: ""}".trimEnd(' ', '·'), false, null)
                TurnSlip.Kind.MONEY -> Row(f, f.entry?.let { e -> "${moneyWord(style)} · ${e.category} ${Fmt.won(e.amount)}" } ?: moneyWord(style), false, null)
            }
        }
            // 설계 4절 조건 13: 투자 puts no work on the calendar, so only its memos and money lines are asked.
            .filter { style != WorkStyle.INVEST || !it.isDay }
        val byLine = rows.associateBy { it.found.line }
        return TurnSlip.ordered(rows.map { it.found.line }, today) { l -> byLine[l]?.alarmOff == true }.mapNotNull { byLine[it] }
    }

    /** The day's own 근무 had its 출근 알람, and what was put on it (연차·쉬는 날·알람 끔) leaves it silent. */
    internal fun alarmOff(day: com.bamnat.core.schedule.ResolvedDay, planned: com.bamnat.core.model.ShiftType?): Boolean {
        val rang = planned != null && !planned.isOff && planned.alarmEnabled
        if (!rang) return false
        return day.alarmSkipped || (day.leave != null && day.leave!!.quarters == 4) || day.shiftType == null || day.shiftType!!.isOff
    }

    /**
     * '연차 · 알람 꺼짐', '야간 · 잔업 2시간', '휴무 · 연차', '휴무 · 알람 꺼짐': what the day holds, in the kind of work's words. A 연차 that landed on a day
     * off counts for nothing there ([com.bamnat.core.schedule.ResolvedDay.leave] is null), and that is just the line to show: what
     * was put on the day is said after the day's own 근무.
     */
    internal fun dayWhat(day: com.bamnat.core.schedule.ResolvedDay, style: WorkStyle?, alarmOff: Boolean): String = buildList {
        if (day.leave != null) add(day.leave!!.label)
        else {
            // No 근무 on the day (쉬는 날로 바꾼 날, or none in the 근무 주기): said in the kind of work's word for a day off.
            add(day.shiftType?.name ?: style.words().offDay)
            day.entry?.leave?.let { add(it.label) }
        }
        day.extras.forEach { add(ExtraText.name(it, style)) }
        if (alarmOff) add("알람 꺼짐")
    }.distinct().joinToString(" · ")

    private fun moneyWord(style: WorkStyle?): String = if (style == WorkStyle.INVEST) "기록" else "가계부"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Screenshots: [ask] as if this run had looked, with the update notice's line said already or not. */
    internal fun putForTest(ask: Ask, told: Boolean) { looked = true; _toldNow.value = told; _ask.value = ask; _loaded.value = true }

    internal fun resetForTest() {
        looked = false; _ask.value = null; _showing.value = false; _loaded.value = false; _finished.value = null; _undone.value = false
        _toldNow.value = false
        picks.clear()
    }

    private const val PREFS = "turn_slip"
    private const val K_UNTIL = "until"
    private const val K_STATE = "state"
    private const val FOLDED = "folded"
    private const val DONE = "done"
    private const val K_TOLD = "told"
}
