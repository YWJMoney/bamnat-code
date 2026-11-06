package com.bamnat.core.imports

import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.BundleShift
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.schedule.ShiftRole

/**
 * [READ-36] 1.38 PC 8차 (사용성): a 조 roster of 주·야·비·휴 read into the setup's 데이·나이트·오프 came out as 데이 07~15 and one
 * 오프 for both 비번 and 휴무. When the sheet's own letters are a Korean 교대 set the setup's kinds do not use, these are the
 * kinds named by them (주간 주, 야간 야, 오후 오, 당번 당, 비번 비, 휴무 휴) with their usual times, offered once, so the month goes
 * in as the sheet says it and 비번 stays apart from 휴무.
 */
object SheetKinds {
    private val LETTERS = mapOf(
        "주" to ShiftRole.DAY, "주간" to ShiftRole.DAY, "야" to ShiftRole.NIGHT, "야간" to ShiftRole.NIGHT,
        "오" to ShiftRole.EVENING, "오후" to ShiftRole.EVENING, "당" to ShiftRole.DUTY, "당번" to ShiftRole.DUTY, "당직" to ShiftRole.DUTY,
        "비" to ShiftRole.AFTER_DUTY, "비번" to ShiftRole.AFTER_DUTY, "휴" to ShiftRole.OFF, "휴무" to ShiftRole.OFF,
    )
    private val ORDER = listOf(ShiftRole.DAY, ShiftRole.EVENING, ShiftRole.NIGHT, ShiftRole.DUTY, ShiftRole.AFTER_DUTY, ShiftRole.OFF)

    /** The kinds the sheet's [raws] (each cell as read) name, or null when they are not such a set or the setup has them already. */
    fun of(raws: Collection<String>, types: List<ShiftType>): List<BundleShift>? {
        val words = raws.map { DutyCodes.norm(it) }.filter { it.isNotEmpty() }
        if (words.size < 8) return null
        val roles = words.mapNotNull { w -> LETTERS[DutyCodes.LOOKALIKE[w] ?: w] }
        // Most of the sheet is these letters (a few 연차, 교육 aside), each said at least twice.
        if (roles.size * 10 < words.size * 8) return null
        val kinds = roles.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        if (kinds.size < 2 || kinds.none { !it.isOff }) return null
        // The setup already says them: nothing to change.
        val mine = types.flatMap { listOf(DutyCodes.norm(it.label), DutyCodes.norm(it.name)) }.toSet()
        if (words.filter { LETTERS.containsKey(DutyCodes.LOOKALIKE[it] ?: it) }.map { DutyCodes.LOOKALIKE[it] ?: it }.toSet().all { it in mine }) return null
        val bundle = when {
            ShiftRole.EVENING in kinds -> ShiftBundles.THREE
            ShiftRole.DUTY in kinds -> ShiftBundles.DUTY
            else -> ShiftBundles.TWO
        }
        val pool = (ShiftBundles.byKey(bundle)?.shifts.orEmpty() + ShiftBundles.all.flatMap { it.shifts })
        return ORDER.filter { it in kinds }.mapNotNull { r -> pool.firstOrNull { it.role == r } }
    }
}
