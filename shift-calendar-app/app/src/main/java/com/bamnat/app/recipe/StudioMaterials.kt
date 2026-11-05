package com.bamnat.app.recipe

import android.content.Context
import com.bamnat.app.AppContainer
import org.json.JSONObject

/**
 * 1.38 [STM-06] 밤낮 재료: 운영자가 이 저장소의 코드로 직접 그린 색 아이콘·배경(tools/materials). 그림은 작품 그림과 같은 곳
 * (/api/recipes/images/:id)에 있어서, 재료를 쓴 작품은 받는 분 휴대폰에서도 그대로 보여요.
 */
data class StudioMaterial(
    val id: String,
    val kind: String,
    val pack: String,
    val name: String,
    /** A small picture for the shelf (배경), or null when the picture itself is small (아이콘). */
    val thumb: String?,
    val w: Int,
    val h: Int,
    val free: Boolean,
) {
    val isIcon: Boolean get() = kind == ICON
    val aspect: Float get() = if (w > 0 && h > 0) w.toFloat() / h else 1f
    /** The picture the shelf shows. */
    val shelf: String get() = thumb ?: id

    companion object {
        const val ICON = "icon"
        const val BG = "bg"
        private val ID = Regex("^[0-9a-f]{16}$")

        fun parse(o: JSONObject): StudioMaterial? {
            val id = o.optString("id")
            val kind = o.optString("kind")
            if (!ID.matches(id) || (kind != ICON && kind != BG)) return null
            val thumb = o.optString("thumb").takeIf { ID.matches(it) }
            return StudioMaterial(id, kind, o.optString("pack"), o.optString("name"), thumb, o.optInt("w"), o.optInt("h"), o.optBoolean("free", true))
        }
    }
}

object StudioMaterials {
    /** 광고 한 편이 여는 한 묶음의 날 수. */
    const val PACK_DAYS = 30
    private const val DAY_MS = 86_400_000L
    private const val KEEP_MS = 10 * 60_000L
    private const val PREFS = "studio_materials"

    @Volatile private var cached: Pair<Long, List<StudioMaterial>>? = null

    /** The open materials, kept for ten minutes; null when the server could not be reached. */
    suspend fun list(c: AppContainer, now: Long = com.bamnat.core.time.Today.millis()): List<StudioMaterial>? {
        cached?.let { (at, list) -> if (now - at < KEEP_MS) return list }
        val got = runCatching { c.recipeApi.materials() }.getOrNull() ?: return null
        cached = now to got
        return got
    }

    /** Free ones always; the rest for 프로, or while its 묶음 is open by an ad. */
    fun usable(m: StudioMaterial, pro: Boolean, openUntil: Long, now: Long): Boolean = m.free || pro || openUntil > now

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun openUntil(context: Context, pack: String): Long = prefs(context).getLong("pack.$pack", 0L)

    fun open(context: Context, pack: String, now: Long = com.bamnat.core.time.Today.millis()) {
        prefs(context).edit().putLong("pack.$pack", now + PACK_DAYS * DAY_MS).apply()
    }

    /** A picture that came from 밤낮 재료: the server has it already, so a shared work points at it instead of sending it again. */
    fun mark(context: Context, id: String) {
        val p = prefs(context)
        val ids = p.getStringSet("ids", emptySet()).orEmpty()
        if (id !in ids) p.edit().putStringSet("ids", ids + id).apply()
    }

    fun isMaterial(context: Context, id: String): Boolean = id in prefs(context).getStringSet("ids", emptySet()).orEmpty()
}
