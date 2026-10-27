package com.bamnat.core.plaza

import com.bamnat.core.remote.Json

/**
 * [PLZD-01][PLZD-02][PLZD-03] 1.30 묶음 BD (사용자 2026-10-27: "광장에 이전 버젼과 동일한 내용이면 다시 올리지 못하게 … 한개 회사를
 * 가지고 여러개의 규칙을 보내는 사람이 존재함 변별력을 가져야한다 … 2교대인데 3교대를 넣어서 내용이 달라진것을 올리는등 기존 근무주기가
 * 다르거나 비정상 사용을 금지"): what a setup about to be sent is beside a version the plaza already has, before anything goes.
 *
 *  - [Kind.SAME]: nothing new. The order may start on another day (a coworker on another 조) and the pay may be left out on
 *    either side; neither is new. The app offers the plaza's version instead of sending ("이미 있어요 · 적용해 드릴까요?").
 *  - [Kind.SHAPE]: the work itself has another shape ([Shape]: how many kinds of work, how long the order, 요일별 or 근무표),
 *    so it is another 사업장 or 부서, never the next version of this one (the server's src/plaza-guard.js says the same).
 *  - [Kind.DIFFERENT]: the same shape with values that differ: best sent as a proposal on that version ([rows]).
 *
 * Both files are company settings files (format `bamnat-company`) as the plaza has them: no amounts.
 */
object SameSetup {
    /**
     * The shape of a setup's work: [works] kinds of work in its order (two shifts of the same times are one), its order [days]
     * long (an order written twice over is its one turn), or the days of a week ([weekly]), or a month's 근무표 ([roster]).
     */
    data class Shape(val works: Int, val days: Int, val weekly: Boolean = false, val roster: Boolean = false) {
        /** "2교대 · 4일 순서", "요일별 · 2가지 근무", "매달 근무표". */
        val words: String
            get() = when {
                roster -> "매달 근무표"
                weekly -> "요일별 · " + workWords(works)
                else -> "${workWords(works)} · ${days}일 순서"
            }

        private fun workWords(n: Int) = when {
            n >= 2 -> "${n}교대"
            n == 1 -> "한 가지 근무"
            else -> "쉬는 날만"
        }
    }

    enum class Kind { SAME, SHAPE, DIFFERENT }

    /**
     * What [compare] found. [rows] are the items that differ, the plaza's version first ([CompareRow.before]) and the one about to
     * go second. [payUnknown]: the plaza's pay is hidden from this phone (not a member), so only the work was compared.
     */
    data class Verdict(val kind: Kind, val rows: List<CompareRow>, val mine: Shape?, val theirs: Shape?, val payUnknown: Boolean = false) {
        val same: Boolean get() = kind == Kind.SAME
    }

    /** The shape of [text]; null when it says nothing of the work (pay only) or is not a company file. */
    fun shape(text: String): Shape? = root(text)?.let(::shape)

    fun shape(root: Map<*, *>): Shape? {
        if (root["format"] != "bamnat-company") return null
        val types = (root["shiftTypes"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val sig = types.associate { t ->
            (t["name"] as? String).orEmpty() to (if (t["off"] == true) null else "${t["start"] ?: ""}-${t["end"] ?: ""}")
        }
        // A name the file does not list still counts as a work of its own; a day with none is a rest.
        fun work(name: Any?): String? = (name as? String)?.let { n -> if (sig.containsKey(n)) sig[n] else "?$n" }
        if (root["roster"] == true) return Shape(sig.values.filterNotNull().distinct().size, 0, roster = true)
        (root["weekly"] as? List<*>)?.takeIf { it.size == 7 }?.let { w ->
            return Shape(w.mapNotNull(::work).distinct().size, 7, weekly = true)
        }
        val steps = ((root["rotation"] as? Map<*, *>)?.get("steps") as? List<*>).orEmpty().map(::work)
        if (steps.isEmpty()) return null
        return Shape(steps.filterNotNull().distinct().size, period(steps).size)
    }

    /** Whether two shapes can be one department's: the same, or one of them says nothing of the work. */
    fun sameShape(a: Shape?, b: Shape?): Boolean = a == null || b == null || a == b

    /**
     * [mine] (about to be sent) beside [theirs] (a version on the plaza). [theirsPayHidden]: the plaza sent this phone only the
     * work, so the pay is not compared. Pay left out of [mine] is not a difference either: sending only the work adds nothing.
     */
    fun compare(mine: String, theirs: String, theirsPayHidden: Boolean = false): Verdict {
        val m = root(mine)
        val t = root(theirs)
        if (m == null || t == null) return Verdict(Kind.DIFFERENT, emptyList(), null, null)
        val ms = shape(m)
        val ts = shape(t)
        if (!sameShape(ms, ts)) return Verdict(Kind.SHAPE, emptyList(), ms, ts)
        val minePay = m.containsKey("pay")
        val a = if (minePay) t else t.without("pay")
        val b = turnedLike(if (theirsPayHidden) m.without("pay") else m, a)
        val rows = SetupCompare.rows(SetupFacts.read(a), SetupFacts.read(b)).filter { !it.same }
        return Verdict(if (rows.isEmpty()) Kind.SAME else Kind.DIFFERENT, rows, ms, ts, payUnknown = theirsPayHidden && minePay)
    }

    /** One turn of [steps]: the shortest block that, said again and again, is all of it. */
    fun <T> period(steps: List<T>): List<T> {
        val n = steps.size
        for (p in 1..n) {
            if (n % p == 0 && (p until n).all { steps[it] == steps[it - p] }) return steps.take(p)
        }
        return steps
    }

    /** Whether [a] and [b] are one order started on different days (and written once or more over). */
    fun <T> sameTurn(a: List<T>, b: List<T>): Boolean {
        val x = period(a)
        val y = period(b)
        if (x.size != y.size) return false
        return x.indices.any { k -> x.indices.all { i -> x[(i + k) % x.size] == y[i] } }
    }

    /** [file] with its order as [like]'s when the two are one order from another day, so only a real change shows. */
    private fun turnedLike(file: Map<*, *>, like: Map<*, *>): Map<*, *> {
        val mine = ((file["rotation"] as? Map<*, *>)?.get("steps") as? List<*>) ?: return file
        val rot = like["rotation"] as? Map<*, *> ?: return file
        val theirs = rot["steps"] as? List<*> ?: return file
        if (mine == theirs || !sameTurn(mine, theirs)) return file
        return file.with("rotation", (file["rotation"] as Map<*, *>).with("steps", theirs))
    }

    private fun root(text: String): Map<*, *>? = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.without(key: String): Map<*, *> = (this as Map<Any?, Any?>).filterKeys { it != key }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.with(key: String, value: Any?): Map<*, *> = LinkedHashMap(this as Map<Any?, Any?>).apply { put(key, value) }
}
