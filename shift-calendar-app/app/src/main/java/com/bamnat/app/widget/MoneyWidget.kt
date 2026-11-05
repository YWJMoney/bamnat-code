package com.bamnat.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bamnat.app.NoticeLinks
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.logic.PayFacts
import com.bamnat.app.logic.PaySource
import com.bamnat.app.logic.formulaWord
import com.bamnat.app.ui.Fmt
import com.bamnat.core.formula.MoneyFacts
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 1.37 [LEDH-14] 오늘 쓸 돈 + 돈 적기: what can be spent today (as 가계부's 앞으로의 돈 counts it, from 내 달력's accounts and the next
 * pay or 입금), what was spent today, and one tap to 돈 적기. Home screen only, and 금액 숨기기 hides the amounts here too.
 */
class MoneyWidget : GlanceAppWidget() {
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    // bc7e2d43 PC 확인 (먼저 놓은 위젯이 돈을 적어도 바로 안 바뀜): a running widget session only recomposes on update, so the line is
    // read inside the composition, again each time [redraw] stamps the widget's state.
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = runCatching { load(context) }.getOrNull() ?: Line(null, 0, false)
        val open = actionStartActivity(NoticeLinks.intent(context, NoticeLinks.Target.MONEY_ADD))
        provideContent {
            val stamp = androidx.glance.currentState(STAMP) ?: 0L
            var line by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(first) }
            androidx.compose.runtime.LaunchedEffect(stamp) { if (stamp != 0L) runCatching { load(context) }.getOrNull()?.let { line = it } }
            WidgetLookTheme {
                Box(
                    GlanceModifier.fillMaxSize().background(widgetBg()).cornerRadius(widgetCorner()).padding(12.dp).clickable(open),
                    contentAlignment = Alignment.CenterStart,
                ) { MoneyContent(line) }
            }
        }
    }

    /** [perDay] is today's money left (null when neither accounts nor a budget can say it); [hidden] follows 금액 숨기기. */
    internal data class Line(val perDay: Long?, val spent: Long, val hidden: Boolean)

    companion object {
        private val STAMP = androidx.datastore.preferences.core.longPreferencesKey("money_stamp")

        internal suspend fun load(context: Context): Line = withContext(Dispatchers.IO) {
            val c = context.appContainer
            val scope = c.scopeOf(PersonalCalendar.MAIN)
            val settings = scope.settings.first()
            val style = settings.workStyle
            val book = c.calendars.money(PersonalCalendar.MAIN).book.first()
            val today = Today.date()
            val payDays = if (style?.usesPayEngine == false) emptyList() else scope.pay.profile.value?.let { p ->
                runCatching { PayFacts(PaySource(p, settings, scope.pay.closed.value), c.scheduleRepository.snapshot.first()).payDays(today) }.getOrNull()
            }.orEmpty()
            // bc7e2d43 PC 확인 (통장·예산을 넣어도 '통장 잔액을 적으면 보여요', 앱은 15,789원): the same number as 가계부's 오늘 한 줄 —
            // from the accounts until the next pay when that can be known, else from this month's budget.
            val spentAll = book.entries.filter { it.date == today && it.kind == MoneyKind.EXPENSE }.sumOf { it.amount }
            val until = com.bamnat.core.money.MoneyAhead.forecast(book, today, com.bamnat.core.money.MoneyAhead.FULL_DAYS, payDays)
                .untilNext(MoneyFacts.nextKinds(style?.formulaWord().orEmpty()), spentAll, com.bamnat.core.money.MoneyAhead.spentFromStart(book.accounts, book.entries, today))
            val left = until?.todayLeft ?: run {
                val month = java.time.YearMonth.from(today)
                val rolling = if (style == com.bamnat.core.work.WorkStyle.INVEST) emptySet() else settings.budgetRollover.split('\t').filter { it.isNotBlank() }.toSet()
                val carry = com.bamnat.core.money.Budgets.carry(book.budgets, book.entries, month.atDay(1), month.atEndOfMonth(), rolling)
                com.bamnat.core.money.Budgets.status(book.budgets, book.entries, month, today,
                    com.bamnat.core.money.UpcomingPayments.leftThisMonth(book.fixed, book.entries, today), carry).todayLeft
            }
            val spent = book.entries.filter { it.date == today && it.kind == MoneyKind.EXPENSE && it.source != MoneySource.FIXED }.sumOf { it.amount }
            com.bamnat.app.ui.Fmt.masked = settings.hideAmounts
            Line(left?.coerceAtLeast(0), spent, settings.hideAmounts)
        }

        /** Redraws only this widget, and only when one is on the home screen. */
        suspend fun redraw(context: Context) {
            val placed = runCatching { GlanceAppWidgetManager(context).getGlanceIds(MoneyWidget::class.java).isNotEmpty() }.getOrDefault(false)
            if (!placed) return
            runCatching {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(MoneyWidget::class.java)
                val now = Today.millis()
                ids.forEach { id -> androidx.glance.appwidget.state.updateAppWidgetState(context, id) { it[STAMP] = now }; MoneyWidget().update(context, id) }
            }
        }
    }
}

class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoneyWidget()
}

@Composable
private fun MoneyContent(line: MoneyWidget.Line) {
    val short = LocalSize.current.height < 90.dp
    fun won(v: Long) = if (line.hidden) Fmt.MASK + "원" else Fmt.won(v)
    val main = line.perDay?.let { won(it) } ?: "통장 잔액이나 예산을 적으면 보여요"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            if (!short) Text("오늘 쓸 돈", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = wsp(12)), maxLines = 1)
            Text(
                if (short && line.perDay != null) "오늘 쓸 돈 $main" else main,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (line.perDay == null) wsp(13) else if (short) wsp(14) else wsp(18),
                    fontWeight = if (line.perDay == null) FontWeight.Normal else FontWeight.Bold),
                maxLines = if (short) 1 else 2,
            )
            if (!short && line.spent > 0) {
                Spacer(GlanceModifier.height(2.dp))
                Text("오늘 쓴 돈 ${won(line.spent)}", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = wsp(12)), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Box(
            GlanceModifier.height(WIDGET_BUTTON_HEIGHT).background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (short) "＋ 적기" else "＋ 돈 적기",
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = wsp(14), fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}
