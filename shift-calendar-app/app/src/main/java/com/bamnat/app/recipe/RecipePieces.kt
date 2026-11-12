package com.bamnat.app.recipe

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.db.PatternEntity
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.app.invest.invest
import com.bamnat.app.pay.PayCodec
import com.bamnat.core.alarm.AlarmMission
import com.bamnat.core.memo.MemoTemplate
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftIcon
import com.bamnat.core.model.ShiftType
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.FieldMode
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.BreakPay
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WonRounding
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.recipe.WEEK_DAYS
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * [RCP-08..16][RCP-25][RCP-26] The nine pieces on this phone: what a piece holds now ([export], for 만들기), what putting one on
 * would change ([preview]), putting it on with what it replaced kept ([apply]) and putting that back ([restore]).
 *
 * Rules every piece keeps (설계서 S3·4-3): a recipe never touches records (근무 기록, 가계부, 메모); it adds alarms and never takes
 * one away or turns a mission off; amounts come only from what the receiver filled in; categories and templates are added,
 * never removed. [restore] puts a value back only while it is still what the recipe set, so a change the user made since stays.
 */
class RecipePieces(private val c: AppContainer) {

    /** One line of what a piece changes; [skipped] lines say what it could not use here. */
    /** [weak] 2.0.0 [RVM-22] RCP-05: it makes the alarm weaker than the phone's now (someone else's work asks before it goes in). */
    data class Change(val text: String, val skipped: Boolean = false, val weak: Boolean = false)

    /** 2.0.0 [RVM-22] RCP-05 The phone's alarm now, to tell what a received 알람 묶음 would make weaker. */
    fun alarmNow(): com.bamnat.core.recipe.AlarmStrength.Now = c.settingsRepository.current.let {
        com.bamnat.core.recipe.AlarmStrength.Now(it.ringMinutes, it.vibrate, it.gradualVolume)
    }

    // ---- 1.39 [NTC-03] 겹치는 알림: 넣기 전에 '기존 설정을 바꿀까요?' ----

    /** The app's own notices that are on in [s]. */
    fun noticesOn(s: com.bamnat.app.data.AppSettings = c.settingsRepository.current): Set<com.bamnat.core.formula.NoticeOverlap.BuiltIn> = buildSet {
        if (s.todayShiftNotice) add(com.bamnat.core.formula.NoticeOverlap.BuiltIn.TODAY)
        if (s.dayCloseNotice) add(com.bamnat.core.formula.NoticeOverlap.BuiltIn.DAY_CLOSE)
    }

    /**
     * 1.39 [NTC-03] (사용자 2026-11-08 "부품 기능을 넣을때 기존의 설정을 바꿀까요?"): the 알림 rules of a 규칙·단추 piece ([values]) that say
     * what an app notice that is on, or a rule of mine that is on, says already. [Mode.REPLACE] turns those off as it goes in
     * (되돌리기 turns them back on); [Mode.BOTH] keeps them, a 오늘 근무 one folded into the lock-screen notice.
     */
    fun overlaps(values: Map<String, Any?>, cur: com.bamnat.app.data.AppSettings = c.settingsRepository.current): List<com.bamnat.core.formula.NoticeOverlap.Hit> {
        val store = com.bamnat.app.logic.LogicStore(c.appContext)
        return com.bamnat.core.formula.NoticeOverlap.check(com.bamnat.core.formula.Logic.decode(cur.logic),
            com.bamnat.core.formula.Logic.from(values).received(), noticesOn(cur)) { !store.isOff(it.key) }
    }

    /** How a piece meets what the user has: put the recipe's on, keep both (where a piece can), or leave it out. */
    enum class Mode { REPLACE, BOTH, SKIP }

    // ---- export (만들기 1단계: 지금 내 설정에서 가져오기) ----

    /** What [kind] holds now on calendar [cal], in the piece's own fields; null when there is nothing to share. */
    suspend fun export(kind: PieceKind, cal: String, from: com.bamnat.app.data.AppSettings = c.settingsRepository.current): Map<String, Any?>? = when (kind) {
        PieceKind.SHIFT_LOOK -> types(cal).takeIf { it.isNotEmpty() }?.take(12)?.let { list ->
            mapOf("items" to list.map { t ->
                // 2.0.0 [RVM-29] SN-16: name and 근무 글자 cut at a letter by ShiftRules (the piece's own check), never half an emoji.
                mapOf("name" to com.bamnat.core.model.ShiftRules.fitName(t.name), "role" to role(t).key,
                    "label" to com.bamnat.core.model.ShiftRules.fitLabel(t.label).ifEmpty { com.bamnat.core.model.ShiftRules.cut(t.name.trim(), 1, com.bamnat.core.model.ShiftRules.LABEL_UNITS) },
                    "color" to (t.colorArgb or 0xFF000000L), "emoji" to ShiftIcon.emoji(t.icon))
            })
        }
        PieceKind.CAL_THEME -> CalendarTheme.decode(from.calTheme).let { t ->
            // 1.36 PC 재검토 ('근무 색 꽉 채우기'로 꾸몄는데 '옅게 칠하기'로 보여요): with 칸 디자인 the cells are drawn by 달력 모양's
            // looks, so the old 칸 칠하기 is not shared (it would read wrong and do nothing).
            val looks = com.bamnat.core.recipe.CalendarView.decode(from.calView).looks.isNotEmpty()
            mapOf("today" to t.today, "sat" to t.sat, "sun" to t.sun, "holiday" to t.holiday, "cell" to (t.cell ?: "tint").takeUnless { looks })
                .takeIf { m -> m.values.any { it != null } }
        }
        PieceKind.ALARMS -> {
            val s = from
            val byRole = types(cal).filter { !it.isOff && it.alarmEnabled }.groupBy { role(it) }.map { (r, list) ->
                mapOf("role" to r.key, "before" to list.flatMap { t -> t.alarms.filter { it.enabled }.map { it.offsetMinutes.toLong() } }
                    .distinct().sortedDescending().take(4))
            }.filter { (it["before"] as List<*>).isNotEmpty() }.take(6)
            mapOf("byRole" to byRole, "snooze" to s.snoozeMinutes.toLong(), "ring" to s.ringMinutes.toLong(), "gradual" to s.gradualVolume,
                "vibrate" to s.vibrate, "mission" to s.alarmMission.takeIf { it in setOf("none", "math", "shake") })
        }
        PieceKind.MEMO_TEMPLATES -> c.memoTemplates.items.value.filter { it.from == null }.take(8).takeIf { it.isNotEmpty() }?.let { list ->
            mapOf("items" to list.map { t ->
                mapOf("title" to t.title.take(30), "emoji" to t.emoji, "check" to t.check.take(20).map { it.take(60) },
                    "body" to t.body.take(300).ifEmpty { null }, "repeat" to t.repeat.ifEmpty { null },
                    // [CVW-10]
                    "asks" to t.asks.take(8).map { a -> mapOf("label" to a.label.take(20), "type" to a.type, "choices" to a.choices.take(8).map { it.take(16) }) }.ifEmpty { null })
            })
        }
        PieceKind.HOURLY_RULES -> c.scopeOf(cal).pay.profile.value?.let { p ->
            // The 시급 goes out only as 받는 사람이 정할 칸 (만들기 2단계 turns it into one first).
            mapOf("wage" to p.base.on(Today.date()).value.takeIf { it > 0 && p.wageType == com.bamnat.core.pay.WageType.HOURLY },
                "five" to p.fiveOrMoreEmployees, "weeklyPay" to p.rules.weeklyHolidayPay, "weekStart" to p.holidayWeekFrom.name,
                "breakPay" to p.rules.breakPay.name, "legalBreaks" to p.legalBreaks,
                "night" to p.rules.nightPremium?.let { Math.round(it * 100) }, "partTime" to p.rules.partTimePremium,
                "rounding" to p.rules.wonRounding.name.takeIf { n -> n in ROUNDINGS }, "tax" to p.taxMode.name)
        }
        PieceKind.FIELD_SHEET -> {
            val f = c.calendars.get(cal)?.field ?: com.bamnat.core.money.FieldRules()
            val cats = c.scopeOf(cal).money.categories.first()
            mapOf("mode" to (f.mode ?: FieldMode.PER_JOB).name, "dayRate" to f.dayRate.takeIf { it > 0 }, "chips" to f.chips.take(5),
                "ends" to f.unitRules.take(6).map { mapOf("end" to "%02d:%02d".format(it.until.hour, it.until.minute), "units" to it.units) },
                "kinds" to MoneyCategories.of(MoneyKind.INCOME, cats, com.bamnat.core.work.WorkStyle.FIELD).take(12),
                "costs" to MoneyCategories.of(MoneyKind.EXPENSE, cats, com.bamnat.core.work.WorkStyle.FIELD).take(12))
        }
        PieceKind.SHOP -> {
            val cal0 = c.calendars.get(cal)
            val r = cal0?.shop ?: com.bamnat.core.work.ShopRules()
            val m = cal0?.shopMoney ?: com.bamnat.core.work.ShopMoney()
            val cats = c.scopeOf(cal).money.categories.first()
            mapOf("open" to WEEK_DAYS.filter { DayOfWeek.valueOf(it) !in r.closedWeekdays }, "opens" to clock(r.open), "closes" to clock(r.close),
                "holidaysClosed" to r.holidaysClosed, "cardFee" to m.cardFeeBp / 100.0, "settleDays" to m.settleDays.toLong(),
                "simpleTax" to m.simpleTax, "valueAdded" to m.valueAddedPercent.toLong(),
                "closing" to (c.memoTemplates.items.value.firstOrNull { it.title == CLOSING_TITLE }?.check ?: emptyList<String>()),
                "costs" to MoneyCategories.of(MoneyKind.EXPENSE, cats, com.bamnat.core.work.WorkStyle.BUSINESS).take(20))
        }
        PieceKind.INVEST -> {
            val s = c.invest(cal).book.first().settings
            mapOf("rules" to s.principles.filter { it.length >= 2 }.take(10).map { mapOf("text" to it.take(80)) },
                "journal" to (c.memoTemplates.items.value.firstOrNull { it.title == JOURNAL_TITLE }?.check ?: emptyList<String>()))
        }
        PieceKind.COMPANY -> if (cal != PersonalCalendar.MAIN) null else
            runCatching { mapOf("setup" to c.companyShare.export("", withAmounts = false)) }.getOrNull()
        // [RCX-11] A bundle is made of codes on 만들기, not taken from the phone.
        PieceKind.BUNDLE -> null
        // 1.37 [LEDH-18] 가계부 틀: names and plans only, never a line, a balance or an account.
        PieceKind.LEDGER -> {
            val money = c.scopeOf(cal).money
            val cats = money.categories.first()
            val style = c.scopeOf(cal).settings.first().workStyle
            val budgets = money.allBudgets().filter { !it.isTotal && it.amount > 0 }.take(12).map { mapOf("category" to it.category.take(20), "amount" to it.amount) }
            val envelopes = money.envelopes.first().first.filter { !it.closed && it.kind == com.bamnat.core.money.EnvelopeKind.GOAL }.take(6).map { e ->
                mapOf("name" to e.name.take(20), "target" to e.target.takeIf { it > 0 }, "share" to e.sharePercent.takeIf { it > 0 }?.toLong(), "emoji" to e.emoji.ifEmpty { null })
            }
            // fc374a23 PC 확인 (내가 더한 야식·헬스장이 안 담기고 기본 20개만): the ones I added go first, then the rest in my order.
            fun mineFirst(kind: MoneyKind, max: Int): List<String> {
                val all = MoneyCategories.of(kind, cats, style)
                val base = MoneyCategories.of(kind, style).toSet()
                return (all.filter { it !in base } + all.filter { it in base }).take(max)
            }
            mapOf("costs" to mineFirst(MoneyKind.EXPENSE, 20), "incomes" to mineFirst(MoneyKind.INCOME, 12),
                "budgets" to budgets, "envelopes" to envelopes)
        }
        PieceKind.LAYOUT -> from.let { s ->
            mapOf("tabs" to com.bamnat.core.layout.TabOrder.of(s.tabOrder), "calendar" to s.calendarLayout.ifEmpty { null })
        }
        PieceKind.CARDS -> from.let { s ->
            mapOf("stats" to s.statsLayout.ifEmpty { null }, "money" to s.moneyLayout.ifEmpty { null }, "memo" to s.memoLayout.ifEmpty { null })
                .takeIf { m -> m.values.any { it != null } }
        }
        PieceKind.WIDGET -> from.let { s ->
            val w = com.bamnat.core.recipe.WidgetLook.decode(s.widgetLook)
            mapOf("alpha" to w.alpha.toLong(), "theme" to w.theme, "memo" to s.widgetMemo,
                "bg" to w.bg, "ink" to w.ink, "corner" to w.corner?.toLong(), "text" to w.text)
        }
        // [CVW-01] 1.35 달력 모양 with the cells' own switches.
        PieceKind.CAL_VIEW -> from.let { s ->
            val v = com.bamnat.core.recipe.CalendarView.decode(s.calView)
            mapOf("size" to v.size, "badge" to v.badge, "date" to v.date, "text" to v.text, "grid" to v.grid,
                "memo" to (v.memo ?: if (s.memoInCells) null else 0)?.toLong(), "hours" to s.showHoursInCells, "lunar" to s.showLunar, "spend" to s.spendInCells,
                "order" to v.order?.joinToString(","),
                "looks" to v.looks.takeIf { it.isNotEmpty() }?.let { com.bamnat.core.recipe.CellLook.encodeAll(it) },
                "pattern" to v.pattern.takeIf { !it.isDefault }?.encode(), "today" to v.todayLook?.toLong())
        }
        // [FREE-04] 1.35 메모장 모양.
        PieceKind.MEMO_LOOK -> com.bamnat.core.recipe.MemoLook.decode(from.memoLook).takeIf { !it.isDefault }?.let { l ->
            mapOf("layout" to l.layout, "card" to l.card, "cardColor" to l.cardColor, "border" to l.border, "corner" to l.corner?.toLong(),
                "lines" to l.lines?.toLong(), "title" to l.title, "bold" to l.bold, "font" to l.font, "date" to l.date, "bg" to l.bg)
        }
        // [FREE-06] 1.35 가계부 모양.
        PieceKind.MONEY_LOOK -> com.bamnat.core.recipe.MoneyLook.decode(from.moneyLook).takeIf { !it.isDefault }?.let { l ->
            mapOf("rows" to l.rows, "amount" to l.amount, "mark" to l.mark, "income" to l.income, "expense" to l.expense, "dayHead" to l.dayHead)
        }
        // [FREE-05][FREE-07] 1.35 화면 색·배경 and 급여 모양; a picture the phone does not have is left out.
        PieceKind.SCREEN_LOOK -> from.let { s ->
            val ctx = c.appContext
            val looks = com.bamnat.core.recipe.ScreenLooks.decode(s.screenLooks).let { all ->
                com.bamnat.core.recipe.ScreenLooks(all.looks.mapValues { (_, l) -> if (l.photo != null && !LookImages.has(ctx, l.photo)) l.copy(photo = null) else l })
            }
            mapOf("screens" to looks.encode().ifEmpty { null }, "pay" to s.payLooks.ifEmpty { null }).takeIf { m -> m.values.any { it != null } }
        }
        // [BLU-01] 1.36 부품 꾸미기: the blueprint's lines as they are.
        PieceKind.PARTS -> com.bamnat.core.recipe.Blueprint.decode(from.parts).takeIf { !it.isEmpty }?.let { mapOf("parts" to it.sorted()) }
        // [RUL-03][RUL-04] 1.36 수식·규칙·버튼.
        PieceKind.LOGIC -> com.bamnat.core.formula.Logic.decode(from.logic).toValues().takeIf { it.isNotEmpty() }
        // [TAB-07] 1.36 탭 보기.
        PieceKind.TAB_VIEW -> com.bamnat.core.layout.TabViews.decode(from.tabViews).takeIf { !it.isDefault }?.let { v ->
            mapOf("cal" to v.cal.key, "memo" to v.memo.view, "memoBy" to v.memo.by, "memoSort" to v.memo.sort.ifEmpty { null }, "money" to v.money.key)
        }
        // [CVW-07] 1.35 앱 동작.
        PieceKind.APP_FEEL -> from.let { s ->
            mapOf("theme" to THEMES.entries.first { it.value == s.themeMode }.key, "dynamic" to s.dynamicColor,
                "accent" to s.accentColor.takeIf { it != 0L }, "textStep" to s.textStep.toLong(), "compact" to s.compact, "clock12" to s.clock12,
                "tap" to s.phoneDayView.name.lowercase(), "hold" to s.longPress, "dayOrder" to s.dayGroupOrder.joinToString(",").ifEmpty { null },
                "flow" to s.sleepTips, "rest" to s.restCheck, "payPeriod" to (s.statsPeriod == com.bamnat.app.data.StatsPeriod.PAY).takeIf { it }, "motion" to s.motion.ifEmpty { null })
        }
        // [LAY-01] 1.36 꾸밈 층.
        PieceKind.DECOR -> com.bamnat.core.recipe.Decor.decode(from.decor).takeIf { !it.isDefault }?.values()
        // [RCX-30] The phone's own picture ids; they become the server's when the recipe is sent ([LookImages.upload]).
        PieceKind.PHOTO_LOOK -> com.bamnat.core.recipe.PhotoLook.decode(from.photoLook).takeIf { !it.isDefault }?.let { l ->
            val ctx = c.appContext
            val mine = types(cal).associateBy { it.id }
            val icons = com.bamnat.core.model.ShiftRef.of(l.icons, cal).mapNotNull { (t, id) -> mine[t]?.let { role(it).key to id } }.filter { LookImages.has(ctx, it.second) }
                .distinctBy { it.first }.take(6).map { (r, id) -> mapOf("role" to r, "image" to id) }
            mapOf("bg" to l.bg?.takeIf { LookImages.has(ctx, it) }, "dim" to l.dim.toLong(), "head" to l.head?.takeIf { LookImages.has(ctx, it) },
                "widget" to l.widget?.takeIf { LookImages.has(ctx, it) }, "corners" to l.corners, "icons" to icons.ifEmpty { null },
                "adjust" to l.adjust.takeIf { !it.isDefault }?.encode(), "frame" to l.frame)
        }
    }

    // ---- preview ----

    /** What putting [values] (inputs already filled in) on calendar [cal] changes, line by line. */
    suspend fun preview(kind: PieceKind, values: Map<String, Any?>, cal: String): List<Change> = when (kind) {
        PieceKind.SHIFT_LOOK -> {
            val mine = types(cal)
            pairs(mine, items(values, "items")).map { (i, t) ->
                if (t == null) Change("‘${i["name"]}’: 맞는 근무가 없어 건너뛰어요", skipped = true)
                else {
                    // PC 확인 보안 7차 ("글자 주→주, 색 바뀜"): only what changes ("주간: 색만 바뀜"), the same letter not said as a change.
                    val label = (i["label"] as? String)?.takeIf { it != t.label }
                    val color = (i["color"] as? Number)?.toLong()?.let { it or 0xFF000000L }?.takeIf { it != (t.colorArgb or 0xFF000000L) }
                    val emoji = (i["emoji"] as? String)?.takeIf { it.isNotEmpty() && it != ShiftIcon.emoji(t.icon) }
                    val parts = listOfNotNull(label?.let { "글자 ${t.label}→$it" }, color?.let { "색 바뀜" }, emoji?.let { "이모지 $it" })
                    Change("${t.name}: " + when {
                        parts.isEmpty() -> "지금과 같아요"
                        parts == listOf("색 바뀜") -> "색만 바뀜"
                        else -> parts.joinToString(", ")
                    }, skipped = parts.isEmpty())
                }
            }
        }
        PieceKind.CAL_THEME -> listOf(Change(listOfNotNull(
            "today".takeIf { values[it] != null }?.let { "오늘" }, "sat".takeIf { values[it] != null }?.let { "토요일" },
            "sun".takeIf { values[it] != null }?.let { "일요일" }, "holiday".takeIf { values[it] != null }?.let { "공휴일" },
        ).joinToString("·").ifEmpty { "글자색 그대로" } + " · 칸 " + cellLabel(values["cell"] as? String)))
        PieceKind.ALARMS -> {
            val mine = types(cal).filter { !it.isOff }
            val lines = items(values, "byRole").mapNotNull { r ->
                val role = ShiftRole.of(r["role"] as? String)
                val add = nums(r["before"])
                val targets = mine.filter { role(it) == role }
                if (targets.isEmpty()) Change("${role.label}: 맞는 근무가 없어 건너뛰어요", skipped = true)
                else targets.map { t -> t to add.filter { m -> t.alarms.none { it.offsetMinutes == m } } }.filter { it.second.isNotEmpty() }
                    .takeIf { it.isNotEmpty() }?.let { list -> Change(list.joinToString(", ") { (t, a) -> "${t.name} ${a.joinToString("·") { "${it}분 전" }} 알람 더하기" }) }
            }
            val mission = (values["mission"] as? String)?.takeIf { it != "none" }
            // 2.0.0 [RVM-22] RCP-05 (진동·점점 크게도 바꾸는데 요약 줄에 없었어요): every alarm setting the piece changes, as 지금 → 바뀐 값;
            // one it leaves as it is says nothing.
            val cur = c.settingsRepository.current
            val weak = com.bamnat.core.recipe.AlarmStrength.weaker(values, alarmNow()).map { it.key }.toSet()
            lines + listOfNotNull(
                (values["snooze"] as? Number)?.toInt()?.takeIf { it != cur.snoozeMinutes }?.let { Change("다시 울림 ${cur.snoozeMinutes}분 → ${it}분") },
                (values["ring"] as? Number)?.toInt()?.takeIf { it != cur.ringMinutes }?.let { Change("울림 길이 ${cur.ringMinutes}분 → ${it}분", weak = "ring" in weak) },
                (values["gradual"] as? Boolean)?.takeIf { it != cur.gradualVolume }?.let {
                    Change(if (it) "알람이 작게 시작해 점점 커져요" else "알람이 처음부터 큰 소리로 울려요(점점 크게 끔)", weak = "gradual" in weak)
                },
                (values["vibrate"] as? Boolean)?.takeIf { it != cur.vibrate }?.let { Change(if (it) "알람에 진동도 울려요" else "알람 진동을 꺼요", weak = "vibrate" in weak) },
                mission?.let { Change("끄기 미션: ${AlarmMission.of(it).label}") },
                (values["mission"] == "none").takeIf { it }?.let { Change("미션은 지금 것을 그대로 둬요", skipped = true) },
            )
        }
        PieceKind.MEMO_TEMPLATES -> items(values, "items").map { Change("메모 틀 ‘${it["title"]}’ 더하기") }
        PieceKind.HOURLY_RULES -> {
            val p = c.scopeOf(cal).pay.profile.value
            if (p == null) listOf(Change("급여 설정이 아직 없어 건너뛰어요. 급여 탭에서 먼저 정해 주세요", skipped = true))
            else listOfNotNull(
                payPair(values, cal)?.let { (a, b) ->
                    Change("이번 달 급여 예상 ${com.bamnat.app.ui.Fmt.won(a)} → ${com.bamnat.app.ui.Fmt.won(b)} (" +
                        (if (b >= a) "+" else "−") + com.bamnat.app.ui.Fmt.won(kotlin.math.abs(b - a)) + ")")
                },
                values["wage"]?.let { Change("시급 ${com.bamnat.app.ui.Fmt.won((it as Number).toLong())} (오늘부터)") },
                values["weeklyPay"]?.let { Change("주휴수당 " + if (it == true) "받음" else "없음") },
                values["five"]?.let { Change("5인 이상 " + if (it == true) "예" else "아니오") },
                values["breakPay"]?.let { Change("휴게시간 " + BreakPay.valueOf(it as String).label) },
                values["night"]?.let { Change("야간 가산 ${(it as Number).toInt()}%") },
                values["tax"]?.let { Change("세금 " + taxLabel(it as String)) },
            )
        }
        PieceKind.FIELD_SHEET -> listOfNotNull(
            (values["mode"] as? String)?.let { Change("세는 방식: " + FieldMode.valueOf(it).label) },
            values["dayRate"]?.let { Change("일당 ${com.bamnat.app.ui.Fmt.won((it as Number).toLong())}") },
            nums(values["chips"]).takeIf { it.isNotEmpty() }?.let { Change("공수 칩 ${(values["chips"] as List<*>).joinToString("·")}") },
            texts(values, "kinds").takeIf { it.isNotEmpty() }?.let { Change("작업 분류 ${it.size}개 더하기") },
            texts(values, "costs").takeIf { it.isNotEmpty() }?.let { Change("경비 분류 ${it.size}개 더하기") },
        )
        PieceKind.SHOP -> listOfNotNull(
            (values["open"] as? List<*>)?.let { Change("영업일 " + it.joinToString("") { d -> dayShort(d as String) }) },
            values["opens"]?.let { Change("영업 ${it}~${values["closes"] ?: ""}") },
            values["cardFee"]?.let { Change("카드 수수료 ${it}%") },
            texts(values, "closing").takeIf { it.isNotEmpty() }?.let { Change("메모 틀 ‘$CLOSING_TITLE’ (${it.size}개) 더하기") },
            texts(values, "costs").takeIf { it.isNotEmpty() }?.let { Change("경비 분류 ${it.size}개 더하기") },
        )
        PieceKind.INVEST -> listOfNotNull(
            items(values, "rules").takeIf { it.isNotEmpty() }?.let { Change("투자 원칙 ${it.size}개 더하기") },
            texts(values, "journal").takeIf { it.isNotEmpty() }?.let { Change("메모 틀 ‘$JOURNAL_TITLE’ 더하기") },
        )
        PieceKind.COMPANY -> if (cal != PersonalCalendar.MAIN) listOf(Change("회사 설정은 ‘내 달력’에만 넣을 수 있어요", skipped = true))
            else listOf(Change(runCatching { c.companyShare.summarize(values["setup"] as String).let { s ->
                // 1.37 [SHR-32] A 근무표 링크's 근무 주기 has no pay part: it says so.
                "근무 ${s.shiftTypes.size}가지와 순서" + if (s.hasRules || s.items.isNotEmpty()) ", 급여 규칙" else "" } }
                .getOrDefault("근무 주기와 급여 규칙")))
        PieceKind.BUNDLE -> listOf(Change("묶음 안 작품 ${items(values, "items").size}개는 아래에서 하나씩 열어 골라 넣어요", skipped = true))
        PieceKind.LEDGER -> listOfNotNull(
            texts(values, "costs").takeIf { it.isNotEmpty() }?.let { Change("지출 분류 ${it.size}개 (없는 것만 더하기)") },
            texts(values, "incomes").takeIf { it.isNotEmpty() }?.let { Change("수입 분류 ${it.size}개 (없는 것만 더하기)") },
            items(values, "budgets").takeIf { it.isNotEmpty() }?.let { b -> Change("예산 ${b.size}개: " + b.joinToString(", ") { "${it["category"]}" + ((it["amount"] as? Number)?.let { a -> " " + com.bamnat.app.ui.Fmt.won(a.toLong()) } ?: " (금액 없이 분류만)") }) },
            items(values, "envelopes").takeIf { it.isNotEmpty() }?.let { e -> Change("빈 모으기 봉투 ${e.size}개: " + e.joinToString(", ") { it["name"].toString() }) },
        ).ifEmpty { listOf(Change("바뀌는 것이 없어요", skipped = true)) }
        // [RCX-30] Said from the recipe itself: its pictures come down only when it is put in.
        PieceKind.PHOTO_LOOK -> {
            val mine = types(cal)
            listOfNotNull(
                values["bg"]?.let { Change("달력 배경 그림" + ((values["dim"] as? Number)?.let { d -> " (흐리게 ${d}%)" } ?: "")) },
                values["head"]?.let { Change("달력 위 머리 그림") },
                items(values, "icons").takeIf { it.isNotEmpty() }?.let { l ->
                    val n = mine.count { t -> l.any { (it["role"] as? String) == role(t).key } }
                    if (n == 0) Change("근무 그림: 맞는 근무가 없어 건너뛰어요", skipped = true) else Change("근무 그림 ${n}개 (같은 종류의 내 근무에)")
                },
                values["widget"]?.let { Change("위젯 배경 그림") },
                (values["corners"] as? String)?.let { Change(com.bamnat.core.recipe.RecipeDescribe.CORNER_NAMES[it] ?: "모서리") },
            ).ifEmpty { listOf(Change("바뀌는 것이 없어요", skipped = true)) }
        }
        // [RUL-04] 켜기 전 한 줄 요약 (사용성 제안): each button and rule as it will act; one that writes data says so first.
        PieceKind.LOGIC -> com.bamnat.core.formula.Logic.from(values).let { raw ->
            val l = raw.received()
            // [RVM-23] STU-23: a step after the first that writes (more·otherwise) is marked too, not the first step alone.
            (l.buttons.map { b -> val w = b.acts.any { it.verb.writes }; Change((if (w) "⚠ " else "") + com.bamnat.core.formula.FormulaText.blanks(b.say())) } +
                l.rules.map { r -> val w = r.acts.any { it.verb.writes }; Change((if (w) "⚠ " else "") + "규칙 ‘${r.name}’: " + com.bamnat.core.formula.FormulaText.blanks(r.say()) + if (w) " (꺼진 채로 들어와요)" else "") } +
                // 1.36 [SEC-12] 보안 점검 M4: said before 넣기.
                listOfNotNull(
                    Change("메모·가계부를 바꾸는 규칙은 꺼진 채로 들어와요. 설정 › 알람 › 수식·규칙·버튼에서 하나씩 켜요").takeIf { l.writingRules.isNotEmpty() },
                    Change("받은 규칙·단추는 가계부에 한 번에 100만 원까지만 넣어요").takeIf { raw.overReceived },
                    // PC D6: 내 규칙 뒤에 더해지고, 같은 것은 하나로 둬요 (말없이 합쳐지지 않게).
                    Change("내 규칙·단추는 그대로 두고 뒤에 더해요"),
                    com.bamnat.core.formula.Logic.decode(c.settingsRepository.current.logic).mergeSay(l)?.let { Change(it) },
                ) +
                    // 1.39 [FX-25] 넣기 전 부딪힘: 같은 때 같은 가계부·값 칸, 뜻이 다른 같은 이름 값 칸, 같은 자리 같은 이름 단추.
                    com.bamnat.core.formula.LogicClash.check(com.bamnat.core.formula.Logic.decode(c.settingsRepository.current.logic), l).map { Change("⚠ $it") })
                .ifEmpty { listOf(Change("바뀌는 것이 없어요", skipped = true)) }
        }
        PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.CAL_VIEW, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK, PieceKind.MONEY_LOOK,
        PieceKind.SCREEN_LOOK, PieceKind.PARTS, PieceKind.DECOR, PieceKind.TAB_VIEW -> {
            val changes = settingsAfter(kind, values, cal).filter { (k, v) -> settingOf(c.settingsRepository.current, k) != v }.map { (k, _) -> Change(SETTING_NAMES[k] ?: k) }
            // PC 확인 C9 1.36 (확인 전 사진이라는 말이 없어 넣은 뒤 회색 화면에 놀람 / 넣은 뒤 '바뀌는 것이 없어요'라 헷갈림): the photos this
            // phone does not have yet, said before and after it is put in.
            val away = if (kind != PieceKind.DECOR) 0 else com.bamnat.core.recipe.Decor.of(values).images.count { !LookImages.has(c.appContext, it) }
            val photos = if (away == 0) null
                else if (changes.isEmpty()) Change("넣었어요 · 꾸밈 사진 ${away}장은 운영자가 확인하면 보여요")
                else Change("꾸밈 사진 ${away}장 · 넣을 때 받아 와요. 운영자 확인 전이면 그동안 기본 바탕이에요")
            (changes + listOfNotNull(photos)).ifEmpty { listOf(Change("지금과 같아서 바뀌는 것이 없어요", skipped = true)) }
        }
    }

    // ---- 전후 (1.39 [MKP-05]) ----

    /**
     * 1.39 [MKP-05] 기능 조각 전후 (사용자 2026-11-07 "기능 조각들도 어떠한지 전후를 알 수 있으면"): one thing a piece touches, as this phone
     * has it [now] and as it would be once put in ([then]). A colour is shown as a swatch beside its words. Rows that stay as they are
     * are kept too (dimmed on the screen), so what does not change is as plain as what does.
     */
    data class Side(val what: String, val now: String, val then: String, val nowColor: Long? = null, val thenColor: Long? = null) {
        val changed: Boolean get() = now != then || nowColor != thenColor
    }

    /** 1.39 [MKP-05] [kind] with [values] (inputs filled in) on calendar [cal], 지금 ↔ 넣으면, row by row; empty where a piece has none. */
    suspend fun compare(kind: PieceKind, values: Map<String, Any?>, cal: String, mode: Mode = Mode.REPLACE): List<Side> =
        runCatching { compareOf(kind, values, cal, mode) }.getOrDefault(emptyList())

    private suspend fun compareOf(kind: PieceKind, values: Map<String, Any?>, cal: String, mode: Mode): List<Side> = when (kind) {
        PieceKind.SHIFT_LOOK -> lookPairs(values, cal).map { (a, b) ->
            fun say(t: ShiftType) = "글자 ${t.label}" + (ShiftIcon.emoji(t.icon)?.let { " $it" } ?: "")
            Side(a.name, say(a), say(b), a.colorArgb or 0xFF000000L, b.colorArgb or 0xFF000000L)
        }
        PieceKind.CAL_THEME -> {
            val t = CalendarTheme.decode(c.settingsRepository.current.calTheme)
            listOf("today" to "오늘", "sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일").mapNotNull { (k, name) ->
                val v = (values[k] as? Number)?.toLong() ?: return@mapNotNull null
                val now = when (k) { "today" -> t.today; "sat" -> t.sat; "sun" -> t.sun; else -> t.holiday }
                Side("$name 글자색", if (now == null) "앱 기본" else "", "", now?.let { it or 0xFF000000L }, v or 0xFF000000L)
            } + listOfNotNull((values["cell"] as? String)?.let { Side("날짜 칸 칠하기", cellLabel(t.cell ?: "tint"), cellLabel(it)) })
        }
        PieceKind.ALARMS -> {
            val cur = c.settingsRepository.current
            val mine = types(cal).filter { !it.isOff }
            fun say(list: List<Int>) = if (list.isEmpty()) "없음" else list.sortedDescending().joinToString("·") { minutesBefore(it) }
            // Each of my shifts once, with what every role line of the piece adds to it (two shifts of one name are two rows).
            val adds = LinkedHashMap<Long, Pair<ShiftType, MutableSet<Int>>>()
            for (r in items(values, "byRole")) {
                val role = ShiftRole.of(r["role"] as? String)
                for (t in mine.filter { role(it) == role }) adds.getOrPut(t.id) { t to mutableSetOf() }.second += nums(r["before"])
            }
            val rows = adds.values.map { (t, add) ->
                val had = t.alarms.map { it.offsetMinutes }
                val now = if (!t.alarmEnabled && had.isNotEmpty()) "꺼짐 (${say(had)})" else say(had)
                Side("${t.name} 알람", now, say((had + add.filter { it !in had }).distinct()))
            }
            fun onOff(b: Boolean) = if (b) "켬" else "끔"
            rows + listOfNotNull(
                (values["snooze"] as? Number)?.toInt()?.let { Side("다시 울림", "${cur.snoozeMinutes}분", "${it}분") },
                (values["ring"] as? Number)?.toInt()?.let { Side("울림 길이", "${cur.ringMinutes}분", "${it}분") },
                (values["gradual"] as? Boolean)?.let { Side("점점 크게", onOff(cur.gradualVolume), onOff(it)) },
                (values["vibrate"] as? Boolean)?.let { Side("진동", onOff(cur.vibrate), onOff(it)) },
                (values["mission"] as? String)?.takeIf { it != "none" }?.let {
                    Side("끄기 미션", AlarmMission.of(cur.alarmMission).label, AlarmMission.of(it).label)
                },
            )
        }
        PieceKind.MEMO_TEMPLATES -> {
            val mine = c.memoTemplates.items.value
            val titles = items(values, "items").mapNotNull { (it["title"] as? String)?.takeIf { t -> t.isNotBlank() } }.distinct()
            val same = titles.filter { t -> mine.any { it.title == t } }
            val after = mine.size + titles.size - if (mode == Mode.REPLACE) mine.count { it.title in same } else 0
            listOf(Side("메모 틀", "${mine.size}개", "${after}개")) + titles.take(MAX_SIDE_ROWS).map { t ->
                val had = t in same
                Side("‘$t’", if (had) "내 틀 있음" else "없음", when { !had -> "새로 생겨요"; mode == Mode.REPLACE -> "이 작품 틀로 바뀌어요"; else -> "둘 다 있어요" })
            }
        }
        PieceKind.HOURLY_RULES -> {
            val p = c.scopeOf(cal).pay.profile.value
            if (p == null) emptyList() else {
                val a = hourlyAfter(p, values)
                val today = Today.date()
                fun yes(b: Boolean, y: String = "예", n: String = "아니오") = if (b) y else n
                fun night(x: Double?) = "${Math.round((x ?: 0.5) * 100)}%"
                listOfNotNull(
                    payPair(values, cal)?.let { (x, y) -> Side("이번 달 예상 급여 (세전)", com.bamnat.app.ui.Fmt.won(x), com.bamnat.app.ui.Fmt.won(y)) },
                    values["wage"]?.takeIf { p.wageType == com.bamnat.core.pay.WageType.HOURLY }?.let {
                        Side("시급 (오늘부터)", com.bamnat.app.ui.Fmt.won(p.base.on(today).value), com.bamnat.app.ui.Fmt.won(a.base.on(today).value))
                    },
                    values["weeklyPay"]?.let { Side("주휴수당", yes(p.rules.weeklyHolidayPay, "받음", "없음"), yes(a.rules.weeklyHolidayPay, "받음", "없음")) },
                    values["five"]?.let { Side("5인 이상 사업장", yes(p.fiveOrMoreEmployees), yes(a.fiveOrMoreEmployees)) },
                    values["breakPay"]?.let { Side("휴게시간", p.rules.breakPay.label, a.rules.breakPay.label) },
                    values["night"]?.let { Side("야간 가산", night(p.rules.nightPremium), night(a.rules.nightPremium)) },
                    values["partTime"]?.let { Side("단시간 가산", yes(p.rules.partTimePremium), yes(a.rules.partTimePremium)) },
                    values["rounding"]?.let { Side("원 단위 맺기", p.rules.wonRounding.label, a.rules.wonRounding.label) },
                    values["weekStart"]?.let { Side("한 주 시작", weekDay(p.holidayWeekStart), weekDay(a.holidayWeekStart)) },
                    values["tax"]?.let { Side("세금", taxLabel(p.taxMode.name), taxLabel(a.taxMode.name)) },
                )
            }
        }
        PieceKind.FIELD_SHEET -> {
            val f = c.calendars.get(cal)?.field ?: com.bamnat.core.money.FieldRules()
            fun chips(l: List<Double>) = if (l.isEmpty()) "기본" else l.joinToString("·") { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
            listOfNotNull(
                (values["mode"] as? String)?.let { Side("세는 방식", f.mode?.label ?: "정하지 않음", FieldMode.valueOf(it).label) },
                (values["dayRate"] as? Number)?.toLong()?.let { Side("일당", if (f.dayRate > 0) com.bamnat.app.ui.Fmt.won(f.dayRate) else "없음", com.bamnat.app.ui.Fmt.won(it)) },
                (values["chips"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }?.takeIf { it.isNotEmpty() }?.let { Side("공수 칩", chips(f.unitChips), chips(it)) },
            ) + categorySides(cal, MoneyKind.INCOME, texts(values, "kinds"), com.bamnat.core.work.WorkStyle.FIELD, "작업 분류") +
                categorySides(cal, MoneyKind.EXPENSE, texts(values, "costs"), com.bamnat.core.work.WorkStyle.FIELD, "경비 분류")
        }
        PieceKind.SHOP -> {
            val cal0 = c.calendars.get(cal)
            val r0 = cal0?.shop ?: com.bamnat.core.work.ShopRules()
            val m0 = cal0?.shopMoney ?: com.bamnat.core.work.ShopMoney()
            fun open(closed: Set<DayOfWeek>) = DayOfWeek.entries.filter { it !in closed }.joinToString("") { dayShort(it.name) }.ifEmpty { "없음" }
            listOfNotNull(
                (values["open"] as? List<*>)?.let { o -> Side("영업일", open(r0.closedWeekdays), open(DayOfWeek.entries.filter { it.name !in o.map(Any?::toString) }.toSet())) },
                (values["opens"] as? String)?.let { Side("여는 시각", clock(r0.open), it) },
                (values["closes"] as? String)?.let { Side("닫는 시각", clock(r0.close), it) },
                (values["holidaysClosed"] as? Boolean)?.let { Side("공휴일", if (r0.holidaysClosed) "쉬어요" else "열어요", if (it) "쉬어요" else "열어요") },
                (values["cardFee"] as? Number)?.let { Side("카드 수수료", "%.2f%%".format(m0.cardFeeBp / 100.0).replace(".00%", "%"), "${it}%") },
            ) + memoSides(texts(values, "closing").isNotEmpty(), CLOSING_TITLE) +
                categorySides(cal, MoneyKind.EXPENSE, texts(values, "costs"), com.bamnat.core.work.WorkStyle.BUSINESS, "경비 분류")
        }
        PieceKind.INVEST -> {
            val before = c.invest(cal).book.first().settings.principles
            val add = items(values, "rules").mapNotNull { it["text"] as? String }.filter { it !in before }
            listOfNotNull(Side("투자 원칙", "${before.size}개", "${(before.size + add.size).coerceAtMost(PRINCIPLES_MAX)}개").takeIf { add.isNotEmpty() || before.isNotEmpty() }) +
                add.take(MAX_SIDE_ROWS).map { Side("‘${it.take(24)}’", "없음", "새로 생겨요") } + memoSides(texts(values, "journal").isNotEmpty(), JOURNAL_TITLE)
        }
        PieceKind.LEDGER -> {
            val money = c.scopeOf(cal).money
            val style = c.scopeOf(cal).settings.first().workStyle ?: com.bamnat.core.work.WorkStyle.SHIFT
            val had = money.allBudgets().associate { it.category to it.amount }
            val budgets = items(values, "budgets").mapNotNull { b ->
                val name = (b["category"] as? String)?.let(MoneyCategories::clean)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val amount = (b["amount"] as? Number)?.toLong() ?: return@mapNotNull null
                Side("‘$name’ 예산", had[name]?.takeIf { it > 0 }?.let { com.bamnat.app.ui.Fmt.won(it) } ?: "없음", com.bamnat.app.ui.Fmt.won(amount))
            }
            val open = money.envelopes.first().first.filter { !it.closed }.map { it.name }.toSet()
            val envs = items(values, "envelopes").mapNotNull { (it["name"] as? String)?.trim()?.takeIf { n -> n.isNotEmpty() } }
            categorySides(cal, MoneyKind.EXPENSE, texts(values, "costs"), style, "지출 분류") +
                categorySides(cal, MoneyKind.INCOME, texts(values, "incomes"), style, "수입 분류") + budgets.take(MAX_SIDE_ROWS) +
                listOfNotNull(Side("모으기 봉투", "${open.size}개", "${open.size + envs.count { it !in open }}개").takeIf { envs.isNotEmpty() })
        }
        PieceKind.LOGIC -> {
            val mine = com.bamnat.core.formula.Logic.decode(c.settingsRepository.current.logic)
            val l = com.bamnat.core.formula.Logic.from(values).received()
            val after = mine.plus(l)
            listOfNotNull(
                Side("규칙", "${mine.rules.size}개", "${after.rules.size}개").takeIf { l.rules.isNotEmpty() },
                Side("단추", "${mine.buttons.size}개", "${after.buttons.size}개").takeIf { l.buttons.isNotEmpty() },
            ) + overlaps(values).map { h ->
                // 1.39 [NTC-03] 겹치는 알림이 넣은 뒤 어떻게 되는지.
                val what = h.builtIn?.let { "${it.label} 알림" } ?: "내 ‘${h.other?.name}’ 규칙"
                Side(what, "켬", when {
                    mode == Mode.REPLACE -> "끔(‘${h.rule.name}’으로)"
                    h.builtIn == com.bamnat.core.formula.NoticeOverlap.BuiltIn.TODAY -> "켬 · 알림 하나로 합쳐요"
                    else -> "켬 · 둘 다 와요"
                })
            }
        }
        PieceKind.COMPANY -> if (cal != PersonalCalendar.MAIN) emptyList() else {
            val now = types(cal)
            val s = runCatching { c.companyShare.summarize(values["setup"] as String) }.getOrNull()
            if (s == null) emptyList() else listOfNotNull(
                Side("근무 종류", now.joinToString("·") { it.name }.ifEmpty { "없음" }.take(40), s.shiftTypes.joinToString("·").take(40)),
                Side("급여 규칙", if (c.payStore.profile.value != null) "내 설정" else "없음", "이 작품 회사 규칙").takeIf { s.hasRules || s.items.isNotEmpty() },
            )
        }
        PieceKind.PHOTO_LOOK -> {
            val l = com.bamnat.core.recipe.PhotoLook.decode(c.settingsRepository.current.photoLook)
            fun has(b: Boolean) = if (b) "내 그림" else "없음"
            listOfNotNull(
                values["bg"]?.let { Side("달력 배경 그림", has(l.bg != null), "작품 그림") },
                values["head"]?.let { Side("달력 위 머리 그림", has(l.head != null), "작품 그림") },
                values["widget"]?.let { Side("위젯 배경 그림", has(l.widget != null), "작품 그림") },
                items(values, "icons").takeIf { it.isNotEmpty() }?.let { Side("근무 그림", if (l.icons.isEmpty()) "없음" else "${l.icons.size}개", "작품 그림 ${it.size}개") },
            )
        }
        // 모양만 바꾸는 조각: which settings move, the picture itself is in 입어 보기.
        PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.CAL_VIEW, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK, PieceKind.MONEY_LOOK,
        PieceKind.SCREEN_LOOK, PieceKind.PARTS, PieceKind.DECOR, PieceKind.TAB_VIEW -> {
            val cur = c.settingsRepository.current
            settingsAfter(kind, values, cal).map { (k, v) -> Side(SETTING_NAMES[k] ?: k, "지금 모양", if (settingOf(cur, k) == v) "지금 모양 그대로" else "작품 모양") }
        }
        PieceKind.BUNDLE -> emptyList()
    }

    /** Categories a piece adds: how many [kind] categories there are now and after, and the new ones by name. */
    private suspend fun categorySides(cal: String, kind: MoneyKind, names: List<String>, style: com.bamnat.core.work.WorkStyle, what: String): List<Side> {
        if (names.isEmpty()) return emptyList()
        val shown = MoneyCategories.of(kind, c.scopeOf(cal).money.categories.first(), style)
        val add = names.map(MoneyCategories::clean).filter { it.isNotEmpty() && it !in shown }.distinct()
        val more = if (add.isEmpty()) "" else " · " + add.take(4).joinToString("·") + if (add.size > 4) " 외 ${add.size - 4}개" else ""
        return listOf(Side(what, "${shown.size}개", "${shown.size + add.size}개$more"))
    }

    /** A memo template a piece adds by its fixed title (마감 점검·매매 일지). */
    private fun memoSides(adds: Boolean, title: String): List<Side> = if (!adds) emptyList() else {
        val had = c.memoTemplates.items.value.any { it.title == title }
        listOf(Side("메모 틀 ‘$title’", if (had) "내 틀 있음" else "없음", if (had) "하나 더 생겨요" else "새로 생겨요"))
    }

    private fun weekDay(d: DayOfWeek?) = d?.let { dayShort(it.name) + "요일" } ?: "앱 기본"

    private fun minutesBefore(m: Int) = when {
        m >= 60 && m % 60 == 0 -> "${m / 60}시간 전"
        m >= 60 -> "${m / 60}시간 ${m % 60}분 전"
        else -> "${m}분 전"
    }

    // ---- apply ----

    /**
     * Puts [values] on calendar [cal]; returns what it replaced (for [restore]) or null when nothing changed. [code] marks what it
     * added (메모 틀) so [restore] takes exactly that away.
     */
    suspend fun apply(kind: PieceKind, values: Map<String, Any?>, cal: String, code: String, mode: Mode = Mode.REPLACE,
                      /** [TRY-06] Put on by [FunctionTrial] itself. */ trial: Boolean = false): JSONObject? {
        if (mode == Mode.SKIP) return null
        // 2.0.0 [RVS-12] A 부품 piece this app cannot read is said by 넣기 ([RecipeRepository.lastMissed]), not taken for 'nothing changed'.
        if (kind == PieceKind.PARTS) (values["parts"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.takeIf { it.isNotEmpty() }
            ?.let { com.bamnat.core.recipe.Blueprint.clean(it) }
        // [TRY-06] Anything put on for real ends a work's 기능 only being tried first (put back whole), so the two never mix.
        if (!trial && FunctionTrial.busy(c.appContext)) com.bamnat.app.ui.recipe.LiveTryOn.settle(c)
        // [TRY-05] A look put on for real ends a theme only being tried on, so what is put on shows.
        if (kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS) com.bamnat.app.ui.recipe.LiveTryOn.drop(c)
        return when (kind) {
            PieceKind.SHIFT_LOOK -> {
                val repo = c.scopeOf(cal).schedule
                val mine = types(cal)
                val list = JSONArray()
                for ((i, matched) in pairs(mine, items(values, "items"))) {
                    val t = matched ?: continue
                    val after = t.copy(label = (i["label"] as? String) ?: t.label, colorArgb = (i["color"] as? Long) ?: t.colorArgb,
                        icon = (i["emoji"] as? String)?.let(ShiftIcon::emojiKey) ?: t.icon)
                    if (after == t) continue
                    repo.saveShiftType(after)
                    list.put(JSONObject().put("id", t.id).put("label", t.label).put("color", t.colorArgb).put("icon", t.icon ?: JSONObject.NULL)
                        .put("aLabel", after.label).put("aColor", after.colorArgb).put("aIcon", after.icon ?: JSONObject.NULL))
                }
                list.takeIf { it.length() > 0 }?.let { JSONObject().put("types", it) }
            }
            PieceKind.CAL_THEME -> {
                val before = c.settingsRepository.current.calTheme
                val t = CalendarTheme.decode(before)
                val after = t.copy(today = values["today"] as? Long ?: t.today, sat = values["sat"] as? Long ?: t.sat,
                    sun = values["sun"] as? Long ?: t.sun, holiday = values["holiday"] as? Long ?: t.holiday,
                    cell = (values["cell"] as? String)?.takeIf { it != "tint" } ?: if (values["cell"] == "tint") null else t.cell).encode()
                if (after == before) null else {
                    c.settingsRepository.update { it.copy(calTheme = after) }
                    JSONObject().put("before", before).put("after", after)
                }
            }
            PieceKind.ALARMS -> {
                val repo = c.scopeOf(cal).schedule
                val mine = types(cal).filter { !it.isOff }
                val changed = JSONArray()
                for (r in items(values, "byRole")) {
                    val role = ShiftRole.of(r["role"] as? String)
                    for (t in mine.filter { role(it) == role }) {
                        val add = nums(r["before"]).filter { m -> t.alarms.none { it.offsetMinutes == m } }
                        if (add.isEmpty() && t.alarmEnabled) continue
                        repo.saveShiftType(t.copy(alarms = t.alarms + add.map { AlarmSpec(it) }, alarmEnabled = true))
                        changed.put(JSONObject().put("id", t.id).put("added", JSONArray(add)).put("wasOn", t.alarmEnabled))
                    }
                }
                val s = c.settingsRepository.current
                val sets = JSONObject()
                val mission = (values["mission"] as? String)?.takeIf { it != "none" }
                c.settingsRepository.update { cur ->
                    var n = cur
                    (values["snooze"] as? Number)?.toInt()?.let { v -> sets.put("snooze", JSONArray().put(cur.snoozeMinutes).put(v)); n = n.copy(snoozeMinutes = v) }
                    (values["ring"] as? Number)?.toInt()?.let { v -> sets.put("ring", JSONArray().put(cur.ringMinutes).put(v)); n = n.copy(ringMinutes = v) }
                    (values["gradual"] as? Boolean)?.let { v -> sets.put("gradual", JSONArray().put(cur.gradualVolume).put(v)); n = n.copy(gradualVolume = v) }
                    (values["vibrate"] as? Boolean)?.let { v -> sets.put("vibrate", JSONArray().put(cur.vibrate).put(v)); n = n.copy(vibrate = v) }
                    // A recipe can give a mission, never take one away (끄기 없음).
                    mission?.let { v -> sets.put("mission", JSONArray().put(cur.alarmMission).put(v)); n = n.copy(alarmMission = v) }
                    n
                }
                if (s == c.settingsRepository.current && changed.length() == 0) null
                else JSONObject().put("types", changed).put("settings", sets)
            }
            PieceKind.MEMO_TEMPLATES -> {
                val ids = JSONArray()
                // [RCX-23] 바꾸기: a template of mine with the same title goes (kept in the snapshot for 되돌리기); 둘 다 두기 keeps both.
                val titles = items(values, "items").map { (it["title"] as? String).orEmpty() }.toSet()
                val replaced = if (mode == Mode.REPLACE) c.memoTemplates.items.value.filter { it.title in titles && it.from != code } else emptyList()
                if (replaced.isNotEmpty()) c.memoTemplates.save(c.memoTemplates.items.value - replaced.toSet())
                items(values, "items").forEachIndexed { i, t ->
                    val id = "r$code$i"
                    c.memoTemplates.add(MemoTemplate(id, (t["title"] as? String).orEmpty(), t["emoji"] as? String,
                        (t["check"] as? List<*>)?.map { it.toString() }.orEmpty(), (t["body"] as? String).orEmpty(), (t["repeat"] as? String).orEmpty(), code,
                        asks = items(t, "asks").map { a -> com.bamnat.core.memo.TemplateAsk((a["label"] as? String).orEmpty(), (a["type"] as? String) ?: "text",
                            (a["choices"] as? List<*>)?.map { it.toString() }.orEmpty()) }))
                    ids.put(id)
                }
                JSONObject().put("templates", ids).put("replaced", com.bamnat.app.data.MemoTemplateStore.encode(replaced))
            }
            PieceKind.HOURLY_RULES -> {
                val store = c.scopeOf(cal).pay
                val p = store.profile.value ?: return null
                val after = hourlyAfter(p, values)
                if (after == p) null else {
                    store.save(after)
                    JSONObject().put("before", PayCodec.encode(p)).put("after", PayCodec.encode(after))
                }
            }
            PieceKind.FIELD_SHEET -> {
                val before = c.calendars.get(cal)?.field ?: com.bamnat.core.money.FieldRules()
                var f = before
                (values["mode"] as? String)?.let { f = f.copy(mode = FieldMode.valueOf(it)) }
                (values["dayRate"] as? Number)?.toLong()?.let { f = f.copy(dayRate = it) }
                nums(values["chips"]).takeIf { it.isNotEmpty() }?.let { f = f.copy(unitChips = (values["chips"] as List<*>).map { (it as Number).toDouble() }) }
                items(values, "ends").takeIf { it.isNotEmpty() }?.let { list ->
                    f = f.copy(unitRules = list.mapNotNull { e -> runCatching {
                        com.bamnat.core.money.DayRate.EndRule(LocalTime.parse(e["end"] as String), (e["units"] as Number).toDouble())
                    }.getOrNull() }.sortedBy { it.until })
                }
                if (f != before) c.calendars.updateField(cal) { f }
                val added = JSONObject()
                addCategories(cal, MoneyKind.INCOME, texts(values, "kinds"), com.bamnat.core.work.WorkStyle.FIELD)?.let { added.put("INCOME", it) }
                addCategories(cal, MoneyKind.EXPENSE, texts(values, "costs"), com.bamnat.core.work.WorkStyle.FIELD)?.let { added.put("EXPENSE", it) }
                JSONObject().put("field", fieldJson(before)).put("after", fieldJson(f)).put("categories", added)
            }
            PieceKind.SHOP -> {
                val cal0 = c.calendars.get(cal)
                val r0 = cal0?.shop ?: com.bamnat.core.work.ShopRules()
                val m0 = cal0?.shopMoney ?: com.bamnat.core.work.ShopMoney()
                var r = r0
                (values["open"] as? List<*>)?.let { open -> r = r.copy(closedWeekdays = DayOfWeek.entries.filter { it.name !in open }.toSet()) }
                (values["opens"] as? String)?.let { r = r.copy(open = LocalTime.parse(it)) }
                (values["closes"] as? String)?.let { r = r.copy(close = LocalTime.parse(it)) }
                (values["holidaysClosed"] as? Boolean)?.let { r = r.copy(holidaysClosed = it) }
                var m = m0
                (values["cardFee"] as? Number)?.let { m = m.copy(cardFeeBp = Math.round(it.toDouble() * 100).toInt()) }
                (values["settleDays"] as? Number)?.let { m = m.copy(settleDays = it.toInt()) }
                (values["simpleTax"] as? Boolean)?.let { m = m.copy(simpleTax = it) }
                (values["valueAdded"] as? Number)?.let { m = m.copy(valueAddedPercent = it.toInt()) }
                if (r != r0) c.calendars.updateShop(cal) { r }
                if (m != m0) c.calendars.updateShopMoney(cal) { m }
                val out = JSONObject().put("shop", shopJson(r0, m0)).put("after", shopJson(r, m))
                texts(values, "closing").takeIf { it.isNotEmpty() }?.let { list ->
                    val id = "r${code}closing"
                    c.memoTemplates.add(MemoTemplate(id, CLOSING_TITLE, "🧾", list, from = code))
                    out.put("templates", JSONArray().put(id))
                }
                addCategories(cal, MoneyKind.EXPENSE, texts(values, "costs"), com.bamnat.core.work.WorkStyle.BUSINESS)?.let {
                    out.put("categories", JSONObject().put("EXPENSE", it))
                }
                out
            }
            PieceKind.INVEST -> {
                val repo = c.invest(cal)
                val before = repo.book.first().settings.principles
                val add = items(values, "rules").mapNotNull { it["text"] as? String }.filter { it !in before }
                if (add.isNotEmpty()) repo.saveSettings { it.copy(principles = (it.principles + add).take(PRINCIPLES_MAX)) }
                val out = JSONObject().put("principles", JSONArray(add))
                texts(values, "journal").takeIf { it.isNotEmpty() }?.let { list ->
                    val id = "r${code}journal"
                    c.memoTemplates.add(MemoTemplate(id, JOURNAL_TITLE, "📒", list, from = code))
                    out.put("templates", JSONArray().put(id))
                }
                out
            }
            PieceKind.COMPANY -> {
                if (cal != PersonalCalendar.MAIN) return null
                val schedule = c.scheduleRepository
                val types = schedule.snapshot.first().shiftTypes
                val patterns = schedule.snapshot.first().patterns
                val profile = c.payStore.profile.value
                c.companyShare.apply(values["setup"] as String)
                val after = schedule.snapshot.first()
                val now = after.shiftTypes
                // 2.0.0 [RVM-44] 검토 RCP-03: what it left too, so 되돌리기 takes back only what it changed (not what the user changed since).
                JSONObject()
                    .put("types", JSONArray(types.map { t -> typeTimes(t) }))
                    .put("added", JSONArray(now.filter { n -> types.none { it.id == n.id } }.map { it.id }))
                    .put("patterns", JSONArray(patterns.map { p -> patternJson(p) }))
                    .put("profile", profile?.let(PayCodec::encode) ?: JSONObject.NULL)
                    .put("typesAfter", JSONArray(now.map { t -> typeTimes(t) }))
                    .put("patternsAfter", JSONArray(after.patterns.map { p -> patternJson(p) }))
                    .put("profileAfter", c.payStore.profile.value?.let(PayCodec::encode) ?: JSONObject.NULL)
            }
            PieceKind.BUNDLE -> null
            PieceKind.LEDGER -> {
                val money = c.scopeOf(cal).money
                val style = c.scopeOf(cal).settings.first().workStyle ?: com.bamnat.core.work.WorkStyle.SHIFT
                val out = JSONObject()
                val added = JSONObject()
                addCategories(cal, MoneyKind.EXPENSE, texts(values, "costs"), style)?.let { added.put("EXPENSE", it) }
                addCategories(cal, MoneyKind.INCOME, texts(values, "incomes"), style)?.let { added.put("INCOME", it) }
                if (added.length() > 0) out.put("categories", added)
                // A budget for a category the user does not have adds the category too, so it can be picked.
                val budgets = items(values, "budgets").mapNotNull { b ->
                    val name = (b["category"] as? String)?.let(MoneyCategories::clean)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    name to ((b["amount"] as? Number)?.toLong() ?: return@mapNotNull null)
                }
                addCategories(cal, MoneyKind.EXPENSE, budgets.map { it.first }, style)?.let { more ->
                    val all = added.optJSONArray("EXPENSE") ?: JSONArray()
                    for (i in 0 until more.length()) all.put(more.getString(i))
                    added.put("EXPENSE", all); out.put("categories", added)
                }
                val had = money.allBudgets().associate { it.category to it.amount }
                val set = JSONArray()
                budgets.forEach { (name, amount) ->
                    val before = had[name] ?: 0L
                    if (amount == before) return@forEach
                    money.setBudget(name, amount)
                    set.put(JSONObject().put("category", name).put("before", before).put("after", amount))
                }
                if (set.length() > 0) out.put("budgets", set)
                val (envs, _) = money.envelopes.first()
                val open = envs.filter { !it.closed }.map { it.name }.toSet()
                var order = (envs.maxOfOrNull { it.order } ?: -1) + 1
                val made = JSONArray()
                items(values, "envelopes").forEach { e ->
                    val name = (e["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it !in open } ?: return@forEach
                    val id = money.saveEnvelope(com.bamnat.core.money.Envelope(name = name, target = (e["target"] as? Number)?.toLong() ?: 0,
                        sharePercent = ((e["share"] as? Number)?.toInt() ?: 0).coerceIn(0, 100), emoji = (e["emoji"] as? String).orEmpty(),
                        order = order++, created = com.bamnat.core.time.Today.date()))
                    made.put(id)
                }
                if (made.length() > 0) out.put("envelopes", made)
                out.takeIf { it.length() > 0 }
            }
            PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.PHOTO_LOOK, PieceKind.CAL_VIEW, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK,
            PieceKind.MONEY_LOOK, PieceKind.SCREEN_LOOK, PieceKind.PARTS, PieceKind.LOGIC, PieceKind.DECOR, PieceKind.TAB_VIEW -> {
                // [RCX-30] The recipe's pictures come first; one the phone cannot get (not looked at yet) is left out.
                if (kind == PieceKind.PHOTO_LOOK || kind == PieceKind.SCREEN_LOOK || kind == PieceKind.DECOR) photoIds(values).forEach { LookImages.fetch(c, it) }
                // [MAT-06] 1.36 and the shared fonts it names (one taken down or not shared stays its stand-in).
                RecipeFonts.fetchIn(c, values.toString())
                val now = c.settingsRepository.current
                // 1.36 [SEC-12] 보안 점검 M4: someone else's buttons and rules come in with 가계부 amounts held to 100만 원.
                val vals = if (kind == PieceKind.LOGIC) com.bamnat.core.formula.Logic.from(values).received().toValues() else values
                // PC D7: one of theirs kept once because mine does the same shows its name on my line (all of it the same is no change).
                if (kind == PieceKind.LOGIC) com.bamnat.app.logic.LogicStore(c.appContext)
                    .noteSame(com.bamnat.core.formula.Logic.decode(now.logic), com.bamnat.core.formula.Logic.from(vals))
                val changes = settingsAfter(kind, vals, cal, mode = mode).filter { (k, v) -> settingOf(now, k) != v }
                // 1.39 [NTC-03] 바꾸기: my rules saying the same at the same time are switched off (the app's notices are in [changes]).
                val twins = if (kind == PieceKind.LOGIC && mode == Mode.REPLACE) overlaps(vals, now).mapNotNull { it.other?.key }.distinct() else emptyList()
                if (changes.isEmpty()) null else {
                    val out = JSONObject()
                    changes.forEach { (k, v) -> out.put(k, JSONArray().put(settingOf(now, k)).put(v)) }
                    c.settingsRepository.update { cur -> changes.entries.fold(cur) { acc, (k, v) -> withSetting(acc, k, v) } }
                    // ...and a rule of theirs that writes the memo or the 가계부 starts switched off (one that was here already keeps its switch).
                    if (kind == PieceKind.LOGIC) {
                        val had = com.bamnat.core.formula.Logic.decode(now.logic).rules.map { it.key }.toSet()
                        val store = com.bamnat.app.logic.LogicStore(c.appContext)
                        com.bamnat.core.formula.Logic.from(vals).writingRules.filter { it.key !in had }.forEach { store.gotOff(it.key) }
                        store.markGot(com.bamnat.core.formula.Logic.from(vals).rules.map { it.key }.filter { it !in had })
                        // 1.39 [FX-23] 작품마다 하루 써 보기: the new ones remember this work.
                        val hadB = com.bamnat.core.formula.Logic.decode(now.logic).buttons.map { it.id }.toSet()
                        val after = com.bamnat.core.formula.Logic.decode(c.settingsRepository.current.logic)
                        store.markFrom(code, after.rules.map { it.key }.filter { it !in had }, after.buttons.map { it.id }.filter { it !in hadB })
                        twins.forEach { store.setOff(it, true) }
                        com.bamnat.app.logic.LogicRunner.rulesChanged(c)
                    }
                    JSONObject().put("settings", out).apply { if (twins.isNotEmpty()) put("rulesOff", JSONArray(twins)) }
                }
            }
        }
    }

    /** [RCP-12] The pay setup with an 알바 근무지 규칙 piece on it (for [apply] and the pay line of [preview]). */
    fun hourlyAfter(p: com.bamnat.core.pay.PayProfile, values: Map<String, Any?>): com.bamnat.core.pay.PayProfile {
        var after = p
        // A 시급 goes only on an hourly pay setup; a monthly one keeps its 기본급.
        (values["wage"] as? Number)?.toLong()?.takeIf { p.wageType == com.bamnat.core.pay.WageType.HOURLY }?.let { w ->
            after = after.copy(base = ValueTimeline(p.base.entries.filter { it.from != Today.date() } + DatedValue(Today.date(), w), p.base.raise))
        }
        (values["five"] as? Boolean)?.let { after = after.copy(fiveOrMoreEmployees = it) }
        (values["legalBreaks"] as? Boolean)?.let { after = after.copy(legalBreaks = it) }
        (values["weekStart"] as? String)?.let { after = after.copy(holidayWeekStart = DayOfWeek.valueOf(it)) }
        (values["tax"] as? String)?.let { after = after.copy(taxMode = TaxMode.valueOf(it), taxChosen = true) }
        var rules = after.rules
        (values["weeklyPay"] as? Boolean)?.let { rules = rules.copy(weeklyHolidayPay = it) }
        (values["breakPay"] as? String)?.let { rules = rules.copy(breakPay = BreakPay.valueOf(it)) }
        (values["night"] as? Number)?.let { rules = rules.copy(nightPremium = it.toDouble() / 100.0) }
        (values["partTime"] as? Boolean)?.let { rules = rules.copy(partTimePremium = it) }
        (values["rounding"] as? String)?.let { rules = rules.copy(wonRounding = WonRounding.valueOf(it)) }
        after = after.copy(rules = rules)
        return after
    }

    /** [RCP-24] Each of my kinds of day a 근무 꾸미기 piece meets, as it is and as it would be. */
    suspend fun lookPairs(values: Map<String, Any?>, cal: String): List<Pair<ShiftType, ShiftType>> {
        val mine = types(cal)
        return pairs(mine, items(values, "items")).mapNotNull { (i, matched) ->
            val t = matched ?: return@mapNotNull null
            t to t.copy(label = (i["label"] as? String) ?: t.label, colorArgb = (i["color"] as? Long) ?: t.colorArgb,
                icon = (i["emoji"] as? String)?.let(ShiftIcon::emojiKey) ?: t.icon)
        }
    }

    /**
     * [RCX-03] 1.35 What this phone has now for a piece put on from a recipe, in the shape of the recipe's [base] values, so a new
     * version can be weighed against it field by field: 근무 꾸미기 reads each item's kind of day as it matched; alarms, 메모 틀,
     * 원칙 and a company file are things a recipe adds, so they count as untouched; the rest are read whole.
     */
    suspend fun current(kind: PieceKind, base: Map<String, Any?>, cal: String): Map<String, Any?> = when (kind) {
        PieceKind.SHIFT_LOOK -> {
            val mine = types(cal)
            base + ("items" to pairs(mine, items(base, "items")).map { (i, matched) ->
                val t = matched ?: return@map i
                i + mapOf("label" to t.label, "color" to (t.colorArgb or 0xFF000000L)) +
                    (ShiftIcon.emoji(t.icon)?.let { mapOf("emoji" to it) } ?: emptyMap())
            })
        }
        PieceKind.ALARMS, PieceKind.MEMO_TEMPLATES, PieceKind.INVEST, PieceKind.COMPANY, PieceKind.BUNDLE, PieceKind.LEDGER -> base
        else -> export(kind, cal)?.filterValues { it != null } ?: base
    }

    /** [RCP-24] This month's pay before and after an 알바 근무지 규칙 piece (세전, the app's own engine); null without a pay setup. */
    suspend fun payPair(values: Map<String, Any?>, cal: String): Pair<Long, Long>? {
        val scope = c.scopeOf(cal)
        val p = scope.pay.profile.value ?: return null
        val snapshot = scope.schedule.snapshot.first()
        val month = java.time.YearMonth.from(Today.date())
        val calc = com.bamnat.core.pay.PayCalculator()
        fun total(x: com.bamnat.core.pay.PayProfile) = runCatching { calc.calculate(x, month, snapshot.resolver::resolve, checks = false).total }.getOrNull()
        return (total(p) ?: return null) to (total(hourlyAfter(p, values)) ?: return null)
    }

    /** [RCX-23] 부딪힘: the titles of [values]' memo templates I already have one of (asked 바꾸기 / 둘 다 두기 / 건너뛰기). */
    fun memoClashes(values: Map<String, Any?>): List<String> {
        val mine = c.memoTemplates.items.value.map { it.title }.toSet()
        return items(values, "items").mapNotNull { (it["title"] as? String)?.takeIf { t -> t in mine } }.distinct()
    }

    // ---- restore (7일 되돌리기) ----

    /**
     * 2.0.0 [RVM-45] 검토 RCP-02: how many places the last [restore]s left as the user made them since (counted while
     * [RecipeRepository.undo] runs, so 되돌리기 can say "직접 바꾼 N곳은 그대로 뒀어요" rather than a plain 되돌렸어요).
     */
    @Volatile var kept: Int = 0

    /** Puts back what [apply] replaced; a value the user changed since is left as they made it. */
    suspend fun restore(kind: PieceKind, before: JSONObject, cal: String) {
        when (kind) {
            PieceKind.SHIFT_LOOK -> {
                val repo = c.scopeOf(cal).schedule
                val mine = types(cal).associateBy { it.id }
                before.optJSONArray("types")?.objects()?.forEach { o ->
                    val t = mine[o.getLong("id")] ?: return@forEach
                    var n = t
                    if (t.label == o.optString("aLabel")) n = n.copy(label = o.getString("label"))
                    if (t.colorArgb == o.optLong("aColor")) n = n.copy(colorArgb = o.getLong("color"))
                    if (t.icon == o.optStringOrNull("aIcon")) n = n.copy(icon = o.optStringOrNull("icon"))
                    if (n != t) repo.saveShiftType(n)
                }
            }
            PieceKind.CAL_THEME -> c.settingsRepository.update { s ->
                // 2.0.0 [RVM-45] RCP-02: colour by colour (a 일요일 colour put on later by another work stays).
                val back = SettingBack.of("calTheme", s.calTheme, before.optString("before"), before.optString("after"))
                kept += back.kept
                if (back.text != s.calTheme) s.copy(calTheme = back.text) else s
            }
            PieceKind.ALARMS -> {
                val repo = c.scopeOf(cal).schedule
                val mine = types(cal).associateBy { it.id }
                before.optJSONArray("types")?.objects()?.forEach { o ->
                    val t = mine[o.getLong("id")] ?: return@forEach
                    val added = o.optJSONArray("added")?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty().toSet()
                    repo.saveShiftType(t.copy(alarms = t.alarms.filter { it.offsetMinutes !in added },
                        alarmEnabled = if (o.optBoolean("wasOn")) t.alarmEnabled else false))
                }
                val sets = before.optJSONObject("settings") ?: JSONObject()
                c.settingsRepository.update { s ->
                    var n = s
                    sets.optJSONArray("snooze")?.let { if (s.snoozeMinutes == it.getInt(1)) n = n.copy(snoozeMinutes = it.getInt(0)) }
                    sets.optJSONArray("ring")?.let { if (s.ringMinutes == it.getInt(1)) n = n.copy(ringMinutes = it.getInt(0)) }
                    sets.optJSONArray("gradual")?.let { if (s.gradualVolume == it.getBoolean(1)) n = n.copy(gradualVolume = it.getBoolean(0)) }
                    sets.optJSONArray("vibrate")?.let { if (s.vibrate == it.getBoolean(1)) n = n.copy(vibrate = it.getBoolean(0)) }
                    sets.optJSONArray("mission")?.let { if (s.alarmMission == it.getString(1)) n = n.copy(alarmMission = it.getString(0)) }
                    n
                }
            }
            PieceKind.MEMO_TEMPLATES -> {
                removeTemplates(before)
                // [RCX-23] What 바꾸기 took away comes back (unless one with its id is there again).
                val back = before.optJSONArray("replaced")?.let { com.bamnat.app.data.MemoTemplateStore.decode(it) }.orEmpty()
                val have = c.memoTemplates.items.value.map { it.id }.toSet()
                back.filter { it.id !in have }.takeIf { it.isNotEmpty() }?.let { c.memoTemplates.save(c.memoTemplates.items.value + it) }
            }
            PieceKind.HOURLY_RULES -> {
                val store = c.scopeOf(cal).pay
                val now = store.profile.value ?: return
                val b = runCatching { PayCodec.decode(before.getJSONObject("before")) }.getOrNull() ?: return
                val a = runCatching { PayCodec.decode(before.getJSONObject("after")) }.getOrNull() ?: return
                // Field by field: what the user changed since stays.
                var n = now
                if (now.base == a.base) n = n.copy(base = b.base)
                if (now.fiveOrMoreEmployees == a.fiveOrMoreEmployees) n = n.copy(fiveOrMoreEmployees = b.fiveOrMoreEmployees)
                if (now.legalBreaks == a.legalBreaks) n = n.copy(legalBreaks = b.legalBreaks)
                if (now.holidayWeekStart == a.holidayWeekStart) n = n.copy(holidayWeekStart = b.holidayWeekStart)
                if (now.taxMode == a.taxMode) n = n.copy(taxMode = b.taxMode, taxChosen = b.taxChosen)
                var rules = n.rules
                if (now.rules.weeklyHolidayPay == a.rules.weeklyHolidayPay) rules = rules.copy(weeklyHolidayPay = b.rules.weeklyHolidayPay)
                if (now.rules.breakPay == a.rules.breakPay) rules = rules.copy(breakPay = b.rules.breakPay)
                if (now.rules.nightPremium == a.rules.nightPremium) rules = rules.copy(nightPremium = b.rules.nightPremium)
                if (now.rules.partTimePremium == a.rules.partTimePremium) rules = rules.copy(partTimePremium = b.rules.partTimePremium)
                if (now.rules.wonRounding == a.rules.wonRounding) rules = rules.copy(wonRounding = b.rules.wonRounding)
                n = n.copy(rules = rules)
                if (n != now) store.save(n)
            }
            PieceKind.FIELD_SHEET -> {
                val b = before.optJSONObject("field")
                val a = before.optJSONObject("after")
                if (b != null && a != null) c.calendars.updateField(cal) { f ->
                    var n = f
                    if (f.mode?.name == a.optStringOrNull("mode")) n = n.copy(mode = b.optStringOrNull("mode")?.let(FieldMode::valueOf))
                    if (f.dayRate == a.optLong("dayRate")) n = n.copy(dayRate = b.optLong("dayRate"))
                    if (JSONArray(f.unitChips).toString() == a.optJSONArray("chips")?.toString()) n = n.copy(unitChips = doubles(b.optJSONArray("chips")))
                    if (endsJson(f.unitRules).toString() == a.optJSONArray("ends")?.toString()) n = n.copy(unitRules = ends(b.optJSONArray("ends")))
                    n
                }
                removeCategories(cal, before.optJSONObject("categories"))
            }
            PieceKind.SHOP -> {
                val b = before.optJSONObject("shop")
                val a = before.optJSONObject("after")
                if (b != null && a != null) {
                    c.calendars.updateShop(cal) { r ->
                        var n = r
                        if (days(r.closedWeekdays) == a.optString("closed")) n = n.copy(closedWeekdays = daysFrom(b.optString("closed")))
                        if (clock(r.open) == a.optString("open")) n = n.copy(open = LocalTime.parse(b.getString("open")))
                        if (clock(r.close) == a.optString("close")) n = n.copy(close = LocalTime.parse(b.getString("close")))
                        if (r.holidaysClosed == a.optBoolean("holidaysClosed")) n = n.copy(holidaysClosed = b.optBoolean("holidaysClosed"))
                        n
                    }
                    c.calendars.updateShopMoney(cal) { m ->
                        var n = m
                        if (m.cardFeeBp == a.optInt("cardFeeBp")) n = n.copy(cardFeeBp = b.optInt("cardFeeBp"))
                        if (m.settleDays == a.optInt("settleDays")) n = n.copy(settleDays = b.optInt("settleDays"))
                        if (m.simpleTax == a.optBoolean("simpleTax")) n = n.copy(simpleTax = b.optBoolean("simpleTax"))
                        if (m.valueAddedPercent == a.optInt("valueAdded")) n = n.copy(valueAddedPercent = b.optInt("valueAdded"))
                        n
                    }
                }
                removeTemplates(before)
                removeCategories(cal, before.optJSONObject("categories"))
            }
            PieceKind.INVEST -> {
                val added = before.optJSONArray("principles")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet()
                if (added.isNotEmpty()) c.invest(cal).saveSettings { it.copy(principles = it.principles.filter { p -> p !in added }) }
                removeTemplates(before)
            }
            PieceKind.COMPANY -> {
                if (cal != PersonalCalendar.MAIN) return
                val schedule = c.scheduleRepository
                // 2.0.0 [RVM-44] 검토 RCP-03·T09b-05 (조건 10): only what this put on goes back. A work order, a shift's hours or a pay
                // setting the user changed since stays, and a shift it added stays while a day, a 대근 or a work order uses it (a 교대
                // worker's own 교육·대근 days are not wiped). A record from before 2.0.0 has no "…After" and is taken back as it was.
                before.optJSONArray("patterns")?.let { a ->
                    val after = before.optJSONArray("patternsAfter")
                    val now = JSONArray(schedule.snapshot.first().patterns.map { p -> patternJson(p) })
                    if (after == null || UndoMerge.same(now, after)) schedule.restorePatterns(a.objects().mapNotNull(::patternFrom))
                    else if (!UndoMerge.same(a, after)) kept++
                }
                val mine = schedule.snapshot.first().shiftTypes.associateBy { it.id }
                val typesAfter = before.optJSONArray("typesAfter")?.objects()?.associateBy { it.getLong("id") }
                before.optJSONArray("types")?.objects()?.forEach { o ->
                    val t = mine[o.getLong("id")] ?: return@forEach
                    val a = typesAfter?.get(t.id)
                    val cur = typeTimes(t)
                    fun untouched(key: String) = (a == null || UndoMerge.same(cur.opt(key), a.opt(key))).also { same ->
                        if (!same && !UndoMerge.same(o.opt(key), a?.opt(key))) kept++
                    }
                    var n = t
                    if (untouched("start")) n = n.copy(start = o.optStringOrNull("start")?.let(LocalTime::parse))
                    if (untouched("end")) n = n.copy(end = o.optStringOrNull("end")?.let(LocalTime::parse))
                    if (untouched("break")) n = n.copy(breakMinutes = o.optInt("break"))
                    if (untouched("breakStart")) n = n.copy(breakStart = o.optStringOrNull("breakStart")?.let(LocalTime::parse))
                    if (untouched("off")) n = n.copy(isOff = o.optBoolean("off"))
                    if (n != t) schedule.saveShiftType(n)
                }
                before.optJSONArray("added")?.let { a ->
                    (0 until a.length()).forEach { i ->
                        val id = a.getLong(i)
                        runCatching {
                            val t = mine[id] ?: return@runCatching
                            val changed = typesAfter?.get(id)?.let { !UndoMerge.same(typeTimes(t), it) } ?: false
                            if (typesAfter != null && (changed || schedule.shiftTypeUse(id).let { u -> u.days > 0 || u.inPattern || u.covers > 0 })) { kept++; return@runCatching }
                            schedule.deleteShiftTypeUndoable(id)
                        }
                    }
                }
                before.optJSONObject("profile")?.let { p ->
                    runCatching {
                        val a = before.optJSONObject("profileAfter")
                        val now = c.payStore.profile.value
                        if (a == null && !before.has("typesAfter")) c.payStore.save(PayCodec.decode(p))
                        else if (a != null && now != null) {
                            val back = UndoMerge.back(PayCodec.encode(now), p, a)
                            if (!UndoMerge.same(back, p)) kept++
                            PayCodec.decode(back).takeIf { it != now }?.let { c.payStore.save(it) }
                        }
                    }
                }
            }
            PieceKind.BUNDLE -> {}
            PieceKind.LEDGER -> {
                val money = c.scopeOf(cal).money
                // A budget the user changed after is theirs; an envelope money went into stays.
                val now = money.allBudgets().associate { it.category to it.amount }
                before.optJSONArray("budgets")?.objects()?.forEach { o ->
                    val name = o.getString("category")
                    if ((now[name] ?: 0L) == o.getLong("after")) money.setBudget(name, o.getLong("before"))
                }
                before.optJSONArray("envelopes")?.let { a ->
                    (0 until a.length()).forEach { i ->
                        val id = a.getLong(i)
                        val (e, moves) = money.envelopeWithMoves(id)
                        if (e != null && moves.isEmpty()) money.deleteEnvelope(id)
                    }
                }
                removeCategories(cal, before.optJSONObject("categories"))
            }
            PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.PHOTO_LOOK, PieceKind.CAL_VIEW, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK,
            PieceKind.MONEY_LOOK, PieceKind.SCREEN_LOOK, PieceKind.PARTS, PieceKind.LOGIC, PieceKind.DECOR, PieceKind.TAB_VIEW -> {
                // 1.39 [NTC-03] my rules switched off for this one come back on.
                before.optJSONArray("rulesOff")?.let { a ->
                    val store = com.bamnat.app.logic.LogicStore(c.appContext)
                    (0 until a.length()).forEach { store.setOff(a.getString(it), false) }
                    com.bamnat.app.logic.LogicRunner.rulesChanged(c)
                }
                val sets = before.optJSONObject("settings") ?: return
                // 2.0.0 [RVM-45] 검토 RCP-02 (조건 10): a setting went back only when its whole text was still as the work left it, so one
                // button of my own (or the widget's 투명도 alone) kept every rule and look the work put on, while 되돌리기 said 되돌렸어요.
                // Now part by part: the rules and buttons the work added (and nothing I made or changed), each 'key=value' of a look.
                var keptHere = 0
                c.settingsRepository.update { cur ->
                    keptHere = 0
                    sets.keys().asSequence().fold(cur) { acc, k ->
                        val pair = sets.getJSONArray(k)
                        val back = SettingBack.of(k, settingOf(acc, k), pair.getString(0), pair.getString(1))
                        keptHere += back.kept
                        if (back.text != settingOf(acc, k)) withSetting(acc, k, back.text) else acc
                    }
                }
                kept += keptHere
            }
        }
    }

    // ---- 1.35 settings pieces (화면 배치·카드·위젯 모양), each setting kept as a line of text ----

    /** The settings [kind] would leave, by key, as text. */
    private suspend fun settingsAfter(kind: PieceKind, values: Map<String, Any?>, cal: String,
                                      cur: com.bamnat.app.data.AppSettings = c.settingsRepository.current, mode: Mode = Mode.BOTH): Map<String, String> {
        return when (kind) {
            PieceKind.LAYOUT -> listOfNotNull(
                // 2.0.0 [RVS-09] 검토 SN-12(3): 탭 순서 is one's own (the first tab is where the app opens). A work always carries one, so its
                // usual order said nothing and put a phone's own order back to the usual; now only an order the maker changed is put on,
                // and the phone's own first tab stays first.
                texts(values, "tabs").takeIf { it.isNotEmpty() }?.let { com.bamnat.core.layout.TabOrder.of(it) }
                    ?.takeIf { it != com.bamnat.core.layout.TabOrder.DEFAULT }?.let { theirs ->
                        val home = com.bamnat.core.layout.TabOrder.home(cur.tabOrder)
                        "tabOrder" to com.bamnat.core.layout.TabOrder.toSave(com.bamnat.core.layout.TabOrder.move(theirs, home, 0)).joinToString(",")
                    },
                (values["calendar"] as? String)?.let { "calendarLayout" to it },
            ).toMap()
            PieceKind.CARDS -> listOfNotNull(
                (values["stats"] as? String)?.let { "statsLayout" to it },
                (values["money"] as? String)?.let { "moneyLayout" to it },
                (values["memo"] as? String)?.let { "memoLayout" to it },
            ).toMap()
            PieceKind.WIDGET -> {
                val w = com.bamnat.core.recipe.WidgetLook.decode(cur.widgetLook)
                val after = w.copy(alpha = (values["alpha"] as? Number)?.toInt()?.coerceIn(30, 100) ?: w.alpha,
                    theme = (values["theme"] as? String)?.takeIf { it in com.bamnat.core.recipe.WidgetLook.THEMES } ?: w.theme,
                    bg = (values["bg"] as? Long) ?: w.bg, ink = (values["ink"] as? Long) ?: w.ink,
                    corner = (values["corner"] as? Number)?.toInt()?.coerceIn(0, 28) ?: w.corner,
                    text = (values["text"] as? String)?.takeIf { it in com.bamnat.core.recipe.WidgetLook.TEXTS } ?: w.text)
                listOfNotNull("widgetLook" to after.encode(), (values["memo"] as? Boolean)?.let { "widgetMemo" to it.toString() }).toMap()
            }
            // [RCX-30] Each picture the phone has (or got) takes its place; a kind of day's picture goes to the user's kinds of day
            // of the same role. What the recipe leaves out stays as it is.
            PieceKind.PHOTO_LOOK -> {
                val ctx = c.appContext
                val l = com.bamnat.core.recipe.PhotoLook.decode(cur.photoLook)
                fun pic(k: String) = (values[k] as? String)?.takeIf { LookImages.has(ctx, it) }
                val byRole = items(values, "icons").mapNotNull { i -> (i["image"] as? String)?.takeIf { LookImages.has(ctx, it) }?.let { (i["role"] as? String) to it } }.toMap()
                val icons = if (byRole.isEmpty()) l.icons else l.icons + types(cal).mapNotNull { t -> byRole[role(t).key]?.let { com.bamnat.core.model.ShiftRef.key(cal, t.id) to it } }
                val after = l.copy(bg = pic("bg") ?: l.bg, head = pic("head") ?: l.head, widget = pic("widget") ?: l.widget,
                    dim = (values["dim"] as? Number)?.toInt()?.coerceIn(0, 80) ?: l.dim,
                    corners = (values["corners"] as? String)?.takeIf { it in com.bamnat.core.recipe.PhotoLook.CORNERS } ?: l.corners, icons = icons,
                    adjust = (values["adjust"] as? String)?.takeIf { com.bamnat.core.recipe.PhotoAdjust.problem(it) == null }
                        ?.let { com.bamnat.core.recipe.PhotoAdjust.decode(it) } ?: l.adjust,
                    frame = (values["frame"] as? String)?.takeIf { it.isNotEmpty() && com.bamnat.core.recipe.PhotoFrame.problem(it) == null } ?: l.frame)
                mapOf("photoLook" to after.encode())
            }
            // [CVW-01] What the piece names takes its place; what it leaves out stays as it is.
            PieceKind.CAL_VIEW -> {
                val v = com.bamnat.core.recipe.CalendarView.decode(cur.calView)
                fun pick(k: String, from: List<String>) = (values[k] as? String)?.takeIf { it in from }
                val memo = (values["memo"] as? Number)?.toInt()?.coerceIn(0, com.bamnat.core.recipe.CalendarView.MAX_MEMO)
                val after = v.copy(
                    size = pick("size", com.bamnat.core.recipe.CalendarView.SIZES) ?: v.size, badge = pick("badge", com.bamnat.core.recipe.CalendarView.BADGES) ?: v.badge,
                    date = pick("date", com.bamnat.core.recipe.CalendarView.DATES) ?: v.date, text = pick("text", com.bamnat.core.recipe.CalendarView.TEXTS) ?: v.text,
                    grid = pick("grid", com.bamnat.core.recipe.CalendarView.GRIDS) ?: v.grid, memo = memo ?: v.memo,
                    order = (values["order"].let { o -> (o as? List<*>)?.joinToString(",") ?: o as? String })?.takeIf { com.bamnat.core.recipe.CalendarView.orderProblem(it) == null && it.isNotEmpty() }?.split(',') ?: v.order,
                    looks = (values["looks"] as? String)?.takeIf { com.bamnat.core.recipe.CellLook.looksProblem(it) == null }
                        ?.let { com.bamnat.core.recipe.CellLook.decodeAll(it) } ?: v.looks,
                    pattern = (values["pattern"] as? String)?.takeIf { com.bamnat.core.recipe.CellPattern.problem(it) == null }
                        ?.let { com.bamnat.core.recipe.CellPattern.decode(it) } ?: v.pattern,
                    todayLook = (values["today"] as? Number)?.toInt()?.coerceIn(0, com.bamnat.core.recipe.CellLook.MAX - 1) ?: v.todayLook,
                )
                listOfNotNull(
                    "calView" to after.encode(),
                    memo?.let { "memoInCells" to (it > 0).toString() },
                    (values["hours"] as? Boolean)?.let { "showHoursInCells" to it.toString() },
                    (values["lunar"] as? Boolean)?.let { "showLunar" to it.toString() },
                    (values["spend"] as? Boolean)?.let { "spendInCells" to it.toString() },
                ).toMap()
            }
            // [CVW-07] Each choice the piece names; never the alarm's 끄기, 잠금, 동의 or 결제.
            PieceKind.APP_FEEL -> listOfNotNull(
                (values["theme"] as? String)?.let { THEMES[it] }?.let { "themeMode" to it.name },
                (values["dynamic"] as? Boolean)?.let { "dynamicColor" to it.toString() },
                (values["accent"] as? Long)?.let { "accentColor" to (it or 0xFF000000L).toString() },
                (values["textStep"] as? Number)?.let { "textStep" to it.toInt().coerceIn(0, 4).toString() },
                (values["compact"] as? Boolean)?.let { "compact" to it.toString() },
                (values["clock12"] as? Boolean)?.let { "clock12" to it.toString() },
                (values["tap"] as? String)?.takeIf { it in com.bamnat.core.recipe.AppFeel.TAPS }?.let { "phoneDayView" to it.uppercase() },
                (values["hold"] as? String)?.takeIf { it in com.bamnat.core.recipe.AppFeel.HOLDS }?.let { "longPress" to it },
                (values["dayOrder"] as? String)?.takeIf { com.bamnat.core.recipe.AppFeel.dayOrderProblem(it) == null }?.let { "dayGroupOrder" to it },
                (values["flow"] as? Boolean)?.let { "sleepTips" to it.toString() },
                (values["rest"] as? Boolean)?.let { "restCheck" to it.toString() },
                // 1.39 [PAYX-60] Off in an older recipe was only the default: it leaves the 급여 기간 to the pay.
                (values["payPeriod"] as? Boolean)?.let { "statsByPayPeriod" to (if (it) com.bamnat.app.data.StatsPeriod.PAY else com.bamnat.app.data.StatsPeriod.AUTO).name },
                (values["motion"] as? String)?.takeIf { com.bamnat.core.recipe.Motion.problem(it) == null }?.let { "motion" to it },
            ).toMap()
            // [FREE-04] What the piece names over the user's own.
            PieceKind.MEMO_LOOK -> {
                val l = com.bamnat.core.recipe.MemoLook.decode(cur.memoLook)
                fun pick(k: String, from: List<String>) = (values[k] as? String)?.takeIf { it in from }
                val after = l.copy(
                    layout = pick("layout", com.bamnat.core.recipe.MemoLook.LAYOUTS) ?: l.layout, card = pick("card", com.bamnat.core.recipe.MemoLook.CARDS) ?: l.card,
                    cardColor = (values["cardColor"] as? Long) ?: l.cardColor, border = pick("border", com.bamnat.core.recipe.MemoLook.BORDERS) ?: l.border,
                    corner = (values["corner"] as? Number)?.toInt()?.coerceIn(0, 24) ?: l.corner,
                    lines = (values["lines"] as? Number)?.toInt()?.coerceIn(1, com.bamnat.core.recipe.MemoLook.MAX_LINES) ?: l.lines,
                    title = pick("title", com.bamnat.core.recipe.MemoLook.TITLES) ?: l.title, bold = (values["bold"] as? Boolean) ?: l.bold,
                    font = (values["font"] as? String)?.takeIf { com.bamnat.core.recipe.FontCatalog.idProblem(it) == null } ?: l.font, date = (values["date"] as? Boolean) ?: l.date,
                    bg = (values["bg"] as? Long) ?: l.bg,
                )
                mapOf("memoLook" to after.encode())
            }
            // [FREE-06]
            PieceKind.MONEY_LOOK -> {
                val l = com.bamnat.core.recipe.MoneyLook.decode(cur.moneyLook)
                fun pick(k: String, from: List<String>) = (values[k] as? String)?.takeIf { it in from }
                val after = l.copy(
                    rows = pick("rows", com.bamnat.core.recipe.MoneyLook.ROWS) ?: l.rows, amount = pick("amount", com.bamnat.core.recipe.MoneyLook.AMOUNTS) ?: l.amount,
                    mark = pick("mark", com.bamnat.core.recipe.MoneyLook.MARKS) ?: l.mark, income = (values["income"] as? Long) ?: l.income,
                    expense = (values["expense"] as? Long) ?: l.expense, dayHead = pick("dayHead", com.bamnat.core.recipe.MoneyLook.DAY_HEADS) ?: l.dayHead,
                )
                mapOf("moneyLook" to after.encode())
            }
            // [FREE-05][FREE-07] Each screen the piece names takes the piece's look (a picture the phone could not get is left out);
            // the others stay the user's own.
            PieceKind.SCREEN_LOOK -> {
                val ctx = c.appContext
                val mine = com.bamnat.core.recipe.ScreenLooks.decode(cur.screenLooks)
                val theirs = (values["screens"] as? String)?.takeIf { com.bamnat.core.recipe.ScreenLooks.problem(it) == null }
                    ?.let { com.bamnat.core.recipe.ScreenLooks.decode(it) }
                val screens = theirs?.looks?.entries?.fold(mine) { acc, (k, l) ->
                    acc.with(k, if (l.photo != null && !LookImages.has(ctx, l.photo)) l.copy(photo = null) else l)
                }
                val pay = (values["pay"] as? String)?.takeIf { com.bamnat.core.recipe.PayLook.problem(it) == null }?.let { p ->
                    com.bamnat.core.recipe.PayLook.encodeAll(com.bamnat.core.recipe.PayLook.decodeAll(cur.payLooks) + com.bamnat.core.recipe.PayLook.decodeAll(p))
                }
                listOfNotNull(screens?.let { "screenLooks" to it.encode() }, pay?.let { "payLooks" to it }).toMap()
            }
            // [BLU-01] 1.36 부품 꾸미기: a recipe's parts are a whole look, so they take the place of mine (tokens already colours).
            PieceKind.PARTS -> (values["parts"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.let { l -> runCatching { com.bamnat.core.recipe.Blueprint(com.bamnat.core.recipe.Blueprint.clean(l)).encode() }.getOrNull() }
                ?.let { mapOf("parts" to it) }.orEmpty()
            // [RUL-03][RUL-04] The recipe's buttons and rules take the place of the ones there were (되돌리기 puts them back).
            // PC D6: they are added after mine (the same one kept once), as from the studio's 보관함; they never take mine away.
            PieceKind.LOGIC -> mapOf("logic" to com.bamnat.core.formula.Logic.encode(com.bamnat.core.formula.Logic.decode(cur.logic).plus(com.bamnat.core.formula.Logic.from(values)))) +
                // 1.39 [NTC-03] 바꾸기: the app notice it repeats goes off (되돌리기 turns it on again).
                (if (mode == Mode.REPLACE) overlaps(values, cur).mapNotNull { it.builtIn }.associate { it.key to "false" } else emptyMap())
            // [LAY-01] 꾸밈 층: the piece's decorations take the places (붙는 곳) it uses; mine on the other places stay.
            PieceKind.DECOR -> {
                val theirs = com.bamnat.core.recipe.Decor.of(values)
                val places = theirs.items.map { it.at }.toSet()
                val mine = com.bamnat.core.recipe.Decor.decode(cur.decor).items.filter { it.at !in places }
                mapOf("decor" to com.bamnat.core.recipe.Decor((mine + theirs.items).take(com.bamnat.core.recipe.Decor.MAX)).encode())
            }
            // [TAB-07] 1.36 탭 보기: each tab the piece names takes its view; the others stay the user's own.
            PieceKind.TAB_VIEW -> {
                val v = com.bamnat.core.layout.TabViews.decode(cur.tabViews)
                val memoView = (values["memo"] as? String)?.takeIf { it in com.bamnat.core.memo.MemoView.VIEWS }
                val after = v.copy(
                    cal = (values["cal"] as? String)?.takeIf { it in com.bamnat.core.layout.CalendarBoard.KEYS }?.let { com.bamnat.core.layout.CalendarBoard.of(it) } ?: v.cal,
                    memo = if (memoView == null) v.memo else com.bamnat.core.memo.MemoView(memoView,
                        (values["memoBy"] as? String)?.takeIf(com.bamnat.core.memo.MemoView::validBy) ?: com.bamnat.core.memo.MemoView.BY_TAG,
                        (values["memoSort"] as? String)?.takeIf(com.bamnat.core.memo.MemoView::validSort) ?: ""),
                    money = (values["money"] as? String)?.takeIf { it in com.bamnat.core.money.LedgerBoard.KEYS }?.let { com.bamnat.core.money.LedgerBoard.of(it) } ?: v.money,
                )
                mapOf("tabViews" to after.encode())
            }
            else -> emptyMap()
        }
    }

    private fun settingOf(s: com.bamnat.app.data.AppSettings, key: String): String = when (key) {
        "tabOrder" -> s.tabOrder.joinToString(",")
        "calendarLayout" -> s.calendarLayout
        "statsLayout" -> s.statsLayout
        "moneyLayout" -> s.moneyLayout
        "memoLayout" -> s.memoLayout
        "tabViews" -> s.tabViews
        "widgetLook" -> s.widgetLook
        "shiftLooks" -> s.shiftLooks
        "widgetMemo" -> s.widgetMemo.toString()
        "photoLook" -> s.photoLook
        "calView" -> s.calView
        "memoInCells" -> s.memoInCells.toString()
        "showHoursInCells" -> s.showHoursInCells.toString()
        "showLunar" -> s.showLunar.toString()
        "spendInCells" -> s.spendInCells.toString()
        "themeMode" -> s.themeMode.name
        "dynamicColor" -> s.dynamicColor.toString()
        "accentColor" -> s.accentColor.toString()
        "textStep" -> s.textStep.toString()
        "compact" -> s.compact.toString()
        "clock12" -> s.clock12.toString()
        "phoneDayView" -> s.phoneDayView.name
        "longPress" -> s.longPress
        "dayGroupOrder" -> s.dayGroupOrder.joinToString(",")
        "sleepTips" -> s.sleepTips.toString()
        "restCheck" -> s.restCheck.toString()
        "statsByPayPeriod" -> s.statsPeriod.name
        "motion" -> s.motion
        "memoLook" -> s.memoLook
        "moneyLook" -> s.moneyLook
        "screenLooks" -> s.screenLooks
        "payLooks" -> s.payLooks
        "calTheme" -> s.calTheme
        "parts" -> s.parts
        "logic" -> s.logic
        "decor" -> s.decor
        "todayShiftNotice" -> s.todayShiftNotice.toString()
        "dayCloseNotice" -> s.dayCloseNotice.toString()
        else -> ""
    }

    private fun withSetting(s: com.bamnat.app.data.AppSettings, key: String, v: String): com.bamnat.app.data.AppSettings = when (key) {
        "tabOrder" -> s.copy(tabOrder = v.split(',').filter { it.isNotEmpty() })
        "calendarLayout" -> s.copy(calendarLayout = v)
        "statsLayout" -> s.copy(statsLayout = v)
        "moneyLayout" -> s.copy(moneyLayout = v)
        "memoLayout" -> s.copy(memoLayout = v)
        "tabViews" -> s.copy(tabViews = v)
        "widgetLook" -> s.copy(widgetLook = v)
        "shiftLooks" -> s.copy(shiftLooks = v)
        "photoLook" -> s.copy(photoLook = v)
        "widgetMemo" -> s.copy(widgetMemo = v == "true")
        "calView" -> s.copy(calView = v)
        "memoInCells" -> s.copy(memoInCells = v == "true", calendarMemos = s.calendarMemos || v == "true")
        "showHoursInCells" -> s.copy(showHoursInCells = v == "true")
        "showLunar" -> s.copy(showLunar = v == "true")
        "spendInCells" -> s.copy(spendInCells = v == "true")
        "themeMode" -> runCatching { com.bamnat.app.data.ThemeMode.valueOf(v) }.getOrNull()?.let { s.copy(themeMode = it) } ?: s
        "dynamicColor" -> s.copy(dynamicColor = v == "true")
        "accentColor" -> s.copy(accentColor = v.toLongOrNull() ?: 0L)
        "textStep" -> s.copy(textStep = (v.toIntOrNull() ?: s.textStep).coerceIn(0, 4))
        "compact" -> s.copy(compact = v == "true")
        "clock12" -> s.copy(clock12 = v == "true")
        "phoneDayView" -> runCatching { com.bamnat.app.data.PhoneDayView.valueOf(v) }.getOrNull()?.let { s.copy(phoneDayView = it) } ?: s
        "longPress" -> s.copy(longPress = v.takeIf { it in com.bamnat.core.recipe.AppFeel.HOLDS } ?: s.longPress)
        "dayGroupOrder" -> s.copy(dayGroupOrder = v.split(',').filter { it.isNotEmpty() })
        "sleepTips" -> s.copy(sleepTips = v == "true")
        "restCheck" -> s.copy(restCheck = v == "true")
        "statsByPayPeriod" -> s.copy(statsPeriod = com.bamnat.app.data.StatsPeriod.read(v.takeIf { it != "true" && it != "false" }, v == "true"))
        "motion" -> s.copy(motion = v.takeIf { com.bamnat.core.recipe.Motion.problem(it) == null } ?: s.motion)
        "memoLook" -> s.copy(memoLook = v)
        "logic" -> s.copy(logic = v)
        "moneyLook" -> s.copy(moneyLook = v)
        "screenLooks" -> s.copy(screenLooks = v)
        "payLooks" -> s.copy(payLooks = v)
        "calTheme" -> s.copy(calTheme = v)
        "parts" -> s.copy(parts = v.takeIf { com.bamnat.core.recipe.Blueprint.problem(it) == null } ?: s.parts)
        "decor" -> s.copy(decor = v)
        "todayShiftNotice" -> s.copy(todayShiftNotice = v == "true")
        "dayCloseNotice" -> s.copy(dayCloseNotice = v == "true")
        else -> s
    }

    // ---- 1.35 [STU-10] 스튜디오 초안: a look kept in memory as settings, never the phone's until 입히기 ----

    /** The look pieces of [draft] (as [export] reads the phone's), for the example phone and for 입히기·저장·올리기. */
    suspend fun lookPieces(draft: com.bamnat.app.data.AppSettings, cal: String = PersonalCalendar.MAIN): List<com.bamnat.core.recipe.Piece> =
        com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS.mapNotNull { k ->
            runCatching { export(k, cal, draft) }.getOrNull()?.filterValues { it != null }?.takeIf { it.isNotEmpty() }?.let { com.bamnat.core.recipe.Piece(k, it) }
        }

    /** [draft] with one look piece's [values] on it, the way [apply] would put them on the phone; any other kind leaves it as it is. */
    suspend fun onDraft(draft: com.bamnat.app.data.AppSettings, kind: PieceKind, values: Map<String, Any?>, cal: String = PersonalCalendar.MAIN): com.bamnat.app.data.AppSettings =
        when (kind) {
            PieceKind.CAL_THEME -> {
                val t = CalendarTheme.decode(draft.calTheme)
                draft.copy(calTheme = t.copy(today = values["today"] as? Long ?: t.today, sat = values["sat"] as? Long ?: t.sat,
                    sun = values["sun"] as? Long ?: t.sun, holiday = values["holiday"] as? Long ?: t.holiday,
                    cell = (values["cell"] as? String)?.takeIf { it != "tint" } ?: if (values["cell"] == "tint") null else t.cell).encode())
            }
            PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.PHOTO_LOOK, PieceKind.CAL_VIEW, PieceKind.APP_FEEL, PieceKind.MEMO_LOOK,
            PieceKind.MONEY_LOOK, PieceKind.SCREEN_LOOK, PieceKind.PARTS, PieceKind.LOGIC, PieceKind.DECOR, PieceKind.TAB_VIEW ->
                settingsAfter(kind, values, cal, draft).entries.fold(draft) { acc, (k, v) -> withSetting(acc, k, v) }
            else -> draft
        }

    /** [draft] with the look of the phone's [LOOK_SETTINGS] put back to the app's own (처음 모양). */
    fun plainLook(draft: com.bamnat.app.data.AppSettings): com.bamnat.app.data.AppSettings {
        val plain = com.bamnat.app.data.AppSettings()
        return LOOK_SETTINGS.fold(draft) { acc, k -> withSetting(acc, k, settingOf(plain, k)) }
    }

    /** [draft]'s look as the settings [restoreLook] puts on the phone (스튜디오의 입히기). */
    fun lookOf(draft: com.bamnat.app.data.AppSettings): Map<String, String> = LOOK_SETTINGS.associateWith { settingOf(draft, it) }

    /** Whether two drafts look the same, and how many look settings differ. */
    fun lookDiff(a: com.bamnat.app.data.AppSettings, b: com.bamnat.app.data.AppSettings): Int = LOOK_SETTINGS.count { settingOf(a, it) != settingOf(b, it) }

    /** [FREE-10] The settings a theme can change ([com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS]); never a record, an alarm or a rule. */
    fun lookSnapshot(): Map<String, String> = LOOK_SETTINGS.associateWith { settingOf(c.settingsRepository.current, it) }

    /** [TRY-05] [s] with the look settings of [look] (as [lookOf] writes them) on it; nothing else changes. */
    fun withLook(s: com.bamnat.app.data.AppSettings, look: Map<String, String>): com.bamnat.app.data.AppSettings =
        look.entries.filter { it.key in LOOK_SETTINGS }.fold(s) { acc, (k, v) -> withSetting(acc, k, v) }

    /** [FREE-10] Puts back a look written by [lookSnapshot]. */
    fun restoreLook(m: Map<String, String>) {
        com.bamnat.app.ui.recipe.LiveTryOn.drop(c)
        c.settingsRepository.update { s -> m.entries.filter { it.key in LOOK_SETTINGS }.fold(s) { acc, (k, v) -> withSetting(acc, k, v) } }
    }

    // ---- helpers ----

    private suspend fun types(cal: String): List<ShiftType> = c.scopeOf(cal).schedule.snapshot.first().shiftTypes

    private fun role(t: ShiftType): ShiftRole = ShiftRole.guess(t.name, t.isOff, t.start?.hour, if (t.isOff) 0 else t.durationMinutes)

    /** The user's kind of day a recipe item meets: the same name first, then the same role. */
    private fun match(mine: List<ShiftType>, item: Map<String, Any?>): ShiftType? {
        val name = (item["name"] as? String)?.trim()
        return mine.firstOrNull { it.name.trim() == name } ?: mine.firstOrNull { role(it) == ShiftRole.of(item["role"] as? String) }
    }

    /**
     * [R136-04] 1.36 (리뷰 R-13: 레시피의 비번이 내 휴무를 먼저 가져가 휴무가 모두 '비'가 됐어요): each of my kinds of day meets one
     * item at most, the same name first across all items, then the same role in order; an item left over is skipped.
     */
    private fun pairs(mine: List<ShiftType>, items: List<Map<String, Any?>>): List<Pair<Map<String, Any?>, ShiftType?>> {
        val used = HashSet<Long>()
        val byName = items.map { i ->
            val name = (i["name"] as? String)?.trim()
            mine.firstOrNull { it.id !in used && it.name.trim() == name }?.also { used += it.id }
        }
        return items.mapIndexed { n, i -> i to (byName[n] ?: match(mine.filter { it.id !in used }, i)?.also { used += it.id }) }
    }

    /** [RCX-30] Every picture id a 그림 꾸미기 piece names. */
    fun photoIds(values: Map<String, Any?>): List<String> =
        (listOf(values["bg"], values["head"], values["widget"], values["memoBg"]) + items(values, "icons").map { it["image"] } +
            items(values, "items").map { it["photo"] } +
            ((values["screens"] as? String)?.let { com.bamnat.core.recipe.ScreenLooks.decode(it).images }.orEmpty()))
            .filterIsInstance<String>().filter { com.bamnat.core.recipe.PhotoLook.isId(it) }.distinct()

    @Suppress("UNCHECKED_CAST")
    private fun items(values: Map<String, Any?>, key: String): List<Map<String, Any?>> = (values[key] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

    private fun texts(values: Map<String, Any?>, key: String): List<String> = (values[key] as? List<*>)?.map { it.toString() }.orEmpty()

    private fun nums(v: Any?): List<Int> = (v as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()

    private suspend fun addCategories(cal: String, kind: MoneyKind, names: List<String>, style: com.bamnat.core.work.WorkStyle): JSONArray? {
        if (names.isEmpty()) return null
        val money = c.scopeOf(cal).money
        val edits = money.categories.first()
        val shown = MoneyCategories.of(kind, edits, style)
        val add = names.map(MoneyCategories::clean).filter { it.isNotEmpty() && it !in shown }.distinct()
        if (add.isEmpty()) return null
        val mine = edits.filter { it.kind == kind }
        money.saveCategories(kind, mine + add.mapIndexed { i, n -> CategoryEdit(kind, n, mine.size + i) })
        return JSONArray(add)
    }

    private suspend fun removeCategories(cal: String, o: JSONObject?) {
        o ?: return
        val money = c.scopeOf(cal).money
        for (k in o.keys()) {
            val kind = runCatching { MoneyKind.valueOf(k) }.getOrNull() ?: continue
            val names = o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }.toSet()
            val edits = money.categories.first().filter { it.kind == kind }
            // Lines already written under one keep their category; it only leaves the list to pick from.
            money.saveCategories(kind, edits.filter { it.name !in names })
        }
    }

    private fun removeTemplates(before: JSONObject) {
        val ids = before.optJSONArray("templates")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet()
        if (ids.isNotEmpty()) c.memoTemplates.save(c.memoTemplates.items.value.filter { it.id !in ids })
    }

    private fun fieldJson(f: com.bamnat.core.money.FieldRules) = JSONObject().put("mode", f.mode?.name ?: JSONObject.NULL).put("dayRate", f.dayRate)
        .put("chips", JSONArray(f.unitChips)).put("ends", endsJson(f.unitRules))

    private fun endsJson(list: List<com.bamnat.core.money.DayRate.EndRule>) = JSONArray(list.map { JSONObject().put("end", clock(it.until)).put("units", it.units) })

    private fun ends(a: JSONArray?) = a?.objects()?.map { com.bamnat.core.money.DayRate.EndRule(LocalTime.parse(it.getString("end")), it.getDouble("units")) }.orEmpty()

    private fun doubles(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getDouble(i) } }.orEmpty()

    private fun shopJson(r: com.bamnat.core.work.ShopRules, m: com.bamnat.core.work.ShopMoney) = JSONObject()
        .put("closed", days(r.closedWeekdays)).put("open", clock(r.open)).put("close", clock(r.close)).put("holidaysClosed", r.holidaysClosed)
        .put("cardFeeBp", m.cardFeeBp).put("settleDays", m.settleDays).put("simpleTax", m.simpleTax).put("valueAdded", m.valueAddedPercent)

    private fun days(s: Set<DayOfWeek>) = s.sortedBy { it.value }.joinToString(",") { it.name }
    private fun daysFrom(s: String) = s.split(',').filter { it.isNotEmpty() }.map(DayOfWeek::valueOf).toSet()

    private fun typeTimes(t: ShiftType) = JSONObject().put("id", t.id).put("start", t.start?.toString() ?: JSONObject.NULL)
        .put("end", t.end?.toString() ?: JSONObject.NULL).put("break", t.breakMinutes).put("breakStart", t.breakStart?.toString() ?: JSONObject.NULL)
        .put("off", t.isOff)

    private fun patternJson(p: com.bamnat.core.model.WorkPattern): JSONObject {
        val e = p.toEntity(1)
        return JSONObject().put("kind", e.kind).put("start", e.startEpochDay).put("end", e.endEpochDay ?: JSONObject.NULL)
            .put("steps", e.steps).put("anchor", e.anchorEpochDay ?: JSONObject.NULL)
    }

    private fun patternFrom(o: JSONObject): com.bamnat.core.model.WorkPattern? = PatternEntity(
        kind = o.getString("kind"), startEpochDay = o.getLong("start"), endEpochDay = if (o.isNull("end")) null else o.getLong("end"),
        steps = o.getString("steps"), anchorEpochDay = if (o.isNull("anchor")) null else o.getLong("anchor"),
    ).toModel()

    private fun clock(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    private fun cellLabel(c: String?) = when (c) { "fill" -> "진하게"; "dot" -> "칠하지 않기"; "tint" -> "옅게"; else -> "그대로" }

    private fun taxLabel(t: String) = when (t) { "WITHHOLDING_3_3" -> "3.3% 떼기"; "INSURANCE_4" -> "4대보험"; else -> "떼지 않음" }

    private fun dayShort(d: String) = "월화수목금토일"[DayOfWeek.valueOf(d).value - 1].toString()

    companion object {
        /** [FREE-10] What a theme may change, as settings. */
        val LOOK_SETTINGS = listOf("calView", "calTheme", "screenLooks", "memoLook", "moneyLook", "payLooks", "widgetLook", "photoLook",
            "calendarLayout", "statsLayout", "moneyLayout", "memoLayout", "tabOrder",
            // [TAB-07] 1.36 탭 보기 (달력 판·메모장 보기·가계부 판).
            "tabViews",
            // [STU-37] 1.36 근무에 따라 바뀌는 모양: 입히기 puts it on with the rest; it stays on this phone (not in a recipe).
            "shiftLooks",
            // [BLU-01] 1.36 부품 꾸미기.
            "parts",
            // [LAY-01] 1.36 꾸밈 층.
            "decor",
            // [MAT-07] 1.36 움직임: 입히기 puts it on with the look (in a recipe it rides 앱 동작).
            "motion")
            // [RUL-03] PC D4 (입히기 뒤 규칙이 모두 사라짐): 단추·규칙은 모양이 아니에요. 테마를 입거나 처음 모양으로 돌려도 그대로이고,
            // 스튜디오 입히기가 따로 저장해요 (StudioViewModel.wear).

        private val SETTING_NAMES = mapOf("logic" to "수식·규칙·버튼","tabOrder" to "아래 탭 순서", "calendarLayout" to "달력 화면 카드 순서·보이기", "statsLayout" to "통계 카드 순서·보이기",
            "moneyLayout" to "가계부 카드 순서·보이기", "memoLayout" to "메모 카드 순서·보이기", "tabViews" to "탭 보기 (달력 판·메모장 보기·가계부 판)", "widgetLook" to "위젯 투명도·밝기", "shiftLooks" to "근무에 따라 바뀌는 모양", "widgetMemo" to "위젯 메모 한 줄",
            "photoLook" to "그림 꾸미기 (달력 배경·머리 그림·근무 그림·위젯 배경·모서리)",
            "calView" to "달력 모양 (칸 높이·근무 표시·날짜 자리·메모 줄·줄 순서·칸 디자인·패턴)", "memoInCells" to "날짜 칸의 메모", "showHoursInCells" to "날짜 칸의 근무 시간",
            "showLunar" to "음력 날짜", "spendInCells" to "날짜 밑 쓴 돈", "themeMode" to "밝기", "dynamicColor" to "휴대폰 배경색에 맞추기", "accentColor" to "앱 강조색",
            "textStep" to "글자 크기", "compact" to "촘촘하게", "clock12" to "오전/오후로 보기", "phoneDayView" to "날짜를 누르면", "longPress" to "날짜를 길게 누르면",
            "dayGroupOrder" to "하루 화면 순서", "sleepTips" to "오늘의 흐름", "restCheck" to "휴식 점검", "statsByPayPeriod" to "통계를 급여 기간으로", "motion" to "움직임",
            "memoLook" to "메모장 모양 (배치·카드·글꼴·배경)", "moneyLook" to "가계부 모양 (줄 간격·금액 크기·표시·색)",
            "screenLooks" to "화면 색·배경", "payLooks" to "급여 화면 모양 (근무 유형별)",
            "parts" to "부품 꾸미기 (달력 머리·칸·날짜·탭 막대·카드 하나하나)",
            "decor" to "꾸밈 층 (도형·아이콘·이모지·글상자)")
        /** [CVW-07] 앱 동작's 밝기 and the settings' own. */
        private val THEMES = mapOf("system" to com.bamnat.app.data.ThemeMode.SYSTEM, "light" to com.bamnat.app.data.ThemeMode.LIGHT,
            "dark" to com.bamnat.app.data.ThemeMode.DARK, "night" to com.bamnat.app.data.ThemeMode.NIGHT_SHIFT)
        const val CLOSING_TITLE = "마감 체크"
        const val JOURNAL_TITLE = "투자일지"
        const val PRINCIPLES_MAX = 20
        /** 1.39 [MKP-05] 전후 표: at most this many named rows of one kind (메모 틀·원칙·예산), the count row says the rest. */
        const val MAX_SIDE_ROWS = 6
        val ROUNDINGS = setOf("HOURLY_FIRST", "AT_END", "AT_END_UP", "AT_END_DOWN")

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
        private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
    }
}
