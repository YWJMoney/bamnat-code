package com.bamnat.app.ui.recipe

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.RecipeText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(vm: RecipeDetailViewModel, onBack: () -> Unit, onRemake: (String) -> Unit,
    /** [RCX-01][RCX-05][RCX-11] 1.35: another recipe (계보, 묶음) and a maker's page. */
    onOpen: (String) -> Unit = {}, onMaker: (String) -> Unit = {},
    /** [RCX-31] 1.35: my own recipe, made again as its next 판 (받은 분들께 새 판으로 알려져요). */
    onNewVersion: (String) -> Unit = {},
    /** [LIN-03] 1.35: my recipe (code) made again with what a 수정판 changed (its written form). */
    onTakeInto: (String, String) -> Unit = { _, _ -> }) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf(false) }
    var disliking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var tryOn by remember { mutableStateOf(false) }
    var adNote by remember { mutableStateOf<String?>(null) }
    val adGate = confirming && vm.looksNeedAd()
    var folded by remember { mutableStateOf(true) }
    var undoing by remember { mutableStateOf(false) }
    LaunchedEffect(s.message) {
        val m = s.message ?: return@LaunchedEffect
        vm.shown()
        val undo = s.appliedId != null && m.startsWith("적용했어요")
        val r = snack.showSnackbar(m, actionLabel = if (undo) "되돌리기" else null, duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) vm.undo()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(s.doc?.item?.title ?: "레시피") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    val doc = s.doc
                    if (doc != null && doc.scope != "private") IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RecipeLinks.shareText(doc.item.title, vm.code))
                        runCatching { context.startActivity(Intent.createChooser(send, "레시피 링크 보내기")) }
                    }) { Icon(Icons.Filled.Share, "링크 보내기") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "더 보기") }
                    DropdownMenu(menu, { menu = false }) {
                        if (s.doc?.mine == true) DropdownMenuItem(text = { Text("새 판 만들기") }, onClick = { menu = false; vm.body()?.let(onNewVersion) }, enabled = s.recipe != null)
                        DropdownMenuItem(text = { Text("고쳐 만들기") }, onClick = { menu = false; vm.body()?.let(onRemake) }, enabled = s.recipe != null)
                        if (s.doc?.mine != true) DropdownMenuItem(text = { Text("신고하기") }, onClick = { menu = false; flagging = true })
                    }
                },
            )
        },
    ) { padding ->
        val doc = s.doc
        val r = s.recipe
        if (doc == null || r == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.error != null) { Text(s.error!!); TextButton(onClick = vm::load) { Text("다시 해 보기") } } else CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecipeCover(r.cover, 80.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleLarge)
                        if (r.summary.isNotEmpty()) Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(listOfNotNull(doc.item.nick?.let { "만든 분 $it" } ?: "만든 분 이름 없음", "v${doc.item.version}",
                            RecipeDescribe.styles(r)).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // [RCX-05] The maker's page: their recipes, badges and 팔로우.
                        doc.item.maker?.let { m -> TextButton(onClick = { onMaker(m) }) { Text("만든 분 페이지") } }
                    }
                }
                Text("쓰는 분 ${doc.item.users}명" + (doc.item.kept?.let { " · 7일 뒤에도 쓰는 분 $it%" } ?: " · 7일 유지율은 적용 20번부터 보여요"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VoteRow(s, vm, onDislike = { disliking = true })
                if (doc.mine) MakerCard(doc, onNewVersion = vm.body()?.let { b -> { onNewVersion(b) } })
                s.appliedAt?.let { at -> AppliedCard(at) { undoing = true } }
                if (r.desc.isNotEmpty()) Text(r.desc, style = MaterialTheme.typography.bodyMedium)
                // [LIN-02][LIN-03] 1.35 a 수정판 of my recipe: what it changed, and 내 판에 가져오기.
                s.parentDoc?.takeIf { it.mine }?.let { pd -> DiffSection(pd, s.diff, onOpen) { picked -> vm.takeInto(picked)?.let { body -> onTakeInto(pd.item.code, body) } } }

                Text("내 달력에 넣을 것 고르기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("켜 둔 조각만 들어가요. 근무 기록·가계부·메모는 그대로예요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.pieces.filter { it.fits }.forEach { p -> PieceCard(r, p, s.answers, vm) }
                val other = s.pieces.filter { !it.fits }
                if (other.isNotEmpty()) {
                    TextButton(onClick = { folded = !folded }) { Text((if (folded) "펼치기: " else "접기: ") + "내 유형에는 없는 조각 ${other.size}개") }
                    AnimatedVisibility(!folded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            other.forEach { p ->
                                Text("• ${p.kind.label}: " + RecipeDescribe.line(r.pieces.first { it.kind == p.kind }, amounts = false),
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
                    company = r.pieces.firstOrNull { it.kind == PieceKind.COMPANY }?.takeIf { companyOn }?.values?.get("setup") as? String)
                ScreenPreview(r) { k -> s.pieces.any { it.kind == k && it.on && it.fits } }
                val chosen = s.pieces.count { it.on && it.fits }
                val used = s.pieces.filter { it.on && it.fits }.flatMap { p -> RecipeResolve.inputsOf(r, r.pieces.first { it.kind == p.kind }) }.map { it.id }.toSet()
                val missing = r.inputs.filter { it.id in used && it.required && s.answers[it.id] == null && it.default == null }
                val outside = r.inputs.filter { it.id in used && RecipeResolve.rangeProblem(it, if (s.answers.containsKey(it.id)) s.answers[it.id] else it.default) != null }
                if (missing.isNotEmpty()) Text("‘${missing.first().label}’을(를) 채워 주세요", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                // 1.35 실사용 점검: the field already says the range in red; here only why the button waits, not a second red line.
                else if (outside.isNotEmpty()) Text("위 ‘${outside.first().label}’ 칸을 고치면 넣을 수 있어요", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                // [TRY-01] 1.35 (사용자 2026-10-30 21:45 "입어보기랑 넣기가 버튼 두개가 있을 필요가 없고"): a recipe with looks has one way in,
                // 입어 보기 — 지금 ↔ 입으면 and the parts to tick — whose button puts in what was ticked. One without looks is put in at once.
                val hasLooks = remember(r) { com.bamnat.core.recipe.LookParts.of(r).isNotEmpty() }
                val ready = missing.isEmpty() && outside.isEmpty() && !s.applying
                if (hasLooks) Button(onClick = { tryOn = true }, enabled = !s.applying, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (s.applying) "넣는 중…" else "입어 보고 고르기")
                } else Button(onClick = { confirming = true }, enabled = chosen > 0 && ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (s.applying) "넣는 중…" else "고른 ${chosen}개 내 달력에 넣기") }
                if (tryOn) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val others = s.pieces.filter { it.fits && it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }
                    TryOnScreen(r, vm.style, onDismiss = { tryOn = false },
                        onKeep = { ids -> android.widget.Toast.makeText(context, if (vm.keep(ids)) "보관함에 담았어요. 마을 › 테마와 스튜디오에서 꺼내 써요"
                            else "보관함이 꽉 찼어요 (${vm.partsLimit()}개)", android.widget.Toast.LENGTH_SHORT).show() },
                        extra = if (others.isEmpty()) null else { {
                            Text("기능 · 모양과 함께 넣을 것", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                            others.forEach { p ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(p.on, { vm.toggle(p.kind, it) })
                                    Column(Modifier.weight(1f)) {
                                        Text(p.kind.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(RecipeDescribe.line(r.pieces.first { it.kind == p.kind }, amounts = false), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                            }
                        } },
                    ) { picked ->
                        val n = picked.size + others.count { it.on }
                        if (!ready && missing.isNotEmpty()) Text("‘${missing.first().label}’을(를) 위에서 채우면 넣을 수 있어요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.lookParts(picked); tryOn = false; confirming = true }, enabled = n > 0 && ready,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (n == 0) "넣을 것을 골라 주세요" else "고른 ${n}개 넣기") }
                    }
                }

                // [RCX-11] 묶음: each recipe in it opens on its own, to be chosen piece by piece.
                r.pieces.firstOrNull { it.kind == PieceKind.BUNDLE }?.let { b ->
                    Text("묶음 안 레시피", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    @Suppress("UNCHECKED_CAST")
                    (b.values["items"] as? List<Map<String, Any?>>).orEmpty().forEach { i ->
                        val c = i["code"] as? String ?: return@forEach
                        OutlinedButton(onClick = { onOpen(c) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(((i["note"] as? String)?.let { "$it ($c)" } ?: c) + " 열기")
                        }
                    }
                }
                // [RCX-01] 계보: what it was made from, and what others made from it.
                s.lineage?.let { l -> LineageSection(l, onOpen) }
                // [LIN-02][LIN-03] 1.35 달라진 점, and for the maker of the recipe it came from, 내 판에 가져오기.
                // For anyone else it sits with the 계보; the maker of the original sees it first, above 넣기 (1.35 자유도 점검).
                s.parentDoc?.takeIf { !it.mine }?.let { pd -> DiffSection(pd, s.diff, onOpen) { } }
                HorizontalDivider()
                if (vm.canTalk) TalkSection(s, vm) else Text("이야기는 정식 출시일(11월 10일 예정)부터 열려요", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("이렇게 바뀌어요") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.pieces.filter { it.on && it.fits }.forEach { p ->
                    Text(p.kind.label, style = MaterialTheme.typography.labelLarge)
                    p.lines.ifEmpty { listOf(RecipePieces.Change("바뀌는 것이 없어요", true)) }.forEach { l ->
                        Text("• " + l.text, style = MaterialTheme.typography.bodySmall,
                            color = if (l.skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text("7일 안에는 설정 › 장터·내 레시피에서 되돌릴 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                adNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            // [FREE-10] 적용할 때만 광고 (사용자 2026-10-30 15:53): the looks of someone else's theme, past the first free ones.
            if (adGate) com.bamnat.app.ads.ThemeAdButton(
                "광고 보고 넣기", "모양(테마)은 광고를 끝까지 보면 30일 동안 써요. 알람·규칙 같은 기능은 기간 없이 계속 써요",
                onReward = { confirming = false; vm.apply { } },
                onQuit = { adNote = "광고를 끝까지 봐야 넣을 수 있어요" },
                onOffline = { adNote = "인터넷에 연결되면 다시 해 주세요" },
                onNoAd = { reason -> confirming = false; vm.noAdCounted(reason); vm.apply { } },
            ) else TextButton(onClick = { confirming = false; vm.apply { } }) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("닫기") } },
    )
    if (undoing) AlertDialog(
        onDismissRequest = { undoing = false },
        title = { Text("넣기 전으로 되돌릴까요?") },
        text = { Text("이 레시피가 바꾼 설정만 돌아가요. 그 사이 적은 근무·메모·가계부 기록과, 내가 따로 고친 칸은 그대로예요") },
        confirmButton = { TextButton(onClick = { undoing = false; vm.undo() }) { Text("되돌리기") } },
        dismissButton = { TextButton(onClick = { undoing = false }) { Text("닫기") } },
    )
    if (flagging) ChoiceDialog("신고하기", RecipeLinks.FLAG_REASONS, onPick = { vm.flag(it); flagging = false }, onDismiss = { flagging = false })
    if (disliking) DislikeDialog(onSend = { reason, opinion -> vm.vote(-1, reason, opinion); disliking = false }, onDismiss = { disliking = false })
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
    if (!vm.canVote) Text("좋아요·안좋아요는 정식 출시일(11월 10일 예정)부터 누를 수 있어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** [RCP-31] What the maker alone sees: the state of the recipe, the reviewer's note, and why people did not like it (never who). */
@Composable
private fun MakerCard(doc: com.bamnat.app.recipe.RecipeDoc, onNewVersion: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("내 레시피", style = MaterialTheme.typography.titleSmall)
            Text(stateLabel(doc.state, doc.scope), style = MaterialTheme.typography.bodyMedium)
            doc.note?.let { Text("운영자 메모: $it", style = MaterialTheme.typography.bodySmall) }
            doc.reasons?.let { rs ->
                if (rs.tally.isNotEmpty()) Text("안좋아요 이유: " + rs.tally.entries.joinToString(" · ") { (k, n) -> "${RecipeLinks.DISLIKE_REASONS[k] ?: k} $n" },
                    style = MaterialTheme.typography.bodySmall)
                rs.opinions.take(10).forEach { (k, t) -> Text("• [${RecipeLinks.DISLIKE_REASONS[k] ?: k}] $t", style = MaterialTheme.typography.bodySmall) }
                if (rs.opinions.isNotEmpty()) Text("의견을 보고 고친 새 판을 올리면 받는 분들께 알려져요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            // [RCX-31] The way to the next 판: what this phone has now, or the recipe as it is, changed and sent again.
            if (onNewVersion != null) androidx.compose.material3.Button(onClick = onNewVersion, modifier = Modifier.padding(top = 6.dp)) {
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
                    Text(RecipeDescribe.line(piece, amounts = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (p.kind == PieceKind.SHIFT_LOOK || p.kind == PieceKind.ALARMS) {
                    Text("내 근무와 이름이 같거나, 주간·야간처럼 역할이 같은 근무에 넣어요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.lines.forEach { l ->
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
    val tops = talk.posts.filter { it.parent == null && !it.hidden }
    tops.forEach { p ->
        PostRow(p, onReply = { replyTo = p }, onLike = { vm.votePost(p.id, if (p.myVote == 1) null else 1) }, onDislike = { disliking = p })
        talk.posts.filter { it.parent == p.id && !it.hidden }.forEach { c ->
            Row { Spacer(Modifier.width(20.dp)); PostRow(c, onReply = null, onLike = { vm.votePost(c.id, if (c.myVote == 1) null else 1) }, onDislike = { disliking = c }) }
        }
    }
    replyTo?.let { Text("‘${it.body.take(20)}’에 답글", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    if (replyTo == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RecipeLinks.TALK_KINDS.forEach { (k, label) -> FilterChip(kind == k, { kind = k }, label = { Text(label) }) }
    }
    val problem = RecipeText.problem(body)
    OutlinedTextField(body, { body = it.take(500) }, label = { Text("글 쓰기 (연락처·금액·링크는 적지 않아요)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    if (problem != null) Text(RecipeText.problemText(problem), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    talk.left?.let { Text("오늘 ${it}번 더 쓸 수 있어요", style = MaterialTheme.typography.labelSmall) }
    Row {
        if (replyTo != null) TextButton(onClick = { replyTo = null }) { Text("답글 그만두기") }
        Spacer(Modifier.weight(1f))
        Button(onClick = { vm.post(body.trim(), kind, replyTo?.id) { ok -> if (ok) { body = ""; replyTo = null } } },
            enabled = RecipeText.graphemes(body.trim()) >= 2 && problem == null) { Text("올리기") }
    }
    disliking?.let { p -> DislikeDialog(onSend = { r, o -> vm.votePost(p.id, -1, r, o); disliking = null }, onDismiss = { disliking = null }) }
}

@Composable
private fun PostRow(p: RecipePost, onReply: (() -> Unit)?, onLike: () -> Unit, onDislike: () -> Unit) {
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
            Text("9", color = com.bamnat.app.ui.components.contentColorOn(today), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Modifier.androidx_clip(c: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(c, androidx.compose.foundation.shape.CircleShape))


/** [RCX-01] 1.35 계보: the recipes it was made from (원작자 표시는 지울 수 없음) and those made from it. */
@Composable
private fun LineageSection(l: com.bamnat.app.recipe.Lineage, onOpen: (String) -> Unit) {
    if (l.parents.isEmpty() && l.children.isEmpty()) return
    Text("계보", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    l.parents.forEachIndexed { i, p ->
        if (p == null) Text("${"↑".repeat(i + 1)} 지워진 레시피에서 고쳐 만들었어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else RecipeRow(p, Modifier) { onOpen(p.code) }
    }
    if (l.parents.isNotEmpty()) Text("위 레시피에서 고쳐 만들었어요. 원래 만든 분 표시는 늘 남아요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (l.children.isNotEmpty()) {
        Text("이 레시피를 고쳐 만든 것 ${l.children.size}개", style = MaterialTheme.typography.labelLarge)
        l.children.take(5).forEach { k -> RecipeRow(k, Modifier) { onOpen(k.code) } }
    }
}

/** [RCX-24] This recipe is on this phone: when, how long 되돌리기 lasts, and the button (설계서 4-3 5: 소식과 레시피 화면에). */
@Composable
private fun AppliedCard(at: Long, onUndo: () -> Unit) {
    val left = (com.bamnat.app.recipe.RecipeRepository.UNDO_DAYS - (com.bamnat.core.time.Today.millis() - at) / 86_400_000L).coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 달력에 넣어 둔 레시피예요", style = MaterialTheme.typography.titleSmall)
                Text(java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(at)) + " 넣음 · ${left}일 안에 되돌릴 수 있어요",
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
                androidx.compose.material3.Button(onClick = { onTake(picked) }, enabled = picked.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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


