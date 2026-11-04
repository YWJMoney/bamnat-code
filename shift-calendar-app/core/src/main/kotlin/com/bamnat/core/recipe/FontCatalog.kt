package com.bamnat.core.recipe

/**
 * [MAT-05] 1.36 글꼴 모음 (871, 2차 조사 5-3·제안 16, 사용자 2026-11-01 "공개된 글꼴들을 최대한 넣어서 선택지를 애초에 많게"): every font a
 * look may name. Four are the phone's own and always there ([SYSTEM]); the rest are open fonts the phone fetches the first time
 * someone picks one, through Google Play services' font provider (Google Fonts: SIL OFL 1.1 or Apache 2.0, free to use in an app),
 * so the app stays inside its size budget and needs no font host of its own. Until a font has come down, and on a phone without
 * Play services, the text is drawn in [Font.fallback] — never blank.
 *
 * [MAT-06] 내 글꼴: a TTF·OTF the user brings, kept on the phone under its own id `u` + 16 hex ([USER]); a recipe names it with the
 * app font most like it ([Font.fallback] of its group), so a receiver who has not got it sees that one ([resolve]).
 *
 * A font id is the only thing a recipe holds: never a file, a link or a name the app does not know (결정 D106). The server keeps the
 * same id list (server/company-worker/src/recipes.js `FONT_IDS`, checked against `docs/recipe-cases.json` "fonts").
 */
object FontCatalog {
    enum class Group(val label: String) {
        SANS("반듯한 고딕"), SERIF("명조·바탕"), ROUND("둥근·귀여운"), HAND("손글씨"), TITLE("제목·굵은"), MONO("고정폭·코딩"), LATIN("숫자·영문 전용")
    }

    /**
     * One font. [family] is its name at the provider (null for the phone's own); [korean] false draws only digits and Latin letters
     * in it (dates, amounts) and the rest in [fallback].
     */
    data class Font(val id: String, val name: String, val group: Group, val family: String? = null, val korean: Boolean = true) {
        val fallback: String get() = when (group) { Group.SERIF -> "serif"; Group.MONO -> "mono"; Group.HAND -> "hand"; else -> "sans" }
        val system: Boolean get() = family == null
    }

    val SYSTEM: List<Font> = listOf(
        Font("sans", "기본 고딕", Group.SANS), Font("serif", "기본 명조", Group.SERIF), Font("mono", "기본 고정폭", Group.MONO),
        Font("hand", "기본 손글씨", Group.HAND),
    )

    private fun k(id: String, name: String, group: Group, family: String) = Font(id, name, group, family, korean = true)
    private fun l(id: String, name: String, group: Group, family: String) = Font(id, name, group, family, korean = false)

    /** Open Korean fonts (Google Fonts, 2026-11-01 목록 기준; 각 글꼴 허가는 OFL 1.1 또는 Apache 2.0). */
    val KOREAN: List<Font> = listOf(
        k("notosanskr", "본고딕 (Noto Sans KR)", Group.SANS, "Noto Sans KR"),
        k("nanumgothic", "나눔고딕", Group.SANS, "Nanum Gothic"),
        k("gothica1", "고딕 A1", Group.SANS, "Gothic A1"),
        k("ibmplexsanskr", "IBM 플렉스 산스", Group.SANS, "IBM Plex Sans KR"),
        k("gowundodum", "고운돋움", Group.SANS, "Gowun Dodum"),
        k("orbit", "오르빗", Group.SANS, "Orbit"),
        k("sunflower", "해바라기", Group.SANS, "Sunflower"),
        k("notoserifkr", "본명조 (Noto Serif KR)", Group.SERIF, "Noto Serif KR"),
        k("nanummyeongjo", "나눔명조", Group.SERIF, "Nanum Myeongjo"),
        k("gowunbatang", "고운바탕", Group.SERIF, "Gowun Batang"),
        k("songmyung", "송명", Group.SERIF, "Song Myung"),
        k("hahmlet", "함렛", Group.SERIF, "Hahmlet"),
        k("diphylleia", "디필레이아", Group.SERIF, "Diphylleia"),
        k("jua", "주아", Group.ROUND, "Jua"),
        k("dongle", "동글", Group.ROUND, "Dongle"),
        k("cutefont", "귀여운 글꼴", Group.ROUND, "Cute Font"),
        k("stylish", "스타일리시", Group.ROUND, "Stylish"),
        k("gamjaflower", "감자꽃", Group.ROUND, "Gamja Flower"),
        k("himelody", "하이멜로디", Group.ROUND, "Hi Melody"),
        k("poorstory", "푸어스토리", Group.ROUND, "Poor Story"),
        k("singleday", "싱글데이", Group.ROUND, "Single Day"),
        k("nanumpen", "나눔손글씨 펜", Group.HAND, "Nanum Pen Script"),
        k("nanumbrush", "나눔손글씨 붓", Group.HAND, "Nanum Brush Script"),
        k("gaegu", "개구", Group.HAND, "Gaegu"),
        k("yeonsung", "연성", Group.HAND, "Yeon Sung"),
        k("dokdo", "독도", Group.HAND, "Dokdo"),
        k("eastseadokdo", "동해 독도", Group.HAND, "East Sea Dokdo"),
        k("kiranghaerang", "기랑해랑", Group.HAND, "Kirang Haerang"),
        k("bwpicture", "흑백사진", Group.HAND, "Black And White Picture"),
        k("blackhansans", "검은고딕", Group.TITLE, "Black Han Sans"),
        k("dohyeon", "도현", Group.TITLE, "Do Hyeon"),
        k("gugi", "구기", Group.TITLE, "Gugi"),
        k("bagelfatone", "베이글", Group.TITLE, "Bagel Fat One"),
        k("gasoekone", "가석", Group.TITLE, "Gasoek One"),
        k("moiraione", "모이라이", Group.TITLE, "Moirai One"),
        k("grandifloraone", "그랜디플로라", Group.TITLE, "Grandiflora One"),
        k("nanumgothiccoding", "나눔고딕 코딩", Group.MONO, "Nanum Gothic Coding"),
    )

    /** Open fonts for digits and Latin letters: a date or an amount in a face of its own; Korean stays [Font.fallback]. */
    val LATIN: List<Font> = listOf(
        l("bebasneue", "Bebas Neue", Group.LATIN, "Bebas Neue"),
        l("oswald", "Oswald", Group.LATIN, "Oswald"),
        l("anton", "Anton", Group.LATIN, "Anton"),
        l("archivoblack", "Archivo Black", Group.LATIN, "Archivo Black"),
        l("montserrat", "Montserrat", Group.LATIN, "Montserrat"),
        l("poppins", "Poppins", Group.LATIN, "Poppins"),
        l("quicksand", "Quicksand", Group.LATIN, "Quicksand"),
        l("comfortaa", "Comfortaa", Group.LATIN, "Comfortaa"),
        l("fredoka", "Fredoka", Group.LATIN, "Fredoka"),
        l("nunito", "Nunito", Group.LATIN, "Nunito"),
        l("rubik", "Rubik", Group.LATIN, "Rubik"),
        l("josefinsans", "Josefin Sans", Group.LATIN, "Josefin Sans"),
        l("playfair", "Playfair Display", Group.LATIN, "Playfair Display"),
        l("lora", "Lora", Group.LATIN, "Lora"),
        l("abrilfatface", "Abril Fatface", Group.LATIN, "Abril Fatface"),
        l("dmserif", "DM Serif Display", Group.LATIN, "DM Serif Display"),
        l("pacifico", "Pacifico", Group.LATIN, "Pacifico"),
        l("lobster", "Lobster", Group.LATIN, "Lobster"),
        l("dancingscript", "Dancing Script", Group.LATIN, "Dancing Script"),
        l("caveat", "Caveat", Group.LATIN, "Caveat"),
        l("permanentmarker", "Permanent Marker", Group.LATIN, "Permanent Marker"),
        l("shadowsintolight", "Shadows Into Light", Group.LATIN, "Shadows Into Light"),
        l("righteous", "Righteous", Group.LATIN, "Righteous"),
        l("bungee", "Bungee", Group.LATIN, "Bungee"),
        l("monoton", "Monoton", Group.LATIN, "Monoton"),
        l("shrikhand", "Shrikhand", Group.LATIN, "Shrikhand"),
        l("orbitron", "Orbitron", Group.LATIN, "Orbitron"),
        l("pressstart", "Press Start 2P", Group.LATIN, "Press Start 2P"),
        l("silkscreen", "Silkscreen", Group.LATIN, "Silkscreen"),
        l("vt323", "VT323", Group.LATIN, "VT323"),
        l("spacemono", "Space Mono", Group.LATIN, "Space Mono"),
        l("robotomono", "Roboto Mono", Group.LATIN, "Roboto Mono"),
        l("jetbrainsmono", "JetBrains Mono", Group.LATIN, "JetBrains Mono"),
    )

    val ALL: List<Font> = SYSTEM + KOREAN + LATIN
    private val BY_ID = ALL.associateBy { it.id }

    /** The ids a recipe may name besides [USER]: the server's `FONT_IDS` is the same list. */
    val IDS: List<String> = ALL.map { it.id }

    /** 내 글꼴: `u` and the first 16 hex of the file's SHA-256. */
    val USER = Regex("^u[0-9a-f]{16}$")

    fun byId(id: String?): Font? = id?.let { BY_ID[it] }
    fun isUser(id: String?): Boolean = id != null && USER.matches(id)

    /** Null passes, else "font". */
    fun idProblem(s: String): String? = if (s in BY_ID || USER.matches(s)) null else "font"

    /**
     * What to draw [id] as on this phone: itself when it is an app font or one of [mine] (the user's fonts here), else the app font
     * [fallbackOf] gives it (a user font that came in a recipe without its file), else the phone's own sans.
     */
    fun resolve(id: String?, mine: Set<String>, fallbackOf: (String) -> String? = { null }): String = when {
        id == null -> "sans"
        id in BY_ID -> id
        isUser(id) && id in mine -> id
        isUser(id) -> fallbackOf(id)?.takeIf { it in BY_ID } ?: "sans"
        else -> "sans"
    }

    /** The sample line each font is shown in (2차 사용성 제안: 이름만으로는 모양을 몰라요). */
    const val SAMPLE = "3월 14일 야간 · 1,234원"
    const val SAMPLE_LATIN = "14 · 23:30 · 1,234"

    /** The groups in the order the picker lists them, each with its fonts. */
    fun grouped(): List<Pair<Group, List<Font>>> = Group.entries.map { g -> g to ALL.filter { it.group == g } }.filter { it.second.isNotEmpty() }

    /** Limits for 내 글꼴 (5-3: 크기 한도를 두고, 읽다 실패하면 기본 글꼴로). */
    const val USER_MAX_BYTES = 20L * 1024 * 1024
    const val USER_MAX_COUNT = 30
}
