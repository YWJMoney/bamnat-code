package com.bamnat.app.recipe

import com.bamnat.app.BuildConfig
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.objects
import com.bamnat.app.company.optStringOrNull
import com.bamnat.app.company.readCall
import com.bamnat.app.company.serverCall
import com.bamnat.app.company.serverExchange
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.work.WorkStyle
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONObject

/** [RCP-21] A recipe as the plaza lists it. [kept] is the 7일 유지율 in percent, null under 20 applications. */
data class RecipeItem(
    val code: String,
    val title: String,
    val summary: String,
    val styles: Set<WorkStyle>,
    val cover: Cover,
    val nick: String?,
    val version: Int,
    val likes: Int,
    val dislikes: Int,
    val users: Int,
    val kept: Int?,
    val official: Boolean,
    val publicData: Boolean,
    val parent: String?,
    val at: Instant,
    val fresh: Boolean = false,
    /** [RCX-05] 1.35 The maker's page id (never the install); null from a server before 1.35. */
    val maker: String? = null,
    /** [RCX-32] 1.35 운영자가 고른 보여주기 판: what 나눔 판 can do, shown first on every 광장. */
    val showcase: Boolean = false,
    /** [FREE-08] 1.35 What the card's small calendar is drawn from; null when the recipe carries no look. */
    val look: ThemeSwatch? = null,
    /** [MKX-01] 1.36 The tabs it changes (calendar, pay, money, memo, alarm, widget, app); empty from a server before 1.36. */
    val tabs: List<String> = emptyList(),
    /** [MKX-01] "theme" (앱 전체를 한 벌로) or "tab" (탭 하나만); null when the server did not say. */
    val scope: String? = null,
    /** 1.36 [VIL-18] The first work of a new maker, given a place in 요즘 뜨는·좋은 순. */
    val newMaker: Boolean = false,
    /** 1.39 [MKC-01] 테마·탭별 작품이 바꾸는 것: "look" 꾸미기, "func" 기능, "both"; null from a server before 1.39. */
    val made: String? = null,
    /** 1.39 [MKC-01] 도구의 종류 (part 부품, rule 자동 규칙, button 단추); empty for a 테마·탭별 작품 or from a server before 1.39. */
    val tools: List<String> = emptyList(),
    /**
     * 1.39 [MKC-04] 1.39부터 새로 연 기능 장치 (서버 fx, core FxPro.Part 키: values·valueAct·valueRead·outside·notifyButtons). 비어 있으면
     * 1.38까지 장치만 써서 모두 무료로 쓸 수 있어요; 있으면 '✦ 프로 기능'.
     */
    val fx: List<String> = emptyList(),
    /**
     * 1.39 [MKD-41] 만든 분 표식 (광장 갈래 MakerMark 키: "month" 이달의 제작자, "popular" 인기 제작자). 쉬는 중이거나 없으면 null.
     * 서버 recipes.js listItem·show의 mark.
     */
    val mark: String? = null,
    /** 1.39 [MKD-41] 만든 분의 팔로워 수 (서버 followers); 옛 서버는 null. */
    val followers: Int? = null,
    /** 1.39 판 정리: 장터에서 내린 작품 (서버 show의 retired). 코드로는 열리고, 넣은 것은 그대로 써요. */
    val retired: Boolean = false,
)

/**
 * [FREE-08] A recipe's look as the list sends it (server lookOf): page, card, ink and accent colours, today·토·일, the kinds of day,
 * the cell looks and their pattern, the memo layout and whether it has a picture.
 */
data class ThemeSwatch(
    val bg: Long? = null, val card: Long? = null, val ink: Long? = null, val accent: Long? = null,
    val today: Long? = null, val sat: Long? = null, val sun: Long? = null,
    val days: List<Day> = emptyList(), val cells: String? = null, val pattern: String? = null, val badge: String? = null,
    val memo: String? = null, val photo: Boolean = false,
    /** PC 확인 V44: drawn for a recipe that brought no colours, with the kind of work's usual days and their letters. */
    val example: Boolean = false,
) {
    data class Day(val color: Long?, val label: String, val emoji: String?, val role: String?)

    /** PC 확인 0efe9dbc: a look that brought its colours but no kinds of day keeps them and shows the kind of work's usual days, with their letters. */
    fun withDays(style: com.bamnat.core.work.WorkStyle?): ThemeSwatch = if (days.isEmpty()) copy(days = example(style).days, example = true) else this

    companion object {
        /** PC 확인 V44: a kind of work's usual days (주·야·휴, 일·쉼 …) in their own colours, for a recipe without a look. */
        fun example(style: com.bamnat.core.work.WorkStyle?): ThemeSwatch = ThemeSwatch(example = true, days = when (style) {
            com.bamnat.core.work.WorkStyle.SHIFT, null -> listOf(Day(0xFFEF6C00, "주", null, "day"), Day(0xFF3949AB, "야", null, "night"), Day(0xFF757575, "휴", null, "off"))
            com.bamnat.core.work.WorkStyle.HOURLY -> listOf(Day(0xFF2E7D32, "일", null, "day"), Day(0xFF757575, "쉼", null, "off"))
            com.bamnat.core.work.WorkStyle.FIELD -> listOf(Day(0xFF6D4C41, "현", null, "day"), Day(0xFF757575, "휴", null, "off"))
            com.bamnat.core.work.WorkStyle.BUSINESS -> listOf(Day(0xFF00897B, "영", null, "day"), Day(0xFF757575, "휴", null, "off"))
            com.bamnat.core.work.WorkStyle.INVEST -> listOf(Day(0xFF1E88E5, "장", null, "day"), Day(0xFF757575, "휴", null, "off"))
            else -> listOf(Day(0xFF1E88E5, "근", null, "day"), Day(0xFF757575, "휴", null, "off"))
        })

        private fun hex(s: String?): Long? = s?.takeIf { Regex("^[0-9A-F]{8}$").matches(it) }?.toLong(16)

        fun of(o: JSONObject?): ThemeSwatch? = o?.let {
            ThemeSwatch(
                hex(it.optStringOrNull("bg")), hex(it.optStringOrNull("card")), hex(it.optStringOrNull("ink")), hex(it.optStringOrNull("accent")),
                it.optLong("today").takeIf { _ -> it.has("today") }, it.optLong("sat").takeIf { _ -> it.has("sat") }, it.optLong("sun").takeIf { _ -> it.has("sun") },
                it.optJSONArray("days").objects().take(4).map { d ->
                    Day(d.optLong("c").takeIf { d.has("c") && !d.isNull("c") }, d.optString("l").take(2), d.optStringOrNull("e"), d.optStringOrNull("r"))
                },
                it.optStringOrNull("cells")?.takeIf { c -> com.bamnat.core.recipe.CellLook.looksProblem(c) == null },
                it.optStringOrNull("pattern")?.takeIf { c -> com.bamnat.core.recipe.CellPattern.problem(c) == null },
                it.optStringOrNull("badge"), it.optStringOrNull("memo"), it.optBoolean("photo"),
            )
        }
    }
}

/** [RCP-23] One recipe with its written form; [state], [note] and [reasons] only for its maker. */
data class RecipeDoc(
    val item: RecipeItem,
    val hash: String,
    val body: String,
    val scope: String,
    val mine: Boolean,
    val myVote: Int? = null,
    val state: String? = null,
    val note: String? = null,
    val reasons: Reasons? = null,
    /** 1.36 [GRW-08] 만든 분께만: 운영자 확인을 기다리는 사진 수(새싹 첫 사진, 신고로 먼저 가린 사진). */
    val photosWaiting: Int = 0,
    /** 신고 2번으로 다른 분께 잠시 가려진 사진 수. */
    val photosHeld: Int = 0,
) {
    /** The recipe itself, checked again on this phone: the written form hashes to what the server named it (S10). */
    fun recipe(): Recipe {
        val r = RecipeCodec.read(body)
        if (RecipeCodec.hash(r) != hash) throw com.bamnat.core.recipe.RecipeException("hash")
        return r
    }
}

/** [RCP-31] What 안좋아요 said, for the maker: how many of each reason, and the short opinions (never who). */
data class Reasons(val tally: Map<String, Int>, val opinions: List<Pair<String, String>>)

data class RecipePage(val items: List<RecipeItem>, val next: Int?,
    /**
     * 1.39 [MKP-01] 장터 로딩 (사용자 2026-11-07 "장터에서 테마 로딩이 너무 느린데"): each work's written form (hash, body) by
     * "code@version", sent with the list (`body=1`) so a card is drawn without asking for its work alone; empty from an older server.
     */
    val bodies: Map<String, Pair<String, String>> = emptyMap(),
    /** 1.39 [MKP-07] The answer as it came, so the phone can keep it and show it at once next time ([RecipeClient.pageOf]). */
    val raw: String? = null)

/** [RCP-29] What the server said of a recipe sent. */
data class Sent(val code: String, val version: Int, val hash: String, val state: String, val scope: String)

/** [RCP-35] A post in a recipe's talk. */
data class RecipePost(
    val id: String,
    val who: Int,
    val nick: String?,
    val body: String,
    val at: Instant,
    val kind: String,
    val parent: String?,
    val mine: Boolean,
    val maker: Boolean,
    val usingDays: Int?,
    val likes: Int,
    val dislikes: Int,
    val myVote: Int? = null,
    val author: String? = null,
    val hidden: Boolean = false,
    val reasons: List<Pair<String, String>> = emptyList(),
)

/** 1.36 [LIM-10] [left] and [limit] only while reports restrict this install; otherwise there is no cap. */
data class RecipeTalk(val posts: List<RecipePost>, val left: Int?, val mutedUntil: Instant? = null,
    val limit: com.bamnat.app.company.WriteLimit? = null)

data class Votes(val likes: Int, val dislikes: Int, val myVote: Int?, val held: Boolean = false)

/** [RCP-29] The phone's own recipes in every state. */
/** [photos]: 1.36 [GRW-08] 사진이 운영자를 기다리는("pending") 또는 신고로 가려진("held") 작품의 코드별 표시. */
data class MyRecipes(val trust: Int, val reviewLeft: Int, val items: List<Pair<RecipeItem, Triple<String, String, String?>>>,
    val photos: Map<String, String> = emptyMap())

/** [RCX-01] 계보: the recipes it was made from (nearest first; null where one is gone) and those made from it. */
data class Lineage(val parents: List<RecipeItem?>, val children: List<RecipeItem>,
    /** 1.36 누락 점검 N15: 고친 판 코드 → 바꾼 곳 (조각 종류 열쇠, "add"·"del"·"edit"). */
    val changed: Map<String, List<Pair<String, String>>> = emptyMap())

/** [RCX-04] 요청 판: a wish, its likes and where it stands (waiting → making → done with a recipe's [code]). */
data class RecipeRequest(
    val id: String, val style: WorkStyle?, val body: String, val likes: Int, val state: String, val code: String?, val at: Instant,
    val mine: Boolean, val taking: Boolean, val liked: Boolean,
    /** 1.36 [GRW-03] 스튜디오 요구가 모여 저절로 올라온 줄이면 부탁한 분 수. */
    val askers: Int = 0,
    /** 1.36 누락 점검 N14: 14일 동안 아무도 만들지 않아 개발자에게 넘어간 요청. */
    val told: Boolean = false,
)

/** 1.36 [GRW-05] 모두의 도구: 많이 쓰이고 7일 뒤에도 쓰는 장터 작품(표 수가 아니라 쓴 사람 수로 올라와요). */
data class CommonTool(val code: String, val title: String, val summary: String, val users: Int, val kept: Int?, val tierName: String)

/** [RCX-05][RCX-06] A maker's page; [PRF-01] 1.36 the 창작자 프로필 on it. */
data class MakerPage(
    val maker: String, val nick: String?, val face: String?, val color: Long?, val mine: Boolean, val recipes: List<RecipeItem>,
    val likes: Int, val users: Int, val followers: Int, val following: Boolean, val badges: List<Pair<String, String>>,
    /** 한 줄 소개 (null when none or hidden from others). */
    val bio: String? = null,
    /** The maker's own: the operator hid the 소개 for 신고. */
    val bioHidden: Boolean = false,
    /** The kind of work the maker chose to show ([WorkStyle] name). */
    val styleTag: WorkStyle? = null,
    /** 대표 작품, codes among [recipes]. */
    val pins: List<String> = emptyList(),
    val followingCount: Int = 0,
    /** This phone turned off the maker's 새 작품 알림 (still following). */
    val quiet: Boolean = false,
    /** This phone blocked the maker: nothing else is sent. */
    val blocked: Boolean = false,
    /** The maker's own: 프로필 숨기기 is on. */
    val hidden: Boolean = false,
    val sets: List<MakerSet> = emptyList(),
    /** [PRF-12] 표지 그림 (shown once the operator looked), or the maker's own still waiting. */
    val cover: String? = null,
    val coverPending: String? = null,
    /** The maker's own: the operator closed the profile (others cannot see or follow it). */
    val closed: Boolean = false,
    /** The maker's own: who hid the 소개, "flags" (세 번 신고) or "operator". */
    val bioHiddenBy: String? = null,
    /** The maker's own: the reason most reports gave ("impersonate", "bio", "spam", "other"), never who. */
    val bioHiddenWhy: String? = null,
    /** 1.36 [V20] 보이는 믿음 단계: 0 새싹, 1 이웃, 2 단골 (null: 밤낮 운영). */
    val trust: Int? = null,
    /** The maker's own: days to the next step, or [trustHeld] when something hidden keeps 단골 away. */
    val trustDays: Int? = null,
    val trustHeld: Boolean = false,
    /** The maker's own: 1.36 [LIM-10] writes a day while reports restrict this install, and until when. */
    val limitPerDay: Int? = null,
    val limitUntil: java.time.Instant? = null,
    /** PC 확인 보안 6차: the same restriction with who hid what, for its line ([com.bamnat.app.company.WriteLimit.cause]). */
    val limit: com.bamnat.app.company.WriteLimit? = null,
) {
    val themes: List<RecipeItem> get() = recipes.filter { it.scope != "tab" }
    val tabRecipes: List<RecipeItem> get() = recipes.filter { it.scope == "tab" }
}

/** [PRF-02] A maker as a face in a row or list. [fresh]: a new public recipe in the last 7 days. */
data class MakerFaceItem(val maker: String, val nick: String?, val face: String?, val color: Long?, val fresh: Boolean = false,
    val quiet: Boolean = false, val bio: String? = null, val picked: Boolean = false,
    /** A blocked maker whose profile is gone (모두 지우기). */
    val gone: Boolean = false)

/** [PRF-02] 팔로잉 and the makers this phone blocked. */
data class Following(val items: List<MakerFaceItem>, val blocked: List<MakerFaceItem>)

/** [PRF-02] 내 팔로워 (the maker's own): those with a 프로필, and how many without. */
data class Followers(val total: Int, val items: List<MakerFaceItem>, val unnamed: Int)

/** [PRF-03] 내 프로필 최근 알림, as the server works it out; [RecipeRepository] keeps which milestones and badges were shown. */
data class ProfileInbox(
    val maker: String, val newFollowers: Int, val followerFaces: List<MakerFaceItem>,
    val questions: List<Triple<String, String, String>>, val remixes: List<Triple<String, String, String>>,
    /** code, title, "users" or "likes", the number passed. */
    val milestones: List<Milestone>, val badges: List<Pair<String, String>>,
    /** When the newest of [newFollowers] followed: names the 새 팔로워 line for 다 봤어요. */
    val followersLatest: String? = null,
    /** The server's time of this answer, kept as the next since (the phone's clock may be off). */
    val at: Instant? = null,
) {
    data class Milestone(val code: String, val title: String, val kind: String, val n: Int) {
        val key: String get() = "$code|$kind|$n"
    }
}

/** [PRF-07] 모음: 2~6 of a maker's own recipes (테마 하나 + 기능별 탭들) put on together. */
data class MakerSet(val id: String, val title: String, val summary: String, val codes: List<String>, val missing: Int = 0, val hidden: Boolean = false)

/** [PRF-07] A 모음 opened: its recipes, 테마 first. */
data class SetDoc(val id: String, val title: String, val summary: String, val maker: String, val mine: Boolean, val items: List<RecipeItem>, val missing: Int)

/** [PRF-09] 작품 통계 (the maker's own). [days] is 28 days of 넣은 수, oldest first. */
data class MakerStats(val week: Int, val users: Int, val likes: Int, val items: List<Item>) {
    data class Item(val code: String, val title: String, val users: Int, val likes: Int, val dislikes: Int, val kept: Int?, val week: Int, val days: List<Int>)
}

/** [PRF-01] What 프로필 고치기 sends; null fields stay as they are. */
data class ProfileEdit(
    val bio: String? = null, val pins: List<String>? = null,
    /** Set [styleTag] (null to clear) only when [setStyle]. */
    val setStyle: Boolean = false, val styleTag: WorkStyle? = null,
    val hidden: Boolean? = null,
    /** Set [cover] (null to clear) only when [setCover]. */
    val setCover: Boolean = false, val cover: String? = null,
)

/** [RCX-07][RCX-10] For 소식: new recipes of followed makers, and makers' answers to my questions (code, title, the answer). */
data class RecipeNews(val recipes: List<RecipeItem>, val replies: List<Triple<String, String, String>>,
    /** [LIN-04] 1.35 수정판 of my recipes by others: the new one and the title of mine it came from. */
    val remixes: List<Pair<RecipeItem, String>> = emptyList(),
    /** 1.36 [VIL-15] My requests someone finished: the recipe's code and title, and what I asked. */
    val requests: List<Triple<String, String, String>> = emptyList())

/** [RCP-21]..[RCP-35] What the app asks of the 나눔 판 server; screenshots and tests answer with a fake. */
interface RecipeApi {
    val enabled: Boolean
    /** [kind] 1.35 종류로 보기: skin, layout, memo, money or rules (server RECIPE_KINDS); null for all. */
    suspend fun list(style: WorkStyle?, sort: String, q: String = "", cursor: Int = 0, kind: String? = null): RecipePage
    suspend fun show(code: String, device: String?, version: Int? = null): RecipeDoc
    suspend fun publish(device: String, recipe: Recipe, scope: String): Sent
    suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?): Sent
    suspend fun delete(device: String, code: String)
    suspend fun vote(device: String, code: String, value: Int?, reason: String? = null, opinion: String? = null): Votes
    suspend fun applied(code: String, kept: Boolean)
    suspend fun flag(device: String, code: String, reason: String)
    suspend fun mine(device: String): MyRecipes
    suspend fun talk(device: String?, code: String): RecipeTalk
    suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?): String
    suspend fun talkVote(device: String, post: String, value: Int?, reason: String? = null, opinion: String? = null): Votes

    // ---- 1.35 (fakes answer empty unless they say otherwise) ----
    suspend fun lineage(code: String): Lineage = Lineage(emptyList(), emptyList())
    /** [RCX-02] The newest version of each code (no device sent). */
    suspend fun versions(codes: List<String>): Map<String, Int> = emptyMap()
    suspend fun requests(device: String?, style: WorkStyle?): List<RecipeRequest> = emptyList()
    /** 1.36 [GRW-05] 모두의 도구 (no device sent). */
    suspend fun tools(style: WorkStyle?): List<CommonTool> = emptyList()
    /** 1.36 누락 점검 N13: 찾아도 없던 말 하나 (휴대폰 번호 없이). True when the server took it. */
    suspend fun searchMiss(place: String, query: String): Boolean = false
    suspend fun request(device: String, style: WorkStyle, body: String): String = throw CompanyException("offline")
    /** [what] like · take · done · delete; [on] false takes a like or a 만들게요 back; [code] for done. */
    suspend fun requestAct(device: String, id: String, what: String, on: Boolean = true, code: String? = null) {}
    suspend fun maker(device: String?, id: String): MakerPage = throw CompanyException("not_found")
    /** The phone's own maker id, and its face when [face]/[color] are given. */
    suspend fun makerMe(device: String, face: String? = null, color: Long? = null, set: Boolean = false): String = throw CompanyException("offline")
    suspend fun follow(device: String, id: String, on: Boolean) {}
    /** [PRF-04] 팔로우는 그대로, 이분 새 작품 알림만 끄기·켜기. */
    suspend fun followQuiet(device: String, id: String, quiet: Boolean) {}
    /** [PRF-01] 프로필 고치기. */
    suspend fun profile(device: String, edit: ProfileEdit) {}
    suspend fun following(device: String): Following = Following(emptyList(), emptyList())
    suspend fun followers(device: String): Followers = Followers(0, emptyList(), 0)
    suspend fun inbox(device: String, since: Instant?): ProfileInbox? = null
    suspend fun block(device: String, id: String, on: Boolean) {}
    suspend fun flagMaker(device: String, id: String, reason: String) {}
    suspend fun setOpen(device: String?, id: String): SetDoc = throw CompanyException("not_found")
    /** A new 모음 ([id] null) or a change to one; its id back. */
    suspend fun setSave(device: String, id: String?, title: String, summary: String, codes: List<String>): String = throw CompanyException("offline")
    suspend fun setDelete(device: String, id: String) {}
    suspend fun feed(device: String, cursor: Int = 0): RecipePage = throw CompanyException("offline")
    suspend fun stats(device: String): MakerStats = throw CompanyException("offline")
    suspend fun monthMakers(style: WorkStyle?): List<MakerFaceItem> = emptyList()
    suspend fun picks(style: WorkStyle?): List<RecipeItem> = emptyList()
    suspend fun news(device: String, since: Instant?): RecipeNews = RecipeNews(emptyList(), emptyList())
    /** [RCX-15] A cover picture already shrunk to WebP; its id back (it shows once the operator looked). */
    suspend fun image(device: String, webp: ByteArray): String = throw CompanyException("offline")
    /** The address of a cover picture, for the image loader (the maker sees a waiting one with its device header). */
    fun imageUrl(id: String): String? = null
    /** 1.38 [STM-06] 밤낮 재료: 운영자가 그린 색 아이콘·배경 중 이 판이 쓸 수 있는 공개된 것. */
    suspend fun materials(): List<StudioMaterial> = emptyList()
    /** [MAT-06] 1.36 내 글꼴 나누기: an open font (its maker lets others use it, as the user said); the server's id (the phone's). */
    suspend fun fontSend(device: String, id: String, name: String, license: String, ttf: ByteArray, fallback: String? = null): FontSent = throw CompanyException("offline")
    /** [MAT-06] A shared font's name and its file, or null when the server does not have it (or took it down). */
    suspend fun fontGet(device: String, id: String): Pair<String, ByteArray>? = null
    /** PC 확인 9차: the app font that stands in for a shared font this phone cannot get yet (its sender's guess), or null. */
    suspend fun fontStandIn(id: String): String? = null
    /** PC 확인 13차: a shared font's name once others may get it, else null (a waiting one's name stays hidden). */
    suspend fun fontName(id: String): String? = null
    /** [MAT-06] 이 글꼴 신고 (라이선스·깨짐·불쾌). */
    suspend fun fontReport(device: String, id: String, why: String) {}
}

/** [MAT-06] A font the server took: its id, and 1.36 [SEC-10] whether it waits for the operator before others get it. */
data class FontSent(val id: String, val waiting: Boolean = false)

class RecipeClient(
    private val baseUrl: String = BuildConfig.COMPANY_BASE_URL,
    /** [COMM-09] The X-Hide header: the writers this phone hides. */
    private val hidden: () -> String? = { null },
) : RecipeApi {
    override val enabled: Boolean get() = baseUrl.isNotBlank()

    private fun call(method: String, path: String, body: JSONObject? = null, device: String? = null): JSONObject {
        val headers = hidden()?.let { mapOf("X-Hide" to it) }.orEmpty()
        return serverExchange(baseUrl, method, path, body, device, headers).json
    }

    override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int, kind: String?): RecipePage = readCall {
        val query = buildList {
            style?.let { add("style=${it.name}") }
            add("sort=$sort")
            if (q.isNotBlank()) add("q=" + URLEncoder.encode(q.trim().take(30), "UTF-8"))
            if (cursor > 0) add("cursor=$cursor")
            // 1.36 [MKS-01] This app's versionCode: the server shows a 출시 작품 made for a newer app only to apps that can read it.
            add("app=${com.bamnat.app.BuildConfig.VERSION_CODE}")
            // 1.39 [MKC-04] '✦ 프로 기능'만 (어느 구간에서든 끝에 [FX]). A server before 1.39 FX-01 sends all; [fits] keeps those with fx.
            if (fxOnly(kind)) add("fx=1")
            if (freeOnly(kind)) add("fx=0")
            val k = plain(kind)
            // [MKX-01] 1.36 두 구간: "theme" or "tab:<tab>" goes as scope (and tab), with the nearest 1.35 kind for a server before 1.36.
            when {
                k == SCOPE_THEME -> { add("scope=theme"); add("kind=skin") }
                k == SCOPE_LOGIC -> { add("scope=logic"); add("kind=rules") }
                // 1.39 [MKC-01] 도구: 부품과 규칙·단추를 함께 (tool= 종류, tab= 쓰는 화면). A server before 1.39 reads kind=rules (규칙·단추만).
                k != null && k.startsWith(SCOPE_TOOL) -> {
                    add("scope=tool"); add("kind=rules")
                    toolOf(k).takeIf { it in TOOL_KINDS }?.let { add("tool=$it") }
                    toolOn(k)?.let { add("tab=$it") }
                }
                k != null && k.startsWith(SCOPE_TAB) -> {
                    add("scope=tab")
                    val t = tabOf(k)
                    if (t in MARKET_TABS) { add("tab=$t"); legacyKind(t)?.let { add("kind=$it") } }
                    madeOf(k)?.let { add("made=$it") }
                }
                else -> k?.takeIf { it in KINDS }?.let { add("kind=$it") }
            }
        }.joinToString("&")
        val o = call("GET", "/api/recipes?$query&body=1")
        pageOf(o).copy(raw = o.toString())
    }

    override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = readCall {
        // 2.0.0 [PC-28] This app's versionCode, as the list sends it: the server serves a work's 스르륵·책장·움직이지 않기 달 넘김 as 밀기 to
        // the apps before 2.0.0 (and to one that says none), and as made to this one. Without it the card (list, app=) and the work read
        // whole would differ and 넣기 would say a new 판 came in between.
        val o = call("GET", "/api/recipes/$code" + listOfNotNull(version?.let { "v=$it" }, "app=${com.bamnat.app.BuildConfig.VERSION_CODE}")
            .joinToString("&", prefix = "?"), device = device)
        RecipeDoc(
            item(o), o.getString("hash"), o.getString("body"), o.optString("scope", "public"), o.optBoolean("mine"),
            o.optInt("myVote").takeIf { o.has("myVote") }, o.optStringOrNull("state"), o.optStringOrNull("note"),
            o.optJSONObject("reasons")?.let { r ->
                val t = r.optJSONObject("tally")
                Reasons(t?.keys()?.asSequence()?.associateWith { t.optInt(it) }.orEmpty(),
                    r.optJSONArray("opinions").objects().map { it.optString("reason") to it.optString("text") })
            },
            photosWaiting = o.optInt("photosWaiting"),
            photosHeld = o.optInt("photosHeld"),
        )
    }

    private fun sent(o: JSONObject) = Sent(o.getString("code"), o.optInt("version", 1), o.getString("hash"), o.getString("state"), o.optString("scope", "public"))

    override suspend fun publish(device: String, recipe: Recipe, scope: String): Sent = serverCall {
        sent(call("POST", "/api/recipes", JSONObject().put("device", device).put("scope", scope).put("recipe", JSONObject(RecipeCodec.write(recipe)))))
    }

    override suspend fun newVersion(device: String, code: String, recipe: Recipe, scope: String?): Sent = serverCall {
        sent(call("POST", "/api/recipes/$code/versions", JSONObject().put("device", device).put("recipe", JSONObject(RecipeCodec.write(recipe)))
            .apply { scope?.let { put("scope", it) } }))
    }

    override suspend fun delete(device: String, code: String) = serverCall {
        call("DELETE", "/api/recipes/$code", device = device)
        Unit
    }

    private fun votes(o: JSONObject) = Votes(o.optInt("likes"), o.optInt("dislikes"), o.optInt("myVote").takeIf { o.has("myVote") && !o.isNull("myVote") },
        o.optBoolean("held"))

    private fun voteBody(device: String, value: Int?, reason: String?, opinion: String?) = JSONObject().put("device", device)
        .put("value", value ?: JSONObject.NULL).apply { reason?.let { put("reason", it) }; opinion?.let { put("opinion", it) } }

    override suspend fun vote(device: String, code: String, value: Int?, reason: String?, opinion: String?): Votes = serverCall {
        votes(call("POST", "/api/recipes/$code/vote", voteBody(device, value, reason, opinion)))
    }

    override suspend fun applied(code: String, kept: Boolean) = serverCall {
        call("POST", "/api/recipes/$code/applied", JSONObject().put("kind", if (kept) "kept" else "applied"))
        Unit
    }

    override suspend fun flag(device: String, code: String, reason: String) = serverCall {
        call("POST", "/api/recipes/$code/flag", JSONObject().put("device", device).put("reason", reason))
        Unit
    }

    override suspend fun mine(device: String): MyRecipes = readCall {
        val o = call("GET", "/api/recipes/mine", device = device)
        val rows = o.optJSONArray("items").objects()
        MyRecipes(o.optInt("trust"), o.optInt("reviewLeft"), rows.map {
            item(it) to Triple(it.optString("state"), it.optString("scope"), it.optStringOrNull("note"))
        }, rows.mapNotNull { r -> r.optStringOrNull("photos")?.let { r.optString("code") to it } }.toMap())
    }

    override suspend fun talk(device: String?, code: String): RecipeTalk = readCall {
        val o = call("GET", "/api/recipes/$code/talk", device = device)
        RecipeTalk(o.optJSONArray("posts").objects().map { p ->
            RecipePost(
                p.getString("id"), p.optInt("who"), p.optStringOrNull("nick"), p.optString("body"), Instant.parse(p.getString("at")),
                p.optString("kind", "opinion"), p.optStringOrNull("parent"), p.optBoolean("mine"), p.optBoolean("maker"),
                p.optInt("usingDays").takeIf { p.has("usingDays") && !p.isNull("usingDays") }, p.optInt("likes"), p.optInt("dislikes"),
                p.optInt("myVote").takeIf { p.has("myVote") }, p.optStringOrNull("author"), p.optBoolean("hidden"),
                p.optJSONArray("reasons").objects().map { it.optString("reason") to it.optString("text") },
            )
        }, o.optInt("left").takeIf { o.has("left") }, o.optStringOrNull("mutedUntil")?.let(Instant::parse),
            com.bamnat.app.company.WriteLimit.of(o))
    }

    override suspend fun talkPost(device: String, code: String, body: String, kind: String, parent: String?, usingDays: Int?): String = serverCall {
        call("POST", "/api/recipes/$code/talk", JSONObject().put("device", device).put("body", body).put("kind", kind)
            .apply { parent?.let { put("parent", it) }; usingDays?.let { put("usingDays", it) } }).getString("id")
    }

    override suspend fun talkVote(device: String, post: String, value: Int?, reason: String?, opinion: String?): Votes = serverCall {
        votes(call("POST", "/api/recipes/talk/$post/vote", voteBody(device, value, reason, opinion)))
    }

    // ---- 1.35 ----

    override suspend fun lineage(code: String): Lineage = readCall {
        val o = call("GET", "/api/recipes/$code/lineage")
        Lineage(o.optJSONArray("parents").objects().map { if (it.optBoolean("gone")) null else item(it) }, o.optJSONArray("children").objects().map(::item),
            o.optJSONArray("children").objects().associate { k ->
                k.optString("code") to (k.optJSONArray("changed")?.objects()?.map { c -> c.optString("kind") to c.optString("how") } ?: emptyList())
            })
    }

    override suspend fun versions(codes: List<String>): Map<String, Int> = readCall {
        if (codes.isEmpty()) return@readCall emptyMap()
        val v = call("GET", "/api/recipes/versions?codes=" + codes.distinct().take(50).joinToString(",")).optJSONObject("versions") ?: JSONObject()
        v.keys().asSequence().associateWith { v.getJSONObject(it).optInt("version", 1) }
    }

    override suspend fun requests(device: String?, style: WorkStyle?): List<RecipeRequest> = readCall {
        call("GET", "/api/recipes/requests" + (style?.let { "?style=${it.name}" } ?: ""), device = device).optJSONArray("items").objects().map { q ->
            RecipeRequest(q.getString("id"), WorkStyle.entries.firstOrNull { it.name == q.optString("style") }, q.optString("body"), q.optInt("likes"),
                q.optString("state", "waiting"), q.optStringOrNull("code"), Instant.parse(q.getString("at")), q.optBoolean("mine"), q.optBoolean("taking"),
                q.optBoolean("liked"), q.optInt("askers"), q.optBoolean("told"))
        }
    }

    override suspend fun tools(style: WorkStyle?): List<CommonTool> = readCall {
        call("GET", "/api/recipes/tools" + (style?.let { "?style=${it.name}" } ?: "")).optJSONArray("items").objects().map { t ->
            CommonTool(t.getString("code"), t.optString("title"), t.optString("summary"), t.optInt("users"), if (t.isNull("kept") || !t.has("kept")) null else t.optInt("kept"),
                t.optString("tierName", "모두의 도구"))
        }
    }

    override suspend fun searchMiss(place: String, query: String): Boolean = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            call("POST", "/api/growth/miss", JSONObject().put("place", place).put("q", query.take(40))).optBoolean("ok")
        }
    }.getOrDefault(false)

    override suspend fun request(device: String, style: WorkStyle, body: String): String = serverCall {
        call("POST", "/api/recipes/requests", JSONObject().put("device", device).put("style", style.name).put("body", body)).getString("id")
    }

    override suspend fun requestAct(device: String, id: String, what: String, on: Boolean, code: String?) = serverCall {
        call("POST", "/api/recipes/requests/$id/$what", JSONObject().put("device", device).put("on", on).apply { code?.let { put("code", it) } })
        Unit
    }

    override suspend fun maker(device: String?, id: String): MakerPage = readCall {
        val o = call("GET", "/api/recipes/makers/$id", device = device)
        MakerPage(o.getString("maker"), o.optStringOrNull("nick"), o.optStringOrNull("face"), if (o.isNull("color") || !o.has("color")) null else o.optLong("color"),
            o.optBoolean("mine"), o.optJSONArray("recipes").objects().map(::item), o.optInt("likes"), o.optInt("users"), o.optInt("followers"),
            o.optBoolean("following"), o.optJSONArray("badges").objects().map { it.optString("key") to it.optString("name") },
            bio = o.optStringOrNull("bio"), bioHidden = o.optBoolean("bioHidden"), styleTag = style(o.optStringOrNull("styleTag")),
            pins = o.optJSONArray("pins").strings(), followingCount = o.optInt("followingCount"), quiet = o.optBoolean("quiet"),
            blocked = o.optBoolean("blocked"), hidden = o.optBoolean("hidden"),
            sets = o.optJSONArray("sets").objects().map { s -> MakerSet(s.getString("id"), s.optString("title"), s.optString("summary"),
                s.optJSONArray("codes").strings(), s.optInt("missing"), s.optBoolean("hidden")) },
            cover = o.optStringOrNull("cover"), coverPending = o.optStringOrNull("coverPending"), closed = o.optBoolean("closed"),
            bioHiddenBy = o.optStringOrNull("bioHiddenBy"), bioHiddenWhy = o.optStringOrNull("bioHiddenWhy"),
            trust = if (o.has("trust") && !o.isNull("trust")) o.optInt("trust").coerceIn(0, 2) else null,
            trustDays = o.optJSONObject("trustNext")?.takeIf { it.has("days") }?.optInt("days"),
            trustHeld = o.optJSONObject("trustNext")?.optBoolean("held") == true,
            limitPerDay = o.optJSONObject("limit")?.optInt("perDay"),
            limitUntil = o.optJSONObject("limit")?.optStringOrNull("until")?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() },
            limit = com.bamnat.app.company.WriteLimit.of(o))
    }

    override suspend fun followQuiet(device: String, id: String, quiet: Boolean) = serverCall {
        call("POST", "/api/recipes/makers/$id/follow", JSONObject().put("device", device).put("quiet", quiet))
        Unit
    }

    override suspend fun profile(device: String, edit: ProfileEdit) = serverCall {
        val body = JSONObject().put("device", device)
        edit.bio?.let { body.put("bio", it) }
        edit.pins?.let { body.put("pins", org.json.JSONArray(it)) }
        if (edit.setStyle) body.put("styleTag", edit.styleTag?.name ?: JSONObject.NULL)
        edit.hidden?.let { body.put("hidden", it) }
        if (edit.setCover) body.put("cover", edit.cover ?: JSONObject.NULL)
        call("POST", "/api/recipes/makers/me", body)
        Unit
    }

    override suspend fun following(device: String): Following = readCall {
        val o = call("GET", "/api/recipes/makers/me/following", device = device)
        Following(o.optJSONArray("items").objects().map(::face), o.optJSONArray("blocked").objects().map(::face))
    }

    override suspend fun followers(device: String): Followers = readCall {
        val o = call("GET", "/api/recipes/makers/me/followers", device = device)
        Followers(o.optInt("total"), o.optJSONArray("items").objects().map(::face), o.optInt("unnamed"))
    }

    override suspend fun inbox(device: String, since: Instant?): ProfileInbox = readCall {
        val o = call("GET", "/api/recipes/makers/me/inbox" + (since?.let { "?since=" + URLEncoder.encode(it.toString(), "UTF-8") } ?: ""), device = device)
        val f = o.optJSONObject("followers")
        ProfileInbox(o.optString("maker"), f?.optInt("count") ?: 0, f?.optJSONArray("items").objects().map(::face),
            o.optJSONArray("questions").objects().map { Triple(it.optString("code"), it.optString("title"), it.optString("body")) },
            o.optJSONArray("remixes").objects().map { Triple(it.optString("code"), it.optString("title"), it.optString("parentTitle")) },
            o.optJSONArray("milestones").objects().map { ProfileInbox.Milestone(it.optString("code"), it.optString("title"), it.optString("kind"), it.optInt("n")) },
            o.optJSONArray("badges").objects().map { it.optString("key") to it.optString("name") },
            followersLatest = f?.optStringOrNull("latest"), at = o.optStringOrNull("at")?.let { runCatching { Instant.parse(it) }.getOrNull() })
    }

    override suspend fun block(device: String, id: String, on: Boolean) = serverCall {
        call("POST", "/api/recipes/makers/$id/block", JSONObject().put("device", device).put("on", on))
        Unit
    }

    override suspend fun flagMaker(device: String, id: String, reason: String) = serverCall {
        call("POST", "/api/recipes/makers/$id/flag", JSONObject().put("device", device).put("reason", reason))
        Unit
    }

    override suspend fun setOpen(device: String?, id: String): SetDoc = readCall {
        val o = call("GET", "/api/recipes/sets/$id", device = device)
        SetDoc(o.getString("id"), o.optString("title"), o.optString("summary"), o.optString("maker"), o.optBoolean("mine"),
            o.optJSONArray("items").objects().map(::item), o.optInt("missing"))
    }

    override suspend fun setSave(device: String, id: String?, title: String, summary: String, codes: List<String>): String = serverCall {
        val body = JSONObject().put("device", device).put("title", title).put("summary", summary).put("codes", org.json.JSONArray(codes))
        call("POST", if (id == null) "/api/recipes/sets" else "/api/recipes/sets/$id", body).getString("id")
    }

    override suspend fun setDelete(device: String, id: String) = serverCall {
        call("DELETE", "/api/recipes/sets/$id", device = device)
        Unit
    }

    override suspend fun feed(device: String, cursor: Int): RecipePage = readCall {
        val o = call("GET", "/api/recipes/feed?cursor=$cursor", device = device)
        RecipePage(o.optJSONArray("items").objects().map(::item), if (o.isNull("next") || !o.has("next")) null else o.optInt("next"))
    }

    override suspend fun stats(device: String): MakerStats = readCall {
        val o = call("GET", "/api/recipes/makers/me/stats", device = device)
        MakerStats(o.optInt("week"), o.optInt("users"), o.optInt("likes"), o.optJSONArray("items").objects().map { i ->
            MakerStats.Item(i.optString("code"), i.optString("title"), i.optInt("users"), i.optInt("likes"), i.optInt("dislikes"),
                i.optInt("kept").takeIf { i.has("kept") && !i.isNull("kept") }, i.optInt("week"),
                i.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty())
        })
    }

    override suspend fun monthMakers(style: WorkStyle?): List<MakerFaceItem> = readCall {
        call("GET", "/api/recipes/makers/month" + (style?.let { "?style=${it.name}" } ?: "")).optJSONArray("items").objects().map(::face)
    }

    override suspend fun makerMe(device: String, face: String?, color: Long?, set: Boolean): String = serverCall {
        val body = JSONObject().put("device", device)
        if (set) body.put("face", face ?: JSONObject.NULL).put("color", color ?: JSONObject.NULL)
        call("POST", "/api/recipes/makers/me", body).getString("maker")
    }

    override suspend fun follow(device: String, id: String, on: Boolean) = serverCall {
        call("POST", "/api/recipes/makers/$id/follow", JSONObject().put("device", device).put("on", on))
        Unit
    }

    override suspend fun picks(style: WorkStyle?): List<RecipeItem> = readCall {
        call("GET", "/api/recipes/picks" + (style?.let { "?style=${it.name}" } ?: "")).optJSONArray("items").objects().map(::item)
    }

    override suspend fun news(device: String, since: Instant?): RecipeNews = readCall {
        val o = call("GET", "/api/recipes/news" + (since?.let { "?since=" + URLEncoder.encode(it.toString(), "UTF-8") } ?: ""), device = device)
        RecipeNews(o.optJSONArray("recipes").objects().map(::item),
            o.optJSONArray("replies").objects().map { Triple(it.optString("code"), it.optString("title"), it.optString("body")) },
            o.optJSONArray("remixes")?.objects()?.map { item(it) to it.optString("parentTitle") }.orEmpty(),
            o.optJSONArray("requests")?.objects()?.map { Triple(it.optString("code"), it.optString("title"), it.optString("body")) }.orEmpty())
    }

    override suspend fun image(device: String, webp: ByteArray): String = serverCall {
        call("POST", "/api/recipes/images", JSONObject().put("device", device)
            .put("webp", android.util.Base64.encodeToString(webp, android.util.Base64.NO_WRAP))).getString("id")
    }

    override suspend fun fontSend(device: String, id: String, name: String, license: String, ttf: ByteArray, fallback: String?): FontSent = serverCall {
        val o = call("POST", "/api/recipes/fonts", JSONObject().put("device", device).put("id", id).put("name", name).put("license", license)
            .put("fallback", fallback ?: JSONObject.NULL)
            .put("ttf", android.util.Base64.encodeToString(ttf, android.util.Base64.NO_WRAP)))
        FontSent(o.getString("id"), o.optString("state") == "pending")
    }

    override suspend fun fontGet(device: String, id: String): Pair<String, ByteArray>? = readCall {
        if (!enabled || !com.bamnat.core.recipe.FontCatalog.isUser(id)) return@readCall null
        val name = runCatching { call("GET", "/api/recipes/fonts/$id", device = device).optString("name", "받은 글꼴") }.getOrNull() ?: return@readCall null
        val conn = java.net.URL("$baseUrl/api/recipes/fonts/$id/file").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("X-Device", device)
        try {
            if (conn.responseCode != 200) null
            else conn.inputStream.use { readAtMost(it, FONT_MAX + 1) }.takeIf { it.size <= FONT_MAX }?.let { name to it }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun fontStandIn(id: String): String? = readCall {
        if (!enabled || !com.bamnat.core.recipe.FontCatalog.isUser(id)) return@readCall null
        runCatching { call("GET", "/api/recipes/fonts/$id/look").optString("fallback").takeIf { it.isNotEmpty() && it != "null" } }.getOrNull()
    }

    override suspend fun fontName(id: String): String? = readCall {
        if (!enabled || !com.bamnat.core.recipe.FontCatalog.isUser(id)) return@readCall null
        runCatching { call("GET", "/api/recipes/fonts/$id").optString("name").takeIf { it.isNotEmpty() } }.getOrNull()
    }

    override suspend fun fontReport(device: String, id: String, why: String) = serverCall {
        call("POST", "/api/recipes/fonts/$id/report", JSONObject().put("device", device).put("why", why)); Unit
    }

    override suspend fun materials(): List<StudioMaterial> = readCall {
        call("GET", "/api/recipes/materials?app=${BuildConfig.VERSION_CODE}").optJSONArray("items").objects().mapNotNull(StudioMaterial::parse)
    }

    override fun imageUrl(id: String): String? = if (enabled && com.bamnat.core.recipe.Cover.IMAGE.matches(id)) "$baseUrl/api/recipes/images/$id" else null

    companion object {
        private fun style(name: String?): WorkStyle? = WorkStyle.entries.firstOrNull { it.name == name }
        private fun org.json.JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

        /** [PRF-02] A maker as a face. */
        fun face(o: JSONObject) = MakerFaceItem(o.getString("maker"), o.optStringOrNull("nick"), o.optStringOrNull("face"),
            if (o.isNull("color") || !o.has("color")) null else o.optLong("color"), o.optBoolean("fresh"), o.optBoolean("quiet"),
            o.optStringOrNull("bio"), o.optBoolean("picked"), o.optBoolean("gone"))

        /** The server keeps shared fonts up to 8 MB. */
        const val FONT_MAX = 8 * 1024 * 1024

        /** A list answer ([RecipeApi.list]): its cards, the next page, and the written forms that came with them. */
        fun pageOf(o: JSONObject): RecipePage {
            val rows = o.optJSONArray("items").objects()
            return RecipePage(rows.map(::item), if (o.isNull("next")) null else o.optInt("next"),
                rows.mapNotNull { r -> r.optStringOrNull("body")?.let { b -> r.optStringOrNull("hash")?.let { h -> "${r.getString("code")}@${r.optInt("version", 1)}" to (h to b) } } }.toMap())
        }
        fun item(o: JSONObject): RecipeItem {
            val c = o.optJSONObject("cover")
            return RecipeItem(
                o.getString("code"), o.optString("title"), o.optString("summary"),
                (0 until (o.optJSONArray("styles")?.length() ?: 0)).mapNotNull { i -> WorkStyle.entries.firstOrNull { it.name == o.getJSONArray("styles").optString(i) } }.toSet(),
                if (c == null) Cover() else Cover(c.optLong("a", Cover().a), c.optLong("b", Cover().b), c.optInt("dir"), c.optString("emoji", Cover().emoji),
                    c.optStringOrNull("image")?.takeIf { Cover.IMAGE.matches(it) }),
                o.optStringOrNull("nick"), o.optInt("version", 1), o.optInt("likes"), o.optInt("dislikes"), o.optInt("users"),
                o.optInt("kept").takeIf { o.has("kept") && !o.isNull("kept") }, o.optBoolean("official"), o.optBoolean("publicData"),
                o.optStringOrNull("parent"), Instant.parse(o.getString("at")), o.optBoolean("fresh"), o.optStringOrNull("maker"),
                o.optBoolean("showcase"), ThemeSwatch.of(o.optJSONObject("look")),
                o.optJSONArray("tabs")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), o.optStringOrNull("scope"),
                o.optBoolean("newMaker"),
                o.optStringOrNull("made")?.takeIf { it == "look" || it == "func" || it == "both" },
                o.optJSONArray("tools")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in TOOL_KINDS } }.orEmpty(),
                o.optJSONArray("fx").strings().filter { it.isNotBlank() },
                o.optStringOrNull("mark")?.takeIf { it.isNotBlank() },
                o.optInt("followers").takeIf { o.has("followers") && !o.isNull("followers") },
                o.optBoolean("retired"),
            )
        }

        /** [FREE-08] 종류로 보기 (server RECIPE_KINDS), in the order the chips show them. */
        val KINDS = listOf("skin", "layout", "memo", "money", "rules")

        /** [MKX-01] 1.36 장터 구간: [SCOPE_THEME], or [SCOPE_TAB] + one of [MARKET_TABS] ("tab:" alone: every tab's). */
        const val SCOPE_THEME = "theme"
        const val SCOPE_TAB = "tab:"
        /** [RUL-07] 1.36 장터 '규칙·단추' 구간 (서버 scope `logic`, 갈래 F sectionOf). 1.39부터는 도구([SCOPE_TOOL])의 한 갈래로 열려요. */
        const val SCOPE_LOGIC = "logic"
        /**
         * 1.39 [MKC-01] 장터 '도구' 구간 (사용자 2026-11-07 "도구 또한 분류를 나눠서"): 지금 입은 테마나 기본 모양 위에 하나만 더하는 것 — 부품
         * (서버 scope `part`)과 규칙·단추 (`logic`). "tool:" alone: every tool; "tool:<종류>" one of [TOOL_KINDS]; "@<탭>" the screen it works on.
         */
        const val SCOPE_TOOL = "tool:"
        val TOOL_KINDS = listOf("part", "rule", "button")
        /** 1.39 [MKC-01] 탭별 작품의 꾸미기·기능: "tab:<탭>~look" or "~func". */
        val MADE_KINDS = listOf("look", "func")
        /**
         * 1.39 [MKC-04] '✦ 프로 기능'만 보기 (사용자 2026-11-07 "무료 프로의 구분은 확실히"): 어느 구간 뒤에든 붙는 표시 ("theme!fx",
         * "tab:memo!fx", "tool:@money!fx"). 탭별 작품·도구에서는 꾸미기·기능, 부품·규칙·단추 칩과 같은 줄의 한 칩이라 그 둘과 함께 고르지 않아요.
         */
        const val FX = "!fx"
        fun fxOnly(kind: String?): Boolean = kind?.endsWith(FX) == true
        /** 1.39 [MKD-11] '무료로 다 되는 것'만 (서버 fx=0): ✦ 장치가 하나도 없는 작품. [FX]와 같은 자리에 붙고 둘 중 하나만이에요. */
        const val FREE = "!free"
        fun freeOnly(kind: String?): Boolean = kind?.endsWith(FREE) == true
        /** [FX], [FREE] or "" — what [kind] keeps when another chip of its row is picked. */
        fun price(kind: String?): String = when { fxOnly(kind) -> FX; freeOnly(kind) -> FREE; else -> "" }
        /** [kind] without [FX] or [FREE]. */
        fun plain(kind: String?): String? = kind?.removeSuffix(FX)?.removeSuffix(FREE)
        fun withPrice(kind: String, price: String): String = plain(kind).orEmpty() + price.takeIf { it == FX || it == FREE }.orEmpty()
        fun withFx(kind: String, on: Boolean): String = withPrice(kind, if (on) FX else "")
        fun withFree(kind: String, on: Boolean): String = withPrice(kind, if (on) FREE else "")

        /**
         * 1.39 [MKD-03] 장치·종류 이름으로 찾기 (사용자는 '값 칸', '위젯', '알림 단추', '프로'로 찾아요; 서버 찾기는 제목·설명만 봐요): 그 말이
         * 가리키는 칩(장터 종류)과 그 칩의 이름. 찾기 칸 아래 칩으로 보여 누르면 그 칩으로 가요.
         */
        fun wordKind(q: String, kind: String?): Pair<String, String>? {
            val w = q.trim().removePrefix("✦").trim().replace(" ", "")
            val here = plain(kind)?.takeIf { it.isNotEmpty() } ?: SCOPE_THEME
            return when (w) {
                "프로", "프로기능", "값칸", "값", "알림단추", "단추위젯", "빠른설정", "바로가기", "fx" -> withFx(here, true) to "✦ 프로 기능 작품"
                "무료" -> withFree(here, true) to "무료로 다 되는 것"
                "위젯" -> tabKind("widget", null) to "탭별 작품 › 위젯"
                "부품" -> toolKind("part", null) to "도구 › 부품"
                "규칙", "자동규칙" -> toolKind("rule", null) to "도구 › 자동 규칙"
                "단추" -> toolKind("button", null) to "도구 › 단추"
                else -> null
            }?.takeIf { it.first != kind }
        }
        fun tabOf(kind: String?): String = plain(kind).orEmpty().removePrefix(SCOPE_TAB).substringBefore('~')
        fun madeOf(kind: String?): String? = plain(kind)?.takeIf { it.startsWith(SCOPE_TAB) }?.substringAfter('~', "")?.takeIf { it in MADE_KINDS }
        fun toolOf(kind: String?): String = plain(kind).orEmpty().removePrefix(SCOPE_TOOL).substringBefore('@')
        fun toolOn(kind: String?): String? = plain(kind)?.takeIf { it.startsWith(SCOPE_TOOL) }?.substringAfter('@', "")?.takeIf { it in MARKET_TABS }
        fun tabKind(tab: String, made: String?, fx: Boolean = false, free: Boolean = false): String =
            withPrice(SCOPE_TAB + tab + (made?.takeIf { it in MADE_KINDS }?.let { "~$it" } ?: ""), if (made != null) "" else if (fx) FX else if (free) FREE else "")
        fun toolKind(tool: String, on: String?, fx: Boolean = false, free: Boolean = false): String = withPrice(
            SCOPE_TOOL + tool.takeIf { it in TOOL_KINDS }.orEmpty() + (on?.takeIf { it in MARKET_TABS }?.let { "@$it" } ?: ""),
            if (tool in TOOL_KINDS) "" else if (fx) FX else if (free) FREE else "")
        // 1.39 [FLOW-21] 밤 화면(밤 화면·아침 한 장 부품).
        val MARKET_TABS = listOf("calendar", "pay", "money", "memo", "alarm", "widget", "night")
        private fun legacyKind(tab: String): String? = when (tab) {
            "calendar" -> "skin"; "memo" -> "memo"; "money" -> "money"; "pay", "alarm" -> "rules"; "widget" -> "layout"; else -> null
        }

        /** [MKX-01] Whether [item] belongs where [kind] points (a server before 1.36 sends no tabs: then all it sent). */
        fun fits(item: RecipeItem, kind: String?): Boolean =
            if ((fxOnly(kind) && item.fx.isEmpty()) || (freeOnly(kind) && item.fx.isNotEmpty())) false else fitsPlain(item, plain(kind))
        private fun fitsPlain(item: RecipeItem, kind: String?): Boolean = when {
            item.tabs.isEmpty() || kind == null -> true
            kind == SCOPE_THEME -> item.scope == "theme"
            kind == SCOPE_LOGIC -> item.scope == "logic"
            kind.startsWith(SCOPE_TOOL) -> (item.scope == "part" || item.scope == "logic") &&
                toolOf(kind).let { t -> t.isEmpty() || item.tools.isEmpty() || t in item.tools } && toolOn(kind).let { it == null || it in item.tabs }
            kind.startsWith(SCOPE_TAB) -> item.scope == "tab" && tabOf(kind).let { it.isEmpty() || item.tabs.firstOrNull() == it } &&
                madeOf(kind).let { m -> m == null || item.made == null || item.made == m || item.made == "both" }
            else -> true
        }

        /** What to tell the user about a refusal from the 나눔 판. */
        fun problem(e: Throwable): String {
            val c = (e as? CompanyException)?.code ?: return "잠시 뒤 다시 해 주세요"
            return when (c) {
                "offline" -> "인터넷에 연결되지 않았어요"
                // 1.36 [SEC-10] The font's own words say a foundry that sells fonts made it.
                "sold" -> "이 글꼴은 글꼴 안에 유료 글꼴 회사가 적혀 있어 나눌 수 없어요. OFL 같은 공개 글꼴만 함께 보낼 수 있어요"
                "not_yet" -> "정식 출시일부터 쓸 수 있어요"
                "name_taken" -> "공식·운영자처럼 보이는 이름은 쓸 수 없어요"
                "name_like" -> "다른 분 레시피와 이름이 거의 같아요. 이름을 바꾸거나 그 레시피를 고쳐 만들어 주세요"
                // 1.39 [FX-17] 서버도 같은 줄: 기능 작품을 남이 보게 올리는 사람은 프로.
                "fx_pro" -> "프로 기능 장치(✦)가 든 작품은 프로에서 장터에 올려요. 나만 보기로는 지금 올릴 수 있어요"
                "rate_limited" -> com.bamnat.app.company.RateLimits.wait((e as CompanyException).retryAfter ?: 86_400).let { w ->
                    "하루에 올릴 수 있는 만큼 올렸어요. ${w.removeSuffix(" 뒤")}${if (w.endsWith("뒤")) " 뒤에" else "에"} 다시 할 수 있어요"
                }
                "reason" -> "안좋아요 이유를 골라 주세요"
                "opinion" -> "의견을 5자 이상 120자 이하로 적어 주세요"
                "own" -> "내 레시피에는 누를 수 없어요"
                // PC 확인 보안 6차: not always 신고, nor 30 days (운영자 가림, 30·90일 단계); the same words as 광장's.
                "muted" -> (e as? CompanyException)?.untilMillis?.let { com.bamnat.core.plaza.CommunityCare.mutedText(it) }
                    ?: "운영 원칙을 지키지 않은 글·작품이 있어 지금은 쓸 수 없어요"
                "repeat" -> com.bamnat.core.plaza.CommunityCare.REPEAT
                "not_found" -> "지워졌거나 가려진 레시피예요"
                "too_long" -> "40자까지 쓸 수 있어요"
                "two_themes" -> "모음에는 테마를 하나만 넣을 수 있어요. 나머지는 탭별 작품으로 골라 주세요"
                "codes" -> "작품을 2~6개 골라 주세요"
                "title" -> "이름을 2~30자로 적어 주세요"
                "summary" -> "한 줄 설명은 80자까지 쓸 수 있어요"
                "blocked" -> "차단한 분이에요. 차단을 풀면 팔로우할 수 있어요"
                "too_many" -> "더 늘릴 수 없어요. 안 쓰는 것을 지우고 다시 해 주세요"
                "not_yours" -> "내 작품만 넣을 수 있어요"
                "same" -> "바뀐 것이 없어요"
                "paused" -> "잠시 쉬고 있어요"
                // 1.36 [SEC-11] Play Integrity: the app from Play on a real phone is needed to write where others see.
                "integrity" -> "Play 스토어에서 받은 밤낮으로만 올리거나 신고할 수 있어요. Play 스토어 앱을 업데이트한 뒤 다시 해 주세요"
                "image_size" -> "사진이 너무 커요. 다른 사진을 골라 주세요"
                "too_big", "too_large" -> "레시피가 너무 커요. 조각을 줄여 주세요"
                else -> com.bamnat.core.recipe.RecipeText.problemText(c)
            }
        }
    }
}
