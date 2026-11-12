package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.ui.studio.PartsTool
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 S 2묶음의 앞·뒤 사진 (디자인 품질 조건 12, `build/screenshots/s2/`): each changed studio screen in the four looks that see
 * every pair of 밝은·어두운 × 글자 100·200% × 접은·편 폭 once. What each must show is checked after the shot, so the same test run on
 * the branch before gives the 앞 사진 too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class S2ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val looks = listOf(
        Triple(false, 1f, FOLDED), Triple(true, 2f, FOLDED), Triple(false, 2f, UNFOLDED), Triple(true, 1f, UNFOLDED),
    )

    private fun night(q: String) = q.replace("-port-", "-port-night-")
    private fun lookName(dark: Boolean, font: Float, q: String) =
        "${if (dark) "dark" else "light"}_${(font * 100).toInt()}_${if (q == FOLDED) "folded" else "unfolded"}"

    /** Each look once; [what] says what it saw, checked when every shot is taken. */
    private fun fourLooks(name: String, what: ScreenshotBase.Screen.() -> String?) {
        val misses = ArrayList<String>()
        for ((dark, font, q) in looks) {
            val n = "s2/${name}_${lookName(dark, font, q)}"
            shoot(n, if (dark) night(q) else q, font) { what()?.let { misses += "$n: $it" } }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    private val bigHead = Recipe("시험 머리 최대", styles = WorkStyle.entries.toSet(), pieces = listOf(Piece(PieceKind.PARTS, mapOf("parts" to listOf(
        mapOf("id" to "cal.head", "out" to 32L, "pad" to 32L, "fill" to 0xFFFFE0B2L, "r" to 24L),
        mapOf("id" to "cal.head.title", "size" to 60L),
        mapOf("id" to "cal.week", "pad" to 20L),
    )))))

    /**
     * RVS-14 (HQ-56 ③): wearing a work whose 달력 머리 pushes the first row far, the calendar draws it within 32dp, and the studio says
     * so in one line beside 작품 크기; tapping it opens 달력 머리.
     */
    @Test fun theStudioSaysTheHeadPushesTheMonth() = fourLooks("rvs14_studio_head") {
        runBlocking {
            c.recipes.apply(bigHead, "HEAD2345", 1, PersonalCalendar.MAIN, bigHead.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())
        }
        settle()
        tapText("도구")
        tapText("카드 배치", exact = true)
        settle()
        if (!appears("첫 줄을")) "스튜디오 아래에 '첫 줄을 …dp 내려요' 줄이 없어요"
        else { tapText("달력 머리·요일 줄이 첫 줄을", prefix = true); settle(); null }
    }

    /**
     * RVS-16 (총괄 #495 사진 검토, 조건 12): the same screen at 글자 200% on the folded phone, light and dark. No word stands letter by
     * letter (‘바 / 탕 / 색’), the bar's title is not cut, the open tool's chip shows whole beside ‘펼치기’, and the colour line's note is
     * one line with ⓘ.
     */
    @Test fun thePartBarFitsBigTextFolded() {
        val misses = ArrayList<String>()
        for (dark in listOf(false, true)) {
            val n = "s2/rvs16_part_bar_${lookName(dark, 2f, FOLDED)}"
            shoot(n, if (dark) night(FOLDED) else FOLDED, 2f) {
                runBlocking {
                    c.recipes.apply(bigHead, "HEAD2345", 1, PersonalCalendar.MAIN, bigHead.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())
                }
                settle()
                tapText("도구")
                tapText("카드 배치", exact = true)
                settle()
                tapText("달력 머리·요일 줄이 첫 줄을", prefix = true)
                settle()
                bigTextMisses(nodes(merged = false), nodes(merged = true)).forEach { misses += "$n: $it" }
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    private fun textOf(node: SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }?.takeIf { it.isNotBlank() }

    /** What breaks at big text: a word standing (taller than twice its width), the bar's title cut, the open tool's chip cut, no ⓘ. */
    private fun bigTextMisses(all: List<SemanticsNode>, merged: List<SemanticsNode>): List<String> {
        val out = ArrayList<String>()
        for (node in all) {
            val t = textOf(node) ?: continue
            // Standing: as many lines as letters (‘바 / 탕 / 색’), read off the text's own layout.
            val chars = t.replace(" ", "").length
            if (chars < 3) continue
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val lines = layouts.firstOrNull()?.lineCount ?: continue
            if (lines >= 3 && lines * 2 > chars) out += "‘$t’ 글자가 세로로 섰어요 (${lines}줄)"
        }
        val title = all.firstOrNull { it.config.getOrNull(SemanticsActions.OnClick)?.label == "시작할 모양 바꾸기" }
        if (title == null) out += "위 막대 제목이 없어요"
        else (listOf(title) + title.children).forEach { n ->
            if (n.boundsInRoot.height + 2 < n.size.height) out += "위 막대 ‘${textOf(n) ?: "제목"}’의 아래가 잘렸어요 (${n.boundsInRoot.height.toInt()}/${n.size.height}px)"
        }
        val chip = merged.firstOrNull { textOf(it) == "바탕색" && it.config.getOrNull(SemanticsProperties.Selected) == true }
        if (chip == null) out += "고른 ‘바탕색’ 칩이 없어요"
        else if (chip.boundsInRoot.width + 2 < chip.size.width) out += "고른 ‘바탕색’ 칩이 잘렸어요 (${chip.boundsInRoot.width.toInt()}/${chip.size.width}px)"
        if (all.none { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d == "설명 보기" } == true }) out += "색 줄 설명에 ⓘ가 없어요"
        return out
    }

    /** RVS-10: a 가게 sees its own days in 어떤 때 (영업일·휴무), not 주간·야간·연차. */
    @Test fun aShopSeesItsOwnDays() {
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.BUSINESS) }
        // 어떤 때 opened (all its chips in view), as a maker who taps 펼치기 sees it.
        com.bamnat.app.ui.components.setFoldOpen(app, "studio.states", true)
        fourLooks("rvs10_shop_states") {
            content {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    PartsTool("cal", "", focus = "cal.cell", onChange = {})
                }
            }
            settle()
            when {
                !scrolledTo("영업일", exact = true) -> "'영업일'이 없어요"
                shows("야간", exact = true) || shows("연차", exact = true) -> "가게에 '야간'·'연차'가 보여요"
                else -> null
            }
        }
    }

    /** RVS-08: a cell that moves and is sized says so in 움직임·크기·자리, each line with what it is now. */
    @Test fun thePartsToolShowsMotionAndSize() {
        val parts = Blueprint(listOf(mapOf("id" to "cal.cell", "motion" to "glow", "w" to 40L, "h" to 40L, "place" to "mc"))).encode()
        fourLooks("rvs08_parts_motion") {
            content {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    PartsTool("cal", parts, focus = "cal.cell", onChange = {})
                }
            }
            settle()
            // runCatching: on the branch before there was no such 묶음, and its 앞 사진 is still taken.
            runCatching { tapText("움직임·크기·자리", prefix = true) }
            settle()
            when {
                !scrolledTo("움직임", exact = true) -> "'움직임' 묶음이 없어요"
                !scrolledTo("너비 40dp") -> "지금 걸린 너비·높이가 한 줄로 안 보여요"
                else -> null
            }
        }
    }
}
