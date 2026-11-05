package com.bamnat.core.imports

/**
 * 1.38 [READ-31] A photo of a list (직원 명단, 근로계약 정리표) as rows of cells, for [StaffSheets] to read as it reads a sheet:
 * words on one line by their middle, a line cut into cells where the gap is wider than a letter, and each cell put under
 * the column of the line that has the most cells (the header, mostly) so a blank cell does not move the ones after it.
 */
object WordTable {
    fun rows(words: List<RosterWord>): List<List<String>> {
        val ws = words.filter { it.text.isNotBlank() }
        if (ws.isEmpty()) return emptyList()
        val letter = ws.map { it.bottom - it.top }.sorted()[ws.size / 2].coerceAtLeast(1)
        // Lines: a word joins the line whose middle is within half a letter of its own.
        val lines = ArrayList<MutableList<RosterWord>>()
        for (w in ws.sortedBy { (it.top + it.bottom) / 2 }) {
            val mid = (w.top + w.bottom) / 2
            val line = lines.lastOrNull()?.takeIf { l -> kotlin.math.abs(l.map { (it.top + it.bottom) / 2 }.average() - mid) <= letter / 2.0 }
            if (line != null) line += w else lines += mutableListOf(w)
        }
        // Cells: the words of a line left to right, a new cell where the gap is wider than a letter.
        data class Cell(val text: String, val left: Int, val right: Int) { val mid get() = (left + right) / 2 }
        val cells = lines.map { l ->
            val out = ArrayList<Cell>()
            for (w in l.sortedBy { it.left }) {
                val last = out.lastOrNull()
                if (last != null && w.left - last.right <= letter) out[out.size - 1] = Cell(last.text + " " + w.text, last.left, maxOf(last.right, w.right))
                else out += Cell(w.text, w.left, w.right)
            }
            out
        }
        val head = cells.maxBy { it.size }
        if (head.size < 2) return cells.map { l -> l.map { it.text } }
        return cells.map { l ->
            val row = MutableList(head.size) { "" }
            l.forEach { c ->
                val at = head.indices.minBy { kotlin.math.abs(head[it].mid - c.mid) }
                row[at] = if (row[at].isEmpty()) c.text else row[at] + " " + c.text
            }
            row
        }
    }
}
