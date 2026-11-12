package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 10묶음의 앞·뒤 화면 사진: 가게 날짜 창의 근로자 휴식 경고([RVH-72] TYPE-03·T30-11), 가게 날짜 창 › 몸의 잠 계획 말
 * ([RVH-75] TYPE-08·T30-16), 관리 화면 이름([RVH-78] T30-105). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%,
 * 편 폭 어두운 100%).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH10ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun one(name: String, look: Int, style: WorkStyle, go: Screen.() -> Unit) {
        val (l, q, scale) = looks[look]
        shoot("rvh10_${name}_$l", q, scale, style(style) { go() })
    }

    /**
     * TYPE-03: a shop that worked late into an early opening. Before, its day sheet's head had the 근로자 '야간 뒤 휴식 부족' chip and the
     * week in 주의 (a shop owner has no 근로시간 limit to break); now the rest check stays on 교대·일근·알바 only.
     */
    private fun shopRest(): Screen.() -> Unit = {
        nightIntoDay().invoke(this)
        runBlocking {
            val repo = app.container.scheduleRepository
            if (repo.snapshot.first().resolver.resolve(today.minusDays(3)).extras.none { it.covered != null })
                repo.addExtra(today.minusDays(3), cover(repo.shiftTypes.first().first { it.name == "야간" }))
        }
        settle()
        openDay(today.minusDays(3))
        settle()
        assertTrue(appears("영업", exact = true))
        assertFalse("가게에 근로자 휴식 경고", shows("휴식 부족") || shows("연속 근무") || shows("연속 야간"))
    }
    @Test fun shopRestA() = one("50_shop_rest_head", 0, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestB() = one("50_shop_rest_head", 1, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestC() = one("50_shop_rest_head", 2, WorkStyle.BUSINESS, shopRest())
    @Test fun shopRestD() = one("50_shop_rest_head", 3, WorkStyle.BUSINESS, shopRest())

    /** TYPE-08: the night a shop stays open late, its 몸 group plans the sleep in 마감 words (was '퇴근 뒤 잠·퇴근길 졸음'). */
    private fun shopSleep(across: Float): Screen.() -> Unit = {
        nightIntoDay().invoke(this)
        openDay(today.minusDays(4))
        settle()
        openGroup("몸", across)
        settle()
        reveal("마감 뒤", across = across)
        assertFalse("가게 잠 계획에 '퇴근'", shows("퇴근"))
    }
    @Test fun shopSleepA() = one("51_shop_sleep", 0, WorkStyle.BUSINESS, shopSleep(FOLDED_ACROSS))
    @Test fun shopSleepB() = one("51_shop_sleep", 1, WorkStyle.BUSINESS, shopSleep(FOLDED_ACROSS))
    @Test fun shopSleepC() = one("51_shop_sleep", 2, WorkStyle.BUSINESS, shopSleep(OPEN_ACROSS))
    @Test fun shopSleepD() = one("51_shop_sleep", 3, WorkStyle.BUSINESS, shopSleep(OPEN_ACROSS))

    /** T30-105: the 관리 화면 of someone who leads a team and runs a 사업장 is '팀·사업장 관리', its ledger card '직원 급여 대장'. */
    private fun manage(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.ManageScreen(PersonalCalendar.MAIN, onBack = {}, showAll = true) }
        settle()
        assertTrue(appears("팀·사업장 관리", exact = true))
        assertFalse(shows("가게"))
    }
    @Test fun manageA() = one("52_manage", 0, WorkStyle.FIELD, manage())
    @Test fun manageB() = one("52_manage", 1, WorkStyle.FIELD, manage())
    @Test fun manageC() = one("52_manage", 2, WorkStyle.FIELD, manage())
    @Test fun manageD() = one("52_manage", 3, WorkStyle.FIELD, manage())

    private companion object {
        const val FOLDED_ACROSS = 0.5f
        const val OPEN_ACROSS = 0.8f
    }
}
