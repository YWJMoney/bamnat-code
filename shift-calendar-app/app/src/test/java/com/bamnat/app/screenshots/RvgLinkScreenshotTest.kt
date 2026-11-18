package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.SharedDay
import com.bamnat.app.share.SharedSchedule
import com.bamnat.app.share.SharedType
import com.bamnat.app.ui.onboarding.FirstStyleScreen
import com.bamnat.app.ui.setup.LinkStart
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import com.bamnat.core.model.ShiftOverride
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * [RVG-15][RVG-16][RVG-18] 받은 근무표 링크로 새로 깐 사람의 첫 화면과 요약 (2.0.0 디자인 품질 조건): 밝은·어두운 × 글자 100%·200% × 폴드
 * 접은 폭·편 폭. 채운 단추는 하나, 단추 글이 잘리지 않고, 다음 달 근무표는 그 달로 열려요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvgLinkScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private fun Screen.first(title: String, lean: LinkStart.Owner) = content {
        FirstStyleScreen(onPick = {}, onSkip = {}, onLater = {}, textStep = c.settingsRepository.current.textStep,
            linkWaiting = true, linkTitle = title, linkLean = lean, onLinkStart = {}, onLinkTheirs = {}, onLinkClose = {})
    }

    private fun family(): Screen.() -> Unit = {
        first("민지", LinkStart.Owner.THEIRS)
        settle()
        assertTrue(appears("가족·동료 것이에요"))
        assertTrue(appears("내 근무표예요"))
    }

    @Test fun familyFoldLight() = shoot("rvg15_first_family_fold_light_100", FOLDED, family())
    @Test fun familyFoldDark() = shoot("rvg15_first_family_fold_dark_100", FOLDED_NIGHT, family())
    @Test fun familyFoldLightHuge() = shoot("rvg15_first_family_fold_light_200", FOLDED, fontScale = 2f, family())
    @Test fun familyFoldDarkHuge() = shoot("rvg15_first_family_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, family())
    @Test fun familyOpenLight() = shoot("rvg15_first_family_open_light_100", UNFOLDED, family())
    @Test fun familyOpenDarkHuge() = shoot("rvg15_first_family_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, family())

    @Test fun teamFoldLight() = shoot("rvg15_first_team_fold_light_100", FOLDED) {
        first("3병동 김간호", LinkStart.Owner.MINE)
        settle()
    }

    private fun kept(): Screen.() -> Unit = {
        content { FirstStyleScreen(onPick = {}, onSkip = {}, onLater = {}, textStep = c.settingsRepository.current.textStep, theirsKept = "민지") }
        settle()
    }
    @Test fun keptFoldLight() = shoot("rvg15_first_kept_fold_light_100", FOLDED, kept())
    @Test fun keptFoldDarkHuge() = shoot("rvg15_first_kept_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, kept())

    /** A 리더's member link of next month (도약 14): the summary opens on that month, with '어떻게 일하세요?' under it. */
    private fun summary(): Screen.() -> Unit = {
        val next = YearMonth.from(today).plusMonths(1)
        val types = listOf(
            SharedType(1, "데이", "D", 0xFF2196F3, "07:00-15:00", false, null),
            SharedType(2, "이브닝", "E", 0xFFFF9800, "15:00-23:00", false, null),
            SharedType(3, "나이트", "N", 0xFF673AB7, "23:00-07:00", false, null),
            SharedType(4, "오프", "O", 0xFF9E9E9E, null, true, null),
        )
        val order = listOf(1L, 1L, 2L, 2L, 3L, 3L, 4L, 4L)
        val days = (1..next.lengthOfMonth()).associate { d -> next.atDay(d) to SharedDay(next.atDay(d), order[(d - 1) % order.size], null, null, null, emptyList(), null) }
        c.setupDrafts.save(LinkStart.draft(SharedSchedule("3병동 김간호", types, days, emptyList(), emptySet()), today)!!)
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c)
        content { com.bamnat.app.ui.setup.SetupHost(vm, onNotMine = {}) }
        settle()
    }
    @Test fun summaryFoldLight() = shoot("rvg16_summary_fold_light_100", FOLDED, summary())
    @Test fun summaryFoldDarkHuge() = shoot("rvg16_summary_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, summary())
    @Test fun summaryOpenLight() = shoot("rvg16_summary_open_light_100", UNFOLDED, summary())
    @Test fun summaryOpenDarkHuge() = shoot("rvg16_summary_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, summary())
    @Test fun summaryFoldLightScrolled() = shoot("rvg16_summary_fold_light_100_below", FOLDED) {
        summary().invoke(this)
        reveal("어떻게 일하세요?")
    }

    /**
     * [RVG-19] 검토 HQ-09: 민지's 가족 link, '내 근무표예요' pressed by mistake: the summary has '가족·동료 근무표였어요' right under its
     * question (the line it replaces is shown by the month and '근무 이름' under it).
     */
    private fun familySummary(): Screen.() -> Unit = {
        c.setupDrafts.save(LinkStart.draft(SharedSchedule.parse(minjiShare().schedule), today)!!)
        val vm = com.bamnat.app.ui.setup.SetupViewModel(c)
        content { com.bamnat.app.ui.setup.SetupHost(vm, onNotMine = {}) }
        settle()
        assertTrue(appears("가족·동료 근무표였어요", exact = true))
    }
    @Test fun familySummaryFoldLight() = shoot("rvg19_summary_family_fold_light_100", FOLDED, familySummary())
    @Test fun familySummaryFoldDark() = shoot("rvg19_summary_family_fold_dark_100", FOLDED_NIGHT, familySummary())
    @Test fun familySummaryFoldLightHuge() = shoot("rvg19_summary_family_fold_light_200", FOLDED, fontScale = 2f, familySummary())
    @Test fun familySummaryFoldDarkHuge() = shoot("rvg19_summary_family_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, familySummary())
    @Test fun familySummaryOpenLight() = shoot("rvg19_summary_family_open_light_100", UNFOLDED, familySummary())
    @Test fun familySummaryOpenDarkHuge() = shoot("rvg19_summary_family_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, familySummary())

    /**
     * [RVG-20] 검토 HQ-09: found after the setup. 민지's link is the user's own: her next ten days are in my calendar, one of them
     * changed by hand (a day off where she works).
     */
    private fun mineByMistake(): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val share = minjiShare()
        shares.links[minjiId] = share
        cc.received.put(minjiId, share, mine = true, basis = share.schedule)
        runBlocking {
            val snap = cc.scheduleRepository.snapshot.first()
            val s = SharedSchedule.parse(share.schedule)
            val pairs = com.bamnat.core.schedule.RosterAdopt.match(com.bamnat.app.share.RosterFollower.theirTypes(s), snap.shiftTypes)
            val days = com.bamnat.app.share.RosterFollower.theirDays(s).filterKeys { !it.isBefore(today) && it.isBefore(today.plusDays(10)) }
            days.forEach { (d, t) ->
                // Only what the link put in on these days (the seeded 교대 user's own extras and alarms there are not this story's).
                cc.scheduleRepository.restoreDay(d, null)
                val o = when (t) {
                    com.bamnat.core.schedule.RosterFollow.Their.Rest -> ShiftOverride.Off
                    is com.bamnat.core.schedule.RosterFollow.Their.Shift -> pairs[t.id]?.let { ShiftOverride.Shift(it) }
                } ?: return@forEach
                cc.scheduleRepository.setOverride(d, o)
            }
            days.entries.first { it.value is com.bamnat.core.schedule.RosterFollow.Their.Shift }.key.let { cc.scheduleRepository.setOverride(it, ShiftOverride.Off) }
        }
        settle()
        cc.received.view(minjiId)
        settle()
    }
    private fun notMineCard(wide: Boolean = false): Screen.() -> Unit = {
        mineByMistake().invoke(this)
        // Unfolded, the card is in the right-hand column.
        reveal("내 근무표가 아니에요", exact = true, across = if (wide) 0.75f else 0.5f)
        // [RVG-21] 검토 HQ-19: 민지's is a 가족 link, so its card says 보낸 분, never 리더.
        assertTrue(appears("보낸 분이 근무표를 고치면", exact = false))
        assertTrue(gone("리더가", exact = false))
    }
    @Test fun notMineFoldLight() = shoot("rvg20_received_fold_light_100", FOLDED, notMineCard())
    @Test fun notMineFoldDarkHuge() = shoot("rvg20_received_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, notMineCard())
    @Test fun notMineOpenLight() = shoot("rvg20_received_open_light_100", UNFOLDED, notMineCard(wide = true))
    @Test fun notMineOpenDarkHuge() = shoot("rvg20_received_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, notMineCard(wide = true))

    /**
     * [RVG-22] 검토 HQ-20 (조건 12): the received month's head at the largest text. '오늘' is the 오늘 icon where the month and its word
     * do not both fit (it stood one letter a line), '내 달력으로' its face only (it was cut to '내 달력…'), and the dates whole.
     */
    private fun receivedHead(big: Boolean): Screen.() -> Unit = {
        mineByMistake().invoke(this)
        assertTrue(appears("오늘", exact = true))
        assertTrue(appears("내 달력으로", exact = true))
        if (!big) assertTrue("there is room: the words, not the icon", appears("2026년 10월", exact = false))
    }
    @Test fun receivedHeadFoldLight() = shoot("rvg22_received_head_fold_light_100", FOLDED, receivedHead(big = false))
    @Test fun receivedHeadFoldDarkHuge() = shoot("rvg22_received_head_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, receivedHead(big = true))
    @Test fun receivedHeadOpenDarkHuge() = shoot("rvg22_received_head_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, receivedHead(big = true))

    /** How many days come out is said first, and how many changed by hand stay. */
    private fun notMineAsk(wide: Boolean = false): Screen.() -> Unit = {
        notMineCard(wide).invoke(this)
        tapText("내 근무표가 아니에요", exact = true, across = if (wide) 0.75f else 0.5f)
        assertTrue(appears("근무표가 넣은 ", exact = false))
        assertTrue(appears("일 치는 그대로 둬요.", exact = false))
    }
    @Test fun notMineAskFoldLight() = shoot("rvg20_ask_fold_light_100", FOLDED, notMineAsk())
    @Test fun notMineAskFoldDarkHuge() = shoot("rvg20_ask_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, notMineAsk())
    @Test fun notMineAskOpenLightHuge() = shoot("rvg20_ask_open_light_200", UNFOLDED, fontScale = 2f, notMineAsk(wide = true))

    /** Taken out: the line at the bottom says so with 되돌리기, and the link is only seen (no '이 근무표가 바뀌면' card any more). */
    @Test fun notMineDoneFoldLight() = shoot("rvg20_done_fold_light_100", FOLDED) {
        notMineAsk().invoke(this)
        tapText("일 치 빼기", exact = false)
        assertTrue(appears("일 치를 뺐어요", exact = false))
        assertTrue(gone("이 근무표가 바뀌면", exact = true))
        assertTrue(!c.received.get(minjiId)!!.mine)
    }

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
