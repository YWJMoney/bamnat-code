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
)

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

data class RecipePage(val items: List<RecipeItem>, val next: Int?)

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

data class RecipeTalk(val posts: List<RecipePost>, val left: Int?, val mutedUntil: Instant? = null)

data class Votes(val likes: Int, val dislikes: Int, val myVote: Int?, val held: Boolean = false)

/** [RCP-29] The phone's own recipes in every state. */
data class MyRecipes(val trust: Int, val reviewLeft: Int, val items: List<Pair<RecipeItem, Triple<String, String, String?>>>)

/** [RCX-01] 계보: the recipes it was made from (nearest first; null where one is gone) and those made from it. */
data class Lineage(val parents: List<RecipeItem?>, val children: List<RecipeItem>)

/** [RCX-04] 요청 판: a wish, its likes and where it stands (waiting → making → done with a recipe's [code]). */
data class RecipeRequest(
    val id: String, val style: WorkStyle?, val body: String, val likes: Int, val state: String, val code: String?, val at: Instant,
    val mine: Boolean, val taking: Boolean, val liked: Boolean,
)

/** [RCX-05][RCX-06] A maker's page. */
data class MakerPage(
    val maker: String, val nick: String?, val face: String?, val color: Long?, val mine: Boolean, val recipes: List<RecipeItem>,
    val likes: Int, val users: Int, val followers: Int, val following: Boolean, val badges: List<Pair<String, String>>,
)

/** [RCX-07][RCX-10] For 소식: new recipes of followed makers, and makers' answers to my questions (code, title, the answer). */
data class RecipeNews(val recipes: List<RecipeItem>, val replies: List<Triple<String, String, String>>)

/** [RCP-21]..[RCP-35] What the app asks of the 나눔 판 server; screenshots and tests answer with a fake. */
interface RecipeApi {
    val enabled: Boolean
    suspend fun list(style: WorkStyle?, sort: String, q: String = "", cursor: Int = 0): RecipePage
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
    suspend fun request(device: String, style: WorkStyle, body: String): String = throw CompanyException("offline")
    /** [what] like · take · done · delete; [on] false takes a like or a 만들게요 back; [code] for done. */
    suspend fun requestAct(device: String, id: String, what: String, on: Boolean = true, code: String? = null) {}
    suspend fun maker(device: String?, id: String): MakerPage = throw CompanyException("not_found")
    /** The phone's own maker id, and its face when [face]/[color] are given. */
    suspend fun makerMe(device: String, face: String? = null, color: Long? = null, set: Boolean = false): String = throw CompanyException("offline")
    suspend fun follow(device: String, id: String, on: Boolean) {}
    suspend fun picks(style: WorkStyle?): List<RecipeItem> = emptyList()
    suspend fun news(device: String, since: Instant?): RecipeNews = RecipeNews(emptyList(), emptyList())
    /** [RCX-15] A cover picture already shrunk to WebP; its id back (it shows once the operator looked). */
    suspend fun image(device: String, webp: ByteArray): String = throw CompanyException("offline")
    /** The address of a cover picture, for the image loader (the maker sees a waiting one with its device header). */
    fun imageUrl(id: String): String? = null
}

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

    override suspend fun list(style: WorkStyle?, sort: String, q: String, cursor: Int): RecipePage = readCall {
        val query = buildList {
            style?.let { add("style=${it.name}") }
            add("sort=$sort")
            if (q.isNotBlank()) add("q=" + URLEncoder.encode(q.trim().take(30), "UTF-8"))
            if (cursor > 0) add("cursor=$cursor")
        }.joinToString("&")
        val o = call("GET", "/api/recipes?$query")
        RecipePage(o.optJSONArray("items").objects().map(::item), if (o.isNull("next")) null else o.optInt("next"))
    }

    override suspend fun show(code: String, device: String?, version: Int?): RecipeDoc = readCall {
        val o = call("GET", "/api/recipes/$code" + (version?.let { "?v=$it" } ?: ""), device = device)
        RecipeDoc(
            item(o), o.getString("hash"), o.getString("body"), o.optString("scope", "public"), o.optBoolean("mine"),
            o.optInt("myVote").takeIf { o.has("myVote") }, o.optStringOrNull("state"), o.optStringOrNull("note"),
            o.optJSONObject("reasons")?.let { r ->
                val t = r.optJSONObject("tally")
                Reasons(t?.keys()?.asSequence()?.associateWith { t.optInt(it) }.orEmpty(),
                    r.optJSONArray("opinions").objects().map { it.optString("reason") to it.optString("text") })
            },
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
        MyRecipes(o.optInt("trust"), o.optInt("reviewLeft"), o.optJSONArray("items").objects().map {
            item(it) to Triple(it.optString("state"), it.optString("scope"), it.optStringOrNull("note"))
        })
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
        }, o.optInt("left").takeIf { o.has("left") }, o.optStringOrNull("mutedUntil")?.let(Instant::parse))
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
        Lineage(o.optJSONArray("parents").objects().map { if (it.optBoolean("gone")) null else item(it) }, o.optJSONArray("children").objects().map(::item))
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
                q.optBoolean("liked"))
        }
    }

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
            o.optBoolean("following"), o.optJSONArray("badges").objects().map { it.optString("key") to it.optString("name") })
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
            o.optJSONArray("replies").objects().map { Triple(it.optString("code"), it.optString("title"), it.optString("body")) })
    }

    override suspend fun image(device: String, webp: ByteArray): String = serverCall {
        call("POST", "/api/recipes/images", JSONObject().put("device", device)
            .put("webp", android.util.Base64.encodeToString(webp, android.util.Base64.NO_WRAP))).getString("id")
    }

    override fun imageUrl(id: String): String? = if (enabled && com.bamnat.core.recipe.Cover.IMAGE.matches(id)) "$baseUrl/api/recipes/images/$id" else null

    companion object {
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
                o.optBoolean("showcase"),
            )
        }

        /** What to tell the user about a refusal from the 나눔 판. */
        fun problem(e: Throwable): String {
            val c = (e as? CompanyException)?.code ?: return "잠시 뒤 다시 해 주세요"
            return when (c) {
                "offline" -> "인터넷에 연결되지 않았어요"
                "not_yet" -> "처리방침 3.8이 시행되는 11월 29일부터 쓸 수 있어요"
                "name_taken" -> "공식·운영자처럼 보이는 이름은 쓸 수 없어요"
                "name_like" -> "다른 분 레시피와 이름이 거의 같아요. 이름을 바꾸거나 그 레시피를 고쳐 만들어 주세요"
                "rate_limited" -> "오늘은 더 올릴 수 없어요. 내일 다시 해 주세요"
                "reason" -> "안좋아요 이유를 골라 주세요"
                "opinion" -> "의견을 5자 이상 120자 이하로 적어 주세요"
                "own" -> "내 레시피에는 누를 수 없어요"
                "muted" -> "신고가 쌓여 30일 동안 쓸 수 없어요"
                "not_found" -> "지워졌거나 가려진 레시피예요"
                "same" -> "바뀐 것이 없어요"
                "paused" -> "잠시 쉬고 있어요"
                "too_big", "too_large" -> "레시피가 너무 커요. 조각을 줄여 주세요"
                else -> com.bamnat.core.recipe.RecipeText.problemText(c)
            }
        }
    }
}
