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

    fun shot(key: String): ImageBitmap? = shots.get(key)
    fun keep(key: String, b: ImageBitmap) { shots.put(key, b) }

    suspend fun recipe(c: AppContainer, item: RecipeItem): Recipe? {
        val k = key(item)
        recipes.get(k)?.let { return it }
        if (k in failed) return null
        val got = gate.withPermit {
            recipes.get(k) ?: withContext(Dispatchers.IO) {
                runCatching { c.recipeApi.show(item.code, null, null) }.getOrNull()?.let { doc -> runCatching { doc.recipe() }.getOrNull() ?: known(doc.body) }
            }
        }
        if (got == null) failed.add(k) else recipes.put(k, got)
        return got
    }

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
    val recipe by produceState<Recipe?>(null, key) { if (picture == null) value = MarketShots.recipe(c, item) }
    // Said once for the card: the real look once it is drawn, the small picture while it comes.
    val said = if (picture != null || recipe != null) "작품 모습" else if (MarketShots.gaveUp(item)) "작품 작은 그림" else "작품 모습 불러오는 중"
    Box(modifier.size(width, height).clip(RoundedCornerShape(width / 10)).clearAndSetSemantics {
        contentDescription = said
    }, contentAlignment = Alignment.Center) {
        val p = picture
        val r = recipe
        when {
            p != null -> Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            r != null -> {
                // Drawn live once; when its fonts and pictures have come in, kept as a picture.
                val layer = rememberGraphicsLayer()
                Box(Modifier.fillMaxSize().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                    ShotPhone(item, r, shown, width / MarketShots.PHONE_W.dp)
                }
                LaunchedEffect(key) {
                    kotlinx.coroutines.delay(1_200)
                    runCatching { layer.toImageBitmap() }.getOrNull()?.let { MarketShots.keep(key, it); picture = it }
                }
            }
            else -> ShotStandIn(item, width, height, shown)
        }
        // On top of everything drawn, so the example's own taps and scroll never take the card's.
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
private fun ShotPhone(item: RecipeItem, r: Recipe, style: WorkStyle?, scale: Float) {
    val face = remember(item, r) { MarketShots.faceOf(item, r) }
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
        "시각" in name -> "07:00"
        "시간" in name -> "40"
        "돈" in name || "급여" in name || "금액" in name || "매출" in name -> "120,000원"
        "공휴일" in name -> "개천절"
        "이름" in name || "근무" == name -> "주간"
        else -> "3"
    }
}
