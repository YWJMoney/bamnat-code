package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftType
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayPresets
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WorkForm
import java.time.LocalDate

/**
 * [SPAT-03] A public row and the order the user ended with, as the shifts and the order a company settings file holds, so
 * it goes through the same apply as a coworker's file (CompanyShare in the app): work shifts by name, 휴무 (and 비번 after a
 * 당번) as off kinds that show on the calendar, and the order by those names. Weekly forms give Monday..Sunday instead.
 */
object PublicSetup {
    data class Plan(val types: List<ShiftType>, val order: List<String?>, val weekly: List<String?>)

    const val OFF = "휴무"
    const val AFTER = "비번"

    fun plan(cycle: PublicCycle, codes: String): Plan {
        val work = cycle.shiftsFor(codes).map { it.toShiftType() }
        val byCode = cycle.shiftsFor(codes).associate { it.code to it.name }
        val off = ShiftType(0, OFF, "휴", 0xFF8E9AAF, null, null, isOff = true)
        val after = ShiftType(0, AFTER, "비", 0xFFB59F84, null, null, isOff = true).takeIf { 'B' in codes }
        val names = codes.map { c ->
            when (c) {
                'O' -> OFF
                'B' -> AFTER
                else -> byCode[c]
            }
        }
        val weekly = cycle.weekly || (cycle.formKey in CycleOrders.WEEKLY_FORMS && codes.length == 7)
        return Plan(work + listOfNotNull(off, after), if (weekly) emptyList() else names, if (weekly) names else emptyList())
    }

    /**
     * [SPAT-05] The note that goes with the report when the setup started from a public row: '공개 자료 확인·고침', which row,
     * and the order the user chose when it is not the row's own. Words only: no link, no amount, nothing about the user, so
     * the server's own checks (no 5-digit numbers, mails or phones) let it through.
     */
    fun note(cycle: PublicCycle?, codes: String, picked: Boolean): String = buildString {
        if (cycle == null || cycle.id.isEmpty()) {
            append("처음 제공: ")
            append(cycle?.formName ?: "근무주기")
            if (codes.isNotEmpty()) append(" · 순서 ").append(CycleOrders.spoken(codes))
            return@buildString
        }
        // [J133-84] 1.33 광장 급여 써 보기: the row's own number (c143) meant nothing to the user reading the note; the
        // company the report names finds the row.
        append("공개 자료 확인·고침: ").append(cycle.formName)
        if (codes.isNotEmpty()) append(" · 순서 ").append(CycleOrders.spoken(codes))
        when {
            codes.isEmpty() -> Unit
            !cycle.hasOrder -> append(" (공개 순서 없음, 직접 골랐어요)")
            codes != cycle.codes -> append(" (공개 자료와 달라서 고쳤어요)")
            picked || cycle.orderState == OrderState.USUAL -> append(" (공개 자료 순서가 맞아요)")
        }
        if (cycle.asOf.isNotEmpty()) append(" · 자료 시점 ").append(cycle.asOf)
    }.take(300)
}

/**
 * [SPAT-04] After a public row is on the calendar, the pay setup starts from the way such work is usually paid: the
 * 근무 form (V 간편 급여, [WorkForm]) and the job's usual allowances from the presets file, their amounts the presets'
 * placeholders marked 확인 필요 ([Unknowns.item]) until the user or a payslip says (D70: nothing the user set is cleared).
 */
object PublicPay {
    /** The job of an 업종; null leaves the form's own. */
    fun occupation(cycle: PublicCycle): Occupation? {
        val i = cycle.industry
        return when {
            "경찰" in i -> Occupation.POLICE
            "소방" in i -> Occupation.FIRE
            i in setOf("교정", "지자체", "기상") -> Occupation.PUBLIC
            "요양" in i -> Occupation.CARE
            "병원" in i -> Occupation.NURSE
            i in setOf("보안", "경비", "시설관리", "공항 보안") || cycle.formKey in setOf("격일제", "3조1교대") -> Occupation.SECURITY
            cycle.formKey == "고정" -> null
            else -> Occupation.MANUFACTURING
        }
    }

    /** The 간편 급여 form a public row points to. */
    fun form(cycle: PublicCycle, codes: String = cycle.codes): WorkForm {
        val occ = occupation(cycle)
        return when {
            occ == Occupation.POLICE || occ == Occupation.FIRE || occ == Occupation.PUBLIC -> WorkForm.PUBLIC
            occ == Occupation.NURSE -> WorkForm.NURSE
            cycle.formKey == "4조2교대" -> WorkForm.FOUR_TEAM
            cycle.formKey in setOf("격일제", "3조1교대") || 'X' in codes -> WorkForm.DAY_ON_DAY_OFF
            cycle.formKey == "고정" -> WorkForm.OFFICE
            cycle.formKey in PublicCycle.THREE_FORMS || 'E' in codes -> WorkForm.THREE_SHIFT
            else -> WorkForm.TWO_SHIFT
        }
    }

    /**
     * [profile] with the job of [cycle]'s 업종 (only over the form's own default) and that job's usual allowances it does not
     * have yet, from [since]; each added amount is a placeholder marked 확인 필요.
     */
    fun prefill(profile: PayProfile, cycle: PublicCycle, since: LocalDate): PayProfile {
        val occ = occupation(cycle) ?: return profile
        val form = form(cycle)
        val job = if (profile.occupation == Occupation.CUSTOM || profile.occupation == form.occupation) occ else profile.occupation
        val have = profile.items.map { it.id }.toSet()
        val added = PayPresets.of(job).items.filter { it.checked && it.id !in have }.map {
            PayItem(it.id, it.name, ValueTimeline(listOf(DatedValue(since, it.suggestedAmount))), it.cadence, ordinary = it.ordinary)
        }
        return profile.copy(
            occupation = job,
            items = profile.items + added,
            estimatedFields = profile.estimatedFields + added.map { Unknowns.item(it.id) },
        )
    }

    /** The allowances [prefill] adds for [cycle], by name, for the line that says so. */
    fun usualItems(cycle: PublicCycle): List<String> =
        occupation(cycle)?.let { PayPresets.of(it).items.filter { i -> i.checked }.map { i -> i.name } }.orEmpty()
}
