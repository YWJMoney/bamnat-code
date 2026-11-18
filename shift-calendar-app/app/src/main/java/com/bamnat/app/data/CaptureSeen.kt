package com.bamnat.app.data

import com.bamnat.core.time.Today
import org.json.JSONObject
import java.io.File

/**
 * 2.0.0 [RVK-14] (검토 MONEY-43·62): which notification messages were read already, and which app's text each line written from
 * one came from. The same message posted again (a chat app's stacked notification brings its earlier messages back each time)
 * is read once, while a second payment of the same amount on the same card (코인노래방 1,000원 두 번, 같은 값 커피 두 잔) or a
 * second fill at the same price (50주 + 50주) from the same app is a line of its own.
 *
 * On this phone only, in the no-backup folder, as short hashes (never the text), for [KEEP_MILLIS].
 */
class CaptureSeen(private val file: File?, private val clock: () -> Long = Today::millis) {
    private val seen = LinkedHashMap<String, Long>()
    private val lines = LinkedHashMap<String, Pair<String, Long>>()

    init { read() }

    /**
     * True the first time this message is read: [origin] tells one notification message from another (the message's own time in a
     * chat stack, the notification's key and time otherwise), so only the very same message again is false.
     */
    @Synchronized
    fun firstTime(origin: String, text: String): Boolean {
        drop()
        val key = hash("$origin\n$text")
        if (key in seen) return false
        seen[key] = clock()
        write()
        return true
    }

    /** The line [line] (its [lineKey]) was written from [app]'s [text]. */
    @Synchronized
    fun wrote(line: String, app: String, text: String) {
        lines[line] = "$app\n${hash(text)}" to clock()
        write()
    }

    /** True when the line [line] was written from a different text of the same [app]: then a new text is another payment. */
    @Synchronized
    fun sameAppOtherText(line: String, app: String, text: String): Boolean {
        val (from, _) = lines[line] ?: return false
        val (fromApp, fromHash) = from.split('\n', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return fromApp == app && fromHash != hash(text)
    }

    private fun drop() {
        val now = clock()
        seen.entries.removeAll { now - it.value > KEEP_MILLIS }
        lines.entries.removeAll { now - it.value.second > KEEP_MILLIS }
        while (seen.size > MAX) seen.remove(seen.keys.first())
        while (lines.size > MAX) lines.remove(lines.keys.first())
    }

    private fun read() {
        val f = file?.takeIf { it.exists() } ?: return
        runCatching {
            val o = JSONObject(f.readText())
            o.optJSONObject("seen")?.let { s -> s.keys().forEach { k -> seen[k] = s.optLong(k) } }
            o.optJSONObject("lines")?.let { s ->
                s.keys().forEach { k -> s.optJSONObject(k)?.let { v -> lines[k] = v.optString("from") to v.optLong("at") } }
            }
        }
        drop()
    }

    private fun write() {
        val f = file ?: return
        drop()
        val o = JSONObject()
            .put("seen", JSONObject().apply { seen.forEach { (k, v) -> put(k, v) } })
            .put("lines", JSONObject().apply { lines.forEach { (k, v) -> put(k, JSONObject().put("from", v.first).put("at", v.second)) } })
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.writeText(o.toString()); tmp.delete() }
        }
    }

    companion object {
        /** A stacked chat notification brings back its messages for 10 minutes; two days covers a phone restarted in between. */
        const val KEEP_MILLIS = 2 * 24 * 60 * 60 * 1000L
        const val MAX = 400

        /** 64-bit FNV-1a, as hex: the text itself is never kept. */
        fun hash(s: String): String {
            var h = -0x340d631b7bdddcdbL
            for (c in s) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return java.lang.Long.toHexString(h)
        }

        /** The key of a line written from a text: its day, kind, amount, time, card and place. */
        fun lineKey(e: com.bamnat.core.money.MoneyEntry): String = listOf(
            e.date, e.kind, kotlin.math.abs(e.amount), e.time, e.cardLast4, com.bamnat.core.money.PaymentParser.placeKey(e.merchant),
        ).joinToString("|")
    }
}
