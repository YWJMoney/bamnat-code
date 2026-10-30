package com.bamnat.core.recipe

/**
 * [FREE-10] 1.35 테마 기간 (사용자 2026-10-30 15:56, 16:00): a theme someone else made is worn for 30 days; then the app asks
 * "기간이 끝났어요. 연장할까요?". 연장 plays a rewarded ad and only a whole view adds 30 days; 아니요 puts back the user's own look and
 * keeps the theme in 입었던 테마, where wearing it again asks for an ad. Never on: the user's own themes, 프로, the ads switch off.
 * A theme is looks only ([BuiltInThemes.LOOK_KINDS]), so none of this ever touches a record, an alarm or a pay rule.
 *
 * When the ad cannot come: offline the ask waits (no days given); no ad to show after one more try gives [NO_AD_DAYS] and the reason
 * is counted ([freeLog]), so the operator sees how often and why. Closing the ask without an answer keeps the theme and asks again.
 */
data class ThemeLease(
    /** What is worn now; null for the user's own look. */
    val worn: Wear? = null,
    /** 입었던 테마, newest first, at most [HISTORY]. */
    val history: List<Wear> = emptyList(),
    /** Themes put on without an ad so far (the first [FREE_FIRST] are free). */
    val freeUsed: Int = 0,
    /** Days given because no ad could come, by "yyyy-MM-dd|reason". */
    val freeLog: Map<String, Int> = emptyMap(),
    /** The day the last ask was closed without an answer (asked again the next day). */
    val askedDay: Long? = null,
) {
    /**
     * One theme: its name, where it came from ([code] a 나눔 판 code, "builtin:<title>" or "" for one of mine), its written form so it
     * can be worn again, and the days it runs ([until] the first day it is over).
     */
    data class Wear(val title: String, val code: String, val body: String, val from: Long, val until: Long, val own: Boolean = false) {
        val key: String get() = code.ifEmpty { "own:$title" }
    }

    enum class State { NONE, OK, SOON, OVER }

    /** Where the worn theme stands on [today]; NONE when no time runs at all. */
    fun state(today: Long, adsOn: Boolean, pro: Boolean): State {
        val w = worn ?: return State.NONE
        if (!runs(w, adsOn, pro)) return State.NONE
        val left = w.until - today
        return when {
            left <= 0 -> State.OVER
            left <= HEADS_UP -> State.SOON
            else -> State.OK
        }
    }

    /** Whether the ask is to be shown on [today]: over, and not closed unanswered today. */
    fun ask(today: Long, adsOn: Boolean, pro: Boolean): Boolean = state(today, adsOn, pro) == State.OVER && askedDay != today

    fun daysLeft(today: Long): Long? = worn?.let { (it.until - today).coerceAtLeast(0) }

    /** Whether putting on [theme] now asks for an ad (not mine, not 프로, the switch on, the free ones used). */
    fun needsAd(own: Boolean, adsOn: Boolean, pro: Boolean): Boolean = adsOn && !pro && !own && freeUsed >= FREE_FIRST

    /** [w] is worn from [today] for [DAYS]; it goes first in the history. [free] counts one of the first free ones. */
    fun wear(w: Wear, today: Long, free: Boolean): ThemeLease {
        val now = w.copy(from = today, until = today + DAYS)
        return copy(worn = now, history = (listOf(now) + history.filter { it.key != now.key }).take(HISTORY), askedDay = null,
            freeUsed = freeUsed + if (free) 1 else 0)
    }

    /** A whole ad watched: 30 more days from [today] (or from the end, if it is not over yet). */
    fun extend(today: Long): ThemeLease = worn?.let { w ->
        val start = maxOf(today, w.until)
        val next = w.copy(until = start + DAYS)
        copy(worn = next, history = history.map { if (it.key == next.key) next else it }, askedDay = null)
    } ?: this

    /** No ad could come for [reason] on [day] ("yyyy-MM-dd"): [NO_AD_DAYS] more, and the reason counted. */
    fun noAd(today: Long, day: String, reason: String): ThemeLease {
        val r = reason.takeIf { it in REASONS } ?: "other"
        val k = "$day|$r"
        val logged = copy(freeLog = (freeLog + (k to (freeLog[k] ?: 0) + 1)).entries.sortedByDescending { it.key }.take(LOG_KEEP).associate { it.toPair() })
        return logged.worn?.let { w ->
            val next = w.copy(until = maxOf(today, w.until) + NO_AD_DAYS)
            logged.copy(worn = next, history = logged.history.map { if (it.key == next.key) next else it }, askedDay = null)
        } ?: logged
    }

    /** 아니요: back to the user's own look; the theme stays in the history to wear again. */
    fun takeOff(): ThemeLease = copy(worn = null, askedDay = null)

    /** The ask closed without an answer on [today]: the theme stays and is asked about again another day. */
    fun later(today: Long): ThemeLease = copy(askedDay = today)

    /** 프로 ended on [today]: the worn theme runs at least 30 more days from then. */
    fun proEnded(today: Long): ThemeLease = worn?.let { w ->
        if (w.own || w.until >= today + DAYS) this else copy(worn = w.copy(until = today + DAYS))
    } ?: this

    private fun runs(w: Wear, adsOn: Boolean, pro: Boolean) = adsOn && !pro && !w.own

    companion object {
        const val DAYS = 30L
        const val NO_AD_DAYS = 7L
        const val HEADS_UP = 3L
        const val FREE_FIRST = 2
        const val HISTORY = 30
        const val LOG_KEEP = 200

        /** Why no ad came: none to show, no internet, too slow, it failed to show, the ad side not ready, the switch off. */
        val REASONS = listOf("no_fill", "offline", "timeout", "show_failed", "not_ready", "flag_off", "other")

        /** Written as one JSON-free line per part so the settings keep it as text. */
        fun encode(l: ThemeLease): String = buildString {
            l.worn?.let { append("W\t").append(wear(it)).append('\n') }
            l.history.forEach { append("H\t").append(wear(it)).append('\n') }
            append("F\t").append(l.freeUsed).append('\n')
            l.askedDay?.let { append("A\t").append(it).append('\n') }
            l.freeLog.forEach { (k, v) -> append("L\t").append(k).append('\t').append(v).append('\n') }
        }

        fun decode(s: String?): ThemeLease {
            if (s.isNullOrBlank()) return ThemeLease()
            var worn: Wear? = null
            val history = ArrayList<Wear>()
            var free = 0
            var asked: Long? = null
            val log = LinkedHashMap<String, Int>()
            for (line in s.lines()) {
                val p = line.split('\t')
                when (p[0]) {
                    "W" -> worn = wearOf(p.drop(1))
                    "H" -> wearOf(p.drop(1))?.let { history += it }
                    "F" -> free = p.getOrNull(1)?.toIntOrNull() ?: 0
                    "A" -> asked = p.getOrNull(1)?.toLongOrNull()
                    "L" -> if (p.size == 3) p[2].toIntOrNull()?.let { log[p[1]] = it }
                }
            }
            return ThemeLease(worn, history.take(HISTORY), free, log, asked)
        }

        private fun clean(t: String) = t.replace('\t', ' ').replace('\n', ' ')
        private fun wear(w: Wear) = listOf(clean(w.title), clean(w.code), java.util.Base64.getEncoder().encodeToString(w.body.toByteArray()),
            w.from.toString(), w.until.toString(), if (w.own) "1" else "0").joinToString("\t")

        private fun wearOf(p: List<String>): Wear? = runCatching {
            Wear(p[0], p[1], String(java.util.Base64.getDecoder().decode(p[2])), p[3].toLong(), p[4].toLong(), p.getOrNull(5) == "1")
        }.getOrNull()
    }
}
