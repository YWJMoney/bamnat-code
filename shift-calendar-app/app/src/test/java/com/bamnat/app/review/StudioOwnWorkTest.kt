package com.bamnat.app.review

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.Sent
import com.bamnat.app.ui.studio.StudioViewModel
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * [RVF-04][RVF-05] 개발 전 검토: 장터에 올린 내 테마 '봄'을 입고 있을 때,
 * STU-01 고쳐 만들기·이어서 하기로 연 다른 초안이 '봄'의 다음 판으로 올라가거나 저장으로 '봄'을 덮어썼어요.
 * STU-25 '봄'을 고쳐 저장하면(장터 판도 바꾸기) 장터 작품의 소개·설명·표지·맞는 유형·기능 조각이 기본값으로 바뀌었어요.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StudioOwnWorkTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private lateinit var before: RecipeApi
    private val sent = mutableListOf<Recipe>()

    /** '봄' as it is in 장터: words, cover, two kinds of work and a 기능 piece, all the maker's. */
    private val live = Recipe("봄", "벚꽃색 토요일과 인수인계 틀", "교대 근무자를 위한 봄 테마예요", setOf(WorkStyle.SHIFT, WorkStyle.DAY),
        cover = Cover(0xFFF8BBD0L, 0xFFF48FB1L, 1, "🌸"),
        pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFFF06292L)),
            Piece(PieceKind.MEMO_TEMPLATES, mapOf("items" to listOf(mapOf("title" to "인수인계", "check" to listOf("약품")))))))

    @Before fun start() {
        before = c.recipeApi
        c.recipeApi = object : RecipeApi by before {
            override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc =
                RecipeDoc(RecipeItem(code, live.title, live.summary, live.styles, live.cover, null, 1, 0, 0, 0, null, false, false, null, Instant.EPOCH),
                    RecipeCodec.hash(live), RecipeCodec.write(live), "public", mine = true)
            override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?): Sent {
                sent += recipe
                return Sent(code, 2, RecipeCodec.hash(recipe), "shown", "public")
            }
        }
        val mine = Recipe("봄", "내가 꾸민 모양", "", WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFFF06292L))))
        assertTrue(c.wardrobe.saveLook(mine))
        c.wardrobe.markShared("own:봄", "SPRNG234", 1, false)
        runBlocking { assertTrue(c.wardrobe.wearAgain(c.wardrobe.lease.value.history.first { it.key == "own:봄" })) }
    }

    @After fun end() { c.recipeApi = before }

    private fun settle(until: () -> Boolean = { false }) {
        repeat(30) { idle(); if (until()) return; Thread.sleep(50) }
        idle()
    }

    @Test fun aDraftOpenedAgainIsNotTheNextVersionOfTheThemeWorn() {
        val vm = StudioViewModel(c)
        settle()
        assertEquals("지금 모양에서 고치면 '봄'의 다음 판", "SPRNG234", vm.nextOf())
        val other = Recipe("여름", "", "", setOf(WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.CAL_THEME, mapOf("sat" to 0xFF00897BL))))
        vm.startRecipe(RecipeCodec.write(other), null, null)
        settle()
        assertNull("이어서 하기·고쳐 만들기로 연 초안은 새 작품", vm.nextOf())
        assertFalse(vm.inPlaza())
        assertNull(vm.ownStart)
    }

    @Test fun savingTheThemeKeepsItsWorkInTheMarket() {
        val vm = StudioViewModel(c)
        settle()
        vm.change { it.copy(calTheme = "sat=FFD32F2F") }
        settle()
        vm.save("봄", toPlaza = true)
        settle { sent.isNotEmpty() }
        val r = sent.singleOrNull()
        assertNotNull("장터 판을 보냈어요", r)
        r!!
        assertEquals(live.summary, r.summary)
        assertEquals(live.desc, r.desc)
        assertEquals(live.cover, r.cover)
        assertEquals("다른 유형 목록에서 빠지지 않아요", live.styles, r.styles)
        assertTrue("기능 조각도 그대로", r.pieces.any { it.kind == PieceKind.MEMO_TEMPLATES })
        assertEquals("모양만 고친 것으로", 0xFFD32F2FL, (r.pieces.first { it.kind == PieceKind.CAL_THEME }.values["sat"] as Number).toLong())
    }
}
