package com.bamnat.app.ui.pay

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bamnat.app.imports.DocScanner
import com.bamnat.app.imports.rememberScanner
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.core.imports.PhotoQuality
import com.bamnat.core.pay.PhotoTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/**
 * [PAYS-05] Two buttons that read the text of a photo on the phone: one picks a photo or screenshot, one takes a
 * picture. The text goes to [onText]; a photo taken here is deleted once read.
 * [READ-13][READ-14] 1.27: the photo is read several ways and put together ([PayslipScanner.scan]), "n/m번째 방법으로 읽는
 * 중" shows while it does, and a photo too dark, blurred or small says how to take a better one. [onScan], when given,
 * gets the whole reading (the put-together payslip, its lines to look at, the photo for "사진과 같은가요?") instead of
 * the text alone.
 * [PHOTO-20] 1.30 AV (R3 A-2·A-6 상, 사용자 2026-10-27 "사진마다 읽는 부분이 다르다"): the scanner is the first, big button
 * ('스캐너로 찍기': the page's edges found, a photo taken at an angle straightened, shadows taken out), which takes away the
 * keystone and the tilt that made one photo read otherwise than the next; 1.29 had it as a small text button under the two
 * others and few pressed it. '사진 찍기' stands in its place on a phone that cannot run the scanner. A photo picked from the
 * gallery that came out slanted or small is offered once more through the scanner, which can open the gallery itself.
 * Taken out of PayslipImport.kt (1.30, so the result screen and these buttons are changed apart).
 */
@Composable
internal fun ScanButtons(
    onText: (String) -> Unit = {}, modifier: Modifier = Modifier,
    onScan: ((PayslipScanner.Scan) -> Unit)? = null,
    /**
     * [PHOTO-01] 1.29: no words found or the photo failed to read: the screen to fill by hand ('직접 넣기' under the message).
     * [PAYIN-01] 1.37 B1 (PC E10 "고른 사진이 화면에서 사라져요"): it gets the photo that could not be read (screen-sized, in memory
     * only), so the lines are typed while looking at it; null when there is none to show.
     */
    onEmpty: ((android.graphics.Bitmap?) -> Unit)? = null,
    /** [READ-41] What the PDF button says: a 체결 캡처 is no 명세서 (PC 10차). */
    pdfLabel: String = "PDF로 받은 명세서 고르기",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var advice by remember { mutableStateOf<String?>(null) }
    var scannerReady by remember { mutableStateOf(DocScanner.available(context)) }
    // [PHOTO-20] A picked or plain photo that came out slanted, small or hard to read: worth the scanner.
    var straighten by remember { mutableStateOf(false) }
    // A 1.32 [FIX132-01] The photo just read, kept for '돌려서 다시 읽기' (a photo taken here is deleted once the screen goes).
    var last by remember { mutableStateOf<Triple<Uri, Boolean, Boolean>?>(null) }
    var lastTurn by remember { mutableStateOf(0) }
    // [PAYIN-01] The photo being read or that failed to read: kept for '같은 사진 다시 읽기' and to show while typing by hand.
    var current by remember { mutableStateOf<Triple<Uri, Boolean, Boolean>?>(null) }
    // [PAYIN-04] The reading model coming down: percent, or -1 while its size is not known; null when not waiting for it.
    var preparing by remember { mutableStateOf<Int?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 2.0.0 [RVL-32] (검토 U07b-01) The one fix the failure's cause asks for (와이파이·저장 공간·Play 서비스).
    var fix by remember { mutableStateOf<com.bamnat.app.pay.OcrModel.Fix?>(null) }
    DisposableEffect(Unit) { onDispose { if (last?.second == true || current?.second == true) PayslipScanner.clear(context) } }

    fun read(uri: Uri, taken: Boolean, scanned: Boolean, turn: Int = 0, pdf: Boolean = false) {
        reading = true
        error = null
        fix = null
        advice = null
        straighten = false
        step = null
        preparing = null
        // [READ-34] A PDF is read again from the same button, not turned or shown as a photo.
        current = Triple(uri, taken, scanned).takeIf { !pdf }
        job = scope.launch {
            try {
                val scan = if (pdf) PayslipScanner.scanPdf(context, uri) { n, m -> step = n to m }
                    else PayslipScanner.scan(context, uri, scanned = scanned, turn = turn,
                        preparing = { p -> preparing = p ?: -1 }) { n, m -> preparing = null; step = n to m }
                last = Triple(uri, taken, scanned).takeIf { !pdf }
                lastTurn = scan.turn
                val quality = scan.quality
                val retake = quality?.takeIf { it.retake }?.line
                straighten = !scanned && scannerReady && quality != null && (quality.retake || quality.issues.any { it in STRAIGHTEN })
                // W7: which step failed, said plainly: here no letters at all (the amounts are looked for after).
                if (scan.text.isBlank() && pdf) error = "PDF에서 글자를 찾지 못했어요. 암호가 걸렸거나 그림만 든 PDF면 화면을 캡처해 ‘사진 고르기’로 넣어 주세요."
                else if (scan.text.isBlank()) error = "사진에서 글자를 하나도 찾지 못했어요. 밝은 곳에서 명세서가 화면에 꽉 차게 다시 찍어 주세요." + (retake?.let { " $it" } ?: "")
                else {
                    advice = retake ?: quality?.issues?.firstOrNull { straighten && it in STRAIGHTEN }?.text
                    if (onScan != null) onScan(scan) else onText(scan.text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.bamnat.core.imports.Sheets.SheetException) {
                // [READ-34] A PDF too big, locked or broken: said as it is.
                error = e.message
            } catch (e: Exception) {
                // [J133-07] The cause in words and its code in the event log; 2.0.0 [RVL-32] (검토 SCAN-05) the model's own why, and its fix.
                error = com.bamnat.app.pay.OcrModel.said(context, e)
                fix = com.bamnat.app.pay.OcrModel.fix(e)
            } catch (e: OutOfMemoryError) {
                error = com.bamnat.app.pay.OcrModel.said(context, e)
            } finally {
                // [PAYIN-01] A photo taken here stays while it can still be read again or shown; it goes with the screen.
                if (taken && last?.first != uri && current?.first != uri) PayslipScanner.clear(context)
                reading = false
                preparing = null
            }
        }
    }

    /** 2.0.0 [RVL-32] (검토 SCAN-03·U07b-01) 멈추기: the reading stops at once, and the buttons are there again. */
    fun stop() {
        job?.cancel()
        reading = false
        preparing = null
        step = null
    }

    /** [PAYIN-01] Typing by hand from the photo that could not be read (or while the model comes down): the photo goes along. */
    fun byHand() {
        val photo = current
        job?.cancel()
        error = null
        reading = false
        preparing = null
        val go = onEmpty ?: return
        if (photo == null) { go(null); return }
        scope.launch {
            val shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    var p = com.bamnat.app.imports.ImagePrep.load(context, photo.first)
                    if (lastTurn != 0 && last?.first == photo.first) p = com.bamnat.app.imports.ImagePrep.turned(p, lastTurn.toFloat())
                    com.bamnat.app.imports.ImagePrep.forScreen(p).first
                }.getOrNull()
            }
            go(shown)
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) read(uri, taken = false, scanned = false) }
    // [READ-34] 1.38: a payslip the company gave as PDF (ERP·급여 프로그램·노무 사이트), read from its own letters.
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) read(uri, taken = false, scanned = false, pdf = true) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot
        if (ok && uri != null) read(uri, taken = true, scanned = false) else PayslipScanner.clear(context)
    }
    // [READ-15] Edges found, straightened and shadows taken out by Google's scanner on the phone.
    val scan = rememberScanner(onImage = { read(it, taken = false, scanned = true) }, onError = { error = it; scannerReady = false })

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScanButtonRow(
            scanner = scannerReady, enabled = !reading,
            onScanner = scan,
            onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onTake = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", PayslipScanner.scanFile(context))
                shot = uri
                try {
                    take.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    error = "카메라 앱을 찾지 못했어요. 사진을 찍어 둔 뒤 ‘사진 고르기’로 골라 주세요."
                }
            },
        )
        if (onScan != null) TextButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, enabled = !reading, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(pdfLabel)
        }
        // 2.0.0 [RVL-32] (검토 SCAN-04·U07b-01·I07ba-01) The same reading line as 한 달 근무 넣기: what is going on, how far, and a
        // way out. [PAYIN-04] Waiting for the model need not hold the user: the photo goes to the lines to fill while it comes.
        if (reading) {
            val handy = preparing != null && onEmpty != null
            com.bamnat.app.ui.imports.ReadingLine(step, preparing, action = if (handy) "사진 보며 직접 넣기" else "멈추기",
                onAction = { if (handy) byHand() else stop() })
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            fix?.takeIf { f -> f != com.bamnat.app.pay.OcrModel.Fix.AGAIN }?.let { f ->
                TextButton(onClick = { com.bamnat.app.ui.imports.openFix(context, f) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(f.label) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (onEmpty != null) Button(onClick = ::byHand, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(if (current != null) "사진 보며 직접 넣기" else "직접 넣기", maxLines = 1)
                }
                current?.let { (uri, taken, scanned) ->
                    OutlinedButton(onClick = { read(uri, taken, scanned) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("같은 사진 다시 읽기", maxLines = 1) }
                }
            }
        }
        advice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tone.Caution.color()) } // [RVP-13] UIX-15
        // A 1.32 [FIX132-01] The way up the phone chose was wrong: the same photo turned by the user, read again.
        last?.takeIf { !reading && error == null }?.let { (uri, taken, scanned) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("방향이 틀렸나요?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { read(uri, taken, scanned, PhotoTurn.add(lastTurn, 180)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("거꾸로 돌리기") }
                TextButton(onClick = { read(uri, taken, scanned, PhotoTurn.add(lastTurn, 90)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("옆으로 돌리기") }
            }
        }
        // [PHOTO-20] The same photo once more, straightened: the scanner opens the gallery too.
        if (straighten && !reading) TextButton(onClick = scan, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.DocumentScanner, null, Modifier.size(18.dp))
            Text(" 스캐너로 반듯하게 펴서 다시 읽기")
        }
    }
}

/** [PHOTO-20] What a photo's look says the scanner would put right: slanted, taken from one side, small letters. */
private val STRAIGHTEN = setOf(PhotoQuality.Issue.TILTED, PhotoQuality.Issue.KEYSTONE, PhotoQuality.Issue.SMALL)

/**
 * [PHOTO-20] 1.30 AV: the buttons as laid out. A phone that runs the scanner: '스캐너로 찍기' big and first (with a line on why),
 * '사진 고르기' under it. One that cannot: '사진 찍기' in its place, '사진 고르기' beside it.
 */
@Composable
internal fun ScanButtonRow(scanner: Boolean, enabled: Boolean, onScanner: () -> Unit, onPick: () -> Unit, onTake: () -> Unit) {
    if (scanner) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onScanner, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Label(Icons.Filled.DocumentScanner, "스캐너로 찍기")
        }
        Text("비스듬히 찍어도 반듯하게 펴서 가장 정확하게 읽어요. 찍어 둔 사진도 스캐너 안에서 고를 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Label(Icons.Filled.PhotoLibrary, "사진 고르기")
        }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onTake, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Label(Icons.Filled.PhotoCamera, "사진 찍기")
        }
        OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Label(Icons.Filled.PhotoLibrary, "사진 고르기")
        }
    }
}

@Composable
private fun Label(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, maxLines = 1)
}

/**
 * [PHOTO-26] 1.30 AV: '+ 한 장 더' for a payslip too long for one photo (지급 on one screen, 공제 on the next): the next photo is
 * read as any other ([ScanButtons]) and put together with [scan] ([PayslipScanner.combine]), the lines a page shows twice
 * once. [onScan] gets the two together.
 */
@Composable
internal fun MorePageButton(scan: PayslipScanner.Scan, onScan: (PayslipScanner.Scan) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!open) TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("+ 한 장 더 (명세서가 길어 잘렸을 때)")
    } else ScanButtons(onScan = { next -> open = false; onScan(PayslipScanner.combine(scan, next)) })
}
