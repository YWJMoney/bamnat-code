package com.bamnat.app.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bamnat.core.report.ReportDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [PROV-01][PROV-05] A report made on the phone and handed to the share sheet, where it can be saved to
 * Drive or Files, printed or sent. Nothing goes anywhere else; the copy sits in the app's cache and is
 * replaced by the next report.
 */
object ReportShare {
    const val PDF = "application/pdf"
    const val CSV = "text/csv"

    suspend fun pdf(context: Context, doc: ReportDoc, name: String) =
        share(context, name, PDF, doc.title) { file -> file.outputStream().use { ReportPdf.write(doc, it) } }

    suspend fun csv(context: Context, csv: String, name: String, subject: String) =
        share(context, name, CSV, subject) { file -> file.writeText(csv, Charsets.UTF_8) }

    const val ZIP = "application/zip"

    /** [PAYX-45] A bundle of files a person reads, zipped. */
    suspend fun zip(context: Context, bytes: ByteArray, name: String, subject: String) =
        share(context, name, ZIP, subject) { file -> file.writeBytes(bytes) }

    /** [doc] as PDF bytes, for a bundle. */
    fun pdfBytes(doc: ReportDoc): ByteArray = java.io.ByteArrayOutputStream().also { ReportPdf.write(doc, it) }.toByteArray()

    /**
     * 2.0.0 [RVP-69] (검토 REST-11·U14-08) Makes a file and opens 보내기, or says why it could not, the same way everywhere: some
     * buttons crashed the app on a full phone and some said nothing. 다시 하기 runs [make] again. True when the share window opened.
     */
    suspend fun orTell(make: suspend () -> Unit): Boolean = try {
        make(); true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        com.bamnat.app.ui.components.AppSnack.show(failWords(e), kind = com.bamnat.app.ui.components.AppSnack.Kind.FAIL, action = "다시 하기",
            onAction = { retry.launch { orTell(make) } })
        false
    }

    /** What a failed file says: no app to send it to, or the file itself. */
    internal fun failWords(e: Throwable): String =
        if (e is android.content.ActivityNotFoundException) "보낼 수 있는 앱을 찾지 못했어요" else "파일을 만들지 못했어요. 저장 공간을 확인해 주세요"

    /** Where 다시 하기 runs: the screen that asked may be gone by then. */
    private val retry = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    /** The file for [name] in the app's cache, written by [write]; older reports are removed. */
    suspend fun file(context: Context, name: String, write: (File) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share/report").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, name).also(write)
    }

    private suspend fun share(context: Context, name: String, mime: String, subject: String, write: (File) -> Unit) {
        val file = file(context, name, write)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
