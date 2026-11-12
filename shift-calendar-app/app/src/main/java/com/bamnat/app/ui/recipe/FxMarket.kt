package com.bamnat.app.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.logic.FxAccess
import com.bamnat.app.ui.components.ProBadge
import com.bamnat.core.formula.FxPro
import com.bamnat.core.formula.FxTaste
import com.bamnat.core.time.Today
import java.time.Instant

/**
 * 1.39 [MKC-04] 장터에서 무료와 프로를 분명하게 (사용자 2026-11-07 "무료 프로의 구분은 확실히 해야 해", "앱코드 + 스튜디오 + 장터 및 테마 페이지의
 * 디자인·인터랙션 세 가지가 완벽하게 맞아떨어져야"). 판정은 FX 갈래의 [FxAccess]·[FxPro] 한 곳을 그대로 써요. 여기서는 보여 주기만 해요.
 */
object FxMarket {
    /** The 하루 써 보기 hours, said in words (FxTaste.HOURS). */
    const val HOURS = 24

    /** "11/8 18:30" — the day and time a 하루 써 보기 ends. */
    fun untilWords(until: Instant): String = until.atZone(Today.zone()).let { "%d/%d %d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) }

    /** How long is left, in whole hours (or minutes under an hour). */
    fun leftWords(until: Instant, now: Instant = Today.instant()): String {
        val m = FxTaste.left(until, now).toMinutes()
        return if (m >= 60) "${m / 60}시간 남았어요" else "${m.coerceAtLeast(1)}분 남았어요"
    }

    /**
     * 1.39 [MKD-13] 장치 이름을 누르면 나오는 한 줄 (사용자는 '값 칸'이 무엇인지 몰라요). 이 앱이 모르는 새 장치는 이름만.
     */
    fun explain(p: FxPro.Part): String = when (p.key) {
        "values" -> "작품이 세는 숫자예요. 커피 잔 수, 이번 달 야간 수처럼 단추로 올리고 화면에 보여요"
        "valueAct" -> "단추나 규칙이 값 칸 숫자를 올리거나 정해요"
        "valueRead" -> "조건이나 알림 글에 값 칸 숫자를 넣어 써요. 예: 10잔이 넘으면 알림"
        "outside" -> "앱을 열지 않고 홈 화면 위젯·알림 막대 빠른 설정·바로가기에서 작품 단추를 눌러요"
        "notifyButtons" -> "작품이 보낸 알림에 단추가 붙어, 누르면 앱을 열지 않고 바로 기록돼요"
        "link" -> "대근·특근·연차 날, 남은 할 일, 잠잔 시간처럼 내가 넣은 근무와 다른 탭 기록에 따라 작품 모양이 바뀌어요"
        else -> p.label
    }

    /** What the card says under its title for [status] (with 프로 [pro]). */
    fun stateLine(pro: Boolean, status: FxTaste.Status, now: Instant = Today.instant()): String = when {
        pro -> "프로라서 넣으면 ✦ 장치까지 바로 다 돌아요"
        status is FxTaste.Status.Using -> "하루 써 보기 중이에요 · ${untilWords(status.until)}까지 (${leftWords(status.until, now)}). 지금 넣으면 ✦ 장치까지 돌아요"
        status is FxTaste.Status.Used -> "하루 써 보기를 이미 썼어요. 무료인 모양·규칙·단추는 지금도 넣을 수 있고, ✦ 장치는 프로에서 다시 켜져요"
        else -> "프로가 아니어도 이 작품을 한 번, ${HOURS}시간 동안 ✦ 장치까지 진짜로 써 볼 수 있어요. 끝나면 무료인 것은 남고 ✦ 장치만 쉬어요"
    }
}

/**
 * 1.39 [MKC-04] 상세 머리의 한 줄: 프로 기능이 든 작품은 앱 어디서나 같은 프로 표와 무엇이 들었는지, 아니면 '무료'.
 */
@Composable
internal fun FxHeadLine(parts: Set<FxPro.Part>) {
    val c = LocalContext.current.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    if (parts.isEmpty()) {
        Text("무료 · 모두 무료로 넣고 쓸 수 있어요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        return
    }
    val locked = !gate.allows(com.bamnat.app.tier.Feature.FX_WORKS)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProBadge(locked = locked)
        Text("✦ " + FxPro.plain(parts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

/**
 * 1.39 [MKC-04] 넣기 단추 바로 위: 무엇이 무료고 무엇이 프로인지, 그리고 이 폰에서 지금 할 수 있는 것.
 * - 프로: 넣으면 다 돌아요.
 * - 무료, 처음: '하루 써 보기' (한 번, 24시간, 진짜로 넣고 ✦까지) · '프로 보기'. 3분 써 보기·입어 보기는 아래 단추 그대로 (무료).
 * - 써 보는 중: 언제까지인지. 다 썼으면: 그동안 한 일과 '프로 보기'.
 * [onTaste]는 하루 써 보기를 시작한 뒤 넣는 길(입어 보기 또는 넣기 확인)을 여는 일이에요.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FxCard(parts: Set<FxPro.Part>, code: String, applied: Boolean, ladder: Boolean = false, onTaste: () -> Unit) {
    if (parts.isEmpty()) return
    val c = LocalContext.current.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val taste by FxAccess.taste(c).collectAsStateWithLifecycle()
    val pro = gate.allows(com.bamnat.app.tier.Feature.FX_WORKS)
    val status = remember(taste, code) { FxAccess.status(c, code) }
    var asking by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProBadge(locked = !pro)
                Text("프로 기능이 든 작품", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { heading() })
            }
            Text("✦ 들어 있는 것: " + FxPro.plain(parts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            // 1.39 [MKD-13] 장치마다 무엇인지: 누르면 한 줄 설명.
            // [RVM-12] PC-59: one chip a thing it does for me (홈 화면 단추·알림 단추·자동 기록 …), its parts told under it.
            var told by remember(parts) { mutableStateOf<FxPro.Plain?>(null) }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FxPro.Plain.of(parts).forEach { p ->
                    com.bamnat.app.ui.components.FilterChip(selected = told == p, onClick = { told = if (told == p) null else p },
                        label = { Text("✦ " + p.label + " ?") }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            told?.let { p ->
                parts.filter { it in p.parts }.forEach { part ->
                    Text(part.label + ": " + FxMarket.explain(part), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Text("1.38까지 있던 모양·규칙·단추는 무료 그대로예요. 1.39부터 새로 연 ✦ 장치만 프로에서 돌아요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(FxMarket.stateLine(pro, status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            if (!pro && status is FxTaste.Status.Used) FxAccess.summary(c)?.let {
                Text("써 보는 동안 한 일: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            if (refused) Text("이 작품은 하루 써 보기를 이미 썼어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!pro) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // 1.39 [MKD-43] 상세에서는 '넣기 전에 해 보기' 사다리 ③이 하루 써 보기를 맡아요.
                if (!ladder && status is FxTaste.Status.Ready) Button(onClick = { asking = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("하루 써 보기") }
                if (!ladder && status is FxTaste.Status.Using && !applied) Button(onClick = onTaste, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("넣고 써 보기") }
                OutlinedButton(onClick = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PRO) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("프로 보기") }
            }
        }
    }
    if (asking) FxTasteAsk(parts, code, onDismiss = { asking = false }, onRefused = { refused = true }, onTaste = onTaste)
}

/** 1.39 [MKC-04] 이 폰에서 지금: 프로인지, 이 작품의 하루 써 보기가 어디쯤인지. */
@Composable
internal fun fxNow(code: String): Pair<Boolean, FxTaste.Status> {
    val c = LocalContext.current.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val taste by FxAccess.taste(c).collectAsStateWithLifecycle()
    val status = remember(taste, code) { FxAccess.status(c, code) }
    return gate.allows(com.bamnat.app.tier.Feature.FX_WORKS) to status
}

/** 1.39 [MKC-04] 하루 써 보기를 시작할지 묻는 창 (상세 카드와 사다리 ③이 같이 써요). */
@Composable
internal fun FxTasteAsk(parts: Set<FxPro.Part>, code: String, onDismiss: () -> Unit, onRefused: () -> Unit, onTaste: () -> Unit) {
    val c = LocalContext.current.appContainer
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("하루 써 보기를 시작할까요?") },
        text = {
            Text("이 작품을 내 앱에 진짜로 넣고, ${FxMarket.HOURS}시간 동안 ✦ ${FxPro.plain(parts)}까지 다 써 봐요. 작품마다 한 번뿐이에요. " +
                "끝나면 넣은 것 가운데 무료인 것은 그대로 두고 ✦ 장치만 쉬어요. 값 칸에 쌓인 것은 지우지 않아서 프로가 되면 이어서 돌아요")
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (FxAccess.start(c, code)) onTaste() else onRefused()
            }) { Text("시작하고 넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
