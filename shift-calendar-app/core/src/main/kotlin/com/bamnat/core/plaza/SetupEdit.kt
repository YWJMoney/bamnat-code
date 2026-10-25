package com.bamnat.core.plaza

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.remote.Json

/**
 * [PLAZA-02] The company's setup opened for 다른 점 말하기: the items a person can change with a tap (no letters to
 * type, 1.24 Q-D2), each a copy with one value changed. The file stays a company settings file the server takes
 * (`cleanSetup`), so the edited file goes as the proposal and the changed items as its lines.
 */
class SetupEdit private constructor(private val root: Map<String, Any?>) {
    val text: String get() = SetupJson.write(root)
    val facts: List<SetupFact> get() = SetupFacts.read(root)

    /** The shifts by name with their times, as the file has them. */
    data class Shift(val name: String, val start: String?, val end: String?, val breakMinutes: Int, val off: Boolean)

    val shifts: List<Shift>
        get() = list("shiftTypes").filterIsInstance<Map<*, *>>().map {
            Shift(it["name"] as String, it["start"] as? String, it["end"] as? String, (it["break"] as? Number)?.toInt() ?: 0, it["off"] == true)
        }

    val rotation: List<String?>? get() = (root["rotation"] as? Map<*, *>)?.let { r -> (r["steps"] as? List<*>)?.map { it as? String } }
    val weekly: List<String?>? get() = (root["weekly"] as? List<*>)?.map { it as? String }?.takeIf { it.size == 7 }
    val hasPay: Boolean get() = root["pay"] is Map<*, *>
    val fiveOrMore: Boolean get() = pay()?.get("fiveOrMore") == true
    fun rate(key: String): Double? = (rules()?.get(key) as? Number)?.toDouble()
    val restDay: String get() = rules()?.get("restDay") as? String ?: "LAW"
    val weeklyDay: String get() = rules()?.get("weeklyHolidayDay") as? String ?: "UNKNOWN"
    /** [HOLI-01][PLAZA-02] The 빨간날 choice the file's fields come to, as [com.bamnat.core.pay.CompanyRules.holidayWork] reads them. */
    val holidayWork: HolidayWork get() = SetupFacts.holidayWork(rules())
    val nightWindow: Pair<String, String>? get() {
        val r = rules() ?: return null
        val s = r["nightStart"] as? String ?: return null
        val e = r["nightEnd"] as? String ?: return null
        return s to e
    }

    /** One way of paying a kind of extra work: its id, name and the numbers that can be changed. */
    data class KindRule(val id: String, val name: String, val basis: String, val multiplier: Double, val percent: Double)

    val kindRules: List<KindRule>
        get() {
            val kinds = SetupFacts.kindLines(root)
            val names = mapOf("1" to "잔업", "2" to "특근", "3" to "대근")
            return (rules()?.get("extra") as? Map<*, *>).orEmpty().mapNotNull { (k, e) ->
                val m = e as? Map<*, *> ?: return@mapNotNull null
                val id = k.toString()
                KindRule(id, kinds.firstOrNull { it.id == id }?.name ?: names[id] ?: "종류 $id", m["basis"] as? String ?: "LEGAL",
                    (m["multiplier"] as? Number)?.toDouble() ?: 1.5, (m["percent"] as? Number)?.toDouble() ?: 0.1)
            }
        }

    /** The allowances by name and how often each is paid. */
    val items: List<Pair<String, String>>
        get() = (pay()?.get("items") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .map { (it["name"] as String) to (it["cadence"] as? String ?: "MONTHLY") }

    fun shiftTime(name: String, start: String, end: String, breakMinutes: Int): SetupEdit = with { r ->
        r["shiftTypes"] = list("shiftTypes").map { t ->
            val m = t as? Map<*, *> ?: return@map t
            if (m["name"] == name) copyMap(m) + mapOf("start" to start, "end" to end, "break" to breakMinutes.coerceIn(0, 720)) else t
        }
    }

    fun rotation(steps: List<String?>): SetupEdit = with { r ->
        val names = shifts.map { it.name }.toSet()
        val kept = steps.take(60).map { s -> s?.takeIf { it in names } }
        if (kept.isEmpty()) return@with
        r["rotation"] = copyMap(root["rotation"] as? Map<*, *> ?: emptyMap<String, Any?>()) + mapOf("steps" to kept)
    }

    fun weekly(days: List<String?>): SetupEdit = with { r ->
        require(days.size == 7) { "Seven days, Monday first" }
        val names = shifts.map { it.name }.toSet()
        r["weekly"] = days.map { s -> s?.takeIf { it in names } }
    }

    fun fiveOrMore(on: Boolean): SetupEdit = withPay { it["fiveOrMore"] = on }

    /** A premium (ot, night, hw, ho) as a share, or null for the law's. */
    fun rate(key: String, value: Double?): SetupEdit {
        require(key in RATES) { "Not a premium: $key" }
        return withRules { it[key] = value?.coerceIn(0.0, 5.0) }
    }

    fun nightWindow(start: String, end: String): SetupEdit = withRules { it["nightStart"] = start; it["nightEnd"] = end }

    fun restDay(value: String): SetupEdit {
        require(value in listOf("LAW", "HOLIDAY", "OVERTIME"))
        return withRules { it["restDay"] = value }
    }

    fun weeklyDay(value: String): SetupEdit = withRules { it["weeklyHolidayDay"] = value }

    /**
     * [HOLI-01][PLAZA-02] The 빨간날 choice, the same fields [com.bamnat.core.pay.CompanyRules.withHolidayWork] sets: 특근·대근처럼
     * keeps the kind the file named (특근 when none) and counts an overnight shift by the day it starts.
     */
    fun holidayWork(mode: HolidayWork): SetupEdit = withRules { r ->
        when (mode) {
            HolidayWork.PREMIUM -> { r["publicHolidays"] = true; r.remove("publicHolidayAs"); r.remove("holidayWork") }
            HolidayWork.AS_EXTRA -> {
                r["publicHolidays"] = true
                r["publicHolidayAs"] = r["publicHolidayAs"]?.toString() ?: ExtraKind.SPECIAL_ID.toString()
                r["holidayByStart"] = true
                r.remove("holidayWork")
            }
            HolidayWork.SUBSTITUTE -> { r["publicHolidays"] = false; r.remove("publicHolidayAs"); r.remove("holidayWork") }
            HolidayWork.SHIFT_NORMAL -> { r["publicHolidays"] = false; r.remove("publicHolidayAs"); r["holidayWork"] = HolidayWork.SHIFT_NORMAL.name }
        }
    }

    /** How a kind of extra work is paid: the law, 통상시급 × [multiplier], or 기본급 [percent] a time. */
    fun kindRule(id: String, basis: String, multiplier: Double? = null, percent: Double? = null): SetupEdit {
        require(basis in listOf("LEGAL", "HOURLY_RATE", "PERCENT_OF_BASE"))
        return withRules { r ->
            val extra = copyMap(r["extra"] as? Map<*, *> ?: emptyMap<String, Any?>())
            val before = copyMap(extra[id] as? Map<*, *> ?: DEFAULT_RULE)
            before["basis"] = basis
            multiplier?.let { before["multiplier"] = it.coerceIn(0.0, 10.0) }
            percent?.let { before["percent"] = it.coerceIn(0.0, 10.0) }
            extra[id] = before
            r["extra"] = extra
        }
    }

    fun itemCadence(name: String, cadence: String): SetupEdit {
        require(cadence in listOf("MONTHLY", "PER_WORK_DAY", "PER_NIGHT_SHIFT", "PER_SHIFT_TYPE", "BONUS"))
        return withPay { p ->
            p["items"] = (p["items"] as? List<*>).orEmpty().map { i ->
                val m = i as? Map<*, *> ?: return@map i
                if (m["name"] == name) copyMap(m) + mapOf("cadence" to cadence) else i
            }
        }
    }

    private fun list(key: String): List<Any?> = (root[key] as? List<*>).orEmpty()
    private fun pay(): Map<*, *>? = root["pay"] as? Map<*, *>
    private fun rules(): Map<*, *>? = pay()?.get("rules") as? Map<*, *>

    private fun with(change: (MutableMap<String, Any?>) -> Unit): SetupEdit = SetupEdit(copyMap(root).also(change))

    private fun withPay(change: (MutableMap<String, Any?>) -> Unit): SetupEdit = with { r ->
        val p = copyMap(r["pay"] as? Map<*, *> ?: return@with)
        change(p)
        r["pay"] = p
    }

    private fun withRules(change: (MutableMap<String, Any?>) -> Unit): SetupEdit = withPay { p ->
        val rules = copyMap(p["rules"] as? Map<*, *> ?: emptyMap<String, Any?>())
        change(rules)
        p["rules"] = rules
    }

    companion object {
        val RATES = listOf("ot", "night", "hw", "ho")
        private val DEFAULT_RULE = mapOf("basis" to "LEGAL", "multiplier" to 1.5, "amount" to 0, "min" to 0, "night" to true,
            "percent" to 0.1, "cover" to -1, "premiums" to false, "bonus" to 0, "unit" to "ORDINARY", "unitMin" to 480, "adds" to emptyList<Any?>())

        /** Null when [text] is not a company settings file. */
        fun of(text: String): SetupEdit? {
            val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
            if (root["format"] != "bamnat-company") return null
            return SetupEdit(copyMap(root))
        }

        @Suppress("UNCHECKED_CAST")
        private fun copyMap(m: Map<*, *>): MutableMap<String, Any?> = LinkedHashMap<String, Any?>().apply { m.forEach { (k, v) -> put(k.toString(), v) } }
    }
}

/** [PLAZA-02] Writes what [Json.parse] reads (maps, lists, strings, numbers, booleans, null) back as JSON. */
object SetupJson {
    fun write(v: Any?): String = StringBuilder().also { put(it, v) }.toString()

    private fun put(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Double -> if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 1e15) sb.append(v.toLong()) else sb.append(v)
            is Float -> put(sb, v.toDouble())
            is Number -> sb.append(v.toLong())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                v.forEach { (k, x) ->
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString()); sb.append(':'); put(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, x -> if (i > 0) sb.append(','); put(sb, x) }
                sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }
}
