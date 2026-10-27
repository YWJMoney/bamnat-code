package com.bamnat.app.invest

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.bamnat.core.time.Today
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [INV-40] 409 C: a screenshot shared to 밤낮 from the gallery while an investor calendar is on screen is a 체결 내역
 * capture. The 투자 tab takes it and reads it on the phone (결정 D68); nothing changes until the user fills the prices.
 */
object IncomingFills {
    private val _shot = MutableStateFlow<Uri?>(null)
    val shot: StateFlow<Uri?> = _shot.asStateFlow()

    fun put(uri: Uri) { _shot.value = uri }

    /** The waiting capture, once. */
    fun take(): Uri? = _shot.value.also { _shot.value = null }

    /**
     * [INVA-01] 1.29 AR: the day the screenshot was taken, from the gallery's own record of it (taken, else changed, else
     * added), so a 체결 list without a day on each row is that day's, not the day it was shared. Null when the file does
     * not say (a file from a chat app, a test).
     */
    fun dayOf(context: Context, uri: Uri): LocalDate? = runCatching {
        val columns = arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED)
        context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            // DATE_TAKEN is in milliseconds; the other two in seconds.
            val millis = listOf(0 to 1L, 1 to 1000L, 2 to 1000L).firstNotNullOfOrNull { (i, scale) ->
                c.getColumnIndex(columns[i]).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) * scale }?.takeIf { it > 0 }
            } ?: return@use null
            Instant.ofEpochMilli(millis).atZone(Today.zone()).toLocalDate().takeIf { !it.isAfter(Today.date()) }
        }
    }.getOrNull()
}
