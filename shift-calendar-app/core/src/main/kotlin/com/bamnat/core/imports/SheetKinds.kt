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

    /**
     * [READ-40] The picked [row] in the new kinds. A cell is read again from what the photo said there ([RosterRow.raw]); a
     * cell with no word of its own (put together from several readings, filled from the repeat) keeps its part (주간 stays
     * 주간) by [oldRole] → [newId]. A day off with no word could be 비번 or 휴무: the 조 repeat says which, else 휴무, in yellow.
     * 1.38 PC 10차: reading again from the words alone left most days empty (8일부터 모두 쉬는 날).
     */
    fun remap(row: RosterRow, before: DutyCodes, after: DutyCodes, oldRole: (Long) -> ShiftRole?, newId: (ShiftRole) -> Long?): RosterRow {
        val cells = sortedMapOf<Int, DutyCell>()
        val offs = HashSet<Int>()
        val guessed = HashSet<Int>()
        for ((d, cell) in row.cells) {
            val word = row.raw[d]
            val again = word?.let { after.read(it)?.first }
            if (again != null) {
                cells[d] = again
                // Yellow only for what is still a guess in the new kinds (the old 주 → 데이 guess is the sheet's own word now).
                if (d in row.guessed && !(before.guessedWork(word, cell) && !after.guessedWork(word, again))) guessed += d
                continue
            }
            when (cell) {
                is DutyCell.Shift -> {
                    val role = oldRole(cell.shiftTypeId)
                    if (role == null || role.isOff) offs += d
                    else newId(role)?.let { cells[d] = DutyCell.Shift(it); if (d in row.guessed) guessed += d } ?: run { offs += d }
                }
                DutyCell.Off -> offs += d
                else -> { cells[d] = cell; if (d in row.guessed) guessed += d }
            }
        }
        // An off day with no word of its own (a day the vote or the period filled) takes the off kind its place in the old
        // line's repeat says (주주야야비휴: the 비 or the 휴 of a day read on the same step), yellow; else 휴무, yellow.
        val lastDay = row.cells.keys.maxOrNull() ?: 0
        val period = RosterPeriod.of(row.cells.filter { (d, c) -> c !is DutyCell.Unknown && d !in row.unsure }, lastDay)
        if (period != null) {
            val offIds = ShiftRole.entries.filter { it.isOff }.mapNotNull(newId).toSet()
            val fromWord = cells.filterKeys { row.raw[it] != null }
            for (d in offs.toList()) {
                val e = fromWord.entries.firstOrNull { (e, c) -> (e - d) % period.length == 0 && c is DutyCell.Shift && c.shiftTypeId in offIds }
                if (e != null) { cells[d] = e.value; guessed += d; offs -= d }
            }
        }
        val filled = RosterPeriod.fill(listOf(row.copy(cells = cells, guessed = guessed))).first()
        val rest = offs.filter { it !in filled.cells }
        val off = newId(ShiftRole.OFF)?.let { DutyCell.Shift(it) } ?: DutyCell.Off
        return filled.copy(cells = (filled.cells + rest.associateWith { off }).toSortedMap(), guessed = filled.guessed + rest)
    }
}
