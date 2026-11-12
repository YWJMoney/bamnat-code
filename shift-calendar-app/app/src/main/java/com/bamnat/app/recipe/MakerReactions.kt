package com.bamnat.app.recipe

import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.app.ui.Fmt
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import org.json.JSONObject

/**
 * 2.0.0 [RVM-36] 검토 HQ-52 (사용자 11-12 20:40 KST "자신이 만든 제작물이나 공유한 정보들의 댓글 및 좋아요가 있었을 때 사용자들과
 * 상호작용하는 모든 알림이 알림함으로"): what people did with the user's works, as rows of the 알림함 in the shape P set (HQ-49: "내 작품
 * ‘밤바다’" · "댓글 2개 · 좋아요 5개").
 *
 * - One row a work under 사람들 반응 ([workKey]): its 글 by kind, 좋아요 and 새로 넣은 분 together. While the row stays in the 알림함 the
 *   numbers add up ([Tally]); once it is opened, closed or cleared, the next one starts from what came after.
 * - 고친 판 of my work: a row each. 새 팔로워: one row that adds up ([FOLLOWERS_KEY]).
 * - What the review decided (올라감, 돌려받음, 가려짐, 다시 보임): under 안내, one row a work, the newest decision.
 * - 안좋아요 and reports are never told (조건 16); the server does not even send them.
 *
 * A tap goes to the work, to its 이야기 when a 글 came ([TALK]); a decision that took the work off 장터 goes to 내 작품.
 * Everything is worked out from what the server already sends this phone for 내 프로필 (rcInbox); nothing new leaves the phone.
 */
internal object MakerReactions {
    /** '다시 보지 않기' on any of these rows turns all of them off. */
    const val KIND = "market.reaction"
    /** The review's decisions, turned off on their own. */
    const val KIND_DECIDED = "market.decided"
    const val FOLLOWERS_KEY = "market.followers"
    /** After a work's code in a row's payload: open it at its 이야기. */
    const val TALK = "#talk"

    fun workKey(code: String) = "market.work:$code"
    fun decidedKey(code: String) = "market.decided:$code"
    fun remixKey(code: String) = "market.remix:$code"

    /** What one work's row has counted while it stays in the 알림함. [talk] by the 글's kind; [quote] the one 글 when it is the only one. */
    data class Tally(
        val title: String = "", val talk: Map<String, Int> = emptyMap(), val likes: Int = 0, val uses: Int = 0, val quote: String? = null,
        val at: Long = 0,
    ) {
        val talks: Int get() = talk.values.sum()
        val count: Int get() = talks + likes + uses
    }

    /** What a round worked out: the rows to put in, and what to keep for the next round. */
    data class Round(
        val rows: List<InboxRow>, val tallies: Map<String, Tally>, val totals: Map<String, Int>, val told: List<String>, val followers: Int,
    )

    /** The 글 kinds in the order a row says them (a question waits for an answer, so it comes first). */
    private val TALK_ORDER = listOf("question", "review", "opinion", "idea")
    private val TALK_WORD = mapOf("question" to "질문", "review" to "후기", "opinion" to "의견", "idea" to "제안")

    /**
     * The rows for [box]. [present]: the keys of rows still in the 알림함. [tallies]: what each work's row counted so far; [totals]: each
     * work's 넣어 본 분 the last round saw; [told]: 글 and 고친 판 already counted; [followers]: the 새 팔로워 row's number so far.
     */
    fun round(
        box: ProfileInbox, present: Set<String>, tallies: Map<String, Tally>, totals: Map<String, Int>, told: Set<String>, followers: Int,
        now: Long,
    ): Round {
        val nextTallies = tallies.filterKeys { workKey(it) in present }.toMutableMap()
        val nextTotals = totals.toMutableMap()
        val newlyTold = ArrayList<String>()
        val touched = LinkedHashSet<String>()
        fun tally(code: String, title: String): Tally = (nextTallies[code] ?: Tally()).let { if (title.isNotEmpty()) it.copy(title = title) else it }
        fun ms(r: ProfileInbox.Reaction) = r.at?.toEpochMilli() ?: now

        box.likes.filter { it.kind == "like" && it.count > 0 }.forEach { r ->
            val t = tally(r.code, r.title)
            nextTallies[r.code] = t.copy(likes = t.likes + r.count, at = maxOf(t.at, ms(r)))
            touched += r.code
        }
        box.talk.filter { it.kind in TALK_WORD }.reversed().forEach { r ->
            val id = "t:" + (r.post ?: "${r.code}:${r.at}")
            if (id in told || id in newlyTold) return@forEach
            newlyTold += id
            val t = tally(r.code, r.title)
            val talk = t.talk + (r.kind to (t.talk[r.kind] ?: 0) + 1)
            nextTallies[r.code] = t.copy(talk = talk, quote = if (t.talks == 0) quote(r) else null, at = maxOf(t.at, ms(r)))
            touched += r.code
        }
        box.uses.filter { it.kind == "use" }.forEach { r ->
            val total = r.total
            val last = nextTotals[r.code]
            val fresh = if (last == null || total == null) r.count else total - last
            if (total != null) nextTotals[r.code] = maxOf(total, last ?: 0)
            if (fresh <= 0) return@forEach
            val t = tally(r.code, r.title)
            nextTallies[r.code] = t.copy(uses = t.uses + fresh, at = maxOf(t.at, ms(r)))
            touched += r.code
        }

        val rows = ArrayList<InboxRow>()
        touched.forEach { code -> nextTallies[code]?.takeIf { it.count > 0 }?.let { rows += workRow(code, it) } }

        box.remixes.forEach { (code, title, parent) ->
            val id = "x:$code"
            if (id in told || id in newlyTold) return@forEach
            newlyTold += id
            rows += InboxRow(
                key = remixKey(code), kind = KIND, section = InboxSection.PEOPLE, title = "내 작품 ‘$parent’",
                line = "고친 판 ‘$title’${Fmt.iga("‘$title’")} 올라왔어요", target = NoticeLinks.Target.RECIPE, payload = code, at = now,
            )
        }

        var follow = if (FOLLOWERS_KEY in present) followers else 0
        if (box.newFollowers > 0) {
            follow += box.newFollowers
            rows += InboxRow(
                key = FOLLOWERS_KEY, kind = KIND, section = InboxSection.PEOPLE, title = "내 프로필", line = "새 팔로워 ${follow}명",
                feature = FeatureRoute(Place.PROFILE), at = now, count = follow,
            )
        }

        box.decided.filter { it.kind == "decided" }.forEach { r ->
            val id = "d:${r.code}:${r.at}"
            if (id in told || id in newlyTold) return@forEach
            newlyTold += id
            decidedRow(r, ms(r))?.let { rows += it }
        }
        return Round(rows, nextTallies, nextTotals, newlyTold, follow)
    }

    /** "질문: 야간에도 되나요" — the one 글, short. */
    private fun quote(r: ProfileInbox.Reaction): String? {
        val body = r.body?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (body.length <= QUOTE) return TALK_WORD[r.kind] + ": " + body
        // Cut between words, not in one (조건 4: "바뀌나요? 궁…" read as a word cut short).
        val cut = body.take(QUOTE).let { t -> t.lastIndexOf(' ').takeIf { it >= QUOTE / 2 && body[QUOTE] != ' ' }?.let { t.take(it) } ?: t }
        return TALK_WORD[r.kind] + ": " + cut.trimEnd() + "…"
    }

    /** One work's row: "질문 1개 · 후기 2개 · 좋아요 5개 · 새로 넣은 분 3명", or the one 글 itself when nothing else came. */
    fun workRow(code: String, t: Tally): InboxRow {
        // The 글 itself only when it is all the row says (with numbers beside it the row went to three lines, rvm_64).
        val talk = if (t.talks == 1 && t.quote != null && t.likes == 0 && t.uses == 0) listOf(t.quote)
        else TALK_ORDER.mapNotNull { k -> t.talk[k]?.takeIf { it > 0 }?.let { "${TALK_WORD[k]} ${it}개" } }
        val line = (talk + listOfNotNull(t.likes.takeIf { it > 0 }?.let { "좋아요 ${it}개" }, t.uses.takeIf { it > 0 }?.let { "새로 넣은 분 ${it}명" }))
            .joinToString(" · ")
        return InboxRow(
            key = workKey(code), kind = KIND, section = InboxSection.PEOPLE, title = "내 작품 ‘${t.title}’", line = line,
            target = NoticeLinks.Target.RECIPE, payload = if (t.talks > 0) code + TALK else code, at = t.at, count = t.count,
        )
    }

    /** What the review decided, in a person's words; a work no longer in 장터 opens 내 작품. */
    fun decidedRow(r: ProfileInbox.Reaction, at: Long): InboxRow? {
        val note = r.note?.trim()?.takeIf { it.isNotEmpty() }
        val line = when (r.state) {
            "visible" -> if (r.again) "다시 장터에 보여요" else "장터에 올라갔어요. 이제 누구나 볼 수 있어요"
            "returned" -> note?.let { "돌려 드렸어요: $it" } ?: "고칠 곳이 있어 돌려 드렸어요. 고쳐서 다시 올릴 수 있어요"
            "hidden" -> if (r.why == "flags") "잠시 가려 두었어요. 살펴본 뒤 알려 드릴게요"
                else note?.let { "장터에서 내렸어요: $it" } ?: "장터 규칙에 맞지 않아 장터에서 내렸어요"
            else -> return null
        }
        val open = r.state == "visible"
        return InboxRow(
            key = decidedKey(r.code), kind = KIND_DECIDED, section = InboxSection.NOTICE, title = "내 작품 ‘${r.title}’", line = line,
            target = if (open) NoticeLinks.Target.RECIPE else null, payload = if (open) r.code else null,
            feature = if (open) null else FeatureRoute(Place.RECIPE_MINE), at = at,
        )
    }

    /** A row's payload as a work's code, and whether to open its 이야기; null when it is not a work's code. */
    fun opened(payload: String?): Pair<String, Boolean>? {
        val talk = payload?.endsWith(TALK) == true
        val code = payload?.removeSuffix(TALK)?.takeIf { CODE.matches(it) } ?: return null
        return code to talk
    }

    fun encode(tallies: Map<String, Tally>): String = JSONObject().apply {
        tallies.forEach { (code, t) ->
            put(code, JSONObject().put("title", t.title).put("talk", JSONObject(t.talk)).put("likes", t.likes).put("uses", t.uses)
                .put("at", t.at).apply { t.quote?.let { put("quote", it) } })
        }
    }.toString()

    fun decode(s: String?): Map<String, Tally> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { code ->
            val t = o.getJSONObject(code)
            val talk = t.optJSONObject("talk")?.let { k -> k.keys().asSequence().associateWith { k.optInt(it) } }.orEmpty()
            Tally(t.optString("title"), talk, t.optInt("likes"), t.optInt("uses"), t.optString("quote").takeIf { t.has("quote") }, t.optLong("at"))
        }
    }.getOrDefault(emptyMap())

    fun encodeTotals(totals: Map<String, Int>): String = JSONObject(totals as Map<*, *>).toString()

    fun decodeTotals(s: String?): Map<String, Int> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optInt(it) }
    }.getOrDefault(emptyMap())

    private const val QUOTE = 24
    private val CODE = Regex("[A-Za-z0-9]{4,16}")
}
