package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.CalendarStorage
import com.bamnat.app.ui.calendar.SentLine
import com.bamnat.app.ui.diag.DiagnosticsScreen
import com.bamnat.app.ui.diag.DiagnosticsViewModel
import com.bamnat.core.plaza.TeamLinkText
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * 2.0.0 [RVL-56][RVL-59][RVL-65] 갈래 L 8차의 화면 (디자인 품질 조건): 달력 공유 창의 '보내지 못한 변경'이 빨간 상자 대신 공통 안내 카드(주의)로,
 * 서버가 받지 않은 까닭과 고칠 곳을 한 줄로(검토 U12-07); 알람 점검 첫 줄이 실제 단추 이름 '점검 내용과 함께 문의하기'로(검토 DATA-25); 데이터 점검의
 * '목록에 없는 달력 파일'이 빨강이 아닌 주의 색과 ※ 표시로(검토 DATA-16, 조건 6·14). 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlTrustScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val stray get() = app.getDatabasePath(CalendarStorage.fileName("stray1"))

    @After fun tidy() { stray.delete() }

    // ---- RVL-56: a change not sent yet, and one the server turned down with its reason ----
    private fun sent(refused: Boolean, why: String? = null): Screen.() -> Unit = {
        val at = Instant.parse("2026-11-12T03:20:00Z")
        content {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("달력 공유", style = MaterialTheme.typography.titleMedium)
                SentLine(at, unsent = true, busy = false, refused = refused, why = why, onSend = {})
            }
        }
        settle()
        assertTrue(appears("지금 보내기", exact = true))
        assertTrue(appears("마지막으로 보낸 때"))
    }

    private val phoneWhy = TeamLinkText.oneLine("phone", "유나 010-1234-5678", "type")

    private fun waiting(): Screen.() -> Unit = {
        sent(refused = false).invoke(this)
        assertTrue(appears("보내지 못한 변경이 있어요"))
    }

    private fun refusedWithWhy(): Screen.() -> Unit = {
        sent(refused = true, why = phoneWhy).invoke(this)
        assertTrue(appears("전화번호가 있어 못 보냈어요"))
        assertTrue(appears("근무 이름에서 빼 주세요"))
        assertFalse(shows("인터넷이 되면"))
    }

    @Test fun waitingFoldLight() = shoot("rvl56_unsent_fold_light_100", FOLDED, waiting())
    @Test fun waitingFoldDarkHuge() = shoot("rvl56_unsent_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, waiting())
    @Test fun refusedFoldLight() = shoot("rvl56_refused_why_fold_light_100", FOLDED, refusedWithWhy())
    @Test fun refusedFoldDarkHuge() = shoot("rvl56_refused_why_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, refusedWithWhy())
    @Test fun refusedOpenLight() = shoot("rvl56_refused_why_open_light_100", UNFOLDED, refusedWithWhy())

    // ---- RVL-59: 알람 점검's first line names the button that is there ----
    private fun diagnostics(): Screen.() -> Unit = {
        content { DiagnosticsScreen(DiagnosticsViewModel(c), onBack = {}) }
        settle()
        assertTrue(appears("‘점검 내용과 함께 문의하기’"))
        assertFalse(shows("오른쪽 위 복사 단추"))
    }

    @Test fun diagnosticsFoldLight() = shoot("rvl59_diag_top_fold_light_100", FOLDED, diagnostics())
    @Test fun diagnosticsFoldDarkHuge() = shoot("rvl59_diag_top_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, diagnostics())

    // ---- RVL-65: a calendar file the list does not name is a note in the caution colour ----
    private fun strayFile(): Screen.() -> Unit = {
        stray.apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
        val vm = DiagnosticsViewModel(c)
        content { DiagnosticsScreen(vm, onBack = {}) }
        settle()
        vm.runDataCheck()
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (vm.dataCheck.value == null) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for the data check" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
        reveal("목록에 없는 달력 파일 1개")
        assertTrue(shows("※ 목록에 없는 달력 파일 1개"))
    }

    @Test fun strayFoldLight() = shoot("rvl65_stray_file_fold_light_100", FOLDED, strayFile())
    @Test fun strayFoldDarkHuge() = shoot("rvl65_stray_file_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, strayFile())
    @Test fun strayOpenLight() = shoot("rvl65_stray_file_open_light_100", UNFOLDED, strayFile())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
    }
}
