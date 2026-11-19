package com.bamnat.core.pay

/**
 * [READ-19] 1.27 순번 447: what a payslip line's name means for this user, learned from the line they corrected on
 * "사진과 같은가요?" ('다른 곳이 있어요' → 이 줄은 야간근로수당이에요). Next payslip from the same company, a line of that
 * name is read as they said. Kept on the phone (and in its backup), never sent anywhere (D68).
 *
 * A name is kept as [key] gives it: letters only, OCR slips fixed (아간근로 → 야간근로), so the same line read a little
 * differently next month still matches. Amounts are never learned: they change every month.
 */
object SlipNames {
    /** Shortest name worth keeping: one or two letters ("식", "기타") say little about one company's line. */
    const val MIN_KEY = 2

    fun key(name: String): String = PayslipReader.ocrFix(name.filter { it.isLetter() })

    /**
     * [read] with every line whose name the user taught read as they said. 2.0.0 [RVL-46] (검토 SCAN-29) A name looked at
     * ([SlipLine.check]) that the user answered before is not asked again: the letters as printed when those were taught
     * (숙비 kept as 숙비, not 식비), else the name shown when that was.
     */
    fun apply(read: PayslipRead, book: Map<String, SlipRole>): PayslipRead {
        if (book.isEmpty()) return read
        val lines = read.lines.map { l ->
            val printed = l.check?.printed?.let(::key)?.takeIf { it.length >= MIN_KEY }
            when {
                printed != null && book[printed] != null -> l.copy(name = l.check!!.printed, role = book[printed]!!, check = null)
                book[key(l.name)] != null -> l.copy(role = book[key(l.name)]!!, check = null)
                else -> l
            }
        }
        return if (lines == read.lines) read else read.copy(lines = lines)
    }

    /**
     * What correcting [before] into [after] teaches: each line kept by name (same place, same name) whose kind the
     * user changed. A line added or removed teaches nothing (the reader may simply have missed it).
     */
    fun learned(before: PayslipRead, after: PayslipRead): Map<String, SlipRole> {
        val out = LinkedHashMap<String, SlipRole>()
        for (a in after.lines) {
            val k = key(a.name)
            if (k.length < MIN_KEY) continue
            // 2.0.0 [RVL-46] A line put back to its printed letters (숙비 for 식비) is found by them.
            val was = before.lines.firstOrNull { key(it.name) == k } ?: before.lines.firstOrNull { it.check?.printed?.let(::key) == k } ?: continue
            // 2.0.0 [RVL-46] A name looked at and answered teaches its kind too, so next month it is not asked again.
            if (was.role != a.role || was.check != null && a.check == null) out[k] = a.role
        }
        return out
    }
}
