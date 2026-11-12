package com.bamnat.core.imports

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * [READ-30] 1.38 (사용자 2026-11-05 "사장님·관리자들이 종이 사진이나 엑셀표·PDF 같은 것 등 어떠한 형식으로든 제공하더라도 완벽하게
 * 인식"): the table files a 병동·공장·가게 hands out besides .xlsx and CSV, read here without a spreadsheet library.
 *
 * - 옛 엑셀 .xls (BIFF8 inside an OLE2 compound file): strings (SST), numbers (NUMBER·RK·MULRK), formulas' saved results,
 *   merged cells, dates by the cell's format, 1904 dates.
 * - HTML 표 saved as .xls: what most Korean 근태·ERP·그룹웨어 "엑셀 내려받기" buttons give (`<table>` with colspan·rowspan, EUC-KR).
 * - Excel 2003 XML (SpreadsheetML) saved as .xls: `ss:Index`, `ss:MergeAcross`·`ss:MergeDown`.
 * - OpenDocument .ods (LibreOffice, 한셀 "다른 이름으로 저장"): repeated rows and columns, spanned cells.
 *
 * What a file holds is told by its first bytes, not its name: a ".xls" is any of the first three.
 */
object SheetFormats {
    enum class Kind { ZIP, OLE, HTML, XML2003, PDF, IMAGE, TEXT }

    /** A merged range, rows and columns from 0, both ends included. */
    data class Merge(val r1: Int, val c1: Int, val r2: Int, val c2: Int)

    private const val MAX_COLS = 200

    fun kindOf(bytes: ByteArray): Kind {
        fun at(i: Int, vararg b: Int) = bytes.size >= i + b.size && b.indices.all { bytes[i + it] == b[it].toByte() }
        if (at(0, 0x50, 0x4B)) return Kind.ZIP
        if (at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) return Kind.OLE
        if (at(0, 0x25, 0x50, 0x44, 0x46)) return Kind.PDF
        if (at(0, 0xFF, 0xD8) || at(0, 0x89, 0x50, 0x4E, 0x47) || at(0, 0x47, 0x49, 0x46) || (at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50)) ||
            at(4, 0x66, 0x74, 0x79, 0x70)) return Kind.IMAGE
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1).trimStart('﻿', 'ï', '»', '¿', ' ', '\r', '\n', '\t').lowercase()
        if (head.contains("urn:schemas-microsoft-com:office:spreadsheet") && head.contains("<workbook")) return Kind.XML2003
        if (head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<table") || (head.startsWith("<") && head.contains("<html"))) return Kind.HTML
        return Kind.TEXT
    }

    /** [rows] (by their row number) with each merged range's top-left value in every cell it covers; empty rows dropped. */
    fun filled(rows: Map<Int, List<String>>, merges: List<Merge>): List<List<String>> {
        if (merges.isEmpty()) return rows.toSortedMap().values.toList()
        val grid = java.util.TreeMap<Int, MutableList<String>>()
        rows.forEach { (i, r) -> grid[i] = r.toMutableList() }
        for (m in merges) {
            if (m.r2 - m.r1 > 400 || m.c2 >= MAX_COLS) continue
            val v = grid[m.r1]?.getOrNull(m.c1).orEmpty()
            if (v.isEmpty()) continue
            for (r in m.r1..m.r2) {
                val row = grid.getOrPut(r) { mutableListOf() }
                while (row.size <= m.c2) row += ""
                for (c in m.c1..m.c2) if (row[c].isEmpty()) row[c] = v
            }
        }
        return grid.values.filter { r -> r.any { it.isNotEmpty() } }
    }

    // ------------------------------------------------------------------ HTML

    fun html(text: String): List<Sheets.Sheet> {
        val tables = Regex("""<table\b[^>]*>(.*?)</table>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(text).toList()
        if (tables.isEmpty()) throw Sheets.SheetException("이 파일에서 표를 찾지 못했어요")
        return tables.mapIndexed { i, t -> Sheets.Sheet("표 ${i + 1}", htmlRows(t.groupValues[1])) }.filter { it.rows.isNotEmpty() }
    }

    private fun htmlRows(body: String): List<List<String>> {
        val trs = Regex("""<tr\b[^>]*>(.*?)(?=<tr\b|</tbody|</thead|$)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(body)
        val cellRe = Regex("""<t([dh])\b([^>]*)>(.*?)(?=<t[dh]\b|</tr|$)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val grid = java.util.TreeMap<Int, MutableMap<Int, String>>()
        val merges = mutableListOf<Merge>()
        var r = 0
        for (tr in trs) {
            if (r >= Sheets.MAX_ROWS) break
            val row = grid.getOrPut(r) { HashMap() }
            var c = 0
            for (td in cellRe.findAll(tr.groupValues[1])) {
                while (row.containsKey(c)) c++
                val attrs = td.groupValues[2]
                val cs = Regex("""colspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, MAX_COLS) ?: 1
                val rs = Regex("""rowspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 400) ?: 1
                val v = htmlText(td.groupValues[3].replace(Regex("""</t[dh]>.*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), ""))
                if (c >= MAX_COLS) break
                // The cells a span covers are taken, so the next cell of a later row goes past them; the value fills them below.
                for (rr in r until r + rs) for (cc in c until minOf(c + cs, MAX_COLS)) grid.getOrPut(rr) { HashMap() }.putIfAbsent(cc, if (rr == r && cc == c) v else "")
                grid.getValue(r)[c] = v
                if (cs > 1 || rs > 1) merges += Merge(r, c, r + rs - 1, minOf(c + cs, MAX_COLS) - 1)
                c += cs
            }
            r++
        }
        val rows = grid.mapValues { (_, m) -> List((m.keys.maxOrNull() ?: -1) + 1) { m[it].orEmpty() } }
        return filled(rows, merges)
    }

    private fun htmlText(s: String): String = entities(
        s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ").replace(Regex("""<style\b.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("""<[^>]*>"""), ""),
    ).replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

    internal fun entities(s: String): String = s
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { runCatching { String(Character.toChars(it.groupValues[1].toInt(16))) }.getOrDefault("") }
        .replace(Regex("""&#(\d+);""")) { runCatching { String(Character.toChars(it.groupValues[1].toInt())) }.getOrDefault("") }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    // ------------------------------------------------------------------ Excel 2003 XML

    fun xml2003(text: String): List<Sheets.Sheet> {
        val sheets = Regex("""<(?:ss:)?Worksheet\b([^>]*)>(.*?)</(?:ss:)?Worksheet>""", RegexOption.DOT_MATCHES_ALL).findAll(text).map { ws ->
            val name = Regex("""Name="([^"]*)"""").find(ws.groupValues[1])?.groupValues?.get(1)?.let(::entities).orEmpty()
            val grid = java.util.TreeMap<Int, MutableMap<Int, String>>()
            val merges = mutableListOf<Merge>()
            var r = -1
            for (row in Regex("""<(?:ss:)?Row\b([^>]*?)(?:/>|>(.*?)</(?:ss:)?Row>)""", RegexOption.DOT_MATCHES_ALL).findAll(ws.groupValues[2])) {
                r = Regex("""Index="(\d+)"""").find(row.groupValues[1])?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: (r + 1)
                if (r >= Sheets.MAX_ROWS) break
                var c = -1
                for (cell in Regex("""<(?:ss:)?Cell\b([^>]*?)(?:/>|>(.*?)</(?:ss:)?Cell>)""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[2])) {
                    val a = cell.groupValues[1]
                    c = Regex("""Index="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: (c + 1)
                    if (c >= MAX_COLS) break
                    val data = Regex("""<(?:ss:)?Data\b([^>]*)>(.*?)</(?:ss:)?Data>""", RegexOption.DOT_MATCHES_ALL).find(cell.groupValues[2])
                    var v = data?.groupValues?.get(2)?.replace(Regex("""<[^>]*>"""), "")?.let(::entities)?.trim().orEmpty()
                    if (data?.groupValues?.get(1)?.contains("DateTime") == true) v = v.substringBefore("T00:00:00").replace('T', ' ').substringBefore(".000")
                    grid.getOrPut(r) { HashMap() }[c] = v
                    val across = Regex("""MergeAcross="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val down = Regex("""MergeDown="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (across > 0 || down > 0) merges += Merge(r, c, r + down, minOf(c + across, MAX_COLS - 1))
                    c += across
                }
            }
            Sheets.Sheet(name, filled(grid.mapValues { (_, m) -> List((m.keys.maxOrNull() ?: -1) + 1) { m[it].orEmpty() } }, merges))
        }.toList()
        if (sheets.isEmpty()) throw Sheets.SheetException("엑셀 파일에서 시트를 찾지 못했어요")
        return sheets
    }

    // ------------------------------------------------------------------ OpenDocument

    fun isOds(bytes: ByteArray): Boolean = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        generateSequence { z.nextEntry }.any { it.name == "content.xml" }
    }

    fun ods(bytes: ByteArray): List<Sheets.Sheet> {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            generateSequence { z.nextEntry }.firstOrNull { it.name == "content.xml" } ?: throw Sheets.SheetException("시트를 찾지 못했어요")
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = z.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > Sheets.MAX_UNPACKED) throw Sheets.SheetException("파일이 너무 커요")
            }
            out.toString("UTF-8")
        }
        return Regex("""<table:table\b([^>]*)>(.*?)</table:table>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { t ->
            val name = Regex("""table:name="([^"]*)"""").find(t.groupValues[1])?.groupValues?.get(1)?.let(::entities).orEmpty()
            val grid = java.util.TreeMap<Int, MutableMap<Int, String>>()
            val merges = mutableListOf<Merge>()
            var r = 0
            for (row in Regex("""<table:table-row\b([^>]*?)(?:/>|>(.*?)</table:table-row>)""", RegexOption.DOT_MATCHES_ALL).findAll(t.groupValues[2])) {
                val times = Regex("""number-rows-repeated="(\d+)"""").find(row.groupValues[1])?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val cells = HashMap<Int, String>()
                var c = 0
                for (cell in Regex("""<table:(?:covered-)?table-cell\b([^>]*?)(?:/>|>(.*?)</table:(?:covered-)?table-cell>)""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[2])) {
                    val a = cell.groupValues[1]
                    val rep = (Regex("""number-columns-repeated="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull() ?: 1).coerceAtMost(MAX_COLS)
                    val v = when {
                        a.contains("office:value-type=\"date\"") -> Regex("""office:date-value="([^"]*)"""").find(a)?.groupValues?.get(1)?.substringBefore("T00:00:00")?.replace('T', ' ').orEmpty()
                        a.contains("office:value-type=\"float\"") || a.contains("office:value-type=\"percentage\"") ->
                            Regex("""office:value="([^"]*)"""").find(a)?.groupValues?.get(1)?.let { n -> n.toDoubleOrNull()?.let { d -> if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else n } ?: n }.orEmpty()
                        a.contains("office:value-type=\"time\"") -> Regex("""office:time-value="PT(\d+)H(\d+)M""").find(a)?.let { "${it.groupValues[1].toInt()}:${it.groupValues[2]}" }.orEmpty()
                        else -> Regex("""<text:p\b[^>]*>(.*?)</text:p>""", RegexOption.DOT_MATCHES_ALL).findAll(cell.groupValues[2])
                            .joinToString(" ") { entities(it.groupValues[1].replace(Regex("""<text:s\b[^>]*/>"""), " ").replace(Regex("""<[^>]*>"""), "")) }.trim()
                    }
                    val across = Regex("""number-columns-spanned="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val down = Regex("""number-rows-spanned="(\d+)"""").find(a)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    if (across > 1 || down > 1) merges += Merge(r, c, r + down - 1, minOf(c + across, MAX_COLS) - 1)
                    repeat(rep) { if (c < MAX_COLS) { if (v.isNotEmpty()) cells[c] = v; c++ } }
                }
                // A run of empty rows written once (LibreOffice fills the sheet to 1,048,576 so) is skipped, not counted out.
                if (cells.isNotEmpty()) repeat(times.coerceAtMost(Sheets.MAX_ROWS)) { grid[r + it] = HashMap(cells) }
                r += times.coerceAtMost(Sheets.MAX_ROWS)
                if (grid.size >= Sheets.MAX_ROWS) break
            }
            Sheets.Sheet(name, filled(grid.mapValues { (_, m) -> List((m.keys.maxOrNull() ?: -1) + 1) { m[it].orEmpty() } }, merges))
        }.toList().ifEmpty { throw Sheets.SheetException("시트를 찾지 못했어요") }
    }

    // ------------------------------------------------------------------ old Excel (BIFF8 in OLE2)

    fun xls(bytes: ByteArray): List<Sheets.Sheet> {
        val ole = Ole2(bytes)
        val stream = ole.stream("Workbook") ?: ole.stream("Book") ?: throw Sheets.SheetException(oleWhy(ole))
        return Biff(stream).sheets()
    }

    /**
     * 2.0.0 [RVL-29] (검토 SCAN-16·T07b-10) What an OLE file without a workbook is, said in what to do: an Excel (or Word) file locked
     * with a password keeps its body in EncryptedPackage, a 한글 .hwp has FileHeader·BodyText, a Word .doc a WordDocument.
     */
    private fun oleWhy(ole: Ole2): String = when {
        ole.stream("EncryptedPackage") != null || ole.stream("EncryptionInfo") != null -> LOCKED
        ole.stream("FileHeader") != null || ole.stream("BodyText") != null -> HWP
        ole.stream("WordDocument") != null -> DOC
        else -> "이 파일은 열지 못했어요. 엑셀에서 .xlsx로 다시 저장해 주세요"
    }

    const val LOCKED = "암호가 걸린 파일이에요. 엑셀에서 암호를 풀고 저장한 뒤 다시 골라 주세요"
    const val HWP = "옛 한글 문서(.hwp)는 읽지 못해요. 한글에서 .hwpx나 PDF로 저장하거나 화면을 캡처해 주세요"
    const val DOC = "옛 워드 문서(.doc)는 읽지 못해요. 워드에서 .docx나 PDF로 저장하거나 화면을 캡처해 주세요"

    // ------------------------------------------------------------------ Word (.docx) and 한글 (.hwpx) tables

    /** 2.0.0 [RVL-29] What a zip holds, by its parts: a workbook, an OpenDocument, a Word or a 한글 document. */
    enum class ZipKind { XLSX, ODS, DOCX, HWPX, OTHER }

    fun zipKind(bytes: ByteArray): ZipKind {
        val names = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.take(4_000).map { it.name }.toList() }
        }.getOrDefault(emptyList())
        return when {
            names.any { it.startsWith("xl/") } -> ZipKind.XLSX
            names.any { it.startsWith("word/") } -> ZipKind.DOCX
            names.any { it.startsWith("Contents/section") } -> ZipKind.HWPX
            "content.xml" in names -> ZipKind.ODS
            else -> ZipKind.OTHER
        }
    }

    private fun part(bytes: ByteArray, wanted: (String) -> Boolean): List<Pair<String, String>> = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        val out = mutableListOf<Pair<String, String>>()
        var unpacked = 0L
        while (true) {
            val e = z.nextEntry ?: break
            if (!wanted(e.name)) continue
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                val n = z.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
                unpacked += n
                if (unpacked > Sheets.MAX_UNPACKED) throw Sheets.SheetException("파일이 너무 커요")
            }
            out += e.name to buf.toString("UTF-8")
        }
        out
    }

    /**
     * 2.0.0 [RVL-29] (I07bb-03) The tables of a Word document (word/document.xml): one sheet a table, a cell's text its paragraphs
     * joined, a cell spread over columns (gridSpan) in each of them, one continued down (vMerge) given the cell above.
     */
    fun docx(bytes: ByteArray): List<Sheets.Sheet> {
        val xml = part(bytes) { it == "word/document.xml" }.firstOrNull()?.second ?: throw Sheets.SheetException("문서에서 표를 찾지 못했어요")
        val tables = tables(xml, "w:tbl").mapIndexedNotNull { i, t ->
            val rows = mutableListOf<List<String>>()
            for (tr in Regex("""<w:tr\b.*?</w:tr>""", RegexOption.DOT_MATCHES_ALL).findAll(t)) {
                val row = mutableListOf<String>()
                for (tc in Regex("""<w:tc\b.*?</w:tc>""", RegexOption.DOT_MATCHES_ALL).findAll(tr.value)) {
                    val span = Regex("""<w:gridSpan\s+w:val="(\d+)"""").find(tc.value)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 40) ?: 1
                    val continued = Regex("""<w:vMerge\s*/>|<w:vMerge\s+w:val="continue"""").containsMatchIn(tc.value)
                    val text = if (continued) rows.lastOrNull()?.getOrNull(row.size).orEmpty() else words(tc.value, "w:t", "w:p")
                    repeat(span) { if (row.size < MAX_COLS) row += text }
                }
                if (rows.size < Sheets.MAX_ROWS) rows += row
            }
            rows.takeIf { r -> r.size >= 2 }?.let { Sheets.Sheet("표 ${i + 1}", it) }
        }
        return tables.ifEmpty { throw Sheets.SheetException("문서에서 표를 찾지 못했어요. 근무표가 표로 된 문서인지 봐 주세요") }
    }

    /**
     * 2.0.0 [RVL-29] (I07bb-03) The tables of a 한글 .hwpx (Contents/section*.xml): each cell where its address says (cellAddr), in
     * every row and column it spans (cellSpan), its text the runs of its paragraphs.
     */
    fun hwpx(bytes: ByteArray): List<Sheets.Sheet> {
        val sections = part(bytes) { it.startsWith("Contents/section") && it.endsWith(".xml") }.sortedBy { it.first }
        val tables = sections.flatMap { (_, xml) -> tables(xml, "hp:tbl") }.mapIndexedNotNull { i, t ->
            val grid = java.util.TreeMap<Int, MutableMap<Int, String>>()
            for (tc in Regex("""<hp:tc\b.*?</hp:tc>""", RegexOption.DOT_MATCHES_ALL).findAll(t)) {
                val addr = Regex("""<hp:cellAddr\s+colAddr="(\d+)"\s+rowAddr="(\d+)"""").find(tc.value) ?: continue
                val c0 = addr.groupValues[1].toInt(); val r0 = addr.groupValues[2].toInt()
                val span = Regex("""<hp:cellSpan\s+colSpan="(\d+)"\s+rowSpan="(\d+)"""").find(tc.value)
                val cs = span?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 40) ?: 1
                val rs = span?.groupValues?.get(2)?.toIntOrNull()?.coerceIn(1, 40) ?: 1
                val text = words(tc.value, "hp:t", "hp:p")
                for (r in r0 until r0 + rs) for (c in c0 until c0 + cs) if (r < Sheets.MAX_ROWS && c < MAX_COLS) grid.getOrPut(r) { HashMap() }[c] = text
            }
            val rows = grid.values.map { m -> List((m.keys.maxOrNull() ?: -1) + 1) { m[it].orEmpty() } }
            rows.takeIf { r -> r.size >= 2 }?.let { Sheets.Sheet("표 ${i + 1}", it) }
        }
        return tables.ifEmpty { throw Sheets.SheetException("문서에서 표를 찾지 못했어요. 근무표가 표로 된 문서인지 봐 주세요") }
    }

    /** The outermost tables [tag] in [xml], a table inside a cell left in it. */
    private fun tables(xml: String, tag: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (m in Regex("""<$tag[\s>/]|</$tag>""").findAll(xml)) {
            if (m.value.startsWith("</")) {
                if (depth > 0 && --depth == 0 && start >= 0) { out += xml.substring(start, m.range.last + 1); start = -1 }
            } else {
                if (depth++ == 0) start = m.range.first
            }
        }
        return out
    }

    /** A cell's words: the text of each [run] element, paragraphs ([para]) apart by a space. */
    private fun words(cell: String, run: String, para: String): String =
        Regex("""<$para\b.*?</$para>""", RegexOption.DOT_MATCHES_ALL).findAll(cell).map { p ->
            Regex("""<$run(?:\s[^>]*)?>(.*?)</$run>""", RegexOption.DOT_MATCHES_ALL).findAll(p.value).joinToString("") { entities(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }
        }.filter { it.isNotBlank() }.joinToString(" ").trim()

    /** The streams of an OLE2 compound file ([MS-CFB]): the sector chains of the FAT, the directory and the mini stream. */
    private class Ole2(val b: ByteArray) {
        private fun u16(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        private fun i32(i: Int) = u16(i) or (u16(i + 2) shl 16)
        private val sector = 1 shl u16(30)
        private val miniSector = 1 shl u16(32)
        private val miniCutoff = i32(56)
        private fun off(s: Int) = (s + 1) * sector

        private val fat: IntArray by lazy {
            val difat = mutableListOf<Int>()
            for (i in 0 until 109) i32(76 + i * 4).takeIf { it >= 0 }?.let(difat::add)
            var next = i32(68)
            var guard = 0
            while (next >= 0 && guard++ < 10_000 && off(next) + sector <= b.size) {
                val o = off(next)
                for (i in 0 until sector / 4 - 1) i32(o + i * 4).takeIf { it >= 0 }?.let(difat::add)
                next = i32(o + sector - 4)
            }
            val out = IntArray(difat.size * sector / 4)
            difat.forEachIndexed { k, s -> val o = off(s); if (o + sector <= b.size) for (i in 0 until sector / 4) out[k * sector / 4 + i] = i32(o + i * 4) }
            out
        }

        private fun chain(start: Int, table: IntArray): List<Int> {
            val out = mutableListOf<Int>()
            var s = start
            // 2.0.0 [RVL-28] (검토 SCAN-17) A chain longer than the file has sectors loops: stopped there.
            val most = minOf(200_000, b.size / 64 + 1)
            val seen = HashSet<Int>()
            while (s >= 0 && s < table.size && out.size < most && seen.add(s)) { out += s; s = table[s] }
            return out
        }

        private fun read(start: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            for (s in chain(start, fat)) { val o = off(s); if (o + sector <= b.size) out.write(b, o, sector) else if (o < b.size) out.write(b, o, b.size - o) }
            return out.toByteArray()
        }

        fun stream(name: String): ByteArray? {
            if (b.size < 512) return null
            val dir = read(i32(48))
            var root: Pair<Int, Int>? = null
            var hit: Triple<Int, Int, Int>? = null
            for (e in 0 until dir.size / 128) {
                val o = e * 128
                val len = ((dir[o + 64].toInt() and 0xFF) or ((dir[o + 65].toInt() and 0xFF) shl 8)).coerceIn(0, 64)
                val n = String(dir, o, (len - 2).coerceAtLeast(0), Charsets.UTF_16LE)
                val type = dir[o + 66].toInt()
                fun d32(i: Int) = (dir[o + i].toInt() and 0xFF) or ((dir[o + i + 1].toInt() and 0xFF) shl 8) or ((dir[o + i + 2].toInt() and 0xFF) shl 16) or ((dir[o + i + 3].toInt() and 0xFF) shl 24)
                if (type == 5) root = d32(116) to d32(120)
                if (type == 2 && n.equals(name, ignoreCase = true)) hit = Triple(d32(116), d32(120), type)
            }
            val (start, size) = hit ?: return null
            if (size < miniCutoff) {
                val (rootStart, rootSize) = root ?: return null
                val mini = read(rootStart).copyOf(rootSize.coerceAtLeast(0))
                val miniFat = read(i32(60)).let { m -> IntArray(m.size / 4) { i -> (m[i * 4].toInt() and 0xFF) or ((m[i * 4 + 1].toInt() and 0xFF) shl 8) or ((m[i * 4 + 2].toInt() and 0xFF) shl 16) or ((m[i * 4 + 3].toInt() and 0xFF) shl 24) } }
                val out = java.io.ByteArrayOutputStream()
                for (s in chain(start, miniFat)) { val o = s * miniSector; if (o + miniSector <= mini.size) out.write(mini, o, miniSector) }
                return out.toByteArray().copyOf(size)
            }
            return read(start).let { it.copyOf(minOf(size, it.size)) }
        }
    }

    /** The BIFF8 records of a Workbook stream: each sheet's cells as text, dates by their format. */
    private class Biff(val b: ByteArray) {
        private fun u16(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        private fun i32(i: Int) = u16(i) or (u16(i + 2) shl 16)

        private class Rec(val type: Int, val start: Int, val len: Int)

        private val recs: List<Rec> by lazy {
            val out = mutableListOf<Rec>()
            var i = 0
            while (i + 4 <= b.size) {
                val t = u16(i); val n = u16(i + 2)
                if (i + 4 + n > b.size) break
                out += Rec(t, i + 4, n)
                i += 4 + n
            }
            out
        }

        private val sst = mutableListOf<String>()
        private val formats = HashMap<Int, String>()
        private val xfFormat = mutableListOf<Int>()
        private var date1904 = false

        fun sheets(): List<Sheets.Sheet> {
            if (recs.any { it.type == 0x002F }) throw Sheets.SheetException("암호가 걸린 엑셀 파일이에요. 암호를 풀고 다시 저장해 주세요")
            val bound = mutableListOf<Pair<String, Int>>()
            var k = 0
            while (k < recs.size) {
                val r = recs[k]
                when (r.type) {
                    0x0085 -> if (r.len >= 8 && b[r.start + 5].toInt() == 0) bound += shortString(r.start + 6) to i32(r.start)
                    0x0022 -> date1904 = u16(r.start) == 1
                    0x041E -> formats[u16(r.start)] = longString(r.start + 2, r.start + r.len).first
                    0x00E0 -> xfFormat += u16(r.start + 2)
                    0x00FC -> {
                        // The table of strings, its pieces in CONTINUE records that may cut a string in two.
                        val pieces = mutableListOf(r)
                        while (k + 1 < recs.size && recs[k + 1].type == 0x003C) pieces += recs[++k]
                        readSst(pieces)
                    }
                }
                if (r.type == 0x000A && bound.isNotEmpty()) break
                k++
            }
            val byOffset = recs.withIndex().associate { (i, r) -> (r.start - 4) to i }
            return bound.mapNotNull { (name, offset) -> byOffset[offset]?.let { Sheets.Sheet(name, sheet(it)) } }
        }

        private fun isDate(xf: Int): Boolean {
            val f = xfFormat.getOrNull(xf) ?: return false
            if (f in 14..22 || f in 45..47) return true
            val code = formats[f]?.replace(Regex("\"[^\"]*\""), "")?.replace(Regex("""\[[^\]]*]"""), "")?.lowercase() ?: return false
            return code.contains('y') || code.contains('d') || code.contains('h')
        }

        private fun number(v: Double, xf: Int): String {
            if (isDate(xf)) {
                if (v < 1.0) return Sheets.dayFraction(v.toString())
                return Sheets.serialDate(v.toString(), date1904)
            }
            return if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        }

        private fun rk(raw: Int): Double {
            val div = raw and 1 != 0
            val v = if (raw and 2 != 0) (raw shr 2).toDouble() else java.lang.Double.longBitsToDouble((raw.toLong() and 0xFFFFFFFCL) shl 32)
            return if (div) v / 100 else v
        }

        private fun sheet(bofIndex: Int): List<List<String>> {
            val grid = java.util.TreeMap<Int, MutableMap<Int, String>>()
            val merges = mutableListOf<Merge>()
            var pendingFormula: Pair<Int, Int>? = null
            fun put(r: Int, c: Int, v: String) { if (r < Sheets.MAX_ROWS && c < MAX_COLS) grid.getOrPut(r) { HashMap() }[c] = v.trim() }
            for (i in bofIndex + 1 until recs.size) {
                val r = recs[i]
                val s = r.start
                when (r.type) {
                    0x000A -> break
                    0x00FD -> put(u16(s), u16(s + 2), sst.getOrElse(i32(s + 6)) { "" })
                    0x0203 -> put(u16(s), u16(s + 2), number(java.lang.Double.longBitsToDouble(long(s + 6)), u16(s + 4)))
                    0x027E -> put(u16(s), u16(s + 2), number(rk(i32(s + 6)), u16(s + 4)))
                    0x00BD -> {
                        val row = u16(s); val first = u16(s + 2); val n = (r.len - 6) / 6
                        for (j in 0 until n) put(row, first + j, number(rk(i32(s + 4 + j * 6 + 2)), u16(s + 4 + j * 6)))
                    }
                    0x0204 -> put(u16(s), u16(s + 2), longString(s + 6, s + r.len).first)
                    0x0006 -> {
                        val row = u16(s); val col = u16(s + 2)
                        if (u16(s + 12) == 0xFFFF) {
                            when (b[s + 6].toInt()) {
                                0 -> pendingFormula = row to col
                                1 -> put(row, col, if (b[s + 8].toInt() != 0) "TRUE" else "FALSE")
                            }
                        } else put(row, col, number(java.lang.Double.longBitsToDouble(long(s + 6)), u16(s + 4)))
                    }
                    0x0207 -> pendingFormula?.let { (row, col) -> put(row, col, longString(s, s + r.len).first); pendingFormula = null }
                    0x0205 -> if (b[s + 7].toInt() == 0) put(u16(s), u16(s + 2), if (b[s + 6].toInt() != 0) "TRUE" else "FALSE")
                    0x00E5 -> {
                        val n = u16(s)
                        for (j in 0 until n) { val o = s + 2 + j * 8; if (o + 8 <= s + r.len) merges += Merge(u16(o), u16(o + 4), u16(o + 2), u16(o + 6)) }
                    }
                }
            }
            return filled(grid.mapValues { (_, m) -> List((m.keys.maxOrNull() ?: -1) + 1) { m[it].orEmpty() } }, merges)
        }

        private fun long(i: Int): Long = (i32(i).toLong() and 0xFFFFFFFFL) or (i32(i + 4).toLong() shl 32)

        /** A sheet name: length (1 byte), flags, then the characters. */
        private fun shortString(i: Int): String {
            val n = b[i].toInt() and 0xFF
            val wide = b[i + 1].toInt() and 1 != 0
            return if (wide) String(b, i + 2, minOf(n * 2, b.size - i - 2), Charsets.UTF_16LE) else String(b, i + 2, minOf(n, b.size - i - 2), Charsets.ISO_8859_1)
        }

        /** A string with a 2-byte length, its flags, rich-text runs and phonetic data skipped; and where it ends. */
        private fun longString(i: Int, end: Int): Pair<String, Int> {
            if (i + 3 > end) return "" to end
            val n = u16(i)
            val flags = b[i + 2].toInt()
            var p = i + 3
            var runs = 0; var ext = 0
            if (flags and 8 != 0) { runs = u16(p); p += 2 }
            if (flags and 4 != 0) { ext = i32(p); p += 4 }
            val wide = flags and 1 != 0
            val bytes = (if (wide) n * 2 else n).coerceAtMost(end - p)
            val text = if (wide) String(b, p, bytes, Charsets.UTF_16LE) else String(b, p, bytes, Charsets.ISO_8859_1)
            return text to (p + bytes + runs * 4 + ext)
        }

        private fun readSst(pieces: List<Rec>) {
            // Joined, remembering where each CONTINUE starts: a string's characters crossing one start again with a flags byte.
            val first = pieces[0]
            val count = i32(first.start + 4)
            var piece = 0
            var p = first.start + 8
            var end = first.start + first.len
            fun nextPiece(): Boolean { if (piece + 1 >= pieces.size) return false; piece++; p = pieces[piece].start; end = p + pieces[piece].len; return true }
            repeat(count) {
                if (p >= end && !nextPiece()) return
                if (p + 3 > end) return
                val n = u16(p)
                var flags = b[p + 2].toInt()
                p += 3
                var runs = 0; var ext = 0
                if (flags and 8 != 0) { runs = u16(p); p += 2 }
                if (flags and 4 != 0) { ext = i32(p); p += 4 }
                val sb = StringBuilder()
                var left = n
                while (left > 0) {
                    if (p >= end) { if (!nextPiece()) break; flags = b[p].toInt(); p += 1 }
                    val wide = flags and 1 != 0
                    val can = if (wide) minOf(left, (end - p) / 2) else minOf(left, end - p)
                    sb.append(if (wide) String(b, p, can * 2, Charsets.UTF_16LE) else String(b, p, can, Charsets.ISO_8859_1))
                    p += if (wide) can * 2 else can
                    left -= can
                }
                var skip = runs * 4 + ext
                while (skip > 0) {
                    if (p >= end && !nextPiece()) break
                    val s = minOf(skip, end - p); p += s; skip -= s
                }
                sst += sb.toString()
            }
        }
    }

    /** A date written in a cell, for tests. */
    internal fun isoDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}
