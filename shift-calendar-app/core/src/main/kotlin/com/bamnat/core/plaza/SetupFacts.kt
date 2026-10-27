package com.bamnat.core.plaza

import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.remote.Json

/**
 * [PLAZA-02] One company settings file (format `bamnat-company`) as the items people compare, each with a
 * key that names the same item in any other file ("shift:주간", "rate:night", "kind:대근", "item:식대"), so two
 * companies or two versions of one line up item by item. The words are the server's `describe()` words (the
 * admin app's), never an amount: the file has none.
 */
data class SetupFact(val key: String, val group: FactGroup, val label: String, val value: String) {
    /** The proposal field (the server's CHANGE_FIELDS) a change of this item is filed under. */
    val field: String
        get() = when {
            key.startsWith("shift:") -> "shift"
            key.startsWith("cycle:") -> "order"
            key == "rate:ot" || key.startsWith("kind") -> "overtime"
            key == "rate:night" || key == "rate:window" -> "night"
            key == "rate:hw" || key == "rate:ho" || key == "rule:restDay" || key == "rule:weeklyDay" ||
                key == "rule:holiday" || key == "rule:holidaysU5" -> "holiday"
            key == "rule:breakPay" -> "break"
            // [ITEM-08] 1.30 BC: the 통상시급's hours have their own field (the server's CHANGE_FIELDS.hours).
            key == "place:hours" -> "hours"
            key.startsWith("item:") -> if (value.contains("월 기본급") || value.contains("상여")) "bonus" else "allowance"
            else -> "other"
        }
}

/** [PLAZA-02] The groups a setup is read in, in the order the compare screen shows them. */
enum class FactGroup(val title: String) {
    SHIFT("근무 시각"), CYCLE("근무 주기"), PLACE("사업장"), RATE("가산율"), RULE("휴일·휴게"), KIND("연장·휴무일 근무"), ITEM("수당"),
}

/** [PLAZA-02] Reads a company settings file into [SetupFact]s. */
object SetupFacts {
    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")
    private val DAY_NAMES = mapOf("MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목", "FRIDAY" to "금",
        "SATURDAY" to "토", "SUNDAY" to "일")
    private val BUILT_IN_KINDS = mapOf("1" to "잔업", "2" to "특근", "3" to "대근")

    /** The facts of [text], in group order; empty when it is not a company settings file. */
    fun read(text: String): List<SetupFact> {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyList()
        return read(root)
    }

    fun read(root: Map<*, *>): List<SetupFact> {
        if (root["format"] != "bamnat-company") return emptyList()
        val out = ArrayList<SetupFact>()
        fun add(key: String, group: FactGroup, label: String, value: String?) {
            if (!value.isNullOrBlank()) out += SetupFact(key, group, label, value)
        }
        val types = (root["shiftTypes"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        for (t in types) {
            val name = t["name"] as? String ?: continue
            add("shift:$name", FactGroup.SHIFT, name, shiftText(t))
        }
        (root["rotation"] as? Map<*, *>)?.let { r ->
            val steps = (r["steps"] as? List<*>).orEmpty()
            if (steps.isNotEmpty()) add("cycle:rotation", FactGroup.CYCLE, "교대 순서",
                steps.joinToString(" ") { (it as? String) ?: "쉼" } + " (${steps.size}일)")
        }
        (root["weekly"] as? List<*>)?.takeIf { it.size == 7 }?.let { w ->
            add("cycle:weekly", FactGroup.CYCLE, "요일별 근무", weeklyText(w))
        }
        if (root["roster"] == true) add("cycle:roster", FactGroup.CYCLE, "근무표", "매달 근무표(순서 없음)")
        val kinds = kindLines(root)
        val kindName = { id: String -> kinds.firstOrNull { it.id == id }?.name ?: BUILT_IN_KINDS[id] ?: "종류 $id" }
        val pay = root["pay"] as? Map<*, *>
        if (pay != null) {
            add("place:size", FactGroup.PLACE, "사업장", if (pay["fiveOrMore"] == true) "5인 이상" else "5인 미만")
            (pay["monthlyHours"] as? Number)?.let { add("place:hours", FactGroup.PLACE, "월 소정 시간", "${it.toInt()}시간") }
            (pay["flexPeriod"] as? String)?.let { add("place:flex", FactGroup.PLACE, "탄력 근무", if (it == "MONTH") "1개월" else "3개월") }
            val r = pay["rules"] as? Map<*, *>
            if (r != null) {
                if (r["supervisory"] == true) add("place:supervisory", FactGroup.PLACE, "감시·단속", "승인 받음")
                add("rate:ot", FactGroup.RATE, "연장", rateText(r["ot"]))
                add("rate:night", FactGroup.RATE, "야간", rateText(r["night"]))
                add("rate:hw", FactGroup.RATE, "휴일", rateText(r["hw"]))
                add("rate:ho", FactGroup.RATE, "휴일 8시간 넘게", rateText(r["ho"]))
                val ns = r["nightStart"] as? String
                val ne = r["nightEnd"] as? String
                if (ns != null && ne != null) add("rate:window", FactGroup.RATE, "야간 시간", "$ns~$ne")
                add("rule:restDay", FactGroup.RULE, "쉬는 날 근무", when (r["restDay"]) {
                    "HOLIDAY" -> "휴일근로로"
                    "OVERTIME" -> "연장근로로"
                    else -> "법대로"
                })
                add("rule:weeklyDay", FactGroup.RULE, "주휴일", when (val d = r["weeklyHolidayDay"]) {
                    "FIRST_REST" -> "쉬는 날 중 첫날"
                    is String -> DAY_NAMES[d]?.let { "${it}요일" } ?: "정하지 않음"
                    else -> "정하지 않음"
                })
                // [HOLI-01] 빨간날 근무, one of four ways (1.25 V); a 5인 미만 workplace only says it when it pays it as a kind.
                val asKind = r["publicHolidayAs"]?.toString()
                if (pay["fiveOrMore"] == true || asKind != null) add("rule:holiday", FactGroup.RULE, "빨간날 근무", holidayText(r, asKind?.let(kindName)))
                if (pay["fiveOrMore"] != true && r["holidaysU5"] == true) add("rule:holidaysU5", FactGroup.RULE, "공휴일", "유급휴일로 줌")
                (r["breakPay"] as? String)?.let {
                    add("rule:breakPay", FactGroup.RULE, "휴게", if (it == "PAID") "유급 (일한 시간은 아님)" else "일한 시간으로 침")
                }
                (r["extra"] as? Map<*, *>)?.forEach { (k, e) ->
                    val id = k.toString()
                    val kind = kinds.firstOrNull { it.id == id }
                    val how = (e as? Map<*, *>)?.let(::ruleText)
                    add("kind:${kindName(id)}", FactGroup.KIND, kindName(id), listOfNotNull(kind?.let(::timingText), how).joinToString(" · "))
                }
                (r["extraByShift"] as? Map<*, *>)?.forEach { (k, by) ->
                    (by as? Map<*, *>)?.forEach { (n, e) ->
                        (e as? Map<*, *>)?.let { add("kindshift:${kindName(k.toString())}·$n", FactGroup.KIND, "${kindName(k.toString())} · $n", ruleText(it)) }
                    }
                }
            }
            (pay["items"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().forEach { i ->
                val name = i["name"] as? String ?: return@forEach
                add("item:$name", FactGroup.ITEM, name, itemText(i))
            }
        }
        // Kinds without a pay rule still say when they are worked.
        kinds.filter { !it.hidden && out.none { f -> f.key == "kind:${it.name}" } }.forEach { k ->
            add("kind:${k.name}", FactGroup.KIND, k.name, timingText(k))
        }
        return out.sortedBy { it.group.ordinal }
    }

    internal data class Kind(val id: String, val name: String, val timing: String, val hidden: Boolean, val covers: List<String>)

    internal fun kindLines(root: Map<*, *>): List<Kind> {
        val text = root["extraKinds"] as? String ?: return emptyList()
        val shifts = root["extraKindShifts"] as? Map<*, *>
        val sets = root["extraKindShiftSets"] as? Map<*, *>
        return text.split("\n").drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val f = line.split("|")
            if (f.size < 8) return@mapNotNull null
            val covers = (sets?.get(f[0]) as? List<*>)?.filterIsInstance<String>()
                ?: listOfNotNull(shifts?.get(f[0]) as? String)
            Kind(f[0], f[1], f[4], f[7] == "1", covers)
        }
    }

    private fun shiftText(t: Map<*, *>): String {
        if (t["off"] == true) return "쉬는 날"
        val brk = (t["break"] as? Number)?.toInt() ?: 0
        return "${t["start"] ?: "?"}~${t["end"] ?: "?"}" + if (brk > 0) " · 휴게 ${brk}분" else ""
    }

    private fun weeklyText(w: List<*>): String =
        w.mapIndexed { i, n -> "${WEEKDAYS[i]} ${(n as? String) ?: "쉼"}" }.joinToString(" · ")

    internal fun pct(x: Double): String {
        val v = Math.round(x * 1000) / 10.0
        return if (v == Math.floor(v)) "${v.toLong()}%" else "$v%"
    }

    private fun num(v: Any?): Double? = (v as? Number)?.toDouble()

    private fun rateText(v: Any?): String = num(v)?.let(::pct) ?: "법대로"

    private fun hours(m: Int): String = if (m % 60 != 0) "${m / 60}시간 ${m % 60}분" else "${m / 60}시간"

    private fun timingText(k: Kind): String {
        val f = k.timing.split(",")
        return when (f[0]) {
            "B" -> "${f.getOrNull(1)}~${f.getOrNull(2)}"
            "A" -> "근무 뒤 ${hours(f.getOrNull(1)?.toIntOrNull() ?: 0)}"
            "P" -> "근무 앞 ${hours(f.getOrNull(1)?.toIntOrNull() ?: 0)}"
            else -> if (k.covers.isNotEmpty()) "${k.covers.joinToString("·")} 대신" else "모든 근무 대신"
        }
    }

    /** The server's ruleText, the same words. */
    internal fun ruleText(e: Map<*, *>): String {
        val cover = (e["cover"] as? Number)?.toInt() ?: -1
        val percent = num(e["percent"]) ?: 0.1
        return when (e["basis"]) {
            "LEGAL" -> "법대로"
            "FIXED_PER_TIME" -> "1회 정액 (금액은 각자 넣어요)"
            "FIXED_PER_HOUR" -> "시간당 정액 (금액은 각자 넣어요)"
            "PERCENT_OF_BASE" -> "${if (cover > 0) hours(cover) else "한 번"}에 기본급 ${pct(percent)}" + if (e["premiums"] == true) " + 법정 가산" else ""
            "HOURLY_RATE" -> "통상시급 × ${pct(num(e["multiplier"]) ?: 1.5)}"
            "TIERED" -> "시간 구간별 가산 " + (e["dayTiers"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                .joinToString(", ") { "${hours((it["min"] as? Number)?.toInt() ?: 0)}부터 +${pct(num(it["premium"]) ?: 0.0)}" }
            else -> {
                val unitMin = (e["unitMin"] as? Number)?.toInt() ?: 480
                val unit = when (e["unit"]) {
                    "BASE_SHARE" -> "${hours(unitMin)}에 기본급 ${pct(percent)}"
                    "PER_HOUR" -> "시간당 정액 (금액은 각자)"
                    "PER_TIME" -> "1회 정액 (금액은 각자)" + if (cover > 0) " ${hours(cover)}까지" else ""
                    else -> "통상시급"
                }
                val adds = (e["adds"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().filter { it["on"] == true }.map { a ->
                    val scope = a["scope"] as? String ?: ""
                    val where = when {
                        scope.startsWith("over:") -> " (${hours(scope.removePrefix("over:").toIntOrNull() ?: 0)} 넘게)"
                        scope.startsWith("window:") -> " (${scope.removePrefix("window:").replace("-", "~")})"
                        else -> ""
                    }
                    "${(a["name"] as? String)?.ifEmpty { null } ?: a["key"]} ${pct(num(a["rate"]) ?: 0.0)}$where" +
                        if (a["offDays"] == true) " (쉬는 날만)" else ""
                }
                val min = (e["min"] as? Number)?.toInt() ?: 0
                (listOf(unit) + adds).joinToString(" + ") + if (min > 0) " · 최소 ${hours(min)}" else ""
            }
        }
    }

    private fun itemText(i: Map<*, *>): String {
        val types = (i["types"] as? List<*>).orEmpty().filterIsInstance<String>()
        val months = (i["months"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }
        val how = when (i["cadence"]) {
            "PER_WORK_DAY" -> "근무일마다"
            "PER_NIGHT_SHIFT" -> "야간마다"
            "PER_SHIFT_TYPE" -> "${types.joinToString("·").ifEmpty { "정한 근무" }}마다"
            "BONUS" -> if (months.isNotEmpty()) "${months.joinToString("·")}월" else "정한 달"
            else -> "매달"
        }
        val covers = (i["covers"] as? List<*>)?.filterIsInstance<String>()?.let { c ->
            if (c.isEmpty()) "가산과 따로" else c.joinToString("·") { mapOf("OVERTIME" to "연장", "NIGHT" to "야간", "HOLIDAY" to "휴일")[it] ?: it } + " 가산 대신"
        }
        val festivals = (i["festivals"] as? List<*>).orEmpty().joinToString("·") { if (it == "SEOLLAL") "설" else "추석" }
        return listOfNotNull(how, num(i["percentOfBase"])?.let { "기본급 ${pct(it)}" }, festivals.ifEmpty { null }, covers, shareText(i))
            .joinToString(" ")
    }

    /**
     * [ITEM-04] 1.30 BC: who sets the amount, the server's itemText words: "회사 공통 100,000원", "기본급의 10%", and "통상임금"
     * when it is in it, so a proposal that only moves it in or out of 통상임금 shows as a change. Nothing for 사람마다.
     */
    internal fun shareText(i: Map<*, *>): String? {
        val share = when (i["share"]) {
            "FIXED" -> "회사 공통" + ((i["fixedAmount"] as? Number)?.toLong()?.takeIf { it > 0 }?.let { " %,d원".format(it) } ?: "")
            "PERCENT" -> num(i["percent"])?.let { p ->
                "${BASES[i["percentOf"]] ?: "기본급"}의 ${pct(p)}"
            }
            else -> null
        }
        val ordinary = "통상임금".takeIf { i["ordinary"] == true }
        return listOfNotNull(share, ordinary).joinToString(" · ").ifEmpty { null }
    }

    private val BASES = mapOf("BASE" to "기본급", "BASE_FIXED" to "기본급+고정수당", "ORDINARY" to "통상임금")

    /** [HOLI-01] The 빨간날 choice a file's rules come to, as [com.bamnat.core.pay.CompanyRules.holidayWork] reads its fields. */
    fun holidayWork(rules: Map<*, *>?): HolidayWork = when {
        rules?.get("publicHolidays") == false ->
            if (rules["holidayWork"] == HolidayWork.SHIFT_NORMAL.name) HolidayWork.SHIFT_NORMAL else HolidayWork.SUBSTITUTE
        rules?.get("publicHolidayAs") != null -> HolidayWork.AS_EXTRA
        else -> HolidayWork.PREMIUM
    }

    /** "특근처럼, 밤샘은 시작한 날로": the server's describe says the same. */
    internal fun holidayText(rules: Map<*, *>, kind: String?): String {
        val mode = holidayWork(rules)
        val byStart = rules["holidayByStart"] == true && (mode == HolidayWork.PREMIUM || mode == HolidayWork.AS_EXTRA)
        val head = if (mode == HolidayWork.AS_EXTRA) "${kind ?: "특근"}처럼" else mode.label
        return head + if (byStart) ", 밤샘은 시작한 날로" else ""
    }
}
