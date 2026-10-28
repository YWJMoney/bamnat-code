package com.bamnat.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A 1.32 [FIX132-05] Puts [text] on the clipboard and says so. */
fun copyText(context: Context, label: String, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
    // Android 13+ shows its own note for a copy; older phones get one line.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
}

/**
 * A 1.32 [FIX132-05] (사용자 2026-10-28 "메모 복사 선택 시 길면 스크롤 안 됨"): the whole text in a box that scrolls itself, so
 * dragging a selection handle to the edge moves the text along (a text field's own scrolling follows the handle; a page that
 * scrolls around plain text does not). '모두 선택', '여기부터 끝까지' (the caret to the end) and '복사' sit under it.
 */
@Composable
fun CopyTextSheet(title: String, text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var value by remember(text) { mutableStateOf(TextFieldValue(text)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp).imePadding(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("길게 눌러 고르고, 손잡이를 위아래 끝으로 끌면 글이 따라 움직여요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = value, onValueChange = { value = it.copy(text = text) }, readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { value = value.copy(selection = TextRange(0, text.length)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모두 선택") }
                    TextButton(onClick = { value = value.copy(selection = TextRange(minOf(value.selection.min, text.length), text.length)) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("여기부터 끝까지") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("닫기") }
                    Button(onClick = {
                        val s = value.selection
                        copyText(context, title, if (s.collapsed) text else text.substring(s.min, s.max))
                        onDismiss()
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (value.selection.collapsed) "전체 복사" else "고른 부분 복사") }
                }
            }
        }
    }
}
