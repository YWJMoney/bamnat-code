package com.bamnat.core.money

import com.bamnat.core.remote.RemoteFile
import com.bamnat.core.remote.RemoteFileException

/**
 * 1.32 [LEDX-12][LEDX-13][LEDX-24] 가맹점 이름 다듬기 (조사/1.32/가계부-하이엔드-조사.md 2-3장), on the phone only:
 * - [display]: a company's legal name as the shop people know (우아한형제들 → 배달의민족), for lists; the line keeps what
 *   the text said, so rules and duplicates still match it.
 * - [clean]: what a payment text adds to a place's name (`_A01`, 주문번호, 온라인·모바일·결제) taken off, and a foreign
 *   card's `AMAZON.COM*AB12` / `GOOGLE *YouTube` read as the shop (amazon, YouTube).
 * - [route]: "네이버페이 / 가게" split into the pay app the money went through and the shop it reached.
 * The table is built in; a signed `merchants` file ([RemoteFiles], server → phone only) may add names. No place the
 * phone reads is ever sent anywhere to grow it.
 */
object MerchantNames {
    /** Legal or billing names (as [key] makes them) → the shop's own name. */
    private val BUILT_IN: Map<String, String> = linkedMapOf(
        "우아한형제들" to "배달의민족", "위대한상상" to "요기요", "쿠팡이츠서비스" to "쿠팡이츠", "쿠팡풀필먼트서비스" to "쿠팡",
        "비지에프리테일" to "CU", "bgf리테일" to "CU", "코리아세븐" to "세븐일레븐", "지에스리테일" to "GS25", "gs리테일" to "GS25",
        "이마트24" to "이마트24", "에스씨케이컴퍼니" to "스타벅스", "sck컴퍼니" to "스타벅스", "스타벅스커피코리아" to "스타벅스",
        "씨제이올리브영" to "올리브영", "cj올리브영" to "올리브영", "아성다이소" to "다이소", "비바리퍼블리카" to "토스",
        "카카오모빌리티" to "카카오T", "한국맥도날드" to "맥도날드", "롯데지알에스" to "롯데리아", "롯데grs" to "롯데리아",
        "비케이알" to "버거킹", "비에이치씨" to "BHC", "제너시스비비큐" to "BBQ", "교촌에프앤비" to "교촌치킨",
        "한국도미노피자" to "도미노피자", "한국파파존스" to "파파존스", "씨제이씨지브이" to "CGV", "cjcgv" to "CGV",
        "메가박스중앙" to "메가박스", "이베이코리아" to "G마켓", "지마켓" to "G마켓", "네이버파이낸셜" to "네이버페이",
        "한국전력공사" to "한국전력", "우정사업본부" to "우체국", "한국철도공사" to "코레일", "넷플릭스서비시스코리아" to "넷플릭스",
        "구글코리아" to "Google", "구글플레이" to "Google Play", "googleplay" to "Google Play", "애플코리아" to "Apple",
        "apple.com/bill" to "Apple", "applecombill" to "Apple", "에프알엘코리아" to "유니클로", "자라리테일코리아" to "ZARA",
        "에이치앤엠헤네스앤모리츠" to "H&M", "홈플러스스토어즈" to "홈플러스", "에스케이텔레콤" to "SKT", "케이티" to "KT",
        "엘지유플러스" to "LG U+", "에스케이에너지" to "SK에너지", "지에스칼텍스" to "GS칼텍스", "에쓰오일" to "S-OIL",
        "현대오일뱅크" to "현대오일뱅크", "서울교통공사" to "지하철", "한국스마트카드" to "티머니", "이비카드" to "캐시비",
    )

    @Volatile private var extra: Map<String, String> = emptyMap()

    /** The name as compared: no 결제대행 prefix, no (주), no spaces, lower case ([PaymentParser.placeKey] without the branch cut). */
    fun key(name: String): String =
        name.trim().replace(Regex("\\(주\\)|㈜|주식회사|\\(유\\)|유한회사"), " ").replace(Regex("\\s+"), "").lowercase()

    /**
     * [LEDX-12] The shop people know, keeping a branch word ("비지에프리테일 역삼점" → "CU 역삼점"); [merchant] itself when
     * the table does not know it.
     */
    fun display(merchant: String): String {
        if (merchant.isBlank()) return merchant
        val words = merchant.trim().split(Regex("\\s+"))
        val branch = words.lastOrNull()?.takeIf { words.size >= 2 && Regex("(점|지점|본점|직영점)$").containsMatchIn(it) }
        val k = key(if (branch != null) words.dropLast(1).joinToString(" ") else merchant)
        val shop = extra[k] ?: BUILT_IN[k] ?: (extra.entries + BUILT_IN.entries).firstOrNull { (legal, _) -> legal.length >= 4 && k.startsWith(legal) }?.value
            ?: return merchant
        return if (branch != null) "$shop $branch" else shop
    }

    /** Pay apps a payment goes through on its way to a shop or a card (경로). */
    val ROUTES = listOf("네이버페이", "카카오페이", "토스페이", "페이코", "PAYCO", "삼성페이", "SSG페이", "스마일페이", "쿠페이", "L.pay", "애플페이", "11pay")

    private val ROUTE_FIRST = Regex("^(${ROUTES.joinToString("|") { Regex.escape(it) }})\\s*(?:결제)?\\s*[-_/:|·>]?\\s*(.{2,})$", RegexOption.IGNORE_CASE)
    private val ROUTE_LAST = Regex("^(.{2,}?)\\s*\\(\\s*(${ROUTES.joinToString("|") { Regex.escape(it) }})\\s*\\)$", RegexOption.IGNORE_CASE)

    /** [LEDX-13] The pay app [merchant] went through and the shop: ("네이버페이", "올리브영") for "네이버페이 - 올리브영". */
    fun route(merchant: String): Pair<String?, String> {
        val s = merchant.trim()
        ROUTE_FIRST.find(s)?.let { m ->
            val shop = m.groupValues[2].trim()
            if (shop.length >= 2 && !Regex("^(결제|머니|포인트|충전|송금)").containsMatchIn(shop)) return canon(m.groupValues[1]) to shop
        }
        ROUTE_LAST.find(s)?.let { m -> return canon(m.groupValues[2]) to m.groupValues[1].trim() }
        return null to s
    }

    private fun canon(route: String): String = ROUTES.first { it.equals(route, ignoreCase = true) }

    /** Billers whose `*` is followed by the real shop: "GOOGLE *YouTube" is YouTube, "PAYPAL *STEAM" is STEAM. */
    private val THROUGH = setOf("google", "paypal", "sq", "tst", "sp", "apple", "amzn", "pp")
    private val STAR = Regex("^([A-Za-z][A-Za-z0-9.&' -]*?)\\s*\\*\\s*([A-Za-z0-9][A-Za-z0-9 .&'-]*)$")
    private val WEB = Regex("(?i)\\.(com|co\\.kr|net|kr|co\\.jp|jp|io)(/\\S*)?$")

    /**
     * [LEDX-12] A foreign card's "AMAZON.COM*AB12" → "AMAZON", "GOOGLE *YouTube" → "YouTube"; null when [piece] is not
     * such a name (a masked card number or 홍*동 stays for the parser to drop).
     */
    fun foreignStar(piece: String): String? {
        val m = STAR.matchEntire(piece.trim()) ?: return null
        val left = m.groupValues[1].trim()
        val right = m.groupValues[2].trim()
        val leftKey = left.replace(WEB, "").lowercase()
        val shop = if (leftKey in THROUGH && right.any { it.isLetter() }) right else left.replace(WEB, "")
        return shop.trim().takeIf { it.length >= 2 }
    }

    /** Words a payment text puts around a place's name. */
    private val NOISE_WORDS = setOf("온라인", "모바일", "인터넷", "결제", "결제완료", "주문", "앱결제", "온라인결제", "모바일결제")
    private val NOISE_TAIL = Regex("([_#]\\s*[A-Za-z0-9]{1,10}|\\s*-\\s*\\d{3,}|\\s*\\(\\s*주문[^)]*\\)|\\s*주문번호\\s*\\S+)$")

    /** [LEDX-12] [raw] without what the text added: a code tail (`_A01`, `#1234`), 주문번호, 온라인·모바일·결제 at either end. */
    fun clean(raw: String): String {
        var s = raw.trim()
        repeat(2) { s = s.replace(NOISE_TAIL, "").trim() }
        val words = s.split(Regex("\\s+")).toMutableList()
        while (words.size >= 2 && words.first() in NOISE_WORDS) words.removeAt(0)
        while (words.size >= 2 && words.last() in NOISE_WORDS) words.removeAt(words.lastIndex)
        val out = words.joinToString(" ").trim()
        return if (out.length >= 2) out else raw.trim()
    }

    /**
     * [LEDX-24] A signed `merchants` file's names laid over the built-in table: `data.aliases` maps a legal name to the
     * shop. Read strictly; a bad file changes nothing.
     */
    fun use(f: RemoteFile?) {
        if (f == null) { extra = emptyMap(); return }
        extra = read(f)
    }

    fun read(f: RemoteFile): Map<String, String> {
        val aliases = f.data.obj("aliases")
        val out = LinkedHashMap<String, String>()
        for (k in aliases.map.keys) {
            val shop = aliases.str(k).trim()
            if (k.isBlank() || shop.isBlank() || k.length > 60 || shop.length > 40) throw RemoteFileException("alias $k")
            out[key(k)] = shop
        }
        return out
    }

    /** The built-in table (for the published file and its test). */
    val builtIn: Map<String, String> get() = BUILT_IN
}
