package com.bamnat.app.ui.imports

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.bamnat.app.imports.PdfLock
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.Button

/**
 * 2.0.0 [RVL-41] (검토 U07b-10·I07bb-01, 상) A locked PDF opened where it was picked: 1.39 said "암호를 풀고 다시 저장해 주세요", which a
 * phone can hardly do, so a payslip mailed with the 생년월일 on it could not be used at all. A sheet from the bottom (a field and
 * the keyboard, 공통 부품 [FormSheet]) with the field ready to type in and one filled button ('열기'); a wrong password stays here and
 * says so under the field until it is changed. The password opens this file this once, on the phone: it is not saved, sent or
 * logged, and the field is not kept when the screen is made again.
 * [payslip]: a 명세서 says where its password usually comes from (the 생년월일); a 근무표 asks for the one it came with.
 */
@Composable
fun PdfPasswordDialog(lock: PdfLock, working: Boolean, payslip: Boolean, onOpen: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    // The wrong-password line goes once the password is changed, and comes back only for the next try.
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(working) { if (working) edited = false }
    val ok = password.isNotEmpty() && !working
    val open = { if (ok) onOpen(password.toCharArray()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    FormSheet(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("암호가 걸린 PDF예요") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (payslip) "메일로 받은 명세서는 보통 생년월일 6자리(예: 900101)가 암호예요." else "파일을 받을 때 함께 받은 암호를 넣어 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                com.bamnat.app.ui.settings.PasswordField(
                    password, { password = it; edited = true }, "PDF 암호",
                    error = if (lock == PdfLock.WRONG && !working && !edited) "암호가 맞지 않아요. 다시 넣어 주세요" else null,
                    onDone = open,
                    modifier = Modifier.focusRequester(focus),
                )
                Text("암호는 이 파일을 여는 데만 쓰고 저장하지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = open, enabled = ok, modifier = Modifier.heightIn(min = 48.dp)) {
                // As [com.bamnat.app.ui.components.SaveButton] says it is working: a big scan can take a few seconds.
                if (working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text("여는 중…")
                } else Text("열기")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } },
        unsaved = password.isNotEmpty() && !working,
    )
}

/**
 * 2.0.0 [RVL-41] The password window for a screen that reads a picked file in place (리더 팀 근무표, 사장님 직원 명단): [ask] when a
 * reading came back locked, [done] when one came back otherwise; [read] reads the file again with the password typed.
 */
class PdfUnlock internal constructor() {
    internal var asking by mutableStateOf<Pair<Uri, PdfLock>?>(null)
    internal var working by mutableStateOf(false)

    fun ask(uri: Uri, lock: PdfLock) { asking = uri to lock; working = false }

    fun done() { asking = null; working = false }
}

@Composable
fun rememberPdfUnlock(payslip: Boolean = false, read: (Uri, CharArray) -> Unit): PdfUnlock {
    val u = remember { PdfUnlock() }
    u.asking?.let { (uri, lock) ->
        PdfPasswordDialog(lock, u.working, payslip, onOpen = { pw -> u.working = true; read(uri, pw) }, onDismiss = u::done)
    }
    return u
}
