package com.bamnat.app.ui.recipe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.recipe.Followers
import com.bamnat.app.recipe.Following
import com.bamnat.app.recipe.MakerFaceItem
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.MakerSet
import com.bamnat.app.recipe.MakerStats
import com.bamnat.app.recipe.ProfileEdit
import com.bamnat.app.recipe.ProfileInbox
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.SetDoc
import com.bamnat.app.tier.Feature
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeText
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role

// ---- [PRF-01]..[PRF-12] 1.36 창작자 프로필 (사용자 2026-10-31 "인스타처럼 내가 만든 테마를 멋지게, 팔로워·최근 알림, 팔로우한 분 프로필에서 공개된 것") ----

/** 대표 작품 수: 3, 프로 꾸미기 6 (server COMMUNITY_LIMITS.pins). */
/** 프로필에서의 실패 말: 레시피 말("지워졌거나 가려진 레시피예요", "더 올릴 수 없어요") 대신 프로필·팔로우에 맞게. */
internal fun profileProblem(e: Throwable): String = when ((e as? com.bamnat.app.company.CompanyException)?.code) {
    "not_found" -> "볼 수 없는 프로필이에요"
    "nick_length" -> "이름은 2~12자로 정해 주세요"
    "taken" -> com.bamnat.app.ui.village.VillageName.TAKEN
    "nick" -> "쓸 수 없는 이름이에요. 관리자·개발자·운영자 같은 말은 빼 주세요"
    "rate_limited" -> com.bamnat.app.company.RateLimits.wait((e as com.bamnat.app.company.CompanyException).retryAfter ?: 86_400).let { w ->
        "오늘은 여기까지 할 수 있어요. ${w.removeSuffix(" 뒤")}${if (w.endsWith("뒤")) " 뒤에" else "에"} 다시 해 주세요"
    }
    else -> RecipeClient.problem(e)
}

/** The kind of work of the one looking (탭 이름 and examples in their words: 알바면 출근·시급). */
internal fun viewerStyle(c: AppContainer): WorkStyle? = c.calendars.styleOf(com.bamnat.app.data.PersonalCalendar.MAIN, c.settingsRepository.current.workStyle)

internal fun setExample(style: WorkStyle?): String = when (style) {
    WorkStyle.DAY -> "평일 출근 세트"
    WorkStyle.HOURLY -> "알바 출근 세트"
    WorkStyle.FIELD -> "현장 일정 세트"
    WorkStyle.BUSINESS -> "가게 장부 세트"
    WorkStyle.INVEST -> "매매 기록 세트"
    else -> "야간 3교대 세트"
}

/** [viewerStyle] for the tiles deep in a profile or 모음. */
internal val LocalViewerStyle = androidx.compose.runtime.staticCompositionLocalOf<WorkStyle?> { null }

internal fun pinLimit(c: AppContainer): Int = if (c.featureGate.allows(Feature.CREATOR_PRO)) 6 else 3

/** The words of 프로필 신고's reasons (server PROFILE_FLAG_REASONS). */
internal val PROFILE_FLAG_WORDS = linkedMapOf("impersonate" to "다른 사람인 척해요", "bio" to "소개 글이 나빠요", "spam" to "광고·도배예요", "other" to "그 밖의 것")

class MakerViewModel(val c: AppContainer, val id: String) : ViewModel() {
    private val device get() = c.companyStore.device
    val canFollow: Boolean get() = c.flags.on(FlagCatalog.RECIPE_TALK)
    /** 소개·모음 are written like a recipe: from 정식 출시일 (처리방침 3.8). */
    val canWrite: Boolean get() = c.flags.on(FlagCatalog.RECIPE_PUBLISH)

    data class State(
        val page: MakerPage? = null, val error: String? = null, val message: String? = null,
        /** The maker's own: 최근 알림 and the keys not yet seen. */
        val inbox: ProfileInbox? = null, val unseen: Set<String> = emptySet(),
        /** 보는 사람 눈으로 보기. */
        val asOthers: Boolean = false,
        val busy: Boolean = false,
        /** The profile is not there (hidden, closed or blocked me): nothing to try again. */
        val gone: Boolean = false,
        /** 신고를 보낸 뒤 받았다는 창. */
        val flagged: Boolean = false,
    )
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    private var shown = false
    /** 1.36 PC: coming back to the page (from another screen or app) reads it again, so 운영자 가림·닫힘 shows without leaving it. */
    fun resumed() {
        if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.d("BamnatProfile", "resumed shown=$shown")
        if (shown) load() else shown = true
    }

    fun load() {
        viewModelScope.launch {
            // 1.36 확인: a link opened right after install could find the network not up yet; one quiet try again.
            val got = runCatching { c.recipeApi.maker(device, id) }.let { r ->
                if ((r.exceptionOrNull() as? com.bamnat.app.company.CompanyException)?.code == "offline") {
                    kotlinx.coroutines.delay(1_500); runCatching { c.recipeApi.maker(device, id) }
                } else r
            }
            _state.value = got
                .fold({ p ->
                    if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.d("BamnatProfile", "loaded mine=${p.mine} bioHidden=${p.bioHidden} by=${p.bioHiddenBy}")
                    if (p.mine) c.recipes.hasProfile(p.recipes.isNotEmpty())
                    _state.value.copy(page = p, error = null)
                }, { _state.value.copy(error = profileProblem(it), gone = (it as? com.bamnat.app.company.CompanyException)?.code == "not_found") })
            if (_state.value.page?.mine == true) loadInbox()
        }
    }

    private fun loadInbox() {
        viewModelScope.launch {
            // Since the last 다 봤어요 (7 days at first), so what was seen does not come back as new.
            // U11 (1.36 PC 사용성): the last 7 days, so what was seen stays as a grey line; what is new comes from the seen keys.
            val box = runCatching { c.recipeApi.inbox(device, null) }.getOrNull() ?: return@launch
            val seen = c.recipes.seenInbox()
            _state.value = _state.value.copy(inbox = box, unseen = inboxKeys(box).filterNot { it in seen }.toSet())
        }
    }

    /** 다 봤어요: what 최근 알림 shows now is no longer new (it folds to one line). */
    fun seen() {
        val box = _state.value.inbox ?: return
        c.recipes.markInboxSeen(inboxKeys(box), box.at)
        _state.value = _state.value.copy(unseen = emptySet())
    }

    private fun say(msg: String) { _state.value = _state.value.copy(message = msg) }

    private fun act(done: String?, f: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { f() }.onSuccess { load(); done?.let(::say) }.onFailure { say(profileProblem(it)) }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun follow(on: Boolean) = act(if (on) "팔로우했어요. 새 작품이 오면 마을 맨 위 얼굴에 테두리가 생기고 밤낮 소식으로도 알려요" else "팔로우를 그만뒀어요") {
        c.recipeApi.follow(device, id, on)
        c.recipes.peopleChanged()
    }

    fun quiet(q: Boolean) = act(if (q) "이분 새 작품 알림을 껐어요. 팔로우는 그대로예요" else "이분 새 작품 알림을 켰어요") { c.recipeApi.followQuiet(device, id, q) }

    fun block(on: Boolean) = act(if (on) "차단했어요. 이분의 작품과 이야기가 내 장터에서 보이지 않아요. 마을 › 팔로잉 › 차단에서 풀 수 있어요" else "차단을 풀었어요") {
        c.recipeApi.block(device, id, on)
        c.recipes.block(id, on)
        c.recipes.peopleChanged()
    }

    fun flag(reason: String) = act(null) { c.recipeApi.flagMaker(device, id, reason); _state.value = _state.value.copy(flagged = true) }
    fun flagSeen() { _state.value = _state.value.copy(flagged = false) }

    /**
     * [PRF-01] 1.36 PC 사용성 P2: 프로필만 지우기. 얼굴·소개·대표 작품·표지·모음이 지워지고, [works]면 올린 작품도
     * 장터에서 내려요. 팔로워는 남지만 작품이 없으면 프로필 줄에 내가 보이지 않아요.
     */
    fun clearProfile(works: Boolean) = act(if (works) "프로필과 작품을 지웠어요" else "프로필을 지웠어요. 작품은 장터에 그대로 있어요") {
        val p = _state.value.page ?: return@act
        p.sets.forEach { runCatching { c.recipeApi.setDelete(device, it.id) } }
        c.recipeApi.profile(device, ProfileEdit(bio = "", pins = emptyList(), setStyle = true, styleTag = null, setCover = true, cover = null))
        c.recipeApi.makerMe(device, null, null, set = true)
        if (works) p.recipes.forEach { runCatching { c.recipeApi.delete(device, it.code) } }
        c.recipes.hasProfile(!works && p.recipes.isNotEmpty())
        c.recipes.profileCleared(!works && p.recipes.isNotEmpty())
        c.recipes.peopleChanged()
    }

    /** [PRF-01] 이름: the same 별명 as 광장 (2~12자), saved with the rest of 프로필 고치기. */
    fun save(e: ProfileEdit, nick: String?, done: (String?) -> Unit = {}) {
        // 1.36 PC 재검토 (이미 쓰인 이름이면 창만 닫히고 이름도 소개도 저장되지 않았어요): 소개·대표 작품 first, on their own; then
        // the name, whose problem comes back to the open sheet ([done]) instead of a snackbar behind it.
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            c.recipes.profileCleared(false)
            val rest = runCatching { c.recipeApi.profile(device, e) }
            val name = if (nick == null) null else runCatching {
                val t = nick.trim()
                if (t.isNotEmpty() && t.length !in 2..12) throw com.bamnat.app.company.CompanyException("nick_length")
                c.companyApi.nick(device, t)
            }
            _state.value = _state.value.copy(busy = false)
            load()
            val nameProblem = name?.exceptionOrNull()?.let(::profileProblem)
            rest.exceptionOrNull()?.let { say(profileProblem(it)) }
            if (nameProblem == null && rest.isSuccess) say("프로필을 고쳤어요")
            done(nameProblem?.let { if (rest.isSuccess) "$it · 이름 말고 고친 것은 저장했어요. 다른 이름을 적어 주세요" else it })
        }
    }

    fun face(emoji: String?, color: Long?) = act(null) { c.recipes.profileCleared(false); c.recipeApi.makerMe(device, emoji, color, set = true) }

    fun edit(e: ProfileEdit, done: String = "프로필을 고쳤어요") = act(done) { c.recipeApi.profile(device, e) }

    fun saveSet(set: MakerSet?, title: String, summary: String, codes: List<String>, ok: () -> Unit) {
        viewModelScope.launch {
            runCatching { c.recipeApi.setSave(device, set?.id, title.trim(), summary.trim(), codes) }
                .onSuccess { load(); say(if (set == null) "모음을 만들었어요" else "모음을 고쳤어요"); ok() }
                .onFailure { say(RecipeClient.problem(it)) }
        }
    }

    fun deleteSet(set: MakerSet) = act("모음을 지웠어요") { c.recipeApi.setDelete(device, set.id) }

    /** [PRF-12] 표지 그림: made small on the phone (no EXIF) and sent; it shows once the operator has looked. */
    fun coverPhoto(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) { edit(ProfileEdit(setCover = true, cover = null), "표지 그림을 뺐어요"); return }
        viewModelScope.launch {
            val webp = com.bamnat.app.recipe.CoverImages.encode(bitmap)
            if (webp == null) { say("사진이 너무 작거나 읽을 수 없어요"); return@launch }
            runCatching {
                val img = c.recipeApi.image(device, webp)
                android.graphics.BitmapFactory.decodeByteArray(webp, 0, webp.size)?.let { com.bamnat.app.recipe.CoverImages.put(img, it) }
                c.recipeApi.profile(device, ProfileEdit(setCover = true, cover = img))
            }.onSuccess { load(); say("표지 그림을 올렸어요. 운영자가 본 뒤 다른 분께 보여요") }.onFailure { say(RecipeClient.problem(it)) }
        }
    }

    fun asOthers(v: Boolean) { _state.value = _state.value.copy(asOthers = v) }
    fun shown() { _state.value = _state.value.copy(message = null) }

    /** 프로필 링크: the share server's page for it (앱이 있으면 앱으로 열려요). */
    fun link(): String = com.bamnat.app.recipe.RecipeLinks.profile(id)

    companion object {
        /** Each thing 최근 알림 can say, as a key the phone remembers once seen. */
        fun inboxKeys(b: ProfileInbox): List<String> = buildList {
            // 1.36 시험: the key once had the since in it, which 다 봤어요 itself moves, so it never matched and came back as new.
            if (b.newFollowers > 0) add("f|${b.followersLatest ?: b.newFollowers}")
            b.questions.forEach { add("q|${it.first}|${it.third.take(20)}") }
            b.remixes.forEach { add("r|${it.first}") }
            b.milestones.forEach { add(it.key) }
            b.badges.forEach { add("b|${it.first}") }
        }
    }
}

/** A face with a ring when there is something new (마을 팔로잉 줄, like a story). */
@Composable
fun MakerRing(face: String?, color: Long?, nick: String?, fresh: Boolean, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val ring = if (fresh) Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))
    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
    Box(Modifier.size(size + 8.dp).clip(CircleShape).background(ring).padding(if (fresh) 3.dp else 1.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp)) { MakerFace(face, color, nick, size) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MakerScreen(
    vm: MakerViewModel, onBack: () -> Unit, onOpen: (String) -> Unit,
    onSet: (String) -> Unit = {}, onPeople: (String) -> Unit = {}, onStats: () -> Unit = {}, onPro: () -> Unit = {},
    /** 내가 보낸 정보 (이 휴대폰이 보낸 정보 모두 지우기가 맨 아래). */
    onSent: (() -> Unit)? = null,
    /** 1.36 PC: 첫 작품 만들기 (스튜디오) from my empty profile. */
    onMake: () -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.resumed() }
    // 1.36 PC 재검토 (열어 둔 채 가려지면 HOME → 돌아와도 안 보였어요): while a page is on screen it looks again each minute too (mine: 가림 카드, others: a 소개 just hidden goes).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    if (s.page != null) LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { kotlinx.coroutines.delay(60_000); vm.load() }
        }
    }
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var coloring by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    /** [PRF-05] 1.36 PC 사용성 P7: 빈 프로필의 '작품도 내리기' opens the same dialog with the works ticked. */
    var clearWorks by remember { mutableStateOf(false) }
    var followMenu by remember { mutableStateOf(false) }
    var setEdit by remember { mutableStateOf<MakerSet?>(null) }
    var newSet by remember { mutableStateOf(false) }
    var part by rememberSaveable { mutableStateOf(0) }
    // 1.36 PC 사용성 P12: open on the first tab that has something (테마 0이면 기능별 탭, 그것도 없으면 모음), once.
    var partPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.page) {
        val pg = s.page ?: return@LaunchedEffect
        if (partPicked || pg.blocked) return@LaunchedEffect
        partPicked = true
        part = when { pg.themes.isNotEmpty() -> 0; pg.tabRecipes.isNotEmpty() -> 1; pg.sets.isNotEmpty() -> 2; else -> 0 }
    }
    var tabFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // PC 확인 20ab0254: cleared after it was shown; cleared first, the key changed and took the snackbar down at once.
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.shown() } }
    val p = s.page
    val mine = p?.mine == true && !s.asOthers
    androidx.compose.runtime.CompositionLocalProvider(LocalViewerStyle provides viewerStyle(vm.c)) {
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text(p?.nick ?: "프로필", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    if (p != null && !p.blocked && !p.hidden) IconButton(onClick = {
                        val text = "밤낮 마을에서 ${p.nick ?: "이분"}의 테마와 기능을 구경해 보세요\n${vm.link()}"
                        runCatching {
                            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "프로필 보내기"))
                        }
                    }) { Icon(Icons.Default.Share, "프로필 보내기") }
                    if (p != null) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "더 보기") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (p.mine) {
                                DropdownMenuItem(text = { Text(if (s.asOthers) "내 눈으로 보기" else "보는 사람 눈으로 보기") }, onClick = { menu = false; vm.asOthers(!s.asOthers) })
                                DropdownMenuItem(text = { Text(if (p.hidden) "프로필 다시 보이기" else "프로필 숨기기") }, onClick = {
                                    menu = false
                                    vm.edit(ProfileEdit(hidden = !p.hidden), if (p.hidden) "프로필이 다시 보여요" else "프로필을 숨겼어요. 작품은 장터에 그대로 있고, 프로필과 팔로우만 막혀요")
                                })
                                DropdownMenuItem(text = { Text("프로필만 지우기") }, onClick = { menu = false; clearing = true })
                                onSent?.let { go -> DropdownMenuItem(text = { Text("내가 보낸 정보·모두 지우기") }, onClick = { menu = false; go() }) }
                            } else {
                                DropdownMenuItem(text = { Text(if (p.blocked) "차단 풀기" else "차단하기") }, onClick = { menu = false; if (p.blocked) vm.block(false) else blocking = true })
                                if (!p.blocked) DropdownMenuItem(text = { Text("신고하기") }, onClick = { menu = false; flagging = true })
                            }
                        }
                    }
                })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p == null) {
                    Text(s.error ?: "불러오는 중이에요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    if (s.error != null && !s.gone) TextButton(onClick = vm::load, modifier = Modifier.padding(horizontal = 8.dp)) { Text("다시 해 보기") }
                    if (s.gone) Text("숨겼거나 닫힌 프로필이에요. 작품은 장터에서 볼 수 있어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    return@Column
                }
                if (p.blocked) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MakerFace(p.face, p.color, p.nick, 56.dp); Spacer(Modifier.width(12.dp))
                            Text("차단한 분이에요. 이분의 작품과 이야기는 보이지 않아요", style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(onClick = { vm.block(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("차단 풀기") }
                    }
                    return@Column
                }
                Header(p, mine, vm, onPeople = onPeople, onFace = { picking = true }, onFollowMenu = { followMenu = true })
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1.36 PC 사용성 P14: 닫힘·숨김·소개 가림·쓰기 제한을 '지금 내 프로필 상태' 한 카드로 (둘 이상일 때 제목).
                    if (mine) {
                        val lines = buildList<Pair<String, (() -> Unit)?>> {
                            if (p.closed) add("⛔ 운영 규칙에 따라 프로필이 닫혔어요. 다른 분은 볼 수도 팔로우할 수도 없고, 작품은 장터에 그대로 있어요. 궁금하면 설정 › 도움말과 문의로 물어봐 주세요." to null)
                            if (p.hidden) add("🔒 프로필을 숨겨 두었어요. 다른 분은 볼 수 없어요 (⋮ › 프로필 다시 보이기)" to null)
                            // 1.36 PC 사용성 P10: why (the reports' most common reason) and 지금 고치기.
                            if (p.bioHidden) add((if (p.bioHiddenBy == "operator") "✋ 운영자가 소개 글을 가렸어요. 운영 규칙에 맞게 고치면 다시 보여요"
                                else "✋ 소개 글이 신고로 가려져 있어요" + (bioWhy(p.bioHiddenWhy)?.let { " (많이 받은 이유: $it)" } ?: "") + ". 고치면 다시 보여요") to { editing = true })
                            p.limitPerDay?.let { n ->
                                // 1.36 PC 6차: the same words (and date) as 광장's 쓰기 제한 line.
                                val until = p.limitUntil
                                // PC 확인 보안 6차: who hid what ("운영자가 프로필을 가려"), not always 신고.
                                val lim = p.limit ?: until?.let { com.bamnat.app.company.WriteLimit(it, n) }
                                add(("⏳ " + (if (lim != null && n > 0) lim.line(null)
                                    else (lim?.cause() ?: "신고가 쌓여") + " " + (until?.let { com.bamnat.app.ui.Fmt.monthDay(it.atZone(com.bamnat.core.time.Today.zone()).toLocalDate()) + "까지 " }.orEmpty()) + "글을 쓸 수 없어요") +
                                    ". 기간이 지나면 저절로 풀려요") to null)
                            }
                        }
                        if (lines.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lines.size > 1) Text("지금 내 프로필 상태", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            lines.forEach { (line, fix) ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (fix != null) TextButton(onClick = fix, modifier = Modifier.heightIn(min = 48.dp)) { Text("지금 고치기") }
                            }
                        }
                    }
                    // [PRF-05] 1.36 PC 사용성 P7: 프로필만 지운 뒤 — what is left and the next step.
                    if (mine && !s.asOthers && vm.c.recipes.profileCleared() && p.face == null && p.bio.isNullOrBlank() && p.pins.isEmpty() && p.sets.isEmpty() && p.cover == null && p.recipes.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("작품 ${p.recipes.size}개만 남은 빈 프로필이에요. 다른 분은 작품의 ‘만든 분’으로 들어와요",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clearWorks = true; clearing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("작품도 내리기") }
                        }
                    }
                    if (mine) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { editing = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("프로필 고치기") }
                            OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("작품 통계") }
                        }
                        s.inbox?.let { InboxCard(it, s.unseen, onOpen, onPeople = { onPeople("followers") }, onSeen = vm::seen) }
                        // 1.36 PC: my profile before the first work: what it is for and the next step, not three empty tabs.
                        if (p.recipes.isEmpty() && !vm.c.recipes.profileCleared()) Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("아직 작품이 없어요", style = MaterialTheme.typography.titleSmall)
                            Text("스튜디오에서 만든 테마나 탭별 작품을 장터에 올리면 여기 모이고, 다른 분이 팔로우할 수 있어요",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = onMake, modifier = Modifier.heightIn(min = 48.dp)) { Text("첫 작품 만들기") }
                        }
                    }
                }
                // 테마 | 기능별 탭 | 모음
                PrimaryTabRow(selectedTabIndex = part) {
                    Tab(part == 0, { part = 0 }, text = { Text("테마 ${p.themes.size}") })
                    Tab(part == 1, { part = 1 }, text = { Text("탭별 작품 ${p.tabRecipes.size}") })
                    Tab(part == 2, { part = 2 }, text = { Text("모음 ${p.sets.size}") })
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (part) {
                        0 -> Grid(p.themes.sortedByDescending { it.code in p.pins }, p.pins, empty = if (mine) "공방 › 스튜디오에서 만들어 올리면 여기 모여요" else "아직 올린 테마가 없어요", onOpen)
                        1 -> {
                            val tabs = p.tabRecipes.mapNotNull { it.tabs.firstOrNull() }.distinct()
                            if (tabs.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(tabFilter == null, { tabFilter = null }, label = { Text("전체") })
                                tabs.forEach { t -> FilterChip(tabFilter == t, { tabFilter = t }, label = { Text(marketTabLabel(t, viewerStyle(vm.c))) }) }
                            }
                            Grid(p.tabRecipes.filter { tabFilter == null || it.tabs.firstOrNull() == tabFilter }.sortedByDescending { it.code in p.pins }, p.pins,
                                empty = if (mine) "탭 하나만 바꾼 작품은 여기 모여요" else "아직 올린 탭별 작품이 없어요", onOpen)
                        }
                        else -> {
                            if (mine) {
                                Text("내 테마 하나에 탭별 작품을 묶어 두면, 받는 분이 한 번에 입어 보고 차례로 넣어요", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (vm.canWrite && p.recipes.size >= 2) FilledTonalButton(onClick = { newSet = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모음 만들기") }
                                else if (p.recipes.size < 2) Text("작품이 2개 이상이면 모음을 만들 수 있어요", style = MaterialTheme.typography.bodySmall)
                            }
                            if (p.sets.isEmpty()) Text(if (mine) "아직 모음이 없어요" else "아직 모음이 없어요", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.sets.forEach { set -> SetCard(set, p.recipes, mine, onOpen = { onSet(set.id) }, onEdit = { setEdit = set }, onDelete = { vm.deleteSet(set) }) }
                        }
                    }
                }
                if (p.badges.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("배지", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.badges.forEach { (_, name) -> AssistChip(onClick = {}, label = { Text("🏅 $name") }) } }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }
    val page = s.page
    if (picking) EmojiPickerDialog(onPick = { e -> picking = false; vm.face(e, page?.color) }, onDismiss = { picking = false })
    if (coloring) ColorPickerDialog(initial = page?.color ?: 0xFF5C6BC0L, onPick = { col -> coloring = false; vm.face(page?.face, col) }, onDismiss = { coloring = false })
    if (editing && page != null) ProfileEditDialog(page, vm, pinLimit(vm.c), onFace = { editing = false; picking = true },
        onColor = { editing = false; coloring = true }, onPro = { editing = false; onPro() }, onDismiss = { editing = false })
    if (followMenu && page != null) AlertDialog(
        onDismissRequest = { followMenu = false },
        title = { Text("팔로잉") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("새 작품 알림", style = MaterialTheme.typography.bodyLarge)
                        Text("꺼도 팔로우는 그대로, 마을 맨 위 얼굴에서 볼 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(!page.quiet, { on -> vm.quiet(!on) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { followMenu = false }) { Text("닫기") } },
        dismissButton = { TextButton(onClick = { followMenu = false; vm.follow(false) }) { Text("팔로우 그만두기") } },
    )
    if (blocking) AlertDialog(
        onDismissRequest = { blocking = false },
        title = { Text(page?.nick?.let { "$it${com.bamnat.app.ui.Fmt.obj(it)} 차단할까요?" } ?: "이분을 차단할까요?") },
        text = { Text("서로 팔로우가 끊기고, 이분의 작품과 이야기가 내 장터에서 보이지 않아요. 이분은 내 프로필을 볼 수 없어요. 차단한 것은 알리지 않아요. 마을 › 팔로잉 › 차단에서 언제든 풀 수 있어요.") },
        confirmButton = { TextButton(onClick = { blocking = false; vm.block(true) }) { Text("차단") } },
        dismissButton = { TextButton(onClick = { blocking = false }) { Text("닫기") } },
    )
    if (flagging) {
        var why by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { flagging = false },
            title = { Text("프로필 신고") },
            text = {
                Column {
                    PROFILE_FLAG_WORDS.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { why = k }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(why == k, { why = k }); Text(v)
                        }
                    }
                    Text("작품 하나가 문제면 그 작품의 ⋮ › 신고를 써 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { why?.let { flagging = false; vm.flag(it) } }, enabled = why != null) { Text("신고") } },
            dismissButton = { TextButton(onClick = { flagging = false }) { Text("닫기") } },
        )
    }
    if (clearing && page != null) {
        var works by remember { mutableStateOf(clearWorks) }
        AlertDialog(
            onDismissRequest = { clearing = false; clearWorks = false },
            title = { Text("프로필을 지울까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("소개·얼굴·대표 작품·표지 그림과 모음 ${page.sets.size}개가 지워져요. 팔로워는 그대로예요." +
                        (page.nick?.let { " 이름 ‘$it’${com.bamnat.app.ui.Fmt.eun("‘$it’")} 광장·이야기에도 쓰여서 남아요." } ?: ""))
                    if (page.recipes.isNotEmpty()) Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { works = !works }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(works, { works = it })
                        Text("올린 작품 ${page.recipes.size}개도 장터에서 내리기")
                    }
                    Text(if (works) "작품을 내리면 넣어 쓰던 분들의 화면은 그대로지만, 장터에서는 더 볼 수 없어요."
                        else "작품은 장터에 남고, 작품의 ‘만든 분’을 누르면 빈 프로필이 보여요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { clearing = false; clearWorks = false; vm.clearProfile(works) }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { clearing = false; clearWorks = false }) { Text("닫기") } },
        )
    }
    if (s.flagged) AlertDialog(
        onDismissRequest = vm::flagSeen,
        title = { Text("신고를 받았어요") },
        text = { Text("운영자가 살펴보고 운영 규칙에 어긋나면 소개를 가리거나 프로필을 닫아요. 누가 신고했는지는 이분에게도 운영자에게도 보이지 않아요. 더 보고 싶지 않으면 ⋮ › 차단하기를 써 주세요.") },
        confirmButton = { TextButton(onClick = vm::flagSeen) { Text("닫기") } },
    )
    if ((newSet || setEdit != null) && page != null) SetEditDialog(setEdit, page.recipes, viewerStyle(vm.c),
        onSave = { title, summary, codes -> vm.saveSet(setEdit, title, summary, codes) { newSet = false; setEdit = null } },
        onDismiss = { newSet = false; setEdit = null })
}

/** The 프로필 head: 표지, face, name, 유형, 소개, 숫자 줄, and 팔로우 or 내 것. */
/** 1.36 PC 사용성 P10: a 신고 reason in words. */
internal fun bioWhy(reason: String?): String? = when (reason) {
    "impersonate" -> "다른 사람인 척"; "bio" -> "소개가 불쾌함"; "spam" -> "광고·도배"; "other" -> "기타"; else -> null
}

/** 1.36 [V20] 새싹·이웃·단골. */
internal fun trustWord(level: Int): String = when (level) { 0 -> "🌱 새싹"; 1 -> "🏡 이웃"; else -> "⭐ 단골" }

/** 1.36 [V20] The maker's own next step, in a few words. */
internal fun trustNext(p: MakerPage): String? = when {
    p.trust == null || p.trust >= 2 -> null
    p.trust == 1 && p.trustHeld -> "가려진 글이 있어 단골은 다음에 돼요"
    p.trust == 0 -> p.trustDays?.let { "${it}일 뒤 이웃이 돼요 (좋아요가 온전히 세져요)" }
    else -> p.trustDays?.let { "가려진 것 없이 ${it}일 더 쓰면 단골이 돼요" }
}

@Composable
private fun Header(p: MakerPage, mine: Boolean, vm: MakerViewModel, onPeople: (String) -> Unit, onFace: () -> Unit, onFollowMenu: () -> Unit) {
    val c = vm.c
    val cover by produceState<android.graphics.Bitmap?>(null, p.cover, p.coverPending) {
        value = (p.cover ?: p.coverPending?.takeIf { p.mine })?.let { com.bamnat.app.recipe.CoverImages.load(c, it) }
    }
    Box(Modifier.fillMaxWidth()) {
        val band = p.color?.asColor() ?: MaterialTheme.colorScheme.primaryContainer
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(band, band.copy(alpha = 0.35f))))) {
            cover?.let { Image(it.asImageBitmap(), contentDescription = "표지 그림", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Box(Modifier.padding(start = 16.dp, top = 52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(4.dp)
            .then(if (mine) Modifier.clickable(onClickLabel = "얼굴 바꾸기", onClick = onFace) else Modifier)) {
            MakerFace(p.face, p.color, p.nick, 84.dp)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.nick ?: "이름 없는 분", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            p.styleTag?.let { st ->
                Spacer(Modifier.width(8.dp))
                Text(RecipeDescribe.short(st), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        // 1.36 [V20] 보이는 믿음 단계: everyone sees the step; the maker alone sees the next one and a 쓰기 제한.
        p.trust?.let { t ->
            // 1.36 PC 사용성 P11: 누르면 단계 설명.
            var explaining by remember { mutableStateOf(false) }
            Text(trustWord(t) + if (mine) trustNext(p)?.let { " · $it" }.orEmpty() else " ⓘ", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClickLabel = "믿음 단계 설명") { explaining = true }.heightIn(min = 32.dp))
            if (explaining) AlertDialog(
                onDismissRequest = { explaining = false },
                title = { Text("새싹 · 이웃 · 단골") },
                text = { Text("🌱 새싹: 마을을 쓴 지 3일 전이에요. 누른 좋아요가 반만 세져요.\n🏡 이웃: 3일이 지났어요.\n⭐ 단골: 가려진 글 없이 14일을 썼어요.\n\n" +
                    "단계는 쓴 날과 가려진 글로만 정해지고, 다른 정보는 쓰지 않아요.") },
                confirmButton = { TextButton(onClick = { explaining = false }) { Text("닫기") } },
            )
        }
        // 1.36 PC 재검토: my hidden 소개 reads as hidden (grey, struck through, with why), never as if others saw it.
        p.bio?.takeIf { !p.bioHidden || mine }?.let {
            if (p.bioHidden) Column {
                Text(it, style = MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text(if (p.bioHiddenBy == "operator") "운영자가 가려 다른 분께는 보이지 않아요" else "신고로 가려져 다른 분께는 보이지 않아요",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            } else Text(it, style = MaterialTheme.typography.bodyLarge)
        }
            ?: if (mine) Text("한 줄 소개를 적어 보세요 (프로필 고치기)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else Unit
        // 숫자 줄: 테마 · 기능별 탭 · 팔로워 · 팔로잉 · 넣은 분
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Count(p.themes.size, "테마")
            Count(p.tabRecipes.size, "탭별 작품")
            Count(p.followers, "팔로워", if (mine) ({ onPeople("followers") }) else null)
            Count(p.followingCount, "팔로잉", if (mine) ({ onPeople("following") }) else null)
            Count(p.users, "넣은 분")
        }
        if (!p.mine) {
            if (vm.canFollow) {
                if (p.following) OutlinedButton(onClick = onFollowMenu, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (p.quiet) "팔로잉 ✓ · 알림 꺼짐" else "팔로잉 ✓")
                } else Button(onClick = { vm.follow(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("팔로우") }
            } else Text("팔로우는 정식 출시일부터 돼요", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Count(n: Int, label: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.clip(MaterialTheme.shapes.small).then(if (onClick != null) Modifier.clickable(onClickLabel = "$label 보기", onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp, vertical = 4.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(compact(n), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 1234 → 1.2천, 12345 → 1.2만. */
internal fun compact(n: Int): String = when {
    n >= 10_000 -> "%.1f만".format(n / 10_000.0).replace(".0만", "만")
    n >= 1_000 -> "%.1f천".format(n / 1_000.0).replace(".0천", "천")
    else -> n.toString()
}

/** Works in a 2-column grid: the theme drawn large, 📌 on 대표 작품. */
@Composable
private fun Grid(items: List<RecipeItem>, pins: List<String>, empty: String, onOpen: (String) -> Unit) {
    if (items.isEmpty()) { Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    items.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { r -> WorkTile(r, r.code in pins, Modifier.weight(1f)) { onOpen(r.code) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun WorkTile(item: RecipeItem, pinned: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClickLabel = "작품 보기", onClick = onClick)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // [MKV-01] 1.37: the work as it really looks, as in 장터.
                MarketShot(item, maxWidth, LocalViewerStyle.current, onClick)
                if (pinned) Text("📌", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).semantics { contentDescription = "대표 작품" })
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val viewer = LocalViewerStyle.current
            Text(buildString {
                if (item.scope == "tab") item.tabs.firstOrNull()?.let { append("🧩 ").append(marketTabLabel(it, viewer)).append(" · ") }
                append("♥ ${item.likes} · 넣은 분 ${item.users}")
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** [PRF-03] 최근 알림: big while something is new, one line once seen (1.36 규칙: 답한 뒤 크게 남는 카드는 문제). */
@Composable
private fun InboxCard(b: ProfileInbox, unseen: Set<String>, onOpen: (String) -> Unit, onPeople: () -> Unit, onSeen: () -> Unit) {
    val lines = buildList<Pair<String, (() -> Unit)?>> {
        if (b.newFollowers > 0) add("👤 새 팔로워 ${b.newFollowers}명" + (b.followerFaces.firstOrNull()?.nick?.let { " ($it 님 등)" } ?: "") to onPeople)
        b.milestones.filter { it.key in unseen }.forEach { m ->
            add((if (m.kind == "users") "🎉 ‘${m.title}’ 넣은 분 ${m.n}명 넘음" else "❤️ ‘${m.title}’ 좋아요 ${m.n}개 넘음") to { onOpen(m.code) })
        }
        b.questions.forEach { (code, title, body) -> add("❓ ‘$title’에 질문: $body" to { onOpen(code) }) }
        b.remixes.forEach { (code, title, parent) -> add("🔁 내 ‘$parent’${com.bamnat.app.ui.Fmt.obj("‘$parent’")} 고친 ‘$title’" to { onOpen(code) }) }
        b.badges.filter { "b|${it.first}" in unseen }.forEach { add("🏅 새 배지 ‘${it.second}’" to null) }
    }
    var open by remember { mutableStateOf(false) }
    val fresh = unseen.isNotEmpty()
    com.bamnat.app.ui.company.SoftCard(highlighted = fresh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 알림" + if (fresh) " · 새 소식 ${unseen.size}" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                if (lines.isNotEmpty()) TextButton(onClick = { if (fresh) { onSeen(); open = false } else open = !open }) {
                    Text(if (fresh) "다 봤어요" else if (open) "접기" else "펼치기")
                }
            }
            if (lines.isEmpty()) Text("지난 7일 동안 소식이 없어요. 새 팔로워·질문·고친 판이 오면 여기 떠요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (fresh || open) lines.take(8).forEach { (text, go) ->
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall).then(if (go != null) Modifier.clickable(role = Role.Button, onClick = go) else Modifier)
                        .heightIn(min = 40.dp).padding(vertical = 6.dp))
            } else Text("지난 소식 · " + lines.first().first, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [PRF-07] A 모음 on the profile: the works' small looks in a row, its name and how many. */
@Composable
private fun SetCard(set: MakerSet, works: List<RecipeItem>, mine: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val items = set.codes.mapNotNull { code -> works.firstOrNull { it.code == code } }
    val viewer = LocalViewerStyle.current
    Card(Modifier.fillMaxWidth().clickable(onClickLabel = "모음 보기", onClick = onOpen)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.take(4).forEach { r -> MarketShot(r, 64.dp, viewer, onOpen) }
            }
            Text(set.title, style = MaterialTheme.typography.titleMedium)
            if (set.summary.isNotEmpty()) Text(set.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("작품 ${items.size}개 · " + items.joinToString(" + ") { if (it.scope == "tab") marketTabLabel(it.tabs.firstOrNull() ?: "", viewer) else "테마" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (mine) {
                if (set.missing > 0) Text("내린 작품 ${set.missing}개는 빠져 보여요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                var asking by remember { mutableStateOf(false) }
                Row { TextButton(onClick = onEdit) { Text("고치기") }; TextButton(onClick = { asking = true }) { Text("지우기") } }
                if (asking) AlertDialog(
                    onDismissRequest = { asking = false },
                    title = { Text("‘${set.title}’ 모음을 지울까요?") },
                    text = { Text("모음만 지워지고 안에 있던 작품은 그대로 남아요.") },
                    confirmButton = { TextButton(onClick = { asking = false; onDelete() }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { asking = false }) { Text("닫기") } },
                )
            }
        }
    }
}

/** [PRF-07] 모음 만들기·고치기: a name, a line, and 2~6 of my works with at most one 테마. */
@Composable
private fun SetEditDialog(set: MakerSet?, works: List<RecipeItem>, style: WorkStyle?, onSave: (String, String, List<String>) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(set?.title ?: "") }
    var summary by remember { mutableStateOf(set?.summary ?: "") }
    var picked by remember { mutableStateOf(set?.codes?.filter { c -> works.any { it.code == c } }.orEmpty()) }
    val themes = picked.count { c -> works.firstOrNull { it.code == c }?.scope != "tab" }
    val problem = when {
        RecipeText.graphemes(title.trim()) !in 2..30 -> "이름을 2~30자로 적어 주세요"
        RecipeText.graphemes(summary.trim()) > 80 -> "한 줄 설명은 80자까지 쓸 수 있어요"
        picked.size !in 2..6 -> "작품을 2~6개 골라 주세요"
        themes > 1 -> "테마는 하나만 넣을 수 있어요"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (set == null) "모음 만들기" else "모음 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(40) }, label = { Text("이름 (예: ${setExample(style)})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(summary, { summary = it.take(100) }, label = { Text("한 줄 설명 (안 써도 돼요)") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${RecipeText.graphemes(summary.trim())}/80") }, isError = RecipeText.graphemes(summary.trim()) > 80)
                Text("테마 하나 + 탭별 작품 여러 개를 골라요 (${picked.size}/6)", style = MaterialTheme.typography.bodySmall)
                works.forEach { r ->
                    val on = r.code in picked
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { picked = if (on) picked - r.code else picked + r.code }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { picked = if (on) picked - r.code else picked + r.code })
                        Column(Modifier.weight(1f)) {
                            Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (r.scope == "tab") "탭별 작품 · " + marketTabLabel(r.tabs.firstOrNull() ?: "", style) else "테마",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, summary, picked) }, enabled = problem == null) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** [PRF-01][PRF-12] 프로필 고치기: 얼굴, 색, 한 줄 소개, 보여 줄 유형, 대표 작품, 표지 그림(프로). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditDialog(p: MakerPage, vm: MakerViewModel, pinMax: Int, onFace: () -> Unit, onColor: () -> Unit, onPro: () -> Unit, onDismiss: () -> Unit) {
    var nick by remember { mutableStateOf(p.nick ?: "") }
    var bio by remember { mutableStateOf(p.bio ?: "") }
    var style by remember { mutableStateOf(p.styleTag) }
    var pins by remember { mutableStateOf(p.pins) }
    // 빌드 221dd083 확인: a tap outside closed it and took what was typed. Asked first when something changed.
    val dirty = nick.trim() != (p.nick ?: "") || bio.trim() != (p.bio ?: "") || style != p.styleTag || pins != p.pins
    var leaving by remember { mutableStateOf(false) }
    val close = { if (dirty) leaving = true else onDismiss() }
    val nickProblem = nick.trim().let { t -> if (t.isNotEmpty() && t.length !in 2..12) "이름은 2~12자로 정해 주세요" else null }
    var saving by remember { mutableStateOf(false) }
    // What the server said about the name just tried (이미 쓰는 이름이에요), until the name is changed.
    var takenFor by remember { mutableStateOf<String?>(null) }
    var nickTakenRaw by remember { mutableStateOf<String?>(null) }
    val nickTaken = nickTakenRaw?.takeIf { takenFor == nick.trim() }
    val count = RecipeText.graphemes(bio.trim())
    val problem = when {
        count > 40 -> "40자까지 쓸 수 있어요"
        bio.isNotBlank() -> com.bamnat.core.recipe.RecipeText.problem(bio)?.let(com.bamnat.core.recipe.RecipeText::problemText)
        else -> null
    }
    val pro = vm.c.featureGate.allows(Feature.CREATOR_PRO)
    val context = LocalContext.current
    val photo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.coverPhoto(com.bamnat.app.recipe.SafeBitmaps.decodeUri(context, uri)) // 1.36 [SEC-04]
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("고친 내용을 버릴까요?") },
        text = { Text("저장하지 않고 닫으면 적은 이름·소개와 고른 것이 사라져요.") },
        confirmButton = { TextButton(onClick = { leaving = false; onDismiss() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("계속 고치기") } },
    )
    AlertDialog(
        onDismissRequest = close,
        title = { Text("프로필 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (dirty) leaving = true else onFace() }) { Text("얼굴 이모지") }
                    OutlinedButton(onClick = { if (dirty) leaving = true else onColor() }) { Text("바탕색") }
                }
                // [PRF-01] 1.36 PC 사용성 P4: 다른 분에게 이렇게 보여요, as it is typed.
                Text("다른 분에게 이렇게 보여요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bamnat.app.ui.company.SoftCard {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MakerFace(p.face, p.color, nick.trim().ifEmpty { null }, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(nick.trim().ifEmpty { "이름 없는 분" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                style?.let { Spacer(Modifier.width(6.dp)); Text(RecipeDescribe.short(it), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary) }
                            }
                            Text(bio.trim().ifEmpty { "소개 없음" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            if (pins.isNotEmpty()) Text("📌 " + pins.mapNotNull { c -> p.recipes.firstOrNull { it.code == c }?.title }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(nick, { nick = it.replace("\n", "").take(12) }, label = { Text("마을 이름 (광장·장터에 같이 보여요)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = nickProblem != null || nickTaken != null,
                    supportingText = { Text(nickProblem ?: nickTaken
                        ?: "2~12자 · 비우면 ‘이름 없는 분’으로 보여요") })
                OutlinedTextField(bio, { bio = it.replace("\n", " ").take(60) }, label = { Text("한 줄 소개") }, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(problem ?: "$count/40 · 전화번호·주소·링크는 쓸 수 없어요") }, isError = problem != null)
                Text("보여 줄 근무 유형", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(style == null, { style = null }, label = { Text("안 보이기") })
                    WorkStyle.entries.forEach { st -> FilterChip(style == st, { style = st }, label = { Text(RecipeDescribe.short(st)) }) }
                }
                Text("대표 작품 (${pins.size}/$pinMax, 맨 앞에 📌)", style = MaterialTheme.typography.titleSmall)
                if (p.recipes.isEmpty()) Text("장터에 공개로 올린 작품이 생기면 고를 수 있어요", style = MaterialTheme.typography.bodySmall)
                p.recipes.forEach { r ->
                    val on = r.code in pins
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = on || pins.size < pinMax) { pins = if (on) pins - r.code else pins + r.code }.heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { pins = if (on) pins - r.code else pins + r.code }, enabled = on || pins.size < pinMax)
                        Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!pro && pins.size >= pinMax) TextButton(onClick = onPro) { Text("프로 꾸미기로 대표 작품 6개까지") }
                HorizontalDivider()
                Text("표지 그림" + if (pro) "" else " (프로 꾸미기)", style = MaterialTheme.typography.titleSmall)
                if (pro) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { photo.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) } }) {
                        Text(if (p.cover != null || p.coverPending != null) "바꾸기" else "사진 고르기")
                    }
                    if (p.cover != null || p.coverPending != null) TextButton(onClick = { vm.coverPhoto(null) }) { Text("빼기") }
                } else TextButton(onClick = onPro) { Text("프로 꾸미기 알아보기") }
                if (p.coverPending != null) Text("올린 그림은 운영자가 본 뒤 다른 분께 보여요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!vm.canWrite) Text("소개는 정식 출시일부터 저장돼요", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = problem == null && nickProblem == null && nickTaken == null && vm.canWrite && !saving, onClick = {
                saving = true
                vm.save(ProfileEdit(bio = bio.trim(), pins = pins, setStyle = true, styleTag = style), nick.takeIf { it.trim() != (p.nick ?: "") }) { err ->
                    saving = false
                    if (err == null) onDismiss() else { nickTakenRaw = err; takenFor = nick.trim() }
                }
            }) { Text(if (saving) "저장하는 중" else "저장") }
        },
        dismissButton = { TextButton(onClick = close) { Text("닫기") } },
    )
}

// ---- [PRF-02][PRF-08] 팔로잉·팔로워·차단, and 새 작품 (팔로잉 피드) ----

class PeopleViewModel(private val c: AppContainer) : ViewModel() {
    private val device get() = c.companyStore.device
    data class State(val following: Following? = null, val followers: Followers? = null, val feed: List<RecipeItem>? = null,
        val next: Int? = null, val error: String? = null, val message: String? = null, val me: String? = null,
        /** The maker just unblocked, for 풀었어요 · 되돌리기. */
        val unblocked: String? = null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
        // [PRF-05] 1.36 PC 4차: 사람들 › 프로필 › 차단 › 뒤로 — the list here was from before; it follows every follow and block.
        viewModelScope.launch { c.recipes.people.drop(1).collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            runCatching { c.recipeApi.following(device) }
                .onSuccess { f -> c.recipes.setBlocked(f.blocked.map { it.maker }.toSet()); _state.value = _state.value.copy(following = f, error = null) }
                .onFailure { _state.value = _state.value.copy(error = RecipeClient.problem(it)) }
            runCatching { c.recipeApi.followers(device) }.onSuccess { _state.value = _state.value.copy(followers = it) }
            runCatching { c.recipeApi.makerMe(device) }.onSuccess { _state.value = _state.value.copy(me = it) }
            loadFeed(false)
        }
    }

    fun loadFeed(more: Boolean) {
        viewModelScope.launch {
            runCatching { c.recipeApi.feed(device, if (more) _state.value.next ?: 0 else 0) }.onSuccess { pg ->
                _state.value = _state.value.copy(feed = (if (more) _state.value.feed.orEmpty() else emptyList()) + pg.items, next = pg.next)
            }
        }
    }

    fun quiet(id: String, q: Boolean) = act { c.recipeApi.followQuiet(device, id, q); c.recipes.peopleChanged() }
    fun unfollow(id: String) = act { c.recipeApi.follow(device, id, false); c.recipes.peopleChanged() }
    fun unblock(id: String, undo: Boolean = true) = act {
        c.recipeApi.block(device, id, false); c.recipes.block(id, false); c.recipes.peopleChanged()
        if (undo) undo(id) else _state.value = _state.value.copy(message = "목록에서 뺐어요")
    }
    /** [PRF-05] 1.36 PC 사용성 P5: 풀었어요 · 되돌리기. */
    fun reblock(id: String) = act { c.recipeApi.block(device, id, true); c.recipes.block(id, true); c.recipes.peopleChanged() }
    private fun undo(id: String) { _state.value = _state.value.copy(unblocked = id) }
    fun undoShown() { _state.value = _state.value.copy(unblocked = null) }

    private fun act(f: suspend () -> Unit) {
        viewModelScope.launch { runCatching { f() }.onFailure { _state.value = _state.value.copy(message = profileProblem(it)) } }
    }

    fun shown() { _state.value = _state.value.copy(message = null) }

    /** [VIL-02] 광장에서 가린 분 (this phone's own list, [com.bamnat.core.plaza.HideList]). */
    val hidden: StateFlow<com.bamnat.core.plaza.HideList> = c.settingsRepository.settings
        .map { com.bamnat.core.plaza.HideList.decode(it.plazaHidden) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, com.bamnat.core.plaza.HideList.decode(c.settingsRepository.current.plazaHidden))

    fun unhide(w: com.bamnat.core.plaza.HiddenWriter) =
        c.settingsRepository.update { s -> s.copy(plazaHidden = com.bamnat.core.plaza.HideList.decode(s.plazaHidden).show(w.mark).encode()) }

    fun rehide(w: com.bamnat.core.plaza.HiddenWriter) =
        c.settingsRepository.update { s -> s.copy(plazaHidden = com.bamnat.core.plaza.HideList.decode(s.plazaHidden).hide(w.mark, w.label, w.atMillis).encode()) }
}

/** [VIL-02] 1.36 마을 V2: the 광장 writers this phone hid, under 차단, each with 다시 보기. */
@Composable
private fun HiddenWriters(vm: PeopleViewModel, onShown: (com.bamnat.core.plaza.HiddenWriter) -> Unit) {
    val list by vm.hidden.collectAsStateWithLifecycle()
    Spacer(Modifier.height(8.dp))
    Text("광장에서 가린 분 ${list.writers.size}", style = MaterialTheme.typography.titleSmall)
    Text("토론 글의 ⋮ › 이 사람 글 안 보기로 가린 분이에요. 이 휴대폰에만 남고 상대에게 알리지 않아요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (list.writers.isEmpty()) Text("가린 분이 없어요", style = MaterialTheme.typography.bodyMedium)
    list.writers.forEach { w ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(w.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { vm.unhide(w); onShown(w) }) { Text("다시 보기") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(vm: PeopleViewModel, start: String, onBack: () -> Unit, onMaker: (String) -> Unit, onOpen: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val parts = listOf("new" to "새 작품", "following" to "팔로잉", "followers" to "팔로워", "blocked" to "차단")
    var part by rememberSaveable { mutableStateOf(parts.indexOfFirst { it.first == start }.coerceAtLeast(0)) }
    val snack = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hiddenCount = vm.hidden.collectAsStateWithLifecycle().value.writers.size
    // PC 확인 20ab0254: cleared after it was shown; cleared first, the key changed and took the snackbar down at once.
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.shown() } }
    LaunchedEffect(s.unblocked) {
        val id = s.unblocked ?: return@LaunchedEffect
        vm.undoShown()
        if (snack.showSnackbar("차단을 풀었어요", actionLabel = "되돌리기", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.reblock(id)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text(parts[part].second) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = part) {
                parts.forEachIndexed { i, (_, label) ->
                    val n = when (i) { 1 -> s.following?.items?.size; 2 -> s.followers?.total; 3 -> s.following?.blocked?.size?.plus(hiddenCount); else -> null }
                    Tab(part == i, { part = i }, text = { Text(label + (n?.let { " $it" } ?: ""), maxLines = 1) })
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.error?.let { Text(it); TextButton(onClick = vm::load) { Text("다시 해 보기") } }
                when (part) {
                    0 -> {
                        val feed = s.feed
                        if (feed == null) Text("불러오는 중이에요")
                        else if (feed.isEmpty()) Text("팔로우한 분의 새 작품이 여기 시간순으로 모여요. 장터에서 마음에 드는 작품의 만든 분을 눌러 팔로우해 보세요",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        feed.orEmpty().forEach { r -> RecipeRow(r) { onOpen(r.code) } }
                        if (s.next != null) TextButton(onClick = { vm.loadFeed(true) }) { Text("더 보기") }
                    }
                    1 -> {
                        val list = s.following?.items.orEmpty()
                        if (s.following != null && list.isEmpty()) Text("아직 팔로우한 분이 없어요", style = MaterialTheme.typography.bodyMedium)
                        list.forEach { m ->
                            PersonRow(m, onClick = { onMaker(m.maker) }) {
                                TextButton(onClick = { vm.quiet(m.maker, !m.quiet) }) { Text(if (m.quiet) "알림 켜기" else "알림 끄기") }
                                TextButton(onClick = { vm.unfollow(m.maker) }) { Text("그만") }
                            }
                        }
                    }
                    2 -> {
                        val f = s.followers
                        if (f != null) {
                            Text("나를 팔로우하는 분 ${f.total}명. 프로필이 있는 분만 이름이 보이고, 이 명단은 나만 봐요", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            f.items.forEach { m -> PersonRow(m, onClick = { onMaker(m.maker) }) {} }
                            if (f.unnamed > 0) Text("이름 없는 분 ${f.unnamed}명", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        val list = s.following?.blocked.orEmpty()
                        Text("장터·프로필에서 차단한 분 ${list.size}", style = MaterialTheme.typography.titleSmall)
                        Text("차단한 분은 서로 팔로우할 수 없고, 그분의 작품과 이야기가 내 장터에서 보이지 않아요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.following != null && list.isEmpty()) Text("차단한 분이 없어요", style = MaterialTheme.typography.bodyMedium)
                        // [PRF-05] 1.36 PC 사용성 P9: a maker whose profile went is taken off the list, with no 되돌리기.
                        list.forEach { m ->
                            PersonRow(if (m.gone) m.copy(nick = "없어진 프로필") else m, onClick = {}) {
                                TextButton(onClick = { vm.unblock(m.maker, undo = !m.gone) }) { Text(if (m.gone) "목록에서 빼기" else "차단 풀기") }
                            }
                        }
                        // [VIL-02] 1.36 마을 V2 차단 하나로: 광장에서 가린 분 on the same list, with 다시 보기 · 되돌리기.
                        HiddenWriters(vm, onShown = { w -> scope.launch {
                            if (snack.showSnackbar("${w.label}님 글을 다시 보여요", actionLabel = "되돌리기", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                                vm.rehide(w)
                        } })
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(m: MakerFaceItem, onClick: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "프로필 보기", onClick = onClick).heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MakerRing(m.face, m.color, m.nick, m.fresh, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.nick ?: "이름 없는 분", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.fresh) Text("새 작품", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            else if (m.quiet) Text("알림 꺼짐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (m.gone) Text("프로필이 없어졌어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

// ---- [PRF-02][PRF-10] 마을 맨 위 '팔로잉' 줄 (팔로우한 분이 없으면 이달의 제작자) ----

class FollowingStripViewModel(private val c: AppContainer, private val style: WorkStyle?) : ViewModel() {
    data class State(val me: MakerFaceItem? = null, val following: List<MakerFaceItem> = emptyList(), val month: List<MakerFaceItem> = emptyList(), val loaded: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedAt = 0L

    init {
        // A 팔로우·차단 on a profile shows on the row as soon as 마을 is back (빌드 221dd083 확인: it waited for another tab).
        viewModelScope.launch { c.recipes.people.collect { if (it > 0) load(force = true) } }
    }

    /** Again when 마을 opens or after a minute, not on every switch between 광장 and 장터. */
    fun load(force: Boolean = false) {
        if (!c.recipeApi.enabled) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && _state.value.loaded && now - loadedAt < 60_000) return
        loadedAt = now
        viewModelScope.launch {
            val device = c.companyStore.device
            val f = runCatching { c.recipeApi.following(device) }.getOrNull()
            f?.let { c.recipes.setBlocked(it.blocked.map { b -> b.maker }.toSet()) }
            val blocked = f?.blocked?.map { it.maker }?.toSet() ?: c.recipes.blocked.value
            val month = if (f?.items.isNullOrEmpty()) runCatching { c.recipeApi.monthMakers(style) }.getOrDefault(emptyList()).filterNot { it.maker in blocked }
                else emptyList()
            // 내 프로필 only once there is one (a public recipe, a face or a 소개): asking makes none on the server.
            val me = runCatching { c.recipeApi.makerMe(device) }.getOrNull()?.let { id ->
                runCatching { c.recipeApi.maker(device, id) }.getOrNull()?.takeIf { p ->
                    p.recipes.isNotEmpty() || p.face != null || p.bio != null || p.sets.isNotEmpty()
                }?.let { p ->
                    c.recipes.hasProfile(p.recipes.isNotEmpty())
                    MakerFaceItem(p.maker, p.nick, p.face, p.color, fresh = c.recipes.seenInbox().let { seen ->
                        runCatching { c.recipeApi.inbox(device, c.recipes.inboxSince()) }.getOrNull()?.let { b -> MakerViewModel.inboxKeys(b).any { it !in seen } } ?: false
                    })
                }
            }
            _state.value = State(me, f?.items.orEmpty(), month.filterNot { it.maker == me?.maker }, loaded = true)
        }
    }
}

/**
 * [PRF-02] The row under 마을's tabs: 내 프로필 first (a ring when 최근 알림 has news), then the makers followed (a ring on a new work),
 * and 모두 보기; with no one followed, 이달의 제작자 to start from.
 */
@Composable
fun FollowingStrip(vm: FollowingStripViewModel, onMaker: (String) -> Unit, onAll: () -> Unit, onFind: (() -> Unit)? = null) {
    val s by vm.state.collectAsStateWithLifecycle()
    // [TOWN-11] Read again when 장터 is tapped again or opened from a 소식, so the ring on a new work shows at once.
    val reselect = com.bamnat.app.ui.hub.LocalHubReselect.current
    val reselectAt = remember { reselect }
    LaunchedEffect(reselect) { vm.load(force = reselect > reselectAt) }
    if (!s.loaded) return
    // 1.39 [MKD-60] (사용자 2026-11-08 장터 윗부분): with no one followed and no 이달의 제작자 the row was a lone '🔍 찾아보기' circle (the
    // same as 마을's 🔍 at the top) and 내 프로필 (the '나' button); it shows only when there are makers in it.
    if (s.following.isEmpty() && s.month.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (s.following.isEmpty() && s.month.isNotEmpty()) Text("이달의 제작자", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.me?.let { m -> StripFace(m.copy(nick = m.nick), "내 프로필", m.fresh) { onMaker(m.maker) } }
            (s.following.ifEmpty { s.month }).forEach { m -> StripFace(m, m.nick ?: "이름 없는 분", m.fresh) { onMaker(m.maker) } }
            if (s.following.isNotEmpty()) Column(Modifier.width(72.dp).clip(MaterialTheme.shapes.medium).clickable(onClickLabel = if (s.following.isEmpty()) "마을에서 찾기" else "팔로잉 모두 보기",
                // PC 확인 5c21f3bb: '🔍 찾아보기' opened the 팔로잉 새 작품 (empty with no one followed), not a search.
                onClick = if (s.following.isEmpty()) onFind ?: onAll else onAll),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                    contentAlignment = Alignment.Center) { Text(if (s.following.isEmpty()) "🔍" else "＋", style = MaterialTheme.typography.titleLarge) }
                Text(if (s.following.isEmpty()) "찾아보기" else "모두 보기", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StripFace(m: MakerFaceItem, label: String, fresh: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "$label 프로필", onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        MakerRing(m.face, m.color, m.nick, fresh, 56.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---- [PRF-07] 모음 열기: 한 번에 입어 보기, 차례로 넣기 ----

class SetViewModel(private val c: AppContainer, val id: String, val style: WorkStyle?) : ViewModel() {
    data class State(val doc: SetDoc? = null, val error: String? = null,
        /** The 테마 with every tab of the 모음 laid on it, to try on at once. */
        val worn: com.bamnat.core.recipe.Recipe? = null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    /** Codes of the 모음 already put on (넣은 것 ✓). */
    val applied = c.recipes.applied

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching { c.recipeApi.setOpen(c.companyStore.device, id) }.onSuccess { d ->
                _state.value = _state.value.copy(doc = d, error = null)
                // 한 번에 입어 보기: my look now, the 테마 over it, then each tab over that (LookParts.layer, as 얹기 does).
                val now = com.bamnat.core.recipe.Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
                var worn = now
                for (item in d.items) {
                    val r = runCatching { c.recipeApi.show(item.code, c.companyStore.device).recipe() }.getOrNull() ?: continue
                    worn = com.bamnat.core.recipe.LookParts.layer(worn, r.copy(pieces = r.pieces.filter { it.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }))
                }
                _state.value = _state.value.copy(worn = worn.copy(title = d.title))
            }.onFailure { _state.value = _state.value.copy(error = if ((it as? com.bamnat.app.company.CompanyException)?.code == "not_found") "볼 수 없는 모음이에요" else RecipeClient.problem(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetScreen(vm: SetViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onMaker: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val applied by vm.applied.collectAsStateWithLifecycle(emptyList())
    val on = applied.filter { it.undoneAt == null && it.replacedAt == null }.map { it.code }.toSet()
    var tab by remember { mutableStateOf(com.bamnat.core.layout.TabOrder.CALENDAR) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(s.doc?.title ?: "모음", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val d = s.doc
                if (d == null) { Text(s.error ?: "불러오는 중이에요"); if (s.error != null) TextButton(onClick = vm::load) { Text("다시 해 보기") }; return@Column }
                if (d.summary.isNotEmpty()) Text(d.summary, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { onMaker(d.maker) }) { Text("만든 분 프로필") }
                Text("한 번에 입어 본 모습", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("지금 내 화면에 테마와 탭을 모두 얹으면 이렇게 보여요. 아직 바뀐 건 없어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.worn?.let { r ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScaledPhone(r, vm.style, tab, { tab = it }, 0.62f) }
                } ?: Text("모습을 그리는 중이에요", style = MaterialTheme.typography.bodySmall)
                Text("차례로 넣기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("테마를 먼저 입고, 탭은 그 위에 얹어요. 하나씩 열어 입어 보고 넣으면 여기 ✓가 붙어요. 넣은 것은 7일 안에 되돌릴 수 있어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                d.items.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r.code in on) "✓" else "${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp),
                            color = if (r.code in on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        RecipeRow(r, Modifier.weight(1f)) { onOpen(r.code) }
                    }
                }
                val next = d.items.firstOrNull { it.code !in on }
                if (next != null) Button(onClick = { onOpen(next.code) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (d.items.none { it.code in on }) "첫 번째부터 넣기: ${next.title}" else "다음 것 넣기: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Text("모음을 모두 넣었어요 🎉 마을 › 테마에서 입은 테마와 얹은 탭을 볼 수 있어요", style = MaterialTheme.typography.bodyMedium)
                if (d.mine && d.missing > 0) Text("내린 작품 ${d.missing}개는 빠져 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---- [PRF-09] 작품 통계 ----

class StatsViewModel(private val c: AppContainer) : ViewModel() {
    data class State(val stats: MakerStats? = null, val error: String? = null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val pro: Boolean get() = c.featureGate.allows(Feature.CREATOR_PRO)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { c.recipeApi.stats(c.companyStore.device) }
                .fold({ State(it) }, { State(error = RecipeClient.problem(it)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onPro: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("작품 통계") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val st = s.stats
            if (st == null) { Text(s.error ?: "불러오는 중이에요"); if (s.error != null) TextButton(onClick = vm::load) { Text("다시 해 보기") }; return@Column }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Count(st.week, "이번 주 넣은 수"); Count(st.users, "넣은 분 모두"); Count(st.likes, "좋아요 모두")
            }
            Text("누가 썼는지는 서버도 몰라요. 작품과 날마다의 수만 세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (st.items.isEmpty()) Text("장터에 공개로 올린 작품이 생기면 여기 보여요")
            st.items.forEach { i ->
                Card(Modifier.fillMaxWidth().clickable(onClickLabel = "작품 보기") { onOpen(i.code) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(i.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("넣은 분 ${i.users} · 이번 주 ${i.week} · 좋아요 ${i.likes} · 안좋아요 ${i.dislikes}" + (i.kept?.let { " · 7일 뒤에도 $it%" } ?: ""),
                            style = MaterialTheme.typography.bodySmall)
                        if (vm.pro) Bars(i.days) else if (i === st.items.first()) TextButton(onClick = onPro) { Text("프로 꾸미기로 28일 날별 그래프 보기") }
                    }
                }
            }
        }
    }
}

/** 28 days of 넣은 수 as small bars, the last day on the right. */
@Composable
private fun Bars(days: List<Int>) {
    val max = (days.maxOrNull() ?: 0).coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(48.dp).semantics(mergeDescendants = true) { contentDescription = "28일 넣은 수, 가장 많은 날 ${days.maxOrNull() ?: 0}" },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { n ->
            Box(Modifier.weight(1f).height((4 + 44f * n / max).dp).clip(RoundedCornerShape(2.dp)).background(if (n == 0) color.copy(alpha = 0.15f) else color))
        }
    }
}
