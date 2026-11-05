package com.bamnat.app.ui.decor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.LookImages
import com.bamnat.app.recipe.StudioMaterial
import com.bamnat.app.recipe.StudioMaterials
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.components.FilterChip
import kotlinx.coroutines.launch

/**
 * 1.38 [STM-06] 밤낮 재료 칸: 운영자가 그린 색 아이콘([StudioMaterial.ICON]) 또는 배경([StudioMaterial.BG])을 묶음별로 보여 주고,
 * 누르면 그림을 받아 꾸밈 사진으로 얹어요. 무료 표시가 없는 것은 프로이거나 광고 한 편으로 그 묶음을 30일 열었을 때 써요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MaterialShelf(kind: String, onPick: (StudioMaterial) -> Unit) {
    val context = LocalContext.current
    val c = context.appContainer
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<StudioMaterial>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tries by remember { mutableIntStateOf(0) }
    LaunchedEffect(tries) {
        val got = StudioMaterials.list(c)
        failed = got == null
        list = got.orEmpty()
    }
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val pro = gate.tier >= Tier.PRO
    var opened by remember { mutableIntStateOf(0) }
    var pack by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf<StudioMaterial?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    val title = if (kind == StudioMaterial.ICON) "밤낮 색 아이콘" else "밤낮 배경"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val all = list
        if (all == null) { Text("불러오는 중…", style = MaterialTheme.typography.bodySmall, color = muted); return@Column }
        if (failed) {
            Text("인터넷에 연결되면 $title 이 보여요", style = MaterialTheme.typography.bodySmall, color = muted)
            TextButton(onClick = { tries++ }) { Text("다시 불러오기") }
            return@Column
        }
        val mine = all.filter { it.kind == kind }
        if (mine.isEmpty()) { Text("곧 새 재료가 들어와요", style = MaterialTheme.typography.bodySmall, color = muted); return@Column }
        val packs = mine.map { it.pack }.distinct()
        val now = pack?.takeIf { it in packs } ?: packs.first()
        com.bamnat.app.ui.components.FoldRow("decor.mat.$kind", what = "묶음") {
            packs.forEach { p -> FilterChip(selected = p == now, onClick = { pack = p; locked = null }, label = { Text(p) }) }
        }
        val shown = mine.filter { it.pack == now }
        val t = com.bamnat.core.time.Today.millis()
        // [opened] is read so a pack opened by an ad redraws its locks.
        val until = remember(now, opened) { StudioMaterials.openUntil(context, now) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { m ->
                val usable = StudioMaterials.usable(m, pro, until, t)
                MaterialCell(m, usable, busy == m.id) {
                    said = null
                    if (!usable) { locked = m; return@MaterialCell }
                    locked = null
                    busy = m.id
                    scope.launch {
                        val ok = LookImages.fetch(c, m.id)
                        busy = null
                        if (ok) { StudioMaterials.mark(context, m.id); onPick(m) } else said = "그림을 받지 못했어요. 인터넷을 확인하고 다시 눌러 주세요"
                    }
                }
            }
        }
        locked?.let { m ->
            com.bamnat.app.ads.ThemeAdButton(
                "광고 보고 ‘${m.pack}’ 묶음 열기", "광고를 끝까지 보면 이 묶음을 ${StudioMaterials.PACK_DAYS}일 동안 써요. 프로는 광고 없이 모두 써요",
                onReward = { StudioMaterials.open(context, m.pack); opened++; locked = null; said = "‘${m.pack}’ 묶음을 열었어요. 그림을 눌러 넣어요" },
                onQuit = { said = "광고를 끝까지 봐야 열려요" },
                onOffline = { said = "인터넷에 연결되면 다시 해 주세요" },
                onNoAd = { StudioMaterials.open(context, m.pack); opened++; locked = null; said = "‘${m.pack}’ 묶음을 열었어요. 그림을 눌러 넣어요" },
            )
            TextButton(onClick = { com.bamnat.app.ui.pro.openPro() }) { Text("프로로 모두 쓰기") }
        }
        said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Text(if (kind == StudioMaterial.ICON) "밤낮이 직접 그린 아이콘이에요. 크기·자리·투명도는 고치기에서 바꿔요"
            else "밤낮이 직접 그린 배경이에요. 화면에 넣으면 화면을 채워요",
            style = MaterialTheme.typography.labelSmall, color = muted)
    }
}

@Composable
private fun MaterialCell(m: StudioMaterial, usable: Boolean, busy: Boolean, onClick: () -> Unit) {
    val c = LocalContext.current.appContainer
    var bmp by remember(m.shelf) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(m.shelf) {
        if (LookImages.fetch(c, m.shelf)) bmp = LookImages.bitmap(c.appContext, m.shelf)?.asImageBitmap()
    }
    val (w, h) = if (m.isIcon) 56.dp to 56.dp else 56.dp to 112.dp
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(w, h).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .clickable(onClickLabel = "${m.name} 넣기", onClick = onClick)
        .semantics { contentDescription = m.name + if (usable) "" else ", 잠김" }, contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it, null, Modifier.size(w, h).padding(if (m.isIcon) 6.dp else 0.dp),
                contentScale = if (m.isIcon) ContentScale.Fit else ContentScale.Crop)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        if (!usable) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp).clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
