package com.bamnat.app.imports

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * [READ-15] 1.27 순번 446: '스캐너로 반듯하게 찍기' — Google's ML Kit document scanner, run by Google Play services on the
 * phone ("The entire document scanner flow operates on-device", developers.google.com/ml-kit/vision/doc-scanner): it
 * finds the page's edges, straightens a photo taken at an angle and takes out shadows and fingers (FULL mode). The page
 * it gives back is read like any photo ([RosterScanner], [com.bamnat.app.pay.PayslipScanner]). No camera permission for
 * the app, nothing leaves the phone (D68). A phone whose Play services cannot run it (under 1.7 GB of memory, or no Play
 * services) says so, and the plain 사진 찍기 stays.
 */
object DocScanner {
    val options: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setPageLimit(1)
        .setGalleryImportAllowed(true)
        .build()

    const val UNAVAILABLE = "이 휴대폰에서는 스캐너를 쓸 수 없어요. ‘사진 찍기’나 ‘사진 고르기’로 해 주세요."

    fun activity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

/** [READ-15] The scanner's button: the straightened page to [onImage], or why it could not run to [onError]. */
@Composable
fun ScannerButton(onImage: (Uri) -> Unit, onError: (String) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val page = runCatching { GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.firstOrNull()?.imageUri }.getOrNull()
        if (page != null) onImage(page) else onError(DocScanner.UNAVAILABLE)
    }
    TextButton(
        onClick = {
            val activity = DocScanner.activity(context)
            if (activity == null) onError(DocScanner.UNAVAILABLE)
            else runCatching {
                // [NXT-01] 1.29 AU: ML Kit starts on the first scan, not at every app start.
                com.bamnat.app.MlKitStart.ensure(context)
                GmsDocumentScanning.getClient(DocScanner.options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { onError(DocScanner.UNAVAILABLE) }
            }.onFailure { onError(DocScanner.UNAVAILABLE) }
        },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(Icons.Filled.DocumentScanner, null, Modifier.size(18.dp))
        Text(" 스캐너로 반듯하게 찍기", maxLines = 1)
    }
}
