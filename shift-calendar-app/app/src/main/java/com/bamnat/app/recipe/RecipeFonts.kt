package com.bamnat.app.recipe

import com.bamnat.app.AppContainer
import com.bamnat.app.ui.theme.UserFonts
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [MAT-06] 1.36 내 글꼴 나누기. A recipe names a user font by its id (`u` + 16 hex of its bytes) wherever a font goes (칸 날짜, 화면,
 * 메모, 부품). On 올리기 the fonts the user marked open (OFL·Apache·무료 공개) go to the server first; the rest stay on the phone and the
 * receiver sees the stand-in. On 적용 the fonts a recipe names and the phone lacks come down and are kept in 내 글꼴.
 */
object RecipeFonts {
    private val ID = Regex("(?<![0-9A-Za-z])u[0-9a-f]{16}(?![0-9A-Za-z])")

    /** Every user-font id in [text] (a recipe's JSON or a piece's values). */
    fun idsIn(text: String): Set<String> = ID.findAll(text).map { it.value }.toSet()

    fun idsIn(r: Recipe): Set<String> = idsIn(RecipeCodec.write(r))

    /**
     * What 올리기 did with the fonts: those sent and those kept back (not open, or too big), by name. 1.36 [SEC-10] [waiting]: sent,
     * and others get them once the operator looked (a first font).
     */
    data class Sent(val shared: List<String>, val kept: List<String>, val waiting: List<String> = emptyList())

    /** Sends the open fonts [r] names that the server does not have yet. A font that fails stays a stand-in for others. */
    suspend fun send(c: AppContainer, r: Recipe, license: String = "ofl"): Sent {
        val ids = idsIn(r)
        if (ids.isEmpty()) return Sent(emptyList(), emptyList())
        val mine = UserFonts.list(c.appContext).filter { it.id in ids }
        val shared = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val waiting = mutableListOf<String>()
        for (m in mine) {
            if (!m.open || m.bytes > RecipeClient.FONT_MAX) { kept += m.name; continue }
            if (UserFonts.sent(c.appContext, m.id)) { shared += m.name; continue }
            val sent = runCatching {
                val bytes = withContext(Dispatchers.IO) { UserFonts.file(c.appContext, m.id)!!.readBytes() }
                c.recipeApi.fontSend(c.companyStore.device, m.id, m.name, license, bytes).takeIf { it.id == m.id }
            }.getOrNull()
            if (sent != null) { UserFonts.markSent(c.appContext, m.id); shared += m.name; if (sent.waiting) waiting += m.name } else kept += m.name
        }
        return Sent(shared, kept, waiting)
    }

    /** A line for 올리기's end when some fonts stay on this phone; null when every font goes along. */
    fun keptNote(s: Sent): String? = listOfNotNull(
        s.kept.takeIf { it.isNotEmpty() }?.let { names ->
            "‘${names.joinToString("’, ‘")}’ 글꼴은 공개 글꼴로 밝히지 않았거나 8MB가 넘어 함께 가지 않아요. 받는 분께는 비슷한 글꼴로 보여요. 내 글꼴에서 공개 글꼴로 밝히면 다음 판부터 함께 가요"
        },
        // 1.36 [SEC-10] 보안 점검 H3·M7: a first font is looked at before others get it.
        s.waiting.takeIf { it.isNotEmpty() }?.let { names ->
            "‘${names.joinToString("’, ‘")}’ 글꼴은 처음 나누는 글꼴이라 운영자가 라이선스를 확인한 뒤(보통 하루 안) 받는 분께 보여요. 그 전에는 비슷한 글꼴로 보여요"
        },
    ).joinToString("\n").ifBlank { null }

    /** Brings down the fonts named in [text] that this phone lacks; false for none got. */
    suspend fun fetchIn(c: AppContainer, text: String): Boolean {
        val have = UserFonts.ids(c.appContext)
        var got = false
        for (id in idsIn(text) - have) {
            if (FontProbe.failed(id)) continue
            val (name, bytes) = runCatching { c.recipeApi.fontGet(c.companyStore.device, id) }.getOrNull() ?: continue
            if (UserFonts.idOf(bytes) != id) continue
            // 1.36 [SEC-10] 보안 점검 H3: opened first in a process with no rights; only then in the app's own.
            val look = java.io.File(c.appContext.cacheDir, "font-look-$id.ttf")
            val safe = try {
                withContext(Dispatchers.IO) { look.writeBytes(bytes) }
                FontProbe.safe(c.appContext, id, look)
            } finally {
                withContext(Dispatchers.IO) { look.delete() }
            }
            if (!safe) continue
            val r = withContext(Dispatchers.IO) { UserFonts.addBytes(c.appContext, bytes, name, open = true, received = true) }
            if (r is UserFonts.Result.Added) got = true
        }
        return got
    }
}
