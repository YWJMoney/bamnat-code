package com.bamnat.core.plaza

import com.bamnat.core.remote.Json
import java.text.Normalizer

/**
 * [PLZD-07][PLZD-10] 1.30 묶음 BD (사용자 2026-10-27: "같은 회사의 여러개의 정보들이 있으면 도대체 뭐가 맞는지 보기도 힘들고 설정도
 * 어렵다"): a company's versions as the screen shows them, one department at a time. Each department has one 대표 (the version
 * the server marked its lead, else the one in use, else the one most stand behind) and the others under it, folded, as a
 * wiki shows its page and keeps the rest in the history, or an answer site keeps the accepted answer on top.
 */
object DeptGroups {
    /** One version as the company screen has it ([com.bamnat.app.company.SharedSetup] reduced to what grouping needs). */
    data class Item(
        val id: String,
        val dept: String,
        val adopted: Boolean,
        val lead: Boolean = false,
        val doubted: Boolean = false,
        val support: Int = 0,
        val persons: Int = 0,
    )

    /** One department: its 대표 [lead] and the [others] of it, the ones most stand behind first. */
    data class Group(val dept: String, val lead: Item, val others: List<Item>) {
        val key: String get() = key(dept)
        /** "회사 전체" for the company's own. */
        val title: String get() = dept.ifBlank { "회사 전체" }
    }

    /**
     * [items] by department, in the order a person looks for them: 회사 전체 first, then the departments most stand behind,
     * a department's lead first within it.
     */
    fun of(items: List<Item>): List<Group> {
        val byDept = LinkedHashMap<String, MutableList<Item>>()
        items.forEach { byDept.getOrPut(key(it.dept)) { ArrayList() } += it }
        return byDept.values.map { list ->
            val lead = list.firstOrNull { it.lead && !it.doubted }
                ?: list.firstOrNull { it.adopted && !it.doubted }
                ?: list.filter { !it.doubted }.maxWithOrNull(compareBy<Item>({ it.support }, { it.persons }))
                ?: list.first()
            Group(lead.dept, lead, list.filter { it !== lead }.sortedWith(compareByDescending<Item> { it.support }.thenByDescending { it.persons }))
        }.sortedWith(compareByDescending<Group> { it.key.isEmpty() }
            .thenByDescending { g -> (listOf(g.lead) + g.others).sumOf { it.support + it.persons } })
    }

    /** A department's name as the server's normName keys it: no spaces, marks or case. */
    fun key(dept: String): String = Normalizer.normalize(dept, Normalizer.Form.NFKC).lowercase().filter { it.isLetterOrDigit() }

    /**
     * [PLZD-10] (계약) The kind of work a setup is, in one line for a card or a confirm sentence: "2교대 · 주주야야휴휴 · 급여 방식
     * 있음". The order is said in the first letters of its shifts (휴 for a day off) while it is short, its length past that.
     * Null when [text] is not a company file or says nothing of the work and has no pay.
     */
    fun summary(text: String): String? {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        if (root["format"] != "bamnat-company") return null
        val shape = SameSetup.shape(root)
        val pay = if (root.containsKey("pay")) "급여 방식 있음" else null
        val work = shape?.let { s ->
            when {
                s.roster || s.weekly -> s.words
                else -> {
                    val order = spoken(root)
                    if (order != null && order.length <= MAX_SPOKEN) "${s.words.substringBefore(" · ")} · $order" else s.words
                }
            }
        }
        return listOfNotNull(work, pay).joinToString(" · ").ifEmpty { null }
    }

    /**
     * [PLZD-10] (계약) One line when the calendar's own work ([mine]) has another shape than a version about to be used
     * ([theirs]): "내 달력은 3교대인데 이 판은 2교대예요". Null when they fit or either says nothing of the work.
     */
    fun mismatch(mine: String?, theirs: String): String? {
        val a = mine?.let(SameSetup::shape) ?: return null
        val b = SameSetup.shape(theirs) ?: return null
        if (SameSetup.sameShape(a, b)) return null
        val (x, y) = if (a.works == b.works && !a.weekly && !b.weekly && !a.roster && !b.roster) "${a.days}일 순서" to "${b.days}일 순서"
            else a.words.substringBefore(" · ") to b.words.substringBefore(" · ")
        return "내 달력은 ${x}인데 이 판은 ${y}예요"
    }

    /** Past this many days the order is said by its length. */
    private const val MAX_SPOKEN = 12

    /** "주주야야휴휴": the first letter of each day's shift, 휴 for a day off or none. */
    private fun spoken(root: Map<*, *>): String? {
        val off = (root["shiftTypes"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .filter { it["off"] == true }.mapNotNull { it["name"] as? String }.toSet()
        val steps = ((root["rotation"] as? Map<*, *>)?.get("steps") as? List<*>) ?: return null
        val turn = SameSetup.period(steps)
        if (turn.isEmpty()) return null
        return turn.joinToString("") { n -> (n as? String)?.takeIf { it.isNotBlank() && it !in off }?.take(1) ?: "휴" }
    }
}
