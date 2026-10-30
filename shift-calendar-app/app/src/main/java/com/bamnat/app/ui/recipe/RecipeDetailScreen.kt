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
    onOpen: (String) -> Unit = {}, onMaker: (String) -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf(false) }
    var disliking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var folded by remember { mutableStateOf(true) }
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
                if (doc.mine) MakerCard(doc)
                if (r.desc.isNotEmpty()) Text(r.desc, style = MaterialTheme.typography.bodyMedium)

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
                val chosen = s.pieces.count { it.on && it.fits }
                val missing = r.inputs.filter { it.required && s.answers[it.id] == null && it.default == null }
                if (missing.isNotEmpty()) Text("‘${missing.first().label}’을(를) 채워 주세요", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { confirming = true }, enabled = chosen > 0 && missing.isEmpty() && !s.applying,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (s.applying) "넣는 중…" else "고른 ${chosen}개 내 달력에 넣기") }

                // [RCX-11] 묶음: each recipe in it opens on its own, to be chosen piece by piece.
                r.pieces.firstOrNull { it.kind == PieceKind.BUNDLE }?.let { b ->
                    Text("묶음 안 레시피", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    @Suppress("UNCHECKED_CAST")
                    (b.values["items"] as? List<Map<String, Any?>>).orEmpty().forEach { i ->
                        val c = i["code"] as? String ?: return@forEach
                        OutlinedButton(onClick = { onOpen(c) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(listOfNotNull(c, i["note"] as? String).joinToString(" · ") + " 열기")
                        }
                    }
                }
                // [RCX-01] 계보: what it was made from, and what others made from it.
                s.lineage?.let { l -> LineageSection(l, onOpen) }
                HorizontalDivider()
                if (vm.canTalk) TalkSection(s, vm) else Text("이야기는 처리방침 3.8이 시행되는 11월 29일부터 열려요", style = MaterialTheme.typography.bodySmall)
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
                Text("7일 안에는 설정 › 나눔 판에서 되돌릴 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { TextButton(onClick = { confirming = false; vm.apply { } }) { Text("넣기") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("닫기") } },
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
    if (!vm.canVote) Text("좋아요·안좋아요는 11월 29일부터 누를 수 있어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** [RCP-31] What the maker alone sees: the state of the recipe, the reviewer's note, and why people did not like it (never who). */
@Composable
private fun MakerCard(doc: com.bamnat.app.recipe.RecipeDoc) {
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
        }
    }
}

fun stateLabel(state: String?, scope: String): String = when (state) {
    "review" -> "검토 중이에요. 처음 3개는 운영자가 보고 광장에 올려요"
    "returned" -> "돌려받았어요. 메모를 보고 고쳐서 다시 올려 주세요"
    "hidden" -> "가려졌어요"
    else -> when (scope) { "link" -> "링크로만 열려요"; "private" -> "나만 봐요"; else -> "광장에 올라가 있어요" }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
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
