package com.bamnat.app.ui.work

import com.bamnat.app.data.PhaseThreeBackup
import com.bamnat.app.data.PhaseThreeStore
import com.bamnat.core.boss.StaffMember
import org.json.JSONObject

/**
 * 2.0.0 [RVJ-75] 검토 PAYU-04 (조건 10 '적던 것은 사라지지 않아요'): the 직원 정보 being typed, kept on this phone
 * ([com.bamnat.app.ui.components.DraftStore], device only, gone by itself after 7 days) while the window is open, so a look at
 * another app, the dark theme or Android closing the app in the background no longer empties it. Written as the backup writes a
 * staff member ([PhaseThreeBackup.staffJson]), but never the 출퇴근 링크's key or the 근로계약서: those come from the person as
 * they are when the window opens again.
 */
internal object StaffDraft {
    const val KEY = "boss.staff"
    private const val HEAD = "staff1"

    /** [start] as the window opened (a new person has a blank name), [now] as it was being typed, on the 사업장 [place]. */
    class Kept(val place: String, val start: StaffMember, val now: StaffMember)

    private fun json(place: String, s: StaffMember): JSONObject = with(PhaseThreeStore) {
        PhaseThreeBackup.staffJson(s.copy(clockId = null, clockToken = null, contract = null).toEntity(place, 0))
    }

    private fun member(o: JSONObject): StaffMember = with(PhaseThreeStore) { PhaseThreeBackup.staffFrom(o).toModel() }

    fun encode(place: String, start: StaffMember, now: StaffMember): String =
        JSONObject().put("h", HEAD).put("p", place).put("s", json(place, start)).put("n", json(place, now)).toString()

    /** What was kept; null when there is none or it cannot be read. */
    fun decode(raw: String): Kept? = if (raw.isBlank()) null else runCatching {
        val o = JSONObject(raw)
        if (o.optString("h") != HEAD) return@runCatching null
        Kept(o.getString("p"), member(o.getJSONObject("s")), member(o.getJSONObject("n")))
    }.getOrNull()
}
