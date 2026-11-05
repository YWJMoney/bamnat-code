package com.bamnat.app.pay

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PERF-04] 결정 D52: ML Kit's Korean text model is not inside the app any more. Google Play services
 * downloads it to the phone once (asked at install by the manifest's com.google.mlkit.vision.DEPENDENCIES,
 * and again here when it is still missing), and the photo is read on the phone as before: nothing is sent.
 * Until the model is there a read ends in [NotReady], whose message says to try again in a moment.
 */
object OcrModel {
    class NotReady : Exception("글자 인식 준비물을 아직 다 받지 못했어요. 사진은 그대로 두었으니 보면서 직접 넣거나, 잠시 뒤 ‘같은 사진 다시 읽기’를 눌러 주세요.")

    /** The words of [image], read on the phone. */
    /**
     * 1.36 PC 사진 인식 원인(logcat: "MlKitContext has not been initialized" at InputImage.fromBitmap): the picture is made into
     * ML Kit's image here, after ML Kit has started, never by the caller before it (1.29 NXT-01 took the start provider out).
     */
    suspend fun read(context: Context, bitmap: android.graphics.Bitmap, preparing: (Int?) -> Unit = {}): Text {
        // [NXT-01] 1.29 AU: ML Kit starts here, on the first photo, not at every app start.
        com.bamnat.app.MlKitStart.ensure(context)
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            if (!available(context, recognizer, preparing)) throw NotReady()
            return try {
                await(recognizer.process(image))
            } catch (e: MlKitException) {
                if (e.errorCode == MlKitException.UNAVAILABLE) {
                    // The model went missing between the check and the read: fetched and waited for once more.
                    if (install(context, recognizer, preparing)) return await(recognizer.process(image))
                    throw NotReady()
                }
                throw e
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * True when Play services has the model. When not, asks for it and **waits** for it ([install]), saying how far it got through
     * [preparing]; false only when it is still not there after [WAIT_MS]. A phone without Play services goes on to try.
     */
    private suspend fun available(context: Context, recognizer: TextRecognizer, preparing: (Int?) -> Unit): Boolean {
        val client = ModuleInstall.getClient(context)
        val there = runCatching { await(client.areModulesAvailable(recognizer)).areModulesAvailable() }.getOrDefault(true)
        return there || install(context, recognizer, preparing)
    }

    /** [PAYIN-04] How long a read waits for the model to come down before it says "잠시 뒤 다시". */
    private const val WAIT_MS = 120_000L

    /**
     * [PAYIN-04] 1.37 B4 (PC E9: an emulator with internet only ever said "준비물을 받는 중, 1~2분 뒤 다시"): 1.36 asked Play services
     * for the model and did not wait, and a failed request (Play services too old, no network) was swallowed, so every photo said
     * the same thing and nothing told why. Now the request is urgent, its progress goes to [preparing] (percent, null while the
     * size is not known), the read waits up to [WAIT_MS] and goes on by itself when the model is there, and a request Play services
     * refuses throws its own error ([why] says PLAY_OLD·PLAY_MISSING·NETWORK). True when the model is there.
     */
    private suspend fun install(context: Context, recognizer: TextRecognizer, preparing: (Int?) -> Unit): Boolean {
        val client = ModuleInstall.getClient(context)
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val listener = com.google.android.gms.common.moduleinstall.InstallStatusListener { update ->
            update.progressInfo?.let { p ->
                preparing(if (p.totalBytesToDownload > 0) (p.bytesDownloaded * 100 / p.totalBytesToDownload).toInt().coerceIn(0, 100) else null)
            }
            when (update.installState) {
                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> done.complete(true)
                ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> done.complete(false)
                else -> Unit
            }
        }
        preparing(null)
        val response = await(client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).setListener(listener).build()))
        return try {
            if (response.areModulesAlreadyInstalled()) true
            else kotlinx.coroutines.withTimeoutOrNull(WAIT_MS) { done.await() } == true
        } finally {
            runCatching { client.unregisterListener(listener) }
        }
    }

    /**
     * [J133-07] 1.33 (보고서 심각 5): why a read failed, said by cause instead of one "사진을 읽지 못했어요" for all, and kept in
     * the phone's event log as a code (`ocr_fail <code> <exception>`) for 알람 점검's record and a 문의.
     */
    enum class Why(val code: String, val text: String) {
        PLAY_OLD("play_old", "Google Play 서비스가 오래돼서 글자를 읽지 못했어요. Play 스토어에서 ‘Google Play 서비스’를 업데이트한 뒤 다시 해 주세요."),
        PLAY_MISSING("play_missing", "이 휴대폰에는 글자 인식에 필요한 Google Play 서비스가 없어요. 글로 붙여 넣거나 직접 넣어 주세요."),
        TOO_BIG("too_big", "사진이 너무 커서 읽지 못했어요. 화면 캡처나 조금 작은 사진으로 해 주세요."),
        CANT_OPEN("cant_open", "사진 파일을 열 수 없었어요. 갤러리에서 다시 고르거나 새로 찍어 주세요."),
        NETWORK("network", "글자 인식 준비물을 받지 못했어요. 인터넷(와이파이)에 연결한 뒤 다시 해 주세요."),
        /** 1.36 PC 사진 인식 (깨끗한 명세서도 OTHER): ML Kit itself failed with a code other than UNAVAILABLE. */
        MODEL("model", "휴대폰의 글자 인식 기능이 오류를 냈어요. ‘Google Play 서비스’를 업데이트하거나 휴대폰을 다시 켠 뒤 해 보세요."),
        OTHER("other", "사진을 읽지 못했어요. 다른 사진으로 해 보거나 글로 붙여 넣어 주세요."),
    }

    fun why(e: Throwable): Why {
        var c: Throwable? = e
        val seen = HashSet<Throwable>()
        while (c != null && seen.add(c)) {
            when (c) {
                is OutOfMemoryError -> return Why.TOO_BIG
                is java.io.FileNotFoundException, is SecurityException -> return Why.CANT_OPEN
                // ImagePrep.load's own words: no picture in the file, or not enough memory for it.
                is IllegalArgumentException -> when {
                    c.message?.startsWith("사진을 열지") == true -> return Why.CANT_OPEN
                    c.message?.startsWith("메모리가 모자라") == true -> return Why.TOO_BIG
                }
                is MlKitException -> if (c.errorCode != MlKitException.UNAVAILABLE) return Why.MODEL
                is com.google.android.gms.common.api.ApiException -> when (c.statusCode) {
                    com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
                    com.google.android.gms.common.ConnectionResult.SERVICE_UPDATING -> return Why.PLAY_OLD
                    com.google.android.gms.common.ConnectionResult.SERVICE_MISSING,
                    com.google.android.gms.common.ConnectionResult.SERVICE_INVALID,
                    com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> return Why.PLAY_MISSING
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> return Why.NETWORK
                }
                is java.net.UnknownHostException, is java.net.SocketTimeoutException -> return Why.NETWORK
            }
            c = c.cause
        }
        return Why.OTHER
    }

    /**
     * 1.36 PC 사진 인식: what failed, for the event log and a 문의, with no words of the photo: each class in the cause chain, an ML
     * Kit or Play services code, e.g. "MlKitException#13 < IllegalStateException". The debug build also writes the stack to logcat.
     */
    fun detail(e: Throwable): String {
        if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.w("BamnatOcr", "read failed", e)
        val chain = generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(4).map { c ->
            c.javaClass.simpleName + when (c) {
                is MlKitException -> "#${c.errorCode}"
                is com.google.android.gms.common.api.ApiException -> "#${c.statusCode}"
                else -> ""
            }
        }
        return chain.joinToString(" < ")
    }

    private suspend fun <T> await(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resumeWithException(it) }
    }
}
