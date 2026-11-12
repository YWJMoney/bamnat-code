package com.bamnat.app.ui.money

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.bamnat.core.time.Today

/**
 * [FLD-08] Receipt photos kept in the app's own folder (files/receipts), picked with the system
 * photo picker so no permission is asked. Each is shrunk to a readable JPEG; the ledger line keeps
 * only its file name ([com.bamnat.core.money.MoneyEntry.receipt]).
 */
object ReceiptFiles {
    private const val MAX_SIDE = 1600

    fun dir(context: Context) = File(context.filesDir, "receipts")

    fun file(context: Context, name: String): File? =
        name.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains("..") }?.let { File(dir(context), it) }

    /** Copies the picked photo in as a new file; its name, or null when it could not be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            // 2.0.0 [RVK-43] (검토 MONEY-06) A photo the camera kept lying down with an EXIF turn is saved upright, as 메모 사진
            // and 근무표 사진 are (the JPEG written here has no EXIF left to say so).
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: android.media.ExifInterface.ORIENTATION_NORMAL
            val upright = com.bamnat.app.imports.ImagePrep.upright(orientation)
                ?.let { m -> Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() } } ?: bmp
            val name = UUID.randomUUID().toString() + ".jpg"
            val out = File(dir(context).apply { mkdirs() }, name)
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            upright.recycle()
            name
        }.getOrNull()
    }

    fun delete(context: Context, name: String) {
        file(context, name)?.delete()
    }

    /** 2.0.0 [RVK-11] A photo picked but not saved, changed or taken off stays this long, for 되돌리기 and a sheet still open. */
    private const val SWEEP_AFTER_MILLIS = 2 * 24 * 60 * 60 * 1000L

    /**
     * 2.0.0 [RVK-11] (검토 MONEY-05·I06aa-08) Deletes the receipt photos that none of [used] names — every calendar's lines and
     * the lines kept in 최근 지운 기록 — once they are two days old, so a receipt the user took off or deleted (카드 번호 일부·가게·
     * 시간) does not stay on the phone and in its backups. How many went.
     */
    fun sweep(context: Context, used: Set<String>, now: Long = com.bamnat.core.time.Today.millis()): Int = sweep(dir(context), used, now)

    internal fun sweep(dir: File, used: Set<String>, now: Long): Int {
        var n = 0
        for (f in dir.listFiles().orEmpty()) {
            if (!f.isFile || f.name in used) continue
            if (now - f.lastModified() < SWEEP_AFTER_MILLIS) continue
            if (f.delete()) n++
        }
        return n
    }

    /** [RVK-11] The receipt names the lines of one database hold. */
    fun usedBy(db: androidx.sqlite.db.SupportSQLiteDatabase): Set<String> =
        db.query("SELECT receipt FROM money_entries WHERE receipt != ''").use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }

    /** A small picture of the receipt for the editor. */
    suspend fun thumbnail(context: Context, name: String, side: Int = 240): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = file(context, name)?.takeIf { it.exists() } ?: return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > side * 2) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }

    /** What may prove a cost; 없음 says there is none. */
    val PROOFS = listOf("세금계산서", "카드 영수증", "현금영수증", "간이영수증", "없음")
}

/**
 * [FLD-08] 증빙 and 영수증 사진 in the expense editor of a field or shop calendar: what proves the
 * cost (for the 5월 신고 and 부가세), and a photo of the receipt with a thumbnail and 지우기.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProofSection(proof: String, onProof: (String) -> Unit, receipt: String, onReceipt: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = ReceiptFiles.import(context, uri)
            if (name != null) onReceipt(name)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("증빙", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReceiptFiles.PROOFS.forEach { p ->
                FilterChip(selected = proof == p, onClick = { onProof(if (proof == p) "" else p) }, label = { Text(p) })
            }
        }
        val thumb by produceState<ImageBitmap?>(null, receipt) { value = if (receipt.isBlank()) null else ReceiptFiles.thumbnail(context, receipt) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thumb?.let {
                Image(it, contentDescription = "영수증 사진", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small))
            }
            AssistChip(
                onClick = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                label = { Text(if (receipt.isBlank()) "영수증 사진" else "사진 바꾸기") },
                leadingIcon = { Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(18.dp)) },
            )
            if (receipt.isNotBlank()) TextButton(onClick = { onReceipt("") }) { Text("지우기") }
        }
        if (proof.isNotBlank() && proof != "없음" && proof != "간이영수증") {
            Text("부가세·종합소득세 경비로 챙길 수 있는 증빙이에요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [LEDG-25] 영수증으로 채우기: a receipt photo read on the phone (ML Kit, the same model as payslips) into a
 * spending line to check. The photo stays as the line's receipt; nothing is saved until the user saves.
 */
@Composable
fun ReceiptScanChip(onRead: (com.bamnat.core.money.ReceiptReader.Draft, String) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // [PHOTO-09] 1.29: once a photo was read, '사진이 달라요' sits by the chip.
    var readOnce by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            reading = true
            try {
                val name = ReceiptFiles.import(context, uri)
                val file = name?.let { ReceiptFiles.file(context, it) }
                if (name == null || file == null) { onMessage("사진을 읽지 못했어요"); return@launch }
                val result = runCatching { com.bamnat.app.pay.PayslipScanner.read(context, Uri.fromFile(file)) }
                val text = result.getOrNull().orEmpty()
                val notReady = result.exceptionOrNull() as? com.bamnat.app.pay.OcrModel.NotReady
                val draft = com.bamnat.core.money.ReceiptReader.read(text, Today.date())
                onRead(draft, name)
                readOnce = text.isNotBlank()
                onMessage(when {
                    // [PERF-04] The model is still on its way: the photo is attached, the reading can come later.
                    notReady != null -> "사진만 붙였어요. ${notReady.message}"
                    draft.isEmpty -> "글자를 찾지 못했어요. 사진만 붙였어요"
                    draft.sure -> "영수증에서 읽었어요. 맞는지 보고 저장해 주세요"
                    else -> "합계를 못 찾아 가장 큰 금액을 넣었어요. 꼭 확인해 주세요"
                })
            } finally {
                reading = false
            }
        }
    }
    AssistChip(
        onClick = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        enabled = !reading,
        label = { Text(if (reading) "읽는 중…" else "영수증 사진으로 채우기") },
        leadingIcon = { Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(18.dp)) },
    )
    if (readOnce) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.RECEIPT)
}
