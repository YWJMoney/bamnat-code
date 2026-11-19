package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftType
import java.time.LocalTime

/**
 * 2.0.0 [RVN-24] 검토 I10ac-02 내 달력이 회사를 찾아 주기: the order the user put on their calendar, read as a public cycle's codes
 * (D 주간, E 오후, N 야간, X 당번, O 쉬는 날), matched on the phone against the public cycles and the 대표 주기 of the companies the
 * phone already read, so "이름을 몰라도" a company with the same work (and its pay way) is offered. Nothing about the calendar is sent.
 */
object CalendarCycles {
    /** A shift of 20 hours or more is a 당번 (24시간 맞교대·소방·경비). */
    private const val WHOLE_DAY_MINUTES = 20 * 60

    /**
     * One day of the order as a code: a day off (or a step with no shift) 'O'; else by its times, a 당번 'X', a start from 4 to 11
     * o'clock 'D', from 12 to 17 'E', otherwise 'N'. Null for a shift with no times (it cannot be told).
     */
    fun code(t: ShiftType?): Char? {
        if (t == null || t.isOff) return 'O'
        val start = t.start ?: return null
        val end = t.end ?: return null
        val minutes = Math.floorMod(end.toSecondOfDay() / 60 - start.toSecondOfDay() / 60, 24 * 60).let { if (it == 0) 24 * 60 else it }
        return when {
            minutes >= WHOLE_DAY_MINUTES -> 'X'
            start.hour in 4..11 -> 'D'
            start.hour in 12..17 -> 'E'
            else -> 'N'
        }
    }

    /** A rotation's [steps] (shift ids, null a day off) as codes; null when a step cannot be told or it is no shift work. */
    fun codes(steps: List<Long?>, types: Map<Long, ShiftType>): String? {
        if (steps.size !in 2..CycleOrders.MAX) return null
        val out = StringBuilder()
        for (id in steps) out.append(code(id?.let { types[it] ?: return null }) ?: return null)
        return out.toString().takeIf(::shiftLike)
    }

    /**
     * A 대표 주기's step by its name (the company list carries names, not times): one of [offs] or null 'O'; 당번·당 'X'; 야간·나이트·
     * 야·N 'N'; 오후·이브닝·석·E 'E'; 주간·데이·오전·주·D 'D'. Null when the name says none of them.
     */
    fun nameCode(name: String?, offs: Set<String>): Char? {
        if (name == null || name in offs) return 'O'
        val n = name.replace(" ", "").lowercase()
        return when {
            n.startsWith("비") || n.startsWith("휴") || n == "off" -> 'O'
            n.startsWith("당") -> 'X'
            n.startsWith("야") || n.startsWith("나이트") || n == "n" || n.startsWith("night") -> 'N'
            n.startsWith("오후") || n.startsWith("이브") || n.startsWith("석") || n == "e" || n.startsWith("evening") -> 'E'
            n.startsWith("주") || n.startsWith("데이") || n.startsWith("오전") || n == "d" || n.startsWith("day") -> 'D'
            else -> null
        }
    }

    /** A 대표 주기 by names as codes; null when a name cannot be told or it is no shift work. */
    fun nameCodes(steps: List<String?>, offs: Set<String>): String? {
        if (steps.size !in 2..CycleOrders.MAX) return null
        val out = StringBuilder()
        for (s in steps) out.append(nameCode(s, offs) ?: return null)
        return out.toString().takeIf(::shiftLike)
    }

    /** 오후·야간·당번 in it: a 주5일 week matches too many to say anything. */
    fun shiftLike(codes: String): Boolean = codes.any { it == 'E' || it == 'N' || it == 'X' }

    /** [a] and [b] are the same order started on another day; a 비번 is a day off (the calendar has no 비번 of its own). */
    fun same(a: String, b: String): Boolean =
        a.length == b.length && PublicCycles.sameRotation(a.replace('B', 'O'), b.replace('B', 'O'))

    /**
     * The public rows whose own order (not the usual one guessed for a count) is [mine], those with the times of [times] (a code's
     * start) first, a community answer last; at most [max].
     */
    fun publicRows(mine: String, rows: List<PublicCycle>, times: Map<Char, LocalTime> = emptyMap(), max: Int = 3): List<PublicCycle> =
        rows.asSequence()
            .filter { it.orderState == OrderState.EXACT && !it.roster && it.codes.isNotEmpty() && same(mine, it.codes) }
            .sortedWith(compareBy<PublicCycle> { it.weak }.thenByDescending { timeFit(it, times) }.thenBy { it.company })
            .take(max).toList()

    /** How many of [times]'s codes the row starts within 30 minutes of. */
    fun timeFit(row: PublicCycle, times: Map<Char, LocalTime>): Int = times.count { (code, start) ->
        row.shifts.firstOrNull { it.code == code }?.let { s ->
            val d = Math.floorMod(s.start.toSecondOfDay() / 60 - start.toSecondOfDay() / 60, 24 * 60)
            minOf(d, 24 * 60 - d) <= 30
        } ?: false
    }

    /** The first start time of each code in [steps] (for [publicRows]'s order). */
    fun times(steps: List<Long?>, types: Map<Long, ShiftType>): Map<Char, LocalTime> = buildMap {
        for (id in steps) {
            val t = id?.let { types[it] } ?: continue
            val c = code(t) ?: continue
            if (c != 'O' && c !in this) t.start?.let { put(c, it) }
        }
    }
}
