package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.CompanyHit
import com.bamnat.core.plaza.CompanyNames
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 2.0.0 [RVN-19] 검토 COMP-20·I10ab-01·U10a-11·T10a-10 회사 이름 도우미. A name typed by hand used to become a new company as it
 * was, so one company split into several and its 맞아요 never met. Wherever a new company's name is about to be used (회사 찾기의
 * '여기 없어요, 직접 추가', 회사를 못 찾았을 때, '우리 회사 근무표 올리기', '내 설정 나누기'), the name can be checked and fixed,
 * a 사업장 has its own box, and up to three companies the phone already knows that it may mean come first.
 */
sealed interface NameLike {
    val name: String
    val why: CompanyNames.Why
    val site: String
    val distance: Int

    /** A company on the plaza, with people behind it. */
    data class Server(val hit: CompanyHit, override val name: String, override val why: CompanyNames.Why, override val site: String,
        override val distance: Int) : NameLike

    /** A row of the public cycles (기사·채용 공고·노무 사이트). */
    data class Public(val row: PublicCycle, override val name: String, override val why: CompanyNames.Why, override val site: String,
        override val distance: Int) : NameLike
}

/** How long typing rests before the names are looked through. */
internal const val NAME_LIKE_PAUSE_MS = 250L

/**
 * The names [typed] may mean: the plaza's ([server]) and the public rows', the plaza's first among equals, [CompanyNames.MAX] at
 * most. When one is the same name written another way, only that one: the rest would be noise next to it.
 */
fun nameLikes(c: AppContainer, typed: String, server: Boolean): List<NameLike> {
    val fromServer = if (server) c.companySearch.similar(typed).map { NameLike.Server(it.item, it.name, it.why, it.site, it.distance) } else emptyList()
    val rows = PublicCycles.all
    val fromPublic = CompanyNames.similar(typed, rows, { listOf(it.company) })
        .map { NameLike.Public(it.item, it.name, it.why, it.site.ifEmpty { it.item.site }, it.distance) }
    val all = (fromServer + fromPublic).withIndex()
        .sortedWith(compareBy({ it.value.why.ordinal }, { it.value.distance }, { it.index })).map { it.value }
    val same = all.filter { it.why == CompanyNames.Why.SAME }
    return (same.ifEmpty { all }).distinctBy { l -> PublicCycles.norm(l.name) + "·" + ((l as? NameLike.Public)?.row?.site.orEmpty()) }
        .take(CompanyNames.MAX)
}

/** [nameLikes] for the name as it is typed, worked out off the frame's thread after a short rest in typing. */
@Composable
fun rememberNameLikes(typed: String, server: Boolean = true): List<NameLike> {
    val c = LocalContext.current.appContainer
    val likes by produceState(emptyList<NameLike>(), typed, server) {
        if (typed.isBlank()) { value = emptyList(); return@produceState }
        delay(NAME_LIKE_PAUSE_MS)
        value = withContext(Dispatchers.Default) { runCatching { nameLikes(c, typed, server) }.getOrDefault(emptyList()) }
    }
    return likes
}

/**
 * "혹시 이 회사인가요?" and one row for each of [likes] (the name, why it is offered, who stands behind it, and [action]); nothing
 * when there is none. A row is a card with its action in words, never a filled button: the screen's filled one stays its own.
 */
@Composable
fun NameLikeList(likes: List<NameLike>, action: String, onPick: (NameLike) -> Unit, modifier: Modifier = Modifier) {
    if (likes.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("혹시 이 회사인가요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        likes.forEach { l -> NameLikeRow(l, action) { onPick(l) } }
    }
}

@Composable
private fun NameLikeRow(l: NameLike, action: String, onClick: () -> Unit) {
    val title = when (l) {
        is NameLike.Server -> l.name
        is NameLike.Public -> listOf(l.name, l.row.site).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    val behind = when (l) {
        is NameLike.Server -> trustText(l.hit)
        is NameLike.Public -> PublicCycles.BADGE
    }
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClickLabel = "$title $action", onClick = onClick)) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NameInitial(l.name)
            Spacer(Modifier.width(12.dp))
            val words: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(CompanyNames.reason(l.why, l.site), behind).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val act: @Composable () -> Unit = { Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            // 2.0.0 [RVN-19] 총괄 사진 검토 (rvn_55·58, 조건 12): at 200% the action at the end left the name a narrow column ('(주)예 /
            // 시정밀') and the line under it five lines. From 130%, or when the name would get less than 160dp, the action goes under
            // the words at the end and the words take the whole width.
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= BIG_FONT) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    words()
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { act() }
                }
            } else com.bamnat.app.ui.components.SideOrBelow(Modifier.weight(1f), minStart = 160.dp, start = words, end = act)
        }
    }
}

/** From this font scale a row's action word goes under its words (as [com.bamnat.app.ui.components.PairRow] does at 150%). */
private const val BIG_FONT = 1.3f

/** A company's first letter in a circle, as the company rows of 회사 찾기 show it. */
@Composable
internal fun NameInitial(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(38.dp).clearAndSetSemantics { }) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().removePrefix("(주)").removePrefix("㈜").trim().take(1), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** The line under the name box: what keeps it from being a company, a second look for a short name, or how names are matched. */
internal fun nameHelp(name: String): String = CompanyNames.problem(name).takeIf { name.isNotBlank() }?.let(CompanyNames::words)
    ?: if (CompanyNames.short(name)) "짧은 이름은 다른 회사와 섞이기 쉬워요. 정식 이름이 맞는지 봐 주세요"
    else "띄어쓰기나 (주)는 달라도 같은 곳으로 찾아요"

/**
 * The name and 사업장 boxes for a company that is not on the list yet. The 사업장 apart keeps one company one: '현대제철 당진공장'
 * typed into the name made a company of its own.
 */
@Composable
fun CompanyNameFields(
    name: String,
    onName: (String) -> Unit,
    site: String,
    onSite: (String) -> Unit,
    modifier: Modifier = Modifier,
    nameLabel: String = "회사·기관 이름",
) {
    val problem = name.isNotBlank() && CompanyNames.problem(name) != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { onName(it.take(40)) },
            label = { Text(nameLabel) }, singleLine = true, isError = problem,
            supportingText = { Text(nameHelp(name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = site, onValueChange = { onSite(it.take(SITE_MAX)) },
            label = { Text("사업장·공장 (선택)") }, singleLine = true,
            placeholder = { Text("예: 당진공장") },
            supportingText = { Text("이름에 붙이지 않고 여기에 적으면 같은 회사 분들과 한곳에 모여요") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A 사업장's length, as the plaza's 부서·조 box takes it. */
internal const val SITE_MAX = 20

/** Whether [name] can become a company as it is. */
internal fun nameReady(name: String): Boolean = CompanyNames.problem(name) == null
