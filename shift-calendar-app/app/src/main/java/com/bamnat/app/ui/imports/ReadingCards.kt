package com.bamnat.app.ui.imports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bamnat.app.imports.DocScanner
import com.bamnat.app.imports.rememberScanner
import com.bamnat.app.pay.OcrModel
import com.bamnat.app.ui.components.firstSentence
import com.bamnat.app.ui.components.AppSheet
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.theme.Tone
import java.io.File

/**
 * 2.0.0 [RVL-32] (검토 SCAN-04·U07b-01·I07ba-01) A reading while it runs, one shape for 한 달 근무 넣기 and 명세서: the common card's
 * line ([NudgeCard]) says what is going on (the reading model coming down with its percent, or which of the ways is being read),
 * a bar under it how far it got, and its one button gets out of the waiting ([action]: 멈추기, or typing it while looking).
 * The spinner in the middle of the page that could not be stopped is gone.
 */
@Composable
internal fun ReadingLine(step: Pair<Int, Int>?, preparing: Int?, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val (text, detail) = readingWords(step, preparing)
    // Read out as it changes (조건 14): "준비물 받는 중 · 42%", then "5번 중 3번째".
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NudgeCard(text, icon = if (preparing != null) Icons.Outlined.Downloading else Icons.Outlined.DocumentScanner, detail = detail,
            action = action, onAction = onAction)
        val done = readingDone(step, preparing)
        // A bar that runs on its own stands still with 움직임 끄기 (조건 15): the words still say what is going on.
        if (done != null) LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        else if (!com.bamnat.app.ui.theme.LocalMotionOff.current) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    }
}

/** 2.0.0 [RVL-32] (검토 U07b-01) "5번 중 3번째", or the model coming down "· 42%" (once only), with the line under it. */
internal fun readingWords(step: Pair<Int, Int>?, preparing: Int?): Pair<String, String> = when {
    preparing != null -> ("글자 읽기 준비물 받는 중" + if (preparing >= 0) " · $preparing%" else "") to "처음 한 번만 받아요. 다 받으면 바로 읽어요"
    step != null && step.second > 0 -> "휴대폰 안에서 읽는 중 · ${step.second}번 중 ${step.first}번째" to READ_HERE
    else -> "휴대폰 안에서 읽는 중" to READ_HERE
}

private const val READ_HERE = "사진은 휴대폰 밖으로 나가지 않아요"

/** How far the reading got, 0..1, or null while it cannot be told. */
internal fun readingDone(step: Pair<Int, Int>?, preparing: Int?): Float? = when {
    preparing != null -> preparing.takeIf { it >= 0 }?.let { it / 100f }
    step != null && step.second > 0 -> (step.first.toFloat() / step.second).coerceIn(0f, 1f)
    else -> null
}

/**
 * 2.0.0 [RVL-32] (검토 U07b-01) What [OcrModel.Fix]'s button opens: the phone's internet panel (와이파이), its storage page, or Play
 * services' page in the Play store. [OcrModel.Fix.AGAIN] is the screen's own (the same photo read again).
 */
internal fun openFix(context: Context, fix: OcrModel.Fix) {
    val tries: List<Intent> = when (fix) {
        OcrModel.Fix.WIFI -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else null,
            Intent(Settings.ACTION_WIFI_SETTINGS),
        )
        OcrModel.Fix.STORAGE -> listOf(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        OcrModel.Fix.PLAY -> listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_SERVICES")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_SERVICES")),
        )
        OcrModel.Fix.AGAIN -> emptyList()
    }
    for (intent in tries) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private const val PLAY_SERVICES = "com.google.android.gms"

/** 2.0.0 [RVL-34] Where a roster photo taken here waits to be read (the FileProvider's "scan" folder); gone with the screen. */
internal fun rosterShot(context: Context): File = File(context.cacheDir, "scan").apply { mkdirs() }.resolve("roster.jpg")

/** 2.0.0 [RVL-34] The ways a roster comes in on this phone, from [rememberRosterWays]. */
internal class RosterWays(
    /** Google's scanner runs here; when not, '사진 찍기' is the first way. */
    val scanner: Boolean,
    /** The first way: the scanner, or the camera. */
    val first: () -> Unit,
    val pick: () -> Unit,
    val others: () -> Unit,
)

/**
 * 2.0.0 [RVL-34] (검토 SCAN-20·U07b-08·U07b-14) The ways a roster comes in, by the names 명세서 uses ([com.bamnat.app.ui.pay.ScanButtonRow]):
 * the way to take it first — Google's scanner, or '사진 찍기' on a phone that cannot run it, so no button leads nowhere and the
 * scanner's words "‘사진 찍기’나 ‘사진 고르기’로" name buttons on the screen — and '사진 고르기' beside it, each one tap as before
 * (조건 8). The rest ('파일 고르기', '글로 붙여 넣기', '사진 찍기' when the scanner is first) are in the '다른 방법' sheet this puts up,
 * each with a line on what it takes. A photo taken with the camera goes to [onTaken] (it waits in [rosterShot]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberRosterWays(
    onPhoto: (Uri) -> Unit,
    onTaken: (Uri) -> Unit,
    onFile: (Uri) -> Unit,
    onPaste: () -> Unit,
    onError: (String) -> Unit,
): RosterWays {
    val context = LocalContext.current
    var scannerReady by remember { mutableStateOf(DocScanner.available(context)) }
    var others by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPhoto) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onFile) }
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot?.let(Uri::parse)
        if (ok && uri != null) onTaken(uri)
    }
    val scan = rememberScanner(onImage = onPhoto, onError = { onError(it); scannerReady = false })
    val take: () -> Unit = {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", rosterShot(context))
        shot = uri.toString()
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            onError("카메라 앱을 찾지 못했어요. 사진을 찍어 둔 뒤 ‘사진 고르기’로 골라 주세요.")
        }
    }
    if (others) AppSheet(onDismissRequest = { others = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("근무표 넣는 다른 방법", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            if (scannerReady) WayRow(Icons.Filled.PhotoCamera, CAMERA, "스캐너 없이 카메라로 바로 찍어요") { others = false; take() }
            WayRow(Icons.Filled.Description, FILE, "엑셀·CSV·PDF·한글(.hwpx)·워드(.docx) 표") {
                others = false
                files.launch(SheetFiles.ROSTER_TYPES)
            }
            WayRow(Icons.Filled.ContentPaste, PASTE, "단톡방·메신저·구글 렌즈에서 복사한 근무표 글") { others = false; onPaste() }
            Text("사진·파일·글은 휴대폰 밖으로 나가지 않아요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
    return RosterWays(
        scanner = scannerReady,
        first = if (scannerReady) scan else take,
        pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        others = { others = true },
    )
}

/** 2.0.0 [RVL-34] The first two ways and '다른 방법', wrapping under each other with big letters instead of scrolling sideways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RosterWaysRow(ways: RosterWays, enabled: Boolean) {
    // Snug buttons so the three stand on one line at the folded width (about 380dp) and wrap only with big letters.
    val snug = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = ways.first, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp), contentPadding = snug) {
            Way(if (ways.scanner) Icons.Filled.DocumentScanner else Icons.Filled.PhotoCamera, if (ways.scanner) SCANNER else CAMERA)
        }
        OutlinedButton(onClick = ways.pick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp), contentPadding = snug) {
            Way(Icons.Filled.PhotoLibrary, PICK)
        }
        TextButton(onClick = ways.others, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(OTHERS) }
    }
}

@Composable
private fun Way(icon: ImageVector, text: String) {
    Icon(icon, null, Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    // At 200% a name wraps inside its button instead of being cut (조건 12).
    Text(text)
}

@Composable
private fun WayRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/**
 * 2.0.0 [RVL-33] (검토 U07b-02·T07b-07·I07ba-01) A reading that gave nothing never ends the road: why, on the common card in the
 * caution colour, with the one fix its cause has (와이파이 켜기, 저장 공간 보기, Play 서비스 업데이트, 다시 읽기), and right under it
 * the three ways on: the photo tapped where the line is ('사진에서 내 줄 알려 주기', when there is a photo), a new photo
 * ('다시 찍기', the scanner or the camera) and the keypad ('직접 칠하기'). One outlined button, two text buttons (조건 2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadFailed(
    text: String,
    fix: OcrModel.Fix?,
    onFix: () -> Unit,
    onClose: () -> Unit,
    onTapPhoto: (() -> Unit)?,
    onRetake: () -> Unit,
    onByHand: () -> Unit,
    retakeLabel: String = "다시 찍기",
) {
    // 2.0.0 [RVL-33] (총괄 #484 사진 '글자 못 찾음' 200%: 카드 글이 네 줄이라 달력이 두 주만 보였어요) The card says why in its first
    // sentence; how to fix it waits behind ⓘ at the end of the ways and opens right under the card (조건 1·4).
    val head = firstSentence(text)
    val rest = text.removePrefix(head).trim()
    var why by rememberSaveable(text) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NudgeCard(head, tone = Tone.Caution, action = fix?.label, onAction = onFix, onClose = onClose)
        // Opened, the whole of it under the card, never cut (the card's own second line stops at two).
        if (why && rest.isNotEmpty()) Text(rest, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onTapPhoto != null) OutlinedButton(onClick = onTapPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(TAP_PHOTO)
            }
            TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp)) { Text(retakeLabel) }
            TextButton(onClick = onByHand, modifier = Modifier.heightIn(min = 48.dp)) { Text("직접 칠하기") }
            if (rest.isNotEmpty()) IconButton(onClick = { why = !why }) {
                Icon(Icons.Outlined.Info, contentDescription = if (why) "고칠 방법 접기" else "고칠 방법 보기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 2.0.0 [RVL-34] (검토 U07b-14) The ways' names, one each everywhere (명세서 the same). */
internal const val SCANNER = "스캐너로 찍기"
internal const val CAMERA = "사진 찍기"
internal const val PICK = "사진 고르기"
internal const val FILE = "파일 고르기"
internal const val PASTE = "글로 붙여 넣기"
internal const val OTHERS = "다른 방법"
/** 2.0.0 [RVL-33] (검토 U07b-02·U07b-14) 사진 위에서 누르기, by what it does. */
internal const val TAP_PHOTO = "사진에서 내 줄 알려 주기"
