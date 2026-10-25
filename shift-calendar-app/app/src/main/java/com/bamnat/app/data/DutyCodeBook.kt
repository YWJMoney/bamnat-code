package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.model.LeaveKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * [ROST-06] Each calendar's code book for 한 달 근무 넣기: what the user said a roster's code means once (병가 → 유급 휴가,
 * M → 미들), so the next month's roster needs no question (결정 D63). On this phone and in the backup, like 모든 조.
 * [ROST-10] Clipboard text is never in here: the screen's ClipboardHint remembers only the copy time it dealt with.
 * [READ-06] And whose line of the roster is the user's (1.25 순번 370 양식 기억): the name or 조 they picked, so next
 * month's roster from the same ward needs no "어느 줄이 내 것인가요?". Kept beside the book, in the backup with it.
 */
class DutyCodeBook(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _books = MutableStateFlow(read())
    /** Calendar id → (code as [DutyCodes.norm] gives it → what it means). */
    val books: StateFlow<Map<String, Map<String, DutyCell>>> = _books.asStateFlow()

    fun of(calendarId: String): Map<String, DutyCell> = _books.value[calendarId].orEmpty()

    /** Remembers that [code] means [cell] on [calendarId]'s rosters; an unknown cell is never kept. */
    fun learn(calendarId: String, code: String, cell: DutyCell) {
        val key = DutyCodes.norm(code)
        if (key.isEmpty() || cell is DutyCell.Unknown) return
        val next = of(calendarId) + (key to cell)
        prefs.edit().putString(calendarId, encode(next)).apply()
        _books.value = _books.value + (calendarId to next)
    }

    /** [READ-06] The line the user picked on [calendarId]'s last roster ("김민지", "B조"), or null. */
    fun mine(calendarId: String): String? = prefs.getString(MINE + calendarId, null)

    fun rememberMine(calendarId: String, label: String) {
        val l = label.trim()
        if (l.isEmpty()) return
        prefs.edit().putString(MINE + calendarId, l.take(40)).apply()
    }

    fun forget(calendarId: String, code: String) {
        val next = of(calendarId) - DutyCodes.norm(code)
        prefs.edit().apply { if (next.isEmpty()) remove(calendarId) else putString(calendarId, encode(next)) }.apply()
        _books.value = _books.value + (calendarId to next)
    }

    /** For the backup: calendar id → encoded book. */
    fun export(): Map<String, String> = _books.value.filterValues { it.isNotEmpty() }.mapValues { encode(it.value) } +
        prefs.all.filterKeys { it.startsWith(MINE) }.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }

    fun import(all: Map<String, String>) {
        prefs.edit().clear().apply {
            all.forEach { (k, v) -> if (k.startsWith(MINE)) putString(k, v.take(40)) else if (decode(v).isNotEmpty()) putString(k, v) }
        }.apply()
        _books.value = read()
    }

    private fun read(): Map<String, Map<String, DutyCell>> =
        prefs.all.filterKeys { !it.startsWith(MINE) }.mapNotNull { (k, v) -> (v as? String)?.let { k to decode(it) } }.toMap()

    companion object {
        private const val PREFS = "duty_codes"
        /** Key prefix of [mine]; a calendar id never starts so. */
        private const val MINE = "mine:"

        /** One cell as a short string: "S12" a kind of work, "O" a day off, "L1.2.0" leave (kind code, quarters, from start). */
        fun encodeCell(c: DutyCell): String? = when (c) {
            is DutyCell.Shift -> "S${c.shiftTypeId}"
            DutyCell.Off -> "O"
            is DutyCell.Leave -> "L${c.kind.code}.${c.quarters}.${if (c.fromStart) 1 else 0}"
            is DutyCell.Unknown -> null
        }

        fun decodeCell(s: String): DutyCell? = when {
            s == "O" -> DutyCell.Off
            s.startsWith("S") -> s.drop(1).toLongOrNull()?.let { DutyCell.Shift(it) }
            s.startsWith("L") -> s.drop(1).split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { (k, q, f) ->
                LeaveKind.fromCode(k)?.takeIf { q in 1..4 }?.let { DutyCell.Leave(it, q, f == 1) }
            }
            else -> null
        }

        fun encode(book: Map<String, DutyCell>): String =
            JSONObject().apply { book.forEach { (k, c) -> encodeCell(c)?.let { put(k, it) } } }.toString()

        fun decode(s: String): Map<String, DutyCell> = runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().mapNotNull { k -> decodeCell(o.optString(k))?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
    }
}
