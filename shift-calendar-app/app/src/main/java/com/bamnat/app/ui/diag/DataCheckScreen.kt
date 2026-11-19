package com.bamnat.app.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.company.InquiryKind
import com.bamnat.app.diag.DataCheck
import com.bamnat.app.diag.DataSelfCheck
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.settings.InquiryLinks
import com.bamnat.app.ui.settings.InquiryStart
import com.bamnat.app.ui.settings.SwitchRow
import com.bamnat.app.ui.theme.color

/** 2.0.0 [RVL-70] The screen's title, 설정 › 데이터's row and its search entry. */
const val DATA_CHECK_TITLE = "데이터 점검"

/** 2.0.0 [RVL-71] The switch on [DataCheckScreen], also found by settings search. */
const val DATA_SELF_CHECK_ROW = "일주일에 한 번 스스로 살펴보기"

/**
 * 2.0.0 [RVL-70] (검토 U12-09·T12-07) 설정 › 데이터 › 데이터 점검. 데이터 점검 and 서버 연결 were only inside 알람 점검, a name that 현장·가게·투자
 * (who use no shift alarm) have no reason to open: here they are where the records are. The check runs as the screen opens (it reads,
 * changes nothing); [DataSelfCheck] (일주일에 한 번) says what it put right by itself and is turned off here. 알람 점검 keeps its own
 * 데이터 점검 card ([DataCheckCard]), the same part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCheckScreen(vm: DiagnosticsViewModel, onBack: () -> Unit) {
    val c = LocalContext.current.appContainer
    val lines by vm.dataCheck.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (lines == null) vm.runDataCheck() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { com.bamnat.app.ui.FitText(DATA_CHECK_TITLE, MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 조건 12: on the unfolded width the body keeps to 640dp.
            val wide = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            item { DataCheckCard(vm, wide, intro = false) }
            item {
                val self by c.dataSelfCheck.state.collectAsStateWithLifecycle()
                val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
                // The switch says what the card is: no title above it saying it again.
                SectionCard(title = null, modifier = wide) {
                    SwitchRow(
                        DATA_SELF_CHECK_ROW,
                        if (!settings.dataSelfCheck) "꺼져 있어요. 켜면 휴대폰을 쓰지 않을 때 살펴보고, 고칠 수 있는 것은 고쳐 ‘밤낮이 한 일’에 적어요"
                        else DataSelfCheck.words(self),
                        settings.dataSelfCheck,
                    ) { on -> c.settingsRepository.update { it.copy(dataSelfCheck = on) } }
                }
            }
            item { androidx.compose.foundation.layout.Box(wide) { ServerCheckCard() } }
            item {
                SectionCard(title = "그래도 이상하면", modifier = wide) {
                    Text("점검한 줄을 붙여 물어볼 수 있어요. 근무·메모·금액은 담지 않아요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { InquiryLinks.ask(InquiryStart(about = "데이터", kind = InquiryKind.BUG, withCheck = true)) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("점검 내용과 함께 문의하기") }
                }
            }
        }
    }
}

/**
 * [QA-07] 330 데이터 점검's lines and its one button, in 알람 점검 and in [DataCheckScreen]. 2.0.0 [RVL-65] (조건 6·14) a note is caution
 * with a mark of its own (※), not red: red only for data that is broken. The lines are said as they come (liveRegion).
 */
@Composable
internal fun DataCheckCard(vm: DiagnosticsViewModel, modifier: Modifier = Modifier, intro: Boolean = true) {
    val lines by vm.dataCheck.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    // On its own screen the bar says 데이터 점검 already: the card says what it looks at.
    SectionCard(title = if (intro) DATA_CHECK_TITLE else "기록과 백업", modifier = modifier) {
        Text(
            if (intro) "근무·기록 데이터가 온전한지, 백업이 제대로 만들어지는지 휴대폰 안에서 살펴봐요. 아무것도 바꾸거나 보내지 않아요."
            else summary(lines, checking),
            style = if (intro) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (intro) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        // On its own screen (조건 4) what is fine folds under one line and what needs a look stays; 알람 점검 lists them all as before.
        var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        val fine = lines.orEmpty().count { it.ok }
        lines?.filter { intro || open || !it.ok }?.forEach { line ->
            Text((if (line.ok) "✓ " else if (line.note) "※ " else "! ") + line.text, style = MaterialTheme.typography.bodySmall,
                color = when {
                    line.ok -> MaterialTheme.colorScheme.onSurface
                    line.note -> com.bamnat.app.ui.theme.Tone.Caution.color()
                    else -> MaterialTheme.colorScheme.error
                })
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.runDataCheck() }, enabled = !checking, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (checking) "점검하는 중…" else if (lines == null) DATA_CHECK_TITLE else "다시 점검")
            }
            if (!intro && fine > 0) TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (open) "접기" else "이상 없는 ${fine}가지 보기")
            }
        }
    }
}

/** The card's first line on [DataCheckScreen]: what the check found, in one sentence (조건 4). */
internal fun summary(lines: List<DataCheck.Line>?, checking: Boolean): String {
    if (lines == null) return if (checking) "휴대폰 안의 기록과 백업을 살펴보는 중이에요." else "휴대폰 안의 기록과 백업을 살펴봐요. 아무것도 바꾸거나 보내지 않아요."
    val bad = lines.count { !it.ok && !it.note }
    val notes = lines.count { it.note }
    return when {
        bad > 0 -> "고칠 것이 ${bad}개 있어요."
        notes > 0 -> "기록은 온전해요. 알아 두실 것이 ${notes}개 있어요."
        else -> "모두 온전해요. ${lines.size}가지를 살펴봤어요."
    }
}
