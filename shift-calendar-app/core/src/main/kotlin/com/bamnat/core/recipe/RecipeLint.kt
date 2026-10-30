package com.bamnat.core.recipe

import com.bamnat.core.layout.Contrast

/**
 * [RCP-06] 올리기 전 검사 (설계서 4-2 5단계, Obsidian 점검표에서): what is ready and what to fix, each line with where to go
 * (the 만들기 step) and, where the app can, a fix in one tap. An [Level.ERROR] line keeps the recipe from going up; a
 * [Level.WARN] line is shown and the maker decides.
 */
object RecipeLint {
    enum class Level { OK, WARN, ERROR }

    /** The 만들기 step a line sends the maker to: 1 고르기, 2 받는 사람이 정할 칸, 3 꾸미기, 4 설명. */
    data class Line(val code: String, val level: Level, val text: String, val step: Int, val fix: ((Recipe) -> Recipe)? = null)

    /** Light and dark backgrounds the calendar draws dates on. */
    private const val LIGHT_BG = 0xFFFFFFFFL
    private const val MIN_RATIO = 4.5

    /**
     * Every check on [r]. [others] are recipes already on the plaza (title and written form) for 비슷한 레시피; [mine] the
     * maker's own codes, which do not count as someone else's.
     */
    fun check(r: Recipe, others: List<Pair<String, Recipe>> = emptyList()): List<Line> {
        val out = ArrayList<Line>()
        // The whole recipe as the server will read it: out of range, too long, a link or amount in the words.
        val read = runCatching { RecipeCodec.clean(Json.read(RecipeCodec.write(r))) }
        read.exceptionOrNull()?.let { e ->
            val code = (e as? RecipeException)?.code ?: "format"
            val path = (e as? RecipeException)?.path ?: ""
            out += Line("read", Level.ERROR, readProblem(code, path), stepOf(path))
        }
        out += if (r.summary.isBlank()) Line("summary", Level.WARN, "한 줄 설명을 적으면 목록에서 더 잘 보여요", 4)
            else Line("summary", Level.OK, "한 줄 설명이 있어요", 4)
        out += if (RecipeText.graphemes(r.desc) < 10) Line("desc", Level.WARN, "설명에 누구에게 좋은지 적어 주세요", 4)
            else Line("desc", Level.OK, "설명이 있어요", 4)
        // Contrast (S3): the theme's day colours drawn as text on the light and the dark calendar.
        // On the dark calendar the app lifts a colour itself (Contrast.onBlack); on the light one it is drawn as chosen.
        val weak = themeColors(r).filter { (_, c) -> Contrast.ratio(c, LIGHT_BG) < MIN_RATIO }
        out += if (weak.isEmpty()) Line("contrast", Level.OK, "글자색이 잘 보여요 (4.5:1 이상)", 3)
            else Line("contrast", Level.WARN, "${weak.joinToString("·") { it.first }} 글자가 흐려요", 3, fix = ::fixContrast)
        // Personal values (S7): an amount kept as is, a name-like word in a memo template or the description.
        val kept = InputExtract.found(r)
        if (kept.isNotEmpty()) out += Line("amount", Level.WARN, "${kept.joinToString("·") { it.label }} 금액이 그대로 나가요. 회사 공통이 아니면 받는 사람이 정하게 해요", 2,
            fix = { InputExtract.extract(it) })
        val names = nameLike(r)
        out += if (names.isEmpty() && kept.isEmpty()) Line("personal", Level.OK, "개인 값이 없어요", 2)
            else if (names.isNotEmpty()) Line("personal", Level.WARN, "‘${names.first()}’처럼 사람 이름으로 보이는 말이 있어요", 4)
            else Line("personal", Level.OK, "사람 이름·연락처가 없어요", 2)
        if (NameSkeleton.mixedScript(r.title + " " + r.summary)) out += Line("script", Level.WARN, "이름에 다른 나라 글자가 섞여 있어요", 3)
        out += if (NameSkeleton.reserved(r.title)) Line("reserved", Level.ERROR, RecipeText.problemText("name_taken"), 3)
            else Line("reserved", Level.OK, "이름이 괜찮아요", 3)
        similar(r, others)?.let { (title, why) -> out += Line("similar", Level.WARN, "‘$title’와 $why. 그 레시피를 고쳐 만들어도 돼요", 1) }
        return out
    }

    fun ready(lines: List<Line>): Boolean = lines.none { it.level == Level.ERROR }

    private fun stepOf(path: String): Int = when {
        path.startsWith("pieces") -> 1
        path.startsWith("inputs") -> 2
        path.startsWith("title") || path.startsWith("cover") || path.startsWith("tokens") -> 3
        else -> 4
    }

    private fun readProblem(code: String, path: String): String {
        val where = when {
            path.startsWith("title") -> "이름"
            path.startsWith("summary") -> "한 줄 설명"
            path.startsWith("desc") -> "설명"
            path.startsWith("inputs") -> "받는 사람이 정할 칸"
            path.startsWith("tokens") -> "테마 색"
            path.startsWith("pieces") -> "조각"
            else -> "레시피"
        }
        return when (code) {
            "text" -> "$where 글자 수를 맞춰 주세요"
            "too_big" -> "레시피가 너무 커요. 조각을 줄여 주세요"
            "range" -> "$where 값이 범위를 넘었어요"
            "pieces" -> "조각을 하나 이상 골라 주세요"
            "styles" -> "맞는 일 유형을 골라 주세요"
            "emoji" -> "$where 이모지를 다시 골라 주세요"
            else -> "$where: " + RecipeText.problemText(code)
        }
    }

    /** The colours the calendar draws as text: today's and the day colours of a 달력 테마 piece, by name. */
    private fun themeColors(r: Recipe): List<Pair<String, Long>> {
        val theme = r.pieces.firstOrNull { it.kind == PieceKind.CAL_THEME } ?: return emptyList()
        return listOf("sat" to "토요일", "sun" to "일요일", "holiday" to "공휴일").mapNotNull { (k, label) ->
            colorOf(r, theme.values[k])?.let { label to it }
        }
    }

    private fun colorOf(r: Recipe, v: Any?): Long? = when (v) {
        is Long -> v
        is Ref.Token -> r.token(v.name)?.light
        else -> null
    }

    /** The theme's day colours darkened, a step at a time, until they read at 4.5:1 on the light calendar. */
    fun fixContrast(r: Recipe): Recipe = r.copy(pieces = r.pieces.map { p ->
        if (p.kind != PieceKind.CAL_THEME) p else Piece(p.kind, p.values.mapValues { (k, v) ->
            if (k in setOf("sat", "sun", "holiday") && v is Long) readableOnLight(v) else v
        })
    }, tokens = r.tokens.map { it.copy(light = readableOnLight(it.light), dark = Contrast.onBlack(it.dark)) })

    fun readableOnLight(c: Long): Long {
        var x = c or 0xFF000000L
        var n = 0
        while (Contrast.ratio(x, LIGHT_BG) < MIN_RATIO && n < 20) {
            fun ch(s: Int) = ((((x shr s) and 0xFF) * 9) / 10)
            x = 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            n++
        }
        return x
    }

    /** A word before 님·씨 or a title (과장, 팀장 …): it is probably someone's name (S7). */
    private val NAME = Regex("([가-힣]{2,3})\\s?(님|씨|과장|대리|팀장|부장|차장|사장|실장|주임|선배|후배|반장|조장)")

    /** Words before 님 or a title that are a role, not a name (사장님, 손님, 고객님 …). */
    private val ROLES = setOf("사장", "선생", "고객", "회원", "직원", "환자", "기사", "사모", "부모", "어르신", "관리자", "운영자", "점장", "실장", "원장",
        "교수", "의사", "간호사", "학생", "선배", "후배", "동료", "손", "님들", "여러분", "이웃", "주인", "알바", "근무자", "보호자")

    /** A word ending like this describes (하는 사장님, 좋은 선배): not a name. */
    private const val ENDINGS = "는를을의에한할된던운쁜"
    /** 1.35: a shop or a place before the title (소매점 사장님, 공장 반장님), never a name. */
    private val PLACES = Regex("(점|숍|몰|당|게|터|매장|공장|현장|식당|학원|병원|약국|가게)$")
    private const val SURNAMES = "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예봉경"

    fun nameLike(r: Recipe): List<String> {
        val words = ArrayList<String>()
        fun scan(s: String) {
            NAME.findAll(s).forEach { m ->
                val who = m.groupValues[1]
                // A name starts with a family name (김철수 과장, 민수 씨 is too short to tell); 카페 사장님 or 동네 손님 does not.
                if (who.first() in SURNAMES && who.last() !in ENDINGS && ROLES.none { who.endsWith(it) } && !PLACES.containsMatchIn(who)) words += m.value
            }
        }
        scan(r.desc)
        r.pieces.forEach { p -> walkTexts(p.values, ::scan) }
        return words.distinct()
    }

    private fun walkTexts(v: Any?, f: (String) -> Unit) {
        when (v) {
            is String -> f(v)
            is Map<*, *> -> v.values.forEach { walkTexts(it, f) }
            is List<*> -> v.forEach { walkTexts(it, f) }
        }
    }

    /**
     * 비슷한 레시피: a title with the same skeleton, or pieces that hold 90% the same values (Jaccard of their leaves). Returns
     * the other's title and how it is alike, or null.
     */
    fun similar(r: Recipe, others: List<Pair<String, Recipe>>): Pair<String, String>? {
        val skel = NameSkeleton.of(r.title)
        val mine = leaves(r)
        for ((title, o) in others) {
            if (NameSkeleton.of(o.title) == skel) return title to "이름이 거의 같아요"
            val theirs = leaves(o)
            val union = (mine + theirs).size
            if (union > 0 && (mine intersect theirs).size * 10 >= union * 9) return title to "내용이 90% 넘게 같아요"
        }
        return null
    }

    private fun leaves(r: Recipe): Set<String> {
        val out = HashSet<String>()
        fun walk(prefix: String, v: Any?) {
            when (v) {
                is Map<*, *> -> v.forEach { (k, x) -> walk("$prefix.$k", x) }
                is List<*> -> v.forEachIndexed { i, x -> walk("$prefix.$i", x) }
                else -> out += "$prefix=$v"
            }
        }
        r.pieces.forEach { walk(it.kind.key, it.values) }
        return out
    }

    /** Reads the written form back for [check] (core's strict reader). */
    private object Json {
        fun read(text: String): Any? = com.bamnat.core.remote.Json.parse(text)
    }
}
