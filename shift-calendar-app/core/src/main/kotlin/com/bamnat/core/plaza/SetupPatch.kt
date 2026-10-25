package com.bamnat.core.plaza

import com.bamnat.core.remote.Json

/**
 * [PLAZA-09] 이 값 쓰기 (결정 D79): one item of another version of the company's setup ([SetupFact.key]) put into the
 * user's own file, everything else as it was, the user's amounts too. The result is a company settings file the app
 * applies as it applies any; null when the item cannot be taken alone (the order of work, which starts from today's
 * step, or pay when the user has none yet) or nothing would change.
 */
object SetupPatch {
    private val BUILT_IN = mapOf("잔업" to "1", "특근" to "2", "대근" to "3")

    /** Whether [key] can be taken alone at all. The order of work goes with 이 판 쓰기, from today's step. */
    fun canTake(key: String): Boolean = !key.startsWith("cycle:")

    fun take(mine: String, theirs: String, key: String): String? {
        if (!canTake(key)) return null
        val a = deep(Json.parse(mine)) as? MutableMap<String, Any?> ?: return null
        val b = Json.parse(theirs) as? Map<*, *> ?: return null
        if (a["format"] != "bamnat-company" || b["format"] != "bamnat-company") return null
        val done = when {
            key.startsWith("shift:") -> shift(a, b, key.removePrefix("shift:"))
            key.startsWith("place:") || key.startsWith("rate:") || key.startsWith("rule:") -> pay(a, b, key)
            key.startsWith("kindshift:") -> kindShift(a, b, key.removePrefix("kindshift:"))
            key.startsWith("kind:") -> kind(a, b, key.removePrefix("kind:"))
            key.startsWith("item:") -> item(a, b, key.removePrefix("item:"))
            else -> false
        }
        if (!done) return null
        val out = SetupJson.write(a)
        val before = SetupFacts.read(mine).firstOrNull { it.key == key }?.value
        val after = SetupFacts.read(out).firstOrNull { it.key == key }?.value
        return if (before == after) null else out
    }

    private fun shift(a: MutableMap<String, Any?>, b: Map<*, *>, name: String): Boolean {
        val theirs = (b["shiftTypes"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().firstOrNull { it["name"] == name } ?: return false
        val list = (a["shiftTypes"] as? MutableList<Any?>) ?: return false
        val i = list.indexOfFirst { (it as? Map<*, *>)?.get("name") == name }
        if (i < 0) {
            list += deep(theirs)
            return true
        }
        val m = list[i] as MutableMap<String, Any?>
        for (f in listOf("start", "end", "break", "breakStart", "off")) {
            if (theirs.containsKey(f)) m[f] = theirs[f] else m.remove(f)
        }
        return true
    }

    private fun payOf(a: MutableMap<String, Any?>): MutableMap<String, Any?>? = a["pay"] as? MutableMap<String, Any?>

    private fun rulesOf(a: MutableMap<String, Any?>): MutableMap<String, Any?>? =
        payOf(a)?.let { p -> (p["rules"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { p["rules"] = it } }

    /** The fields each item of the workplace, premiums and holidays is made of. */
    private val FIELDS = mapOf(
        "place:size" to ("pay" to listOf("fiveOrMore")),
        "place:hours" to ("pay" to listOf("monthlyHours")),
        "place:flex" to ("pay" to listOf("flexPeriod", "flexStart")),
        "place:supervisory" to ("rules" to listOf("supervisory")),
        "rate:ot" to ("rules" to listOf("ot")),
        "rate:night" to ("rules" to listOf("night")),
        "rate:hw" to ("rules" to listOf("hw")),
        "rate:ho" to ("rules" to listOf("ho")),
        "rate:window" to ("rules" to listOf("nightStart", "nightEnd")),
        "rule:restDay" to ("rules" to listOf("restDay")),
        "rule:weeklyDay" to ("rules" to listOf("weeklyHolidayDay")),
        "rule:holiday" to ("rules" to listOf("publicHolidays", "holidayWork", "publicHolidayAs", "holidayByStart")),
        "rule:holidaysU5" to ("rules" to listOf("holidaysU5")),
        "rule:breakPay" to ("rules" to listOf("breakPay")),
    )

    private fun pay(a: MutableMap<String, Any?>, b: Map<*, *>, key: String): Boolean {
        val (where, fields) = FIELDS[key] ?: return false
        val theirPay = b["pay"] as? Map<*, *> ?: return false
        val from = if (where == "pay") theirPay else theirPay["rules"] as? Map<*, *> ?: return false
        val to = (if (where == "pay") payOf(a) else rulesOf(a)) ?: return false
        if (key == "rule:holiday") {
            // A kind named for 빨간날 is this phone's kind of the same name, else the built-in one of that id.
            val kind = from["publicHolidayAs"]?.toString()?.let { id -> kindIdHere(a, kindName(b, id)) ?: id.takeIf { it in BUILT_IN.values } }
            if (from["publicHolidayAs"] != null && kind == null) return false
            for (f in fields) if (from.containsKey(f)) to[f] = if (f == "publicHolidayAs") kind else deep(from[f]) else to.remove(f)
            return true
        }
        for (f in fields) if (from.containsKey(f)) to[f] = deep(from[f]) else to.remove(f)
        return true
    }

    /** The name of the kind [id] in file [root]. */
    private fun kindName(root: Map<*, *>, id: String): String =
        SetupFacts.kindLines(root).firstOrNull { it.id == id }?.name ?: BUILT_IN.entries.firstOrNull { it.value == id }?.key ?: "종류 $id"

    /** The id of the kind called [name] in this phone's file, or null when it has none. */
    private fun kindIdHere(a: Map<*, *>, name: String): String? =
        SetupFacts.kindLines(a).firstOrNull { it.name == name }?.id ?: BUILT_IN[name]

    private fun kindIdThere(b: Map<*, *>, name: String): String? =
        SetupFacts.kindLines(b).firstOrNull { it.name == name }?.id ?: BUILT_IN[name]

    /** A kind's pay rule and when it is worked; the amount the user typed for it stays. */
    private fun kind(a: MutableMap<String, Any?>, b: Map<*, *>, name: String): Boolean {
        val there = kindIdThere(b, name) ?: return false
        val here = kindIdHere(a, name) ?: return false
        var changed = false
        val theirLine = (b["extraKinds"] as? String)?.split("\n")?.drop(1)?.firstOrNull { it.split("|").firstOrNull() == there }
        val mine = a["extraKinds"] as? String
        if (theirLine != null && mine != null) {
            val lines = mine.split("\n").toMutableList()
            val i = lines.indexOfFirst { it.split("|").firstOrNull() == here }
            val f = theirLine.split("|")
            if (i > 0 && f.size >= 8) {
                val m = lines[i].split("|").toMutableList()
                // When it is worked (the timing) comes from theirs; its id, name and color stay this phone's.
                if (m.size >= 8) {
                    m[4] = f[4]
                    lines[i] = m.joinToString("|")
                    a["extraKinds"] = lines.joinToString("\n")
                    changed = true
                }
            }
        }
        val theirRule = ((b["pay"] as? Map<*, *>)?.get("rules") as? Map<*, *>)?.let { (it["extra"] as? Map<*, *>)?.get(there) } as? Map<*, *>
        if (theirRule != null) {
            val rules = rulesOf(a) ?: return changed
            val extra = (rules["extra"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { rules["extra"] = it }
            val before = extra[here] as? Map<*, *>
            val next = deep(theirRule) as MutableMap<String, Any?>
            // The won amounts are each person's own: theirs is always 0 from the server.
            for (f in listOf("amount", "bonus")) if (before?.containsKey(f) == true) next[f] = before[f] else next.remove(f)
            extra[here] = next
            changed = true
        }
        return changed
    }

    private fun kindShift(a: MutableMap<String, Any?>, b: Map<*, *>, what: String): Boolean {
        val (name, shift) = what.split("·", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return false
        val there = kindIdThere(b, name) ?: return false
        val here = kindIdHere(a, name) ?: return false
        val theirs = ((((b["pay"] as? Map<*, *>)?.get("rules") as? Map<*, *>)?.get("extraByShift") as? Map<*, *>)?.get(there) as? Map<*, *>)?.get(shift)
        val rules = rulesOf(a) ?: return false
        val by = (rules["extraByShift"] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { rules["extraByShift"] = it }
        val forKind = (by[here] as? MutableMap<String, Any?>) ?: LinkedHashMap<String, Any?>().also { by[here] = it }
        val before = forKind[shift] as? Map<*, *>
        if (theirs == null) forKind.remove(shift) else {
            val next = deep(theirs) as MutableMap<String, Any?>
            for (f in listOf("amount", "bonus")) if (before?.containsKey(f) == true) next[f] = before[f] else next.remove(f)
            forKind[shift] = next
        }
        return true
    }

    /** An allowance's way (how often, which share of the base); the amount the user typed stays, a new one has none. */
    private fun item(a: MutableMap<String, Any?>, b: Map<*, *>, name: String): Boolean {
        val pay = payOf(a) ?: return false
        val items = (pay["items"] as? MutableList<Any?>) ?: ArrayList<Any?>().also { pay["items"] = it }
        val theirs = ((b["pay"] as? Map<*, *>)?.get("items") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().firstOrNull { it["name"] == name }
        val i = items.indexOfFirst { (it as? Map<*, *>)?.get("name") == name }
        if (theirs == null) {
            if (i < 0) return false
            items.removeAt(i)
            return true
        }
        val next = deep(theirs) as MutableMap<String, Any?>
        if (i >= 0) {
            val before = items[i] as Map<*, *>
            for (f in listOf("id", "amount")) if (before.containsKey(f)) next[f] = deep(before[f]) else next.remove(f)
            items[i] = next
        } else {
            // A new allowance: no amount (the server's files have none), the user types theirs.
            next["amount"] = linkedMapOf("entries" to ArrayList<Any?>(), "raise" to null)
            items += next
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun deep(v: Any?): Any? = when (v) {
        is Map<*, *> -> LinkedHashMap<String, Any?>().also { m -> v.forEach { (k, x) -> m[k.toString()] = deep(x) } }
        is List<*> -> v.mapTo(ArrayList()) { deep(it) }
        else -> v
    }
}

/**
 * [PLAZA-09] 고친 점 카드 (결정 D79): after joining, a member sees only what the others changed, item by item: "야간 50% →
 * 60%로 바꾼 사람 3명", with 이 값 쓰기. Each card is one item and one value, adding up the people of every version that has it.
 */
data class RoomCard(
    val key: String,
    val label: String,
    val before: String?,
    val after: String?,
    val persons: Int,
    /** The version to take the value from: the one with the most people behind it. */
    val from: String,
    val canTake: Boolean,
) {
    /** "야간 50% → 60%로 바꾼 사람 3명", "식대 근무일마다 넣은 사람 1명", "식대 뺀 사람 2명". */
    val text: String
        get() = when {
            before == null -> "$label ${after}${if (final(after ?: "") != 0) "을" else "를"} 넣은 사람 ${persons}명"
            after == null -> "$label${if (final(label) != 0) "을" else "를"} 뺀 사람 ${persons}명"
            else -> "$label $before → ${after}${final(after).let { if (it != 0 && it != 8) "으로" else "로" }} 바꾼 사람 ${persons}명"
        }

    private companion object {
        /** The last sound's 받침 (0 none, 8 ㄹ), read as said: "60%" is 퍼센트, "1" 일, "3" 삼, "2" 이. */
        fun final(word: String): Int {
            val ch = word.trimEnd { it == ')' || it == ' ' }.lastOrNull() ?: return 0
            return when (ch) {
                in '가'..'힣' -> (ch - '가') % 28
                '1', '7', '8' -> 8
                '0', '3', '6' -> 1
                else -> 0
            }
        }
    }
}

object RoomCards {
    /** One version beside the user's own: its id, its file and how many people stand behind it. */
    data class Version(val id: String, val text: String, val persons: Int)

    /** The cards for [mine] against [others], the most people first; at most [max]. */
    fun cards(mine: String, others: List<Version>, max: Int = 8): List<RoomCard> {
        data class Acc(val row: CompareRow, var persons: Int, var from: String, var best: Int)
        val acc = LinkedHashMap<Pair<String, String?>, Acc>()
        for (v in others) {
            val n = v.persons.coerceAtLeast(1)
            for (row in SetupCompare.changes(mine, v.text)) {
                val k = row.key to row.after
                val a = acc[k]
                if (a == null) acc[k] = Acc(row, n, v.id, n)
                else {
                    a.persons += n
                    if (n > a.best) { a.best = n; a.from = v.id }
                }
            }
        }
        return acc.values.sortedWith(compareByDescending<Acc> { it.persons }.thenBy { it.row.group.ordinal })
            .take(max)
            .map { RoomCard(it.row.key, it.row.label, it.row.before, it.row.after, it.persons, it.from, SetupPatch.canTake(it.row.key)) }
    }
}
