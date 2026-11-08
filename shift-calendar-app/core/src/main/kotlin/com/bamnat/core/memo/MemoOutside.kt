package com.bamnat.core.memo

/**
 * 1.39 [TASK-39] What a memo's notice or the 할 일 위젯 changes while the memo is open in the editor: its schedule time (10분 뒤,
 * 다른 때…) and whether it is finished (끝내기, the widget's circle). The editor keeps what it last read ([base]); when the stored memo
 * has one of these changed since and the editor has not changed it itself, the stored one is kept, so a letter typed afterwards does
 * not put 9:35 back over the 9:45 the notice moved it to (PC 6차, 11-08). What the editor changed itself wins.
 */
object MemoOutside {
    /** [edited] with the outside changes in [stored] (made since [base]) that the editor left alone. */
    fun keep(edited: Memo, base: Memo, stored: Memo): Memo {
        var m = edited
        if (stored.reminder != base.reminder && edited.reminder == base.reminder) m = m.copy(reminder = stored.reminder)
        if (stored.doneAtMillis != base.doneAtMillis && edited.doneAtMillis == base.doneAtMillis) m = m.copy(doneAtMillis = stored.doneAtMillis)
        if (stored.doneDays != base.doneDays && edited.doneDays == base.doneDays) m = m.copy(doneDays = stored.doneDays)
        return m
    }

    /** [base] once [stored]'s outside fields have been seen, so they are not taken again over a later edit. */
    fun seen(base: Memo, stored: Memo): Memo =
        base.copy(reminder = stored.reminder, doneAtMillis = stored.doneAtMillis, doneDays = stored.doneDays)
}
