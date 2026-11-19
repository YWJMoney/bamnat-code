package com.bamnat.app.diag

import android.content.Context
import androidx.core.app.NotificationCompat
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import com.bamnat.core.journal.JournalEntry
import com.bamnat.core.journal.JournalKind
import com.bamnat.core.journal.dataCheckDetail
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * 2.0.0 [RVL-71] (검토 I12b-01·U12-09) 데이터 점검 by itself, once a week: a problem is caught before the person opens a screen to look.
 *
 * It runs [DataCheck] and first puts right what can be put right the way the app already does it by hand:
 * - a day set to a kind of work that is gone follows the pattern again, as deleting that kind does ([com.bamnat.app.data.ScheduleRepository.releaseLostShiftDays]);
 * - memo and receipt photos nothing names any more go, with the same guards as at start (a day old, every calendar read, 최근 지운 기록 kept).
 * What it did is one line in '밤낮이 한 일' ([JournalKind.DATA_CHECK]). What it cannot put right (a database that fails SQLite's own check,
 * an added calendar without its file, a backup that does not read back the same) is said once in a notice that opens 설정 › 데이터 › 데이터 점검.
 * A note ([DataCheck.Line.note]: a file the list does not name) is shown there but never notified.
 *
 * Nothing leaves the phone. It is turned off with 설정 › 데이터 › 데이터 점검 › 일주일에 한 번 스스로 살펴보기 ([com.bamnat.app.data.AppSettings.dataSelfCheck]).
 */
class DataSelfCheck(
    private val c: AppContainer,
    private val clock: () -> Instant = Today::instant,
    /** The lines; a test gives a problem the phone cannot make. */
    private val check: suspend (AppContainer) -> List<DataCheck.Line> = DataCheck::run,
) {
    private val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** The last run: when, what it put right, and the problems it left (their words as [DataCheck] says them). */
    data class State(
        val at: Instant? = null,
        val fixedDays: Int = 0,
        val fixedPhotos: Int = 0,
        val problems: List<String> = emptyList(),
        val running: Boolean = false,
    ) {
        val fixed: Int get() = fixedDays + fixedPhotos
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun load() = State(
        at = prefs.getLong(K_AT, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        fixedDays = prefs.getInt(K_DAYS, 0),
        fixedPhotos = prefs.getInt(K_PHOTOS, 0),
        problems = prefs.getString(K_PROBLEMS, null)?.split(SEP)?.filter { it.isNotBlank() }.orEmpty(),
    )

    /** A week since the last run (or never run), with the switch on. */
    fun due(now: Instant = clock()): Boolean {
        if (!c.settingsRepository.current.dataSelfCheck) return false
        val last = _state.value.at ?: return true
        return Duration.between(last, now) >= EVERY || now.isBefore(last)
    }

    /** Runs when [due]; null when it was not. Not while a work is tried (같은 까닭으로 자동 백업도 기다려요). */
    suspend fun runIfDue(): State? = if (due() && !com.bamnat.app.recipe.FunctionTrial.busy(c.appContext)) runNow() else null

    suspend fun runNow(): State = lock.withLock {
        _state.value = _state.value.copy(running = true)
        val now = clock()
        val days = runCatching { releaseLostDays() }.getOrDefault(0)
        val photos = runCatching { sweepPhotos(now.toEpochMilli()) }.getOrDefault(0)
        val lines = runCatching { check(c) }.getOrElse { listOf(DataCheck.Line(false, "점검하지 못했어요 (${it.javaClass.simpleName})")) }
        val problems = lines.filter { !it.ok && !it.note }.map { it.text }
        val before = _state.value.problems
        prefs.edit().putLong(K_AT, now.toEpochMilli()).putInt(K_DAYS, days).putInt(K_PHOTOS, photos)
            .putString(K_PROBLEMS, problems.joinToString(SEP)).commit()
        _state.value = load()
        runCatching { c.journal.add(JournalEntry(now, JournalKind.DATA_CHECK, 1, dataCheckDetail(days, photos, problems.size))) }
        c.telemetry.event("data_self_check", if (problems.isEmpty()) "ok" else "problem")
        if (problems.isEmpty()) Notifications.cancel(c.appContext, Notifications.DATA_CHECK_ID)
        else if (tell(problems, before)) post(c.appContext, problems)
        _state.value
    }

    private suspend fun releaseLostDays(): Int {
        var n = c.calendars.repository(PersonalCalendar.MAIN).releaseLostShiftDays().size
        for (cal in runCatching { c.calendars.added() }.getOrDefault(emptyList())) {
            n += runCatching { c.calendars.repository(cal.id).releaseLostShiftDays().size }.getOrDefault(0)
        }
        return n
    }

    /** The photo sweeps the app runs as it starts ([AppContainer]), counted; receipts only when every calendar's lines could be read. */
    private suspend fun sweepPhotos(now: Long): Int {
        var n = runCatching { com.bamnat.app.ui.memo.MemoPhotoFiles.sweep(c.appContext, c.memoRepository.photoNames(), now) }.getOrDefault(0)
        n += runCatching {
            val added = c.calendars.calendars.value.filter { !it.isMain }
            if (!added.all { it.ready }) 0 else {
                val used = buildSet {
                    addAll(com.bamnat.app.ui.money.ReceiptFiles.usedBy(c.database.openHelper.readableDatabase))
                    added.forEach { addAll(com.bamnat.app.ui.money.ReceiptFiles.usedBy(c.calendars.database(it.id).openHelper.readableDatabase)) }
                    addAll(c.recordsTrash.keptReceipts())
                }
                com.bamnat.app.ui.money.ReceiptFiles.sweep(c.appContext, used, now)
            }
        }.getOrDefault(0)
        return n
    }

    companion object {
        private const val PREFS = "data_self_check"
        private const val K_AT = "at"
        private const val K_DAYS = "days"
        private const val K_PHOTOS = "photos"
        private const val K_PROBLEMS = "problems"
        private const val SEP = "\n"
        val EVERY: Duration = Duration.ofDays(7)
        private const val OPEN_REQUEST = 4412

        /** The same problems as last week are not said again: the notice was seen or put away, and the screen still shows them. */
        internal fun tell(problems: List<String>, before: List<String>): Boolean = problems.isNotEmpty() && problems != before

        /** The words of a run for 설정 › 데이터's row and the screen: when, then what it did or "이상 없음". */
        fun words(s: State, today: java.time.LocalDate = Today.date(), zone: java.time.ZoneId = Today.zone()): String {
            val at = s.at ?: return "아직 살펴본 적이 없어요"
            val day = at.atZone(zone).toLocalDate()
            val whenSaid = when (day) {
                today -> "오늘"
                today.minusDays(1) -> "어제"
                else -> "${day.monthValue}월 ${day.dayOfMonth}일"
            }
            val did = buildList {
                if (s.fixedDays > 0) add("지운 근무를 가리키던 날 ${s.fixedDays}개를 근무표대로 돌려놓았어요")
                if (s.fixedPhotos > 0) add("아무 기록도 쓰지 않는 사진 ${s.fixedPhotos}장을 치웠어요")
            }
            return when {
                s.problems.isNotEmpty() -> "$whenSaid 살펴봤어요 · 살펴볼 것 ${s.problems.size}개"
                did.isNotEmpty() -> "$whenSaid 살펴봤어요 · " + did.joinToString(", ")
                else -> "$whenSaid 살펴봤어요 · 이상 없음"
            }
        }

        const val TITLE = "데이터 점검에서 살펴볼 것이 있어요"

        fun post(context: Context, problems: List<String>) {
            val text = problems.first() + if (problems.size > 1) " 외 ${problems.size - 1}개" else ""
            val notification = NotificationCompat.Builder(context, Notifications.BACKUP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(TITLE)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n눌러서 데이터 점검을 열어요"))
                .setContentIntent(Notifications.open(context, NoticeLinks.Target.DATA_CHECK, OPEN_REQUEST))
                .setAutoCancel(true)
                .build()
            Notifications.post(context, Notifications.DATA_CHECK_ID, notification)
        }
    }
}
