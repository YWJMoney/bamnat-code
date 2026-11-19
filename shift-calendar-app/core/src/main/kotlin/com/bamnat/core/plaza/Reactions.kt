package com.bamnat.core.plaza

/**
 * 2.0.0 [RVN-14] 검토 HQ-51 알림함: 내 것에 온 반응 (사용자 2026-11-12 20:38~20:40 KST "자신이 만든 제작물이나 공유한 정보들의 댓글 및
 * 좋아요가 있었을때 사용자들과 상호작용하는 모든 알림이 알림으로 가야해"). One line of what others did to what this phone put out, as the
 * server's `/api/digest` `reactions` sends it: never who, only how many.
 */
data class Reaction(
    /** answer, thread, agree, report_vote, rate_vote, request_like, request_take or contest_pick. */
    val kind: String,
    /** The thing's id: a post, "<company>/<setup>", a rate, a request or a contest entry's code. */
    val target: String,
    /** Its name as the row says it (the 광장, the company, the trade, the request's first words, the work's title). */
    val title: String,
    val count: Int,
    val atMillis: Long,
    /** A post's room (style-shift …, or a company's id), for opening it. */
    val room: String? = null,
    /** A post's talk in [room]: "room" for a 광장 or a company's room, a rule set's id for its talk (null from a server before 2.0.0). */
    val setup: String? = null,
    /** A post's kind when not plain talk (question, tip, proposal …). */
    val postKind: String? = null,
    val up: Int = 0,
    val down: Int = 0,
    /** contest_pick: the contest's theme. */
    val theme: String? = null,
)

/**
 * The reactions as the 알림함 shows them: one row per thing (likes and answers on the same post are one row, "답 3 · 나도요 2", and a
 * newer count takes the older row's place by [Row.key]), newest first, with the kinds the user turned off in 설정 › 알림 left out.
 * The keys and kinds are the ones 갈래 M uses for 내 작품 반응 (HQ-52, `market.work:<code>`, `market.reaction`).
 */
object Reactions {
    /** What 설정 › 알림 turns on and off, one switch each, in the order shown. */
    enum class Group(val kind: String, val label: String, val about: String) {
        PLAZA("plaza.reaction", "광장의 내 글", "답, 나도요, 공감이 오면"),
        BOARD("board.reaction", "내가 알린 회사 규칙", "맞아요·달라요가 오면"),
        RATE("plaza.rate", "내가 보낸 시세", "맞아요·달라요가 오면"),
        REQUEST("village.request", "내 부탁", "좋아요가 오거나 누군가 만들기 시작하면"),
        CONTEST("village.contest", "이달의 공모", "내 작품이 뽑히면"),
    }

    /** A row for the 알림함: [key] is the same for the same thing, so a newer count replaces the older row. */
    data class Row(val key: String, val group: Group, val title: String, val line: String, val target: String, val atMillis: Long,
        val room: String? = null, val setup: String? = null, val count: Int = 1,
        /** The 광장's, company's or trade's name the row starts with (null when the server sent none). */
        val place: String? = null)

    fun group(kind: String): Group? = when (kind) {
        "answer", "thread", "agree" -> Group.PLAZA
        "report_vote" -> Group.BOARD
        "rate_vote" -> Group.RATE
        "request_like", "request_take" -> Group.REQUEST
        "contest_pick" -> Group.CONTEST
        else -> null
    }

    fun key(r: Reaction): String? = when (group(r.kind)) {
        Group.PLAZA -> "plaza.post:${r.target}"
        Group.BOARD -> "board.report:${r.target}"
        Group.RATE -> "plaza.rate:${r.target}"
        Group.REQUEST -> "village.request:${r.target}"
        Group.CONTEST -> "village.contest:${r.target}"
        null -> null
    }

    /** The rows, newest first: a kind this app does not know (a newer server's) or one turned [off] is left out. */
    fun rows(list: List<Reaction>, off: Set<String> = emptySet()): List<Row> =
        list.filter { r -> r.count > 0 && group(r.kind)?.kind?.let { it !in off } == true }
            .groupBy { key(it)!! }
            .map { (key, same) -> row(key, same) }
            .sortedByDescending { it.atMillis }

    private fun row(key: String, same: List<Reaction>): Row {
        val first = same.maxBy { it.atMillis }
        val g = group(first.kind)!!
        val words = same.sortedBy { order(it.kind) }.flatMap(::words).joinToString(" · ")
        val title = when {
            same.any { it.kind == "contest_pick" } -> "이달의 공모에 내 작품이 뽑혔어요"
            same.any { it.kind == "request_take" } -> "내 부탁을 누군가 만들기 시작했어요"
            g == Group.PLAZA && same.all { it.kind == "thread" } -> "내가 답한 글에 새 답이 왔어요"
            g == Group.PLAZA -> "내 글에 반응이 왔어요"
            g == Group.BOARD -> "내가 알린 규칙에 반응이 왔어요"
            g == Group.RATE -> "내가 보낸 시세에 반응이 왔어요"
            else -> "내 부탁에 반응이 왔어요"
        }
        val place = same.firstNotNullOfOrNull { it.title.takeIf(String::isNotBlank) }
        val line = listOfNotNull(place, words.ifEmpty { null }).joinToString(" · ")
        return Row(key, g, title, line, first.target, first.atMillis, same.firstNotNullOfOrNull { it.room },
            same.firstNotNullOfOrNull { it.setup }, same.sumOf { it.count }.coerceAtLeast(1), place)
    }

    private fun order(kind: String) = listOf("answer", "thread", "agree", "report_vote", "rate_vote", "request_like", "request_take", "contest_pick")
        .indexOf(kind)

    /** "답 3", "나도요 2", "맞아요 2 · 달라요 1": a 0 is left out. */
    private fun words(r: Reaction): List<String> = when (r.kind) {
        "answer" -> listOf("답 ${r.count}")
        "thread" -> listOf("새 답 ${r.count}")
        "agree" -> {
            // A kind of work's 광장 has 나도요 only; a company room's or a rule's talk has 공감·비공감, and a proposal both.
            val styleRoom = r.room?.startsWith("style-") == true
            if (styleRoom && r.postKind != "proposal") listOfNotNull("나도요 ${r.up}".takeIf { r.up > 0 })
            else listOfNotNull("공감 ${r.up}".takeIf { r.up > 0 }, "비공감 ${r.down}".takeIf { r.down > 0 && r.postKind == "proposal" })
        }
        "report_vote", "rate_vote" -> listOfNotNull("맞아요 ${r.up}".takeIf { r.up > 0 }, "달라요 ${r.down}".takeIf { r.down > 0 })
        "request_like" -> listOf("좋아요 ${r.count}")
        "contest_pick" -> listOfNotNull(r.theme?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }

    /** How '다시 보지 않기' and 설정 › 알림 name [g] when it is off (기능 켜고 끄기 lists it by this). */
    fun offLabel(g: Group): String = when (g) {
        Group.PLAZA -> "광장의 내 글에 온 답·나도요·공감"
        Group.BOARD -> "내가 알린 회사 규칙의 맞아요·달라요"
        Group.RATE -> "내가 보낸 시세의 맞아요·달라요"
        Group.REQUEST -> "내 부탁의 좋아요·만들기 시작"
        Group.CONTEST -> "이달의 공모에 뽑힘"
    }

    /**
     * 2.0.0 [RVN-19] HQ-51 What a row still in the 알림함 ([kept], not opened yet) says with [new] since added: the same thing's same
     * kind summed (답 2 then 답 1 is 답 3), the newest time and words kept. A row that was opened or wiped starts again from [new].
     */
    fun merge(kept: List<Reaction>, new: List<Reaction>): List<Reaction> {
        val out = LinkedHashMap<Pair<String, String>, Reaction>()
        for (r in kept + new) {
            val k = (key(r) ?: continue) to r.kind
            val was = out[k]
            out[k] = if (was == null) r else r.copy(count = was.count + r.count, up = was.up + r.up, down = was.down + r.down,
                atMillis = maxOf(was.atMillis, r.atMillis), title = r.title.ifBlank { was.title }, room = r.room ?: was.room,
                setup = r.setup ?: was.setup, postKind = r.postKind ?: was.postKind, theme = r.theme ?: was.theme)
        }
        return out.values.toList()
    }

    /**
     * An answer under my post in a 광장 or a company's room: the phone counts these itself (answers it has not seen, VillageReplies),
     * so the server's count of them is not added on top.
     */
    fun roomAnswer(r: Reaction): Boolean = r.kind == "answer" && (r.setup == null || r.setup == "room")

    /**
     * [kept] with the answers under my room posts as this phone counts them ([answers]: not seen yet, so a count, not an addition):
     * the larger of the two, its time moved only when it grew. A post whose answers were all read keeps what its row said.
     */
    fun withAnswers(kept: List<Reaction>, answers: List<Reaction>): List<Reaction> {
        val now = answers.filter { it.kind == "answer" && it.count > 0 }.associateBy { key(it) }
        val rest = kept.filter { !(it.kind == "answer" && key(it) in now) }
        val set = now.values.map { a ->
            val was = kept.firstOrNull { it.kind == "answer" && key(it) == key(a) }
            if (was == null || a.count > was.count) a.copy(room = a.room ?: was?.room, setup = a.setup ?: was?.setup) else was
        }
        return rest + set
    }

    /** The kinds turned off, as 설정 keeps them ("plaza.rate,village.contest"). */
    fun decodeOff(text: String): Set<String> = text.split(',').map(String::trim).filter { k -> Group.entries.any { it.kind == k } }.toSet()

    fun encodeOff(off: Set<String>): String = Group.entries.map { it.kind }.filter { it in off }.joinToString(",")
}
