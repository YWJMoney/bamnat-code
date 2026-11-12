package com.bamnat.app.ui.calendar

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

/**
 * [UX-03] Hints shown once and never again once read: kept in a small file of their own on the phone,
 * apart from the settings a backup carries, since they are about this phone's first days only.
 */
internal class HintStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hints", Context.MODE_PRIVATE)

    fun done(key: String): Boolean = prefs.getBoolean(key, false)

    fun markDone(key: String) {
        prefs.edit { putBoolean(key, true) }
    }

    companion object {
        /** The calendar's 공유 was pointed out, or used. */
        const val SHARE = "share_hint"

        /** [CAL-17] A long press on a date opens 빠른 설정: said once on the first tap, or found already. */
        const val LONG_PRESS = "long_press_hint"

        /** [PAYR-08] The pay screen's first walkthrough (체험 풀이, notes M12) was closed: it stays closed. */
        const val PAY_WALKTHROUGH = "pay_walkthrough"
        /** U 1.32 [UX132-07] The pay screen's tabs, said once. */
        const val PAY_TABS = "pay_tabs_hint"
        /** U 1.32 [UX132-07] 근무·급여's 고치기 chips, said once. */
        const val PAY_CHIPS = "pay_chips_hint"
    }
}

/**
 * [UX-03] The first time a calendar has shifts and has never been shared, a quiet card under the bar
 * points up at 공유: people did not find it. Closed with ×, or gone once 공유 is used.
 */
@Composable
internal fun ShareHintCard(onShare: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
    /** 2.0.0 [RVH-20] 검토 CAL-25: what is sent, in the calendar's words (근무표, 영업일, 일정). */
    word: String = "근무표") {
    val color = MaterialTheme.colorScheme.secondaryContainer
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        // A small point under 공유, so the card reads as being about that button.
        Box(Modifier.fillMaxWidth().padding(end = 26.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.size(width = 16.dp, height = 8.dp).drawBehind {
                drawPath(Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }, color)
            })
        }
        // The whole card opens 공유; × puts the hint away for good.
        Surface(onClick = onShare, shape = MaterialTheme.shapes.large, color = color, contentColor = onColor) {
            Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text("‘공유’로 $word${com.bamnat.app.ui.Fmt.obj(word)} 보낼 수 있어요", style = MaterialTheme.typography.titleSmall)
                    Text("동료·가족에게 이미지나 링크로 보내요. 링크는 앱이 없어도 열려요.", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "공유 안내 닫기") }
            }
        }
    }
}

/**
 * 1.38 [EASE-30] 같이 보기: the two ways a couple or a family sees each other's shifts, side by side, both without an
 * account (a link opens in any browser; one taken in sits beside the user's own calendar with 같이 쉬는 날).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TogetherSheet(onSend: () -> Unit, onTake: (() -> Unit)?, onDismiss: () -> Unit, shop: Boolean = false, field: Boolean = false) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            // 1.38 PC 4a5c3828: a shop's calendar says 영업일, not 근무.
            // 1.38 PC 12차 유형별 빈틈 10: a 현장 calendar's days are 일정.
            val noun = if (field) "일정" else "근무"
            Text(if (shop) "영업일 같이 보기" else if (field) "일정 같이 보기" else "가족·연인과 근무 같이 보기", style = MaterialTheme.typography.titleLarge)
            Text(if (shop) "가입이나 로그인 없이 링크 하나로 돼요. 가족·직원이 가게 문 여는 날과 쉬는 날을 바로 봐요."
                else "가입이나 로그인 없이 링크 하나로 돼요. 받은 ${noun}은 내 달력 옆에 두고, 같이 쉬는 날이 바로 보여요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TogetherRow(Icons.Outlined.Share, if (shop) "영업일 보내기" else "내 $noun 보내기",
                if (shop) "링크는 앱이 없어도 브라우저로 열려요. 매출·메모는 빼고 보내요" else "링크는 앱이 없어도 브라우저로 열려요. 메모·급여는 빼고 보내요", onSend)
            if (onTake != null) TogetherRow(Icons.Outlined.CalendarMonth, "상대 $noun 받아 보기",
                "받은 링크를 붙여 넣으면 내 달력과 바꿔 가며 봐요", onTake)
        }
    }
}

@Composable
private fun TogetherRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
