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
    data class Sent(val shared: List<String>, val kept: List<String>, val waiting: List<String> = emptyList(), val sold: List<String> = emptyList())

    /** Sends the open fonts [r] names that the server does not have yet. A font that fails stays a stand-in for others. */
    suspend fun send(c: AppContainer, r: Recipe, license: String = "ofl"): Sent {
        val ids = idsIn(r)
        if (ids.isEmpty()) return Sent(emptyList(), emptyList())
        val mine = UserFonts.list(c.appContext).filter { it.id in ids }
        val shared = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val waiting = mutableListOf<String>()
        val sold = mutableListOf<String>()
        for (m in mine) {
            // PC 확인 6차: a paid foundry's font is told as such (it was told 'not open or over 8MB' though marked open).
            if (m.sold) { sold += m.name; continue }
            if (!m.open || m.bytes > RecipeClient.FONT_MAX) { kept += m.name; continue }
            if (UserFonts.sent(c.appContext, m.id)) { shared += m.name; continue }
            val sent = runCatching {
                val bytes = withContext(Dispatchers.IO) { UserFonts.file(c.appContext, m.id)!!.readBytes() }
                c.recipeApi.fontSend(c.companyStore.device, m.id, m.name, license, bytes, m.fallback).takeIf { it.id == m.id }
            }
            val e = sent.exceptionOrNull() as? com.bamnat.app.company.CompanyException
            when {
                sent.getOrNull() != null -> { UserFonts.markSent(c.appContext, m.id); shared += m.name; if (sent.getOrNull()!!.waiting) waiting += m.name }
                e?.code == "sold" -> { withContext(Dispatchers.IO) { UserFonts.markSold(c.appContext, m.id) }; sold += m.name }
                else -> kept += m.name
            }
        }
        return Sent(shared, kept, waiting, sold)
    }

    /**
     * PC 확인 7차 (사용성 제안 "올리기 전에 유료라 함께 안 가는 글꼴을 미리"): before 올리기, a line for each font [r] names that will not go
     * along (a paid foundry's, not marked open, over 8MB), and one for those that wait for the operator. Read off the main thread.
     */
    fun before(c: android.content.Context, r: Recipe): List<String> {
        val ids = idsIn(r)
        if (ids.isEmpty()) return emptyList()
        val mine = UserFonts.list(c).filter { it.id in ids && !it.received }
        val q = { names: List<String> -> "‘${names.joinToString("’, ‘")}’ 글꼴은" }
        return listOfNotNull(
            mine.filter { it.sold }.map { it.name }.takeIf { it.isNotEmpty() }?.let { "${q(it)} 유료 글꼴이라 함께 가지 않아요. 받는 분께는 비슷한 글꼴로 보여요" },
            mine.filter { !it.sold && !it.open }.map { it.name }.takeIf { it.isNotEmpty() }?.let { "${q(it)} 공개 글꼴로 밝히지 않아 함께 가지 않아요. 글꼴 고르기 › 내 글꼴에서 밝힐 수 있어요" },
            mine.filter { !it.sold && it.open && it.bytes > RecipeClient.FONT_MAX }.map { it.name }.takeIf { it.isNotEmpty() }?.let { "${q(it)} 8MB가 넘어 함께 가지 않아요" },
            mine.filter { !it.sold && it.open && it.bytes <= RecipeClient.FONT_MAX && !UserFonts.sent(c, it.id) }.map { it.name }.takeIf { it.isNotEmpty() }
                ?.let { "${q(it)} 함께 가고, 운영자가 확인한 뒤(보통 하루 안) 받는 분께 보여요" },
        )
    }

    /** A line for 올리기's end when some fonts stay on this phone; null when every font goes along. */
    fun keptNote(s: Sent): String? = listOfNotNull(
        s.kept.takeIf { it.isNotEmpty() }?.let { names ->
            "‘${names.joinToString("’, ‘")}’ 글꼴은 공개 글꼴로 밝히지 않았거나 8MB가 넘어 함께 가지 않아요. 받는 분께는 비슷한 글꼴로 보여요. 내 글꼴에서 공개 글꼴로 밝히면 다음 판부터 함께 가요"
        },
        s.sold.takeIf { it.isNotEmpty() }?.let { names ->
            "‘${names.joinToString("’, ‘")}’ 글꼴은 글꼴 안에 유료 글꼴 회사가 적혀 있어 나눌 수 없어요. 이 휴대폰에서는 그대로 쓰고, 받는 분께는 비슷한 글꼴로 보여요"
        },
        // 1.36 [SEC-10] 보안 점검 H3·M7·M8: a shared font is looked at before others get it.
        s.waiting.takeIf { it.isNotEmpty() }?.let { names ->
            "‘${names.joinToString("’, ‘")}’ 글꼴은 운영자가 라이선스를 확인한 뒤(보통 하루 안) 받는 분께 보여요. 그 전에는 비슷한 글꼴로 보이고, 확인되면 받는 분 휴대폰에 저절로 들어가요"
        },
    ).joinToString("\n").ifBlank { null }

    /**
     * PC 확인 6차 (사용성 제안 "나누기 뒤 받는 쪽은 테마를 다시 넣어야 바뀜"): fonts a recipe named that the server did not give yet
     * (waiting for the operator, or offline), asked again each time the app opens, for [WAIT_DAYS].
     */
    private const val WAITING = "font_waiting"
    private const val WAIT_DAYS = 14L

    private fun prefs(c: AppContainer) = c.appContext.getSharedPreferences(WAITING, android.content.Context.MODE_PRIVATE)

    fun waiting(c: AppContainer): Set<String> {
        val p = prefs(c)
        val cut = com.bamnat.core.time.Today.millis() - WAIT_DAYS * 86_400_000L
        return p.all.filter { (k, v) -> (v as? Long ?: 0L) >= cut && ID.matches(k) }.keys
    }

    /** Asks again for the fonts still waiting; those that came, for a line to the user. */
    suspend fun retryWaiting(c: AppContainer): List<UserFonts.Mine> {
        val ids = waiting(c)
        prefs(c).edit().apply { prefs(c).all.keys.filter { it !in ids }.forEach { remove(it) } }.apply()
        if (ids.isEmpty()) return emptyList()
        val before = UserFonts.ids(c.appContext)
        fetchIn(c, ids.joinToString(" "))
        return UserFonts.list(c.appContext).filter { it.id in ids && it.id !in before }
    }

    /** Brings down the fonts named in [text] that this phone lacks; false for none got. */
    suspend fun fetchIn(c: AppContainer, text: String): Boolean {
        val have = UserFonts.ids(c.appContext)
        var got = false
        for (id in idsIn(text) - have) {
            if (FontProbe.failed(id)) continue
            val (name, bytes) = runCatching { c.recipeApi.fontGet(c.companyStore.device, id) }.getOrNull()
                ?: run {
                    if (!prefs(c).contains(id)) prefs(c).edit().putLong(id, com.bamnat.core.time.Today.millis()).apply()
                    // PC 확인 9차 (기다리는 글꼴이 '내 글꼴'인데 화면은 기본 고딕): the app font its sender's phone found most like it, till it comes.
                    if (UserFonts.standIn(c.appContext, id) == null) runCatching { c.recipeApi.fontStandIn(id) }.getOrNull()?.let { fb ->
                        UserFonts.setStandIn(c.appContext, id, fb)
                        withContext(Dispatchers.Main) {
                            com.bamnat.app.ui.theme.AppFonts.forget(id)
                            com.bamnat.app.ui.theme.AppFonts.loads[id] = com.bamnat.app.ui.theme.AppFonts.Load.WAITING
                        }
                    }
                    null
                } ?: continue
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
            if (r is UserFonts.Result.Added) {
                got = true
                prefs(c).edit().remove(id).apply()
                // Every text in that font redraws in it now (the screens key their font on this).
                withContext(Dispatchers.Main) { com.bamnat.app.ui.theme.AppFonts.loads[id] = com.bamnat.app.ui.theme.AppFonts.Load.READY }
            }
        }
        return got
    }
}
