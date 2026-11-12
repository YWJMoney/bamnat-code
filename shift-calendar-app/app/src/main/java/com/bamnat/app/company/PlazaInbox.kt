package com.bamnat.app.company

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.inbox.Inbox
import com.bamnat.app.inbox.InboxRow
import com.bamnat.app.inbox.InboxSection
import com.bamnat.core.plaza.PlazaNews
import com.bamnat.core.plaza.PlazaNewsItem
import com.bamnat.core.plaza.Reaction
import com.bamnat.core.plaza.Reactions
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2.0.0 [RVN-19] 검토 HQ-51 (사용자 11-12 20:38~20:40 KST "자신이 만든 제작물이나 공유한 정보들의 댓글 및 좋아요가 있었을때 사용자들과
 * 상호작용하는 모든 알림이 알림으로 가야해", "달력 위에 뜨는 것은 꼭 봐야 하는 것들로만"): what others did to what this phone put out on
 * the plaza, as rows of the 알림함's 사람들 반응 (P #492's [Inbox]), one row per thing, a tap on the very place.
 *
 * Two roads in, no new ask: the daily `/api/digest` (`reactions`, since its last ask: 나도요·공감, 맞아요·달라요, 시세, 부탁, 공모, answers in a
 * rule's talk) and the answers under my 광장 posts that this phone counts itself ([VillageReplies], on opening the app and on an 답 push),
 * so a new answer is in the bell without waiting for the day. While a row is still in the 알림함 (not opened, not wiped) what comes next
 * adds to it ("답 2" and later "답 1" is "답 3"); once it was opened it starts again. The reactions behind the rows are kept on this phone
 * only ([PREFS], not in the backup) and only while their rows are there. Nothing is sent anywhere.
 */
object PlazaInbox {
    private const val PREFS = "plaza_inbox"
    private const val K_KEPT = "kept"

    /** The digest's [reactions] into the 알림함 (the answers under my room posts left to [answers] when it can count them). */
    fun fromDigest(c: AppContainer, reactions: List<Reaction>, answersCounted: Boolean) {
        val new = if (answersCounted) reactions.filterNot(Reactions::roomAnswer) else reactions
        update(c) { kept -> Reactions.merge(kept, new) }
    }

    /** The answers under my 광장 posts this phone has not seen ([fresh] by post id, from [VillageReplies.fresh]) into the 알림함. */
    fun answers(c: AppContainer, posts: List<MyPost>, fresh: Map<String, Int>, now: Long = com.bamnat.core.time.Today.millis()) {
        val byId = posts.associateBy { it.id }
        val list = fresh.mapNotNull { (id, n) ->
            val p = byId[id]?.takeIf { n > 0 && it.parent == null } ?: return@mapNotNull null
            Reaction(kind = "answer", target = id, title = p.roomName, count = n, atMillis = now, room = p.room, setup = ROOM)
        }
        if (list.isEmpty()) return
        update(c) { kept -> Reactions.withAnswers(kept, list) }
    }

    /**
     * 2.0.0 [RVN-20] (총괄 11-12 "광장·회사에서 생기는 알림(답, 맞아요, 채택)은 이제 알림함으로"): the day's 광장 소식 as rows too, each
     * opening its own place (the one 소식 notice could open only one): 채택·제안 반영·임금표·보완 요청·고마워요 are what people did
     * ([InboxSection.PEOPLE]), a newer 판·신고 결과·내 글 결과 are 안내. 새 답 is left to [answers] (counted on this phone), 마을 돌봄
     * and 쓰기 제한 끝 to their own card in 마을 (they cannot be turned off).
     */
    fun news(c: AppContainer, items: List<PlazaNewsItem>) {
        val inbox = Inbox.get(c.appContext)
        items.mapNotNull(::newsRow).distinctBy { it.key }.forEach { inbox.post(it) }
    }

    internal fun newsRow(i: PlazaNewsItem): InboxRow? {
        val line = PlazaNews.line(i) ?: return null
        // The title is what happened in a word or two; the line under it says where ('보낸 가나다화학 규칙이 채택됐어요'), not twice.
        val company = i.companyId?.takeIf { Regex("[A-Za-z0-9]{12}").matches(it) }
        val companyPage = company?.let { NoticeLinks.Target.COMPANY_SETUP to it } ?: (NoticeLinks.Target.PLAZA to null)
        val pay = NoticeLinks.Target.PAY to null
        val (section, title, place) = when (i.kind) {
            "adopted" -> Triple(InboxSection.PEOPLE, "규칙 채택", companyPage)
            "proposal" -> Triple(InboxSection.PEOPLE, "고치자 제안 반영", companyPage)
            "rate" -> Triple(InboxSection.PEOPLE, "임금표 시세", NoticeLinks.Target.RATES to null)
            "fix" -> Triple(InboxSection.PEOPLE, "보완 요청 ${i.count.coerceAtLeast(1)}",
                PlazaNewsJob.fixTalk(listOf(i))?.let { NoticeLinks.Target.FIX_TALK to it } ?: companyPage)
            "fix_answer" -> Triple(InboxSection.PEOPLE, "보완 요청에 답", pay)
            "thanks" -> Triple(InboxSection.PEOPLE, "내 급여 방식 ${i.count.coerceAtLeast(1)}명", pay)
            "changed" -> Triple(InboxSection.NOTICE, "규칙 새 판", companyPage)
            "room" -> Triple(InboxSection.NOTICE, "다르게 쓰는 판", companyPage)
            "room_posts" -> Triple(InboxSection.NOTICE, "정보 광장 새 글", companyPage)
            "newer" -> Triple(InboxSection.NOTICE, "급여 방식 새 판", pay)
            "flag_result" -> Triple(InboxSection.NOTICE, "신고 결과", NoticeLinks.Target.PLAZA to null)
            "post_result" -> Triple(InboxSection.NOTICE, "내 글", NoticeLinks.Target.VILLAGE_POSTS to null)
            else -> return null
        }
        val about = listOfNotNull(i.companyId, i.setup, i.post, i.trade).joinToString("/")
        return InboxRow(key = "plaza.news:${i.kind}:$about", kind = "plaza.news.${i.kind}", section = section, title = title, line = line,
            target = place.first, payload = place.second, at = i.atMillis.takeIf { it > 0 } ?: com.bamnat.core.time.Today.millis(),
            count = i.count.coerceAtLeast(1))
    }

    /** The reactions behind the rows still in the 알림함, changed by [change], and the rows put again. */
    @Synchronized
    private fun update(c: AppContainer, change: (List<Reaction>) -> List<Reaction>) {
        val inbox = Inbox.get(c.appContext)
        val there = inbox.rows.value.map { it.key }.toSet()
        val kept = read(c.appContext).filter { Reactions.key(it) in there }
        val all = change(kept)
        val off = Reactions.decodeOff(c.settingsRepository.current.reactionsOff) + inbox.muted.value
        val rows = Reactions.rows(all, off)
        // Only what changed is put again: a row put again comes to the top of 사람들 반응.
        val was = kept.groupBy { Reactions.key(it) }
        rows.filter { r -> all.filter { Reactions.key(it) == r.key }.toSet() != was[r.key].orEmpty().toSet() }.forEach { inbox.post(row(it)) }
        val shown = rows.map { it.key }.toSet()
        write(c.appContext, all.filter { Reactions.key(it) in shown })
    }

    /** One 알림함 row: its words, and where a tap goes. */
    internal fun row(r: Reactions.Row): InboxRow {
        val (target, payload) = when (r.group) {
            Reactions.Group.PLAZA -> {
                val room = r.room
                if (room.isNullOrBlank()) NoticeLinks.Target.VILLAGE_POSTS to null
                else NoticeLinks.Target.FIX_TALK to NoticeLinks.FixTalk(room, r.setup?.takeIf { it.isNotBlank() } ?: ROOM,
                    placeName(r), focus = r.target).payload
            }
            Reactions.Group.BOARD -> NoticeLinks.Target.COMPANY_SETUP to r.target
            Reactions.Group.RATE -> NoticeLinks.Target.RATES to null
            Reactions.Group.REQUEST -> NoticeLinks.Target.REQUESTS to null
            Reactions.Group.CONTEST -> NoticeLinks.Target.RECIPE to r.target
        }
        return InboxRow(key = r.key, kind = r.group.kind, section = InboxSection.PEOPLE, title = r.title, line = r.line,
            target = target, payload = payload, at = r.atMillis.takeIf { it > 0 } ?: com.bamnat.core.time.Today.millis(), count = r.count)
    }

    /** The talk's name on the sheet: the room's or company's name. */
    private fun placeName(r: Reactions.Row): String = r.place?.takeIf { it.isNotBlank() } ?: "광장"

    private const val ROOM = "room"

    private fun read(context: Context): List<Reaction> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_KEPT, null) ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                Reaction(o.getString("kind"), o.getString("target"), o.optString("title"), o.optInt("count"), o.optLong("at"),
                    o.optString("room").ifEmpty { null }, o.optString("setup").ifEmpty { null }, o.optString("postKind").ifEmpty { null },
                    o.optInt("up"), o.optInt("down"), o.optString("theme").ifEmpty { null })
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, list: List<Reaction>) {
        val a = JSONArray()
        list.forEach { r ->
            a.put(JSONObject().put("kind", r.kind).put("target", r.target).put("title", r.title).put("count", r.count).put("at", r.atMillis)
                .put("room", r.room ?: "").put("setup", r.setup ?: "").put("postKind", r.postKind ?: "").put("up", r.up).put("down", r.down)
                .put("theme", r.theme ?: ""))
        }
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_KEPT, a.toString()).apply() }
    }
}
