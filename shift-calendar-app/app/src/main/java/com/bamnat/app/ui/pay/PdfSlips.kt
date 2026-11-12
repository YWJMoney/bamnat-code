package com.bamnat.app.ui.pay

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.pay.PayslipScanner
import java.time.YearMonth

/**
 * 2.0.0 [RVL-44] (검토 SCAN-28·I07bb-04·U07b-09) A PDF of several payslips (a few months, or several people's): 1.39 put every page
 * into one payslip with every line of them all. Each is read apart ([com.bamnat.core.pay.SlipReading.slips]) and picked here, one at
 * a time: '10월 명세서 · 실수령 3,429,060원'. One already looked at has a ✓, and the list waits for the next one when the payslip
 * screen opens again ([Held]), so a year of payslips in one file is matched month by month without picking the file again.
 */
internal object PdfSlips {
    /** How long the list waits for the next one: a payslip matched and saved, then the next. */
    private const val KEEP_MS = 30 * 60 * 1000L

    /**
     * The payslips read from one file and which were looked at. Kept in memory only (never saved); a file opened with a password
     * keeps no way to draw its pages once the screen that asked for it is gone (the password goes with it), so the next one
     * picked from here comes without its picture.
     */
    class Held internal constructor(val id: Long, val read: PayslipScanner.PdfRead.Slips, val seen: Set<Int>, internal val at: Long) {
        val left: Int get() = read.scans.size - seen.size
        fun looked(i: Int) = Held(id, read, seen + i, at)
    }

    private var held: Held? = null
    private var next = 0L

    /** [read] to choose from now; kept (without its password) for when the screen opens again. */
    @Synchronized
    fun put(read: PayslipScanner.PdfRead.Slips, locked: Boolean): Held {
        val now = SystemClock.elapsedRealtime()
        val h = Held(++next, read, emptySet(), now)
        held = Held(h.id, if (locked) read.withoutPages() else read, emptySet(), now)
        return h
    }

    /** The payslips of the file picked a little while ago with some not yet looked at, or null. */
    @Synchronized
    fun get(): Held? {
        val h = held ?: return null
        if (SystemClock.elapsedRealtime() - h.at > KEEP_MS || h.left <= 0) { held = null; return null }
        return h
    }

    @Synchronized
    fun looked(id: Long, i: Int) {
        held?.takeIf { it.id == id }?.let { held = it.looked(i) }
    }

    @Synchronized
    fun forget() { held = null }

    /**
     * What each payslip is called: its month when every one has its own ('10월 명세서', another year with it), else its place
     * ('2번째 명세서 · 10월'), three people's October payslips being three 10월s.
     */
    fun labels(scans: List<PayslipScanner.Scan>, thisYear: Int = com.bamnat.core.time.Today.month().year): List<String> {
        val months = scans.map { it.slip.read.month }
        fun said(m: YearMonth) = if (m.year == thisYear) "${m.monthValue}월" else "${m.year}년 ${m.monthValue}월"
        val apart = months.none { it == null } && months.distinct().size == months.size
        return months.mapIndexed { i, m ->
            if (apart) "${said(m!!)} 명세서" else "${i + 1}번째 명세서" + (m?.let { " · ${said(it)}" } ?: "")
        }
    }

    /** The amount that tells one payslip from another: 실수령액, else 지급 총액. */
    fun amount(scan: PayslipScanner.Scan): String? {
        val r = scan.slip.read
        return r.net?.let { "실수령 ${won(it)}" } ?: r.statedGross?.let { "지급 ${won(it)}" }
    }
}

/**
 * [RVL-44] The payslips of one file to pick from. [again]: the list kept from a file picked a little while ago (says so, and has
 * an X to let it go); otherwise the file just read. Each row is 48dp and more, with its name over its amount so neither is cut at
 * 200% letters or on a folded phone; one looked at has a ✓ that is said too.
 */
@Composable
internal fun PdfSlipsChooser(held: PdfSlips.Held, again: Boolean, enabled: Boolean, onPick: (Int) -> Unit, onForget: () -> Unit) {
    val scans = held.read.scans
    val labels = PdfSlips.labels(scans)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (again) "방금 고른 PDF에 아직 안 본 명세서가 ${held.left}장 있어요"
                else "이 PDF에 명세서가 ${scans.size}장 들어 있어요. 볼 것을 골라 주세요",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (again) IconButton(onClick = onForget) { Icon(Icons.Filled.Close, "이 PDF 명세서 목록 닫기") }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column {
                scans.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val seen = i in held.seen
                    val amount = PdfSlips.amount(s)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(enabled = enabled, role = Role.Button) { onPick(i) }
                            .semantics { contentDescription = listOfNotNull(labels[i], amount, if (seen) "봤어요" else null).joinToString(", ") }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(labels[i], style = MaterialTheme.typography.titleSmall)
                            amount?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (seen) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** The same payslips with no way to draw their pages (and no password held). */
private fun PayslipScanner.PdfRead.Slips.withoutPages(): PayslipScanner.PdfRead.Slips = PayslipScanner.PdfRead.Slips(scans, cut, null, firstPages)
