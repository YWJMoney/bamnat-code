package com.bamnat.app.company

import com.bamnat.core.plaza.SharePart
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2.0.0 [RVN-22] 검토 I10bc-03: the plaza file with some parts ([SharePart]) as they are now and the others as the plaza has them,
 * so one part can go without the others (근무 시간 저절로 while 급여 계산 방식 is asked, or 안 함).
 *
 * - 근무주기: the shift list (which shifts there are, a day off or not), the order (rotation, weekly, roster) and the rest of the file.
 * - 근무 시간: each shift's hours ([TIME_KEYS]), matched by name.
 * - 급여 계산 방식: `pay` and the kinds of extra work ([PAY_KEYS]).
 */
internal object ShareFile {
    private val PAY_KEYS = listOf("pay", "extraKinds", "extraKindShifts", "extraKindShiftSets")
    private val TIME_KEYS = listOf("start", "end", "break", "breakStart", "rest", "restStart", "gapStart", "gap")

    /**
     * [now] for the parts in [take], [has] for the others. Null when the cycle is not taken and there is nothing to take it from
     * ([has] null): a pay way or hours alone cannot go to a plaza that has no shifts of this calendar yet. With [has] null and the
     * cycle taken, the hours go with the shifts (a shift is its hours) and a pay way not taken stays out.
     */
    fun compose(now: String, has: String?, take: Set<SharePart>): String? {
        if (take.containsAll(SharePart.entries)) return now
        val n = JSONObject(now)
        val h = has?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (SharePart.CYCLE !in take && h == null) return null
        val out = JSONObject((if (SharePart.CYCLE in take) n else h!!).toString())
        // The hours of each shift, by name, from the side they are taken from.
        (if (SharePart.TIMES in take) n else h)?.let { from ->
            val hours = from.optJSONArray("shiftTypes").objects().associateBy { it.optString("name") }
            out.optJSONArray("shiftTypes").objects().forEach { t ->
                val src = hours[t.optString("name")] ?: return@forEach
                if (src.optBoolean("off") || t.optBoolean("off")) return@forEach
                TIME_KEYS.forEach { k -> if (src.has(k)) t.put(k, src.get(k)) else t.remove(k) }
            }
        }
        // The pay way and the kinds of extra work, whole, from the side they are taken from (none when that side has none).
        PAY_KEYS.forEach { out.remove(it) }
        (if (SharePart.PAY in take) n else h)?.let { from -> PAY_KEYS.forEach { k -> if (from.has(k)) out.put(k, from.get(k)) } }
        // The file's version says the newest thing in it, whichever side it came from.
        val v = maxOf(n.optInt("v"), h?.optInt("v") ?: 0)
        if (v > 0) out.put("v", v)
        return out.toString()
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
