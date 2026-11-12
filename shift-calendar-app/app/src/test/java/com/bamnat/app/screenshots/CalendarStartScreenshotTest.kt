package com.bamnat.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.Fmt
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 [RVP-83][RVP-45] 디자인 품질 조건 1 '핵심이 먼저 보여요'(`docs/디자인-품질-조건.md`): 달력 탭을 열면 달력의 첫 줄이
 * 화면 위 25% 안에서 시작해요. 근무 유형마다 달력 첫 줄(그달 1일 칸)의 위쪽이 화면 높이의 몇 %인지 재고, 천장([CEILING])을
 * 넘으면 실패해요. 천장은 줄어드는 쪽으로만 가요(목표 [GOAL]). 숫자는 build/reports/design-rules.txt에도 남아 PR에 적어요.
 *
 * 사진은 쌓인 사용자의 하루(시드: 안내 카드는 접어 둔 상태)예요. 꼭 봐야 하는 띠(알람이 안 울릴 수 있어요)가 뜬 채로 재고, 빈 달력의
 * 첫 할 일과 종(알림함)은 ReviewP3ShotsTest가 봐요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CalendarStartScreenshotTest : ScreenshotBase() {

    private fun Screen.calendarStart(): Float {
        val first = Fmt.dayTitle(today.withDayOfMonth(1))
        fun look(): Float? {
            val all = nodes()
            val height = all.maxOfOrNull { it.boundsInWindow.bottom } ?: return null
            val cell = all.firstOrNull { n ->
                val b = n.boundsInWindow
                b.height > 1f && n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(first) }
            } ?: return null
            return cell.boundsInWindow.top / height
        }
        look()?.let { return it }
        // A kind of work whose first tab is not the calendar: open 달력 from the bar.
        runCatching { tapText("달력", exact = true, scrolls = 0) }
        return look() ?: 1f
    }

    private fun measure(style: WorkStyle, shot: String) {
        var at = 1f
        shoot(shot, PHONE, style(style) { at = calendarStart() })
        val percent = Math.round(at * 100)
        record(style, percent)
        val ceiling = CEILING.getValue(style)
        assertTrue("${style.label}: 달력이 화면 위 $percent%에서 시작해요(천장 $ceiling%, 목표 $GOAL%). 달력 위는 한 줄(docs/design/공통-부품.md 1절)",
            percent <= ceiling)
    }

    private fun record(style: WorkStyle, percent: Int) {
        val f = File("build/reports/design-rules.txt").apply { parentFile?.mkdirs() }
        val what = "달력 시작 높이 ${style.name}"
        val old = if (f.exists()) f.readLines().filterNot { it.startsWith("$what:") } else emptyList()
        f.writeText((old + "$what: 지금 $percent% · 천장 ${CEILING.getValue(style)}% · 목표 $GOAL%").sorted().joinToString("\n") + "\n")
    }

    @Test fun shift() = measure(WorkStyle.SHIFT, "rvp_start_shift")
    @Test fun day() = measure(WorkStyle.DAY, "rvp_start_day")
    @Test fun hourly() = measure(WorkStyle.HOURLY, "rvp_start_hourly")
    @Test fun field() = measure(WorkStyle.FIELD, "rvp_start_field")
    @Test fun business() = measure(WorkStyle.BUSINESS, "rvp_start_business")
    @Test fun invest() = measure(WorkStyle.INVEST, "rvp_start_invest")

    companion object {
        const val GOAL = 25

        /**
         * 2.0.0 첫 PR(갈래 P)에서 잰 값은 교대 41·일근 46·알바 41·현장 32·가게 32·투자 58(%)이었어요. 달력 위 한 줄 PR(P 3/3: 머리 한 줄,
         * 꼭 봐야 하는 띠 하나, 나머지는 종)에서 모두 [GOAL]이에요.
         */
        val CEILING: Map<WorkStyle, Int> = WorkStyle.entries.associateWith { GOAL }
    }
}
