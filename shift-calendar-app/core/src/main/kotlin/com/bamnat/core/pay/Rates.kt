package com.bamnat.core.pay

import java.time.LocalDate
import java.time.LocalTime

/**
 * Legal rates for one period. Numbers live here as data with a source and version, never in
 * the calculation code, so a yearly update is a data change [PAY-02]. A newer signed `remote/v1/rates.json`
 * replaces the built-in years ([RateTable.use], [REMOTE-02]).
 */
data class Rates(
    val effectiveFrom: LocalDate,
    val version: String,
    val source: String,
    /** 근로기준법 제56조 야간근로: 22:00~06:00. */
    val nightStart: LocalTime = LocalTime.of(22, 0),
    val nightEnd: LocalTime = LocalTime.of(6, 0),
    /** Premiums on top of 100%: 연장 50%, 야간 50%, 휴일 8시간 이내 50%, 8시간 초과 100%. */
    val overtimePremium: Double = 0.5,
    val nightPremium: Double = 0.5,
    val holidayPremiumWithin8h: Double = 0.5,
    val holidayPremiumOver8h: Double = 1.0,
    val dailyLimitMinutes: Int = 8 * 60,
    val weeklyLimitMinutes: Int = 40 * 60,
    /** 최저임금 시급(원). */
    val minimumHourlyWage: Long,
    /** 월 소정근로시간 기본값: 주 40시간 + 주휴 8시간 기준 209시간. */
    val defaultMonthlyHours: Int = 209,
    /** 국민연금 총 보험료율 (근로자·사업주 합계); null when not yet fixed [PAY-03]. */
    val pensionRate: Double? = null,
    /** 건강보험료율 (합계). */
    val healthRate: Double? = null,
    /** 장기요양보험료율, 소득 대비 (합계). */
    val longTermCareRate: Double? = null,
    /**
     * [REMOTE-02] 고용보험 실업급여 보험료율 (합계, 근로자·사업주 반씩): 고용보험 및 산업재해보상보험의
     * 보험료징수 등에 관한 법률 시행령 제12조 제1항 제2호 1천분의 18 (2025-12-23 시행본 확인).
     */
    val unemploymentRate: Double = 0.018,
    /**
     * 2.0.0 [RVJ-04] 검토 PAYC-04: values that change on 1 January by 고시 or 세법 and used to be code constants, so a signed rates
     * file can carry them too; null = the last year that had one (see [RateTable.latest]).
     * One side's (근로자 or 사업주) monthly 건강보험료 cap, 보건복지부고시 '월별 건강보험료액의 상한과 하한'.
     */
    val healthCapShare: Long? = null,
    /** [RVJ-04] 검토 PAYC-14: one side's monthly 건강보험료 floor from the same 고시; null = 보수월액 하한 28만 원 basis. */
    val healthMinShare: Long? = null,
    /** [RVJ-04] 식대 등 비과세 월 한도 (소득세법 시행령 제17조의2). */
    val mealFreeLimit: Long? = null,
    /** [RVJ-04] 두루누리 지원 보수 기준 (미만), only for a year whose 공고 was read. */
    val durunuriUnder: Long? = null,
    /** [RVJ-04] 산재보험 사업종류별 요율 (‰) that replace the built-in ones from this year, by [com.bamnat.core.boss.AccidentRates.Kind.code]. */
    val accidentPermille: Map<String, Double> = emptyMap(),
    val commutePermille: Double? = null,
    val wageClaimPermille: Double? = null,
    val asbestosPermille: Double? = null,
)

/** [REMOTE-02] 국민연금 기준소득월액 하한·상한 from [from] (보건복지부 고시, 7월~이듬해 6월). */
data class PensionBase(val from: LocalDate, val min: Long, val max: Long)

object RateTable {
    val builtIn: List<Rates> = listOf(
        Rates(
            effectiveFrom = LocalDate.of(2025, 1, 1),
            version = "2025.1",
            source = "근로기준법 제50·53·56조, 2025년 최저임금 고시(시급 10,030원)",
            minimumHourlyWage = 10_030,
            pensionRate = 0.09, healthRate = 0.0709, longTermCareRate = 0.009182,
        ),
        Rates(
            effectiveFrom = LocalDate.of(2026, 1, 1),
            version = "2026.1",
            source = "근로기준법 제50·53·56조, 2026년 최저임금 고시(시급 10,320원)",
            minimumHourlyWage = 10_320,
            pensionRate = 0.095, healthRate = 0.0719, longTermCareRate = 0.009448,
        ),
        // [PAY-03] Checked 2026-10-15: 최저임금 고용노동부 고시 2026-08-05 (10,700원; 1.39 G2 다시 확인 2026-11-07: 대한민국 정책브리핑
        // korea.kr/news/policyNewsView.do?newsId=148969730 '내년도 최저임금 1만 700원'), 국민연금법 개정
        // (2027년 1천분의 100), 건강보험정책심의위원회 2026-09-08 (7.19% 동결). [P3-ACC] 2026-11-05: 장기요양보험료율은
        // 2026-10-30 제5차 장기요양위원회 의결로 소득 대비 0.9994% (건강보험료 대비 13.90%).
        Rates(
            effectiveFrom = LocalDate.of(2027, 1, 1),
            version = "2027.1",
            source = "근로기준법 제50·53·56조, 2027년 최저임금 고시(시급 10,700원)",
            minimumHourlyWage = 10_700,
            pensionRate = 0.10, healthRate = 0.0719, longTermCareRate = 0.009994,
        ),
    )

    /** [PAYR-01] 2026.7: 41만~659만 (research/1.21-급여엔진-리뷰.md 출처). */
    val builtInBases: List<PensionBase> = listOf(
        PensionBase(LocalDate.of(2025, 7, 1), 400_000, 6_370_000),
        PensionBase(LocalDate.of(2026, 7, 1), 410_000, 6_590_000),
    )

    /** [REMOTE-02] The `version` of `remote/v1/rates.json` that holds exactly [builtIn]; a download must be newer. */
    const val BUILT_IN_VERSION = 2026110501L

    private class Table(val rates: List<Rates>, val bases: List<PensionBase>, val version: Long)

    @Volatile private var remote: Table? = null

    /** [REMOTE-02] The years in use: [builtIn] with a newer signed rates file laid over it. */
    val current: List<Rates> get() = remote?.rates ?: builtIn
    val pensionBases: List<PensionBase> get() = remote?.bases ?: builtInBases
    val version: Long get() = remote?.version ?: BUILT_IN_VERSION

    /**
     * [REMOTE-02] Lays a downloaded rates file over the built-in years: a year in the file replaces the one
     * starting the same day, a new year is added. Older or equal versions, and null, go back to [builtIn].
     */
    fun use(file: com.bamnat.core.remote.RemoteFile?) {
        remote = file?.takeIf { it.version > BUILT_IN_VERSION }?.let { f ->
            val r = RateFile.read(f)
            Table(
                (builtIn.filter { b -> r.rates.none { it.effectiveFrom == b.effectiveFrom } } + r.rates).sortedBy { it.effectiveFrom },
                (builtInBases.filter { b -> r.bases.none { it.from == b.from } } + r.bases).sortedBy { it.from },
                f.version,
            )
        }
    }

    /** The rates in force on [date]; the earliest table before any table starts. */
    fun on(date: LocalDate, table: List<Rates> = current): Rates =
        table.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }
            ?: table.minBy { it.effectiveFrom }

    /**
     * 2.0.0 [RVJ-04] The value [pick] gives from the latest year at or before [date] that has one, and that year's table:
     * a 1 January without a new 고시 keeps last year's value, and the screen can say so ([staleYear]).
     */
    fun <T : Any> latest(date: LocalDate, table: List<Rates> = current, pick: (Rates) -> T?): Pair<T, Rates>? =
        table.filter { !it.effectiveFrom.isAfter(date) }.sortedByDescending { it.effectiveFrom }
            .firstNotNullOfOrNull { r -> pick(r)?.let { it to r } }

    /**
     * 2.0.0 [RVJ-05] 검토 PAYC-20·U04-05: the year whose rates stand in for [date]'s year when the app has none for it yet
     * (2028-01 with only 2027.1 known → 2027); null when the year is in.
     */
    fun staleYear(date: LocalDate, table: List<Rates> = current): Int? =
        on(date, table).effectiveFrom.year.takeIf { it < date.year }

    /**
     * 2.0.0 [RVJ-05] 국민연금 보험료율 the law already fixes year by year: 국민연금법 제88조 (2025-03-20 개정, 2026-01-01 시행) 9%에서 2026년부터
     * 해마다 0.5%p씩 올려 2033년 13%. Used only for a year the rates have no row for yet.
     */
    val LAW_PENSION: Map<Int, Double> = mapOf(2028 to 0.105, 2029 to 0.11, 2030 to 0.115, 2031 to 0.12, 2032 to 0.125, 2033 to 0.13)

    /**
     * 2.0.0 [RVJ-20] 뿌리 고침 11 '새해 전환 점검': the yearly values that on [date] still come from an earlier year's 고시 or a built-in
     * default, by their rates-file key. Before 1 January the next year's signed `rates.json` should leave none of these but the ones
     * whose 고시 is not out yet; `NewYearSwitchTest` keeps the list for the coming year. 국민연금 follows [LAW_PENSION] where the law
     * fixes the year, so it is not carried then.
     */
    fun carried(date: LocalDate, table: List<Rates> = current): List<String> {
        val y = date.year
        fun <T : Any> from(key: String, pick: (Rates) -> T?): String? =
            key.takeIf { (latest(date, table, pick)?.second?.effectiveFrom?.year ?: 0) < y }
        return listOfNotNull(
            "minimumHourlyWage".takeIf { on(date, table).effectiveFrom.year < y },
            from("pensionRate") { it.pensionRate }?.takeIf { y !in LAW_PENSION },
            from("healthRate") { it.healthRate },
            from("longTermCareRate") { it.longTermCareRate },
            from("healthCapShare") { it.healthCapShare },
            from("healthMinShare") { it.healthMinShare },
            from("mealFreeLimit") { it.mealFreeLimit },
            from("durunuriUnder") { it.durunuriUnder },
            from("accidentPermille") { r -> r.accidentPermille.takeIf { it.isNotEmpty() } },
            from("commutePermille") { it.commutePermille },
            from("wageClaimPermille") { it.wageClaimPermille },
            from("asbestosPermille") { it.asbestosPermille },
        )
    }

    /** 2.0.0 [RVJ-20] 최저임금 시급 on [date] (today by default), for a first value or a check that must not be a year's number in code. */
    fun minimumWage(date: LocalDate = com.bamnat.core.time.Today.date()): Long = on(date).minimumHourlyWage

    /** 2.0.0 [RVJ-20] A rate as people write it: 0.0475 → "4.75%", 0.05 → "5%", 0.009994 → "0.9994%"; [places] after the point at most. */
    fun pct(rate: Double, places: Int = 4): String =
        java.math.BigDecimal.valueOf(rate * 100).setScale(places, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"

    /** [RVJ-05] 국민연금 합계 보험료율 on [date]: the year's row, else the law's schedule for a year not in yet. */
    fun pensionRateOn(date: LocalDate, table: List<Rates> = current): Double? {
        val r = on(date, table)
        if (r.effectiveFrom.year >= date.year) return r.pensionRate
        // 2033년 13%에 닿은 뒤로는 13%를 이어 가요.
        return LAW_PENSION.filterKeys { it <= date.year }.maxByOrNull { it.key }?.value?.coerceAtLeast(r.pensionRate ?: 0.0) ?: r.pensionRate
    }
}

/**
 * [REMOTE-02] `remote/v1/rates.json` data: `rates[]` (`from`, `version`, `source`, `minimumHourlyWage`,
 * `pensionRate`, `healthRate`, `longTermCareRate`, `unemploymentRate`; null = 아직 결정 전; 2.0.0 [RVJ-04] optional `healthCapShare`,
 * `healthMinShare`, `mealFreeLimit`, `durunuriUnder`, `accidentPermille` {code: ‰}, `commutePermille`, `wageClaimPermille`,
 * `asbestosPermille`) and `pensionBases[]`
 * (`from`, `min`, `max`). Premiums and hours are the 근로기준법 ones unless a year gives them.
 */
object RateFile {
    data class Data(val rates: List<Rates>, val bases: List<PensionBase>)

    fun read(f: com.bamnat.core.remote.RemoteFile): Data {
        if (f.kind != com.bamnat.core.remote.RemoteFiles.RATES) throw com.bamnat.core.remote.RemoteFileException("kind ${f.kind}")
        fun date(s: String) = try { LocalDate.parse(s) } catch (e: java.time.format.DateTimeParseException) {
            throw com.bamnat.core.remote.RemoteFileException("date $s")
        }
        fun share(v: Double?, k: String) = v?.also { if (it < 0 || it > 0.3) throw com.bamnat.core.remote.RemoteFileException(k) }
        fun permille(v: Double?, k: String) = v?.also { if (it < 0 || it > 10) throw com.bamnat.core.remote.RemoteFileException(k) }
        fun won(o: com.bamnat.core.remote.JsonObj, k: String, min: Long, max: Long): Long? =
            if (!o.has(k)) null else o.long(k).also { if (it < min || it > max) throw com.bamnat.core.remote.RemoteFileException("$k $it") }
        val d = Rates(LocalDate.MIN, "", "", minimumHourlyWage = 0)
        val rates = f.data.objs("rates").map { o ->
            val wage = o.int("minimumHourlyWage")
            if (wage < 5_000 || wage > 100_000) throw com.bamnat.core.remote.RemoteFileException("minimumHourlyWage $wage")
            Rates(
                effectiveFrom = date(o.str("from")), version = o.str("version"), source = o.strOr("source", ""),
                overtimePremium = o.doubleOrNull("overtimePremium") ?: d.overtimePremium,
                nightPremium = o.doubleOrNull("nightPremium") ?: d.nightPremium,
                holidayPremiumWithin8h = o.doubleOrNull("holidayPremiumWithin8h") ?: d.holidayPremiumWithin8h,
                holidayPremiumOver8h = o.doubleOrNull("holidayPremiumOver8h") ?: d.holidayPremiumOver8h,
                minimumHourlyWage = wage.toLong(),
                pensionRate = share(o.doubleOrNull("pensionRate"), "pensionRate"),
                healthRate = share(o.doubleOrNull("healthRate"), "healthRate"),
                longTermCareRate = share(o.doubleOrNull("longTermCareRate"), "longTermCareRate"),
                unemploymentRate = share(o.doubleOrNull("unemploymentRate"), "unemploymentRate") ?: d.unemploymentRate,
                // [RVJ-04] Optional yearly values; a file without them keeps the built-in ones.
                healthCapShare = won(o, "healthCapShare", 100_000L, 50_000_000L),
                healthMinShare = won(o, "healthMinShare", 1_000L, 100_000L),
                mealFreeLimit = won(o, "mealFreeLimit", 50_000L, 1_000_000L),
                durunuriUnder = won(o, "durunuriUnder", 1_000_000L, 10_000_000L),
                accidentPermille = if (o.has("accidentPermille")) o.obj("accidentPermille").map.keys.associateWith { k ->
                    o.obj("accidentPermille").double(k).also { if (it <= 0 || it > 300) throw com.bamnat.core.remote.RemoteFileException("accidentPermille $k") }
                } else emptyMap(),
                commutePermille = permille(o.doubleOrNull("commutePermille"), "commutePermille"),
                wageClaimPermille = permille(o.doubleOrNull("wageClaimPermille"), "wageClaimPermille"),
                asbestosPermille = permille(o.doubleOrNull("asbestosPermille"), "asbestosPermille"),
            )
        }
        val bases = f.data.objs("pensionBases").map { o ->
            PensionBase(date(o.str("from")), o.long("min"), o.long("max")).also {
                if (it.min <= 0 || it.max <= it.min) throw com.bamnat.core.remote.RemoteFileException("pensionBases")
            }
        }
        return Data(rates, bases)
    }
}
