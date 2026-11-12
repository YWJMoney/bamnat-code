package com.bamnat.core.memo

/**
 * 1.39 [TASK-39] What changes a memo while it is open in the editor: its notice or the 할 일 위젯 (시각, 끝내기), and since 2.0.0
 * [RVI-32] (검토 MEMO-12) the list beside it on an open fold (고정, 색, a 표·보드 칸, 휴지통). The editor keeps what it last read
 * ([base]); a field the stored memo has changed since and the editor has not changed itself takes the stored value, so a letter
 * typed afterwards does not put the old 고정·색·칸 back. What the editor changed itself wins, field by field; a checklist is
 * taken item by item while its items line up.
 */
object MemoOutside {
    /** [edited] with the outside changes in [stored] (made since [base]) that the editor left alone. */
    fun keep(edited: Memo, base: Memo, stored: Memo): Memo {
        fun <T> pick(get: (Memo) -> T): T {
            val mine = get(edited)
            return if (mine == get(base) && get(stored) != get(base)) get(stored) else mine
        }
        return edited.copy(
            date = pick { it.date },
            title = pick { it.title },
            body = pick { it.body },
            checklist = checklist(edited.checklist, base.checklist, stored.checklist),
            isChecklist = pick { it.isChecklist },
            colorIndex = pick { it.colorIndex },
            pinned = pick { it.pinned },
            deletedAtMillis = pick { it.deletedAtMillis },
            reminder = pick { it.reminder },
            reminderAsked = pick { it.reminderAsked },
            calendars = pick { it.calendars },
            repeat = pick { it.repeat },
            skips = pick { it.skips },
            doneAtMillis = pick { it.doneAtMillis },
            doneDays = pick { it.doneDays },
        )
    }

    /** The checklist item by item when all three line up (the same number of items), else as one field. */
    private fun checklist(edited: List<ChecklistItem>, base: List<ChecklistItem>, stored: List<ChecklistItem>): List<ChecklistItem> {
        if (edited == base) return stored
        if (stored == base) return edited
        if (edited.size != base.size || stored.size != base.size) return edited
        return edited.indices.map { i -> if (edited[i] == base[i]) stored[i] else edited[i] }
    }

    /** [base] once [stored] has been seen, so its changes are not taken again over a later edit. */
    fun seen(base: Memo, stored: Memo): Memo = stored

    /** [RVI-32] The memo went to the trash from outside (the list beside the editor): the editor closes and does not bring it back. */
    fun trashedOutside(base: Memo, stored: Memo): Boolean = base.deletedAtMillis == null && stored.deletedAtMillis != null
}
