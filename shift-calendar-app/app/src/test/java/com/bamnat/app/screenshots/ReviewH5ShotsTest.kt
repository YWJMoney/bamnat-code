package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.work.WorkStyle
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 검토 갈래 H 5묶음의 앞·뒤 화면 사진: 날짜 창 › 추가 근무의 겹침 글([RVH-58] PC-109), 대근을 넣은 날짜 창의 휴식 점검과 이 주
 * 시간([RVH-59] PC-63), 가게·투자 도구 창의 받기 말([RVH-60] PC-127). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%)으로 그려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH5ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh5_${name}_$look", q, scale, style(style) { go() })
    }

    /**
     * One look on a phone of its own: a day sheet opened and scrolled in one look is still remembered in the next (the sheet
     * comes back open and the next taps miss), so these days are drawn in a test each.
     */
    private fun one(name: String, look: Int, style: WorkStyle, go: Screen.() -> Unit) {
        val (l, q, scale) = looks[look]
        shoot("rvh5_${name}_$l", q, scale, style(style) { go() })
    }

    /**
     * PC-109: a 주간(08~20) day with 특근 09~18 (inside the shift: no hours), 잔업 20~22 and 특근 21~23 (one hour counted twice). Before,
     * the red '겹친 시간도 따로따로 세니' stood under all three, though the 특근 inside counted none; now one line in the 안내 colour says
     * the hour that counts twice.
     */
    private fun overlap(across: Float): Screen.() -> Unit = {
        val d = runBlocking {
            val repo = app.container.scheduleRepository
            val snap = repo.snapshot.first()
            val d = (1..12).flatMap { listOf(today.plusDays(it.toLong()), today.minusDays(it.toLong())) }
                .filter { it.month == today.month }
                .first { val r = snap.resolver.resolve(it); r.shiftType?.name == "주간" && r.extras.isEmpty() && r.leave == null }
            repo.addExtra(d, ExtraEntry(ExtraKind.SPECIAL_ID, LocalTime.of(9, 0), LocalTime.of(18, 0)))
            repo.addExtra(d, ExtraEntry(ExtraKind.OVERTIME_ID, LocalTime.of(20, 0), LocalTime.of(22, 0)))
            repo.addExtra(d, ExtraEntry(ExtraKind.SPECIAL_ID, LocalTime.of(21, 0), LocalTime.of(23, 0)))
            d
        }
        settle()
        openDay(d)
        settle()
        openGroup("추가 근무", across)
        settle()
        reveal("근무 시간 안이라", across = across)
    }

    @Test fun extraOverlapA() = one("31_extra_overlap", 0, WorkStyle.SHIFT, overlap(FOLDED_ACROSS))
    @Test fun extraOverlapB() = one("31_extra_overlap", 1, WorkStyle.SHIFT, overlap(FOLDED_ACROSS))
    @Test fun extraOverlapC() = one("31_extra_overlap", 2, WorkStyle.SHIFT, overlap(OPEN_ACROSS))
    @Test fun extraOverlapD() = one("31_extra_overlap", 3, WorkStyle.SHIFT, overlap(OPEN_ACROSS))

    /**
     * PC-63: 야간 then 주간 the next morning, with a 야간 대근 on top: before, a red chip '야간 뒤 휴식 부족' in the head, the week in red under
     * it, red in the 몸 group's line and a big red '휴식 점검' box. Now the head says it once in 주의 and the 몸 group keeps the detail plain.
     */
    private fun coverAfterNight(): Screen.() -> Unit = {
        nightIntoDay().invoke(this)
        runBlocking {
            val repo = app.container.scheduleRepository
            // Once: [restHead]'s four looks share the phone's data.
            if (repo.snapshot.first().resolver.resolve(today.minusDays(3)).extras.none { it.covered != null })
                repo.addExtra(today.minusDays(3), cover(repo.shiftTypes.first().first { it.name == "야간" }))
        }
        settle()
        openDay(today.minusDays(3))
        settle()
    }

    @Test fun restHead() = all("32_rest_head", WorkStyle.SHIFT) { coverAfterNight().invoke(this) }

    private fun body(across: Float): Screen.() -> Unit = {
        coverAfterNight().invoke(this)
        openGroup("몸", across)
        settle()
        reveal("몸", exact = true, across = across)
    }

    @Test fun restBodyA() = one("33_rest_body", 0, WorkStyle.SHIFT, body(FOLDED_ACROSS))
    @Test fun restBodyB() = one("33_rest_body", 1, WorkStyle.SHIFT, body(FOLDED_ACROSS))
    @Test fun restBodyC() = one("33_rest_body", 2, WorkStyle.SHIFT, body(OPEN_ACROSS))
    @Test fun restBodyD() = one("33_rest_body", 3, WorkStyle.SHIFT, body(OPEN_ACROSS))

    private companion object {
        /** Where a finger scrolls the day: the sheet's middle on the folded phone, the day pane at the right on the open one. */
        const val FOLDED_ACROSS = 0.5f
        const val OPEN_ACROSS = 0.8f
    }

    /**
     * PC-127: the 가게 tool sheet offered '동료 근무표 받기', a word for a crew on a rota. A shop (and 투자·현장, who keep no pay rota)
     * now reads '다른 사람 달력 받기', and the 패턴 tool says the kind's own word.
     */
    private fun tools(): Screen.() -> Unit = {
        tapText("도구", scrolls = 0)
        settle()
        listOf("다른 사람 달력 받기", "동료 근무표 받기").firstOrNull { shows(it, exact = true) }?.let { reveal(it, exact = true) }
    }

    @Test fun shopTools() = all("34_shop_tools", WorkStyle.BUSINESS) { tools().invoke(this) }

    @Test fun investTools() = all("35_invest_tools", WorkStyle.INVEST) { tools().invoke(this) }
}
