package com.bamnat.app.ui.recipe

import com.bamnat.app.appContainer
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bamnat.app.ui.company.SoftCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.recipe.RecipeLinks
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipePost
import com.bamnat.core.recipe.Input
import com.bamnat.core.recipe.InputKind
import com.bamnat.core.recipe.PieceKind
import kotlinx.coroutines.launch
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.RecipeText
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore

@Composable
fun RecipeDetailScreen(vm: RecipeDetailViewModel, onBack: () -> Unit, onRemake: (String) -> Unit,
    /** [RCX-01][RCX-05][RCX-11] 1.35: another recipe (계보, 묶음) and a maker's page. */
    onOpen: (String) -> Unit = {}, onMaker: (String) -> Unit = {},
    /** [RCX-31] 1.35: my own recipe, made again as its next 판 (받은 분들께 새 판으로 알려져요). */
    onNewVersion: (String) -> Unit = {},
    /** [LIN-03] 1.35: my recipe (code) made again with what a 수정판 changed (its written form). */
    onTakeInto: (String, String) -> Unit = { _, _ -> },
    /** PC 제안 1.36: opened from the studio's 보관함 › 장터에서 찾기 — 담기 says so and goes back to it. */
    onToStudio: (() -> Unit)? = null) {
    // 2.0.0 [RVM-21] MKT-32 The work's open fonts as card fonts from our public files, never the whole font from Google Fonts until it is put in.
    val r = vm.state.collectAsStateWithLifecycle().value.recipe
    MarketFontsOf(r) { RecipeDetailPage(vm, onBack, onRemake, onOpen, onMaker, onNewVersion, onTakeInto, onToStudio) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailPage(vm: RecipeDetailViewModel, onBack: () -> Unit, onRemake: (String) -> Unit, onOpen: (String) -> Unit,
    onMaker: (String) -> Unit, onNewVersion: (String) -> Unit, onTakeInto: (String, String) -> Unit, onToStudio: (() -> Unit)?) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf(false) }
    var disliking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var tryOn by remember { mutableStateOf(false) }
    var tryAgain by remember { mutableStateOf<LiveTryOn.Reopen?>(null) }
    var adNote by remember { mutableStateOf<String?>(null) }
    val adGate = confirming && vm.looksNeedAd()
    var folded by remember { mutableStateOf(true) }
    var undoing by remember { mutableStateOf(false) }
    // [RVM-08] PC-66 ('이 작품 넣기'가 한 번 내려야 보여요): the 넣기 step sits in a bar on the bottom edge, with the line that says why it waits.
    var putBar by remember { mutableStateOf<PutBar?>(null) }
    LaunchedEffect(s.message) {
        val m = s.message ?: return@LaunchedEffect
        // PC 확인 보안 6차: a tab laid on the theme is taken back here too (with 마을 › 테마's 빼기 as before).
        val undo = (s.appliedId != null && m.startsWith("넣었어요")) || (s.laidAt != null && m.contains("테마 위에 얹었어요"))
        // PC 확인 20ab0254: cleared after it was shown; cleared first, the key changed and took the snackbar down at once.
        // PC 확인 7525ccff (신고 띠가 아래 스위치를 가려요): a line with nothing to do has its own ✕, so it goes at once when asked.
        val r = snack.showSnackbar(m, actionLabel = if (undo) "되돌리기" else null, withDismissAction = !undo,
            duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short)
        vm.shown()
        if (r == SnackbarResult.ActionPerformed) vm.undo()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { putBar?.let { StickyPut(it) } },
        topBar = {
            TopAppBar(
                title = { Text(s.doc?.item?.title ?: "작품", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, // 2.0.0 [RVM-08] 큰 글자에서 둘째 줄이 탭 칩 밑에 잘렸어요
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    val doc = s.doc
                    if (doc != null && doc.scope != "private") IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RecipeLinks.shareText(doc.item.title, vm.code))
                        runCatching { context.startActivity(Intent.createChooser(send, "작품 링크 보내기")) }
                    }) { Icon(Icons.Filled.Share, "링크 보내기") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "더 보기") }
                    DropdownMenu(menu, { menu = false }) {
                        if (s.doc?.mine == true) DropdownMenuItem(text = { Text("새 판 만들기") }, onClick = { menu = false; vm.body()?.let(onNewVersion) }, enabled = s.recipe != null)
                        DropdownMenuItem(text = { Text("스튜디오에서 고치기") }, onClick = { menu = false; vm.body()?.let(onRemake) }, enabled = s.recipe != null)
                        // PC 확인 7525ccff: once reported here, the menu says so (the line about it is gone in a few seconds).
                        if (s.doc?.mine != true) {
                            if (s.flagged) DropdownMenuItem(text = { Text("신고했어요 · 운영자가 살펴봐요") }, onClick = {}, enabled = false)
                            else DropdownMenuItem(text = { Text("신고하기") }, onClick = { menu = false; flagging = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        val doc = s.doc
        val r = s.recipe
        if (doc == null || r == null) {
            androidx.compose.runtime.SideEffect { putBar = null }
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.error != null) {
                    Text(s.error!!)
                    // PC 확인 edb46c62 LOW: a work from a newer app cannot open by trying again; only an update helps.
                    if (!s.error!!.startsWith("새 판 밤낮")) TextButton(onClick = vm::load) { Text("다시 해 보기") }
                } else CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val fxParts = remember(r) { com.bamnat.app.logic.FxAccess.partsOf(r) }
                // 1.39 [MKD-42] 상세 다시 짜기 (사용자 2026-11-07 "설명들이 너무 난잡 … 간결하게 표시하고 실제 맛은 미리보기와 입어보기 예시 사용들로"):
                // 맨 위는 작품 실제 모습 크게(도구는 전후 해 보기), 그 아래 이름 · 만든 분 · 한두 줄, 가운데 '넣기 전에 해 보기' 사다리,
                // 조각 목록과 규칙 원문은 '자세히 보기'로 접어요.
                val tool = remember(r, doc.item.scope) { doc.item.scope == "part" || doc.item.scope == "logic" || com.bamnat.core.recipe.LookParts.partUnit(r) != null }
                val hasLooks = remember(r) { com.bamnat.core.recipe.LookParts.of(r).isNotEmpty() }
                var sawExample by androidx.compose.runtime.saveable.rememberSaveable(vm.code) { mutableStateOf(false) }
                // 도구의 전후 해 보기는 길어서 이름·만든 분·무료/프로 줄 아래에 둬요 (하루 써 보기가 끝나 알림으로 열 때 그 줄이 먼저 보이게).
                if (!tool) WorkHero(doc.item, r, vm.style) { if (hasLooks) { sawExample = true; tryOn = true } }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleLarge)
                    if (r.summary.isNotEmpty()) Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // [RCX-05][PRF-01] 만든 분: 표식 · 팔로워, 누르면 프로필.
                MakerLine(doc.item, onMaker.takeIf { doc.item.maker != null })
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 1.39 판 정리: 새 판이 나와 장터에서 내린 작품 (넣은 분이 코드로 연 것).
                    if (doc.item.retired) Text(if ((s.appliedAt ?: s.laidAt) != null) "장터에서 내려간 작품이에요 · 넣은 것은 그대로 써요"
                        else "장터에서 내려간 작품이에요 · 링크로는 넣을 수 있어요", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 1.39 [MKC-04] 무료인지, 프로 기능이 들었는지 맨 위에서 한 줄로.
                    FxHeadLine(fxParts)
                    // 1.39 [MKD-12] 넣은 작품의 ✦ 장치가 쉬는 중이면 (하루 써 보기가 끝났거나 프로가 끝남).
                    val (fxPro, fxStatus) = fxNow(vm.code)
                    val fxOn = fxPro || fxStatus is com.bamnat.core.formula.FxTaste.Status.Using || com.bamnat.app.logic.FxAccess.on(LocalContext.current.appContainer)
                    // FX-23·24: 써 보기가 끝나 알림을 누르면 이 상세가 열려요. 맨 위에서 마쳤다는 것과 그동안 한 일, 프로 보기를 바로.
                    val ended = fxParts.isNotEmpty() && !fxPro && fxStatus is com.bamnat.core.formula.FxTaste.Status.Used
                    val did = if (ended) com.bamnat.app.logic.FxAccess.summary(LocalContext.current.appContainer) else null
                    if (ended) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦ 하루 써 보기를 마쳤어요" + (did?.let { " · 그동안 한 일: $it" } ?: "") +
                            (if ((s.appliedAt ?: s.laidAt) != null) ". 무료인 것은 그대로 돌고, ✦ 장치는 프로에서 다시 켜져요" else ""),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.PRO) }) { Text("프로 보기") }
                    } else if (fxParts.isNotEmpty() && (s.appliedAt ?: s.laidAt) != null && !fxOn)
                        Text("✦ 쉬는 중 · 무료인 것은 그대로 돌고, ✦ 장치는 프로에서 다시 켜져요", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary)
                    // [RVM-07] PC-53: the count is phones that ever put it in (once each), so it stays after 되돌리기 and says so.
                    Text(listOfNotNull("넣어 본 분 ${doc.item.users + s.usersPlus}명", doc.item.kept?.let { "7일 뒤에도 쓰는 분 $it%" }, RecipeDescribe.styles(r).takeIf { it.isNotBlank() })
                        .joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tool) ToolTryout(r, vm.style)
                ShortDesc(r.desc)
                if (doc.mine) MakerCard(doc, onNewVersion = vm.body()?.let { b -> { onNewVersion(b) } })
                // [LIN-02][LIN-03] 1.35 a 수정판 of my recipe: what it changed, and 내 판에 가져오기.
                s.parentDoc?.takeIf { it.mine }?.let { pd -> DiffSection(pd, s.diff, onOpen) { picked -> vm.takeInto(picked)?.let { body -> onTakeInto(pd.item.code, body) } } }

                val chosen = s.pieces.count { it.on && it.fits }
                val used = s.pieces.filter { it.on && it.fits }.flatMap { p -> RecipeResolve.inputsOf(r, r.pieces.first { it.kind == p.kind }) }.map { it.id }.toSet()
                val missing = r.inputs.filter { it.id in used && it.required && s.answers[it.id] == null && it.default == null }
                val outside = r.inputs.filter { it.id in used && RecipeResolve.rangeProblem(it, if (s.answers.containsKey(it.id)) s.answers[it.id] else it.default) != null }
                // PC 확인 V34: opened from a 입어 보기 elsewhere, it goes on to 입어 보기 at once.
                LaunchedEffect(r) {
                    if (hasLooks && com.bamnat.app.recipe.RecipeLinks.tryNext != null && com.bamnat.app.recipe.RecipeLinks.tryNext == vm.code) {
                        com.bamnat.app.recipe.RecipeLinks.tryNext = null
                        tryOn = true
                    }
                }
                // [TRY-05] Back from trying it on the real app: 입어 보기 again, the same parts ticked and the same tab.
                val again by LiveTryOn.reopen.collectAsStateWithLifecycle()
                // [TRY-07] 1.39 발굴: 입기·넣기 pressed on the band comes back to the question of 넣기, the tried parts ticked.
                LaunchedEffect(again, r) {
                    val back = LiveTryOn.take { it is LiveTryOn.From.Detail && it.code == vm.code } ?: return@LaunchedEffect
                    if (hasLooks) { tryAgain = back; tryOn = true; if (back.wear) { vm.lookParts(back.picked); confirming = true } }
                    else if (back.wear) confirming = true
                }
                val ready = missing.isEmpty() && outside.isEmpty() && !s.applying
                val liveTry = LocalContext.current.appContainer
                val tryScope = androidx.compose.runtime.rememberCoroutineScope()
                // [TRY-06] 1.39 기능까지 써 보기: the 기능 switched on, put on the real app for a while and taken back whole.
                val tryFns = s.pieces.filter { it.on && it.fits && it.kind in com.bamnat.app.recipe.FunctionTrial.KINDS }
                val ended by LiveTryOn.ended.collectAsStateWithLifecycle()
                ended?.takeIf { it.code == vm.code }?.let { e -> TriedCard(LiveTryOn.say(e)) { LiveTryOn.forget() } }
                var liveNote by remember(r) { mutableStateOf<String?>(null) }
                var tasting by remember { mutableStateOf(false) }
                var tasteRefused by remember { mutableStateOf(false) }
                val (pro, fxStatus) = fxNow(vm.code)
                val allLooks = remember(r) { com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet() }
                val logicOnly = remember(r) { r.pieces.isNotEmpty() && r.pieces.all { it.kind == PieceKind.LOGIC } }
                val inside = remember(r) { r.pieces.filter { it.kind == PieceKind.LOGIC }.fold(com.bamnat.core.formula.Logic()) { a, p -> a.plus(com.bamnat.core.formula.Logic.from(p.values)) } }
                val putIn = s.appliedAt ?: s.laidAt
                val steps = buildList {
                    // ① 예시로 보기: 예시 폰에서 지금 ↔ 입으면, 부분만 골라 넣기도 여기서.
                    if (hasLooks) add(LadderStep("예시로 보기", "예시 폰에서 지금 ↔ 입으면, 마음에 드는 부분만 골라요", done = sawExample, enabled = !s.applying) {
                        sawExample = true; tryOn = true
                    })
                    // ② 내 앱에 입혀 보기: 실제 내 화면, 저장 안 됨 (기능도 돌아요).
                    if (hasLooks || tryFns.isNotEmpty()) add(LadderStep("내 앱에 입혀 보기",
                        "실제 내 화면에 잠깐 입어 봐요. 저장되지 않아요" + (if (tryFns.isNotEmpty()) " · " + tryFns.joinToString("·") { it.kind.label } + "도 정말 돌아요" else ""),
                        done = ended?.code == vm.code, enabled = ready) {
                        liveNote = null
                        tryScope.launch {
                            val tab = if (hasLooks) MarketShots.tabOf(MarketShots.faceOf(doc.item, r))
                                else com.bamnat.app.recipe.FunctionTrial.tab(r, tryFns.map { it.kind }.toSet())
                            val ok = runCatching {
                                LiveTryOn.start(liveTry, r, own = doc.mine, if (hasLooks) allLooks else emptySet(), tab, LiveTryOn.From.Detail(vm.code), s.answers,
                                    functions = tryFns.associate { it.kind to it.mode }, cal = vm.calendar)
                            }.getOrDefault(false)
                            if (!ok) liveNote = "지금 내 앱과 같아서 바뀌는 곳이 없어요"
                        }
                    })
                    // ③ 하루 써 보기 (✦, 무료 폰만): 진짜로 넣고 24시간 ✦ 장치까지.
                    if (fxParts.isNotEmpty() && !pro) when (fxStatus) {
                        is com.bamnat.core.formula.FxTaste.Status.Ready -> add(LadderStep("하루 써 보기 ✦", "${FxMarket.HOURS}시간 동안 ✦ 장치까지 진짜로 넣어 써요 · 작품마다 한 번") { tasting = true })
                        is com.bamnat.core.formula.FxTaste.Status.Using -> add(LadderStep("하루 써 보기 ✦",
                            "${FxMarket.untilWords(fxStatus.until)}까지 · ${FxMarket.leftWords(fxStatus.until)}", done = true, enabled = putIn == null,
                            action = if (putIn == null) "넣고 써 보기" else null) { if (hasLooks) tryOn = true else confirming = true })
                        else -> add(LadderStep("하루 써 보기 ✦", "이미 썼어요 · ✦ 장치는 프로에서 다시 켜져요", done = true, enabled = false) {})
                    }
                    // ④ 넣기, 넣은 뒤에는 '✓ 넣었어요 · 되돌리기' (입혀 보기 갈래 PC 확인).
                    // PC 4차 c0ca3d9f: 부품 상세에도 '이 테마 입기'가 나왔어요. 작품 종류대로 말해요.
                    val part = doc.item.scope == "part" || com.bamnat.core.recipe.LookParts.partUnit(r) != null
                    val whole = wholeWords(doc.item.scope, part)
                    if (putIn != null) {
                        val left = (com.bamnat.app.recipe.RecipeRepository.UNDO_DAYS - (com.bamnat.core.time.Today.millis() - putIn) / 86_400_000L).coerceAtLeast(1)
                        add(LadderStep(if (s.applying) "넣는 중…" else "✓ 넣었어요 · 다시 넣기",
                            java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(putIn)) +
                                (if (s.appliedAt == null) " 테마 위에 얹음 · 언제든 되돌려요" else " 넣음 · ${left}일 안에 되돌려요"),
                            done = true, main = true, enabled = ready && (hasLooks || chosen > 0), other = "되돌리기" to { undoing = true }) {
                            if (hasLooks) { vm.lookParts(allLooks); confirming = true } else confirming = true
                        })
                    } else add(LadderStep(when {
                        s.applying -> "넣는 중…"
                        hasLooks -> whole
                        // PC D15 (사용성 낮): 규칙·단추만 있는 작품은 실제로 들어가는 수로.
                        logicOnly -> listOfNotNull(inside.rules.size.takeIf { it > 0 }?.let { "규칙 ${it}개" }, inside.buttons.size.takeIf { it > 0 }?.let { "단추 ${it}개" })
                            .joinToString("·") + " 넣기"
                        else -> "고른 ${chosen}개 내 달력에 넣기"
                    }, if (hasLooks && !part) "부분만 넣으려면 ‘예시로 보기’에서 골라요" else null, main = true, enabled = ready && (hasLooks || chosen > 0)) {
                        if (hasLooks) { vm.lookParts(allLooks); confirming = true } else confirming = true
                    })
                }
                val putStep = steps.firstOrNull { it.main }
                TryLadder(steps.filterNot { it.main })
                // [RVM-16] PC-68: put in with a 빠른 설정 button, the tile is one tap away (안드로이드 13부터 더하기 요청).
                if (putIn != null && inside.buttons.any { it.place == com.bamnat.core.formula.ButtonPlace.TILE }) TileOffer()
                val putNote: @Composable () -> Unit = {
                    liveNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    // 1.39 [NTC-03] 겹치는 알림은 접힌 '자세히 보기'에 묻히지 않게 넣기 단추 아래에도 한 줄 (넣기 창에서 골라요).
                    s.pieces.firstOrNull { it.kind == PieceKind.LOGIC && it.on && it.fits && it.overlaps.isNotEmpty() }?.let { p ->
                        Text("⚠ " + p.overlaps.first().say() + " · 넣을 때 바꿀지 골라요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (tasteRefused) Text("이 작품은 하루 써 보기를 이미 썼어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (missing.isNotEmpty()) Text("‘${missing.first().label}’${com.bamnat.app.ui.Fmt.obj(missing.first().label)} ‘자세히 보기’에서 채워 주세요",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    // 1.35 실사용 점검: the field already says the range in red; here only why the button waits.
                    else if (outside.isNotEmpty()) Text("‘자세히 보기’의 ‘${outside.first().label}’ 칸을 고치면 넣을 수 있어요", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.runtime.SideEffect { putBar = putStep?.let { PutBar(it, putNote) } }
                if (tasting) FxTasteAsk(fxParts, vm.code, onDismiss = { tasting = false }, onRefused = { tasteRefused = true }) {
                    if (hasLooks) tryOn = true else confirming = true
                }
                // 1.39 [MKC-04] 프로 기능 작품: 무엇이 프로인지와 프로 보기 (하루 써 보기는 위 사다리 ③).
                FxCard(fxParts, vm.code, applied = s.appliedAt != null, ladder = true) { if (hasLooks) tryOn = true else confirming = true }
                VoteRow(s, vm, onDislike = { disliking = true })

                // 1.39 [MKD-42] 자세히 보기: 넣을 조각 고르기와 칸 채우기, 내 달력 미리 보기, 담기 (채울 칸이 비었으면 펼친 채로).
                var details by androidx.compose.runtime.saveable.rememberSaveable(vm.code) { mutableStateOf(false) }
                val open = details || missing.isNotEmpty() || outside.isNotEmpty()
                val shownPieces = s.pieces.filter { it.fits && !(it.lines.isNotEmpty() && it.lines.all { l -> l.skipped && l.text.startsWith("지금과 같아서") }) }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { details = !open },
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("자세히 보기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                                Text("넣을 것 ${chosen}/${s.pieces.count { it.fits }}개 고르기 · 내 달력 미리 보기 · v${doc.item.version}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (open) "접기" else "펼치기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        AnimatedVisibility(open) {
                            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("켜 둔 조각만 들어가요. 근무 기록·가계부·메모는 그대로예요", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // [MAT-06] PC 확인 9차 (받는 쪽은 글꼴이 함께 오는지 모름): the shared fonts it names come along.
                                val fontCount = remember(r) { com.bamnat.app.recipe.RecipeFonts.idsIn(com.bamnat.core.recipe.RecipeCodec.write(r)).size }
                                if (fontCount > 0) Text("만든 분이 나눈 글꼴 ${fontCount}개가 함께 와요. 운영자가 확인한 글꼴은 넣을 때 바로, 확인 전인 글꼴은 확인 뒤 저절로 들어와요(그 전에는 비슷한 앱 글꼴로 보여요)",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                shownPieces.forEach { p -> PieceCard(r, p, s.answers, vm) }
                                val other = s.pieces.filter { !it.fits }
                                if (other.isNotEmpty()) {
                                    TextButton(onClick = { folded = !folded }) { Text((if (folded) "펼치기: " else "접기: ") + "내 유형에는 없는 조각 ${other.size}개") }
                                    AnimatedVisibility(!folded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            other.forEach { p ->
                                                Text("• ${p.kind.label}: " + RecipeDescribe.line(r.pieces.first { it.kind == p.kind }, amounts = false, style = vm.style),
                                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                // [RCX-27] 내 달력에서 미리 보기: this month, 지금 ↔ 넣은 뒤, with only the pieces switched on.
                                val lookOn = s.pieces.any { it.kind == PieceKind.SHIFT_LOOK && it.on && it.fits }
                                val themeOn = s.pieces.firstOrNull { it.kind == PieceKind.CAL_THEME && it.on && it.fits }
                                val companyOn = s.pieces.any { it.kind == PieceKind.COMPANY && it.on && it.fits }
                                MyMonthPreview(vm.calendar, if (lookOn) s.looks else emptyList(),
                                    themeOn?.let { RecipeResolve.piece(r, r.pieces.first { p -> p.kind == PieceKind.CAL_THEME }, RecipeResolve.answers(r, s.answers)) },
                                    company = r.pieces.firstOrNull { it.kind == PieceKind.COMPANY }?.values?.get("setup") as? String)
                                ScreenPreview(r, vm.style) { k -> s.pieces.any { it.kind == k && it.on && it.fits } }
                                // [BLU-07] PC 6차: a 부품 is kept for the studio from here in one tap, not only inside 입어 보기.
                                val unit = remember(r) { com.bamnat.core.recipe.LookParts.partUnit(r) }
                                if (unit != null) {
                                    var keptLine by remember(r) { mutableStateOf<String?>(null) }
                                    androidx.compose.material3.OutlinedButton(onClick = {
                                        keptLine = if (vm.keep(setOf(unit))) "✓ 보관함에 담았어요. 스튜디오 › 보관함에서 꺼내 얹어요"
                                            else "보관함이 꽉 찼어요. 스튜디오 › 보관함에서 하나를 빼고 다시 담아 주세요"
                                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("이 부품 보관함에 담기") }
                                    keptLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                // [RUL-07] 1.36 규칙·단추는 보관함에 담아 두고 스튜디오 › 규칙·단추에서 내 것에 더해 얹어요.
                                if (r.pieces.any { it.kind == PieceKind.LOGIC }) {
                                    var keptLogic by remember(r) { mutableStateOf<String?>(null) }
                                    OutlinedButton(onClick = {
                                        keptLogic = if (vm.keep(setOf(com.bamnat.core.recipe.LookParts.LOGIC))) "✓ 보관함에 담았어요. 스튜디오 › 규칙·단추 › 보관함에서 내 규칙에 더해 얹어요"
                                            else "보관함이 꽉 찼어요 (${vm.partsLimit()}개까지)"
                                    }, modifier = Modifier.fillMaxWidth()) { Text("규칙·단추만 보관함에 담기") }
                                    keptLogic?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
                if (tryOn) {
                    val others = s.pieces.filter { it.fits && it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }
                    val allParts = remember(r) { com.bamnat.core.recipe.LookParts.of(r).size }
                    val liveC = LocalContext.current.appContainer
                    TryOnScreen(r, vm.style, onDismiss = { tryOn = false; tryAgain = null },
                        onKeep = { ids -> vm.keep(ids) }, onToStudio = onToStudio,
                        // [TRY-05] 1.39 실제 화면으로 입어 보기.
                        // [TRY-06] with the 기능 ticked below, which then really run.
                        live = { picked, tab ->
                            LiveTryOn.start(liveC, r, own = doc.mine, picked, tab, LiveTryOn.From.Detail(vm.code), s.answers,
                                functions = tryFns.associate { it.kind to it.mode }, cal = vm.calendar).also { if (it) { tryOn = false; tryAgain = null } }
                        },
                        startPicked = tryAgain?.picked, startTab = tryAgain?.tab, own = doc.mine,
                        liveWith = tryFns.takeIf { it.isNotEmpty() }?.joinToString("·") { it.kind.label },
                        extra = if (others.isEmpty()) null else { {
                            Text("기능 · 모양과 함께 넣을 것", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                            others.forEach { p ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(p.on, { vm.toggle(p.kind, it) })
                                    Column(Modifier.weight(1f)) {
                                        Text(p.kind.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(RecipeDescribe.line(r.pieces.first { it.kind == p.kind }, amounts = false, style = vm.style), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                                // 1.39 [MKP-05] 기능도 입어 보듯: what it changes here, 지금 ↔ 넣으면, under its tick (3 rows, the rest unfold).
                                if (p.on) BeforeAfter(p.sides, Modifier.padding(start = 12.dp), fold = 3)
                            }
                        } },
                    ) { picked ->
                        val n = picked.size + others.count { it.on }
                        // [R136-06] 1.36 (리뷰 R-17: '고를 것 5/5'인데 '고른 8개 넣기'): the look and the functions said apart.
                        val fn = others.count { it.on }
                        if (!ready && missing.isNotEmpty()) Text("‘${missing.first().label}’${com.bamnat.app.ui.Fmt.obj(missing.first().label)} 위에서 채우면 넣을 수 있어요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        // PC 확인 보안 10차 (넣기 창을 닫으면 입어 보기까지 닫혀요): the question opens over 입어 보기, so 닫기 goes back to it.
                        Button(onClick = { vm.lookParts(picked); confirming = true }, enabled = n > 0 && ready,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            // PC 확인 보안 8차 ('모양 1개·기능 1개'에서 근무 꾸미기를 기능으로 세요): what goes with the look is said by
                            // its own names (근무 꾸미기·알람 묶음), as the list above shows them, and counted only when there are many.
                            val names = others.filter { it.on }.map { it.kind.label }
                            val rest = if (names.size <= 2) names.joinToString("·") else "함께 넣을 것 ${names.size}개"
                            val detail = when {
                                n == 0 -> null
                                fn == 0 -> "고른 모양 ${picked.size}개"
                                picked.isEmpty() -> rest
                                else -> "모양 ${picked.size}개 · $rest"
                            }
                            // 1.38 PC 스튜디오 확인 (중): '모양 27개 · 앱 동작 넣기'가 어려워요. 다 고른 채면 한마디로, 무엇이 들어가는지는 작게.
                            val whole = n > 0 && picked.size == allParts && others.all { it.on }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(when {
                                    n == 0 -> "넣을 것을 골라 주세요"
                                    whole -> wholeWords(doc.item.scope, doc.item.scope == "part" || com.bamnat.core.recipe.LookParts.partUnit(r) != null, plain = true)
                                    else -> "$detail 넣기"
                                })
                                if (whole && detail != null) Text(detail, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // [RCX-11] 묶음: each recipe in it opens on its own, to be chosen piece by piece.
                r.pieces.firstOrNull { it.kind == PieceKind.BUNDLE }?.let { b ->
                    Text("묶음 안 작품", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    @Suppress("UNCHECKED_CAST")
                    (b.values["items"] as? List<Map<String, Any?>>).orEmpty().forEach { i ->
                        val c = i["code"] as? String ?: return@forEach
                        OutlinedButton(onClick = { onOpen(c) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(((i["note"] as? String)?.let { "$it ($c)" } ?: c) + " 열기")
                        }
                    }
                }
                // 1.36 [VIL-14] 마을 V14: 고쳐 만들기 out of the ⋮ menu, where someone almost happy with it looks next.
                if (doc.mine != true) SoftCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("거의 마음에 들면 내 것으로 고쳐요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        Text("스튜디오에서 이 작품부터 시작해요. 만든 분 표시는 늘 남고, 세 곳 이상 직접 바꾸면 내 창작으로 올릴 수 있어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { vm.body()?.let(onRemake) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("스튜디오에서 고치기") }
                    }
                }
                // [RCX-01] 계보: what it was made from, and what others made from it.
                s.lineage?.let { l -> LineageSection(l, onOpen, s.doc?.item?.look) }
                // [LIN-02][LIN-03] 1.35 달라진 점, and for the maker of the recipe it came from, 내 판에 가져오기.
                // For anyone else it sits with the 계보; the maker of the original sees it first, above 넣기 (1.35 자유도 점검).
                s.parentDoc?.takeIf { !it.mine }?.let { pd -> DiffSection(pd, s.diff, onOpen) { } }
                HorizontalDivider()
                if (vm.canTalk) TalkSection(s, vm) else Text("이야기는 정식 출시일부터 열려요", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    // PC 03111522 (써 보는 중 넣기 창: '‘커피’은 이미 있는 것과 하는 일이 같아'): a work being tried is put back first, and what 넣기
    // changes is worked out again against my own app, before the question shows.
    val ctx = LocalContext.current
    var settling by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (!confirming || !com.bamnat.app.recipe.FunctionTrial.busy(ctx)) return@LaunchedEffect
        settling = true
        try { LiveTryOn.settle(ctx.appContainer, snack = false); vm.preview()?.join() } finally { settling = false }
    }
    // [RVM-13] 총괄 #462: the 넣기 창 is its own 창 with its one filled '넣기' (the 상세's own filled button stays under it).
    if (confirming && !settling) PutDialog(s, vm, adGate, adNote,
        onClose = { confirming = false },
        onPut = { confirming = false; tryOn = false; vm.apply { } },
        onNoAd = { reason -> confirming = false; tryOn = false; vm.noAdCounted(reason); vm.apply { } },
        onAdNote = { adNote = it })
    if (undoing) AlertDialog(
        onDismissRequest = { undoing = false },
        title = { Text("넣기 전으로 되돌릴까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // PC 확인 7525ccff (두 번 넣은 것 중 무엇이 돌아가는지 몰라요): each put-on it takes back, when and what.
                // PC 확인 보안 6차 (목록이 없어요): always, one line a put-on, a tab laid on the theme as well.
                if (s.undoable.size >= 2) Text("이 작품을 ${s.undoable.size}번 넣었어요. 모두 넣기 전으로 돌아가요", style = MaterialTheme.typography.bodyMedium)
                s.undoable.forEach { (at, what) ->
                    Text("· " + java.text.SimpleDateFormat("M월 d일 HH:mm", java.util.Locale.KOREA).format(java.util.Date(at)) + " " + what,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("이 작품이 바꾼 설정만 돌아가요. 그 사이 적은 근무·메모·가계부 기록과, 내가 따로 고친 칸은 그대로예요")
            }
        },
        confirmButton = { TextButton(onClick = { undoing = false; vm.undo() }) { Text("되돌리기") } },
        dismissButton = { TextButton(onClick = { undoing = false }) { Text("닫기") } },
    )
    if (flagging) ChoiceDialog("신고하기", RecipeLinks.FLAG_REASONS, onPick = { vm.flag(it); flagging = false }, onDismiss = { flagging = false })
    if (disliking) DislikeDialog(onSend = { reason, opinion -> vm.vote(-1, reason, opinion); disliking = false }, onDismiss = { disliking = false })
}

/** [RVM-13] PC-59·60·34 · 총괄 #462 The 넣기 창: short lines first, '바뀌는 것 모두 보기' as a row, '넣기' its one filled button and '닫기'. */
@Composable
private fun PutDialog(
    s: RecipeDetailViewModel.State, vm: RecipeDetailViewModel, adGate: Boolean, adNote: String?,
    onClose: () -> Unit, onPut: () -> Unit, onNoAd: (String) -> Unit, onAdNote: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("이렇게 바뀌어요") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // [RVM-13] PC-59·60·34: four short lines first (모양·단추·알람·그 밖에), the 알람 line's switch '알람도 넣기', and the full list folded.
                val fxOn = com.bamnat.app.logic.FxAccess.on(LocalContext.current.appContainer)
                PutBrief(putBrief(s, fxOn), onLetters = vm::letters) { vm.toggle(PieceKind.ALARMS, it) }
                // 1.39 [NTC-03] 겹치는 알림: '기존 설정을 바꿀까요?' asked here too, right before 넣기 (사용자 2026-11-08).
                s.pieces.firstOrNull { it.kind == PieceKind.LOGIC && it.on && it.fits && it.overlaps.isNotEmpty() }?.let { p ->
                    com.bamnat.app.ui.settings.OverlapAsk(p.overlaps, p.mode) { vm.mode(p.kind, it) }
                }
                var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                // [RVM-13] 총괄 #462 (채운 단추 없이 글자 단추가 넷): '바뀌는 것 모두 보기' is a row that opens the list (chevron), so the 창 has
                // '넣기' filled and '닫기' as its only 글자 단추.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { more = !more },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (more) "바뀌는 것 접기" else "바뀌는 것 모두 보기", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // PC 확인 보안 9차 (단추는 '고른 모양 2개'인데 창은 '달력 모양' 한 줄): the looks ticked in 입어 보기 are listed part by part,
                // as many as the button said, instead of the whole look piece they come from (which also told changes not ticked).
                val parts = s.lookParts?.mapNotNull { com.bamnat.core.recipe.LookParts.byId(it) }?.sortedBy { com.bamnat.core.recipe.LookParts.ALL.indexOf(it) }
                if (more && !parts.isNullOrEmpty()) {
                    Text("모양 ${parts.size}개", style = MaterialTheme.typography.labelLarge)
                    // One line a screen, as 입어 보기 shows them by tab ('위젯 · 위젯' said once).
                    parts.groupBy { it.tab }.forEach { (tab, ps) ->
                        val name = com.bamnat.core.recipe.LookParts.tabLabel(tab)
                        val labels = ps.map { it.label }
                        Text("• " + if (labels == listOf(name)) name else name + ": " + labels.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (more) s.pieces.filter { it.on && it.fits && (parts == null || it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS) }.forEach { p ->
                    Text(p.kind.label, style = MaterialTheme.typography.labelLarge)
                    p.lines.ifEmpty { listOf(RecipePieces.Change("바뀌는 것이 없어요", true)) }.forEach { l ->
                        Text("• " + l.text, style = MaterialTheme.typography.bodySmall,
                            color = if (l.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
                // [MAT-06] PC 확인 12차 (넣기 창에 함께 오는 글꼴 안내가 없음).
                // PC 확인 13차: each font by its name (a waiting one's name stays hidden till the operator looks).
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.bamnat.app.BamnatApp
                val fontLines by androidx.compose.runtime.produceState(emptyList<String>(), s.recipe) {
                    val r = s.recipe; val c = app?.container
                    value = if (r == null || c == null) emptyList() else runCatching { com.bamnat.app.recipe.RecipeFonts.comingLines(c, r) }.getOrDefault(emptyList())
                }
                if (fontLines.isNotEmpty() && !more) Text("글꼴 ${fontLines.size}개도 함께 와요", style = MaterialTheme.typography.bodyMedium)
                if (fontLines.isNotEmpty() && more) {
                    Text("글꼴", style = MaterialTheme.typography.labelLarge)
                    fontLines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                // [MKX-02] 1.36 빌드 8: a tab laid on is taken off in 마을 › 테마; the rest is undone in 내 것 › 받은 레시피.
                val laysOn = s.recipe?.let { r ->
                    val looks = s.pieces.filter { it.on && it.fits && it.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }.map { it.kind }.toSet()
                    if (looks.isEmpty()) null else com.bamnat.core.recipe.LookParts.onlyTab(r.copy(pieces = r.pieces.filter { it.kind in looks }))
                } != null
                val restToo = s.pieces.any { it.on && it.fits && it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }
                Text(when {
                    laysOn && restToo -> "모양은 지금 테마 위에 얹어요(마을 › 테마에서 빼기). 나머지는 7일 안에 마을 › 테마(내 서랍)의 받은 것에서 되돌려요"
                    laysOn -> "지금 테마 위에 얹어요. 마을 › 테마에서 언제든 뺄 수 있어요"
                    else -> "7일 안에는 마을 › 테마(내 서랍)의 받은 것에서 되돌릴 수 있어요"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) // 총괄 #462: 안내는 보조 색 (링크 파랑 아님)
                adNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                // 1.39 [NTC-04] 알림을 보내는 규칙이 들어오면: 지금 켜 둔 알림과 더 켤 수 있는 알림 (줄이거나 더하기).
                val notifies = s.recipe?.pieces?.firstOrNull { it.kind == PieceKind.LOGIC }?.let { lp ->
                    s.pieces.any { it.kind == PieceKind.LOGIC && it.on && it.fits } &&
                        com.bamnat.core.formula.Logic.from(lp.values).rules.any { r -> r.acts.any { a -> a.verb == com.bamnat.core.formula.LogicVerb.NOTIFY } }
                } == true
                if (notifies) com.bamnat.app.ui.settings.NoticeBoardCard(modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            // [FREE-10] 적용할 때만 광고 (사용자 2026-10-30 15:53): the looks of someone else's theme, past the first free ones.
            if (adGate) com.bamnat.app.ads.ThemeAdButton(
                "광고 보고 넣기", "모양(테마)은 광고를 끝까지 보면 30일 동안 써요. 알람·규칙 같은 기능은 기간 없이 계속 써요",
                onReward = onPut,
                onQuit = { onAdNote("광고를 끝까지 봐야 넣을 수 있어요") },
                onOffline = { onAdNote("인터넷에 연결되면 다시 해 주세요") },
                onNoAd = onNoAd,
            ) else androidx.compose.material3.Button(onClick = onPut, modifier = Modifier.heightIn(min = 48.dp)) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } },
    )
}

@Composable
private fun VoteRow(s: RecipeDetailViewModel.State, vm: RecipeDetailViewModel, onDislike: () -> Unit) {
    val v = s.votes ?: return
    if (s.doc?.mine == true) {
        Text("좋아요 ${v.likes} · 안좋아요 ${v.dislikes}", style = MaterialTheme.typography.bodyMedium); return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { vm.vote(if (v.myVote == 1) null else 1) }, enabled = vm.canVote) {
            Icon(if (v.myVote == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null); Spacer(Modifier.width(6.dp)); Text("좋아요 ${v.likes}")
        }
        OutlinedButton(onClick = { if (v.myVote == -1) vm.vote(null) else onDislike() }, enabled = vm.canVote) {
            Icon(if (v.myVote == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, null); Spacer(Modifier.width(6.dp)); Text("안좋아요 ${v.dislikes}")
        }
    }
    if (!vm.canVote) Text("좋아요·안좋아요는 정식 출시일부터 누를 수 있어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** [RCP-31] What the maker alone sees: the state of the recipe, the reviewer's note, and why people did not like it (never who). */
@Composable
private fun MakerCard(doc: com.bamnat.app.recipe.RecipeDoc, onNewVersion: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("내 작품", style = MaterialTheme.typography.titleSmall)
            Text(stateLabel(doc.state, doc.scope), style = MaterialTheme.typography.bodyMedium)
            // 1.36 [GRW-08] PC 8d290ec3: 새싹 첫 사진은 운영자가 본 뒤에 다른 분께 보여요.
            if (doc.photosWaiting > 0) com.bamnat.app.ui.company.StatusChip("사진 확인 중 · 보통 하루 안", com.bamnat.app.ui.company.Tone.WAIT)
            if (doc.photosHeld > 0) {
                com.bamnat.app.ui.company.StatusChip("사진이 신고로 잠시 가려졌어요", com.bamnat.app.ui.company.Tone.BAD)
                Text("운영자가 살펴본 뒤 괜찮으면 다시 보여요. 작품의 다른 부분은 그대로 보여요", style = MaterialTheme.typography.bodySmall)
            }
            doc.note?.let { Text("운영자 메모: $it", style = MaterialTheme.typography.bodySmall) }
            doc.reasons?.let { rs ->
                if (rs.tally.isNotEmpty()) Text("안좋아요 이유: " + rs.tally.entries.joinToString(" · ") { (k, n) -> "${RecipeLinks.DISLIKE_REASONS[k] ?: k} $n" },
                    style = MaterialTheme.typography.bodySmall)
                rs.opinions.take(10).forEach { (k, t) -> Text("• [${RecipeLinks.DISLIKE_REASONS[k] ?: k}] $t", style = MaterialTheme.typography.bodySmall) }
                if (rs.opinions.isNotEmpty()) Text("의견을 보고 고친 새 판을 올리면 받는 분들께 알려져요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            // [RCX-31] The way to the next 판: what this phone has now, or the recipe as it is, changed and sent again.
            if (onNewVersion != null) com.bamnat.app.ui.components.Button(onClick = onNewVersion, modifier = Modifier.padding(top = 6.dp)) {
                Text("새 판 만들기 (v${doc.item.version + 1})")
            }
        }
    }
}

fun stateLabel(state: String?, scope: String): String = when (state) {
    "review" -> "운영자가 살펴보고 있어요. 곧 장터에 다시 보여요"
    // [RCX-31] Waiting from before 1.35 dropped the first look: the operator lets it show soon.
    "pending" -> "운영자 확인을 기다려요. 확인되면 장터와 코드로 열려요"
    "returned" -> "돌려받았어요. 메모를 보고 고쳐서 다시 올려 주세요"
    "hidden" -> "가려졌어요"
    else -> when (scope) { "link" -> "링크로만 열려요"; "private" -> "나만 봐요"; else -> "장터에 올라가 있어요" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceCard(r: com.bamnat.core.recipe.Recipe, p: RecipeDetailViewModel.Piece, answers: Map<String, Any?>, vm: RecipeDetailViewModel) {
    val piece = r.pieces.first { it.kind == p.kind }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(RecipeDescribe.line(piece, amounts = false, style = vm.style), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(p.on, { vm.toggle(p.kind, it) })
            }
            if (p.on) {
                RecipeResolve.inputsOf(r, piece).forEach { i -> InputField(i, answers[i.id], { vm.answer(i.id, it) }) }
                // [RCP-24] 내 근무가 어떻게 보일지: 지금 → 적용 뒤.
                if (p.kind == PieceKind.SHIFT_LOOK) vm.state.collectAsStateWithLifecycle().value.looks.forEach { (a, b) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(a.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                        com.bamnat.app.ui.components.ShiftBadge(a, size = 26.dp)
                        Text("→")
                        com.bamnat.app.ui.components.ShiftBadge(b, size = 26.dp)
                    }
                }
                if (p.kind == PieceKind.CAL_THEME) ThemeSample(r, piece)
                // [RCX-23] 부딪힘: a memo template of the same name is already mine.
                if (p.clashes.isNotEmpty()) {
                    Text("같은 이름 메모 틀이 이미 있어요: " + p.clashes.joinToString(", ") { "‘$it’" }, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(p.mode == RecipePieces.Mode.BOTH, { vm.mode(p.kind, RecipePieces.Mode.BOTH) }, label = { Text("둘 다 두기") })
                        FilterChip(p.mode == RecipePieces.Mode.REPLACE, { vm.mode(p.kind, RecipePieces.Mode.REPLACE) }, label = { Text("내 것을 이걸로 바꾸기") })
                    }
                    Text("넣지 않으려면 위 스위치를 끄면 돼요. 바꿔도 7일 안에 되돌리면 내 것이 돌아와요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 1.39 [NTC-03] 겹치는 알림: 기존 것 끄기 / 둘 다(하나로) 두기.
                if (p.kind == PieceKind.LOGIC) com.bamnat.app.ui.settings.OverlapAsk(p.overlaps, p.mode) { vm.mode(p.kind, it) }
                if (p.kind == PieceKind.SHIFT_LOOK || p.kind == PieceKind.ALARMS) {
                    Text("내 근무와 이름이 같거나, 주간·야간처럼 역할이 같은 근무에 넣어요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 1.39 [MKP-05] 기능 조각 전후: 지금 ↔ 넣으면 side by side (근무 꾸미기 shows its own badges above). With the table, the lines
                // left are what it cannot use here and warnings; 규칙·단추 keep each rule's sentence, which the counts do not say.
                val table = p.kind != PieceKind.SHIFT_LOOK && p.sides.isNotEmpty()
                if (table) BeforeAfter(p.sides)
                p.lines.filter { !table || it.skipped || p.kind == PieceKind.LOGIC || it.text.startsWith("⚠") }.forEach { l ->
                    Text((if (l.skipped) "– " else "→ ") + l.text, style = MaterialTheme.typography.bodySmall,
                        color = if (l.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** [RCP-24] 받는 사람이 정할 칸: one field of the right kind, empty meaning "내 것 그대로". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputField(i: Input, value: Any?, onChange: (Any?) -> Unit) {
    val shown = value ?: i.default
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (i.kind) {
            InputKind.MONEY, InputKind.NUMBER -> {
                var text by remember(i.id) { mutableStateOf(shown?.toString().orEmpty()) }
                OutlinedTextField(text, { t ->
                    text = t.filter { it.isDigit() || it == '.' }.take(12)
                    onChange(if (i.kind == InputKind.MONEY) text.toLongOrNull() else text.toLongOrNull() ?: text.toDoubleOrNull())
                }, label = { Text(i.label + if (i.kind == InputKind.MONEY) " (원)" else "") }, singleLine = true,
                    visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            InputKind.TIME, InputKind.TEXT -> {
                var text by remember(i.id) { mutableStateOf(shown?.toString().orEmpty()) }
                OutlinedTextField(text, { t -> text = t.take(if (i.kind == InputKind.TIME) 5 else 40); onChange(text.ifEmpty { null }) },
                    label = { Text(i.label + if (i.kind == InputKind.TIME) " (예: 07:30)" else "") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            InputKind.COLOR -> ColorSwatch((shown as? Long) ?: 0xFF5C6BC0L, i.label, { onChange(it) })
            InputKind.EMOJI -> {
                var open by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { open = true }) { Text("${i.label}: ${shown ?: "고르기"}") }
                if (open) EmojiPickerDialog(onPick = { onChange(it); open = false }, onDismiss = { open = false })
            }
            InputKind.CHOICE -> {
                Text(i.label, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    i.choices.forEach { ch -> FilterChip(selected = shown == ch, onClick = { onChange(ch) }, label = { Text(ch) }) }
                }
            }
            InputKind.FLAG -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.label, Modifier.weight(1f)); Switch(shown == true, { onChange(it) })
            }
        }
        if (i.hint.isNotEmpty()) Text(i.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // [RCX-25] The maker's 범위, checked on this phone.
        RecipeResolve.rangeProblem(i, shown)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceDialog(title: String, choices: Map<String, String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { choices.forEach { (k, label) -> TextButton(onClick = { onPick(k) }, modifier = Modifier.fillMaxWidth()) { Text(label) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** [RCP-31] 안좋아요 needs a reason and an opinion (5~120자), so the maker can make it better (결정 D107). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DislikeDialog(onSend: (String, String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var opinion by remember { mutableStateOf("") }
    val n = RecipeText.graphemes(opinion.trim())
    val problem = RecipeText.problem(opinion)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("왜 안좋았나요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("만든 분만 이유와 의견을 봐요. 누가 썼는지는 보이지 않아요", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecipeLinks.DISLIKE_REASONS.forEach { (k, label) -> FilterChip(reason == k, { reason = k }, label = { Text(label) }) }
                }
                OutlinedTextField(opinion, { opinion = it.take(140) }, label = { Text("어떻게 바꾸면 좋을까요? (5~120자)") }, modifier = Modifier.fillMaxWidth())
                Text(when {
                    problem != null -> RecipeText.problemText(problem)
                    else -> "$n/120"
                }, style = MaterialTheme.typography.labelSmall, color = if (problem != null || n > 120) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason!!, opinion.trim()) }, enabled = reason != null && n in 5..120 && problem == null) { Text("보내기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** [RCP-35] 레시피 이야기: questions, opinions, ideas and reviews, the maker marked, 쓰는 중 N일 next to those who use it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalkSection(s: RecipeDetailViewModel.State, vm: RecipeDetailViewModel) {
    var body by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("question") }
    var replyTo by remember { mutableStateOf<RecipePost?>(null) }
    var disliking by remember { mutableStateOf<RecipePost?>(null) }
    Text("이야기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    val talk = s.talk
    if (talk == null) { Text("불러오는 중이에요", style = MaterialTheme.typography.bodySmall); return }
    if (talk.posts.isEmpty()) Text("아직 이야기가 없어요. 써 본 후기나 궁금한 점을 남겨 주세요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    var deleting by remember { mutableStateOf<RecipePost?>(null) }
    var flagging by remember { mutableStateOf<RecipePost?>(null) }
    // [RVF-03] A reply whose post its writer took back stays, at the top.
    val tops = talkTops(talk.posts)
    tops.forEach { p ->
        PostRow(p, onReply = { replyTo = p }, onLike = { vm.votePost(p.id, if (p.myVote == 1) null else 1) }, onDislike = { disliking = p },
            onDelete = { deleting = p }, onFlag = { flagging = p })
        talk.posts.filter { it.parent == p.id && !it.hidden }.forEach { c ->
            Row { Spacer(Modifier.width(20.dp)); PostRow(c, onReply = null, onLike = { vm.votePost(c.id, if (c.myVote == 1) null else 1) }, onDislike = { disliking = c },
                onDelete = { deleting = c }, onFlag = { flagging = c }) }
        }
    }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("이 글을 지울까요?") },
            text = { Text("지우면 서버에서도 사라지고 되돌릴 수 없어요" + if (talk.posts.any { it.parent == p.id }) ". 달린 답글은 남아요" else "") },
            confirmButton = { TextButton(onClick = { vm.deletePost(p.id); deleting = null }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("그대로 두기") } })
    }
    flagging?.let { p -> ChoiceDialog("이 글 신고하기", TALK_FLAG_REASONS, onPick = { vm.flagPost(p.id, it); flagging = null }, onDismiss = { flagging = null }) }
    replyTo?.let { Text("‘${it.body.take(20)}’에 답글", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    if (replyTo == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RecipeLinks.TALK_KINDS.forEach { (k, label) -> FilterChip(kind == k, { kind = k }, label = { Text(label) }) }
    }
    val problem = RecipeText.problem(body)
    OutlinedTextField(body, { body = it.take(500) }, label = { Text("글 쓰기 (연락처·금액·링크는 적지 않아요)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    if (problem != null) Text(RecipeText.problemText(problem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    com.bamnat.app.ui.company.RulesLink()
    // 1.36 [LIM-10] No daily cap; said only while reports restrict this install.
    talk.limit?.let { Text(it.line(talk.left), style = MaterialTheme.typography.labelSmall,
        color = if (talk.left == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    Row {
        if (replyTo != null) TextButton(onClick = { replyTo = null }) { Text("답글 그만두기") }
        Spacer(Modifier.weight(1f))
        // 1.39 조사 ⑦ 쓰기 전 한마디(막지 않아요).
        var nudge by remember { mutableStateOf(false) }
        val go = { vm.post(body.trim(), kind, replyTo?.id) { ok -> if (ok) { body = ""; replyTo = null } } }
        if (nudge) com.bamnat.app.ui.hub.NudgeDialog(onEdit = { nudge = false }, onSend = { nudge = false; go() })
        Button(onClick = { if (com.bamnat.core.plaza.PostNudge.needed(body)) nudge = true else go() },
            enabled = RecipeText.graphemes(body.trim()) >= 2 && problem == null) { Text("올리기") }
    }
    disliking?.let { p -> DislikeDialog(onSend = { r, o -> vm.votePost(p.id, -1, r, o); disliking = null }, onDismiss = { disliking = null }) }
}

/** [RVF-03] 광장 글 신고와 같은 이유(server FLAGS). */
internal val TALK_FLAG_REASONS = linkedMapOf("abuse" to "욕설·비방", "personal" to "개인 정보가 있어요", "spam" to "광고·도배", "false" to "일부러 틀린 정보")

/** [RVF-03] The posts that start a line: no parent, or a parent its writer took back. */
internal fun talkTops(posts: List<RecipePost>): List<RecipePost> =
    posts.filter { p -> !p.hidden && (p.parent == null || posts.none { it.id == p.parent && !it.hidden }) }

@Composable
private fun PostRow(p: RecipePost, onReply: (() -> Unit)?, onLike: () -> Unit, onDislike: () -> Unit, onDelete: () -> Unit, onFlag: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(listOfNotNull(
            if (p.mine) "나" else p.nick ?: "참여자 ${p.who}", "만든 분".takeIf { p.maker }, RecipeLinks.TALK_KINDS[p.kind],
            p.usingDays?.let { "쓰는 중 ${it}일" },
        ).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = if (p.maker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(p.body, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLike, enabled = !p.mine) { Text("좋아요 ${p.likes}", fontSize = 12.sp) }
            TextButton(onClick = onDislike, enabled = !p.mine && p.myVote != -1) { Text("안좋아요 ${p.dislikes}", fontSize = 12.sp) }
            onReply?.let { TextButton(onClick = it) { Text("답글", fontSize = 12.sp) } }
            // [RVF-03] 내 글은 지우기, 남의 글은 신고.
            if (p.mine) TextButton(onClick = onDelete) { Text("지우기", fontSize = 12.sp) }
            else TextButton(onClick = onFlag) { Text("신고", fontSize = 12.sp) }
        }
        if (p.mine && p.reasons.isNotEmpty()) p.reasons.take(5).forEach { (k, t) ->
            Text("• [${RecipeLinks.DISLIKE_REASONS[k] ?: k}] $t", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [RCP-24] 달력 테마 as the calendar would draw it: 토·일·공휴일 글자와 오늘 동그라미. */
@Composable
private fun ThemeSample(r: com.bamnat.core.recipe.Recipe, piece: com.bamnat.core.recipe.Piece) {
    val v = RecipeResolve.piece(r, piece, emptyMap())
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    val dark = com.bamnat.app.ui.components.isDarkSurface()
    val look = com.bamnat.app.ui.theme.CalendarLook.of(com.bamnat.core.recipe.CalendarTheme(v["today"] as? Long, v["sat"] as? Long, v["sun"] as? Long,
        v["holiday"] as? Long, v["cell"] as? String), dark)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("토 6", color = look.saturday(cal), style = MaterialTheme.typography.titleSmall)
        Text("일 7", color = look.sunday(cal), style = MaterialTheme.typography.titleSmall)
        Text("공휴일", color = look.holiday(cal), style = MaterialTheme.typography.titleSmall)
        val today = look.today ?: MaterialTheme.colorScheme.primary
        androidx.compose.foundation.layout.Box(Modifier.width(28.dp).height(28.dp).androidx_clip(today), contentAlignment = Alignment.Center) {
            // PC 확인 W5 1.36 (견본 동그라미가 늘 9): today's own date.
            Text("${com.bamnat.core.time.Today.date().dayOfMonth}", color = com.bamnat.app.ui.components.contentColorOn(today), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Modifier.androidx_clip(c: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(c, androidx.compose.foundation.shape.CircleShape))


/** [RCX-01] 1.35 계보: the recipes it was made from (원작자 표시는 지울 수 없음) and those made from it. */
@Composable
private fun LineageSection(l: com.bamnat.app.recipe.Lineage, onOpen: (String) -> Unit, look: com.bamnat.app.recipe.ThemeSwatch? = null) {
    if (l.parents.isEmpty() && l.children.isEmpty()) return
    Text("계보", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    l.parents.forEachIndexed { i, p ->
        if (p == null) Text("${"↑".repeat(i + 1)} 지워진 작품에서 고쳐 만들었어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else RecipeRow(p, Modifier) { onOpen(p.code) }
    }
    if (l.parents.isNotEmpty()) Text("위 작품에서 고쳐 만들었어요. 원래 만든 분 표시는 늘 남아요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (l.children.isNotEmpty()) {
        Text("이 작품을 고쳐 만든 것 ${l.children.size}개", style = MaterialTheme.typography.labelLarge)
        l.children.take(5).forEach { k -> ForkRow(k, look, l.changed[k.code].orEmpty()) { onOpen(k.code) } }
    }
}

/**
 * 1.36 누락 점검 N15 (1차 사용성 제안): 고친 판 한 줄에 반반 그림(왼쪽 이 판, 오른쪽 고친 판)과 '바꾼 곳' 한 줄,
 * so which 수정판 is worth opening shows without opening each.
 */
@Composable
private fun ForkRow(k: com.bamnat.app.recipe.RecipeItem, mineLook: com.bamnat.app.recipe.ThemeSwatch?, changed: List<Pair<String, String>>, onOpen: () -> Unit) {
    val words = changed.joinToString(" · ") { (kind, how) ->
        (PieceKind.entries.firstOrNull { it.key == kind }?.label ?: kind) + when (how) { "add" -> " 새로 넣음"; "del" -> " 뺌"; else -> " 바꿈" }
    }.ifEmpty { "조각은 같고 이름·설명·표지만 달라요" }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val theirs = k.look
            if (mineLook != null && theirs != null) {
                androidx.compose.foundation.layout.Box(Modifier.width(84.dp)) {
                    ThemeThumb(mineLook, width = 84.dp)
                    ThemeThumb(theirs, width = 84.dp, modifier = Modifier.drawWithContent {
                        clipRect(left = size.width / 2f) { this@drawWithContent.drawContent() }
                    })
                    androidx.compose.foundation.layout.Box(Modifier.align(Alignment.Center).width(2.dp).height(48.dp).background(MaterialTheme.colorScheme.outline))
                }
                Spacer(Modifier.width(10.dp))
            } else if (theirs != null) { ThemeThumb(theirs, width = 84.dp); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(k.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("바꾼 곳: $words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${k.nick ?: "이름 없음"} · 좋아요 ${k.likes} · 넣어 본 분 ${k.users}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** [RCX-24] This recipe is on this phone: when, how long 되돌리기 lasts, and the button (설계서 4-3 5: 소식과 레시피 화면에). */
@Composable
private fun AppliedCard(at: Long, laidOnly: Boolean = false, onUndo: () -> Unit) {
    val left = (com.bamnat.app.recipe.RecipeRepository.UNDO_DAYS - (com.bamnat.core.time.Today.millis() - at) / 86_400_000L).coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 달력에 넣어 둔 작품이에요", style = MaterialTheme.typography.titleSmall)
                Text(java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(at)) +
                    (if (laidOnly) " 테마 위에 얹음 · 언제든 되돌릴 수 있어요" else " 넣음 · ${left}일 안에 되돌릴 수 있어요"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text("되돌리기") }
        }
    }
}

/**
 * [LIN-02] 1.35 원래 판과 달라진 점: each piece this 수정판 added, took away or changed, and each changed field before → after. [LIN-03]
 * The maker of the original ticks what to take and makes their next 판 with it (사용자 2026-10-30 "내꺼랑 어떤점이 달라졌는지").
 */
@Composable
private fun DiffSection(parent: com.bamnat.app.recipe.RecipeDoc, diff: List<com.bamnat.core.recipe.RecipeDiff.Piece>, onOpen: (String) -> Unit,
                        onTake: (Set<Pair<PieceKind, String>>) -> Unit) {
    val mine = parent.mine
    var picked by remember(diff) { mutableStateOf(diff.flatMap { d -> if (d.change != com.bamnat.core.recipe.RecipeDiff.Kind.CHANGED || d.lines.isEmpty()) listOf(d.kind to "*") else d.lines.map { d.kind to it.path } }.toSet()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (mine) "내 판과 달라진 점" else "원래 판과 달라진 점", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            TextButton(onClick = { onOpen(parent.item.code) }) { Text("원래 판 ‘${parent.item.title}’ 보기") }
            if (diff.isEmpty()) Text("조각은 원래 판과 같아요. 이름·설명·표지만 달라요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            diff.forEach { d ->
                val head = d.kind.label + when (d.change) {
                    com.bamnat.core.recipe.RecipeDiff.Kind.ADDED -> " · 새로 넣음"
                    com.bamnat.core.recipe.RecipeDiff.Kind.REMOVED -> " · 뺌"
                    else -> " · 바꿈"
                }
                if (d.lines.isEmpty() || d.change != com.bamnat.core.recipe.RecipeDiff.Kind.CHANGED) {
                    DiffRow(head, null, mine, d.kind to "*" in picked) { on -> picked = if (on) picked + (d.kind to "*") else picked - (d.kind to "*") }
                } else {
                    Text(head, style = MaterialTheme.typography.labelLarge)
                    d.lines.take(20).forEach { l ->
                        DiffRow(l.label, (l.before ?: "없음") + " → " + (l.after ?: "없음"), mine, d.kind to l.path in picked) { on ->
                            picked = if (on) picked + (d.kind to l.path) else picked - (d.kind to l.path)
                        }
                    }
                    if (d.lines.size > 20) Text("그 밖에 ${d.lines.size - 20}칸", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (mine && diff.isNotEmpty()) {
                Text("고른 것만 내 판의 새 판으로 가져와요. 올리기 전에 한 번 더 보고 고칠 수 있고, 이 수정판 이름이 '도움 준 수정판'으로 설명에 남아요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bamnat.app.ui.components.Button(onClick = { onTake(picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("고른 ${picked.size}개 내 판에 가져오기")
                }
            }
        }
    }
}

@Composable
private fun DiffRow(label: String, change: String?, pickable: Boolean, on: Boolean, onPick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pickable) androidx.compose.material3.Checkbox(on, onPick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            change?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}



/** [TRY-06] 넣기 전에 기능을 내 앱에서 실제로 써 보기 (규칙·단추가 정말 돌고, 끝내면 원래대로). */
@Composable
internal fun TryFunctionsButton(kinds: List<PieceKind>, enabled: Boolean, start: suspend () -> Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.FilledTonalButton(onClick = {
        if (busy) return@FilledTonalButton
        busy = true
        note = null
        scope.launch {
            val ok = runCatching { start() }.getOrDefault(false)
            busy = false
            if (!ok) note = "이 기능은 지금 내 앱과 같아서 바뀌는 곳이 없어요"
        }
    }, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (busy) "준비하는 중…" else "넣기 전에 내 앱에서 실제로 써 보기")
            Text("정말 돌아요: " + kinds.joinToString("·") { it.label } + " · 끝내면 원래대로", style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

/** [TRY-06] What the 기능 did while tried, on the page they were tried from (until closed). */
@Composable
internal fun TriedCard(line: String, onClose: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
        }
    }
}

/** [RVM-08] PC-66: the 넣기 step and the line about it, kept on the bottom edge of 작품 상세. */
internal class PutBar(val step: LadderStep, val note: @Composable () -> Unit)

@Composable
private fun StickyPut(bar: PutBar) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bar.note()
                PutRow(bar.step)
            }
        }
    }
}

/** [RVM-16] PC-68: '빠른 설정에 넣기' after a work with a 빠른 설정 button went in; one line, its answer in the same place. */
@Composable
private fun TileOffer() {
    val ctx = LocalContext.current
    val c = ctx.appContainer
    var b by remember { mutableStateOf(com.bamnat.app.logic.FxOutside.tileOffer(c)) }
    var said by remember { mutableStateOf<String?>(null) }
    said?.let { line ->
        com.bamnat.app.ui.components.NudgeCard(line, tone = com.bamnat.app.ui.theme.Tone.Good, onClose = { said = null })
        return
    }
    val button = b ?: return
    val activity = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? android.app.Activity } ?: return
    com.bamnat.app.ui.components.NudgeCard(
        "빠른 설정에도 ‘${listOfNotNull(button.emoji, button.label).joinToString(" ")}’${com.bamnat.core.text.Josa.eulreul(button.label)} 둘 수 있어요",
        action = "빠른 설정에 넣기",
        onAction = { com.bamnat.app.logic.FxOutside.askTile(activity, button) { said = it; b = null } },
        onClose = { com.bamnat.app.logic.FxOutside.closeTileOffer(c); b = null },
    )
}
