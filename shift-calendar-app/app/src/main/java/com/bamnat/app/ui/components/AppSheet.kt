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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged

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
    /**
     * PC 확인 1.37 첫 설정 270fc15e (날짜 시트 높이가 넣기·빼기·다음 날마다 바뀌어 움직이는 사이 '주간 대근'이 잘못 눌림): the sheet
     * never gets shorter while it is open, so a change in it (or the next day in it) does not slide what is under the finger.
     */
    holdHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // PC 확인 7b26965a ("아주 크게"에서도 자유 토의 시트 글자는 그대로): a sheet is a window of its own, whose view gives it the
    // phone's own density again; the app's 글자 크기 (BamnatTheme's LocalDensity) is handed on to it here.
    val appDensity = androidx.compose.ui.platform.LocalDensity.current
    // 1.37 PC 5차 (높음, 한 번): 되돌리기 줄이 '돈 적기'로 바뀌는 순간 연 창이 안 보인 채 열려, 화면을 덮은 창 때문에 2분쯤 멈춘
    // 것처럼 보였어요. 여는 움직임이 끊겨 숨은 채 남으면 한 번 다시 펼쳐요.
    androidx.compose.runtime.LaunchedEffect(sheetState) {
        kotlinx.coroutines.delay(800)
        if (!sheetState.isVisible && sheetState.targetValue == androidx.compose.material3.SheetValue.Hidden) runCatching { sheetState.show() }
    }
    // 1.39.1 [R391-10] Opened while 앱 잠금 holds, the cover comes back over it.
    StaysUnderTheLock()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // [J133-49] 1.33 (중간 빌드 확인: 끝까지 펼친 시트의 X가 상태줄 밑에 그려져 눌리지 않았어요): a sheet stops under the status
        // bar, so its handle and X are always where a finger can reach them.
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        sheetState = sheetState,
        dragHandle = if (closeButton) ({ SheetHandle(onClose) }) else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides appDensity) {
            if (!holdHeight) content()
            else {
                var tallest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val min = with(appDensity) { tallest.toDp() }
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().heightIn(min = min)
                    .onSizeChanged { if (it.height > tallest) tallest = it.height }) { content() }
            }
        }
    }
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

/**
 * 1.37 PC 확인 6a4efc92 (높음, 돈 적기 시트를 아래로 끌자 적은 금액·날짜·카드가 말없이 사라졌어요): a form sheet that asks before
 * a swipe, 뒤로 or a tap outside throws away what was typed ([unsaved]); the X and the form's own 닫기 still close at once.
 * Same as [FormSheet]'s guard, for sheets that lay out their own form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardedSheet(
    onDismissRequest: () -> Unit,
    unsaved: Boolean,
    modifier: Modifier = Modifier,
    closeButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var asking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val keep by androidx.compose.runtime.rememberUpdatedState(unsaved)
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { androidx.compose.material3.Text("적은 내용을 버릴까요?") },
        text = { androidx.compose.material3.Text("저장하지 않고 닫으면 지금 적은 것이 사라져요.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { asking = false; onDismissRequest() }) { androidx.compose.material3.Text("버리기") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { androidx.compose.material3.Text("계속 적기") } },
    )
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { v ->
        if (v == androidx.compose.material3.SheetValue.Hidden && keep) { asking = true; false } else true
    })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 1.38 (1.37 PC LOW 621c55f4, 아래로 쓸면 적던 금액이 사라져요): a fast fling can still carry the sheet to Hidden past the check above
    // (the drag settles before confirmValueChange is asked again). If the sheet ever ends up hidden with something typed, it comes
    // back up and asks, rather than staying open unseen and losing what was typed when it is opened next.
    androidx.compose.runtime.LaunchedEffect(state) {
        var opened = false
        androidx.compose.runtime.snapshotFlow { state.currentValue }.collect { v ->
            if (v != androidx.compose.material3.SheetValue.Hidden) opened = true
            else if (opened && keep) { asking = true; runCatching { state.show() } }
        }
    }
    AppSheet(
        // 뒤로 hides the sheet without the swipe's check, so it comes back up and asks (as FormSheet does).
        onDismissRequest = { if (!keep) onDismissRequest() else { asking = true; scope.launch { state.show() } } },
        modifier = modifier, sheetState = state, closeButton = closeButton, onClose = onDismissRequest, content = content,
    )
}
