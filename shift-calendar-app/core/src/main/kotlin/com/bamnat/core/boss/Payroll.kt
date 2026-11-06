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
    /**
     * 1.38 계산 단위 (경쟁 앱 조사 P3-B-12): each day's work is counted up to this many minutes (1, 10, 30, 60). Only up: 근로시간을
     * 깎는 절사는 임금 전액 지급(근로기준법 제43조)에 어긋나서, a 7분 늦은 퇴근 is 10분 at 10, never 0.
     */
    val minuteUnit: Int = 1,
    /** 1.38 [P3-X-01] 대타 요청 on their way (the same yes/no link as the 리더's 결원 메우기; [TeamCall.gap] is the staff id and day). */
    val calls: List<com.bamnat.core.team.TeamCall> = emptyList(),
) {
    fun payDate(month: YearMonth): LocalDate? = payday?.let { month.plusMonths(1).atDay(it.coerceAtMost(month.plusMonths(1).lengthOfMonth())) }
}

enum class WageForm(val label: String) { HOUR("시급"), MONTH("월급"), DAY("일급") }

/** [BOSS-01] 4대보험 enrolment, each on its own (설계 2-2 직원 추가). 산재 has no employee share and is always on by law. */
data class Insurance(val pension: Boolean = false, val health: Boolean = false, val employment: Boolean = false,
                     /** 1.38 [BOSS-08] How income tax comes off the 명세서: 간이세액표, 사업소득 3.3%, or none. */
                     val tax: StaffTax = StaffTax.WAGE) {
    val any: Boolean get() = pension || health || employment
    val label: String get() = listOfNotNull("국민".takeIf { pension }, "건강".takeIf { health }, "고용".takeIf { employment }).joinToString("·").ifEmpty { "가입 안 함" }

    companion object {
        val ALL = Insurance(true, true, true)
    }
}

/** 1.38 [BOSS-08] 소득세 떼는 법. 3.3% is for a 사업소득자 (프리랜서) only; an employee's is the 간이세액표 (소득세법 제134조). */
enum class StaffTax(val label: String) { WAGE("근로소득(간이세액표)"), BIZ("사업소득 3.3%"), NONE("떼지 않음") }

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
    val base: Long = RateTable.on(com.bamnat.core.time.Today.date()).minimumHourlyWage,
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
    /**
     * 1.38 요일별 시급 (P3-B-12): 시급 for some weekdays ("주말 11,000원"), the others [base]. 통상시급 is the week's 소정근로 임금 over its
     * hours (시행령 제6조 제2항 제4호), so 가산·주휴 use that; each day's own hours are paid at its own 시급.
     */
    val dayRates: Map<DayOfWeek, Long> = emptyMap(),
    /** 1.38 휴직: 무급으로 쉬는 기간들. 재직은 이어지고(입사일·근속 그대로), 그 날들은 임금을 주지 않아요. */
    val leaves: List<StaffLeave> = emptyList(),
    /** 1.38 [P3-B-11] The 출퇴근 링크 on the share server and its key, null until the boss makes one. */
    val clockId: String? = null,
    val clockToken: String? = null,
) {
    fun employedOn(d: LocalDate): Boolean = (hiredOn == null || !d.isBefore(hiredOn)) && (leftOn == null || !d.isAfter(leftOn))
    fun onLeave(d: LocalDate): Boolean = leaves.any { it.covers(d) }
    /** Working on [d]: employed and not on 휴직. What the 직원 요금제 counts. */
    fun workingOn(d: LocalDate): Boolean = employedOn(d) && !onLeave(d)
    fun workingIn(month: YearMonth): Boolean = (1..month.lengthOfMonth()).any { workingOn(month.atDay(it)) }
    /**
     * 1.38 직원 요금제 인원 (사용자 결정 2026-11-06 "그달 일하는 사람만 셈"): 그달 전에 퇴사했거나 그달 내내 휴직이면 세지 않아요.
     * 그달 뒤에 들어올 사람은 세요(미리 더해 둔 사람).
     */
    fun countsFor(month: YearMonth): Boolean = leftOn?.isBefore(month.atDay(1)) != true &&
        !(1..month.lengthOfMonth()).all { onLeave(month.atDay(it)) }
}

/** 1.38 휴직 한 번: [start]부터 [end]까지(둘 다 포함), [end] null이면 아직 쉬는 중. */
data class StaffLeave(val start: LocalDate, val end: LocalDate? = null) {
    fun covers(d: LocalDate): Boolean = !d.isBefore(start) && (end == null || !d.isAfter(end))
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
        null -> "5인 이상인지 아직 몰라요. 그때까지는 덜 주지 않게 가산을 넣어 계산했어요. 아래에서 한 번만 골라 주세요"
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
        // 1.38 PC 8차: dated from the hire day, a 월급 joined on 11/20 read 0원 for November (the month asks the value on the 1st).
        base = ValueTimeline(listOf(DatedValue(LocalDate.of(2000, 1, 1), s.base))),
        items = s.allowances.mapIndexed { i, a ->
            PayItem("staff-$i", a.name, ValueTimeline(listOf(DatedValue(LocalDate.of(2000, 1, 1), a.amount))),
                if (a.perDay) Cadence.PER_WORK_DAY else Cadence.MONTHLY)
        },
        hireDate = s.hiredOn,
        // 1.38 퇴사·휴직: the 월급 of a month left or rested part way is cut to the days worked (it was a whole month before).
        leaveDate = s.leftOn,
        unpaidDays = s.leaves.flatMap { l ->
            val to = l.end ?: s.leftOn ?: l.start.plusYears(3)
            generateSequence(l.start) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        }.toSet(),
        fiveOrMoreEmployees = place.fivePlus != false,
        weekStart = place.weekStart,
        legalBreaks = place.legalBreaks,
        rules = CompanyRules(weeklyHolidayPay = s.weeklyHolidayPay),
    )

    /** [Workplace.minuteUnit]: [end] moved later so start → end is a whole number of units (never earlier). */
    fun roundUp(start: LocalTime?, end: LocalTime?, unit: Int): LocalTime? {
        if (start == null || end == null || unit <= 1) return end
        val span = ((end.toSecondOfDay() - start.toSecondOfDay()) / 60).let { if (it <= 0) it + 24 * 60 else it }
        val up = (span + unit - 1) / unit * unit
        return start.plusMinutes(up.toLong())
    }

    fun resolver(place: Workplace, s: StaffMember, work: Collection<StaffWork>): ScheduleResolver {
        val types = DayOfWeek.entries.map { d ->
            val shift = s.weekly[d]
            ShiftType(typeId(d), d.name, d.name.take(1), 0xFF888888, shift?.start, roundUp(shift?.start, shift?.end, place.minuteUnit), isOff = shift == null)
        }
        val start = s.hiredOn ?: LocalDate.of(2000, 1, 1)
        val pattern = WorkPattern.Weekly(start, DayOfWeek.entries.associateWith { typeId(it) }, endDate = s.leftOn)
        val entries = work.filter { it.staffId == s.id }.map { entry(it.copy(end = roundUp(it.start, it.end, place.minuteUnit)), s, place.legalBreaks) }
        return ScheduleResolver(types, listOf(pattern), entries, legalBreaks = place.legalBreaks)
    }

    /**
     * 1.38 [P3-X-01] 검토(정확성): a 대근 on a day off is paid as extra work, which the legal break (제54조) did not reach, so 10~18시
     * 대타 was paid 8시간 while the same 10~18시 in the week was 7.5시간. The place's break now applies to it the same way.
     */
    private fun extra(w: StaffWork, breaks: Boolean): ExtraEntry {
        val span = if (w.start != null && w.end != null) com.bamnat.core.work.StaffShift(w.start, w.end).spanMinutes else 0
        return ExtraEntry(ExtraKind.COVER_ID, w.start, w.end, breakMinutes = if (breaks) com.bamnat.core.schedule.LegalBreak.minutesFor(span) else 0)
    }

    private fun entry(w: StaffWork, s: StaffMember, breaks: Boolean): DayEntry = when (w.kind) {
        WorkKind.NORMAL -> if (s.weekly[w.date.dayOfWeek] != null) DayEntry(w.date, customStart = w.start, customEnd = w.end)
            else DayEntry(w.date, extras = listOf(extra(w, breaks)))
        WorkKind.LATE -> DayEntry(w.date, customStart = w.start, customEnd = w.end, attendance = Attendance.LATE)
        WorkKind.EARLY -> DayEntry(w.date, customStart = w.start, customEnd = w.end, attendance = Attendance.EARLY)
        WorkKind.ABSENT -> DayEntry(w.date, attendance = Attendance.ABSENT)
        WorkKind.COVER -> DayEntry(w.date, extras = listOf(extra(w, breaks)))
        WorkKind.LEAVE -> DayEntry(w.date, leave = Leave())
        WorkKind.OFF -> DayEntry(w.date, override = com.bamnat.core.model.ShiftOverride.Off)
    }

    /** [StaffMember.dayRates]: one weekday's 시급. */
    fun rateOn(s: StaffMember, d: DayOfWeek): Long = s.dayRates[d] ?: s.base

    /**
     * 통상시급 with [StaffMember.dayRates]: the usual week's pay over its hours (시행령 제6조 제2항 제4호), to the won. With no usual
     * week, the plain average of the rates set and [StaffMember.base] for the others.
     */
    fun ordinaryHourly(place: Workplace, s: StaffMember): Long {
        if (s.form != WageForm.HOUR || s.dayRates.isEmpty()) return s.base
        val mins = s.weekly.mapValues { (_, sh) -> sh.paidMinutes(place.legalBreaks) }.filterValues { it > 0 }
        if (mins.isEmpty()) return DayOfWeek.entries.map { rateOn(s, it) }.average().toLong()
        val pay = mins.entries.sumOf { (d, m) -> java.math.BigDecimal.valueOf(rateOn(s, d)).multiply(java.math.BigDecimal.valueOf(m.toLong())) }
        return pay.divide(java.math.BigDecimal.valueOf(mins.values.sum().toLong()), 0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun line(place: Workplace, s: StaffMember, month: YearMonth, work: Collection<StaffWork>, calculator: PayCalculator = PayCalculator()): LedgerLine {
        val employed = (1..month.lengthOfMonth()).map(month::atDay).any(s::employedOn)
        val r = resolver(place, s, work)
        // 요일별 시급: the engine works at the 통상시급, then each day's hours get the difference to that day's own 시급.
        val ordinary = ordinaryHourly(place, s)
        val calcAs = if (ordinary != s.base) s.copy(base = ordinary) else s
        val pay = if (employed) calculator.calculate(profile(place, calcAs), month, r::resolve).let { p ->
            if (s.dayRates.isEmpty() || s.form != WageForm.HOUR) p else {
                val diff = p.minutes.filter { s.workingOn(it.date) }.sumOf { dm ->
                    (rateOn(s, dm.date.dayOfWeek) - ordinary) * dm.total.worked / 60.0
                }.let { Math.round(it) }
                if (diff == 0L) p else p.copy(lines = p.lines + com.bamnat.core.pay.PayLine(LineKind.ALLOWANCE, "요일별 시급 차액", diff,
                    "요일마다 정한 시급 − 통상시급 ${"%,d".format(ordinary)}원, 그날 일한 시간만큼",
                    basis = "통상시급 = 주 소정근로 임금 ÷ 주 소정근로시간 (근로기준법 시행령 제6조 제2항 제4호)"))
            }
        } else null
        val days = (1..month.lengthOfMonth()).map { r.resolve(month.atDay(it)) }.filter { s.workingOn(it.date) }
        val worked = pay?.minutes.orEmpty()
        val workDays = days.count { it.isWorkDay || it.extras.isNotEmpty() }
        // 1.38 PC 1차: hours worked on a 휴일 are hours worked too (10월 22일 근무가 160시간으로 보이고 지난달 비교가 거꾸로 말하던 것).
        val workMinutes = pay?.lines?.filter { it.kind == LineKind.BASE || it.kind == LineKind.EXTRA_HOURS || it.kind == LineKind.HOLIDAY }?.sumOf { it.minutes }
            ?.takeIf { it > 0 } ?: days.sumOf { it.scheduledMinutes + it.extras.sumOf { e -> e.minutes } }
        fun minutesOf(kind: LineKind) = pay?.lines?.filter { it.kind == kind }?.sumOf { it.minutes } ?: 0
        val gross = pay?.total ?: 0
        // [BOSS-11] Only the 4대보험 the law puts them in (G2·G3·G6), not just what was ticked.
        val eligible = InsuranceRules.of(place, s, month).effective
        val insurance = if (eligible.any) employerInsurance(gross, eligible, month.atDay(1)) else 0
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
        // [P3-ACC] The same exact won × rate as [LaborCost.employerInsurance], so a box taken off takes off exactly what it added.
        val cut = com.bamnat.core.pay.WonCut
        val health = cut.cut10(pay, sh.health)
        val care = cut.cut10(health, sh.careRatio)
        val pension = cut.cut10((pay / 1_000 * 1_000).coerceIn(sh.baseMin, sh.baseMax), sh.pension)
        val employment = cut.cut10(pay, cut.exact(sh.employment).add(cut.exact(LaborCost.STABILITY)).toDouble())
        return all - (if (ins.pension) 0 else pension) - (if (ins.health) 0 else health + care) - (if (ins.employment) 0 else employment)
    }

    /** [BOSS-05] 최저임금 미달, 주휴가 빠진 주, 결근, 아직 모르는 5인 여부, the engine's own 확인 필요. */
    fun warnings(place: Workplace, l: LedgerLine, noWork: Boolean): List<LedgerWarning> = buildList {
        val s = l.staff
        val minimum = RateTable.on(l.month.atDay(1)).minimumHourlyWage
        val hourly = when (s.form) {
            // 요일별 시급: the lowest day is the one the 최저임금 has to hold for.
            WageForm.HOUR -> (s.dayRates.values + s.base).min()
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
        // 1.38 [BOSS-10] 주 52시간 (근로기준법 제53조): a week past it is told, for a 5인 이상 place the law caps.
        if (place.fivePlus != false) l.pay?.holidayWeeks.orEmpty().filter { it.workedMinutes > 52 * 60 }.takeIf { it.isNotEmpty() }?.let { w ->
            add(LedgerWarning("week52", "주 52시간을 넘은 주가 있어요", "${w.size}주가 한 주 52시간(근로기준법 제53조)을 넘었어요. 근무를 나눠 주세요."))
        }
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
