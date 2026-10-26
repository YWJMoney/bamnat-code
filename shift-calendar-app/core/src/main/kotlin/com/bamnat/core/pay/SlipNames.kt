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

    /** [read] with every line whose name the user taught read as they said. */
    fun apply(read: PayslipRead, book: Map<String, SlipRole>): PayslipRead {
        if (book.isEmpty()) return read
        val lines = read.lines.map { l -> book[key(l.name)]?.takeIf { it != l.role }?.let { l.copy(role = it) } ?: l }
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
            val was = before.lines.firstOrNull { key(it.name) == k } ?: continue
            if (was.role != a.role) out[k] = a.role
        }
        return out
    }
}
