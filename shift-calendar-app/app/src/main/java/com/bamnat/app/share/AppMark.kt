package com.bamnat.app.share

import com.bamnat.app.AppContainer
import com.bamnat.core.flags.FlagCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.time.Instant

/**
 * 2.0.0 [RVJ-83] SPREAD-38 (처리방침 3.14 14번, 30일 줄): a 팀 리더's link whose changes this phone puts into its own calendar by itself
 * ([RosterFollower.Mode.AUTO]) carries one '앱으로 받음' mark on the share server, so the leader can see how many of the team read the
 * roster in the app (the share server shows one number for the team, only from 3, never whose). The mark is a random word made here and
 * kept only here (no-backup storage, like the received links); the server keeps its hash, so only this phone takes it back. It goes
 * when the link is no longer put in by itself: 넣지 않기·물어보기, the link taken out of the app, the leader no longer asking, or the flag
 * off. Nothing is sent before [FlagCatalog.TEAM_APP_COUNT] (the 3.14 day) and the share server keeps nothing before its own 3.14 day.
 */
object AppMark {
    private const val FILE = "app_marks.json"
    private val lock = Mutex()

    /** A link that would carry the mark once the flag is on: a leader's watched link this phone puts in by itself. */
    fun candidate(cal: ReceivedCalendar): Boolean = !cal.gone && cal.watch && RosterFollower.mode(cal) == RosterFollower.Mode.AUTO

    /** What was sent: link id to the word and the link's time it was sent for (the server keeps the mark as long as the link). */
    internal data class Sent(val mark: String, val until: Instant?)

    /** Leaves, renews or takes back each mark as the links are now. Without internet what is left waits for the next time. */
    suspend fun sync(c: AppContainer) = lock.withLock {
        val on = runCatching { c.flags.on(FlagCatalog.TEAM_APP_COUNT) }.getOrDefault(false) && c.shareApi.enabled
        val file = File(c.appContext.noBackupFilesDir, FILE)
        val sent = read(file).toMutableMap()
        if (!on && sent.isEmpty()) return@withLock
        val wanted = if (on) c.received.calendars.value.filter(::candidate).associateBy { it.id } else emptyMap()
        var changed = false
        for ((id, s) in sent.toMap()) {
            if (id in wanted) continue
            try {
                c.shareApi.appUnmark(id, s.mark)
                sent.remove(id); changed = true
            } catch (e: ShareLinkException) {
                // A link the server no longer has took the mark along.
                if (e.message == "not_found" || e.message == "bad_mark") { sent.remove(id); changed = true }
            } catch (_: IOException) {
            }
        }
        for ((id, cal) in wanted) {
            val s = sent[id]
            if (s != null && s.until == cal.expiresAt) continue
            val mark = s?.mark ?: word()
            try {
                c.shareApi.appMark(id, mark)
                sent[id] = Sent(mark, cal.expiresAt); changed = true
            } catch (_: ShareLinkException) {
                // not_found: the leader no longer asks, or the server's 3.14 day is not here yet; the next look asks again.
            } catch (_: IOException) {
            }
        }
        if (changed) write(file, sent)
    }

    private fun word(): String {
        val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    internal fun read(file: File): Map<String, Sent> = runCatching {
        if (!file.exists()) return emptyMap()
        val o = JSONObject(file.readText())
        o.keys().asSequence().mapNotNull { id ->
            val e = o.optJSONObject(id) ?: return@mapNotNull null
            val mark = e.optString("mark").takeIf { it.length >= 16 } ?: return@mapNotNull null
            id to Sent(mark, e.optString("until").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun write(file: File, sent: Map<String, Sent>) {
        runCatching {
            if (sent.isEmpty()) { file.delete(); return }
            file.parentFile?.mkdirs()
            file.writeText(JSONObject().apply {
                sent.forEach { (id, s) -> put(id, JSONObject().put("mark", s.mark).apply { s.until?.let { put("until", it.toString()) } }) }
            }.toString())
        }
    }
}
