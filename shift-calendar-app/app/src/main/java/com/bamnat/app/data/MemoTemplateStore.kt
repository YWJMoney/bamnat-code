package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.memo.MemoTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * [RCP-11] 1.34 메모 틀, kept in the preferences file "memo_templates" (in the backup, [SettingsFilesBackup.PARTS]). At most
 * [MemoTemplate.MAX]; a recipe's templates carry its code so 되돌리기 takes exactly those away.
 */
class MemoTemplateStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<MemoTemplate>> = _items.asStateFlow()

    fun reload() { _items.value = read() }

    @Synchronized
    fun save(list: List<MemoTemplate>) {
        val kept = list.take(MemoTemplate.MAX)
        prefs.edit().putString(KEY, encode(kept).toString()).commit()
        _items.value = kept
    }

    fun add(t: MemoTemplate) = save(_items.value.filter { it.id != t.id } + t)
    fun remove(id: String) = save(_items.value.filter { it.id != id })

    private fun read(): List<MemoTemplate> = runCatching { decode(JSONArray(prefs.getString(KEY, "[]"))) }.getOrDefault(emptyList())

    companion object {
        const val FILE = "memo_templates"
        const val KEY = "items"

        fun encode(list: List<MemoTemplate>): JSONArray = JSONArray().apply {
            list.forEach { t ->
                put(JSONObject().put("id", t.id).put("title", t.title).put("emoji", t.emoji ?: JSONObject.NULL)
                    .put("check", JSONArray(t.check)).put("body", t.body).put("repeat", t.repeat).put("from", t.from ?: JSONObject.NULL))
            }
        }

        fun decode(a: JSONArray): List<MemoTemplate> = (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val check = o.optJSONArray("check")?.let { c -> (0 until c.length()).map { c.optString(it) } }.orEmpty()
            MemoTemplate(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("title"),
                o.optString("emoji").takeIf { !o.isNull("emoji") && it.isNotEmpty() }, check, o.optString("body"), o.optString("repeat"),
                o.optString("from").takeIf { !o.isNull("from") && it.isNotEmpty() })
        }
    }
}
