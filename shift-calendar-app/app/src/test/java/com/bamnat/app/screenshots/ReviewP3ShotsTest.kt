package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.app.ui.Fmt
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
    /** The alarms turned off for today in [clearAllKeepsTheAlarmWarning] are on again for the next test. */
    @org.junit.After fun alarmsBack() = com.bamnat.app.alarm.AlarmsOff.turnOn(app)

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

    /**
     * Where the month's 1일 칸 starts, as a share of the screen: only a cell inside the screen counts (총괄 HQ-56: a test that found the
     * cell of a month drawn beside the screen passed whatever the screen showed).
     */
    private fun Screen.visibleStart(): Float {
        val first = Fmt.dayTitle(today.withDayOfMonth(1))
        val m = app.resources.displayMetrics
        val cell = nodes().filter { n ->
            val b = n.boundsInWindow
            b.height > 1f && b.left >= 0f && b.right <= m.widthPixels + 1f && b.top >= 0f && b.bottom <= m.heightPixels + 1f &&
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(first) }
        }.minByOrNull { it.boundsInWindow.top } ?: return 1f
        return cell.boundsInWindow.top / m.heightPixels
    }

    private fun Screen.firstDayTop(): Float {
        val first = Fmt.dayTitle(today.withDayOfMonth(1))
        return nodes().first { n ->
            n.boundsInWindow.height > 1f && n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(first) }
        }.boundsInWindow.top
    }

    @Test fun head() = all("01_head", WorkStyle.SHIFT) { reactions(); settle() }

    /**
     * 총괄 HQ-49 숫자(합친 코드에서 1일 칸: 접은 폭 100% 35%, 200% 42%, 편 폭 100% 39%, 200% 52%): the same day as [head] (the alarm
     * band up, people's reactions in the bell), measured on the 1일 칸 that is on screen ([visibleStart]) in all four looks. 100% is
     * held to 조건 1's 25%; 200% to what it is now, going down only.
     */
    @Test fun startsHighInEveryLook() {
        val found = mutableListOf<String>()
        for ((look, q, scale) in looks) {
            var at = 1f
            shoot("rvp3_00_start_$look", q, scale, style(WorkStyle.SHIFT) { reactions(); settle(); at = visibleStart() })
            val percent = Math.round(at * 100)
            found += "$look $percent%"
            val ceiling = if (scale > 1f) BIG_CEILING else CalendarStartScreenshotTest.GOAL
            assertTrue("$look: 달력 1일 칸이 화면 위 $percent%에서 시작해요(천장 $ceiling%) · $found", percent <= ceiling)
        }
        java.io.File("build/reports/design-rules.txt").apply { parentFile?.mkdirs() }.let { f ->
            val old = if (f.exists()) f.readLines().filterNot { it.startsWith("달력 시작 높이 네 모습:") } else emptyList()
            f.writeText((old + "달력 시작 높이 네 모습: ${found.joinToString(", ")}").sorted().joinToString("\n") + "\n")
        }
    }

    /**
     * 총괄 HQ-59 (갈래 R: 360dp 폭에서 달력 머리의 '2026년 10월'이 보조 글씨만큼 작아요): on a 360dp phone at 100% and 200%, plain and
     * with a work's big month and its 바로 단추 on, the month says this year's month whole without the year (the rest folds into
     * icons and 도구), so it never has to shrink to fit.
     */
    @Test fun aNarrowPhoneKeepsTheMonthWhole() {
        for ((look, scale) in listOf("100" to 1f, "200" to 2f)) for (worn in listOf(false, true)) {
            shoot("rvp3_09_narrow_360_${look}_${if (worn) "worn" else "plain"}", PHONE, scale, style(WorkStyle.SHIFT) {
                if (worn) wearWork() else { reactions(); settle() }
                assertTrue("$look: 올해 달은 해 없이 '${today.monthValue}월' 그대로", appears("${today.monthValue}월", exact = true))
            })
        }
    }

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

    /**
     * 검토 PC-139 (높음) with PC-140·HQ-91·HQ-92: with the alarms turned off for today (one of the alarm lines that stay until fixed),
     * 종 › 전체 지움 leaves '오늘 알람 끔' in the bell (no X, no ⋮), takes the rest, and offers 되돌리기 inside the 알림함; the sheet
     * opens whole at 200% too.
     */
    @Test fun clearAllKeepsTheAlarmWarning() = all("10_clear_all_keeps_alarm", WorkStyle.SHIFT) {
        reactions()
        com.bamnat.app.alarm.AlarmsOff.turnOff(app, null)
        settle()
        assertTrue("띠", appears("오늘 알람 끔", exact = false))
        tapText("알림 ", prefix = true, scrolls = 0)
        settle()
        tapText("전체 지움", exact = true, scrolls = 0)
        settle()
        assertTrue("되돌리기는 알림함 안", appears("되돌리기", exact = true))
        assertTrue("남긴 까닭", appears("알람에 꼭 필요한 것은 고칠 때까지 남겨 둬요", exact = false))
        assertTrue("알람 경고는 남아요", appears("오늘 알람 끔", exact = false))
        assertTrue("반응은 지워져요", gone("내 작품 ‘밤바다’", exact = false))
    }

    /** 총괄 HQ-49: the bell beside 공유 (사용자 11-13 00:35 KST 카드 '공유 옆에 따로'), 380 폭 100%·200%, 편 폭. */
    @Test fun bellBesideShare() {
        val sizes = listOf(Triple("folded_100", FOLDED, 1f), Triple("folded_200", FOLDED, 2f), Triple("open_100", UNFOLDED, 1f))
        for ((look, q, scale) in sizes) shoot("rvp3_06_bell_share_$look", q, scale, style(WorkStyle.SHIFT) {
            reactions()
            settle()
            assertTrue("종이 머리 줄에 있어요", appears("알림", exact = false))
        })
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

    /** HQ-50: 설정 › 기능 켜고 끄기 › 안내, beside 안 써 본 기능: 권유 끄기, and a kind turned off with its way back. Light and dark 200%. */
    @Test fun inboxSwitches() {
        for ((name, dark, scale) in listOf(Triple("a_folded_light_100", false, 1f), Triple("b_folded_dark_200", true, 2f))) {
            shoot("rvp3_08_inbox_switches_$name", FOLDED, scale) {
                Inbox.get(app).mute("suggest.companyRule", "회사 규칙으로 올리기")
                content {
                    ShotTheme(themeMode = if (dark) com.bamnat.app.data.ThemeMode.DARK else com.bamnat.app.data.ThemeMode.LIGHT) {
                        androidx.compose.foundation.layout.Column(
                            androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                                .padding(16.dp),
                        ) { com.bamnat.app.ui.guide.InboxSwitches() }
                    }
                }
                settle()
                check(appears("알림함 권유 받기", exact = true)) { "no switch" }
                check(appears("회사 규칙으로 올리기", exact = true) && appears("다시 보기", exact = true)) { "the kind turned off has no way back" }
            }
        }
    }

    private companion object {
        /**
         * Letters at 200%: what the four looks measure now, going down only (P 3/3: 접은 폭 29%, 편 폭 32%, with the alarm band up in
         * two lines; before, 42% and 52%).
         */
        const val BIG_CEILING = 32
    }
}
