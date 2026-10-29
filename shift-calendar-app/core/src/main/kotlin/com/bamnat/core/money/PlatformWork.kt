package com.bamnat.core.money

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/**
 * G 1.32 [WTYP-02] 604 플랫폼(배달·운전)형 ([FieldMode.PLATFORM], 조사 `근무유형별-서비스-조사.md` 7장): a platform's day is one
 * [Job] whose title and 업체 are the platform's name, its 공수 ([Job.units]) the 건수, its amount what the platform shows,
 * its costs the day's 유류비·소모품, and the 운행 시간 in its memo ("운행 6시간 30분"). So the money tab, 받을 돈, 3.3% and
 * the 5월 묶음 count it as they count any job, with no new table; 정산 marks the week's jobs paid.
 */
object PlatformWork {
    /**
     * The platforms offered first, with the weekly 정산 the app assumes until the user changes it (조사 7장: 쿠팡이츠
     * 화요일까지 일한 것을 금요일 입금 사례; 배민·요기요·대리는 확인 필요, so they start at 일요일 마감·수요일 입금).
     */
    enum class Platform(val label: String, val cutoff: DayOfWeek, val payDay: DayOfWeek, val withholding: Boolean, val checked: Boolean) {
        BAEMIN("배민", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        COUPANG("쿠팡이츠", DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, true, true),
        YOGIYO("요기요", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        DAERI("대리운전", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        // G 1.32 [WTYP-23] 조사 대조 W28: 대리 콜 출처 (대리가계부 리뷰 "로지·콜마너·티맵 콜도 표기", "프로그램마다 건당 보험료를 다르게").
        KAKAO_DAERI("카카오T 대리", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        LOGI("로지", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        CALLMANNER("콜마너", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        TMAP_DAERI("티맵 대리", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        TAXI("택시", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, false, false),
        OTHER("기타 플랫폼", DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, true, false),
        ;

        /** G 1.32 [WTYP-23] A 대리 call source, which most often has its own 수수료 and 건당 보험료. */
        val daeri: Boolean get() = this == DAERI || this == KAKAO_DAERI || this == LOGI || this == CALLMANNER || this == TMAP_DAERI

        companion object {
            fun of(name: String): Platform? = entries.firstOrNull { it.label == name.trim() }
        }
    }

    /**
     * G 1.32 [WTYP-23] 조사 대조 W28: what a platform or 대리 콜 출처 takes off the amount it shows, as the user set it:
     * 수수료 in tenths of a percent (200 = 20%) and 보험료 per 건. Nothing is assumed: rates differ by program and change.
     */
    data class Cut(val feePermille: Int = 0, val perCall: Long = 0) {
        val isEmpty: Boolean get() = feePermille <= 0 && perCall <= 0

        /** What comes off [amount] for [count] 건. */
        fun of(amount: Long, count: Int): Long =
            ((amount.coerceAtLeast(0) * feePermille.coerceIn(0, 1000)) / 1000.0).roundToLong() + perCall.coerceAtLeast(0) * count.coerceAtLeast(0)

        /** "수수료 20% · 건당 보험 1,000원"; empty when nothing is set. */
        fun line(won: (Long) -> String): String = listOfNotNull(
            feePermille.takeIf { it > 0 }?.let { "수수료 ${percent(it)}" },
            perCall.takeIf { it > 0 }?.let { "건당 보험 ${won(it)}" },
        ).joinToString(" · ")

        companion object {
            /** "20%", "12.5%". */
            fun percent(permille: Int): String = if (permille % 10 == 0) "${permille / 10}%" else "${permille / 10}.${permille % 10}%"

            /** A typed rate ("20", "12.5") in tenths of a percent; null outside 0..100%. */
            fun parsePercent(text: String): Int? = text.trim().replace(',', '.').toDoubleOrNull()
                ?.let { Math.round(it * 10).toInt() }?.takeIf { it in 0..1000 }
        }
    }

    /** G 1.32 [WTYP-23] What [j]'s platform took that day under [rules]. */
    fun cutOf(j: Job, rules: FieldRules): Long = rules.cutOf(j.client).of(j.amount, count(j))

    /** A job written by the platform sheet: its title is its 업체 and a platform's name. */
    fun isPlatform(j: Job): Boolean = j.title.isNotBlank() && j.title == j.client && Platform.of(j.client) != null

    private val DRIVE = Regex("운행 (?:(\\d+)시간)? ?(?:(\\d+)분)?")

    /** The 운행 시간 in minutes from a platform job's memo; 0 when not written. */
    fun minutes(j: Job): Int = DRIVE.find(j.note)?.let { m ->
        (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    } ?: 0

    fun driveNote(minutes: Int): String = if (minutes <= 0) "" else "운행 " + listOfNotNull(
        (minutes / 60).takeIf { it > 0 }?.let { "${it}시간" }, (minutes % 60).takeIf { it > 0 }?.let { "${it}분" }).joinToString(" ")

    /** The day's job for [platform]: [existing] changed, or a new one. 3.3% follows the platform unless the user said. */
    fun job(date: LocalDate, platform: String, count: Int, amount: Long, minutes: Int, costs: Long, existing: Job? = null,
            withholding: Boolean = Platform.of(platform)?.withholding ?: true): Job {
        val name = platform.trim().take(20)
        val base = existing ?: Job(date = date, title = name, client = name)
        return base.copy(title = name, client = name, units = count.toDouble().takeIf { it > 0 }, amount = amount.coerceAtLeast(0),
            costs = costs.coerceAtLeast(0), withholding = withholding, status = JobStatus.DONE,
            note = driveNote(minutes), balancePaid = base.balancePaid && amount <= base.amount)
    }

    fun count(j: Job): Int = j.units?.roundToLong()?.toInt() ?: 0

    /**
     * G 1.32 [WTYP-23] 조사 대조 W29: the widget's +1건, one more 건 on [today] for [platform]. Today's job for it counts one more
     * (its amount stays, filled at 하루 마감); with none, a new one of 1건.
     */
    fun plusOne(today: LocalDate, platform: String, jobs: List<Job>): Job {
        val there = jobs.firstOrNull { it.date == today && isPlatform(it) && it.client == platform && !it.cancelled }
        return there?.copy(units = (count(there) + 1).coerceAtMost(999).toDouble())
            ?: job(today, platform, 1, 0, 0, 0)
    }

    /** G 1.32 [WTYP-23] The platform +1건 adds to: today's busiest, else the one used most in the last 60 days; null when none. */
    fun usual(today: LocalDate, jobs: List<Job>): String? {
        val live = jobs.filter { isPlatform(it) && !it.cancelled }
        live.filter { it.date == today }.maxByOrNull(::count)?.let { return it.client }
        return live.filter { !it.date.isBefore(today.minusDays(60)) && !it.date.isAfter(today) }.groupBy { it.client }
            .maxByOrNull { (_, l) -> l.size }?.key
    }

    /** [fees] G 1.32 [WTYP-23]: the 수수료·보험료 the platform took, when the user set them. */
    data class Row(val platform: String, val count: Int, val amount: Long, val minutes: Int, val costs: Long, val fees: Long = 0)

    data class Month(val month: YearMonth, val rows: List<Row>, val ledgerCosts: Long) {
        val count: Int get() = rows.sumOf { it.count }
        val amount: Long get() = rows.sumOf { it.amount }
        val minutes: Int get() = rows.sumOf { it.minutes }
        val costs: Long get() = rows.sumOf { it.costs } + ledgerCosts
        val fees: Long get() = rows.sumOf { it.fees }
        val net: Long get() = amount - costs - fees
        /** 시간당 순수익; null without 운행 시간. */
        val perHour: Long? get() = if (minutes <= 0) null else (net * 60.0 / minutes).roundToLong()
        val perCount: Long? get() = if (count <= 0) null else amount / count
    }

    /** Categories of the ledger counted as a rider's or driver's costs (유류비·정비·보험). */
    val COST_CATEGORIES = setOf("자동차", "교통")

    /** The month by platform, with the ledger's 자동차·교통 spending as costs besides each day's own, and [rules]' 수수료·보험료. */
    fun month(month: YearMonth, jobs: List<Job>, entries: List<MoneyEntry> = emptyList(), rules: FieldRules = FieldRules()): Month {
        val list = jobs.filter { YearMonth.from(it.date) == month && isPlatform(it) && !it.cancelled }
        val rows = list.groupBy { it.client }.map { (p, l) ->
            Row(p, l.sumOf(::count), l.sumOf { it.amount }, l.sumOf(::minutes), l.sumOf { it.costs }, l.sumOf { cutOf(it, rules) })
        }
            .sortedByDescending { it.amount }
        val ledger = entries.filter { it.kind == MoneyKind.EXPENSE && YearMonth.from(it.date) == month && it.category in COST_CATEGORIES }.sumOf { it.amount }
        return Month(month, rows, ledger)
    }

    /** One week of one platform still to be paid; [fees] (G 1.32 [WTYP-23]) are taken off before it comes. */
    data class Payout(val platform: String, val from: LocalDate, val to: LocalDate, val payDate: LocalDate, val jobs: List<Job>, val fees: Long = 0) {
        val amount: Long get() = jobs.sumOf { it.unpaid }
        /** What should come into the account. */
        val expected: Long get() = (amount - fees).coerceAtLeast(0)
    }

    fun payOf(platform: String, rules: FieldRules): Pair<DayOfWeek, DayOfWeek> =
        rules.platformPay[platform] ?: Platform.of(platform)?.let { it.cutoff to it.payDay } ?: (DayOfWeek.SUNDAY to DayOfWeek.WEDNESDAY)

    /** The weeks not yet paid, soonest pay day first: each week ends on the platform's 마감 요일 and is paid the next 입금 요일. */
    fun payouts(jobs: List<Job>, rules: FieldRules): List<Payout> =
        jobs.filter { isPlatform(it) && !it.cancelled && it.unpaid > 0 }.groupBy { j ->
            val (cutoff, _) = payOf(j.client, rules)
            j.client to j.date.with(TemporalAdjusters.nextOrSame(cutoff))
        }.map { (k, l) ->
            val (p, end) = k
            val pay = end.with(TemporalAdjusters.next(payOf(p, rules).second))
            Payout(p, end.minusDays(6), end, pay, l.sortedBy { it.date }, l.sumOf { cutOf(it, rules) })
        }.sortedBy { it.payDate }

    /** The week's jobs marked paid, when its money came. */
    fun settle(p: Payout): List<Job> = p.jobs.map { it.copy(depositPaid = true, balancePaid = true) }

    /**
     * A deposit that looks like [p]'s money: from the platform (its name in the memo or place) within 3 days of the pay day,
     * between 85% and 101% of what is owed (보험료·수수료 are taken off before it comes).
     */
    fun depositFor(p: Payout, entries: List<MoneyEntry>): MoneyEntry? {
        val names = listOf(p.platform, when (p.platform) {
            "배민" -> "우아한"
            "쿠팡이츠" -> "쿠팡"
            "카카오T 대리" -> "카카오"
            "티맵 대리" -> "티맵"
            else -> p.platform
        })
        return entries.filter { e ->
            e.kind == MoneyKind.INCOME && kotlin.math.abs(e.date.toEpochDay() - p.payDate.toEpochDay()) <= 3 &&
                names.any { (e.merchant + " " + e.note).contains(it) } && e.amount in (p.expected * 85 / 100)..(p.expected * 101 / 100 + 1_000)
        }.minByOrNull { kotlin.math.abs(it.amount - p.expected) }
    }
}
