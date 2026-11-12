package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.AppSnackHost
import com.bamnat.app.ui.components.AskTurn
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.guide.OpenAsk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVP-37] (검토 UIX-62) 갈래 P's root fixes as a person meets them, numbers 2711~2714: the undo line comes before a
 * line that was waiting, one window at a time when the app opens (the law first), no window over a sheet being written in,
 * and a sheet asks before typed words go.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RootFixScreenshotTest : ScreenshotBase() {
    @Composable private fun Page(body: @Composable () -> Unit) {
        BamnatTheme(themeMode = ThemeMode.LIGHT) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) { body() }
                AppSnackHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /** A window the app opens by itself, drawn only on its turn. */
    @Composable private fun Opening(ask: OpenAsk, title: String) {
        var open by remember { mutableStateOf(true) }
        if (open && AskTurn.turn(ask)) AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("$title 닫기") } },
        )
    }

    @Test fun undoComesFirst() = shoot("2711_root_undo_first", FOLDED) {
        content { Page { Text("메모") } }
        settle()
        AppSnack.show("배경을 바꿨어요")
        AppSnack.show("메모를 지웠어요", undo = {})
        settle()
        check(appears("메모를 지웠어요")) { "the undo line did not come" }
        check(appears("되돌리기", exact = true)) { "the undo line has no 되돌리기" }
        check(!shows("배경을 바꿨어요")) { "two lines at once" }
    }

    @Test fun oneWindowWhenTheAppOpens() = shoot("2712_root_one_window", FOLDED) {
        content {
            Page {
                Text("달력")
                Opening(OpenAsk.WHATS_NEW, "새 소식")
                Opening(OpenAsk.NEW_CONSENT, "처리방침 동의")
                Opening(OpenAsk.THEME_EXPIRY, "테마 기간")
            }
        }
        settle()
        check(appears("처리방침 동의")) { "the law did not come first" }
        check(!shows("새 소식") && !shows("테마 기간")) { "more than one window when the app opened" }
        tapText("처리방침 동의 닫기", exact = true)
        settle()
        check(gone("처리방침 동의")) { "the window did not close" }
        check(!shows("새 소식") && !shows("테마 기간")) { "a second window came in the same visit" }
        AskTurn.newVisit()
        settle()
        check(appears("새 소식")) { "the next visit did not bring the next window" }
    }

    @Test fun noWindowOverASheetBeingWritten() = shoot("2713_root_sheet_first", FOLDED) {
        content {
            Page {
                var words by remember { mutableStateOf("오늘 야간 끝나고 장보기") }
                FormSheet(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("저장") } },
                    title = { Text("메모 적기") },
                    text = { OutlinedTextField(words, { words = it }, label = { Text("메모") }) },
                    unsaved = words.isNotBlank(),
                )
                Opening(OpenAsk.WHATS_NEW, "새 소식")
            }
        }
        settle()
        check(appears("메모 적기")) { "the sheet did not open" }
        check(!shows("새 소식")) { "a window came over the sheet being written in" }
    }

    @Test fun aSheetAsksBeforeWordsGo() = shoot("2714_root_close_asks", FOLDED) {
        content {
            Page {
                var open by remember { mutableStateOf(true) }
                var words by remember { mutableStateOf("오늘 야간 끝나고 장보기") }
                if (open) FormSheet(
                    onDismissRequest = { open = false },
                    confirmButton = { TextButton(onClick = { open = false }) { Text("저장") } },
                    title = { Text("메모 적기") },
                    text = { OutlinedTextField(words, { words = it }, label = { Text("메모") }) },
                    unsaved = words.isNotBlank(),
                )
            }
        }
        settle()
        check(appears("메모 적기")) { "the sheet did not open" }
        // The sheet's own X (back goes to the sheet's window, which the test's back() does not reach; FormSheet sends both the
        // same way, through onClose and onDismissRequest).
        tapText("닫기", exact = true)
        settle()
        check(appears("적은 내용을 버릴까요?")) { "the X closed the sheet without asking" }
        tapText("계속 적기", exact = true)
        settle()
        check(gone("적은 내용을 버릴까요?")) { "the ask did not close" }
        check(appears("오늘 야간 끝나고 장보기")) { "the words went" }
    }
}
