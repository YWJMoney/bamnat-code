package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [J133-49] 1.33 (사용자 2026-10-29 13:32 "닫는 창도 없는 것"): every sheet of the app, with an X to close it in the handle's row,
 * so closing never depends on knowing to swipe down or press 뒤로. Same arguments as [ModalBottomSheet]; the X does what
 * swiping it away does ([onDismissRequest]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    /** False for a sheet whose own title row has 닫기 already, so there are not two. */
    closeButton: Boolean = true,
    /** [J133-78] What the X does, when the sheet asks before closing (typed days that would be lost). */
    onClose: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // [J133-49] 1.33 (중간 빌드 확인: 끝까지 펼친 시트의 X가 상태줄 밑에 그려져 눌리지 않았어요): a sheet stops under the status
        // bar, so its handle and X are always where a finger can reach them.
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        sheetState = sheetState,
        dragHandle = if (closeButton) ({ SheetHandle(onClose) }) else ({ BottomSheetDefaults.DragHandle() }),
        content = content,
    )
}

/** The drag handle, and the X at its end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHandle(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
            Icon(Icons.Filled.Close, "닫기")
        }
    }
}
