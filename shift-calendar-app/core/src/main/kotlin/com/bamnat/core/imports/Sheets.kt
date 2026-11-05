package com.bamnat.core.imports

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * [DAYW-06] A table out of a file a company's 근태 system exports: an Excel workbook (.xlsx, its first sheet)
 * or a CSV in UTF-8 or the Korean Windows encoding (CP949). Read here without any spreadsheet library: an
 * .xlsx is a zip of XML parts, and only the cells' text is needed. Every cell comes back as text; a number
 * Excel keeps for a date or a time stays a number, and [AttendanceImport] reads it by the column it is in.
 */
object Sheets {
    /** Largest file read, and largest part unpacked from a workbook (a zip that unpacks to much more is refused). */
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_PART = 40 * 1024 * 1024
    const val MAX_ROWS = 2000

    class SheetException(message: String) : Exception(message)

    /** [READ-30] One sheet of a workbook, by its tab name ("" for a CSV or a page of HTML). */
    data class Sheet(val name: String, val rows: List<List<String>>)

    /**
     * The rows of [bytes]: the first sheet that has anything on it. [READ-30] 1.38: a workbook whose first sheet is a
     * cover note (안내) no longer reads as empty.
     */
    fun read(bytes: ByteArray): List<List<String>> = book(bytes).firstOrNull { s -> s.rows.any { r -> r.any { it.isNotBlank() } } }?.rows.orEmpty()

    /**
     * [READ-30] 1.38 (사용자 "엑셀표·PDF 등 어떠한 형식으로든"): every sheet of [bytes], whatever the file is underneath its name —
     * .xlsx and .ods (zips), a real old .xls (BIFF8 in an OLE2 file), the HTML table or Excel 2003 XML a 근태·ERP system saves as
     * ".xls", or CSV in UTF-8 or CP949. A merged cell's value is put in every cell it covers (연차 written once over three
     * days counts for three days; a name over two rows is on both). Nothing leaves the phone.
     */
    fun book(bytes: ByteArray): List<Sheet> {
        if (bytes.size > MAX_BYTES) throw SheetException("파일이 너무 커요")
        return when (SheetFormats.kindOf(bytes)) {
            SheetFormats.Kind.ZIP -> if (SheetFormats.isOds(bytes)) SheetFormats.ods(bytes) else xlsxBook(bytes)
            SheetFormats.Kind.OLE -> SheetFormats.xls(bytes)
            SheetFormats.Kind.HTML -> SheetFormats.html(decode(bytes))
            SheetFormats.Kind.XML2003 -> SheetFormats.xml2003(decode(bytes))
            SheetFormats.Kind.PDF -> throw SheetException("PDF는 표 파일이 아니라 그림으로 읽어요")
            SheetFormats.Kind.IMAGE -> throw SheetException("사진은 표 파일이 아니라 그림으로 읽어요")
            SheetFormats.Kind.TEXT -> listOf(Sheet("", csv(decode(bytes))))
        }
    }

    // ---- CSV ----

    /** Text of a CSV file: UTF-8 (with or without its mark) when it reads as that, CP949 otherwise. */
    fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
            val korean = listOf("x-windows-949", "MS949", "windows-949", "EUC-KR").firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() }
            String(body, korean ?: Charsets.ISO_8859_1)
        }
    }

    /** Rows of CSV text; the separator is whichever of comma, tab and semicolon the first lines use most. */
    fun csv(text: String): List<List<String>> {
        val head = text.lineSequence().take(5).joinToString("\n")
        val sep = listOf(',', '\t', ';').maxByOrNull { c -> head.count { it == c } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length && rows.size < MAX_ROWS) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == sep -> { row += cell.toString().trim(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString().trim(); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        row += cell.toString().trim()
        if (row.any { it.isNotEmpty() } && rows.size < MAX_ROWS) rows += row
        return rows
    }

    // ---- XLSX ----

    /** Rows of a workbook's first sheet, with empty cells in their places. */
    fun xlsx(bytes: ByteArray): List<List<String>> = xlsxBook(bytes).firstOrNull()?.rows.orEmpty()

    /** [READ-30] Every sheet of a workbook in its tab order, merged cells filled. */
    fun xlsxBook(bytes: ByteArray): List<Sheet> {
        val parts = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" || name == "xl/styles.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_PART) throw SheetException("파일이 너무 커요")
                    }
                    parts[name] = out.toString("UTF-8")
                }
            }
        }
        val strings = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val order = sheetsInOrder(parts).ifEmpty {
            parts.keys.filter { it.startsWith("xl/worksheets/") && !it.contains("/_rels/") }.sorted().map { it.substringAfterLast('/').removeSuffix(".xml") to it }
        }
        if (order.isEmpty()) throw SheetException("엑셀 파일에서 시트를 찾지 못했어요")
        val styles = parts["xl/styles.xml"]
        // [IMPORT-04] A workbook made on a Mac may count dates from 1904-01-01 (<workbookPr date1904="1"/>).
        val date1904 = parts["xl/workbook.xml"]?.let { wb ->
            Regex("""<workbookPr\b[^>]*>""").find(wb)?.value?.let { attr(it, "date1904") } in setOf("1", "true")
        } == true
        val times = styles?.let(::timeStyles).orEmpty()
        val dates = styles?.let(::dateStyles).orEmpty()
        return order.mapNotNull { (name, path) ->
            val xml = parts[path] ?: return@mapNotNull null
            val merges = Regex("""<mergeCell\b[^>]*\bref="([A-Z]+)(\d+):([A-Z]+)(\d+)"""").findAll(xml).mapNotNull { m ->
                val (c1, r1, c2, r2) = m.destructured
                SheetFormats.Merge(r1.toInt() - 1, columnOf(c1) ?: return@mapNotNull null, r2.toInt() - 1, columnOf(c2) ?: return@mapNotNull null)
            }.toList()
            Sheet(name, sheetRows(xml, strings, times, dates, date1904, merges))
        }
    }

    /**
     * [IMPORT-04] Which cell styles show a number as a date: Excel's built-in date formats 14–17 and 22, or a
     * format of its own with a year or a day. Such a cell comes back as 2026-10-01 (with its time when it has
     * one), so the 1900 and 1904 date systems read the same.
     */
    private fun dateStyles(xml: String): Set<Int> {
        val custom = Regex("""<numFmt\b[^>]*>""").findAll(xml).mapNotNull { m ->
            val id = attr(m.value, "numFmtId")?.toIntOrNull() ?: return@mapNotNull null
            id to unescape(attr(m.value, "formatCode").orEmpty())
        }.toMap()
        fun isDate(id: Int): Boolean {
            if (id in 14..17 || id == 22) return true
            val code = custom[id] ?: return false
            val bare = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("""\[[^\]]*]"""), "").lowercase()
            return bare.contains('y') || bare.contains('d')
        }
        val xfs = Regex("""<cellXfs\b[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1) ?: return emptySet()
        return Regex("""<xf\b[^>]*>""").findAll(xfs).withIndex()
            .filter { (_, m) -> attr(m.value, "numFmtId")?.toIntOrNull()?.let(::isDate) == true }.map { it.index }.toSet()
    }

    /**
     * [IMPORT-04] Excel's day number as a date: days since 1899-12-30 (1900 system, its 1900-02-29 included), or
     * since 1904-01-01 when the workbook says date1904. A fraction is the time of day.
     */
    internal fun serialDate(v: String, date1904: Boolean): String {
        val d = v.trim().toDoubleOrNull() ?: return v
        if (d < 1.0 || d > 2_958_465.0) return v
        val day = kotlin.math.floor(d)
        val date = (if (date1904) java.time.LocalDate.of(1904, 1, 1) else java.time.LocalDate.of(1899, 12, 30)).plusDays(day.toLong())
        val minutes = Math.round((d - day) * 24 * 60).toInt()
        return if (minutes in 1 until 24 * 60) String.format(java.util.Locale.ROOT, "%s %d:%02d", date, minutes / 60, minutes % 60) else date.toString()
    }

    /**
     * [DAYW-06] Which cell styles (their index in cellXfs) show a number as a clock time or a length (h:mm,
     * [h]:mm, mm:ss): Excel's built-in time formats 18–21 and 45–47, or a format of its own with hours or
     * seconds and no year or day.
     */
    private fun timeStyles(xml: String): Set<Int> {
        val custom = Regex("""<numFmt\b[^>]*>""").findAll(xml).mapNotNull { m ->
            val id = attr(m.value, "numFmtId")?.toIntOrNull() ?: return@mapNotNull null
            id to unescape(attr(m.value, "formatCode").orEmpty())
        }.toMap()
        fun isTime(id: Int): Boolean {
            if (id in 18..21 || id in 45..47) return true
            val code = custom[id] ?: return false
            val bare = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("""\[(?![hms]+])[^\]]*]""", RegexOption.IGNORE_CASE), "").lowercase()
            return !bare.contains('y') && !bare.contains('d') && (bare.contains('h') || bare.contains('s'))
        }
        val xfs = Regex("""<cellXfs\b[^>]*>(.*?)</cellXfs>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1) ?: return emptySet()
        return Regex("""<xf\b[^>]*>""").findAll(xfs).withIndex()
            .filter { (_, m) -> attr(m.value, "numFmtId")?.toIntOrNull()?.let(::isTime) == true }.map { it.index }.toSet()
    }

    /** [READ-30] The sheets in the workbook's tab order (hidden ones left out): tab name and part, through relationship ids. */
    private fun sheetsInOrder(parts: Map<String, String>): List<Pair<String, String>> {
        val wb = parts["xl/workbook.xml"] ?: return emptyList()
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: return emptyList()
        val byId = Regex("""<Relationship\b[^>]*>""").findAll(rels).map { it.value }.mapNotNull { r -> attr(r, "Id")?.let { it to attr(r, "Target") } }.toMap()
        return Regex("""<sheet\b[^>]*>""").findAll(wb).mapNotNull { m ->
            val tag = m.value
            if (attr(tag, "state") in setOf("hidden", "veryHidden")) return@mapNotNull null
            val rid = Regex("""\br:id="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val target = byId[rid] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            unescape(attr(tag, "name").orEmpty()) to path
        }.toList()
    }

    private fun sharedStrings(xml: String): List<String> =
        Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            // Rich text keeps its runs in several <t>; phonetic hints (<rPh>) are not the text.
            val body = si.groupValues[1].replace(Regex("""<rPh\b.*?</rPh>""", RegexOption.DOT_MATCHES_ALL), "")
            Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()

    private fun sheetRows(
        xml: String, strings: List<String>, timeStyles: Set<Int> = emptySet(), dateStyles: Set<Int> = emptySet(), date1904: Boolean = false,
        merges: List<SheetFormats.Merge> = emptyList(),
    ): List<List<String>> {
        val grid = sortedMapOf<Int, List<String>>()
        val rowRe = Regex("""<row\b[^>]*?(?:/>|>(.*?)</row>)""", RegexOption.DOT_MATCHES_ALL)
        val cellRe = Regex("""<c\b([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
        var nextRow = 0
        for (r in rowRe.findAll(xml)) {
            if (grid.size >= MAX_ROWS) break
            // [READ-30] Rows keep their place (r="5"), so a merge's rows and a blank row between two tables line up.
            val rowAt = Regex("""^<row\b[^>]*?\br="(\d+)"""").find(r.value)?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: nextRow
            nextRow = rowAt + 1
            val cells = sortedMapOf<Int, String>()
            var next = 0
            for (c in cellRe.findAll(r.groupValues[1])) {
                val attrs = c.groupValues[1]
                val body = c.groupValues[2]
                val col = attr(attrs, "r")?.let(::columnOf) ?: next
                next = col + 1
                val v = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::unescape)
                val text = when (attr(attrs, "t")) {
                    "s" -> v?.trim()?.toIntOrNull()?.let { strings.getOrNull(it) }
                    "inlineStr" -> Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { unescape(it.groupValues[1]) }
                    // Only a cell shown as a time is a fraction of a day; 0.5 in a decimal-hours column is half an hour.
                    null, "n" -> when (attr(attrs, "s")?.toIntOrNull()) {
                        in timeStyles -> v?.let(::dayFraction)
                        in dateStyles -> v?.let { serialDate(it, date1904) }
                        else -> v
                    }
                    else -> v
                }.orEmpty().trim()
                if (col < 200) cells[col] = text
            }
            if (cells.values.any { it.isNotEmpty() }) {
                val width = (cells.keys.maxOrNull() ?: -1) + 1
                grid[rowAt] = List(width) { cells[it].orEmpty() }
            }
        }
        return SheetFormats.filled(grid, merges)
    }

    /**
     * [DAYW-06] A number cell below 1 shown as a time ([timeStyles]) is Excel's fraction of a day — a clock time
     * or a length (0.25 is 6:00, 0.5 is 12:00) — so it comes back as h:mm, read the same as text. Other numbers
     * stay as they are: a 출근·퇴근 column still reads a bare fraction as a time ([AttendanceImport.parseTime]),
     * a 근무시간·연장 column reads 0.5 as half an hour.
     */
    internal fun dayFraction(v: String): String {
        val d = v.trim().toDoubleOrNull() ?: return v
        if (d <= 0.0 || d >= 1.0) return v
        val minutes = Math.round(d * 24 * 60).toInt()
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
    }

    /** "C12" → 2 (A is 0). */
    internal fun columnOf(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
    }

    private fun attr(tag: String, name: String): String? = Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    private fun unescape(s: String): String = s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { runCatching { String(Character.toChars(it.groupValues[1].toInt(16))) }.getOrDefault("") }
        .replace(Regex("""&#(\d+);""")) { runCatching { String(Character.toChars(it.groupValues[1].toInt())) }.getOrDefault("") }
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
