package com.bamnat.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.Quote
import com.bamnat.core.money.QuoteVat
import java.io.File
import java.text.DecimalFormat

/**
 * [FLD-11] 견적서 as one picture — items with quantity, unit price and amount, 공급가액, 부가세 and the
 * total, valid-until and a note — drawn like the 거래내역서 ([StatementImage]) and sent through the share
 * sheet. Made on the phone; nothing goes to a server.
 */
object QuoteImage {
    private const val WIDTH = 1080
    private const val PAD = 56f
    private const val ROW = 84f

    private val won = DecimalFormat("#,###")
    private fun w(v: Long) = won.format(v) + "원"

    /** 1.39 [SPREAD-05][SPREAD-11] The line that names the app, also shown beside the send window's switch. */
    const val MARK_LINE = "밤낮 달력에서 만든 견적서예요."

    /** 2.0.0 [RVP-64] An item's name takes two lines at most; a note, all of its 200 letters. */
    private const val NAME_LINES = 2
    private const val NOTE_LINES = 12
    private const val LINE = 40f
    private const val NOTE_LINE = 44f

    /**
     * 2.0.0 [RVP-64] (검토 REST-05) What the picture says, line by line, before it is drawn: a long note (계좌, 조건) and an item's
     * long name ('욕실 타일 철거 및 방수 공사') wrap instead of being cut with '…' where the customer never sees the rest, and the
     * picture grows to hold them. Read by the test too.
     */
    internal class Lines(val sub: List<String>, val names: List<List<String>>, val note: List<String>)

    internal fun lines(q: Quote, sender: String): Lines {
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.textSize = 36f
        val sub = listOfNotNull(q.title.takeIf { it.isNotBlank() }, sender.takeIf { it.isNotBlank() }?.let { "보낸 사람 $it" }).joinToString(" · ")
        val subLines = if (sub.isEmpty()) emptyList() else wrap(sub, text, WIDTH - 2 * PAD, 2)
        text.textSize = 32f
        val names = items(q).map { wrap(it.name.ifBlank { "품목" }, text, NAME_WIDTH, NAME_LINES) }
        text.textSize = 30f
        val note = q.note.trim().lines().filter { it.isNotBlank() }.flatMap { wrap(it.trim(), text, WIDTH - 2 * PAD, NOTE_LINES) }.let { all ->
            if (all.size <= NOTE_LINES) all else all.take(NOTE_LINES - 1) + ellipsize(all.drop(NOTE_LINES - 1).joinToString(" "), text, WIDTH - 2 * PAD)
        }
        return Lines(subLines, names, note)
    }

    private fun items(q: Quote) = q.items.filter { it.name.isNotBlank() || it.unitPrice > 0 }

    private val xQty = WIDTH - PAD - 520f
    private val NAME_WIDTH = xQty - 150f - PAD

    fun render(q: Quote, sender: String, mark: Boolean = true): Bitmap {
        val items = items(q)
        val laid = lines(q, sender)
        val itemsHeight = laid.names.sumOf { (ROW + (it.size - 1) * LINE).toDouble() }.toFloat()
        val subExtra = (laid.sub.size - 1).coerceAtLeast(0) * 48f
        val height = (380f + subExtra + ROW + itemsHeight + 4 * 64f + 80f + laid.note.size * NOTE_LINE + 140f).toInt()
        val bmp = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        val line = Paint().apply { color = 0xFFE5E7EB.toInt(); strokeWidth = 2f }

        text.textSize = 60f; text.typeface = bold
        c.drawText("견적서", PAD, 120f, text)
        text.textSize = 36f; text.typeface = Typeface.DEFAULT; text.color = MUTED
        c.drawText("${q.date.year}.${q.date.monthValue}.${q.date.dayOfMonth}" + (q.client.takeIf { it.isNotBlank() }?.let { " · $it 귀하" } ?: ""), PAD, 180f, text)
        laid.sub.forEachIndexed { i, s -> c.drawText(s, PAD, 228f + i * 48f, text) }

        val xName = PAD
        val xPrice = WIDTH - PAD - 260f
        val xAmount = WIDTH - PAD
        var y = 310f + subExtra
        text.textSize = 30f; text.color = MUTED; text.typeface = bold
        c.drawText("품목", xName, y, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("수량", xQty, y, text)
        c.drawText("단가", xPrice, y, text)
        c.drawText("금액", xAmount, y, text)
        text.textAlign = Paint.Align.LEFT
        y += 24f
        c.drawLine(PAD, y, WIDTH - PAD, y, line)
        for ((at, it) in items.withIndex()) {
            val name = laid.names[at]
            val top = y
            y += ROW + (name.size - 1) * LINE
            text.typeface = Typeface.DEFAULT; text.textSize = 32f; text.color = INK
            // The numbers stand on the first line of the name; the name's second line goes under it.
            name.forEachIndexed { i, s -> c.drawText(s, xName, top + ROW - 24f + i * LINE, text) }
            text.textAlign = Paint.Align.RIGHT
            c.drawText(DayRate.trim(it.quantity) + it.unit, xQty, top + ROW - 24f, text)
            c.drawText(won.format(it.unitPrice), xPrice, top + ROW - 24f, text)
            c.drawText(won.format(it.amount), xAmount, top + ROW - 24f, text)
            text.textAlign = Paint.Align.LEFT
            c.drawLine(PAD, y, WIDTH - PAD, y, line)
        }
        y += 72f
        val rows = buildList {
            if (q.vat != QuoteVat.NONE) {
                add("공급가액" to w(q.supply))
                add("부가세 (10%)" to w(q.vatAmount))
            }
            add((if (q.vat == QuoteVat.NONE) "합계" else "합계 (부가세 포함)") to w(q.total))
        }
        for ((i, row) in rows.withIndex()) {
            val last = i == rows.lastIndex
            text.textSize = if (last) 42f else 34f
            text.typeface = if (last) bold else Typeface.DEFAULT
            text.color = INK
            c.drawText(row.first, xQty - 260f, y, text)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(row.second, xAmount, y, text)
            text.textAlign = Paint.Align.LEFT
            y += 64f
        }
        y += 16f
        text.textSize = 30f; text.typeface = Typeface.DEFAULT; text.color = MUTED
        c.drawText("유효기간 ${q.validUntil.year}.${q.validUntil.monthValue}.${q.validUntil.dayOfMonth}까지" +
            if (q.vat == QuoteVat.NONE) " · 부가세 없음" else "", PAD, y, text)
        for (n in laid.note) {
            y += NOTE_LINE
            c.drawText(n, PAD, y, text)
        }
        text.textSize = 26f
        // 1.39 [SPREAD-05] The line that names the app goes only while 설정 › 데이터 › '보내는 사진에 밤낮 표시' is on.
        if (mark) c.drawText(MARK_LINE, PAD, height - 48f, text)
        return bmp
    }

    /**
     * 2.0.0 [RVP-64] [s] in lines no wider than [width], broken at a space where one is near and anywhere in a long run (한글 has
     * no spaces in a long word, and an account number has none); past [max] lines the last ends in '…'.
     */
    internal fun wrap(s: String, paint: Paint, width: Float, max: Int): List<String> {
        val out = mutableListOf<String>()
        var rest = s.trim()
        while (rest.isNotEmpty()) {
            if (out.size == max - 1) { out += ellipsize(rest, paint, width); break }
            if (paint.measureText(rest) <= width) { out += rest; break }
            var n = paint.breakText(rest, true, width, null).coerceAtLeast(1)
            // Back to a space in the last third of the line, so a word is not split when it need not be.
            val space = rest.lastIndexOf(' ', n - 1)
            if (space > n * 2 / 3) n = space
            out += rest.substring(0, n).trimEnd()
            rest = rest.substring(n).trimStart()
        }
        return out
    }

    private fun ellipsize(s: String, paint: Paint, width: Float): String {
        if (paint.measureText(s) <= width) return s
        var t = s
        while (t.isNotEmpty() && paint.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    suspend fun share(context: Context, q: Quote, sender: String) {
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = File(context.cacheDir, "share/quote").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "quote-${q.date}.png")
            val bmp = render(q, sender, com.bamnat.app.share.ExportMark.on(context))
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            file
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "견적서" + (q.client.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "견적서 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val INK = 0xFF1B1F2A.toInt()
    private const val MUTED = 0xFF6B7280.toInt()
}
