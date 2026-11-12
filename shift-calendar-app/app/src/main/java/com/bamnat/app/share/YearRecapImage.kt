package com.bamnat.app.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.bamnat.core.work.YearRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 1.40 [SPREAD-37] 연말 근무 결산 as one picture (1080×1350, the 4:5 a feed or a 카톡 shows whole): the year's big number,
 * the tiles under it, and the small '밤낮 근무 달력' when [Look.mark]. Night colours: 남색 with 금빛 numbers (the app's
 * own marks). The money tile goes on only when the user showed it ([Look.money]); otherwise it is left off the picture
 * entirely, not drawn as ●●●. No name, no company, nothing that says who: the numbers are the user's own to send.
 */
object YearRecapImage {
    data class Look(val money: Boolean = false, val mark: Boolean = true)

    private const val W = 1080
    private const val H = 1350
    private const val PAD = 72f
    private const val NAVY_TOP = 0xFF151A33.toInt()
    private const val NAVY_BOTTOM = 0xFF262D57.toInt()
    private const val GOLD = 0xFFF2C46D.toInt()
    private const val IVORY = 0xFFF6F1E7.toInt()
    private const val MUTED = 0xFFA9AFCB.toInt()
    private const val TILE = 0x1FFFFFFF

    /** The tiles that go on the picture: the money one only when shown. */
    fun tilesOn(recap: YearRecap, look: Look): List<YearRecap.Tile> {
        val tiles = recap.tiles.filter { !it.money || look.money }.toMutableList()
        // Six fit. The money (when shown) and 평균 잠 always stay; the 'nice to know' ones give way first.
        for (label in GIVE_WAY) { if (tiles.size <= MAX_TILES) break; tiles.removeAll { it.label == label } }
        while (tiles.size > MAX_TILES) tiles.removeAt(tiles.indexOfLast { !it.money && it.label != "평균 잠" })
        return tiles
    }

    private const val MAX_TILES = 6
    private val GIVE_WAY = listOf("가장 바빴던 달", "가장 잘된 달", "쓴 연차·휴가", "공휴일에 한 일")

    fun render(recap: YearRecap, look: Look = Look()): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), NAVY_TOP, NAVY_BOTTOM, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // A few stars and a moon in the upper right: the night the year was worked in.
        p.color = 0x33FFFFFF
        listOf(880f to 110f, 960f to 190f, 800f to 230f, 1010f to 90f, 720f to 140f).forEach { (x, y) -> c.drawCircle(x, y, 4f, p) }
        p.color = GOLD
        c.drawCircle(930f, 150f, 46f, p)
        p.color = NAVY_TOP
        c.drawCircle(952f, 134f, 42f, p)

        p.typeface = bold; p.textSize = 40f; p.color = GOLD
        c.drawText("${recap.year}", PAD, 150f, p)
        p.textSize = 64f; p.color = IVORY
        c.drawText("내 근무 결산", PAD, 230f, p)
        p.typeface = Typeface.DEFAULT; p.textSize = 32f; p.color = MUTED
        c.drawText(recap.period, PAD, 285f, p)

        // The big number.
        val hero = recap.hero
        p.typeface = bold; p.textSize = 40f; p.color = IVORY
        c.drawText(hero.label, PAD, 410f, p)
        p.textSize = 150f; p.color = GOLD
        c.drawText(hero.value, PAD - 6f, 560f, p)
        if (hero.note.isNotEmpty()) {
            p.typeface = Typeface.DEFAULT; p.textSize = 34f; p.color = MUTED
            c.drawText(hero.note, PAD, 620f, p)
        }

        // Up to six tiles, two to a row.
        val tiles = tilesOn(recap, look)
        val gap = 24f
        val tileW = (W - 2 * PAD - gap) / 2
        val tileH = 170f
        val top = 660f
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
        tiles.forEachIndexed { i, t ->
            val x = PAD + (i % 2) * (tileW + gap)
            val y = top + (i / 2) * (tileH + gap)
            c.drawRoundRect(RectF(x, y, x + tileW, y + tileH), 28f, 28f, box)
            p.typeface = Typeface.DEFAULT; p.textSize = 30f; p.color = MUTED
            c.drawText(fit(t.label, p, tileW - 56f), x + 28f, y + 52f, p)
            p.typeface = bold; p.textSize = 56f; p.color = if (t.money) GOLD else IVORY
            c.drawText(fit(t.value, p, tileW - 56f), x + 28f, y + 118f, p)
            if (t.note.isNotEmpty()) {
                p.typeface = Typeface.DEFAULT; p.textSize = 26f; p.color = MUTED
                c.drawText(fit(t.note, p, tileW - 56f), x + 28f, y + 154f, p)
            }
        }

        p.typeface = Typeface.DEFAULT; p.textSize = 30f; p.color = MUTED
        c.drawText(if (recap.unfinished) "올해도 수고 많았어요. 남은 날도 무사히." else "한 해 동안 수고 많았어요.", PAD, H - 56f, p)
        if (look.mark) {
            p.textAlign = Paint.Align.RIGHT; p.textSize = 26f; p.color = 0xFF8A91B4.toInt()
            c.drawText(MonthImage.MARK, W - PAD, H - 56f, p)
            p.textAlign = Paint.Align.LEFT
        }
        return bmp
    }

    /** Saves the picture to 사진(Pictures/밤낮) on Android 10+, where no permission is needed; null when it could not. */
    suspend fun save(context: Context, recap: YearRecap, look: Look): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "bamnat-${recap.year}-결산-${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/밤낮")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return@withContext null
        val ok = runCatching {
            val bmp = render(recap, look)
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
            bmp.recycle()
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }.isSuccess
        if (!ok) { runCatching { resolver.delete(uri, null, null) }; null } else uri
    }

    /** Whether [save] can put the picture in 사진 on this phone (Android 10+); older phones send it instead. */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Writes the picture to the share cache and opens the share sheet. */
    suspend fun share(context: Context, recap: YearRecap, look: Look) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "bamnat-${recap.year}-recap.png")
            val bmp = render(recap, look)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            file
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, recap.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "결산 사진 보내기"))
    }

    private fun fit(s: String, paint: Paint, width: Float): String {
        if (paint.measureText(s) <= width) return s
        var n = s.length
        while (n > 1 && paint.measureText(s.take(n) + "…") > width) n--
        return s.take(n) + "…"
    }
}
