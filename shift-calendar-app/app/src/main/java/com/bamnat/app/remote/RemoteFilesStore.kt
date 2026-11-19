package com.bamnat.app.remote

import com.bamnat.core.money.TradeWages
import com.bamnat.core.pay.RateTable
import com.bamnat.core.pay.WithholdingTable
import com.bamnat.core.remote.PresetBook
import com.bamnat.core.remote.RemoteFile
import com.bamnat.core.remote.RemoteFileException
import com.bamnat.core.remote.RemoteFiles
import com.bamnat.core.remote.RemoteKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import com.bamnat.core.time.Today

/** [REMOTE-02] Reads one published file; null when it is not there or the phone is offline. */
fun interface RemoteFetcher {
    suspend fun get(path: String): ByteArray?

    /**
     * 2.0.0 [RVL-67] (검토 DATA-24) The same, asking with the [etag] the phone kept from the last time: when the site says the file
     * has not changed ([Fetched.unchanged]) nothing comes down, neither the file nor its signatures. A fetcher that cannot ask
     * (the tests' fakes) downloads as before.
     */
    suspend fun fetch(path: String, etag: String?): Fetched = Fetched(get(path), null)
}

/** [RVL-67] A file as it came (null: not there or offline), its ETag, or [unchanged] since [RemoteFetcher.fetch]'s etag. */
class Fetched(val bytes: ByteArray?, val etag: String?, val unchanged: Boolean = false)

/** [REMOTE-02] GitHub Pages (`bamnat.remoteBaseUrl`): plain GET, short timeouts, at most 1 MB. */
class HttpRemoteFetcher(private val baseUrl: String) : RemoteFetcher {
    override suspend fun get(path: String): ByteArray? = fetch(path, null).bytes

    override suspend fun fetch(path: String, etag: String?): Fetched = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext Fetched(null, null)
        try {
            val conn = URL(baseUrl.trimEnd('/') + "/" + path).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            // [RVL-67] GitHub Pages answers 304 with no body when the file still has this ETag.
            if (!etag.isNullOrBlank()) conn.setRequestProperty("If-None-Match", etag)
            try {
                if (!etag.isNullOrBlank() && conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return@withContext Fetched(null, etag, unchanged = true)
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext Fetched(null, null)
                val tag = conn.getHeaderField("ETag")?.takeIf { it.length in 1..200 && it.none { c -> c == '\n' || c == '\r' || c == '\t' } }
                conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8_192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) return@withContext Fetched(null, null)
                    }
                    Fetched(out.toByteArray(), tag)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Fetched(null, null)
        }
    }

    companion object {
        const val MAX_BYTES = 1_000_000
    }
}

/**
 * [REMOTE-02] The signed remote files (결정 D3, `remote/v1/` on GitHub Pages): asks once a day, keeps a file
 * only when its Ed25519 signature checks out, it is the kind asked for, this app can read it and it is newer
 * than what the phone has; then lays it over the built-in data ([apply]). Saved copies are checked again when
 * the app starts, so a phone offline for months keeps the last good files, and with nothing saved it uses
 * the files the app shipped with. Nothing about the phone or the user is sent: it is a plain download.
 */
class RemoteFilesStore(
    private val dir: File,
    private val fetcher: RemoteFetcher,
    private val appVersion: Int,
    private val keys: List<String> = RemoteKeys.TRUSTED,
) {
    private val stamp = File(dir, "fetched_at")

    /** The version in use per kind (the shipped file's until a newer one is saved). */
    fun version(kind: String): Long = saved(kind)?.version ?: RemoteFiles.bundled(kind)?.version ?: 0L

    private fun saved(kind: String): RemoteFile? = runCatching {
        val json = File(dir, "$kind.json")
        val sig = File(dir, "$kind.json.sig")
        if (!json.exists() || !sig.exists()) return null
        val bytes = json.readBytes()
        val signature = sig.readText()
        // [PERF-19] 1.28 AO (검토 6-1 병목 3): this ran the Ed25519 check (BigInteger arithmetic, up to three keys) for
        // every saved file on every start, inside the container the first screen waits for. The check is now
        // run once per file, signature, key list and app version; the mark beside the file says it passed.
        val mark = RemoteFiles.checkedMark(bytes, signature, keys, appVersion)
        val ok = File(dir, "$kind.json.ok")
        if (runCatching { ok.readText().trim() }.getOrNull() == mark) return RemoteFiles.openChecked(bytes, kind, appVersion)
        RemoteFiles.open(bytes, signature, kind, appVersion, keys).also { runCatching { write(ok, mark.toByteArray(Charsets.US_ASCII)) } }
    }.getOrNull()

    /** Lays every saved file over the built-in data; call once when the app starts. */
    fun applySaved() {
        for (kind in RemoteFiles.KINDS) saved(kind)?.let { runCatching { apply(it) } }
    }

    fun due(now: Instant): Boolean {
        val at = runCatching { Instant.parse(stamp.readText().trim()) }.getOrNull() ?: return true
        return now.isBefore(at) || Duration.between(at, now) >= EVERY
    }

    /** Asks for each file when due; returns the kinds that changed. A bad or missing file changes nothing. */
    suspend fun refresh(now: Instant = Today.instant(), force: Boolean = false): List<String> {
        if (!force && !due(now)) return emptyList()
        val changed = mutableListOf<String>()
        var reached = false
        val tags = etags()
        for (kind in RemoteFiles.KINDS) {
            // 2.0.0 [RVL-67] (검토 DATA-24) Asked with the ETag of the copy last settled: unchanged, nothing more is fetched.
            val got = fetcher.fetch(RemoteFiles.path(kind), tags[kind].takeIf { !force })
            if (got.unchanged) { reached = true; continue }
            val bytes = got.bytes ?: continue
            reached = true
            // [REMOTE-08] .sig2 (2026-10-26 key) first, then the old .sig; the first that checks out wins.
            var sig: String? = null
            var file: RemoteFile? = null
            for (ext in RemoteKeys.SIGNATURES) {
                val s = fetcher.get(RemoteFiles.path(kind) + ext)?.toString(Charsets.US_ASCII) ?: continue
                file = try { RemoteFiles.open(bytes, s, kind, appVersion, keys) } catch (e: RemoteFileException) { continue }
                sig = s
                break
            }
            if (file == null || sig == null) continue
            // [RVL-67] Settled: a file not newer than the phone's is not asked for again until it changes. A signature that did not
            // check out keeps no ETag, so a file signed again (the same bytes, a new .sig2) is fetched again.
            if (file.version <= version(kind)) { got.etag?.let { tags[kind] = it }; continue }
            try {
                apply(file)
            } catch (e: RemoteFileException) {
                continue
            }
            dir.mkdirs()
            write(File(dir, "$kind.json.sig"), sig.toByteArray(Charsets.US_ASCII))
            write(File(dir, "$kind.json"), bytes)
            // [PERF-19] Checked just now: the next start need not check it again.
            runCatching { write(File(dir, "$kind.json.ok"), RemoteFiles.checkedMark(bytes, sig, keys, appVersion).toByteArray(Charsets.US_ASCII)) }
            changed += kind
            got.etag?.let { tags[kind] = it }
        }
        if (reached) runCatching { dir.mkdirs(); stamp.writeText(now.toString()); keepEtags(tags) }
        return changed
    }

    /** [RVL-67] The ETag of each kind's copy last settled, one "kind<TAB>etag" a line; an unreadable list is an empty one. */
    private val etagFile = File(dir, "etags")

    private fun etags(): MutableMap<String, String> = runCatching {
        etagFile.readLines().mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 && it[0] in RemoteFiles.KINDS }?.let { it[0] to it[1] } }
            .toMap(LinkedHashMap())
    }.getOrDefault(LinkedHashMap())

    private fun keepEtags(tags: Map<String, String>) {
        write(etagFile, tags.entries.joinToString("\n") { "${it.key}\t${it.value}" }.toByteArray(Charsets.UTF_8))
    }

    private fun write(f: File, b: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(b)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        val EVERY: Duration = Duration.ofDays(1)

        /**
         * 2.0.0 [RVH-14] 검토 CAL-19: [com.bamnat.core.holiday.KoreanHolidays.revision] as a flow. Every calendar's snapshot carries it, so a
         * 임시공휴일 that comes while the app is open redraws the open month and plans the alarms again (a calendar resting on 공휴일).
         */
        val holidays = kotlinx.coroutines.flow.MutableStateFlow(com.bamnat.core.holiday.KoreanHolidays.revision)

        /** Hands a checked file to the part of core that uses it. */
        fun apply(f: RemoteFile) {
            when (f.kind) {
                RemoteFiles.PRESETS -> PresetBook.use(f)
                RemoteFiles.RATES -> RateTable.use(f)
                RemoteFiles.WAGES -> TradeWages.use(f)
                RemoteFiles.TAXTABLE -> WithholdingTable.use(f)
                RemoteFiles.PUBLICPAY -> com.bamnat.core.pay.PublicPayTable.use(f)
                RemoteFiles.HOLIDAYS -> { com.bamnat.core.holiday.KoreanHolidays.use(f); holidays.value = com.bamnat.core.holiday.KoreanHolidays.revision }
                RemoteFiles.TAXES -> com.bamnat.core.invest.InvestTaxes.use(f)
                RemoteFiles.ALARM_GUIDE -> com.bamnat.core.remote.AlarmGuides.use(f)
                RemoteFiles.CYCLES -> com.bamnat.core.plaza.PublicCycles.use(f)
                RemoteFiles.MERCHANTS -> com.bamnat.core.money.MerchantNames.use(f) // 1.32 [LEDX-24]
            }
        }
    }
}
