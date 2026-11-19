package com.bamnat.core.tier

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.bamnat.core.text.Josa

/**
 * 2.0.0 [RVO-14] (검토 I11bb-02 '끝나기 전에 알려 주는 프로', PRO-29·U11b-10, 총괄 11-12 #481 '프로를 잃으면 앱이 쓴 휴대폰 캘린더 일정이
 * 지워지는 것을 지우기 전에 한 번'): 조용히 꺼지는 기능(휴대폰 캘린더·자동 백업·끄기 미션·구독 주소)이 가장 큰 불만이 돼요. 프로가
 * 저절로 끝나는 휴대폰에는 끝나기 전날 저녁에 한 번, 그날부터 멈추는 것 가운데 **이 휴대폰이 쓰는 것만** 묶어 알려요. 무엇이 지워지고
 * 무엇이 남는지, 다시 프로가 되면 다시 켜진다는 것까지. 끄기 미션의 7일(처음 7일·광고 7일)이 끝날 때도 같은 저녁에 한 번.
 *
 * 저절로 끝나지 않는 프로(갱신하는 구독·관리자 휴대폰·팀·직원 요금제·나눠 받은 프로·하루 이용권)에는 알리지 않아요: 끝나는 날을
 * 휴대폰이 확실히 모르거나(갱신·나눠 준 분의 구독), 사용자가 그날 고른 것(하루 이용권)이에요.
 */
object ProEnding {
    /** The evening before the end's day, at this time; an end before [EARLY] counts as the night before (00:17 → the day before). */
    val EVENING: LocalTime = LocalTime.of(20, 0)
    private val EARLY: LocalTime = LocalTime.of(6, 0)

    enum class Stop(val key: String, val words: String) {
        PHONE_CALENDAR("calendar", "휴대폰 캘린더의 근무 일정을 지워요(앱 안 근무는 그대로예요)"),
        AUTO_BACKUP("backup", "매일 자동 백업이 멈춰요"),
        MISSION("mission", "알람이 끄기 미션 없이 울려요"),
        FEED("feed", "구독 주소에 새 근무가 더 가지 않아요"),
    }

    /** What this phone uses now of what stops (and what the server has not opened to everyone). */
    data class Use(
        val phoneCalendar: Boolean = false,
        val autoBackup: Boolean = false,
        val mission: Boolean = false,
        val feed: Boolean = false,
    )

    fun stops(use: Use): List<Stop> = buildList {
        if (use.phoneCalendar) add(Stop.PHONE_CALENDAR)
        if (use.autoBackup) add(Stop.AUTO_BACKUP)
        if (use.mission) add(Stop.MISSION)
        if (use.feed) add(Stop.FEED)
    }

    /**
     * When the 프로 this phone has at [now] ends by itself, or null: none now, or one that does not end by the clock as far as the phone
     * knows. 프로 that follow one another (보상 이용권 then 감사 기간, a 3.1 hold then 감사 기간) count as one: the end is when none is left.
     */
    fun end(sources: ProSources, now: Instant, zone: ZoneId): Instant? {
        var s = sources
        var at = now
        repeat(LINKS) {
            val a = ProAccess.resolve(s, at, zone)
            if (!a.pro) return at.takeIf { it > now }
            when (a.reason) {
                ProReason.THANKS, ProReason.PASS, ProReason.PASS_1Y -> {}
                // 2.0.0 [RVO-12] The hold ends at the start of its day and 결제 opens: what is left is what a launched phone has.
                ProReason.HELD -> s = s.copy(billingLive = true, heldUntil = null)
                // A subscription the user turned off ends at its expiry; one set to renew does not end. PRO-04: an expiry the server has not
                // checked is the phone's own 3-day hold, not the end: no notice from it.
                ProReason.SUBSCRIPTION, ProReason.TRIAL -> if (s.subscription?.autoRenew != false || s.subscription?.verified != true) return null
                else -> return null
            }
            at = a.until ?: return null
        }
        return null
    }

    /** When to tell of an end at [end]: the evening before its day, or [now] when that has passed and the end has not. */
    fun noticeAt(end: Instant, now: Instant, zone: ZoneId): Instant? {
        if (!now.isBefore(end)) return null
        val local = end.atZone(zone)
        val day = if (local.toLocalTime().isBefore(EARLY)) local.toLocalDate().minusDays(1) else local.toLocalDate()
        val evening = day.minusDays(1).atTime(EVENING).atZone(zone).toInstant()
        return maxOf(minOf(evening, end), now)
    }

    /** "오늘", "내일", "2일 뒤" before the date: how far [day] is from [today]. */
    fun dayWords(day: LocalDate, today: LocalDate): String {
        val left = ChronoUnit.DAYS.between(today, day)
        val near = when {
            left <= 0 -> "오늘"
            left == 1L -> "내일"
            else -> "${left}일 뒤"
        }
        return "$near(${day.monthValue}월 ${day.dayOfMonth}일)"
    }

    /**
     * When [what] ends, said so the user never loses a day they were promised: an end before 06:00 (감사 기간 00:17) is '…까지 써요' with
     * the day before, a later one names its time ('내일(12월 20일) 오전 9:00에 끝나요').
     */
    fun whenWords(what: String, end: Instant, now: Instant, zone: ZoneId): String {
        val local = end.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        if (local.toLocalTime().isBefore(EARLY)) return "${what}${Josa.eulreul(what)} ${dayWords(local.toLocalDate().minusDays(1), today)}까지 써요"
        val t = local.toLocalTime()
        val clock = "${if (t.hour < 12) "오전" else "오후"} ${(t.hour + 11) % 12 + 1}:${"%02d".format(t.minute)}"
        return "${what}${Josa.iga(what)} ${dayWords(local.toLocalDate(), today)} ${clock}에 끝나요"
    }

    /**
     * The notice of a 프로 that ends at [end] with [stops] on this phone: a title that says when, a text of what stops and what stays.
     */
    fun words(stops: List<Stop>, end: Instant, now: Instant, zone: ZoneId): Pair<String, String> =
        whenWords("프로", end, now, zone) to
            ("그다음부터 " + stops.joinToString(" · ") { it.words } + ". 만든 기록과 설정은 남고, 다시 프로가 되면 다시 켜져요.")

    /** PRO-29·U11b-10: the 끄기 미션's 7 days (처음 7일·광고 7일) end at [end] while no 프로 keeps it on. */
    fun missionWords(end: Instant, now: Instant, zone: ZoneId): Pair<String, String> =
        whenWords("끄기 미션", end, now, zone) to
            "그다음 알람부터 미션 없이 울려요. 설정 › 알람 › 확실하게 깨우기에서 광고 한 편으로 7일 더 열 수 있어요."

    /**
     * PRO-29 '아침 한 줄': a shift alarm the user set a 끄기 미션 (or 일어났나요) for rang without it, because no 프로 or 7일권 keeps it now.
     * Told once after it is turned off, until the mission is open again.
     */
    val MORNING: Pair<String, String> =
        "오늘 알람은 끄기 미션 없이 울렸어요" to "미션을 열어 둔 기간이 끝났어요. 광고 한 편이면 7일 더 열려요."

    /** At most this many 프로 one after another are followed to their end. */
    private const val LINKS = 8
}
