package com.bamnat.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [QA-08] The one place the app asks what day and time it is (결정 D66). Code reads [date], [dateTime], [millis]…
 * here instead of `LocalDate.now()` or `System.currentTimeMillis()`, so a test can [fix] the clock and draw the
 * same screen on any day. On a phone it is the system clock, read afresh each time: a change of time zone on the
 * phone is followed at once. ClockRulesTest keeps direct calls out of the main code.
 */
object Today {
    @Volatile private var fixed: Clock? = null

    /**
     * 2.0.0 [I60b-01] (검토 2026-11-12) 시간 여행 시험: 시험 JVM에 `-Dbamnat.todayShift=P30D`(ISO-8601 기간)를 주면 '시스템 시계'가
     * 그만큼 앞으로 가요. 날마다 main에서 core·앱 시험을 30·365일 뒤로 한 번 돌려(.github/workflows/date-watch.yml), 그날이 오면 깨질
     * 시험을 미리 알아요. 폰에는 이 속성이 없어 언제나 null이에요. 시험이 [fix]·[use]로 정한 시계는 그대로예요.
     */
    private val shift: java.time.Duration? = shiftOf(System.getProperty("bamnat.todayShift"))

    /** [I60b-01] 'P30D' → 30일. 비었거나 읽지 못하면 null(시계를 옮기지 않아요). */
    internal fun shiftOf(raw: String?): java.time.Duration? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.time.Duration.parse(it) }.getOrNull() }

    /** [I60b-01] 이 시험이 시간 여행 중이면 옮긴 만큼(아니면 null). */
    val travelled: java.time.Duration? get() = shift

    /** The system clock in the phone's time zone (moved by [shift] in a time-travel test run), or the clock a test fixed. */
    val clock: Clock get() = fixed ?: Clock.systemDefaultZone().let { c -> shift?.let { Clock.offset(c, it) } ?: c }

    fun zone(): ZoneId = clock.zone
    fun instant(): Instant = clock.instant()
    fun millis(): Long = clock.millis()
    fun date(): LocalDate = LocalDate.now(clock)
    fun dateTime(): LocalDateTime = LocalDateTime.now(clock)
    fun time(): LocalTime = LocalTime.now(clock)
    fun month(): YearMonth = YearMonth.now(clock)
    fun zoned(): ZonedDateTime = ZonedDateTime.now(clock)

    /** The date in [zone], for code that keeps its own zone (UTC file stamps, a watch's time zone). */
    fun date(zone: ZoneId): LocalDate = LocalDate.now(clock.withZone(zone))
    fun dateTime(zone: ZoneId): LocalDateTime = LocalDateTime.now(clock.withZone(zone))

    /** Tests: stop the clock at [at]. */
    fun fix(at: ZonedDateTime) { fixed = Clock.fixed(at.toInstant(), at.zone) }

    /** Tests: use [clock] as it is (a fixed or offset clock). */
    fun use(clock: Clock) { fixed = clock }

    /** Back to the system clock. */
    fun reset() { fixed = null }

    val isFixed: Boolean get() = fixed != null
}
