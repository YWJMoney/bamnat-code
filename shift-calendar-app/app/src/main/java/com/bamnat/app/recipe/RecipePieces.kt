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
    data class Change(val text: String, val skipped: Boolean = false)

    /** How a piece meets what the user has: put the recipe's on, keep both (where a piece can), or leave it out. */
    enum class Mode { REPLACE, BOTH, SKIP }

    // ---- export (만들기 1단계: 지금 내 설정에서 가져오기) ----

    /** What [kind] holds now on calendar [cal], in the piece's own fields; null when there is nothing to share. */
    suspend fun export(kind: PieceKind, cal: String): Map<String, Any?>? = when (kind) {
        PieceKind.SHIFT_LOOK -> types(cal).takeIf { it.isNotEmpty() }?.take(12)?.let { list ->
            mapOf("items" to list.map { t ->
                mapOf("name" to t.name.take(20), "role" to role(t).key, "label" to t.label.ifEmpty { t.name.take(1) },
                    "color" to (t.colorArgb or 0xFF000000L), "emoji" to ShiftIcon.emoji(t.icon))
            })
        }
        PieceKind.CAL_THEME -> CalendarTheme.decode(c.settingsRepository.current.calTheme).let { t ->
            mapOf("today" to t.today, "sat" to t.sat, "sun" to t.sun, "holiday" to t.holiday, "cell" to (t.cell ?: "tint"))
        }
        PieceKind.ALARMS -> {
            val s = c.settingsRepository.current
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
        PieceKind.LAYOUT -> c.settingsRepository.current.let { s ->
            mapOf("tabs" to com.bamnat.core.layout.TabOrder.of(s.tabOrder), "calendar" to s.calendarLayout.ifEmpty { null })
        }
        PieceKind.CARDS -> c.settingsRepository.current.let { s ->
            mapOf("stats" to s.statsLayout.ifEmpty { null }, "money" to s.moneyLayout.ifEmpty { null }, "memo" to s.memoLayout.ifEmpty { null })
                .takeIf { m -> m.values.any { it != null } }
        }
        PieceKind.WIDGET -> c.settingsRepository.current.let { s ->
            val w = com.bamnat.core.recipe.WidgetLook.decode(s.widgetLook)
            mapOf("alpha" to w.alpha.toLong(), "theme" to w.theme, "memo" to s.widgetMemo)
        }
        // [CVW-01] 1.35 달력 모양 with the cells' own switches.
        PieceKind.CAL_VIEW -> c.settingsRepository.current.let { s ->
            val v = com.bamnat.core.recipe.CalendarView.decode(s.calView)
            mapOf("size" to v.size, "badge" to v.badge, "date" to v.date, "text" to v.text, "grid" to v.grid,
                "memo" to (v.memo ?: if (s.memoInCells) null else 0)?.toLong(), "hours" to s.showHoursInCells, "lunar" to s.showLunar, "spend" to s.spendInCells,
                "order" to v.order?.joinToString(","))
        }
        // [CVW-07] 1.35 앱 동작.
        PieceKind.APP_FEEL -> c.settingsRepository.current.let { s ->
            mapOf("theme" to THEMES.entries.first { it.value == s.themeMode }.key, "dynamic" to s.dynamicColor,
                "accent" to s.accentColor.takeIf { it != 0L }, "textStep" to s.textStep.toLong(), "compact" to s.compact, "clock12" to s.clock12,
                "tap" to s.phoneDayView.name.lowercase(), "hold" to s.longPress, "dayOrder" to s.dayGroupOrder.joinToString(",").ifEmpty { null },
                "flow" to s.sleepTips, "rest" to s.restCheck, "payPeriod" to s.statsByPayPeriod)
        }
        // [RCX-30] The phone's own picture ids; they become the server's when the recipe is sent ([LookImages.upload]).
        PieceKind.PHOTO_LOOK -> com.bamnat.core.recipe.PhotoLook.decode(c.settingsRepository.current.photoLook).takeIf { !it.isDefault }?.let { l ->
            val ctx = c.appContext
            val mine = types(cal).associateBy { it.id }
            val icons = l.icons.mapNotNull { (t, id) -> mine[t]?.let { role(it).key to id } }.filter { LookImages.has(ctx, it.second) }
                .distinctBy { it.first }.take(6).map { (r, id) -> mapOf("role" to r, "image" to id) }
            mapOf("bg" to l.bg?.takeIf { LookImages.has(ctx, it) }, "dim" to l.dim.toLong(), "head" to l.head?.takeIf { LookImages.has(ctx, it) },
                "widget" to l.widget?.takeIf { LookImages.has(ctx, it) }, "corners" to l.corners, "icons" to icons.ifEmpty { null })
        }
    }

    // ---- preview ----

    /** What putting [values] (inputs already filled in) on calendar [cal] changes, line by line. */
    suspend fun preview(kind: PieceKind, values: Map<String, Any?>, cal: String): List<Change> = when (kind) {
        PieceKind.SHIFT_LOOK -> {
            val mine = types(cal)
            items(values, "items").map { i ->
                val t = match(mine, i)
                if (t == null) Change("‘${i["name"]}’: 맞는 근무가 없어 건너뛰어요", skipped = true)
                else Change("${t.name}: 글자 ${t.label}→${i["label"] ?: t.label}" + (if (i["color"] != null) ", 색 바뀜" else "") +
                    (i["emoji"]?.let { ", 이모지 $it" } ?: ""))
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
            lines + listOfNotNull(
                values["snooze"]?.let { Change("다시 울림 ${(it as Number).toInt()}분") },
                values["ring"]?.let { Change("울림 길이 ${(it as Number).toInt()}분") },
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
            else listOf(Change(runCatching { c.companyShare.summarize(values["setup"] as String).let { s -> "근무 ${s.shiftTypes.size}가지와 순서, 급여 규칙" } }
                .getOrDefault("근무 주기와 급여 규칙")))
        PieceKind.BUNDLE -> listOf(Change("묶음 안 레시피 ${items(values, "items").size}개는 아래에서 하나씩 열어 골라 넣어요", skipped = true))
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
        PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.CAL_VIEW, PieceKind.APP_FEEL ->
            settingsAfter(kind, values, cal).filter { (k, v) -> settingOf(c.settingsRepository.current, k) != v }.map { (k, _) -> Change(SETTING_NAMES[k] ?: k) }
                .ifEmpty { listOf(Change("지금과 같아서 바뀌는 것이 없어요", skipped = true)) }
    }

    // ---- apply ----

    /**
     * Puts [values] on calendar [cal]; returns what it replaced (for [restore]) or null when nothing changed. [code] marks what it
     * added (메모 틀) so [restore] takes exactly that away.
     */
    suspend fun apply(kind: PieceKind, values: Map<String, Any?>, cal: String, code: String, mode: Mode = Mode.REPLACE): JSONObject? {
        if (mode == Mode.SKIP) return null
        return when (kind) {
            PieceKind.SHIFT_LOOK -> {
                val repo = c.scopeOf(cal).schedule
                val mine = types(cal)
                val list = JSONArray()
                val used = HashSet<Long>()
                for (i in items(values, "items")) {
                    val t = match(mine.filter { it.id !in used }, i) ?: continue
                    used += t.id
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
                val now = schedule.snapshot.first().shiftTypes
                JSONObject()
                    .put("types", JSONArray(types.map { t -> typeTimes(t) }))
                    .put("added", JSONArray(now.filter { n -> types.none { it.id == n.id } }.map { it.id }))
                    .put("patterns", JSONArray(patterns.map { p -> patternJson(p) }))
                    .put("profile", profile?.let(PayCodec::encode) ?: JSONObject.NULL)
            }
            PieceKind.BUNDLE -> null
            PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.PHOTO_LOOK, PieceKind.CAL_VIEW, PieceKind.APP_FEEL -> {
                // [RCX-30] The recipe's pictures come first; one the phone cannot get (not looked at yet) is left out.
                if (kind == PieceKind.PHOTO_LOOK) photoIds(values).forEach { LookImages.fetch(c, it) }
                val now = c.settingsRepository.current
                val changes = settingsAfter(kind, values, cal).filter { (k, v) -> settingOf(now, k) != v }
                if (changes.isEmpty()) null else {
                    val out = JSONObject()
                    changes.forEach { (k, v) -> out.put(k, JSONArray().put(settingOf(now, k)).put(v)) }
                    c.settingsRepository.update { cur -> changes.entries.fold(cur) { acc, (k, v) -> withSetting(acc, k, v) } }
                    JSONObject().put("settings", out)
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
        val used = HashSet<Long>()
        return items(values, "items").mapNotNull { i ->
            val t = match(mine.filter { it.id !in used }, i) ?: return@mapNotNull null
            used += t.id
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
            val used = HashSet<Long>()
            base + ("items" to items(base, "items").map { i ->
                val t = match(mine.filter { it.id !in used }, i) ?: return@map i
                used += t.id
                i + mapOf("label" to t.label, "color" to (t.colorArgb or 0xFF000000L)) +
                    (ShiftIcon.emoji(t.icon)?.let { mapOf("emoji" to it) } ?: emptyMap())
            })
        }
        PieceKind.ALARMS, PieceKind.MEMO_TEMPLATES, PieceKind.INVEST, PieceKind.COMPANY, PieceKind.BUNDLE -> base
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
                if (s.calTheme == before.optString("after")) s.copy(calTheme = before.optString("before")) else s
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
                before.optJSONArray("patterns")?.let { a -> schedule.restorePatterns(a.objects().mapNotNull(::patternFrom)) }
                val mine = schedule.snapshot.first().shiftTypes.associateBy { it.id }
                before.optJSONArray("types")?.objects()?.forEach { o ->
                    val t = mine[o.getLong("id")] ?: return@forEach
                    schedule.saveShiftType(t.copy(start = o.optStringOrNull("start")?.let(LocalTime::parse), end = o.optStringOrNull("end")?.let(LocalTime::parse),
                        breakMinutes = o.optInt("break"), breakStart = o.optStringOrNull("breakStart")?.let(LocalTime::parse), isOff = o.optBoolean("off")))
                }
                before.optJSONArray("added")?.let { a -> (0 until a.length()).forEach { runCatching { schedule.deleteShiftTypeUndoable(a.getLong(it)) } } }
                before.optJSONObject("profile")?.let { p -> runCatching { c.payStore.save(PayCodec.decode(p)) } }
            }
            PieceKind.BUNDLE -> {}
            PieceKind.LAYOUT, PieceKind.CARDS, PieceKind.WIDGET, PieceKind.PHOTO_LOOK, PieceKind.CAL_VIEW, PieceKind.APP_FEEL -> {
                val sets = before.optJSONObject("settings") ?: return
                c.settingsRepository.update { cur ->
                    sets.keys().asSequence().fold(cur) { acc, k ->
                        val pair = sets.getJSONArray(k)
                        if (settingOf(acc, k) == pair.getString(1)) withSetting(acc, k, pair.getString(0)) else acc
                    }
                }
            }
        }
    }

    // ---- 1.35 settings pieces (화면 배치·카드·위젯 모양), each setting kept as a line of text ----

    /** The settings [kind] would leave, by key, as text. */
    private suspend fun settingsAfter(kind: PieceKind, values: Map<String, Any?>, cal: String): Map<String, String> {
        val cur = c.settingsRepository.current
        return when (kind) {
            PieceKind.LAYOUT -> listOfNotNull(
                texts(values, "tabs").takeIf { it.isNotEmpty() }?.let { "tabOrder" to com.bamnat.core.layout.TabOrder.toSave(it).joinToString(",") },
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
                    theme = (values["theme"] as? String)?.takeIf { it in com.bamnat.core.recipe.WidgetLook.THEMES } ?: w.theme)
                listOfNotNull("widgetLook" to after.encode(), (values["memo"] as? Boolean)?.let { "widgetMemo" to it.toString() }).toMap()
            }
            // [RCX-30] Each picture the phone has (or got) takes its place; a kind of day's picture goes to the user's kinds of day
            // of the same role. What the recipe leaves out stays as it is.
            PieceKind.PHOTO_LOOK -> {
                val ctx = c.appContext
                val l = com.bamnat.core.recipe.PhotoLook.decode(cur.photoLook)
                fun pic(k: String) = (values[k] as? String)?.takeIf { LookImages.has(ctx, it) }
                val byRole = items(values, "icons").mapNotNull { i -> (i["image"] as? String)?.takeIf { LookImages.has(ctx, it) }?.let { (i["role"] as? String) to it } }.toMap()
                val icons = if (byRole.isEmpty()) l.icons else l.icons + types(cal).mapNotNull { t -> byRole[role(t).key]?.let { t.id to it } }
                val after = l.copy(bg = pic("bg") ?: l.bg, head = pic("head") ?: l.head, widget = pic("widget") ?: l.widget,
                    dim = (values["dim"] as? Number)?.toInt()?.coerceIn(0, 80) ?: l.dim,
                    corners = (values["corners"] as? String)?.takeIf { it in com.bamnat.core.recipe.PhotoLook.CORNERS } ?: l.corners, icons = icons)
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
                (values["payPeriod"] as? Boolean)?.let { "statsByPayPeriod" to it.toString() },
            ).toMap()
            else -> emptyMap()
        }
    }

    private fun settingOf(s: com.bamnat.app.data.AppSettings, key: String): String = when (key) {
        "tabOrder" -> s.tabOrder.joinToString(",")
        "calendarLayout" -> s.calendarLayout
        "statsLayout" -> s.statsLayout
        "moneyLayout" -> s.moneyLayout
        "memoLayout" -> s.memoLayout
        "widgetLook" -> s.widgetLook
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
        "statsByPayPeriod" -> s.statsByPayPeriod.toString()
        else -> ""
    }

    private fun withSetting(s: com.bamnat.app.data.AppSettings, key: String, v: String): com.bamnat.app.data.AppSettings = when (key) {
        "tabOrder" -> s.copy(tabOrder = v.split(',').filter { it.isNotEmpty() })
        "calendarLayout" -> s.copy(calendarLayout = v)
        "statsLayout" -> s.copy(statsLayout = v)
        "moneyLayout" -> s.copy(moneyLayout = v)
        "memoLayout" -> s.copy(memoLayout = v)
        "widgetLook" -> s.copy(widgetLook = v)
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
        "statsByPayPeriod" -> s.copy(statsByPayPeriod = v == "true")
        else -> s
    }

    // ---- helpers ----

    private suspend fun types(cal: String): List<ShiftType> = c.scopeOf(cal).schedule.snapshot.first().shiftTypes

    private fun role(t: ShiftType): ShiftRole = ShiftRole.guess(t.name, t.isOff, t.start?.hour, if (t.isOff) 0 else t.durationMinutes)

    /** The user's kind of day a recipe item meets: the same name first, then the same role. */
    private fun match(mine: List<ShiftType>, item: Map<String, Any?>): ShiftType? {
        val name = (item["name"] as? String)?.trim()
        return mine.firstOrNull { it.name.trim() == name } ?: mine.firstOrNull { role(it) == ShiftRole.of(item["role"] as? String) }
    }

    /** [RCX-30] Every picture id a 그림 꾸미기 piece names. */
    fun photoIds(values: Map<String, Any?>): List<String> =
        (listOf(values["bg"], values["head"], values["widget"]) + items(values, "icons").map { it["image"] }).filterIsInstance<String>()
            .filter { com.bamnat.core.recipe.PhotoLook.isId(it) }.distinct()

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
        private val SETTING_NAMES = mapOf("tabOrder" to "아래 탭 순서", "calendarLayout" to "달력 화면 카드 순서·보이기", "statsLayout" to "통계 카드 순서·보이기",
            "moneyLayout" to "가계부 카드 순서·보이기", "memoLayout" to "메모 카드 순서·보이기", "widgetLook" to "위젯 투명도·밝기", "widgetMemo" to "위젯 메모 한 줄",
            "photoLook" to "그림 꾸미기 (달력 배경·머리 그림·근무 그림·위젯 배경·모서리)",
            "calView" to "달력 모양 (칸 높이·근무 표시·날짜 자리·메모 줄·줄 순서)", "memoInCells" to "날짜 칸의 메모", "showHoursInCells" to "날짜 칸의 근무 시간",
            "showLunar" to "음력 날짜", "spendInCells" to "날짜 밑 쓴 돈", "themeMode" to "밝기", "dynamicColor" to "휴대폰 배경색에 맞추기", "accentColor" to "앱 강조색",
            "textStep" to "글자 크기", "compact" to "촘촘하게", "clock12" to "오전/오후로 보기", "phoneDayView" to "날짜를 누르면", "longPress" to "날짜를 길게 누르면",
            "dayGroupOrder" to "하루 화면 순서", "sleepTips" to "오늘의 흐름", "restCheck" to "휴식 점검", "statsByPayPeriod" to "통계를 급여 기간으로")
        /** [CVW-07] 앱 동작's 밝기 and the settings' own. */
        private val THEMES = mapOf("system" to com.bamnat.app.data.ThemeMode.SYSTEM, "light" to com.bamnat.app.data.ThemeMode.LIGHT,
            "dark" to com.bamnat.app.data.ThemeMode.DARK, "night" to com.bamnat.app.data.ThemeMode.NIGHT_SHIFT)
        const val CLOSING_TITLE = "마감 체크"
        const val JOURNAL_TITLE = "투자일지"
        const val PRINCIPLES_MAX = 20
        val ROUNDINGS = setOf("HOURLY_FIRST", "AT_END", "AT_END_UP", "AT_END_DOWN")

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
        private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
    }
}
