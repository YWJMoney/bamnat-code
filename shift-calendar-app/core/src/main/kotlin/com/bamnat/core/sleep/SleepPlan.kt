package com.bamnat.core.sleep

import com.bamnat.core.schedule.ResolvedDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [SLP-03][SLP-05][SLP-07][SLP-13] Sleep times that fit the schedule, worked out by rules from the
 * shifts around a day. Wording follows the plan's rules: "일정에 맞춘 수면 시간 제안", no claim
 * to diagnose, prevent or treat anything. Each tip says how strong its basis is.
 *
 * Rules (app defaults, to be checked by an occupational or sleep physician before release), worked
 * out in [SleepSchedule]:
 * - After work that runs overnight (a night, a 24-hour duty): sleep from the way home after it ends,
 *   at least 6 hours in one go (KOSHA guidance in the plan), or the user's target if longer.
 * - Before the first night of a run: a nap of up to 90 minutes in the afternoon, ending 2.5 hours
 *   before the shift.
 * - Before an early start: in bed early enough for the target, waking at its alarm (or 90 minutes
 *   before it without one).
 * - Caffeine: none in the 6 hours before a planned sleep (general information, low evidence).
 * - Drowsy driving: one reminder when overnight work ends.
 */
object SleepPlan {
    enum class Kind(val label: String) {
        NAP("야간 전 낮잠"),
        SLEEP_AFTER_NIGHT("퇴근 뒤 잠"),
        BEDTIME("잠자리"),
        CAFFEINE("카페인 마지막"),
        DROWSY("퇴근길 졸음 주의"),
    }

    enum class Basis(val label: String) {
        GUIDANCE("안전보건 지침"),
        GENERAL("일반 권고"),
        LOW("근거 수준 낮음"),
    }

    /** 2.0.0 [RVH-75] [label]: the kind's name in the words of the work ([Words.label]). */
    data class Tip(val kind: Kind, val at: LocalDateTime, val until: LocalDateTime?, val text: String, val basis: Basis,
                   val label: String = kind.label)

    /**
     * 2.0.0 [RVH-75] 검토 TYPE-08·T30-16·T30-91 (가게 잠 계획에 '내일 09:00 근무라·퇴근 뒤 잠·야간 전 낮잠'): the plan's words by the
     * kind of work. 교대·일근·알바 keep 근무·퇴근·야간; a shop speaks of 영업·마감, 현장·투자 of 일정.
     */
    class Words private constructor(
        private val nap: String, private val after: String, private val drowsy: String,
        /** "첫 야간 전에" */ val beforeNight: String,
        /** "퇴근하고" */ val offWork: String,
        /** "근무" (내일 09:00 근무라, 다음 근무까지) */ val work: String,
        /** "이라" or "라" after [work] */ val ra: String,
        /** "퇴근 후" (the briefing notice: 퇴근 후 08:30~14:30 잘 시간) */ val afterWork: String,
        /** "예요" or "이에요" after [work] */ val ye: String,
    ) {
        fun label(kind: Kind): String = when (kind) { Kind.NAP -> nap; Kind.SLEEP_AFTER_NIGHT -> after; Kind.DROWSY -> drowsy; else -> kind.label }

        companion object {
            private val WORKER = Words("야간 전 낮잠", "퇴근 뒤 잠", "퇴근길 졸음 주의", "첫 야간 전에", "퇴근하고", "근무", "라", "퇴근 후", "예요")
            private val SHOP = Words("밤 영업 전 낮잠", "마감 뒤 잠", "마감 뒤 졸음 주의", "밤 영업 전에", "마감하고", "영업", "이라", "마감 후", "이에요")
            private val PLANS = Words("밤 일정 전 낮잠", "일 마친 뒤 잠", "일 마친 뒤 졸음 주의", "밤 일정 전에", "일을 마치고", "일정", "이라", "일 마친 뒤", "이에요")
            fun of(style: com.bamnat.core.work.WorkStyle?): Words = when (style) {
                com.bamnat.core.work.WorkStyle.BUSINESS -> SHOP
                com.bamnat.core.work.WorkStyle.FIELD, com.bamnat.core.work.WorkStyle.INVEST -> PLANS
                else -> WORKER
            }
        }
    }

    const val DEFAULT_TARGET_MINUTES = 7 * 60
    const val MIN_AFTER_NIGHT_MINUTES = 6 * 60
    const val COMMUTE_MINUTES = 60L
    const val NAP_MINUTES = 90L
    const val NAP_GAP_MINUTES = 150L
    const val WAKE_BEFORE_MINUTES = 90L
    const val CAFFEINE_GAP_HOURS = 6L
    /** Day shifts starting before this need an early night. */
    val EARLY_START: LocalTime = LocalTime.of(10, 0)

    /** Tips for [day], given the day before and after it, without alarms; sorted by time. */
    fun forDay(prev: ResolvedDay?, day: ResolvedDay, next: ResolvedDay?, targetMinutes: Int = DEFAULT_TARGET_MINUTES,
               style: com.bamnat.core.work.WorkStyle? = null): List<Tip> =
        tips(SleepSchedule.of(listOfNotNull(prev, day, next), emptyList(), targetMinutes), day.date, style)

    /**
     * [SLP-14] The tips for [date] from the plan the today card reads too: around the day's own work
     * (a nap before a first night, the way home and the sleep after work that ran overnight), and the
     * night before an early start the next day.
     */
    fun tips(schedule: SleepSchedule, date: LocalDate, style: com.bamnat.core.work.WorkStyle? = null): List<Tip> {
        val tips = mutableListOf<Tip>()
        val words = Words.of(style)
        fun tip(kind: Kind, at: LocalDateTime, until: LocalDateTime?, text: String, basis: Basis) = Tip(kind, at, until, text, basis, words.label(kind))
        for (w in schedule.work.filter { it.date == date }) {
            schedule.napBefore(w)?.let { n ->
                tips += tip(Kind.NAP, n.from, n.to, "${words.beforeNight} ${n.minutes}분 안쪽으로 짧게 자 두기", Basis.GENERAL)
            }
            if (!w.overnight) continue
            tips += tip(Kind.DROWSY, w.to, null, "밤새 일한 직후라 졸릴 수 있어요. 운전은 쉬고 나서", Basis.GENERAL)
            schedule.sleepAfter(w)?.let { s ->
                tips += tip(Kind.SLEEP_AFTER_NIGHT, s.from, s.to,
                    if (s.short) "다음 ${words.work}까지 쉴 틈이 짧아요. ${words.offWork} 바로 자기 (암막, 휴대폰 무음)"
                    else "${words.offWork} 바로 ${hours(s.minutes)} 이상 이어서 자기 (암막, 휴대폰 무음)", Basis.GUIDANCE)
                tips += caffeine(s.from)
            }
        }
        for (s in schedule.sleeps) {
            val w = s.before?.takeIf { s.after == null && it.date == date.plusDays(1) } ?: continue
            // [SET-04] Read on the evening before: a bed time past midnight says so (ux-review 3 (5) 24).
            val bed = (if (s.from.toLocalDate() != date) "자정 넘어 " else "") + t(s.from)
            tips += tip(Kind.BEDTIME, s.from, s.to,
                if (s.short) "내일 ${t(w.from)} ${words.work}${words.ra} 쉴 틈이 짧아요. ${bed}엔 잠자리에 들기"
                else "내일 ${t(w.from)} ${words.work}${words.ra} ${bed}쯤 잠자리에 들기", Basis.GENERAL)
            tips += caffeine(s.from)
        }
        return tips.sortedBy { it.at }
    }

    private fun caffeine(sleep: LocalDateTime) =
        Tip(Kind.CAFFEINE, sleep.minusHours(CAFFEINE_GAP_HOURS), null, "잠들기 ${CAFFEINE_GAP_HOURS}시간 전부터는 커피·에너지 음료 줄이기", Basis.LOW)

    private fun t(x: LocalDateTime) = "%02d:%02d".format(x.hour, x.minute)
    private fun hours(m: Long) = if (m % 60 == 0L) "${m / 60}시간" else "${m / 60}시간 ${m % 60}분"
}
