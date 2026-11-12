package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeApi
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipePage
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Sent
import com.bamnat.app.recipe.Votes
import com.bamnat.app.ui.recipe.MakerScreen
import com.bamnat.app.ui.recipe.MakerViewModel
import com.bamnat.app.ui.recipe.RecipeMineScreen
import com.bamnat.app.ui.recipe.RecipeMineViewModel
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamMonth
import com.bamnat.core.team.TeamShift
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 Q1 PR 3b (검토 U22-01·I22b-01·SVC-23·SVC-24·U22b-09·SVC-17·SVC-21): the six screens the app part touches, light·dark ×
 * 글자 100%·200% × 접은·편 폭, for the PR's before/after sheets. What differs between before and after is only in [Rvq3b].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvq3bScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val shotAt = Instant.parse("2026-10-14T00:30:00Z")

    // ---- 01 리더: 확인 1/4명 · 열어 봄 3/4명 ----
    private fun leader(): Screen.() -> Unit = {
        runBlocking {
            val store = c.phaseThree
            val shifts = listOf(
                TeamShift(1, "D", "주간", 0xFF4F8BD6, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
                TeamShift(2, "E", "저녁", 0xFFE39B3B, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
                TeamShift(3, "N", "야간", 0xFF5E4AA8, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
                TeamShift(4, "O", "휴무", 0xFF9AA3AD, true),
            )
            val team = Team("t1", "7병동", "민지", shifts, 1L)
            store.saveTeam(team)
            val names = listOf("민지", "준호", "서연", "지훈")
            val members = names.mapIndexed { i, n -> TeamMember("m$i", "t1", n, sort = i, linkId = "Lk${i}dEfGh5JkLmNoPqRsTu", linkToken = "k$i") }
            store.saveMembers(members)
            val month = YearMonth.from(Today.date()).plusMonths(1)
            val rot = listOf(1L, 2L, 3L, 4L)
            val cells = members.flatMapIndexed { i, m -> (1..month.lengthOfMonth()).map { d -> (m.id to month.atDay(d)) to TeamCell(m.id, month.atDay(d), rot[(d + i) % rot.size]) } }.toMap()
            val board = TeamBoard(month, members, cells)
            store.saveBoard("t1", board)
            store.confirm("t1", board, TeamMonth("t1", month))
        }
        val opened = mapOf("Lk0dEfGh5JkLmNoPqRsTu" to shotAt, "Lk1dEfGh5JkLmNoPqRsTu" to shotAt, "Lk2dEfGh5JkLmNoPqRsTu" to shotAt,
            "Lk3dEfGh5JkLmNoPqRsTu" to null)
        c.shareApi = Rvq3b.seen(FakeShareApi(), opened, mapOf("Lk0dEfGh5JkLmNoPqRsTu" to shotAt))
        content { com.bamnat.app.ui.team.LeaderTeamScreen("main", onBack = {}) }
        settle()
        tapText("열기", exact = true)
        settle()
        reveal(Rvq3b.LEADER, exact = false)
    }

    // ---- 02 받은 근무표: 확인했어요 ----
    private fun received(wide: Boolean): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val share = Rvq3b.watched(minjiShare())
        shares.links[minjiId] = share
        cc.received.put(minjiId, share)
        settle()
        cc.received.view(minjiId)
        settle()
        reveal(Rvq3b.RECEIVED, exact = false, across = if (wide) 0.75f else 0.5f)
    }

    // ---- 03 내 작품: 지난 판 지우기 ----
    private fun item(code: String, title: String, version: Int) = RecipeItem(code, title, "야간이 잘 보여요", setOf(WorkStyle.SHIFT), Cover(), "밤샘간호사",
        version, likes = 41, dislikes = 1, users = 214, kept = 80, official = false, publicData = false, parent = null, at = shotAt)

    private open inner class Market : RecipeApi {
        override val enabled = true
        override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?) = RecipePage(emptyList(), null)
        override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = error("not used")
        override suspend fun publish(device: String, recipe: Recipe, scope: String) = Sent("NEWC2345", 1, "h", "review", scope)
        override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?) = Sent(code, 2, "h", "public", scope ?: "public")
        override suspend fun delete(device: String, code: String) {}
        override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?) = Votes(41, 1, value)
        override suspend fun applied(code: String, kept: Boolean) {}
        override suspend fun flag(device: String, code: String, reason: String) {}
        override suspend fun mine(device: String): MyRecipes = Rvq3b.mine(listOf(
            item("NGT2K4M7", "야간 많은 교대 세트", 3) to Triple("visible", "public", null),
            item("DAY8P2Q5", "주간 근무 파스텔", 1) to Triple("visible", "public", null),
        ), mapOf("NGT2K4M7" to 2))
        override suspend fun talk(device: String?, code: String) = RecipeTalk(emptyList(), null)
        override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?) = "p1"
        override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?) = Votes(0, 0, value)
    }

    private fun mineList(): Screen.() -> Unit = {
        c.recipeApi = Market()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = RecipeMineViewModel(c)
        content { RecipeMineScreen(model, onBack = {}, onOpen = {}, onDraft = {}, onList = {}) }
        settle()
        reveal("야간 많은 교대 세트", exact = true)
        if (Rvq3b.AFTER) tapText("‘야간 많은 교대 세트’ 더 보기", exact = true)
    }

    // ---- 04 내 프로필: 고친 소개 확인 중 ----
    private fun profile(): Screen.() -> Unit = {
        c.recipeApi = object : Market() {
            override suspend fun maker(device: String?, id: String) = Rvq3b.checking(MakerPage(id, "밤샘간호사", "🦉", 0xFF3949ABL, mine = true,
                recipes = listOf(item("NGT2K4M7", "야간 많은 교대 세트", 3)), likes = 41, users = 214, followers = 18, following = false,
                badges = emptyList(), bio = "야간 근무자를 위한 테마를 만들어요", bioHidden = true, bioHiddenBy = "operator"))
        }
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        val model = MakerViewModel(c, "a1b2c3d4e5f6")
        content { MakerScreen(model, onBack = {}, onOpen = {}) }
        settle()
        appears("밤샘간호사", exact = false)
    }

    // ---- 05 문의: 결제·환불 ----
    private fun billing(): Screen.() -> Unit = {
        inquiriesOn()
        askFromScreen(com.bamnat.app.ui.settings.InquiryStart(about = "프로·결제", kind = Rvq3b.BILLING))
        reveal("1. 어떤 문의인가요?", exact = true)
    }

    // ---- 06 프로: 결제·환불 문의 ----
    private fun proLinks(): Screen.() -> Unit = launched {
        pro().invoke(this)
        settle()
        reveal("구매 복원", exact = true)
    }

    @Test fun leaderLight() = shoot("rvq3b_01_leader_light_100_fold", FOLDED, leader())
    @Test fun leaderLightBig() = shoot("rvq3b_01_leader_light_200_fold", FOLDED, 2f, leader())
    @Test fun leaderDark() = shoot("rvq3b_01_leader_dark_100_fold", DARK, leader())
    @Test fun leaderDarkBig() = shoot("rvq3b_01_leader_dark_200_fold", DARK, 2f, leader())
    @Test fun leaderWide() = shoot("rvq3b_01_leader_light_100_open", UNFOLDED, leader())
    @Test fun leaderWideBig() = shoot("rvq3b_01_leader_light_200_open", UNFOLDED, 2f, leader())
    @Test fun leaderWideDark() = shoot("rvq3b_01_leader_dark_100_open", DARK_WIDE, leader())
    @Test fun leaderWideDarkBig() = shoot("rvq3b_01_leader_dark_200_open", DARK_WIDE, 2f, leader())

    @Test fun receivedLight() = shoot("rvq3b_02_received_light_100_fold", FOLDED, received(false))
    @Test fun receivedLightBig() = shoot("rvq3b_02_received_light_200_fold", FOLDED, 2f, received(false))
    @Test fun receivedDark() = shoot("rvq3b_02_received_dark_100_fold", DARK, received(false))
    @Test fun receivedDarkBig() = shoot("rvq3b_02_received_dark_200_fold", DARK, 2f, received(false))
    @Test fun receivedWide() = shoot("rvq3b_02_received_light_100_open", UNFOLDED, received(true))
    @Test fun receivedWideBig() = shoot("rvq3b_02_received_light_200_open", UNFOLDED, 2f, received(true))
    @Test fun receivedWideDark() = shoot("rvq3b_02_received_dark_100_open", DARK_WIDE, received(true))
    @Test fun receivedWideDarkBig() = shoot("rvq3b_02_received_dark_200_open", DARK_WIDE, 2f, received(true))

    @Test fun mineLight() = shoot("rvq3b_03_mine_light_100_fold", FOLDED, mineList())
    @Test fun mineLightBig() = shoot("rvq3b_03_mine_light_200_fold", FOLDED, 2f, mineList())
    @Test fun mineDark() = shoot("rvq3b_03_mine_dark_100_fold", DARK, mineList())
    @Test fun mineDarkBig() = shoot("rvq3b_03_mine_dark_200_fold", DARK, 2f, mineList())
    @Test fun mineWide() = shoot("rvq3b_03_mine_light_100_open", UNFOLDED, mineList())
    @Test fun mineWideBig() = shoot("rvq3b_03_mine_light_200_open", UNFOLDED, 2f, mineList())
    @Test fun mineWideDark() = shoot("rvq3b_03_mine_dark_100_open", DARK_WIDE, mineList())
    @Test fun mineWideDarkBig() = shoot("rvq3b_03_mine_dark_200_open", DARK_WIDE, 2f, mineList())

    @Test fun profileLight() = shoot("rvq3b_04_profile_light_100_fold", FOLDED, profile())
    @Test fun profileLightBig() = shoot("rvq3b_04_profile_light_200_fold", FOLDED, 2f, profile())
    @Test fun profileDark() = shoot("rvq3b_04_profile_dark_100_fold", DARK, profile())
    @Test fun profileDarkBig() = shoot("rvq3b_04_profile_dark_200_fold", DARK, 2f, profile())
    @Test fun profileWide() = shoot("rvq3b_04_profile_light_100_open", UNFOLDED, profile())
    @Test fun profileWideBig() = shoot("rvq3b_04_profile_light_200_open", UNFOLDED, 2f, profile())
    @Test fun profileWideDark() = shoot("rvq3b_04_profile_dark_100_open", DARK_WIDE, profile())
    @Test fun profileWideDarkBig() = shoot("rvq3b_04_profile_dark_200_open", DARK_WIDE, 2f, profile())

    @Test fun billingLight() = shoot("rvq3b_05_billing_light_100_fold", FOLDED, billing())
    @Test fun billingLightBig() = shoot("rvq3b_05_billing_light_200_fold", FOLDED, 2f, billing())
    @Test fun billingDark() = shoot("rvq3b_05_billing_dark_100_fold", DARK, billing())
    @Test fun billingDarkBig() = shoot("rvq3b_05_billing_dark_200_fold", DARK, 2f, billing())
    @Test fun billingWide() = shoot("rvq3b_05_billing_light_100_open", UNFOLDED, billing())
    @Test fun billingWideBig() = shoot("rvq3b_05_billing_light_200_open", UNFOLDED, 2f, billing())
    @Test fun billingWideDark() = shoot("rvq3b_05_billing_dark_100_open", DARK_WIDE, billing())
    @Test fun billingWideDarkBig() = shoot("rvq3b_05_billing_dark_200_open", DARK_WIDE, 2f, billing())

    @Test fun proLight() = shoot("rvq3b_06_pro_light_100_fold", FOLDED, proLinks())
    @Test fun proLightBig() = shoot("rvq3b_06_pro_light_200_fold", FOLDED, 2f, proLinks())
    @Test fun proDark() = shoot("rvq3b_06_pro_dark_100_fold", DARK, proLinks())
    @Test fun proDarkBig() = shoot("rvq3b_06_pro_dark_200_fold", DARK, 2f, proLinks())
    @Test fun proWide() = shoot("rvq3b_06_pro_light_100_open", UNFOLDED, proLinks())
    @Test fun proWideBig() = shoot("rvq3b_06_pro_light_200_open", UNFOLDED, 2f, proLinks())
    @Test fun proWideDark() = shoot("rvq3b_06_pro_dark_100_open", DARK_WIDE, proLinks())
    @Test fun proWideDarkBig() = shoot("rvq3b_06_pro_dark_200_open", DARK_WIDE, 2f, proLinks())

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
        const val DARK_WIDE = "w707dp-h823dp-port-night-420dpi"
    }
}
