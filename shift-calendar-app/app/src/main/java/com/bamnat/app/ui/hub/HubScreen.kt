package com.bamnat.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.settings.FreeLookScreen
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle

/**
 * [HUB-01] 1.35 마을 (사용자 2026-10-30 18:46 "나눔판을 이름을 변경하고 메인 메뉴로 … 광장을 흡수한 메인 탭"): one main tab where people
 * of the same kind of work talk (광장), pick and try themes, share and take recipes, and make their own in the studio. It was three
 * places (설정 › 화면 꾸미기, 광장, 광장 › 나눔 판) that looked like different features.
 */
enum class HubSection(val label: String, val line: String) {
    TALK("광장", "같은 일을 하는 분들과 이야기하고, 회사 규칙과 시세를 나눠요"),
    THEMES("테마", "잘 꾸민 테마를 미리 보고 입어요. 기록은 그대로예요"),
    SHARE("장터", "알람·메모 틀·급여 규칙·화면 배치를 받아 쓰고 좋아요·댓글을 남겨요"),
    MAKE("공방", "내 모양과 설정을 직접 만들고 올려요"),
}

/** [HUB-01] The tab's name, kept in one place so the bottom bar, the guide and the search say the same. */
const val HUB_NAME = "마을"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    c: AppContainer,
    style: WorkStyle?,
    talk: @Composable () -> Unit,
    share: @Composable () -> Unit,
    onThemesAll: () -> Unit,
    onMake: () -> Unit,
    onMine: () -> Unit,
    onRequests: () -> Unit,
    onCode: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HubSection.TALK) }
    var freeLook by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Text(HUB_NAME, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.padding(start = 8.dp))
                    style?.let { Text(RecipeDescribe.short(it) + " 분들과", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
                }
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                    HubSection.entries.forEach { s ->
                        Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label) })
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
            when (section) {
                HubSection.TALK -> talk()
                HubSection.SHARE -> share()
                HubSection.THEMES -> Themes(style, onLook = { freeLook = true }, onThemesAll = onThemesAll, onMake = { section = HubSection.MAKE })
                HubSection.MAKE -> Make(onLook = { freeLook = true }, onMake, onMine, onRequests, onCode)
            }
        }
    }
    if (freeLook) {
        val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
        androidx.compose.ui.window.Dialog(onDismissRequest = { freeLook = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) {
                FreeLookScreen(settings, { f -> c.settingsRepository.update(f) }, onBack = { freeLook = false })
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** [HUB-02] 테마: the built-in themes of this kind of work, what is worn and was worn, then others' themes and 직접 꾸미기. */
@Composable
private fun Themes(style: WorkStyle?, onLook: () -> Unit, onThemesAll: () -> Unit, onMake: () -> Unit) = Page {
    Text(HubSection.THEMES.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    com.bamnat.app.ui.recipe.ThemePicker(style)
    Card("다른 분들이 만든 테마", "좋아요가 많은 테마부터 보여요. 미리 보고 입어 본 뒤 마음에 들면 넣어요", "모두 보기", onThemesAll)
    Card("직접 꾸미기", "달력 칸 하나를 꾸며 모든 칸에 쓰고, 메모장·가계부·급여와 화면마다 색과 배경을 바꿔요", "화면 꾸미기 열기", onLook)
    OutlinedButton(onClick = onMake, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("내 테마 만들어 올리기") }
}

/** [HUB-03] 만들기: the studio, my look, my recipes and what others wish for, in one place. */
@Composable
private fun Make(onLook: () -> Unit, onMake: () -> Unit, onMine: () -> Unit, onRequests: () -> Unit, onCode: () -> Unit) = Page {
    Text(HubSection.MAKE.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card("스튜디오에서 만들기", "예시 앱을 탭별로 보며 꾸미고, 알람·메모 틀·규칙도 골라 묶어요. 저장만, 내 앱에만, 올리기 중에서 골라요", "만들기 시작", onMake)
    Card("화면 꾸미기", "내 앱의 모양을 바로 바꿔요. 다 꾸민 뒤 테마로 저장하거나 올릴 수 있어요", "화면 꾸미기 열기", onLook)
    Card("내 레시피·받은 레시피", "올린 것의 좋아요·댓글과 검토 상태를 보고, 받은 것은 7일 안에 되돌려요", "열기", onMine)
    Card("이런 거 있었으면", "필요한 레시피를 부탁하거나, 다른 분의 부탁을 ‘만들게요’로 받아 만들어요", "요청 보기", onRequests)
    OutlinedButton(onClick = onCode, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("코드·링크로 받기") }
}

@Composable
private fun Card(title: String, text: String, action: String, onClick: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}
