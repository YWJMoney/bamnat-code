package com.bamnat.app.pay

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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
    class NotReady : Exception("글자 인식 준비물을 휴대폰에 받는 중이에요. 잠시 뒤(와이파이에서 1~2분) 다시 해 주세요.")

    /** The words of [image], read on the phone. */
    suspend fun read(context: Context, image: InputImage): Text {
        // [NXT-01] 1.29 AU: ML Kit starts here, on the first photo, not at every app start.
        com.bamnat.app.MlKitStart.ensure(context)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            if (!available(context, recognizer)) throw NotReady()
            return try {
                await(recognizer.process(image))
            } catch (e: MlKitException) {
                if (e.errorCode == MlKitException.UNAVAILABLE) {
                    install(context, recognizer)
                    throw NotReady()
                }
                throw e
            }
        } finally {
            recognizer.close()
        }
    }

    /** True when Play services has the model; when not, asks for it and says no. A phone without Play services goes on to try. */
    private suspend fun available(context: Context, recognizer: TextRecognizer): Boolean {
        val client = ModuleInstall.getClient(context)
        val there = runCatching { await(client.areModulesAvailable(recognizer)).areModulesAvailable() }.getOrDefault(true)
        if (!there) install(context, recognizer)
        return there
    }

    private fun install(context: Context, recognizer: TextRecognizer) {
        runCatching { ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()) }
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
        OTHER("other", "사진을 읽지 못했어요. 다른 사진으로 해 보거나 글로 붙여 넣어 주세요."),
    }

    fun why(e: Throwable): Why {
        var c: Throwable? = e
        val seen = HashSet<Throwable>()
        while (c != null && seen.add(c)) {
            when (c) {
                is OutOfMemoryError -> return Why.TOO_BIG
                is java.io.FileNotFoundException, is SecurityException -> return Why.CANT_OPEN
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

    private suspend fun <T> await(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resumeWithException(it) }
    }
}
