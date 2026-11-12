package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.calendar.HeadBell
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicAct
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicValue
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.ValueReset
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 3/3의 앞·뒤 화면 사진(디자인 품질 조건 1·2·3·8·12): 달력 위는 머리 한 줄과 꼭 봐야 하는 띠 하나, 나머지는 공유 옆
 * 종(알림함)으로 ([RVP-45][RVP-43] 검토 UIX-01·U13-01·U13-02·I13b-01·PC-55·HQ-49·HQ-50), 작품을 입은 머리(HQ-22·HQ-29·PC-29),
 * 늦게 오는 띠가 달을 밀지 않음(PC-81). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP3ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, style: WorkStyle, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvp3_${name}_$look", q, scale, style(style) { go() })
    }

    /** What people did with the user's things, as 장터 and 광장 put it in the 알림함 (HQ-51·52). */
    private fun reactions() {
        Inbox.post(app, InboxRow("market.work:1", "market.reaction", InboxSection.PEOPLE, "내 작품 ‘밤바다’", "댓글 2개 · 좋아요 5개",
            target = NoticeLinks.Target.MONEY, at = Today.millis() - 20 * 60_000L, count = 7))
        Inbox.post(app, InboxRow("plaza.post:9", "plaza.reaction", InboxSection.PEOPLE, "광장 글 ‘3교대 연차 계산’", "맞아요 3 · 답 1",
            target = NoticeLinks.Target.MONEY, at = Today.millis() - 3 * 60 * 60_000L, count = 4))
    }

    /** A work with a big month (28sp) and two of its own buttons on the head: 물 +1잔 and 집중 시작 (검토 HQ-22·HQ-29·PC-29). */
    private fun Screen.wearWork() {
        app.container.settingsRepository.update {
            it.copy(
                parts = Blueprint(Blueprint.clean(listOf(mapOf("id" to "cal.head.title", "size" to 28.0, "weight" to 800L)))).encode(),
                decor = Decor(listOf(
                    DecorItem(DecorKind.TEXT, "물 +1잔", at = Decor.HEAD, x = 850, y = 690, w = 104, h = 26, fill = 0xFF7CF0FF,
                        ink = 0xFF04121A, r = 13, size = 11.0, weight = 800, over = true, tap = "water"),
                    DecorItem(DecorKind.TEXT, "집중 시작", at = Decor.HEAD, x = 700, y = 690, w = 120, h = 26, fill = 0xFF1E335C,
                        ink = 0xFFFFFFFF, r = 13, size = 11.0, weight = 800, over = true, tap = "focus"),
                )).encode(),
                logic = Logic.encode(Logic(
                    buttons = listOf(
                        LogicButton("물 한 잔", "💧", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "물 잔", by = 1.0), givenId = "water"),
                        LogicButton("집중 시작", "🎯", ButtonPlace.WIDGET, LogicAct(LogicVerb.VAL_ADD, "집중", by = 1.0), givenId = "focus"),
                    ),
                    values = listOf(LogicValue("물 잔", ValueReset.DAY, goal = 6.0, unit = "잔"), LogicValue("집중", ValueReset.DAY, unit = "번")),
                )),
            )
        }
        settle()
    }

    /** The bell's number as TalkBack reads it ("알림 3개"), 0 without one. */
    private fun Screen.bellCount(): Int = nodes().firstNotNullOfOrNull { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().firstNotNullOfOrNull { d ->
            Regex("""^알림 (\d+)개$""").find(d)?.groupValues?.get(1)?.toInt()
        }
    } ?: 0

    private fun Screen.firstDayTop(): Float {
        val first = Fmt.dayTitle(today.withDayOfMonth(1))
        return nodes().first { n ->
            n.boundsInWindow.height > 1f && n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(first) }
        }.boundsInWindow.top
    }

    @Test fun head() = all("01_head", WorkStyle.SHIFT) { reactions(); settle() }

    @Test fun dayFirstScreen() = all("02_day", WorkStyle.DAY) {}

    @Test fun investFirstStep() {
        for ((look, q, scale) in looks) {
            // The first step is the band once (총괄 11-12); each look starts before it was seen.
            app.getSharedPreferences("hints", 0).edit().remove(com.bamnat.app.ui.calendar.HintStore.START_BAND).commit()
            shoot("rvp3_03_invest_start_$look", q, scale, fresh(WorkStyle.INVEST) {})
        }
    }

    @Test fun wornWork() = all("04_worn_work", WorkStyle.SHIFT) { wearWork() }

    /** HQ-49: the 알림함 opened from the bell, people first. */
    @Test fun inboxSheet() {
        for ((look, q, scale) in looks.take(2)) shoot("rvp3_05_inbox_$look", q, scale, style(WorkStyle.SHIFT) {
            reactions()
            settle()
            tapText("알림 ", prefix = true, scrolls = 0)
            settle()
            assertTrue("알림함이 열려요", appears("사람들 반응", exact = true))
        })
    }

    /** 총괄 HQ-49: the bell beside 공유 and in 나, for the user to compare (380 폭 100%·200%, 편 폭). */
    @Test fun bellPlaces() {
        val sizes = listOf(Triple("folded_100", FOLDED, 1f), Triple("folded_200", FOLDED, 2f), Triple("open_100", UNFOLDED, 1f))
        for (atMe in listOf(false, true)) for ((look, q, scale) in sizes) {
            HeadBell.atMe = atMe
            shoot("rvp3_06_bell_${if (atMe) "me" else "share"}_$look", q, scale, style(WorkStyle.SHIFT) { reactions(); settle() })
        }
        HeadBell.atMe = false
    }

    /**
     * 검토 PC-81 (스튜디오에서 입힌 뒤 달력으로 오면 띠 두 개가 1~2초 늦게 나타나 달력이 327px 밀림): with the calendar open and settled,
     * the studio puts a look on (its undo kept, the look going on a moment later): the 1일 칸 stays where it was two seconds on, and what
     * came late is in the bell.
     */
    @Test fun aBandThatComesLateDoesNotMoveTheMonth() = shoot("rvp3_07_late_band", PHONE, style(WorkStyle.SHIFT) {
        val c = app.container
        val top = firstDayTop()
        val bell = bellCount()
        com.bamnat.app.data.LookUndo.keep(app, c.recipes.pieces.lookSnapshot())
        c.settingsRepository.update {
            it.copy(parts = Blueprint(Blueprint.clean(listOf(mapOf("id" to "cal.week.day", "on" to "sun", "ink" to 0xFFE5484DL)))).encode())
        }
        waitFor(2_000)
        assertEquals("1일 칸이 그 자리에 있어요(PC-81)", top, firstDayTop(), 0.5f)
        assertEquals("늦게 온 '입힌 모양'은 종으로 가요", bell + 1, bellCount())
    })
}
