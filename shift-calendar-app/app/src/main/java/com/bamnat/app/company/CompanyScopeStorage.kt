package com.bamnat.app.company

import android.content.SharedPreferences
import com.bamnat.app.data.CalendarStorage
import com.bamnat.app.data.PersonalCalendar
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * [CALI-01] 1.30 BB: where an added calendar keeps its company connection, beside its pay setup in its own folder
 * ([CalendarStorage.filesOf]): `company.json` (what [CompanyStore] keeps) and `pay_way.json` ([PayWayStore]). The main
 * calendar keeps the preferences files it always had (`company`, `pay_way`), so a phone from before 1.30 reads them as
 * they are, nothing moved. A file goes with its calendar: a backup carries it in the calendar's part, deleting the
 * calendar deletes it, and a calendar a restore brings back keeps it ([KEPT]).
 */
object CompanyScopeStorage {
    const val COMPANY = "company.json"
    const val PAY_WAY = "pay_way.json"

    /** Kept when a restore takes a calendar away and brings it back ([CalendarStorage.Files.delete] with keepTools). */
    val KEPT = setOf(COMPANY, PAY_WAY)

    private val open = ConcurrentHashMap<String, JsonPrefs>()

    /** One instance per file, so a restore writing it and the store reading it see the same values. */
    fun prefs(context: android.content.Context, calendarId: String, name: String): SharedPreferences {
        require(calendarId != PersonalCalendar.MAIN) { "The main calendar keeps its preferences files" }
        val file = File(CalendarStorage.filesOf(context, calendarId), name)
        return open.getOrPut(file.absolutePath) { JsonPrefs(file) }
    }

    /**
     * [CALI-07] The calendar went: its files stop being written (a write still queued is dropped, so the folder being
     * deleted does not come back) and the next [prefs] reads afresh, e.g. when 최근 지운 기록 puts it back.
     */
    fun forget(context: android.content.Context, calendarId: String) {
        if (calendarId == PersonalCalendar.MAIN) return
        val dir = CalendarStorage.filesOf(context, calendarId).absolutePath
        for (name in KEPT) open.remove(File(dir, name).absolutePath)?.close()
    }

    /** The calendar id as the company server takes it for `cal` (1~24 of A-Z a-z 0-9 _ -); the main one is "main". */
    fun serverCal(calendarId: String): String {
        if (calendarId == PersonalCalendar.MAIN) return AutoShare.CAL
        val clean = calendarId.filter { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }.take(24)
        return clean.ifEmpty { AutoShare.CAL }
    }

    /** All writes of these small files, one after another off the caller's thread (as SharedPreferences.apply does). */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "company-scope-files").apply { isDaemon = true } }

    /**
     * [CALI-01] SharedPreferences over one small JSON file, so [CompanyStore] and [PayWayStore] keep the same code for the
     * main calendar's preferences and an added calendar's file. Read once when first asked; [SharedPreferences.Editor.apply]
     * changes the values at once and writes the file in the background, [SharedPreferences.Editor.commit] writes it now.
     */
    internal class JsonPrefs(private val file: File) : SharedPreferences {
        private val lock = Any()
        private val values: MutableMap<String, Any?> = read()
        @Volatile private var closed = false
        /** Bumped on each change; a queued write only goes when it is still the newest. */
        private var version = 0L

        private fun read(): MutableMap<String, Any?> = runCatching {
            if (!file.exists()) return@runCatching HashMap<String, Any?>()
            val o = JSONObject(file.readText())
            val out = HashMap<String, Any?>()
            for (k in o.keys()) decode(o.optJSONObject(k))?.let { out[k] = it }
            out
        }.getOrDefault(HashMap())

        fun close() = synchronized(lock) { closed = true }

        override fun getAll(): MutableMap<String, *> = synchronized(lock) { HashMap(values) }
        override fun getString(key: String, defValue: String?): String? = synchronized(lock) { values[key] as? String ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(lock) { (values[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { (values[key] as? Int) ?: defValue }
        override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { (values[key] as? Long) ?: defValue }
        override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) { (values[key] as? Float) ?: defValue }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { (values[key] as? Boolean) ?: defValue }
        override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }
        override fun edit(): SharedPreferences.Editor = Editor()
        /** Nothing here listens: the stores keep their own flows. */
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val puts = LinkedHashMap<String, Any?>()
            private val removes = HashSet<String>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = also { if (value == null) remove(key) else puts.put(key, value) }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                also { if (values == null) remove(key) else puts.put(key, values.toSet()) }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = also { puts[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = also { puts[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = also { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = also { puts[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = also { puts.remove(key); removes.add(key) }
            override fun clear(): SharedPreferences.Editor = also { clear = true }

            /** The change in memory; the snapshot to write and its version. */
            private fun change(): Pair<JSONObject, Long>? = synchronized(lock) {
                if (closed) return null
                if (clear) values.clear()
                removes.forEach { values.remove(it) }
                values.putAll(puts)
                version++
                encode(values) to version
            }

            override fun commit(): Boolean {
                val (o, v) = change() ?: return false
                return write(o, v)
            }

            override fun apply() {
                val (o, v) = change() ?: return
                writer.execute { write(o, v) }
            }
        }

        private fun write(o: JSONObject, v: Long): Boolean = synchronized(lock) {
            if (closed || v != version) return@synchronized !closed
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(o.toString())
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }.isSuccess
        }

        private fun encode(map: Map<String, Any?>): JSONObject = JSONObject().apply {
            for ((k, v) in map) when (v) {
                is String -> put(k, JSONObject().put("s", v))
                is Boolean -> put(k, JSONObject().put("b", v))
                is Int -> put(k, JSONObject().put("i", v))
                is Long -> put(k, JSONObject().put("l", v))
                is Float -> put(k, JSONObject().put("f", v.toDouble()))
                is Set<*> -> put(k, JSONObject().put("ss", JSONArray(v.filterIsInstance<String>().sorted())))
            }
        }

        private fun decode(v: JSONObject?): Any? = when {
            v == null -> null
            v.has("s") -> v.getString("s")
            v.has("b") -> v.getBoolean("b")
            v.has("i") -> v.getInt("i")
            v.has("l") -> v.getLong("l")
            v.has("f") -> v.getDouble("f").toFloat()
            v.has("ss") -> v.getJSONArray("ss").let { a -> (0 until a.length()).map(a::getString).toSet() }
            else -> null
        }
    }
}
