package com.bamnat.app.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bamnat.app.NoticeLinks
import com.bamnat.app.appContainer
import com.bamnat.app.logic.FxOutside
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 1.39 [FX-04] 단추 위젯 (조사 18장 FXW-01 '위젯 단추', 예: 배달 '+1건', 물 한 잔): up to four buttons a work put on the home screen, each
 * with its 값 칸 number, pressed without opening the app. Without 프로 or a 하루 써 보기 it says the buttons rest and opens the 프로
 * screen; with none made it points at the 스튜디오.
 */
class FxButtonWidget : GlanceAppWidget() {
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    /** One button as drawn: its id and its line ("🛵 +1건 · 12건"). */
    /**
     * 1.39 [FX-14] 위젯 꾸밈: a button that counts a 숫자 값 칸 with a 목표 shows how far it is ([progress] 0~1) and, when the widget is tall
     * enough, its last 7 periods as small bars ([recent], oldest first; the bar of one that reached the goal is full colour).
     */
    internal data class Item(val id: String, val line: String, val progress: Float? = null, val recent: List<Double> = emptyList(), val goal: Double? = null,
        /** [RVM-06] Under each bar: 월 화 … 오늘, or the week's Monday, or the month. */
        val marks: List<String> = emptyList(),
        /** [RVM-14] PC-35: the screen it opens ('잠들어요' → 수면): pressed, the app opens there and presses it on screen. */
        val opens: NoticeLinks.Target? = null)

    /** [items] run now; [resting] buttons are kept but wait for 프로; none of either, nothing was made. */
    internal data class Board(val items: List<Item>, val resting: Int)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { load(context) }.getOrNull() ?: Board(emptyList(), 0)
        val pro = actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.PRO).setData(android.net.Uri.parse("bamnat-widget://fx/pro")))
        val make = actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.MARKET).setData(android.net.Uri.parse("bamnat-widget://fx/make")))
        provideContent {
            val stamp = androidx.glance.currentState(STAMP) ?: 0L
            var board by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(first) }
            androidx.compose.runtime.LaunchedEffect(stamp) { if (stamp != 0L) runCatching { load(context) }.getOrNull()?.let { board = it } }
            WidgetLookTheme {
                Box(GlanceModifier.fillMaxSize().background(widgetBg()).cornerRadius(widgetCorner()).padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        board.items.isNotEmpty() -> Buttons(board.items)
                        board.resting > 0 -> Note("단추 ${board.resting}개는 프로에서 켜져요", "눌러서 하루 써 보기·프로 보기", pro)
                        else -> Note("밤낮 단추", "스튜디오 › 규칙·단추에서 '단추 위젯' 자리 단추를 만들거나 장터에서 받아요", make)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Buttons(items: List<Item>) {
        val context = androidx.glance.LocalContext.current
        val size = LocalSize.current
        val wide = size.width >= 220.dp
        val rows = if (wide) items.chunked(2) else items.map { listOf(it) }
        // [RVM-06] PC-45: a tall widget left the space above and below empty and its 7-day bars were short lines with no names. Bars
        // (with 요일 under them) show when one row fits them; the buttons grow to the height left (40dp ~ 140dp) and the bars take the
        // rest (36dp ~ 120dp).
        val bars = rows.size == 1 && rows[0].any { it.recent.size > 1 } && size.height >= 150.dp
        val progress = if (rows.any { r -> r.any { it.progress != null } }) 7.dp * rows.size else 0.dp
        val room = size.height - 16.dp - 6.dp * (rows.size - 1) - progress
        val buttonHeight = ((room - (if (bars) BARS + 6.dp + MARK else 0.dp)) / rows.size).coerceIn(WIDGET_BUTTON_HEIGHT, 140.dp)
        val barsHeight = (room - buttonHeight - 6.dp - MARK).coerceIn(BARS, 120.dp)
        val big = buttonHeight >= 64.dp
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    row.forEachIndexed { j, it ->
                        if (j > 0) Spacer(GlanceModifier.width(6.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Box(
                                GlanceModifier.fillMaxWidth().height(buttonHeight).background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp)
                                    .padding(horizontal = 10.dp).clickable(it.opens?.let { t -> actionStartActivity(FxOutside.openIntent(context, it.id, t)) }
                                        ?: actionRunCallback<FxPressAction>(actionParametersOf(FxPressAction.ID to it.id))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(it.line, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = wsp(if (big) 16 else 13), fontWeight = FontWeight.Medium),
                                    maxLines = if (big) 2 else 1)
                            }
                            // 1.39 [FX-14] 목표 막대, and on a tall widget with one row the last 7 periods.
                            it.progress?.let { p ->
                                Spacer(GlanceModifier.height(3.dp))
                                androidx.glance.appwidget.LinearProgressIndicator(progress = p, modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                                    color = GlanceTheme.colors.primary, backgroundColor = GlanceTheme.colors.surfaceVariant)
                            }
                            if (bars && it.recent.size > 1) Bars(it.recent, it.goal, it.marks, barsHeight)
                        }
                    }
                }
            }
        }
    }

    /**
     * 1.39 [FX-14] The last periods as small bars (목표에 닿은 기간은 진하게); [RVM-06] each named under it, the last one 오늘. A Glance row
     * keeps 10 children at most, so the gaps are each bar's own padding: with a Spacer between them the 7 bars were 5 (PC-45 '짧은 줄 5개').
     */
    @androidx.compose.runtime.Composable
    private fun Bars(recent: List<Double>, goal: Double?, marks: List<String>, height: androidx.compose.ui.unit.Dp = BARS) {
        val top = (recent + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().height(height), verticalAlignment = Alignment.Bottom) {
            recent.forEach { v ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.5.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(GlanceModifier.fillMaxWidth().height((v / top * height.value).coerceIn(2.0, height.value.toDouble()).dp).cornerRadius(3.dp)
                        .background(if (goal != null && v >= goal) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)) {}
                }
            }
        }
        if (marks.size == recent.size) Row(GlanceModifier.fillMaxWidth().height(MARK), verticalAlignment = Alignment.CenterVertically) {
            marks.forEachIndexed { i, m ->
                Text(m, modifier = GlanceModifier.defaultWeight(), maxLines = 1, style = TextStyle(
                    color = if (i == marks.lastIndex) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = wsp(10), fontWeight = if (i == marks.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.glance.text.TextAlign.Center))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Note(title: String, text: String, open: androidx.glance.action.Action) {
        Column(GlanceModifier.fillMaxSize().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = wsp(14), fontWeight = FontWeight.Bold), maxLines = 1)
            Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(12)), maxLines = 3)
        }
    }

    companion object {
        private val STAMP = androidx.datastore.preferences.core.longPreferencesKey("fx_stamp")

        internal suspend fun load(context: Context): Board = withContext(Dispatchers.IO) {
            val c = context.appContainer
            val run = FxOutside.buttons(c, ButtonPlace.WIDGET).take(FxOutside.MAX_WIDGET)
            val kept = FxOutside.kept(c, ButtonPlace.WIDGET)
            Board(run.map { b ->
                val name = b.act.arg.takeIf { b.act.verb == com.bamnat.core.formula.LogicVerb.VAL_ADD || b.act.verb == com.bamnat.core.formula.LogicVerb.VAL_SET }
                val def = name?.let { com.bamnat.app.logic.LogicRunner.logic(c, all = true).value(it) }?.takeIf { it.kind == com.bamnat.core.formula.ValueKind.NUM }
                val goal = def?.goal
                val now = name?.let { com.bamnat.app.logic.FxValues.now(it) }
                val keeps = def != null && def.reset != com.bamnat.core.formula.ValueReset.NEVER
                Item(b.id, FxOutside.line(c, b), if (goal != null && now != null) (now / goal).toFloat().coerceIn(0f, 1f) else null,
                    if (keeps) com.bamnat.app.logic.FxValues.recent(c, def!!.name, 7) else emptyList(), goal,
                    if (keeps) marks(def!!.reset, Today.date(), 7) else emptyList(), FxOutside.opens(c, b.id))
            }, if (run.isEmpty()) kept.size else 0)
        }

        private val BARS = 36.dp
        private val MARK = 14.dp

        /** [RVM-06] The names of the last [n] periods, oldest first: 요일 (the last 오늘), a week's Monday (이번 주), a month (이번 달). */
        internal fun marks(reset: com.bamnat.core.formula.ValueReset, today: java.time.LocalDate, n: Int): List<String> = (n - 1 downTo 0).map { k ->
            val d = reset.back(today, k)
            when (reset) {
                com.bamnat.core.formula.ValueReset.DAY -> if (k == 0) "오늘" else "일월화수목금토"[d.dayOfWeek.value % 7].toString()
                com.bamnat.core.formula.ValueReset.WEEK -> if (k == 0) "이번 주" else d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).let { "${it.monthValue}/${it.dayOfMonth}" }
                com.bamnat.core.formula.ValueReset.MONTH -> if (k == 0) "이번 달" else "${d.monthValue}월"
                com.bamnat.core.formula.ValueReset.NEVER -> ""
            }
        }

        /** Redraws the 단추 위젯, only when one is on the home screen. */
        suspend fun redraw(context: Context) {
            val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(FxButtonWidget::class.java) }.getOrDefault(emptyList())
            if (ids.isEmpty()) return
            val now = Today.millis()
            runCatching { ids.forEach { id -> androidx.glance.appwidget.state.updateAppWidgetState(context, id) { it[STAMP] = now }; FxButtonWidget().update(context, id) } }
        }
    }
}

class FxButtonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FxButtonWidget()
}

/** 1.39 [FX-04] One 단추 위젯 button pressed: it runs, says what it did, and the widget shows the new number. */
class FxPressAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.appContainer
        val id = parameters[ID] ?: return
        val said = runCatching { FxOutside.press(c, id) }.getOrDefault("지금은 누를 수 없어요. 앱을 열어 주세요")
        withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, said, Toast.LENGTH_SHORT).show() }
        FxButtonWidget.redraw(context)
    }

    companion object {
        val ID = ActionParameters.Key<String>("fx_id")
    }
}
