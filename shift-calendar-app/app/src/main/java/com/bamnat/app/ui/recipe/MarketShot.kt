package com.bamnat.app.ui.recipe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [MKV-01] 1.37 장터 카드 실제 모습 (사용자 2026-11-05 "장터에서 테마나 기능들 보여지는 모습들이 실제로 해당 테마나 기능들의 모습을
 * 보여지게 … 지금은 이상한 모습이라 어떤 강점이 있는지 잘 몰라"): a 장터 card shows the work itself, drawn by the same example app the
 * studio and the 입어 보기 use ([MockPhone]): a 테마 on its calendar, a 기능별 탭 on that tab's page (급여 page, 가계부, 메모, 위젯 on a home
 * screen, 알람 화면), a 부품 on the screen it sits on, and 규칙·단추 as the notifications and buttons it makes.
 *
 * The list only carries a small swatch ([RecipeItem.look]); the work's written form is read once ([RecipeApi.show], no install sent), the
 * phone drawn once and kept as a picture, so scrolling back is a picture, not a phone drawn again. While it comes (or offline) the old
 * small picture stands in the same place, so nothing below jumps.
 */
internal object MarketShots {
    /** The example phone's own size ([MockPhone]): every shot keeps this shape. */
    const val PHONE_W = 340f
    const val PHONE_H = 560f

    private val recipes = android.util.LruCache<String, Recipe>(80)
    private val shots = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failed = java.util.Collections.synchronizedSet(HashSet<String>())
    /** A few at a time, so a page of cards does not hold the network for the one that was tapped. */
    private val gate = Semaphore(3)

    fun key(item: RecipeItem) = "${item.code}@${item.version}"

    /**
     * PC 확인 e50bb622 MED-2 (긴 테마 목록을 천천히 내리면 그림을 처음 그릴 때 끊김, Janky 19%): one example phone is drawn live at a time;
     * the others keep their small picture until it is their turn, and a card only flown past is never drawn.
     * PC 확인 edb46c62 MED-1 (멈춘 뒤 보이는 카드 넷이 작은 그림으로 남음): a count could be taken and never given back (a card composed
     * ahead of the screen and never drawn held it); a fair lock is given back when its card leaves, and after [HOLD] at most.
     */
    val drawing = kotlinx.coroutines.sync.Mutex()
    const val HOLD = 1500L
    /** How long a card must have stood still on the screen (the list not moving) before it is drawn. */
    const val STILL = 200L
    /** 1.39 [MKP-04] After a card's first frame, a frame or two more before the next card starts drawing. */
    const val SETTLE = 48L

    fun shot(key: String): ImageBitmap? = shots.get(key)
    fun keep(key: String, b: ImageBitmap) { shots.put(key, b) }

    suspend fun recipe(c: AppContainer, item: RecipeItem): Recipe? {
        val k = key(item)
        recipes.get(k)?.let { return it }
        if (k in failed) return null
        // 1.39 [MKP-01][MKP-02] The written form that came with the list, or the one kept on the phone, before asking for it alone.
        (raws.get(k) ?: MarketDisk.body(c.appContext, k)?.also { raws.put(k, it) })?.let { (hash, body) ->
            read(hash, body)?.let { r -> recipes.put(k, r); return r }
        }
        val got = gate.withPermit {
            recipes.get(k) ?: withContext(Dispatchers.IO) {
                runCatching { c.recipeApi.show(item.code, null, null) }.getOrNull()?.let { doc ->
                    keepDoc(c, doc)
                    runCatching { doc.recipe() }.getOrNull() ?: known(doc.body)
                }
            }
        }
        if (got == null) failed.add(k) else recipes.put(k, got)
        return got
    }

    /** The work in [body], checked by its [hash] as [com.bamnat.app.recipe.RecipeDoc.recipe] does (a newer app's: what this app knows of it). */
    private suspend fun read(hash: String, body: String): Recipe? = withContext(Dispatchers.Default) {
        runCatching {
            val r = com.bamnat.core.recipe.RecipeCodec.read(body)
            r.takeIf { com.bamnat.core.recipe.RecipeCodec.hash(it) == hash }
        }.getOrNull() ?: known(body)
    }

    // ---- 1.39 [MKP-01][MKP-03] 장터 로딩: what a list brought, for the cards and for opening a work at once ----

    /** Each work's written form (hash, body) by [key]: from the list (`body=1`), a work opened, or the phone's own copy. */
    private val raws = android.util.LruCache<String, Pair<String, String>>(200)
    /** The last list card seen of each code, so a work opens with its name and counts while the rest comes. */
    private val items = android.util.LruCache<String, RecipeItem>(200)

    /** A page of the list came: its cards and written forms are kept here, and the written forms on the phone (in the background). */
    fun feed(c: AppContainer, page: com.bamnat.app.recipe.RecipePage) {
        page.items.forEach { items.put(it.code, it) }
        if (page.bodies.isEmpty()) return
        page.bodies.forEach { (k, v) -> raws.put(k, v) }
        c.appScope.launch { page.bodies.forEach { (k, v) -> MarketDisk.keepBody(c.appContext, k, v.first, v.second) } }
    }

    /** A work read whole ([com.bamnat.app.recipe.RecipeApi.show]): its written form kept like a list's. */
    fun keepDoc(c: AppContainer, doc: com.bamnat.app.recipe.RecipeDoc) {
        val k = key(doc.item)
        items.put(doc.item.code, doc.item)
        if (raws.get(k) == null) {
            raws.put(k, doc.hash to doc.body)
            c.appScope.launch { MarketDisk.keepBody(c.appContext, k, doc.hash, doc.body) }
        }
    }

    /**
     * 1.39 [MKP-03] 작품 바로 열기: what a tapped card already has — its card and written form — as a work to show at once, while the
     * whole one (this phone's 좋아요, whether it is mine) comes; null when this phone has not seen it.
     */
    fun seen(code: String): com.bamnat.app.recipe.RecipeDoc? {
        val item = items.get(code) ?: return null
        val (hash, body) = raws.get(key(item)) ?: return null
        return com.bamnat.app.recipe.RecipeDoc(item, hash, body, scope = "public", mine = false)
    }

    /** The written form of card [key] (for [MarketDisk.complete]). */
    fun body(key: String): String? = raws.get(key)?.second

    /** Whether this card's work could not be had (offline, gone): it keeps its small picture for good. */
    fun gaveUp(item: RecipeItem): Boolean = key(item) in failed

    /**
     * PC 확인 7b219467 MED-1 (가계부 틀이 든 작품은 그림이 끝내 안 나옴): a work made by a newer app carries a kind of piece this app does
     * not know, and the whole work is refused. For the picture only (never to put in), the pieces this app knows are drawn and the
     * rest left out, so a newer work still shows what this app can show of it.
     */
    internal fun known(body: String): Recipe? = runCatching {
        val o = org.json.JSONObject(body)
        val keys = PieceKind.entries.map { it.key }.toSet()
        val all = o.optJSONArray("pieces") ?: return null
        val kept = org.json.JSONArray()
        for (i in 0 until all.length()) all.optJSONObject(i)?.takeIf { it.optString("kind") in keys }?.let(kept::put)
        if (kept.length() == 0 || kept.length() == all.length()) return null
        com.bamnat.core.recipe.RecipeCodec.read(o.put("pieces", kept).toString())
    }.getOrNull()

    /** What a card shows: the tab the work changes, or one of the pictures that are not a tab. */
    sealed interface Face {
        data class Tab(val tab: String) : Face
        data object Pay : Face
        data object Widget : Face
        data object Alarm : Face
        data object Rules : Face
    }

    fun faceOf(item: RecipeItem, r: Recipe): Face {
        val kinds = r.pieces.map { it.kind }.toSet()
        if (LookParts.partUnit(r) == LookParts.LOGIC || (kinds.isNotEmpty() && kinds.all { it == PieceKind.LOGIC })) return Face.Rules
        // A 부품 alone: the screen it sits on.
        LookParts.partUnit(r)?.let { unit -> LookParts.byId(unit)?.tab?.let { t -> return tabFace(t, kinds) } }
        if (item.scope == "tab") item.tabs.firstOrNull()?.let { t ->
            return when (t) {
                "pay" -> Face.Pay
                "money" -> Face.Tab(TabOrder.MONEY)
                "memo" -> Face.Tab(TabOrder.MEMOS)
                "alarm" -> Face.Alarm
                "widget" -> Face.Widget
                else -> Face.Tab(TabOrder.CALENDAR)
            }
        }
        // A 테마 (or a work the server did not sort): its calendar, which is what is seen first after wearing it.
        return when {
            PieceKind.SHIFT_LOOK in kinds || PieceKind.CAL_THEME in kinds || PieceKind.CAL_VIEW in kinds || PieceKind.SCREEN_LOOK in kinds -> Face.Tab(TabOrder.CALENDAR)
            PieceKind.MONEY_LOOK in kinds -> Face.Tab(TabOrder.MONEY)
            PieceKind.MEMO_LOOK in kinds || PieceKind.MEMO_TEMPLATES in kinds -> Face.Tab(TabOrder.MEMOS)
            PieceKind.WIDGET in kinds -> Face.Widget
            PieceKind.ALARMS in kinds -> Face.Alarm
            else -> Face.Tab(TabOrder.CALENDAR)
        }
    }

    /**
     * 1.39 [MKF-02] 크게 넘겨 보기의 위아래: 이 작품이 바꾸는 모든 화면, 카드가 보여 주는 화면([faceOf])부터. 테마는 달력만이 아니라
     * 가계부·메모·급여·위젯·알람·규칙까지 (사용자 2026-11-07 "테마는 말그대로 모든 것을 꾸민 모습"). [style]에 없는 탭(투자의 급여 등)은 빼요.
     */
    fun faces(item: RecipeItem, r: Recipe, style: com.bamnat.core.work.WorkStyle?): List<Face> {
        val kinds = r.pieces.map { it.kind }.toSet()
        val first = faceOf(item, r)
        if (first == Face.Rules || item.scope == "part") return listOf(first)
        val payLook = r.pieces.firstOrNull { it.kind == PieceKind.SCREEN_LOOK }?.values?.get("pay") != null
        val all = buildList {
            add(first)
            if (PieceKind.SHIFT_LOOK in kinds || PieceKind.CAL_THEME in kinds || PieceKind.CAL_VIEW in kinds || PieceKind.SCREEN_LOOK in kinds)
                add(Face.Tab(TabOrder.CALENDAR))
            if (PieceKind.MONEY_LOOK in kinds || PieceKind.SCREEN_LOOK in kinds) add(Face.Tab(TabOrder.MONEY))
            if (PieceKind.MEMO_LOOK in kinds || PieceKind.MEMO_TEMPLATES in kinds || PieceKind.SCREEN_LOOK in kinds) add(Face.Tab(TabOrder.MEMOS))
            if ((PieceKind.CARDS in kinds || payLook) && marketTabFits("pay", style)) add(Face.Pay)
            if (PieceKind.WIDGET in kinds) add(Face.Widget)
            if (PieceKind.ALARMS in kinds && marketTabFits("alarm", style)) add(Face.Alarm)
            if (PieceKind.LOGIC in kinds) add(Face.Rules)
        }
        return all.distinct()
    }

    /** 1.39 [MKF-02] 그 화면의 이름 (넘겨 보기 위아래 칩), 이 유형의 말로. */
    fun faceName(f: Face, style: com.bamnat.core.work.WorkStyle?): String = when (f) {
        is Face.Tab -> when (f.tab) {
            TabOrder.MONEY -> marketTabLabel("money", style)
            TabOrder.MEMOS -> marketTabLabel("memo", style)
            else -> marketTabLabel("calendar", style)
        }
        Face.Pay -> marketTabLabel("pay", style)
        Face.Widget -> "위젯"
        Face.Alarm -> "알람"
        Face.Rules -> "규칙·단추"
    }

    private fun tabFace(t: String, kinds: Set<PieceKind>): Face = when (t) {
        TabOrder.STATS -> Face.Pay
        LookParts.WIDGET -> Face.Widget
        TabOrder.MONEY, TabOrder.MEMOS, TabOrder.CALENDAR -> Face.Tab(t)
        else -> if (PieceKind.WIDGET in kinds) Face.Widget else Face.Tab(TabOrder.CALENDAR)
    }
}

/**
 * [MKV-01] The work as it really looks, [width] wide and the example phone's shape. [onClick] is the card's: a tap on the picture opens the
 * work (the example's own buttons do not take it), and a drag scrolls the list.
 */
@Composable
internal fun MarketShot(item: RecipeItem, width: Dp, style: WorkStyle?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val height = width * (MarketShots.PHONE_H / MarketShots.PHONE_W)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shown = style?.takeIf { it in item.styles || item.styles.isEmpty() } ?: item.styles.firstOrNull() ?: style
    val key = MarketShots.key(item) + "|" + shown?.name + "|" + dark + "|" + width.value.toInt()
    var picture by remember(key) { mutableStateOf(MarketShots.shot(key)) }
    val recipe by produceState<Recipe?>(null, key) {
        if (picture != null) return@produceState
        // 1.39 [MKP-02] The picture this phone kept from before (the 장터 opened again, or after the app was closed): no drawing at all.
        MarketDisk.shot(c.appContext, key)?.let { MarketShots.keep(key, it); picture = it; return@produceState }
        value = MarketShots.recipe(c, item)
    }
    // Said once for the card: the real look once it is drawn, the small picture while it comes.
    ShotFrame(key, width, height, picture, { got ->
        picture = got
        // 1.39 [MKP-02] Kept on the phone too, when all it names (pictures, fonts) was there to draw it as it really looks.
        val body = MarketShots.body(MarketShots.key(item))
        if (body != null) c.appScope.launch { if (MarketDisk.complete(c.appContext, body)) MarketDisk.keepShot(c.appContext, key, got) }
    }, recipe, MarketShots.gaveUp(item), style = shown, onClick = onClick, modifier = modifier,
        face = { MarketShots.faceOf(item, it) }, standIn = { ShotStandIn(item, width, height, shown) })
}

/** [MKV-05] A theme that comes with the app (처음이라면 이런 테마부터), drawn the same way as the 장터 cards. */
@Composable
internal fun BuiltInShot(r: Recipe, name: String, width: Dp, style: WorkStyle?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val height = width * (MarketShots.PHONE_H / MarketShots.PHONE_W)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val key = "builtin:" + name + "|" + style?.name + "|" + dark + "|" + width.value.toInt()
    var picture by remember(key) { mutableStateOf(MarketShots.shot(key)) }
    ShotFrame(key, width, height, picture, { picture = it }, r, false, style = style, onClick = onClick, modifier = modifier,
        face = { MarketShots.Face.Tab(TabOrder.CALENDAR) },
        standIn = { Box(Modifier.size(width, height).background(MaterialTheme.colorScheme.surfaceContainerHigh)) })
}

@Composable
private fun ShotFrame(
    key: String, width: Dp, height: Dp, picture: ImageBitmap?, setPicture: (ImageBitmap) -> Unit, recipe: Recipe?, gaveUp: Boolean,
    style: WorkStyle?, onClick: () -> Unit, modifier: Modifier, face: (Recipe) -> MarketShots.Face, standIn: @Composable () -> Unit,
) {
    // MED-2: this card's turn to be drawn live: it is on the screen (not only composed ahead of it), has stayed a moment, and no other is drawing.
    var turn by remember(key) { mutableStateOf(false) }
    // PC 확인 f020cead MED (끊김이 3차보다 나빠짐: 이제 모든 카드가 그려지니 스크롤 중에도 그림): a card is drawn only once the list
    // has stood still under it, so nothing heavy is drawn while a finger or a fling moves the list. Kept outside Compose state so moving
    // never recomposes the card.
    val still = remember(key) { StillWatch() }
    val now by androidx.compose.runtime.rememberUpdatedState(picture)
    // Whether the example phone has been drawn into its layer once.
    val drew = remember(key) { booleanArrayOf(false) }
    if (picture == null && recipe != null) LaunchedEffect(key) {
        while (true) {
            val was = still.where
            kotlinx.coroutines.delay(MarketShots.STILL)
            if (was != null && was == still.where) break
        }
        MarketShots.drawing.lock()
        try {
            turn = true
            // 1.39 [MKP-04] 장터 로딩: the turn is held only while this phone is drawn the first time (the heavy part), not through the
            // wait for its fonts and pictures before it is kept as a picture; the next card is drawn meanwhile (four still cards on the
            // screen took four of those waits one after another).
            kotlinx.coroutines.withTimeoutOrNull(MarketShots.HOLD) { while (!drew[0] && now == null) kotlinx.coroutines.delay(16) }
            kotlinx.coroutines.delay(MarketShots.SETTLE)
        } finally { MarketShots.drawing.unlock() }
    }
    val said = if (picture != null || recipe != null) "작품 모습" else if (gaveUp) "작품 작은 그림" else "작품 모습 불러오는 중"
    Box(modifier.size(width, height).clip(RoundedCornerShape(width / 10)).onGloballyPositioned { still.at(it.positionInWindow()) }.clearAndSetSemantics {
        contentDescription = said
    }, contentAlignment = Alignment.Center) {
        val p = picture
        val r = recipe
        when {
            p != null -> Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            r != null && turn -> {
                // Drawn live once; when its fonts and pictures have come in, kept as a picture.
                val layer = rememberGraphicsLayer()
                // Taken only once the phone has really been drawn into the layer ([drew]); until then it is tried again.
                // PC 확인 e50bb622 MED-3: a picture of the phone, its letters the example's own size under 아주 크게 too.
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1f)) {
                    Box(Modifier.fillMaxSize().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer); drew[0] = true }) {
                        ShotPhone(face(r), r, style, width / MarketShots.PHONE_W.dp)
                    }
                }
                LaunchedEffect(key) {
                    kotlinx.coroutines.delay(700)
                    while (true) {
                        if (drew[0]) runCatching { layer.toImageBitmap() }.getOrNull()?.let { MarketShots.keep(key, it); setPicture(it); return@LaunchedEffect }
                        kotlinx.coroutines.delay(300)
                    }
                }
            }
            else -> standIn()
        }
        // On top of everything drawn, so the example's own taps and scroll never take the card's.
        Box(Modifier.fillMaxSize().pointerInput(onClick) { detectTapGestures { onClick() } })
    }
}

/**
 * 1.39 [MKF-02] 작품의 한 화면을 [width] 폭으로 바로 그려요 (넘겨 보기·상세 맨 위). 카드와 같은 예시 폰이고, 예시 안의 단추는 누르지 않아요
 * (위에 덮은 층이 누름을 받고, 끌기는 바깥의 넘기기로 가요). [onClick]은 그 누름이에요.
 */
@Composable
internal fun WorkFace(r: Recipe, face: MarketShots.Face, width: Dp, style: WorkStyle?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val height = width * (MarketShots.PHONE_H / MarketShots.PHONE_W)
    val d = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.size(width, height).clip(RoundedCornerShape(width / 10)).clearAndSetSemantics { contentDescription = "작품 모습" }) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 1f)) {
            ShotPhone(face, r, style, width / MarketShots.PHONE_W.dp)
        }
        Box(Modifier.fillMaxSize().pointerInput(onClick) { detectTapGestures { onClick() } })
    }
}

/** While the work comes in (or when it cannot): the old small picture, in the phone's place so the card does not change size. */
@Composable
private fun ShotStandIn(item: RecipeItem, width: Dp, height: Dp, style: WorkStyle?) {
    Box(Modifier.size(width, height).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(width / 10)), contentAlignment = Alignment.Center) {
        val look = item.thumb()
        if (look != null) ThemeThumb(look, width = width * 0.8f) else RecipeCover(item.cover, size = width * 0.5f)
    }
}

@Composable
private fun ShotPhone(face: MarketShots.Face, r: Recipe, style: WorkStyle?, scale: Float) {
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(MarketShots.PHONE_W.dp, MarketShots.PHONE_H.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            when (face) {
                is MarketShots.Face.Tab -> MockPhone(r, style, face.tab, onTab = {})
                MarketShots.Face.Pay -> {
                    val pay = remember(r) {
                        com.bamnat.core.recipe.PayLook.decodeAll(r.pieces.firstOrNull { it.kind == PieceKind.SCREEN_LOOK }?.values?.get("pay") as? String)
                    }
                    val st = style?.takeIf { it != WorkStyle.BUSINESS && it != WorkStyle.INVEST } ?: pay.keys.firstOrNull() ?: WorkStyle.SHIFT
                    MockPhone(r, st, TabOrder.STATS, onTab = {}, lead = {
                        com.bamnat.app.ui.theme.ScreenSkin("pay." + st.name, Modifier.fillMaxWidth()) {
                            com.bamnat.app.ui.settings.PaySample(pay[st] ?: com.bamnat.core.recipe.PayLook(), st) {}
                        }
                    })
                }
                MarketShots.Face.Widget -> MockPhone(r, style, TabOrder.SETTINGS, onTab = {}, only = true, bar = false, lead = { HomeWithWidget(r, style) })
                MarketShots.Face.Alarm -> MockPhone(r, style, TabOrder.SETTINGS, onTab = {}, only = true, bar = false, lead = { AlarmScreen(r) })
                MarketShots.Face.Rules -> RulesPhone(r)
            }
        }
    }
}

/** The widget as the home screen shows it: on a wallpaper, with app icons under it. */
@Composable
private fun HomeWithWidget(r: Recipe, style: WorkStyle?) {
    val w = remember(r) {
        val v = r.pieces.firstOrNull { it.kind == PieceKind.WIDGET }?.values.orEmpty()
        val base = com.bamnat.core.recipe.WidgetLook.decode("")
        base.copy(alpha = (v["alpha"] as? Number)?.toInt()?.coerceIn(30, 100) ?: base.alpha,
            theme = (v["theme"] as? String)?.takeIf { it in com.bamnat.core.recipe.WidgetLook.THEMES } ?: base.theme,
            bg = (v["bg"] as? Number)?.toLong() ?: base.bg, ink = (v["ink"] as? Number)?.toLong() ?: base.ink,
            corner = (v["corner"] as? Number)?.toInt()?.coerceIn(0, 28) ?: base.corner,
            text = (v["text"] as? String)?.takeIf { it in com.bamnat.core.recipe.WidgetLook.TEXTS } ?: base.text)
    }
    Column(Modifier.fillMaxWidth().height(520.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFF26A69A)))).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("09:41", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
        com.bamnat.app.ui.studio.WidgetSample(w, style)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.35f))) }
        }
    }
}

/** The alarm screen in the work's colours, and the alarms it sets under it. */
@Composable
private fun AlarmScreen(r: Recipe) {
    val v = remember(r) { r.pieces.firstOrNull { it.kind == PieceKind.ALARMS }?.values.orEmpty() }
    com.bamnat.app.ui.theme.ScreenSkin(com.bamnat.core.recipe.ScreenLooks.ALARM, Modifier.fillMaxWidth().height(520.dp)) {
        Column(Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.bamnat.app.ui.settings.AlarmSample {}
            @Suppress("UNCHECKED_CAST")
            val byRole = (v["byRole"] as? List<Map<String, Any?>>).orEmpty()
            byRole.take(4).forEach { m ->
                val role = com.bamnat.core.formula.LogicRule.ROLE_WORDS[m["role"] as? String] ?: "근무"
                val before = (m["before"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }
                if (before.isNotEmpty()) Text("$role · " + before.joinToString(", ") { "${it}분 전" },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            (v["snooze"] as? Number)?.let { Text("다시 울림 ${it}분", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** 규칙·단추: what the user will actually get — the notifications on the lock screen and the buttons in the app. */
@Composable
private fun RulesPhone(r: Recipe) {
    val logic = remember(r) { com.bamnat.core.formula.Logic.from(r.pieces.firstOrNull { it.kind == PieceKind.LOGIC }?.values) }
    Column(Modifier.fillMaxSize().border(6.dp, Color(0xFF3A3A44), RoundedCornerShape(32.dp)).padding(6.dp)
        .background(Brush.verticalGradient(listOf(Color(0xFF1B2440), Color(0xFF3B2F5C))), RoundedCornerShape(26.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("07:10", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 18.dp))
        Text("잠금 화면", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        logic.rules.take(3).forEach { x ->
            val body = if (x.act.verb == com.bamnat.core.formula.LogicVerb.NOTIFY) sample(x.act.arg.ifEmpty { x.name }) else sample(x.act.say())
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.92f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("밤낮 · ${x.sayWhen()}", fontSize = 12.sp, color = Color(0xFF5B5B66), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(x.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, fontSize = 14.sp, color = Color(0xFF2B2B33), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (logic.buttons.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            logic.buttons.take(3).forEach { b ->
                Text("${b.emoji?.let { "$it " }.orEmpty()}${b.label}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFE082)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        val more = logic.rules.size - 3
        if (more > 0) Text("규칙 ${more}개 더", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
    }
}

/** A notification's words with its blanks ({이번주야간} …) filled as on an ordinary day. */
private fun sample(text: String): String = text.replace(Regex("\\{[^}]{1,20}\\}")) { m ->
    val name = m.value.trim('{', '}')
    when {
        // PC 확인 edb46c62 LOW: '오늘 매출 3, 목표를 넘었어요' — money blanks are written {원(…)}.
        // PC 확인 f020cead LOW: '큰 지출 38,500원 · 이달 쓴 돈 38,500원' — a month's sum is bigger than one day's.
        name.startsWith("원(이달") && "지출" in name -> "1,284,000원"
        name.startsWith("원(이달") -> "3,120,000원"
        name.startsWith("원(") && "지출" in name -> "38,500원"
        name.startsWith("원(") || "돈" in name || "급여" in name || "금액" in name || "매출" in name -> "120,000원"
        name == "근무시작" -> "07:00"
        name == "근무끝" -> "19:00"
        name == "근무시간" -> "12"
        name == "이달근무시간" -> "168"
        name == "날짜" -> "11월 5일"
        name == "요일" -> "수"
        name == "월" -> "11"
        name == "일" -> "5"
        name == "이번주야간" -> "2"
        name == "연속근무" -> "4"
        name == "공휴일이름" -> "개천절"
        "시각" in name -> "07:00"
        "시간" in name -> "40"
        "공휴일" in name -> "개천절"
        "이름" in name || "근무" == name -> "주간"
        else -> "3"
    }
}

/** Where a card was last placed on the screen (null until it is, so a card composed ahead of the screen is never drawn). */
private class StillWatch {
    var where: androidx.compose.ui.geometry.Offset? = null
        private set
    fun at(o: androidx.compose.ui.geometry.Offset) { where = o }
}
