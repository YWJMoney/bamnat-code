package com.bamnat.core.boss

import com.bamnat.core.model.Attendance
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RateTable
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.work.LaborCost
import com.bamnat.core.work.Staff
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [BOSS-01][P3-03] 1.38 Phase 3 M25 B1 (설계 2-2, 순번 499): 사업장. Kept on the boss's phone and in the 암호 백업 only (설계 1-3);
 * nothing of it goes to a server. [fivePlus] is asked once (null until it is answered): under 5 people the law's
 * 연장·야간·휴일 가산 do not apply (근로기준법 제11조, 시행령 제7조 별표 1), and the ledger says so above itself.
 */
data class Workplace(
    val id: String,
    val name: String,
    val fivePlus: Boolean? = null,
    /** Day of the month the pay goes out (1..31, the month's last day when it is shorter); null when not set. */
    val payday: Int? = null,
    /** Take the legal break (제54조) off a shift with none set, as SHOP-11 did. */
    val legalBreaks: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** [BOSS-02] A 리더 팀 근무표 this place takes its plan from ([com.bamnat.core.team.Team.id]); null for the weekly hours. */
    val teamId: String? = null,
) {
    fun payDate(month: YearMonth): LocalDate? = payday?.let { month.plusMonths(1).atDay(it.coerceAtMost(month.plusMonths(1).lengthOfMonth())) }
}

enum class WageForm(val label: String) { HOUR("시급"), MONTH("월급"), DAY("일급") }

/** [BOSS-01] 4대보험 enrolment, each on its own (설계 2-2 직원 추가). 산재 has no employee share and is always on by law. */
data class Insurance(val pension: Boolean = false, val health: Boolean = false, val employment: Boolean = false) {
    val any: Boolean get() = pension || health || employment
    val label: String get() = listOfNotNull("국민".takeIf { pension }, "건강".takeIf { health }, "고용".takeIf { employment }).joinToString("·").ifEmpty { "가입 안 함" }

    companion object {
        val ALL = Insurance(true, true, true)
    }
}

/** [BOSS-01] A fixed allowance paid every month (식대·교통비 같은), or every working day. */
data class StaffAllowance(val name: String, val amount: Long, val perDay: Boolean = false)

/**
 * [BOSS-01] 직원. The name and (결정 P3-D8: optional) birth date are what a 임금대장·임금명세서 needs to tell people apart (시행령
 * 제27조 제1항 제2호, 제27조의2 제1호). 주민등록번호 is never asked (개인정보 보호법 제24조의2). [weekly] is the usual week, as
 * SHOP-11 kept it; the days that went differently are [StaffWork] rows.
 */
data class StaffMember(
    val id: String,
    val name: String,
    val form: WageForm = WageForm.HOUR,
    /** 시급, 월급 or 일급 in won. */
    val base: Long = RateTable.on(LocalDate.now()).minimumHourlyWage,
    val allowances: List<StaffAllowance> = emptyList(),
    val hiredOn: LocalDate? = null,
    val leftOn: LocalDate? = null,
    val birth: LocalDate? = null,
    /** 종사하는 업무 (시행령 제27조 제1항 제4호), free words: "홀", "주방". */
    val job: String = "",
    val insurance: Insurance = Insurance(),
    val weeklyHolidayPay: Boolean = true,
    val weekly: Map<DayOfWeek, StaffShift> = emptyMap(),
    /** [BOSS-02] The same person on the 팀 근무표 ([com.bamnat.core.team.TeamMember.id]), so a day is written once (설계 2-2 139). */
    val memberId: String? = null,
) {
    fun employedOn(d: LocalDate): Boolean = (hiredOn == null || !d.isBefore(hiredOn)) && (leftOn == null || !d.isAfter(leftOn))
}

/** [BOSS-03] How a day went against the usual week (설계 1-3 `staff_work.kind`): only the days that differ are written (139). */
enum class WorkKind(val label: String) {
    /** Other hours than usual, or work on a day that has none. */
    NORMAL("시간 바꿈"),
    LATE("지각"),
    EARLY("조퇴"),
    ABSENT("결근"),
    /** Covered someone's shift on a day of their own off. */
    COVER("대근"),
    /** 유급 연차: paid as worked. */
    LEAVE("연차"),
    /** Not working this day though the week says so (가게 쉬는 날), unpaid and not a 결근. */
    OFF("쉬는 날"),
}

data class StaffWork(val staffId: String, val date: LocalDate, val kind: WorkKind, val start: LocalTime? = null, val end: LocalTime? = null) {
    init {
        require(kind !in TIMED || (start != null && end != null)) { "${kind.label} needs its times" }
    }

    companion object {
        val TIMED = setOf(WorkKind.NORMAL, WorkKind.LATE, WorkKind.EARLY, WorkKind.COVER)
    }
}

/**
 * [BOSS-04] One person's month in the 임금대장: what [PayCalculator] gives (each line with its formula, the 계산 방법 of
 * 시행령 제27조의2 제5호), the hours the ledger lists (제27조 제1항 제6~8호), and what the boss pays on top (사업주 4대보험,
 * [LaborCost.employerInsurance]). 공제 (제10호) is the next 판's 공제 미리보기 (B2, 순번 504).
 */
data class LedgerLine(
    val staff: StaffMember,
    val month: YearMonth,
    val pay: MonthPay?,
    val workDays: Int,
    val workMinutes: Int,
    val overtimeMinutes: Int,
    val nightMinutes: Int,
    val holidayMinutes: Int,
    val employerInsurance: Long,
    val warnings: List<LedgerWarning>,
) {
    val lines: List<PayLine> get() = pay?.lines.orEmpty().filter { it.amount != 0L }
    val gross: Long get() = lines.sumOf { it.amount }
    val employerTotal: Long get() = gross + employerInsurance
    /** Pay lines grouped by what the ledger calls them: 기본급, 주휴수당, 연장·야간·휴일, 수당. */
    fun amountOf(kind: LineKind): Long = lines.filter { it.kind == kind }.sumOf { it.amount }
}

/** [BOSS-05] A sign on a ledger line, with the words a tap shows (설계 2-2 "⚠ 주휴 빠짐? 누르면 설명"). */
data class LedgerWarning(val key: String, val title: String, val text: String)

data class Ledger(val workplace: Workplace, val month: YearMonth, val lines: List<LedgerLine>) {
    val gross: Long get() = lines.sumOf { it.gross }
    val employerInsurance: Long get() = lines.sumOf { it.employerInsurance }
    val total: Long get() = gross + employerInsurance
    /** The line above the ledger for a place under 5 people, or one not answered yet. */
    val note: String? get() = when (workplace.fivePlus) {
        false -> "5인 미만 사업장: 연장·야간·휴일 가산 없이 계산했어요(근로기준법 제11조)"
        null -> "5인 이상인지 아직 몰라요. 5인 이상으로 보고 가산을 넣었어요. 사업장 설정에서 한 번만 골라 주세요"
        true -> null
    }
}

/**
 * [BOSS-04][P3-03] 월 급여 대장 (순번 501): each staff member's month worked out by the same engine as the user's own pay, from
 * their usual week and the days that went differently. A staff member is a [PayProfile] of their own (시급·월급·일급, 5인 이상,
 * 주휴, legal break), so 연장·야간·주휴 follow every rule the pay screen already tests. 참고용 계산이에요 (설계 2-2 고정 문구).
 */
object PayrollLedger {
    const val NOTICE = "참고용 계산이에요. 최종 지급액은 사장님이 확인해요. 신고·4대보험 취득은 세무사·공단에서 해요."

    /** The fixed shift type of [day] for a staff member: id 1..7 by the weekday, so the week is a plain weekly pattern. */
    private fun typeId(day: DayOfWeek): Long = day.value.toLong()

    fun profile(place: Workplace, s: StaffMember): PayProfile = PayProfile(
        occupation = Occupation.PART_TIME,
        wageType = when (s.form) { WageForm.HOUR -> WageType.HOURLY; WageForm.MONTH -> WageType.MONTHLY; WageForm.DAY -> WageType.DAILY },
        base = ValueTimeline(listOf(DatedValue(s.hiredOn ?: LocalDate.of(2000, 1, 1), s.base))),
        items = s.allowances.mapIndexed { i, a ->
            PayItem("staff-$i", a.name, ValueTimeline(listOf(DatedValue(LocalDate.of(2000, 1, 1), a.amount))),
                if (a.perDay) Cadence.PER_WORK_DAY else Cadence.MONTHLY)
        },
        hireDate = s.hiredOn,
        fiveOrMoreEmployees = place.fivePlus != false,
        weekStart = place.weekStart,
        legalBreaks = place.legalBreaks,
        rules = CompanyRules(weeklyHolidayPay = s.weeklyHolidayPay),
    )

    fun resolver(place: Workplace, s: StaffMember, work: Collection<StaffWork>): ScheduleResolver {
        val types = DayOfWeek.entries.map { d ->
            val shift = s.weekly[d]
            ShiftType(typeId(d), d.name, d.name.take(1), 0xFF888888, shift?.start, shift?.end, isOff = shift == null)
        }
        val start = s.hiredOn ?: LocalDate.of(2000, 1, 1)
        val pattern = WorkPattern.Weekly(start, DayOfWeek.entries.associateWith { typeId(it) }, endDate = s.leftOn)
        val entries = work.filter { it.staffId == s.id }.map { entry(it, s) }
        return ScheduleResolver(types, listOf(pattern), entries, legalBreaks = place.legalBreaks)
    }

    private fun entry(w: StaffWork, s: StaffMember): DayEntry = when (w.kind) {
        WorkKind.NORMAL -> if (s.weekly[w.date.dayOfWeek] != null) DayEntry(w.date, customStart = w.start, customEnd = w.end)
            else DayEntry(w.date, extras = listOf(ExtraEntry(ExtraKind.COVER_ID, w.start, w.end)))
        WorkKind.LATE -> DayEntry(w.date, customStart = w.start, customEnd = w.end, attendance = Attendance.LATE)
        WorkKind.EARLY -> DayEntry(w.date, customStart = w.start, customEnd = w.end, attendance = Attendance.EARLY)
        WorkKind.ABSENT -> DayEntry(w.date, attendance = Attendance.ABSENT)
        WorkKind.COVER -> DayEntry(w.date, extras = listOf(ExtraEntry(ExtraKind.COVER_ID, w.start, w.end)))
        WorkKind.LEAVE -> DayEntry(w.date, leave = Leave())
        WorkKind.OFF -> DayEntry(w.date, override = com.bamnat.core.model.ShiftOverride.Off)
    }

    fun line(place: Workplace, s: StaffMember, month: YearMonth, work: Collection<StaffWork>, calculator: PayCalculator = PayCalculator()): LedgerLine {
        val employed = (1..month.lengthOfMonth()).map(month::atDay).any(s::employedOn)
        val r = resolver(place, s, work)
        val pay = if (employed) calculator.calculate(profile(place, s), month, r::resolve) else null
        val days = (1..month.lengthOfMonth()).map { r.resolve(month.atDay(it)) }.filter { s.employedOn(it.date) }
        val worked = pay?.minutes.orEmpty()
        val workDays = days.count { it.isWorkDay || it.extras.isNotEmpty() }
        val workMinutes = pay?.lines?.filter { it.kind == LineKind.BASE || it.kind == LineKind.EXTRA_HOURS }?.sumOf { it.minutes }
            ?.takeIf { it > 0 } ?: days.sumOf { it.scheduledMinutes + it.extras.sumOf { e -> e.minutes } }
        fun minutesOf(kind: LineKind) = pay?.lines?.filter { it.kind == kind }?.sumOf { it.minutes } ?: 0
        val gross = pay?.total ?: 0
        val insurance = if (s.insurance.any) employerInsurance(gross, s.insurance, month.atDay(1)) else 0
        val line = LedgerLine(
            staff = s, month = month, pay = pay,
            workDays = workDays, workMinutes = workMinutes,
            overtimeMinutes = minutesOf(LineKind.OVERTIME), nightMinutes = minutesOf(LineKind.NIGHT), holidayMinutes = minutesOf(LineKind.HOLIDAY),
            employerInsurance = insurance, warnings = emptyList(),
        )
        return line.copy(warnings = warnings(place, line, worked.isEmpty()))
    }

    fun month(place: Workplace, staff: List<StaffMember>, month: YearMonth, work: Collection<StaffWork>): Ledger {
        val calc = PayCalculator()
        return Ledger(place, month, staff.filter { s -> (1..month.lengthOfMonth()).any { s.employedOn(month.atDay(it)) } }
            .map { line(place, it, month, work, calc) })
    }

    /**
     * The employer's 4대보험 for the boxes that are ticked: [LaborCost.employerInsurance] works the four out together, so each
     * one not ticked is taken back off by the same rates (국민연금·건강·장기요양·고용, 2026 or the year's half).
     */
    fun employerInsurance(pay: Long, ins: Insurance, on: LocalDate): Long {
        if (!ins.any) return 0
        val all = LaborCost.employerInsurance(pay, on)
        val sh = com.bamnat.core.pay.TakeHome.sharesOn(on)
        val health = (pay * sh.health).toLong() / 10 * 10
        val care = (health * sh.careOfHealth).toLong() / 10 * 10
        val pension = ((pay / 1_000 * 1_000).coerceIn(sh.baseMin, sh.baseMax) * sh.pension).toLong() / 10 * 10
        val employment = (pay * (sh.employment + LaborCost.STABILITY)).toLong() / 10 * 10
        return all - (if (ins.pension) 0 else pension) - (if (ins.health) 0 else health + care) - (if (ins.employment) 0 else employment)
    }

    /** [BOSS-05] 최저임금 미달, 주휴가 빠진 주, 결근, 아직 모르는 5인 여부, the engine's own 확인 필요. */
    fun warnings(place: Workplace, l: LedgerLine, noWork: Boolean): List<LedgerWarning> = buildList {
        val s = l.staff
        val minimum = RateTable.on(l.month.atDay(1)).minimumHourlyWage
        val hourly = when (s.form) {
            WageForm.HOUR -> s.base
            WageForm.DAY -> s.base / 8
            // 월급 ÷ 209시간 (주 40시간, 주휴 포함), 최저임금법 시행령 제5조 제1항 제3호.
            WageForm.MONTH -> s.base / 209
        }
        if (hourly in 1 until minimum) add(LedgerWarning("min", "최저임금보다 적어요",
            "시간당 ${"%,d".format(hourly)}원으로 ${l.month.year}년 최저임금 ${"%,d".format(minimum)}원보다 적어요(최저임금법 제6조). 수습 3개월 감액은 1년 이상 계약·단순 노무가 아닐 때만이에요."))
        val missed = l.pay?.holidayWeeks.orEmpty().filter { !it.earned && it.absent }
        if (s.weeklyHolidayPay && s.form == WageForm.HOUR && missed.isNotEmpty()) add(LedgerWarning("holiday", "주휴가 빠진 주가 있어요",
            "결근이 있는 주 ${missed.size}번은 주휴수당이 없어요(시행령 제30조: 소정근로일 개근). 결근이 아니라 쉬기로 한 날이면 ‘쉬는 날’로 바꿔 주세요."))
        if (!s.weeklyHolidayPay && s.form == WageForm.HOUR && l.pay?.holidayWeeks.orEmpty().any { it.workedMinutes >= 15 * 60 }) add(LedgerWarning("holiday-off", "주휴수당을 끈 직원이에요",
            "주 15시간 이상 일한 주가 있어요. 그 주는 주휴수당을 줘야 해요(근로기준법 제55조, 시행령 제30조)."))
        if (noWork) add(LedgerWarning("empty", "근무가 없어요", "이번 달 일한 날이 없어요. 평소 근무 요일·시간을 넣거나 근무를 고쳐 주세요."))
        l.pay?.warnings.orEmpty().take(2).forEachIndexed { i, w -> add(LedgerWarning("engine-$i", "확인 필요", w)) }
    }

    /** [BOSS-06] SHOP-11 직원 인건비 → 사업장·직원, once (설계 1-3). The old roster stays for a 판 so it can be taken back. */
    fun fromRoster(roster: StaffRoster, placeId: String, name: String = "우리 가게"): Pair<Workplace, List<StaffMember>> {
        val place = Workplace(placeId, name, legalBreaks = roster.legalBreaks)
        return place to roster.staff.map { it.toMember() }
    }

    fun Staff.toMember(): StaffMember = StaffMember(
        id = id, name = name, form = WageForm.HOUR, base = hourlyWage, weeklyHolidayPay = holidayPay,
        insurance = if (insured) Insurance.ALL else Insurance(), weekly = shifts,
    )
}
