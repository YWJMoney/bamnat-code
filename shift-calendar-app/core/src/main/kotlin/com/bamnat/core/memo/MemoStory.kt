package com.bamnat.core.memo

import com.bamnat.core.design.StoryItem
import com.bamnat.core.design.StoryKind
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.38 [FLOW-08] 내 하루 이야기: the memos of a day that have a time, as lines of the day's story ("15:00 도시락").
 * The time is the memo's schedule ([Memo.reminder]) or else a clock time written in it ([MemoTime.timeOn]); a memo with
 * neither is not on the time line (it stays in the day's memo cards). A repeating memo is on [day] at its time of day.
 * Opening a line opens the memo: [memoIdOf] reads the memo back from the line.
 */
object MemoStory {
    private const val PREFIX = "memo:"

    /** The story lines of [memos] (those shown on [day], as the day view has them) on [day], in the order given. */
    fun items(memos: List<Memo>, day: LocalDate): List<StoryItem> = memos.mapNotNull { m ->
        if (m.inTrash) return@mapNotNull null
        val at = timeOf(m, day) ?: return@mapNotNull null
        if (at.toLocalDate() != day) return@mapNotNull null
        val title = m.title.ifBlank { m.preview }.ifBlank { "메모" }
        val detail = m.preview.takeIf { it.isNotBlank() && it != title }
        StoryItem(start = at, kind = StoryKind.Memo, title = title, detail = detail, id = "$PREFIX${m.id}@$day")
    }

    /** The memo a story line came from, or null for a line that is not a memo. */
    fun memoIdOf(item: StoryItem): Long? =
        if (item.kind != StoryKind.Memo || !item.id.startsWith(PREFIX)) null
        else item.id.removePrefix(PREFIX).substringBefore('@').toLongOrNull()

    /** When [m] is on [day]: its schedule (at that time of day for a repeating memo), else a time written in it. */
    fun timeOf(m: Memo, day: LocalDate): LocalDateTime? {
        m.reminder?.let { r -> return if (m.repeat.isNotEmpty()) day.atTime(r.at.toLocalTime()) else r.at }
        return MemoTime.timeOn(m, day)
    }
}
