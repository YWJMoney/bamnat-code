package com.bamnat.core.memo

/**
 * 2.0.0 [RVI-35] (검토 MEMO-16) 체크리스트 기본기, kept out of the screen so it is tested: a list pasted into one line becomes one
 * line each (카톡으로 받은 준비물), an empty line taken back with ⌫ joins the line above, lines move up and down, and a list
 * turned into words keeps what was checked ("☑ 장갑"), which comes back checked when the words become a list again.
 */
object ChecklistEdit {
    const val CHECKED = "☑ "
    const val OPEN = "☐ "

    /** A line's own mark: ☑/☐, a bullet or a number ("- ", "• ", "1. "), taken off a pasted line. */
    private val MARK = Regex("""^\s*(?:[☑✅✔☒]|\[[xX]\])\s*""")
    private val OPEN_MARK = Regex("""^\s*(?:[☐□▢]|\[ \]|[-*•·▪◦]|\d{1,3}[.)])\s+""")

    data class Line(val text: String, val checked: Boolean)

    /** [raw] as a list line: its mark taken off, ☑ for checked. */
    fun line(raw: String): Line {
        MARK.find(raw)?.let { return Line(raw.substring(it.range.last + 1).trim(), true) }
        OPEN_MARK.find(raw)?.let { return Line(raw.substring(it.range.last + 1).trim(), false) }
        return Line(raw.trim(), false)
    }

    /**
     * Typed or pasted [value] into the line at [index]: with line breaks, the first part stays on that line and every other
     * non-empty part becomes a line of its own after it. Returns the list and the line to put the cursor on.
     */
    fun typed(items: List<ChecklistItem>, index: Int, value: String): Pair<List<ChecklistItem>, Int?> {
        if (index !in items.indices) return items to null
        if (!value.contains('\n')) return items.mapIndexed { i, it -> if (i == index) it.copy(text = value) else it } to null
        val parts = value.split('\n')
        val first = parts.first()
        val rest = parts.drop(1).filter { it.isNotBlank() }.map { line(it) }
        val list = items.toMutableList()
        // One Enter keeps the line as typed; a pasted list loses its marks ("- 장갑" → "장갑") on every line.
        list[index] = list[index].copy(text = if (rest.isEmpty()) first else line(first).text.ifEmpty { first.trim() })
        // One Enter typed at the end: a new empty line after it, as before.
        if (rest.isEmpty()) {
            list.add(index + 1, ChecklistItem(""))
            return list to index + 1
        }
        rest.forEachIndexed { k, l -> list.add(index + 1 + k, ChecklistItem(l.text, l.checked)) }
        return list to index + rest.size
    }

    /**
     * ⌫ at the start of the line at [index]: the line joins [above] (the line shown above it), its words at the end. Returns
     * the list, the line the cursor goes to and where in it; null when there is no line above.
     */
    fun joinAbove(items: List<ChecklistItem>, index: Int, above: Int?): Triple<List<ChecklistItem>, Int, Int>? {
        if (index !in items.indices || above == null || above !in items.indices || above == index) return null
        val top = items[above]
        val caret = top.text.length
        val list = items.toMutableList()
        list[above] = top.copy(text = top.text + items[index].text)
        list.removeAt(index)
        val at = if (above > index) above - 1 else above
        return Triple(list, at, caret)
    }

    /** The line at [from] moved to [to] (both positions in [items]). */
    fun move(items: List<ChecklistItem>, from: Int, to: Int): List<ChecklistItem> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        val list = items.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }

    /**
     * 2.0.0 [RVIB-02] (검토 I07ab-03) 별표: the line at [index] starred goes up to the top of the open lines, after the ones starred
     * before it, and stays where the finger puts it after that; unstarred, it stays where it is.
     */
    fun star(items: List<ChecklistItem>, index: Int, on: Boolean): List<ChecklistItem> {
        if (index !in items.indices) return items
        val list = items.toMutableList()
        val item = list.removeAt(index).copy(starred = on)
        if (!on) {
            list.add(index, item)
            return list
        }
        val lastStar = list.indexOfLast { it.starred && !it.checked }
        val firstOpen = list.indexOfFirst { !it.checked }
        list.add(if (lastStar >= 0) lastStar + 1 else if (firstOpen >= 0) firstOpen else list.size, item)
        return list
    }

    /** 2.0.0 [RVIB-02] A line's own time, or none. */
    fun time(items: List<ChecklistItem>, index: Int, time: java.time.LocalTime?): List<ChecklistItem> =
        items.mapIndexed { i, it -> if (i == index) it.copy(time = time) else it }

    /** 2.0.0 [RVIB-02] A line's note, kept to one line. */
    fun note(items: List<ChecklistItem>, index: Int, note: String): List<ChecklistItem> =
        items.mapIndexed { i, it -> if (i == index) it.copy(note = note.replace('\n', ' ')) else it }

    /**
     * The list as words, one line each; a checked line keeps its ☑ so nothing finished is lost. 2.0.0 [RVIB-02] A starred line
     * keeps its ★, and a line's time and note follow it ("★ 약 받기 · 15:00 · 처방전 챙기기"), so they stay as words.
     */
    fun toText(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) CHECKED else "") + words(it) }

    /** A line copied or shared as words, its box first: "☐ ★ 약 받기 · 15:00 · 처방전". */
    fun shared(item: ChecklistItem): String = (if (item.checked) CHECKED else OPEN) + words(item)

    private fun words(item: ChecklistItem): String = (if (item.starred) STAR else "") +
        listOfNotNull(item.text, item.time?.let { t -> "%02d:%02d".format(t.hour, t.minute) }, item.note.takeIf { it.isNotBlank() }).joinToString(" · ")

    /** Words as a list, one line each; "☑ …" comes back checked, "★ …" starred. */
    fun fromText(text: String): List<ChecklistItem> =
        text.lines().filter { it.isNotBlank() }.map { line(it) }.filter { it.text.isNotEmpty() }.map {
            val starred = it.text.startsWith(STAR) && it.text.length > STAR.length
            ChecklistItem(if (starred) it.text.removePrefix(STAR).trim() else it.text, it.checked, starred = starred)
        }

    const val STAR = "★ "
}
