package com.bamnat.app.data

import com.bamnat.core.memo.Memo
import com.bamnat.core.model.DayEntry
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.schedule.TurnSlip
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * 2.0.0 [RVH-51] PC-72 '이 날 맞아요?': the days, memos and money lines written in 1.36.1~1.39.0 that the month turn may have put a
 * month on ([TurnSlip]), on every calendar of the user's, and what each answer does (설계 docs/plans/2.0.0/PC-72-지난판-다른-날-확인-설계.md
 * 3·4절). Nothing here says a line is wrong: the sheet asks, and 맞아요 changes nothing.
 */
class TurnSlipCheck(
    /** The user's calendars that are ready (received calendars are not drawn on the month grid of their own). */
    private val calendars: () -> List<String>,
    private val schedule: (String) -> ScheduleRepository,
    private val money: (String) -> MoneyRepository,
    private val memos: MemoRepository,
    private val weekStart: () -> DayOfWeek,
    private val zone: () -> ZoneId = Today::zone,
) {
    /**
     * One line on [calendar]. A day carries what it holds now ([held]) and what the day meant holds ([meantHeld], told before it is
     * replaced, 조건 10); a memo or a money line carries itself.
     */
    data class Found(
        val calendar: String,
        val line: TurnSlip.Line,
        val held: DayEntry? = null,
        val meantHeld: DayEntry? = null,
        val memo: Memo? = null,
        val entry: MoneyEntry? = null,
    ) {
        val kind: TurnSlip.Kind get() = line.source.kind
    }

    /** 맞아요 · P로 · 빼기. 빼기 is for a day only: a memo or a money line is not thrown away from here. */
    enum class Choice { KEEP, MOVE, CLEAR }

    /** What [apply] changed, for 되돌리기: only these, all at once (조건 10). */
    class Done internal constructor(internal val undo: List<suspend () -> Unit>) {
        val count: Int get() = undo.size
    }

    /** The lines to ask about, for changes written until [until] (the update to 2.0.0). */
    suspend fun find(until: Instant): List<Found> {
        val zone = zone()
        val weekStart = weekStart()
        val from = TurnSlip.SINCE.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = until.toEpochMilli()
        val ids = calendars().ifEmpty { listOf(PersonalCalendar.MAIN) }
        val out = ArrayList<Found>()
        for (id in ids) {
            val repo = schedule(id)
            val written = money(id).writtenBetween(from, to)
            val lines = written.associate { it.first.id to it.first }
            val sources = repo.changedDays(from, to).map { (d, at) -> TurnSlip.Source(TurnSlip.Kind.DAY, d, Instant.ofEpochMilli(at)) } +
                written.map { (e, at) -> TurnSlip.Source(TurnSlip.Kind.MONEY, e.date, Instant.ofEpochMilli(at), e.id) }
            for (line in TurnSlip.lines(sources, weekStart, zone, until)) {
                if (line.source.kind == TurnSlip.Kind.DAY) {
                    // A day put back since holds nothing to move.
                    val held = repo.entryNow(line.date)?.takeUnless { it.isEmpty } ?: continue
                    out += Found(id, line, held = held, meantHeld = repo.entryNow(line.meant)?.takeUnless { it.isEmpty })
                } else {
                    lines[line.source.id]?.let { out += Found(id, line, entry = it) }
                }
            }
        }
        // A memo is one for every calendar it is on: asked about once, on the first of its calendars that is the user's.
        val dated = memos.memos.first().filter { it.date != null && it.repeat.isEmpty() && it.createdAtMillis in from until to }
        val byId = dated.associateBy { it.id }
        val sources = dated.map { TurnSlip.Source(TurnSlip.Kind.MEMO, it.date!!, Instant.ofEpochMilli(it.createdAtMillis), it.id) }
        for (line in TurnSlip.lines(sources, weekStart, zone, until)) {
            val memo = byId[line.source.id] ?: continue
            out += Found(memo.owners.firstOrNull { it in ids } ?: ids.first(), line, memo = memo)
        }
        return out
    }

    /**
     * Each answer of [choices] done (맞아요 is nothing). A day moved takes what it holds to the day meant, in place of what that day
     * held, and goes back to its 근무 주기; 빼기 puts it back only. A memo or a money line moved keeps all but its date.
     */
    suspend fun apply(choices: Map<Found, Choice>): Done {
        val undo = ArrayList<suspend () -> Unit>()
        for ((found, choice) in choices) {
            if (choice == Choice.KEEP) continue
            val line = found.line
            when (found.kind) {
                TurnSlip.Kind.DAY -> {
                    val repo = schedule(found.calendar)
                    if (choice == Choice.MOVE) {
                        val m = repo.moveDay(line.date, line.meant)
                        undo += { repo.undoMove(m) }
                    } else {
                        val was = repo.updateDay(line.date) { DayEntry(line.date) }
                        undo += { repo.restoreDay(line.date, was) }
                    }
                }
                TurnSlip.Kind.MEMO -> if (choice == Choice.MOVE) {
                    val memo = memos.get(found.memo?.id ?: continue) ?: continue
                    memos.save(memo.copy(date = line.meant))
                    undo += { memos.get(memo.id)?.let { memos.save(it.copy(date = memo.date)) } }
                }
                TurnSlip.Kind.MONEY -> if (choice == Choice.MOVE) {
                    val book = money(found.calendar)
                    val entry = found.entry ?: continue
                    book.save(entry.copy(date = line.meant))
                    undo += { book.save(entry) }
                }
            }
        }
        return Done(undo)
    }

    /** [apply] undone: what it changed, back as it was, and nothing else. */
    suspend fun undo(done: Done) {
        for (step in done.undo.asReversed()) step()
    }
}
