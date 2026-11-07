package com.bamnat.core.layout

/**
 * [NAV2-01] 1.39 (사용자 2026-11-07 "메인 5칸을 사용자가 원하는 기능들로 채울 수 있게, 하나의 탭에 기능들을 추가하면 탭 클릭 시
 * 리스트가 떠서 바로 이동"): the bottom bar as up to [MAX] places the user fills. A place holds one screen ([One]: a tab such as
 * "calendar", or another screen such as "f:SLEEP") or a 묶음 of several ([Group]), which opens a list to pick from.
 *
 * Saved as one line ([encode]): places split by ';', a 묶음 as `g|이름|key+key+…`. Nothing saved is the bar made from the old
 * [TabOrder] ([fromOrder]): the five tabs in their order, with 설정 moved to the 나 button (it stays a place only for a phone that
 * had put it first, its home). A screen is in one place only. Every tab not in a place stays reachable from the 나 button.
 */
object TabSlots {
    const val MAX = 5
    /**
     * 1.39 [NAV2-07] (조사 19장, 총괄 11-07 1.39로): beside the rail of an unfolded phone there is room for two more places. A phone bar
     * (and the cover screen) shows the first [MAX]; the screens of the places after them wait by the 나 button ([offBar]).
     */
    const val MAX_WIDE = 7
    const val MAX_IN_GROUP = 10
    const val NAME_MAX = 8
    /** A screen that is not one of the tabs, by its feature place ("f:SLEEP"); the app knows which ones it can open. */
    const val FEATURE = "f:"
    /** Another screen of the app's own ("x:studio"). */
    const val EXTRA = "x:"

    sealed interface Slot {
        val keys: List<String>
    }

    data class One(val key: String) : Slot {
        override val keys: List<String> get() = listOf(key)
    }

    data class Group(val name: String, val members: List<String>) : Slot {
        override val keys: List<String> get() = members
    }

    private const val GROUP = "g"
    private const val BAD = ";|+\n\r"

    /** A 묶음's name as it can be kept: without the characters the line is split by, at most [NAME_MAX] letters. */
    fun cleanName(name: String): String = name.filterNot { it in BAD }.trim().take(NAME_MAX)

    fun encode(slots: List<Slot>): String = slots.joinToString(";") { s ->
        when (s) {
            is One -> s.key
            is Group -> listOf(GROUP, cleanName(s.name), s.members.joinToString("+")).joinToString("|")
        }
    }

    /** One place's own key (unique in a bar: a screen is in one place only), for moving places about. */
    fun key(slot: Slot): String = encode(listOf(slot))

    /**
     * The places in [text]: keys the app does not know ([known]) are dropped, a screen already in an earlier place is dropped,
     * a 묶음 left with one screen becomes that screen, an empty one goes, and at most [MAX_WIDE] places and [MAX_IN_GROUP] in a 묶음.
     */
    fun decode(text: String, known: (String) -> Boolean): List<Slot> {
        val seen = HashSet<String>()
        fun keep(k: String): Boolean = k.isNotBlank() && known(k) && seen.add(k)
        val out = ArrayList<Slot>()
        for (part in text.split(';')) {
            if (out.size >= MAX_WIDE) break
            val p = part.trim()
            if (p.isEmpty()) continue
            if (p.startsWith("$GROUP|")) {
                val bits = p.split('|')
                if (bits.size < 3) continue
                val members = bits[2].split('+').map { it.trim() }.filter(::keep).take(MAX_IN_GROUP)
                when (members.size) {
                    0 -> Unit
                    1 -> out += One(members.single())
                    else -> out += Group(cleanName(bits[1]).ifEmpty { DEFAULT_GROUP_NAME }, members)
                }
            } else if (keep(p)) {
                out += One(p)
            }
        }
        return out
    }

    /** The bar of a phone that never filled its places: the tabs in their saved order, 설정 by the 나 button unless it was first. */
    fun fromOrder(order: List<String>): List<Slot> {
        val tabs = TabOrder.of(order)
        val kept = if (tabs.first() == TabOrder.SETTINGS) tabs.take(MAX) else tabs.filter { it != TabOrder.SETTINGS }.take(MAX)
        return kept.map(::One)
    }

    /** The bar to show: the places saved, or, with none (or none left that this version knows), the one from the tab order. */
    fun resolve(saved: String, order: List<String>, known: (String) -> Boolean): List<Slot> =
        if (saved.isBlank()) fromOrder(order) else decode(saved, known).ifEmpty { fromOrder(order) }

    /**
     * The tab order that makes [slots] when they are only tabs (no other screen, no 묶음), so a bar of tabs moved about is kept as
     * the tab order it always was (themes and 화면 배치 pieces carry that); null when it needs the places saved.
     */
    fun asOrder(slots: List<Slot>): List<String>? {
        val keys = slots.map { (it as? One)?.key ?: return null }
        if (keys.any { it !in TabOrder.DEFAULT }) return null
        val order = keys + TabOrder.DEFAULT.filter { it !in keys }
        return order.takeIf { fromOrder(it) == slots }
    }

    /** What to save for [slots]: nothing when it is just the bar the tab order makes, so that stays the one place to change. */
    fun toSave(slots: List<Slot>, order: List<String>): String = if (slots == fromOrder(order)) "" else encode(slots)

    /** The screen the app opens on and back ends at: the first tab in the bar (in a 묶음 too), else 달력. */
    /** 1.39 [NAV2-07] The places a bar shows: all of them (up to [MAX_WIDE]) beside the rail, the first [MAX] on a phone bar. */
    fun onBar(slots: List<Slot>, wide: Boolean): List<Slot> = slots.take(if (wide) MAX_WIDE else MAX)

    /** 1.39 [NAV2-07] The screens of the places a phone bar has no room for, in their order: the 나 button lists them. */
    fun offBar(slots: List<Slot>, wide: Boolean): List<String> = slots.drop(onBar(slots, wide).size).flatMap { it.keys }

    fun home(slots: List<Slot>): String = slots.flatMap { it.keys }.firstOrNull { it in TabOrder.DEFAULT } ?: TabOrder.CALENDAR

    fun shows(slots: List<Slot>, key: String): Boolean = slots.any { key in it.keys }

    /** The tabs no place holds, which the 나 button lists so none is ever out of reach. */
    fun missing(slots: List<Slot>): List<String> = TabOrder.DEFAULT.filter { !shows(slots, it) }

    /** [slots] with the place at [index] moved to [to]. */
    fun move(slots: List<Slot>, index: Int, to: Int): List<Slot> {
        if (index !in slots.indices) return slots
        val target = to.coerceIn(0, slots.lastIndex)
        if (target == index) return slots
        return slots.toMutableList().apply { add(target, removeAt(index)) }
    }

    /** [slots] in the order of their [key]s in [keys] (after a drag of the bar); places not named keep their turn at the end. */
    fun ordered(slots: List<Slot>, keys: List<String>): List<Slot> {
        val byKey = slots.associateBy(::key)
        val first = keys.mapNotNull { byKey[it] }
        return first + slots.filter { it !in first }
    }

    /** [slots] with [key] taken out of wherever it was (a 묶음 of two becomes the one left, a lone place goes). */
    fun without(slots: List<Slot>, key: String): List<Slot> = slots.mapNotNull { s ->
        when (s) {
            is One -> s.takeIf { it.key != key }
            is Group -> {
                val left = s.members - key
                when (left.size) {
                    s.members.size -> s
                    0 -> null
                    1 -> One(left.single())
                    else -> s.copy(members = left)
                }
            }
        }
    }

    /**
     * [slots] with [key] put in the place at [index]: a place holding one screen becomes a 묶음 named [name] of the two, a 묶음 takes
     * one more (up to [MAX_IN_GROUP]). The screen leaves any other place first. An [index] past the end makes a new place (up to [max]:
     * [MAX], or [MAX_WIDE] in the editor of an unfolded phone).
     */
    fun addTo(slots: List<Slot>, index: Int, key: String, name: String = DEFAULT_GROUP_NAME, max: Int = MAX): List<Slot> {
        val at = slots.getOrNull(index)
        if (at != null && key in at.keys) return slots
        val rest = without(slots, key)
        // Where the place is now: taking the screen out may have dropped a place before it.
        val i = if (at == null) -1 else rest.indexOfFirst { s -> at.keys.any { it != key && it in s.keys } }
        if (i < 0) return if (rest.size < max.coerceAtMost(MAX_WIDE)) rest + One(key) else slots
        return rest.toMutableList().apply {
            this[i] = when (val s = this[i]) {
                is One -> Group(cleanName(name).ifEmpty { DEFAULT_GROUP_NAME }, listOf(s.key, key))
                is Group -> if (s.members.size >= MAX_IN_GROUP) return slots else s.copy(members = s.members + key)
            }
        }
    }

    /** [slots] with the place at [index] holding [key] alone instead (it leaves any other place). */
    fun replace(slots: List<Slot>, index: Int, key: String): List<Slot> {
        val at = slots.getOrNull(index) ?: return slots
        if (at is One && at.key == key) return slots
        val marker = "\u0000"
        val marked = slots.toMutableList().apply { this[index] = One(marker) }
        return without(marked, key).map { if (it is One && it.key == marker) One(key) else it }
    }

    /** [slots] without the place at [index]; the last place cannot go. */
    fun remove(slots: List<Slot>, index: Int): List<Slot> =
        if (slots.size <= 1 || index !in slots.indices) slots else slots.toMutableList().apply { removeAt(index) }

    /** A 묶음 renamed. */
    fun rename(slots: List<Slot>, index: Int, name: String): List<Slot> {
        val s = slots.getOrNull(index) as? Group ?: return slots
        val n = cleanName(name).ifEmpty { return slots }
        return slots.toMutableList().apply { this[index] = s.copy(name = n) }
    }

    /** A 묶음's screens in a new order. */
    fun moveMember(slots: List<Slot>, index: Int, from: Int, to: Int): List<Slot> {
        val s = slots.getOrNull(index) as? Group ?: return slots
        if (from !in s.members.indices) return slots
        val t = to.coerceIn(0, s.members.lastIndex)
        val m = s.members.toMutableList().apply { add(t, removeAt(from)) }
        return slots.toMutableList().apply { this[index] = s.copy(members = m) }
    }

    const val DEFAULT_GROUP_NAME = "모음"
}
