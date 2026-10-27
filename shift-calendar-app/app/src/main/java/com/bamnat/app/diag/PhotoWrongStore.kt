package com.bamnat.app.diag

import android.content.Context
import com.bamnat.app.BuildConfig
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.serverRequest
import com.bamnat.core.stats.PhotoScreen
import com.bamnat.core.stats.PhotoWrongBook
import com.bamnat.core.stats.PhotoWrongReason
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * [PHOTO-09] 1.29 AQ: '사진이 달라요' presses kept on the phone ([PhotoWrongBook]: day, screen, reason, a number) and sent to the
 * company server by [com.bamnat.app.company.OutboxJob] once the `photo.wrong` row is on (처리방침 3.3). Offline presses wait,
 * like a question in the outbox. Never the photo or what it was read as.
 */
class PhotoWrongStore(
    context: Context,
    private val baseUrl: String = BuildConfig.COMPANY_BASE_URL,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val post: (String, JSONObject) -> Unit = { url, body -> serverRequest(url, "POST", "/api/photo-wrong", body) },
) {
    private val prefs = context.applicationContext.getSharedPreferences("photo_wrong", Context.MODE_PRIVATE)
    private val lock = Any()

    private fun book(): PhotoWrongBook = PhotoWrongBook.decode(prefs.getString(K_BOOK, null))

    /** Presses waiting to go, for 서버 연결 점검. */
    val waiting: Int get() = synchronized(lock) { book().waiting }

    fun record(screen: PhotoScreen, reason: PhotoWrongReason) = synchronized(lock) {
        prefs.edit().putString(K_BOOK, book().record(screen, reason, Today.date(zone()).toEpochDay()).encode()).apply()
    }

    /** What would go, for a test and the settings page. */
    fun preview(): String? = synchronized(lock) { book().bundle(BuildConfig.VERSION_NAME) { LocalDate.ofEpochDay(it).toString() } }

    /**
     * Sends what waits when [allowed]; true when nothing is left to try now (sent, nothing waiting, or not allowed yet: the
     * presses stay until 3.3). A refused bundle (it would be refused again) is dropped; no connection keeps it.
     */
    suspend fun flush(allowed: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!allowed || baseUrl.isBlank()) return@withContext true
        val snapshot = synchronized(lock) { book() }
        val body = snapshot.bundle(BuildConfig.VERSION_NAME) { LocalDate.ofEpochDay(it).toString() } ?: return@withContext true
        try {
            post(baseUrl, JSONObject(body))
        } catch (e: CompanyException) {
            if (e.code == "offline" || e.code == "rate_limited" || e.code.startsWith("http_5")) return@withContext false
        }
        synchronized(lock) { prefs.edit().putString(K_BOOK, book().sent(snapshot.toSend()).encode()).apply() }
        synchronized(lock) { book().waiting == 0 }
    }

    private companion object {
        const val K_BOOK = "book"
    }
}
