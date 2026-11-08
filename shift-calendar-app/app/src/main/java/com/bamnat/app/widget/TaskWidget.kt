package com.bamnat.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bamnat.app.R
import com.bamnat.core.recipe.PartStyle
import com.bamnat.app.NoticeLinks
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.tier.Feature
import com.bamnat.app.ui.Fmt
import com.bamnat.core.memo.TaskList
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 1.39 [TASK-07] 할 일 위젯 (할 일 앱 조사, 사용자 2026-11-07 "할 일 위젯 먼저"): 내 달력's to-dos on the home screen — late ones, today's
 * (its date or a repeat that comes back today) and pinned notes with no date ([TaskList]) — each with a circle that finishes it right
 * there (the same 끝냄 as the app, a repeating one for today only), the words opening it, and ＋ writing a new one for today.
 *
 * 무료·프로 (사용자 2026-11-07 "무료 프로의 구분은 확실히"): the first to-do widget on the home screen is free, its look included (위젯
 * 모양·부품 `widget.task`·`widget.task.check`·`widget.task.add`·꾸밈 층); a second one and more are [Feature.MORE_TASK_WIDGETS] and say so
 * in place of the list, with the way to 프로. The first placed (the lowest widget id) is the free one, so removing it frees the next.
 */
class TaskWidget : GlanceAppWidget() {
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    // As [MoneyWidget]: a running session recomposes only on update, so the list is read again each time [redraw] stamps the state.
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { load(context, id) }.getOrNull() ?: Shown(emptyList(), true, Today.date())
        val placedAsLine = runCatching {
            val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.className == TaskLineWidgetReceiver::class.java.name
        }.getOrDefault(false)
        provideContent {
            val stamp = androidx.glance.currentState(STAMP) ?: 0L
            var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(first) }
            androidx.compose.runtime.LaunchedEffect(stamp) { if (stamp != 0L) runCatching { load(context, id) }.getOrNull()?.let { shown = it } }
            WidgetLookTheme {
                // 1.39 [TASK-31] One row high (할 일 한 줄, or the big one made short): the next to-do and what is left.
                val short = oneRow(androidx.glance.LocalSize.current.height, placedAsLine)
                WidgetSurface(GlanceModifier) {
                    when {
                        !shown.allowed -> TaskLocked(short)
                        short -> TaskLine(shown.rows)
                        else -> TaskContent(shown.rows, shown.today)
                    }
                }
            }
        }
    }

    /** The list for [today], or [allowed] false on a widget past the free one without 프로. */
    internal data class Shown(val rows: List<TaskList.Row>, val allowed: Boolean, val today: LocalDate)

    companion object {
        private val STAMP = androidx.datastore.preferences.core.longPreferencesKey("task_stamp")

        /** 내 달력's to-dos now ([TaskList.of]), with whether widget [id] may show them. */
        internal suspend fun load(context: Context, id: GlanceId?): Shown = withContext(Dispatchers.IO) {
            val c = context.appContainer
            val today = Today.date()
            val allowed = id == null || c.featureGate.allows(Feature.MORE_TASK_WIDGETS) || isFirst(context, id)
            if (!allowed) return@withContext Shown(emptyList(), false, today)
            val memos = c.memoRepository.memosFor(PersonalCalendar.MAIN).first()
            val shifts = c.memoRepository.shiftLookup(memos, PersonalCalendar.MAIN).first()
            Shown(TaskList.of(memos, today, shifts), true, today)
        }

        /** The free one: the to-do widget placed first (the lowest id) on this phone. */
        private suspend fun isFirst(context: Context, id: GlanceId): Boolean = runCatching {
            val manager = GlanceAppWidgetManager(context)
            val mine = manager.getAppWidgetId(id)
            manager.getGlanceIds(TaskWidget::class.java).minOfOrNull { manager.getAppWidgetId(it) }?.let { it == mine } ?: true
        }.getOrDefault(true)

        /** Redraws the to-do widgets, only when one is on the home screen. */
        suspend fun redraw(context: Context) {
            runCatching {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(TaskWidget::class.java)
                if (ids.isEmpty()) return
                val now = Today.millis()
                ids.forEach { id -> androidx.glance.appwidget.state.updateAppWidgetState(context, id) { it[STAMP] = now }; TaskWidget().update(context, id) }
            }
        }

        /** Shorter than this, the widget is one row ([TaskLine]). */
        internal val SHORT_BELOW = 100.dp

        /**
         * 할 일 한 줄 stays one row up to this height: a 2×1 cell is about 100~110 dp on a Pixel launcher, more than [SHORT_BELOW]
         * (PC c0ca3d9f: it showed the head and a row, two lines). Made two cells high or more, it opens into the list.
         */
        internal val LINE_BELOW = 160.dp

        /** Whether a widget [height] high is drawn as one row; [placedAsLine] for one placed as 할 일 한 줄. */
        internal fun oneRow(height: androidx.compose.ui.unit.Dp, placedAsLine: Boolean): Boolean =
            height < if (placedAsLine) LINE_BELOW else SHORT_BELOW

        internal val MEMO_ID = ActionParameters.Key<Long>("memo")
        internal val DAY = ActionParameters.Key<String>("day")
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

/**
 * 1.39 [TASK-31] 할 일 한 줄 (2×1, wider to 4×1): the same [TaskWidget], listed small in the phone's widget list. Its ids count with the
 * big one's ([GlanceAppWidgetManager.getGlanceIds] by the widget class), so the free one is still the first 할 일 widget placed.
 */
class TaskLineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}

/** 1.39 [TASK-07] The circle: finishes the memo on its day (a repeating one today only), or brings it back. */
class TaskDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskWidget.MEMO_ID] ?: return
        val day = parameters[TaskWidget.DAY]?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        runCatching { toggle(context, id, day) }
        TaskWidget.redraw(context)
    }

    companion object {
        /** Memo [id] finished on [day] if it was not, else back to do; null when it is gone or in the trash. */
        internal suspend fun toggle(context: Context, id: Long, day: LocalDate?): com.bamnat.core.memo.Memo? {
            val repo = context.appContainer.memoRepository
            val memo = repo.get(id)?.takeUnless { it.inTrash } ?: return null
            return repo.save(memo.withDone(!memo.isDoneOn(day), day, Today.millis()))
        }
    }
}

/** Opens memo [id] (0: a new one) on [day], as the memo tab would. */
internal fun openMemo(context: Context, id: Long, day: LocalDate?) =
    actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.MEMO_OPEN, payload = "$id|${day ?: ""}"))

@Composable
internal fun TaskContent(rows: List<TaskList.Row>, today: LocalDate) {
    val context = androidx.glance.LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        // 1.39 [TASK-38] ＋ opens the 한 줄 window over the home screen, not the whole app.
        TaskHead(TaskList.headline(rows), actionStartActivity(com.bamnat.app.memo.QuickTaskActivity.intent(context)))
        Spacer(GlanceModifier.height(4.dp))
        if (rows.isEmpty()) {
            Text("오늘 할 일이 없어요. ＋를 눌러 써 보세요", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(12)), maxLines = 2,
                modifier = GlanceModifier.clickable(actionStartActivity(com.bamnat.app.memo.QuickTaskActivity.intent(context))))
            return@Column
        }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(rows, itemId = { it.memo.id * 4 + it.kind.ordinal }) { row -> TaskRow(row, today) }
        }
    }
}

@Composable
private fun TaskHead(title: String, add: androidx.glance.action.Action) {
    val head = widgetPart("widget.head")
    val plus = widgetPart("widget.task.add")
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (head.hide == true) "" else title,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(13), fontWeight = FontWeight.Medium).widgetLetters(head),
            maxLines = 1, modifier = GlanceModifier.defaultWeight().widgetBox(head))
        if (plus.hide != true) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 4.dp)
                    .widgetBox(plus, 16).clickable(add).semantics { contentDescription = "할 일 쓰기" },
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = wsp(16), fontWeight = FontWeight.Bold).widgetLetters(plus), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TaskRow(row: TaskList.Row, today: LocalDate) {
    val context = androidx.glance.LocalContext.current
    val states = setOfNotNull("today".takeIf { row.kind == TaskList.Kind.TODAY }, "late".takeIf { row.kind == TaskList.Kind.LATE }, "done".takeIf { row.done })
    val line = widgetPart("widget.task", states)
    if (line.hide == true) return
    val check = widgetPart("widget.task.check", states - "today")
    val toggle = actionRunCallback<TaskDoneAction>(actionParametersOf(TaskWidget.MEMO_ID to row.memo.id, TaskWidget.DAY to (row.day?.toString() ?: "")))
    // A finished row is struck through and pale: in Korean the strike line sits on the ㅡ·ㅗ·ㅜ strokes and hardly shows on its own
    // (PC c0ca3d9f, 두 앱 실사용 2차), so the paler 테두리 색 says it as well.
    val faint = if (row.done) GlanceTheme.colors.outline else GlanceTheme.colors.onSurface
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).widgetBox(line), verticalAlignment = Alignment.CenterVertically) {
        // A wide target for a thumb on the home screen: the circle sits in 36 dp.
        // TalkBack reads what the circle does, not "○" (조사 보완 11-07: 위젯 글자 읽기).
        val said = if (row.done) "끝냄 취소: ${row.text}" else "끝내기: ${row.text}"
        Box(GlanceModifier.width(36.dp).height(36.dp).clickable(toggle).semantics { contentDescription = said }, contentAlignment = Alignment.Center) {
            TaskCircle(row.done, when { row.done -> GlanceTheme.colors.primary; row.kind == TaskList.Kind.LATE -> GlanceTheme.colors.error
                else -> GlanceTheme.colors.onSurfaceVariant }, check)
        }
        Column(GlanceModifier.defaultWeight().clickable(openMemo(context, row.memo.id, row.day))) {
            Text(row.text.ifEmpty { "제목 없는 메모" },
                style = TextStyle(color = faint, fontSize = wsp(14), textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None).widgetLetters(line),
                maxLines = 1)
            note(row, today)?.let { n ->
                Text(n, style = TextStyle(color = if (row.kind == TaskList.Kind.LATE) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = wsp(11)), maxLines = 1)
            }
        }
    }
}

/**
 * 1.39 [TASK-07] The circle a row is finished with, drawn rather than written: a launcher's font draws "○" as a dot (PC e0f19f47,
 * 위젯2-늦음.jpg), so it is a picture the size the letter was meant to be — 18 sp times 위젯 글자 크기, or the 동그라미 part's 크기 —
 * in its colour (the part's 글자 색 when set) on the part's fill and corners.
 */
@Composable
private fun TaskCircle(done: Boolean, color: ColorProvider, check: PartStyle) {
    val look = TextStyle(color = color, fontSize = wsp(18)).widgetLetters(check)
    val side = (look.fontSize?.value ?: 18f).coerceIn(10f, 40f)
    Image(ImageProvider(if (done) R.drawable.ic_task_done else R.drawable.ic_task_open), contentDescription = null,
        colorFilter = ColorFilter.tint(look.color),
        modifier = GlanceModifier.widgetBox(check, 18).width(side.dp).height(side.dp))
}

/** The small line under a row: "늦음 · 11/5 10:00", "15:00 · 2/5", "고정", or nothing. */
private fun note(row: TaskList.Row, today: LocalDate): String? = listOfNotNull(
    when (row.kind) {
        TaskList.Kind.LATE -> "늦음 · ${row.day?.let { Fmt.shortDate(it) }.orEmpty()}"
        TaskList.Kind.PINNED -> "고정"
        TaskList.Kind.TODAY -> null
    },
    row.time?.let { Fmt.time(it) },
    row.count,
).joinToString(" · ").ifEmpty { null }

/**
 * 1.39 [TASK-31] One row: the circle of the next to-do left (late first, as the list goes), its words and time, how many are left,
 * and ＋. With nothing left it says so.
 */
@Composable
internal fun TaskLine(rows: List<TaskList.Row>) {
    val context = androidx.glance.LocalContext.current
    val next = rows.firstOrNull { !it.done }
    val left = rows.count { !it.done }
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (next != null) {
            val states = setOfNotNull("today".takeIf { next.kind == TaskList.Kind.TODAY }, "late".takeIf { next.kind == TaskList.Kind.LATE })
            val check = widgetPart("widget.task.check", states - "today")
            val toggle = actionRunCallback<TaskDoneAction>(actionParametersOf(TaskWidget.MEMO_ID to next.memo.id, TaskWidget.DAY to (next.day?.toString() ?: "")))
            Box(GlanceModifier.width(36.dp).height(36.dp).clickable(toggle).semantics { contentDescription = "끝내기: ${next.text}" },
                contentAlignment = Alignment.Center) {
                TaskCircle(false, if (next.kind == TaskList.Kind.LATE) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, check)
            }
            val line = widgetPart("widget.task", states)
            // PC eae4e024: in one line '우유 사기 ·…' cut the time and what is left at 2×1. The title gets the line, the time (늦음) goes
            // small under it, and how many are left sits as a number by ＋, so none of it is cut.
            val note = listOfNotNull("늦음".takeIf { next.kind == TaskList.Kind.LATE }, next.time?.let { Fmt.time(it) }).joinToString(" · ")
            Column(GlanceModifier.defaultWeight().clickable(openMemo(context, next.memo.id, next.day))) {
                Text(next.text.ifEmpty { "제목 없는 메모" },
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = wsp(14)).widgetLetters(line), maxLines = 1)
                if (note.isNotEmpty()) Text(note, style = TextStyle(color = if (next.kind == TaskList.Kind.LATE) GlanceTheme.colors.error
                    else GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(11)), maxLines = 1)
            }
            if (left > 1) {
                Box(GlanceModifier.padding(horizontal = 6.dp).semantics { contentDescription = "남은 할 일 ${left}개" }, contentAlignment = Alignment.Center) {
                    Text("$left", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(12), fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        } else {
            Text(if (rows.isEmpty()) "오늘 할 일이 없어요" else "오늘 할 일을 다 끝냈어요",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(13)), maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = 4.dp))
        }
        val plus = widgetPart("widget.task.add")
        if (plus.hide != true) Box(
            GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 4.dp)
                .widgetBox(plus, 16).clickable(actionStartActivity(com.bamnat.app.memo.QuickTaskActivity.intent(context)))
                .semantics { contentDescription = "할 일 쓰기" },
            contentAlignment = Alignment.Center,
        ) {
            Text("＋", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = wsp(16), fontWeight = FontWeight.Bold).widgetLetters(plus), maxLines = 1)
        }
    }
}

/** A second to-do widget and more without 프로: what it would do, and the way there. The first one stays free. */
@Composable
internal fun TaskLocked(short: Boolean = false) {
    val context = androidx.glance.LocalContext.current
    if (short) {
        Row(GlanceModifier.fillMaxSize().clickable(actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.PRO))),
            verticalAlignment = Alignment.CenterVertically) {
            Text("할 일 위젯 2개째부터는 프로예요", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = wsp(13)), maxLines = 1,
                modifier = GlanceModifier.defaultWeight())
            Text("프로 보기", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = wsp(13), fontWeight = FontWeight.Medium), maxLines = 1)
        }
        return
    }
    // PC 2afb3a76 (둘째 위젯의 프로 글이 위쪽만 쓰고 아래가 비어요): in the middle, with the way there as a button.
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.PRO))),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("할 일 위젯 2개째부터는 프로예요", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = wsp(14), fontWeight = FontWeight.Medium), maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        Text("처음 놓은 할 일 위젯은 계속 무료예요", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(12)), maxLines = 2)
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("프로 보기", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = wsp(13), fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}
