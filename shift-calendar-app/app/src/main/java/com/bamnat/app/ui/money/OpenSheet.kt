package com.bamnat.app.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 2.0.0 [RVK-03] (검토 MONEY-01): which saved line a sheet has open, kept by its id, so a screen rebuilt (어두운 모드 자동 전환,
 * 글자 크기) or an app the phone closed while the user checked the amount in the bank app opens the same sheet again, with
 * what was typed in it (the sheet's own fields are saveable). A line not saved yet (id 0) is kept for this screen only, as before.
 *
 * ```
 * var editing by rememberOpenLine<MoneyEntry>({ it.id }) { id -> book.entries.firstOrNull { it.id == id } }
 * ```
 */
@Composable
internal fun <T : Any> rememberOpenLine(idOf: (T) -> Long, find: (Long) -> T?): MutableState<T?> {
    val id = rememberSaveable { mutableStateOf<Long?>(null) }
    val live = remember { mutableStateOf<T?>(null) }
    val look = rememberUpdatedState(find)
    val key = rememberUpdatedState(idOf)
    return remember {
        object : MutableState<T?> {
            override var value: T?
                get() = live.value ?: id.value?.let { look.value(it) }
                set(v) {
                    live.value = v
                    id.value = v?.let(key.value)?.takeIf { it != 0L }
                }

            override fun component1(): T? = value
            override fun component2(): (T?) -> Unit = { value = it }
        }
    }
}
