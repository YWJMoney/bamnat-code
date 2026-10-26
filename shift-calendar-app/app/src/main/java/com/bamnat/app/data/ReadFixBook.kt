package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.RosterLearn
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipRole

/**
 * [READ-19] 1.27 순번 447: what the user corrected on "사진과 같은가요?", so the next photo reads the way they said.
 * - 명세서 줄 이름 → 종류 ("가족수당" → 수당, "야근수당" → 야간근로수당): "slip:<이름>" → [SlipRole] name.
 * - 듀티표 글자 고친 횟수 ("OF" → 야간, 두 번째에 [DutyCodeBook]으로 옮겨요): "duty:<달력>:<글자>" → "<칸>|<횟수>".
 * On this phone, in the backup (설정 파일 "read_fixes", [SettingsFilesBackup.PARTS]), never sent anywhere (D68). No
 * amount, no name of a person, no photo is kept: only the words of a payslip's line names and a roster's codes.
 */
class ReadFixBook(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Line name (as [SlipNames.key] gives it) → kind, as the user taught. */
    fun slipNames(): Map<String, SlipRole> = prefs.all.mapNotNull { (k, v) ->
        if (!k.startsWith(SLIP)) return@mapNotNull null
        val role = (v as? String)?.let { s -> SlipRole.entries.firstOrNull { it.name == s } } ?: return@mapNotNull null
        k.removePrefix(SLIP) to role
    }.toMap()

    fun learnSlip(taught: Map<String, SlipRole>) {
        if (taught.isEmpty()) return
        prefs.edit().apply { taught.forEach { (k, r) -> if (k.length >= SlipNames.MIN_KEY) putString(SLIP + k.take(40), r.name) } }.apply()
    }

    /**
     * A roster cell corrected: [word] read, [cell] meant, on [calendarId]. True when it has now been corrected so
     * [RosterLearn.TIMES] times, and so goes in the calendar's code book; the count is then dropped.
     */
    fun dutyFixed(calendarId: String, word: String, cell: DutyCell): Boolean {
        val code = DutyCodeBook.encodeCell(cell) ?: return false
        val key = DUTY + calendarId + ":" + DutyCodes.norm(word)
        val (was, n) = prefs.getString(key, null)?.split('|')?.takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } ?: ("" to 0)
        val count = if (was == code) n + 1 else 1
        if (count >= RosterLearn.TIMES) {
            prefs.edit().remove(key).apply()
            return true
        }
        prefs.edit().putString(key, "$code|$count").apply()
        return false
    }

    companion object {
        const val PREFS = "read_fixes"
        private const val SLIP = "slip:"
        private const val DUTY = "duty:"

        /** The system keeps each preferences file once, so a book made where it is needed costs nothing. */
        fun get(context: Context): ReadFixBook = ReadFixBook(context.applicationContext)
    }
}
