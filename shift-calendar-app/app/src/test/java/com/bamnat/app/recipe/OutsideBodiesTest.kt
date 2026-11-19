package com.bamnat.app.recipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.screenshots.MarketWorks
import com.bamnat.app.screenshots.ScreenshotBase
import com.bamnat.app.share.ShareAudience
import com.bamnat.app.share.ShareContent
import com.bamnat.app.share.ShareOptions
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2.0.0 [RVR-22] (검토 SD-09, 스튜디오 제작 오류 점검 S7-2) 바깥 길 시험의 앱 쪽: 장터 작품을 시험 폰(화면 사진과 같은 교대 근무자)에
 * 하나씩 넣고, 그 상태로 앱이 바깥으로 보내는 몸통을 앱의 코드 그대로 만들어 build/outside-bodies/에 적어요. 세 서버의 검사기가
 * 그 몸통을 받아 주는지는 서버 시험이 봐요(share-worker·company-worker test/outside-bodies.test.mjs). PC 통합 2차 Q8(링크로 보내기
 * type_icon)이 이 길이에요.
 *
 * - link·refresh: 링크 만들기와 자동 갱신(ShareSync.send)의 [ShareContent.payload], 고를 수 있는 것은 모두 켜고 깃발도 모두 연 채로
 *   ([ShareContent.Gates.ALL]: 깃발은 무엇을 보낼지만 정하고 모양은 바꾸지 않아요). 자동 갱신은 끝 날짜 없이(ttl null).
 * - company: 회사 방식 공유(광장·회사 근무표)의 CompanyShare.exportForServer.
 * - market: 넣은 작품을 내 판으로 다시 올릴 때(올리기 화면 RecipeMakeViewModel이 폰에서 읽는 pieces.export)와 스튜디오 › 내 테마 올리기·링크의
 *   테마(ThemeWardrobe.myLookRecipe, 사진 뺌).
 *
 * 늘(unit): 가장자리 작품 모두와 출시 작품 40개마다 하나. `-Pbamnat.outsideBodies=all`: 작품 전체(CI 'market' 일).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OutsideBodiesTest : ScreenshotBase() {
    private val all = System.getProperty("bamnat.outsideBodies") == "all"

    private fun line(key: String, title: String, body: String) = JSONObject().put("key", key).put("title", title).put("body", body).toString()

    @Test fun everyWorkOnThePhoneGoesOutInAShapeTheServersTake() = runBlocking {
        val works = MarketWorks.all().let { list -> if (all) list else list.filterIndexed { i, w -> w.edge || i % 40 == 0 } }
        assertTrue("docs/recipes에서 작품을 못 찾았어요", works.isNotEmpty())
        val c = app.container
        val out = File("build/outside-bodies").apply { deleteRecursively(); mkdirs() }
        val files = listOf("link", "refresh", "company", "market", "look").associateWith { StringBuilder() }
        val errors = ArrayList<String>()
        // A coworker's link: the one that may carry the company setup too.
        val options = ShareOptions(leaveKinds = true, extras = true, memos = true, pay = true, jobs = true, audience = ShareAudience.COWORKER,
            setup = true, look = true, kit = true)
        for ((n, w) in works.withIndex()) {
            var id: Long? = null
            try {
                val r = RecipeCodec.read(w.body)
                id = c.recipes.apply(r, "OUT" + n.toString().padStart(5, '0'), 1, PersonalCalendar.MAIN,
                    r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())
                val snapshot = c.calendars.repository(PersonalCalendar.MAIN).snapshot.first()
                val from = today.withDayOfMonth(1)
                val link = ShareContent.payload(c, snapshot, from, "민지", 30, options, PersonalCalendar.MAIN, gates = ShareContent.Gates.ALL)
                files.getValue("link").appendLine(line(w.key, w.title, link.toString()))
                val refresh = ShareContent.payload(c, snapshot, from, "민지", null, options, PersonalCalendar.MAIN, gates = ShareContent.Gates.ALL)
                files.getValue("refresh").appendLine(line(w.key, w.title, refresh.toString()))
                runCatching { c.companyOf(PersonalCalendar.MAIN).share.exportForServer() }.getOrNull()?.let {
                    files.getValue("company").appendLine(line(w.key, w.title, it.toString()))
                }
                // The work as worn, sent again as mine: each kind it has, read back from the phone.
                val mine = r.pieces.mapNotNull { p ->
                    runCatching { c.recipes.pieces.export(p.kind, PersonalCalendar.MAIN) }.getOrNull()?.filterValues { it != null }
                        ?.takeIf { it.isNotEmpty() }?.let { Piece(p.kind, it) }
                }
                if (mine.isNotEmpty()) {
                    val again = Recipe("${w.title.take(24)} · 내 판", r.summary, r.desc, r.styles, pieces = mine)
                    files.getValue("market").appendLine(line(w.key, w.title, RecipeCodec.write(RecipeCodec.read(RecipeCodec.write(again)))))
                }
                val look = LookParts.withoutPhotos(c.wardrobe.myLookRecipe("내가 꾸민 테마"))
                if (look.pieces.isNotEmpty()) files.getValue("look").appendLine(line(w.key, w.title, RecipeCodec.write(look)))
            } catch (e: Throwable) {
                errors += "${w.key} ${w.title}: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            } finally {
                id?.let { runCatching { c.recipes.undo(it) } }
            }
        }
        for ((name, text) in files) File(out, "$name.jsonl").writeText(text.toString())
        File(out, "works.json").writeText(JSONObject().put("all", all).put("count", works.size).toString())
        assertTrue("몸통을 만들다 넘어진 작품 ${errors.size}개:\n" + errors.take(20).joinToString("\n"), errors.isEmpty())
        // Every work leaves a link and a refresh body; a sample without them would check nothing.
        assertTrue(files.getValue("link").lines().count { it.isNotBlank() } == works.size)
    }
}
