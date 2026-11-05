package com.bamnat.app.data

import android.content.Context
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.36 PC 재검토 (알바 → 현장으로 바꿔도 '오전 알바·오후 알바'가 하루 시트에 그대로): the kinds of day each calendar keeps but has put
 * away ('안 쓰는 종류'). A folded kind is not offered on a day sheet unless that day already has it; it is never deleted, so past days,
 * patterns and pay stay as they were, and 근무 종류와 시간 brings it back. Kept on the phone only.
 */
class FoldedTypes(context: Context?) {
    private val prefs = context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    /** Calendar id → the kinds of day folded there. */
    val state: StateFlow<Map<String, Set<Long>>> = _state.asStateFlow()

    fun of(calendar: String): Set<Long> = _state.value[calendar].orEmpty()

    fun fold(calendar: String, ids: Collection<Long>) = put(calendar, of(calendar) + ids)

    fun unfold(calendar: String, id: Long) = put(calendar, of(calendar) - id)

    fun unfold(calendar: String, ids: Collection<Long>) { if (of(calendar).any { it in ids }) put(calendar, of(calendar) - ids.toSet()) }

    private fun put(calendar: String, ids: Set<Long>) {
        // 1.36 PC 재검토 (접어 두기를 눌러도 그대로였어요): who folded or unfolded what, in a debug build's log (tag BamnatFold).
        if (com.bamnat.app.BuildConfig.DEBUG) runCatching {
            android.util.Log.d("BamnatFold", "$calendar ${of(calendar)} -> $ids", Throwable("by"))
        }
        _state.value = _state.value.toMutableMap().apply { if (ids.isEmpty()) remove(calendar) else put(calendar, ids) }
        prefs?.edit()?.apply {
            if (ids.isEmpty()) remove(calendar) else putString(calendar, ids.joinToString(","))
        }?.apply()
    }

    /**
     * [SETUP-16] 1.37 PC b98f9d1e (현장으로 접어 두기 → 되돌리기를 놓치고 교대로 돌아가니 11/6부터 다 쉬는 날): the repeating schedule as it
     * was before 접어 두기 stopped it on [StoppedCycle.on], so going back to work with a cycle brings it back. In the same file, so it
     * goes in the backup with the folded kinds.
     */
    data class StoppedCycle(
        val on: java.time.LocalDate,
        val was: List<com.bamnat.core.model.WorkPattern>,
        /** The kind of work it was stopped from: only going back to that one brings it back (1.37 PC 0068727b: 알바 got 교대's 주간·야간). */
        val from: com.bamnat.core.work.WorkStyle? = null,
    )

    fun stopped(calendar: String): StoppedCycle? = runCatching {
        val o = org.json.JSONObject(prefs?.getString(CYCLE + calendar, null) ?: return null)
        val a = o.getJSONArray("was")
        StoppedCycle(java.time.LocalDate.ofEpochDay(o.getLong("on")), from = o.optString("from").let { n ->
            com.bamnat.core.work.WorkStyle.entries.firstOrNull { it.name == n } }, was = (0 until a.length()).mapNotNull { i ->
            val p = a.getJSONObject(i)
            com.bamnat.app.data.db.PatternEntity(
                kind = p.getString("kind"), startEpochDay = p.getLong("start"), endEpochDay = if (p.isNull("end")) null else p.getLong("end"),
                steps = p.getString("steps"), anchorEpochDay = if (p.isNull("anchor")) null else p.getLong("anchor"),
            ).toModel()
        })
    }.getOrNull()

    fun stop(calendar: String, cycle: StoppedCycle?) {
        val e = prefs?.edit() ?: return
        if (cycle == null) e.remove(CYCLE + calendar) else e.putString(CYCLE + calendar, org.json.JSONObject()
            .put("on", cycle.on.toEpochDay())
            .put("from", cycle.from?.name ?: "")
            .put("was", org.json.JSONArray(cycle.was.mapIndexed { i, p ->
                val x = p.toEntity(i + 1)
                org.json.JSONObject().put("kind", x.kind).put("start", x.startEpochDay).put("end", x.endEpochDay ?: org.json.JSONObject.NULL)
                    .put("steps", x.steps).put("anchor", x.anchorEpochDay ?: org.json.JSONObject.NULL)
            })).toString())
        e.apply()
    }

    private fun load(): Map<String, Set<Long>> = prefs?.all.orEmpty().filterKeys { !it.startsWith(CYCLE) }.mapNotNull { (k, v) ->
        (v as? String)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }?.let { k to it }
    }.toMap()

    companion object {
        private const val FILE = "folded_types"
        private const val CYCLE = "cycle:"

        /** The names of the kinds of day a kind of work starts with (to fold what the old one brought and the new one does not use). */
        fun startNames(style: com.bamnat.core.work.WorkStyle?): Set<String> = when (style) {
            com.bamnat.core.work.WorkStyle.HOURLY -> com.bamnat.core.schedule.ShiftBundles.hourly.map { it.type.name }.toSet()
            com.bamnat.core.work.WorkStyle.DAY -> com.bamnat.core.schedule.ShiftBundles.weekday.filter { !it.type.isOff }.map { it.type.name }.toSet()
            com.bamnat.core.work.WorkStyle.SHIFT -> (com.bamnat.core.schedule.ShiftBundles.all.flatMap { b -> b.shifts.filter { !it.type.isOff } }
                .map { it.type.name } + listOf("주간", "오후", "야간")).toSet()
            else -> emptySet()
        }
    }
}
