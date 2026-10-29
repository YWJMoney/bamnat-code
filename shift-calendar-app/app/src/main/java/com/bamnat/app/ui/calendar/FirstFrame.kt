package com.bamnat.app.ui.calendar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import java.io.File
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * [SPD-28] 1.31 N5 (상세리뷰 N5 '첫 화면을 미리 그려 두기'): the month last shown is kept as a small file (each day's colour and the one
 * or two letters of its shift), and the next start draws it at once while the calendar's data is still being read, so the
 * app opens on the calendar rather than on a spinner. Only this month's; on this phone only (no backup, nothing sent).
 */
object FirstFrame {
    data class Day(val colorArgb: Long?, val label: String)
    data class Frame(val month: YearMonth, val weekStart: DayOfWeek, val days: List<Day>)

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "first_frame.txt")

    /** Kept when the month on screen is drawn; written off the main thread by the caller. */
    fun save(context: Context, snap: ScheduleSnapshot, month: YearMonth, weekStart: DayOfWeek) {
        val days = (1..month.lengthOfMonth()).map { d ->
            val t = snap.resolver.resolve(month.atDay(d)).shiftType?.takeIf { !it.isOff }
            Day(t?.colorArgb, t?.label.orEmpty().take(2))
        }
        runCatching { file(context).writeText(encode(Frame(month, weekStart, days))) }
    }

    fun load(context: Context): Frame? = runCatching { decode(file(context).readText()) }.getOrNull()

    internal fun encode(f: Frame): String = buildString {
        append(f.month).append('|').append(f.weekStart.name)
        f.days.forEach { append('|').append(it.colorArgb?.toString(16).orEmpty()).append(',').append(it.label.replace("|", "").replace(",", "")) }
    }

    internal fun decode(text: String): Frame? {
        val p = text.split('|')
        if (p.size < 3) return null
        val month = runCatching { YearMonth.parse(p[0]) }.getOrNull() ?: return null
        val start = runCatching { DayOfWeek.valueOf(p[1]) }.getOrNull() ?: return null
        val days = p.drop(2).map { d -> d.split(',', limit = 2).let { Day(it[0].takeIf { s -> s.isNotEmpty() }?.toLongOrNull(16), it.getOrElse(1) { "" }) } }
        return Frame(month, start, days).takeIf { days.size == month.lengthOfMonth() }
    }
}

/** [SPD-28] The kept month, drawn plainly with a thin bar on top until the real calendar takes its place. */
@Composable
internal fun FirstFrameGrid(frame: FirstFrame.Frame) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).semantics { contentDescription = "달력을 불러오는 중이에요" }) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("${frame.month.year}년 ${frame.month.monthValue}월", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
        val lead = Math.floorMod(frame.month.atDay(1).dayOfWeek.value - frame.weekStart.value, 7)
        val cells = List(lead) { null } + frame.days.mapIndexed { i, d -> (i + 1) to d }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 7).forEach { i ->
                    val cell = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(0.8f).padding(1.dp), contentAlignment = Alignment.TopCenter) {
                        if (cell != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${cell.first}", style = MaterialTheme.typography.bodySmall)
                            val c = cell.second.colorArgb
                            if (c != null) Box(Modifier.fillMaxWidth().padding(top = 2.dp).background(Color(c.toInt()), RoundedCornerShape(6.dp)).padding(vertical = 2.dp)) {
                                Text(cell.second.label, style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}
