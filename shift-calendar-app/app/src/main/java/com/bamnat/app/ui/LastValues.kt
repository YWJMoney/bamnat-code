package com.bamnat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * [UX-10] 1.25 Z (U7): the last value each screen worked out, with what it was worked out from. A screen opened again
 * (another tab and back, the pay screen a second time) shows it from its first frame instead of a spinner or 0 that
 * then turns into the same number, and does not work it out again while what it depends on is the same.
 * Kept for the app's run only; nothing here is saved.
 */
object LastValues {
    private class Entry(val inputs: List<Any?>, val value: Any?)

    private val map = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX
    }

    /** How many times a value was worked out; tests read it. */
    @Volatile var computed = 0
        private set

    @Synchronized internal fun value(key: String): Any? = map[key]?.value

    @Synchronized internal fun fresh(key: String, inputs: List<Any?>): Boolean = map[key]?.inputs == inputs

    @Synchronized internal fun put(key: String, inputs: List<Any?>, value: Any?) {
        computed++
        map[key] = Entry(inputs, value)
    }

    @Synchronized fun clear() = map.clear()

    private const val MAX = 64
}

/**
 * [UX-10] Like `produceState(null, inputs) { compute() }`, but the first frame shows the last value kept under [key],
 * and [compute] runs only when [inputs] differ from the ones that value came from. Setting the same value again does
 * not draw again (Compose compares the values).
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T> rememberLast(key: String, vararg inputs: Any?, compute: suspend () -> T): State<T?> {
    val state = remember(key) { mutableStateOf(LastValues.value(key) as T?) }
    val list = inputs.toList()
    LaunchedEffect(key, *inputs) {
        if (LastValues.fresh(key, list)) {
            state.value = LastValues.value(key) as T?
            return@LaunchedEffect
        }
        val v = compute()
        LastValues.put(key, list, v)
        state.value = v
    }
    return state
}
