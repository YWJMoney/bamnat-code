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
     * [READ-40] The picked [row] in the new kinds. A work day keeps its part (주간 stays 주간) by [oldRole] → [newId]; a cell the
     * old kinds could not read is read again from what the photo said there ([RosterRow.raw]). A day off is 비번 right after a
     * 야간·당번, else 휴무, yellow where the day's own word does not say the same.
     * 1.38 PC 10차: reading again from the words alone left most days empty (8일부터 모두 쉬는 날). PC 11차: a word can sit a day
     * off (the anchor reading's date line lost 2·3·6·8·9일), which put 비 on a 야 day and 휴 on 비 days.
     */
    fun remap(row: RosterRow, before: DutyCodes, after: DutyCodes, oldRole: (Long) -> ShiftRole?, newId: (ShiftRole) -> Long?): RosterRow {
        val roleOf = ShiftRole.entries.mapNotNull { r -> newId(r)?.let { it to r } }.toMap()
        val cells = sortedMapOf<Int, DutyCell>()
        val offs = HashSet<Int>()
        val guessed = HashSet<Int>()
        // What a day's own word says, read in the new kinds (비 or 휴 for a day off).
        val own = HashMap<Int, ShiftRole>()
        for ((d, cell) in row.cells) {
            val word = row.raw[d]
            val again = word?.let { after.read(it)?.first }
            (again as? DutyCell.Shift)?.let { a -> roleOf[a.shiftTypeId]?.let { own[d] = it } }
            val role = (cell as? DutyCell.Shift)?.let { oldRole(it.shiftTypeId) }
            when {
                // 1.38 PC 11차: a day's word is the anchor reading's, put under a day by its own date line (2·3·6·8·9일 lost
                // there), so it can sit a day off. The cell put together from every reading keeps its part: 야간 stays 야간.
                role != null && !role.isOff && newId(role) != null -> {
                    cells[d] = DutyCell.Shift(newId(role)!!)
                    if (d in row.guessed) guessed += d
                }
                role != null || cell == DutyCell.Off -> offs += d
                cell is DutyCell.Shift && again != null -> {
                    cells[d] = again
                    if (d in row.guessed && !(before.guessedWork(word, cell) && !after.guessedWork(word, again))) guessed += d
                }
                cell is DutyCell.Shift -> offs += d
                cell is DutyCell.Unknown && again != null -> { cells[d] = again; guessed += d }
                else -> { cells[d] = cell; if (d in row.guessed) guessed += d }
            }
        }
        // A day off: 비번 the day after a 야간 or 당번 when the sheet has 비번, 휴무 otherwise; that is what 비 means, and it
        // does not hang on which word sat over the day. With the day before unknown, the day's own word, else 휴무. Yellow
        // unless the day's own word says the same.
        val afterDuty = newId(ShiftRole.AFTER_DUTY)
        val rest = newId(ShiftRole.OFF)?.let { DutyCell.Shift(it) } ?: DutyCell.Off
        for (d in offs.sorted()) {
            val prev = (cells[d - 1] as? DutyCell.Shift)?.let { roleOf[it.shiftTypeId] }
            val mine = own[d]?.takeIf { it.isOff }
            val pick = when {
                afterDuty == null -> ShiftRole.OFF
                prev == ShiftRole.NIGHT || prev == ShiftRole.DUTY -> ShiftRole.AFTER_DUTY
                prev != null -> ShiftRole.OFF
                else -> mine ?: ShiftRole.OFF
            }
            cells[d] = if (pick == ShiftRole.AFTER_DUTY) DutyCell.Shift(afterDuty!!) else rest
            if (mine != pick || d in row.guessed) guessed += d
        }
        return RosterPeriod.fill(listOf(row.copy(cells = cells, guessed = guessed))).first()
    }
}
