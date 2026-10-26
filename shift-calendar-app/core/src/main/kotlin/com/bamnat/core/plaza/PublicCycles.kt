package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftType
import com.bamnat.core.remote.JsonObj
import com.bamnat.core.remote.RemoteFile
import com.bamnat.core.remote.RemoteFileException
import com.bamnat.core.remote.RemoteFiles
import java.time.LocalTime

/**
 * [SPAT-01] Where a public row's facts come from, as research/shift-patterns/README.md tells how to read them: an
 * article says the company works that form (rarely the order), a job notice states the work, the labor site gives the
 * 4조2교대 code table, and a community answer is one person's word (shown as 투표 중 on the plaza, never adopted).
 */
enum class CycleTrust(val label: String, val note: String) {
    ARTICLE("기사", "회사가 이 근무형태를 쓴다는 기사예요. 순서까지는 안 밝힌 것이 많아요"),
    NOTICE("공고", "채용 공고에 적힌 근무예요"),
    LABOR("노무사이트", "노무 사이트의 교대 코드표예요"),
    COMMUNITY("커뮤니티", "한 사람의 답변이라 확인 중으로만 봐요"),
    UNION("노조 게시판", "노조 게시판 글이에요"),
}

/**
 * [SPAT-01] How sure the order is: [EXACT] the source spells it, [USUAL] the source gives a count (6근2휴, 2근2휴) and
 * the usual order stands in for the user to confirm, [NONE] 형태만 (공개 순서 없음): the user picks the order.
 */
enum class OrderState { EXACT, USUAL, NONE }

/** [SPAT-01] One working shift of a public row; [usual] when the source gave only a length and the usual times stand in. */
data class CycleShift(val code: Char, val name: String, val label: String, val start: LocalTime, val end: LocalTime, val usual: Boolean = false) {
    /** As a shift type to save; the colors are the bundles' own (주간 blue, 오후 orange, 야간 purple, 당번 red). */
    fun toShiftType(): ShiftType = ShiftType(
        0, name, label, COLORS[code] ?: 0xFF2F80ED, start, end,
        breakMinutes = when {
            code == 'X' -> 240
            WorkMinutes.of(start, end) >= 11 * 60 -> 60
            else -> 30
        },
    )

    companion object {
        val COLORS = mapOf('D' to 0xFF2F80ED, 'E' to 0xFFF2994A, 'N' to 0xFF6C5CE7, 'X' to 0xFFEB5757)
        private val NAMES = mapOf('D' to ("주간" to "주"), 'E' to ("오후" to "오"), 'N' to ("야간" to "야"), 'X' to ("당번" to "당"))
        private val USUAL = mapOf('D' to (7 to 19), 'E' to (14 to 22), 'N' to (19 to 7), 'X' to (9 to 9))

        /** A shift with the usual times, for a code the row has no time for. */
        fun usual(code: Char, threeShifts: Boolean = false): CycleShift {
            val (s, e) = when {
                threeShifts && code == 'D' -> 6 to 14
                threeShifts && code == 'N' -> 22 to 6
                else -> USUAL.getValue(code)
            }
            val (name, label) = NAMES.getValue(code)
            return CycleShift(code, name, label, LocalTime.of(s, 0), LocalTime.of(e, 0), usual = true)
        }
    }
}

private object WorkMinutes {
    fun of(start: LocalTime, end: LocalTime): Int = Math.floorMod(end.toSecondOfDay() / 60 - start.toSecondOfDay() / 60, 24 * 60)
}

/**
 * [SPAT-01] One row of the public research (148 in 2026-10-25's file), its columns as they are and what the app made of
 * them. [codes] are preset codes (D 주간 · E 오후 · N 야간 · X 당번 · B 비번 · O 휴무), Monday first when [weekly]; empty for 형태만.
 * Only shift times and orders: never a pay value (결정 D79), and never shown as a company's adopted setup.
 */
data class PublicCycle(
    val id: String,
    val display: String,
    val company: String,
    val industry: String,
    val site: String,
    val form: String,
    val formKey: String,
    val orderText: String,
    val timeText: String,
    val asOf: String,
    val trustText: String,
    val trust: Set<CycleTrust>,
    /** "링크 대응 확인 필요": the link may not lead to this very fact. */
    val linkCheck: Boolean,
    /** A partner company's notice (협력사), which may differ from the main company's own staff. */
    val partner: Boolean,
    val source: String,
    val codes: String,
    val orderState: OrderState,
    val weekly: Boolean,
    val roster: Boolean,
    val shifts: List<CycleShift>,
) {
    val hasOrder: Boolean get() = codes.isNotEmpty()

    val formName: String get() = PublicCycles.formName(formKey)

    /** "기사 · 공고 · 링크 확인 필요" */
    val trustLine: String
        get() = (trust.sortedBy { it.ordinal }.map { it.label } + listOfNotNull("링크 확인 필요".takeIf { linkCheck }, "협력사 공고".takeIf { partner }))
            .joinToString(" · ").ifEmpty { trustText }

    /** Only a community answer: shown as 확인 중 and never offered first. */
    val weak: Boolean get() = trust.isNotEmpty() && trust.all { it == CycleTrust.COMMUNITY || it == CycleTrust.UNION }

    /** The row's shifts, with the usual ones for any code of [order] the row has no time for. */
    fun shiftsFor(order: String): List<CycleShift> {
        val three = formKey in THREE_FORMS || 'E' in order
        val have = shifts.associateBy { it.code }
        val need = order.filter { it in WORK }.toSet().sortedBy { WORK.indexOf(it) }
        return need.map { have[it] ?: CycleShift.usual(it, three) }
    }

    companion object {
        const val WORK = "DENX"
        val THREE_FORMS = setOf("4조3교대", "3조3교대", "5조3교대", "6조3교대", "3교대")
    }
}

/**
 * [SPAT-01][SPAT-02] The public shift cycles (결정 D80): built into the app from `remote/v1/cycles.json` (tools/cycles_build.py
 * makes it from tools/data/company-cycles.csv), and a newer signed copy from the site replaces it whole, as presets do.
 * [search] is the one search every screen uses: name (띄어쓰기·(주) 무시), 초성, 업종 and 근무형태.
 */
object PublicCycles {
    /** Shown on every public card, so no one takes it for a company's own setup. */
    const val BADGE = "공개 자료 기반"

    data class Book(val forms: List<Pair<String, String>>, val cycles: List<PublicCycle>)

    private val bundled: Book by lazy {
        RemoteFiles.bundled(RemoteFiles.CYCLES)?.let { runCatching { read(it) }.getOrNull() } ?: Book(emptyList(), emptyList())
    }
    private val bundledVersion: Long by lazy { RemoteFiles.bundled(RemoteFiles.CYCLES)?.version ?: 0L }

    @Volatile private var downloaded: Book? = null

    private val book: Book get() = downloaded ?: bundled

    val all: List<PublicCycle> get() = book.cycles

    /** A newer signed file replaces the built-in one; null goes back to it. */
    fun use(file: RemoteFile?) {
        downloaded = file?.takeIf { it.version > bundledVersion }?.let(::read)
    }

    fun byId(id: String?): PublicCycle? = all.firstOrNull { it.id == id }

    fun formName(key: String): String = book.forms.firstOrNull { it.first == key }?.second ?: key

    /** The forms in the file, most rows first, for the 근무형태 chips. */
    val forms: List<Pair<String, String>>
        get() = all.groupingBy { it.formKey }.eachCount().entries.sortedByDescending { it.value }.map { it.key to formName(it.key) }

    /** The industries, most rows first, for the 업종 chips. */
    val industries: List<String>
        get() = all.groupingBy { it.industry }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

    fun read(f: RemoteFile): Book {
        if (f.kind != RemoteFiles.CYCLES) throw RemoteFileException("kind ${f.kind}")
        val forms = f.data.objs("forms").map { it.str("key") to it.str("name") }
        val cycles = f.data.objs("cycles").mapNotNull { o -> runCatching { cycle(o) }.getOrNull() }
        if (cycles.isEmpty()) throw RemoteFileException("no cycles")
        return Book(forms, cycles)
    }

    private fun cycle(o: JsonObj): PublicCycle? {
        val codes = o.strOr("codes", "")
        if (codes.any { it !in "DENXBO" }) return null
        val shifts = o.objs("shifts").map { s ->
            CycleShift(s.str("code").single(), s.str("name"), s.str("label"), LocalTime.parse(s.str("start")), LocalTime.parse(s.str("end")), s.boolOr("usual", false))
        }
        // A rotation names a working code no shift says: the file is wrong for this row, which is left out.
        if (codes.any { it in PublicCycle.WORK } && shifts.isEmpty()) return null
        return PublicCycle(
            id = o.str("id"), display = o.str("display"), company = o.str("company"), industry = o.strOr("industry", ""),
            site = o.strOr("site", ""), form = o.strOr("form", ""), formKey = o.strOr("formKey", "기타"), orderText = o.strOr("order", ""),
            timeText = o.strOr("time", ""), asOf = o.strOr("asOf", ""), trustText = o.strOr("trust", ""),
            trust = o.strings("trustKinds").mapNotNull { k -> CycleTrust.entries.firstOrNull { it.name == k } }.toSet(),
            linkCheck = o.boolOr("linkCheck", false), partner = o.boolOr("partner", false), source = o.strOr("source", ""),
            codes = codes,
            orderState = when (o.strOr("orderState", "none")) { "exact" -> OrderState.EXACT; "usual" -> OrderState.USUAL; else -> OrderState.NONE }
                .let { if (codes.isEmpty()) OrderState.NONE else it },
            weekly = o.boolOr("weekly", false), roster = o.boolOr("roster", false), shifts = shifts,
        )
    }

    /** Lower case without spaces, (주)·주식회사 and marks, so "S-OIL 온산", "에스오일" stay what they are but "(주) 예시" finds 예시. */
    fun norm(s: String): String = s.lowercase().replace("주식회사", "").replace("(주)", "").replace("㈜", "")
        .filter { it.isLetterOrDigit() }

    /**
     * [SPAT-02] The rows for [query] (each word must match the name, 사업장, 업종 or 근무형태; 초성 only on the name), within
     * [industry] and [form] when given. Best first: a name that starts with the query, then one that has it, then the rest.
     * Two letters at least, as the company server asks; with only a chip picked, every row of it.
     */
    fun search(query: String, industry: String? = null, form: String? = null, rows: List<PublicCycle> = all): List<PublicCycle> {
        val q = query.trim()
        val pool = rows.filter { (industry == null || it.industry == industry) && (form == null || it.formKey == form) }
        if (norm(q).length < 2) return if (industry != null || form != null) pool.sortedBy { norm(it.company) } else emptyList()
        // "4조 2교대" is one thing when some row has it as one; "포스코 광양" two that must both be there.
        val whole = norm(q)
        fun fields(c: PublicCycle) = listOf(norm(c.company), norm(c.site), norm(c.industry), norm(c.formKey), norm(c.form), norm(c.display))
        val words = if (pool.any { c -> fields(c).any { whole in it } }) listOf(whole) else q.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
        val cho = Choseong.isQuery(q)
        fun score(c: PublicCycle): Int? {
            val name = norm(c.company)
            if (cho) {
                val letters = q.filter { !it.isWhitespace() }
                val of = Choseong.of(c.company)
                return when {
                    of.startsWith(letters) -> 0
                    letters in of -> 1
                    else -> null
                }
            }
            val all = fields(c)
            if (words.any { w -> all.none { w in it } }) {
                // [PLAZA-17] 1.27 AH-I1: the name said the other way (GS ↔ 지에스) or with one 자모 wrong, after every exact one.
                return if (words.size == 1) CompanyMatch.fit(c.company, q)?.let { 3 + it } else null
            }
            val first = words.first()
            return when {
                name.startsWith(first) -> 0
                first in name -> 1
                first in norm(c.site) -> 2
                else -> 3
            }
        }
        return pool.mapNotNull { c -> score(c)?.let { it to c } }
            .sortedWith(compareBy<Pair<Int, PublicCycle>> { it.first }.thenBy { it.second.weak }.thenBy { norm(it.second.company) }.thenBy { it.second.id })
            .map { it.second }
    }

    /**
     * [SPAT-02] 처음으로 회사 정보를 제공해 주세요 (사용자 2026-10-25 14:05): a company no one has given yet, as a row of its own
     * with the usual shifts of [formKey] and no order, so the same apply sheet sets it up. Nothing here is public data.
     */
    fun provided(company: String, formKey: String): PublicCycle {
        val three = formKey in PublicCycle.THREE_FORMS
        val codes = when (formKey) {
            "3조1교대", "격일제" -> listOf('X')
            "주간연속2교대" -> listOf('D', 'E')
            "고정" -> listOf('D')
            else -> if (three) listOf('D', 'E', 'N') else listOf('D', 'N')
        }
        return PublicCycle(
            id = "", display = company, company = company, industry = "", site = "", form = formName(formKey), formKey = formKey,
            orderText = "", timeText = "", asOf = "", trustText = "", trust = emptySet(), linkCheck = false, partner = false, source = "",
            codes = "", orderState = OrderState.NONE, weekly = false, roster = false, shifts = codes.map { CycleShift.usual(it, three) },
        )
    }

    /** The forms a first provider picks from, in the order people most often work them. */
    val PROVIDE_FORMS: List<String> = listOf("4조2교대", "3조2교대", "4조3교대", "3조3교대", "5조3교대", "2조2교대", "3조1교대", "격일제", "주간연속2교대", "고정")
}
