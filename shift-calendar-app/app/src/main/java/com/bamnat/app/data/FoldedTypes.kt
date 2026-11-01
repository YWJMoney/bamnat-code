package com.bamnat.app.data

import android.content.Context
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
        _state.value = _state.value.toMutableMap().apply { if (ids.isEmpty()) remove(calendar) else put(calendar, ids) }
        prefs?.edit()?.apply {
            if (ids.isEmpty()) remove(calendar) else putString(calendar, ids.joinToString(","))
        }?.apply()
    }

    private fun load(): Map<String, Set<Long>> = prefs?.all.orEmpty().mapNotNull { (k, v) ->
        (v as? String)?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }?.let { k to it }
    }.toMap()

    companion object {
        private const val FILE = "folded_types"

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
