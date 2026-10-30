package com.bamnat.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
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
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle

/**
 * [HUB-01] 1.35 마을 (사용자 2026-10-30 18:46 "나눔판을 이름을 변경하고 메인 메뉴로 … 광장을 흡수한 메인 탭"): one main tab where people
 * of the same kind of work talk (광장), pick and try themes, share and take recipes, and make their own in the studio. It was three
 * places (설정 › 화면 꾸미기, 광장, 광장 › 나눔 판) that looked like different features.
 */
enum class HubSection(val label: String, val line: String) {
    TALK("광장", "같은 일을 하는 분들과 이야기하고, 회사 규칙과 시세를 나눠요"),
    // 1.35 (사용자 2026-10-30 21:37 "장터를 2번째로 옮기고 테마를 3번째로"): 고르고 받기는 장터, 입고 간직하기는 테마, 만들기는 공방.
    SHARE("장터", "테마·알람·메모 틀·급여 규칙·화면 배치를 받아 쓰고 좋아요·댓글을 남겨요"),
    THEMES("테마", "내가 입은 테마와 간직한 테마예요. 기록은 그대로예요"),
    MAKE("공방", "내 테마와 설정을 레시피로 만들어 올려요"),
}

/** [HUB-01] The tab's name, kept in one place so the bottom bar, the guide and the search say the same. */
const val HUB_NAME = "마을"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    c: AppContainer,
    style: WorkStyle?,
    talk: @Composable () -> Unit,
    /** 장터, opened on one kind ("skin" from 테마's 장터에서 새 테마 찾기) or on all. */
    share: @Composable (kind: String?) -> Unit,
    onMake: () -> Unit,
    onMine: () -> Unit,
    onRequests: () -> Unit,
    onCode: () -> Unit,
    /** 스튜디오, started from "mine", "plain" or "theme:<key>" ([com.bamnat.app.ui.studio.StudioViewModel]). */
    onStudio: (String) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HubSection.TALK) }
    var shareKind by rememberSaveable { mutableStateOf<String?>(null) }
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
                        Tab(selected = section == s, onClick = { if (s == HubSection.SHARE && section != s) shareKind = null; section = s }, text = { Text(s.label) })
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
            when (section) {
                HubSection.TALK -> talk()
                HubSection.SHARE -> share(shareKind)
                HubSection.THEMES -> Themes(style, onLook = { onStudio("mine") }, onFind = { shareKind = "skin"; section = HubSection.SHARE })
                HubSection.MAKE -> Make(c, style, onStudio, onMake, onMine, onRequests, onCode)
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

/** [HUB-02] 테마: the user's own page (지금 입은 테마, 내 테마, 입었던 테마); new themes are found in 장터, made in 공방. */
@Composable
private fun Themes(style: WorkStyle?, onLook: () -> Unit, onFind: () -> Unit) = Page {
    Text(HubSection.THEMES.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    com.bamnat.app.ui.recipe.ThemePicker(style, onFind = onFind, onFix = onLook)
}

/** [HUB-03] 공방: the studio, my recipes and what others wish for, in one place. */
/**
 * [HUB-03] 공방 (사용자 2026-10-30 21:39 "마을 탭 자체를 아주 좋게"): the studio first, large, with my look as it is now; my themes to go on
 * from in a row; then a recipe with 알람·메모 틀·규칙 too, my recipes, the wishes and a code.
 */
@Composable
private fun Make(c: AppContainer, style: WorkStyle?, onStudio: (String) -> Unit, onMake: () -> Unit, onMine: () -> Unit, onRequests: () -> Unit,
                 onCode: () -> Unit) = Page {
    Text(HubSection.MAKE.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
    val mine by androidx.compose.runtime.produceState<com.bamnat.core.recipe.Recipe?>(null, lease) {
        value = com.bamnat.core.recipe.Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
    }
    SoftCard {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            mine?.let { r ->
                Box(Modifier.clickable(onClickLabel = "스튜디오 열기") { onStudio("mine") }) {
                    com.bamnat.app.ui.recipe.ScaledPhone(r, style, com.bamnat.core.layout.TabOrder.CALENDAR, {}, 0.3f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("스튜디오", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("달력 칸, 패턴, 색·배경, 카드 배치, 위젯까지 한곳에서 꾸며요. 입히기 전까지 내 앱은 그대로예요",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Button(onClick = { onStudio("mine") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("지금 모양에서 꾸미기") }
                TextButton(onClick = { onStudio("plain") }) { Text("처음 모양에서 새로") }
            }
        }
    }
    val themes = lease.history.sortedByDescending { it.own }.take(10)
    if (themes.isNotEmpty() || kept.isNotEmpty()) {
        Text("이어서 꾸미기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEach { w ->
                androidx.compose.material3.AssistChip(onClick = { onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_THEME + w.key) },
                    label = { Text((if (w.own) "🎨 " else "") + w.title, maxLines = 1) })
            }
        }
        if (kept.isNotEmpty()) Text("보관함에 조각 ${kept.size}개가 있어요. 스튜디오의 ‘보관함’에서 얹어 섞어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card("레시피 만들기", "테마에 알람·메모 틀·급여 규칙까지 묶어 장터에 올려요", "레시피 만들기", onMake)
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
