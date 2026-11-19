package com.bamnat.core.plaza

/**
 * 2.0.0 [RVN-22] 검토 I10bb-03: what the company server will not take in typed text (메일·전화번호·계좌번호·금액), as one table the
 * phone and the server both read (core is the 원본; `server/company-worker/src/text-rules.js` is made from it by TextRulesExportTest),
 * so a post is checked on the phone before it goes and the user is shown the very letters, not refused after sending.
 *
 * [personalRules] is for everything ordinary (회사 규칙 토론·답글·이름·메모); [contactRules] for a room's post once 방 글에 금액 받기
 * (roomMoney, COMP-14) is on: amounts may be written, a way to reach someone or to send money may not. The 계좌번호 rules (처리방침
 * 3.14 N몫 ①의 조건): a run of digit groups joined by '-' that holds 10 to 16 digits (은행 계좌·카드 번호), and a long number right
 * after a bank word (은행·계좌·입금·송금·이체…).
 */
object TextRules {
    /** One rule: [code] what the server answers ("email", "phone", "account", "amount"); [digits] a match counts only with that many digits. */
    data class Rule(val code: String, val pattern: String, val ignoreCase: Boolean = false, val digits: IntRange? = null) {
        val regex: Regex = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
    }

    private val EMAIL = Rule("email", "[^\\s@]+@[^\\s@]+\\.[a-z]{2,}", ignoreCase = true)
    /** Any phone-like number (the ordinary check, as before 2.0.0). */
    private val PHONE_ANY = Rule("phone", "(\\+?82[\\s.-]?0?|0)\\d{1,2}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}")
    /** [RVN-17] A real phone number's shape (010·02·031…, not inside a longer run), so '3억 = 300000000원' is not one. */
    private val PHONE = Rule("phone", "(?<!\\d)(\\+?82[\\s.-]?0?|0)(1[016789]|2|[3-6][1-5]|70)[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?!\\d)")
    /** Digit groups joined by '-' (a phone number's own shape left to the phone rules). */
    private val ACCOUNT_GROUPS = Rule(
        "account",
        "(?<![\\d-])(?!(?:0|82-?)(?:1[016789]|2|[3-6][1-5]|70)-\\d{3,4}-\\d{4}(?![\\d-]))\\d{2,6}(?:-\\d{2,8}){1,4}(?![\\d-])",
        digits = 10..16,
    )
    private val ACCOUNT_BANK = Rule("account", "(은행|계좌|입금|송금|이체|뱅크|우체국|농협|신협|새마을금고)[^\\d\\n]{0,12}\\d[\\d -]{6,}\\d")
    private val AMOUNT = Rule(
        "amount",
        "\\d[\\d,.]*\\s*(원|만\\s*원|천\\s*원|억\\s*원|won\\b|krw\\b)|[일이삼사오육칠팔구십백천]+\\s*만\\s*원|\\d{1,3}(,\\d{3})+|\\d{5,}",
        ignoreCase = true,
    )

    /** The 계좌번호 rules go before the phone's: '3333-01-2345678' holds a phone-like '01-2345678' but is an account. */
    val personalRules: List<Rule> = listOf(EMAIL, ACCOUNT_GROUPS, ACCOUNT_BANK, PHONE_ANY, AMOUNT)
    val contactRules: List<Rule> = listOf(EMAIL, ACCOUNT_GROUPS, ACCOUNT_BANK, PHONE)

    /** Where [text] breaks a rule: its [code] and the very letters ([range] of [text]). */
    data class Hit(val code: String, val range: IntRange, val text: String)

    /** The first rule of [rules] that [text] breaks, with where. */
    fun find(text: String, rules: List<Rule>): Hit? {
        for (r in rules) {
            val m = r.regex.findAll(text).firstOrNull { m -> r.digits == null || m.value.count(Char::isDigit) in r.digits } ?: continue
            return Hit(r.code, m.range, m.value)
        }
        return null
    }

    fun personal(text: String): String? = find(text, personalRules)?.code
    fun contact(text: String): String? = find(text, contactRules)?.code

    /** A post's check: in a room with [roomMoney] on, a way to reach someone or send money only; anywhere else, [personal]. */
    fun post(text: String, room: Boolean, roomMoney: Boolean): Hit? = find(text, if (room && roomMoney) contactRules else personalRules)

    /**
     * Texts and what each check says of them, written into the server's file too: the server's tests hold its personal() and contact()
     * to these, so the phone and the server never part.
     */
    val SAMPLES: List<Triple<String, String?, String?>> = listOf(
        Triple("야간 끝나고 다들 뭐 드세요?", null, null),
        Triple("주간 08:00~20:00 · 야간 20:00~08:00", null, null),
        Triple("기본급의 10%, 야간은 1.5배예요", null, null),
        Triple("2026-11-13부터 바뀌어요", null, null),
        Triple("이번 달 야간 수당 32만 원 받았어요", "amount", null),
        Triple("연봉 300000000원", "amount", null),
        Triple("3억 = 300000000원", "amount", null),
        Triple("시급 10,030원", "amount", null),
        Triple("문의는 010-1234-5678로", "phone", "phone"),
        Triple("02 123 4567로 전화 주세요", "phone", "phone"),
        Triple("+82 10 1234 5678", "phone", "phone"),
        Triple("메일 a.b@example.com", "email", "email"),
        Triple("110-123-456789 로 보내 주세요", "account", "account"),
        Triple("3333-01-2345678 카카오", "account", "account"),
        Triple("카드 1234-5678-9012-3456", "account", "account"),
        Triple("국민은행 123456 78 901234", "account", "account"),
        Triple("계좌 12345678901 입니다", "account", "account"),
        Triple("입금은 우리 1002 123 456789", "account", "account"),
        Triple("2026-11-13~2026-12-13", null, null),
        Triple("82-10-1234-5678", "phone", "phone"),
        Triple("031-123-4567", "phone", "phone"),
        Triple("사번 12345", "amount", null),
        Triple("1조 2조 3조 4조", null, null),
    )
}
