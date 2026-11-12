package com.bamnat.app.pay

import android.content.Context
import com.bamnat.app.appContainer
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PERF-04] 결정 D52: ML Kit's Korean text model is not inside the app any more. Google Play services
 * downloads it to the phone once (asked at install by the manifest's com.google.mlkit.vision.DEPENDENCIES,
 * and again here when it is still missing), and the photo is read on the phone as before: nothing is sent.
 * Until the model is there a read ends in [NotReady], whose message says why and what to do.
 */
object OcrModel {
    /**
     * 2.0.0 [RVL-32] (검토 SCAN-05·U07b-01) Why the model is not on the phone yet, each with what to do: one "잠시 뒤 다시" for all left
     * a phone with no network or no room waiting for nothing. [code] goes in the event log (`ocr_fail not_ready <code>`).
     */
    enum class Wait(val code: String, val text: String) {
        /** No network to fetch it over: said at once, without the two minutes of waiting. */
        NETWORK("network", "글자 읽기 준비물을 받으려면 인터넷이 필요해요. 와이파이에 연결한 뒤 다시 읽어 주세요."),
        /** Play services said the phone has no room for it (ModuleInstallStatusCodes.INSUFFICIENT_STORAGE). */
        STORAGE("storage", "휴대폰 저장 공간이 모자라 글자 읽기 준비물을 받지 못했어요. 150MB쯤 비운 뒤 다시 읽어 주세요."),
        /** The fetch was stopped on the phone's side (Play services or the user): asking again carries it on. */
        STOPPED("stopped", "글자 읽기 준비물 받기가 멈췄어요. 다시 읽으면 이어서 받아요."),
        /** Play services could not fetch it for another reason: usually an old Play services. */
        FAILED("failed", "글자 읽기 준비물을 받지 못했어요. Play 스토어에서 ‘Google Play 서비스’를 업데이트한 뒤 다시 읽어 주세요."),
        /** Still coming after [WAIT_MS]. */
        SLOW("slow", "글자 읽기 준비물을 아직 다 받지 못했어요. 사진을 보며 직접 넣거나, 잠시 뒤 같은 사진을 다시 읽어 주세요."),
    }

    class NotReady(val wait: Wait = Wait.SLOW) : Exception(wait.text)

    /**
     * 2.0.0 [RVL-31] [block] reads with one [Reader], closed after. [preparing] hears the model coming down (percent, null while its
     * size is not known) when it is not on the phone yet.
     */
    suspend fun <T> reading(context: Context, preparing: (Int?) -> Unit = {}, block: suspend (Reader) -> T): T {
        // [NXT-01] 1.29 AU: ML Kit starts here, on the first photo, not at every app start.
        com.bamnat.app.MlKitStart.ensure(context)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            return block(Reader(context, recognizer, preparing))
        } finally {
            recognizer.close()
        }
    }

    /** The words of [bitmap], read on the phone: one reading ([reading] for several). */
    suspend fun read(context: Context, bitmap: android.graphics.Bitmap, preparing: (Int?) -> Unit = {}): Text =
        reading(context, preparing) { it.read(bitmap) }

    /**
     * 2.0.0 [RVL-31] (검토 SCAN-24·I07ba-06) One reading of a photo in several ways: one recognizer for all of them (a band-by-band
     * reading made and closed one some forty times), and the model looked for once, before the first. Made by [reading].
     */
    class Reader internal constructor(private val context: Context, private val recognizer: TextRecognizer, private val preparing: (Int?) -> Unit) {
        private var ready = false

        /** The words of [bitmap], read on the phone. Stops at once when the reading is cancelled (검토 SCAN-03). */
        suspend fun read(bitmap: android.graphics.Bitmap): Text {
            currentCoroutineContext().ensureActive()
            if (!ready) {
                ensure(context, recognizer, preparing)
                ready = true
            }
            // 1.36 PC 사진 인식 원인(logcat: "MlKitContext has not been initialized" at InputImage.fromBitmap): the picture is made into
            // ML Kit's image here, after ML Kit has started, never by the caller before it (1.29 NXT-01 took the start provider out).
            val image = InputImage.fromBitmap(bitmap, 0)
            return try {
                await(recognizer.process(image))
            } catch (e: MlKitException) {
                if (e.errorCode != MlKitException.UNAVAILABLE) throw e
                // The model went missing between the check and the read: fetched and waited for once more.
                install(context, recognizer, preparing)
                await(recognizer.process(image))
            }
        }
    }

    /**
     * Returns when Play services has the model. When not, asks for it and **waits** for it ([install]), saying how far it got
     * through [preparing]; [NotReady] says why when it does not come. A phone without Play services goes on to try.
     * 2.0.0 [RVL-31] (검토 SCAN-03) A cancelled reading is not taken for "the model is there".
     */
    private suspend fun ensure(context: Context, recognizer: TextRecognizer, preparing: (Int?) -> Unit) {
        val client = ModuleInstall.getClient(context)
        val there = try {
            await(client.areModulesAvailable(recognizer)).areModulesAvailable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
        if (!there) install(context, recognizer, preparing)
    }

    /** [PAYIN-04] How long a read waits for the model to come down before it says "잠시 뒤 다시". */
    private const val WAIT_MS = 120_000L

    /**
     * [PAYIN-04] 1.37 B4 (PC E9: an emulator with internet only ever said "준비물을 받는 중, 1~2분 뒤 다시"): 1.36 asked Play services
     * for the model and did not wait, and a failed request (Play services too old, no network) was swallowed, so every photo said
     * the same thing and nothing told why. Now the request is urgent, its progress goes to [preparing] (percent, null while the
     * size is not known), the read waits up to [WAIT_MS] and goes on by itself when the model is there, and a request Play services
     * refuses throws its own error ([why] says PLAY_OLD·PLAY_MISSING·NETWORK).
     * 2.0.0 [RVL-32] (검토 SCAN-04·05) With no network it is said at once instead of after two minutes, and a fetch that failed says
     * why ([Wait]: no room, stopped, failed, still coming).
     */
    private suspend fun install(context: Context, recognizer: TextRecognizer, preparing: (Int?) -> Unit) {
        if (!online(context)) throw NotReady(Wait.NETWORK)
        val client = ModuleInstall.getClient(context)
        val done = kotlinx.coroutines.CompletableDeferred<Wait?>()
        val listener = com.google.android.gms.common.moduleinstall.InstallStatusListener { update ->
            update.progressInfo?.let { p ->
                preparing(if (p.totalBytesToDownload > 0) (p.bytesDownloaded * 100 / p.totalBytesToDownload).toInt().coerceIn(0, 100) else null)
            }
            when (update.installState) {
                ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> done.complete(null)
                ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> done.complete(failed(update.errorCode))
                ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> done.complete(Wait.STOPPED)
                // Paused waiting for a network that went away: said now, not after the wait.
                ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOAD_PAUSED -> if (!online(context)) done.complete(Wait.NETWORK)
                else -> Unit
            }
        }
        preparing(null)
        val response = await(client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).setListener(listener).build()))
        val wait = try {
            if (response.areModulesAlreadyInstalled()) null
            else kotlinx.coroutines.withTimeoutOrNull(WAIT_MS) { done.await() } ?: if (done.isCompleted) null else Wait.SLOW
        } finally {
            try { client.unregisterListener(listener) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        if (wait != null) throw NotReady(wait)
    }

    /** 2.0.0 [RVL-32] What a failed fetch's code says (ModuleInstallStatusCodes, CommonStatusCodes). */
    internal fun failed(code: Int): Wait = when (code) {
        com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes.INSUFFICIENT_STORAGE -> Wait.STORAGE
        com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> Wait.NETWORK
        else -> Wait.FAILED
    }

    /** Tests set it to be offline; null asks the phone. */
    @androidx.annotation.VisibleForTesting
    var forceOnline: Boolean? = null

    /** 2.0.0 [RVL-32] Whether the phone has a network that reaches the internet now (a phone that cannot tell is taken as online). */
    fun online(context: Context): Boolean {
        forceOnline?.let { return it }
        return try {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            true
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
        NETWORK("network", "글자 읽기 준비물을 받지 못했어요. 인터넷(와이파이)에 연결한 뒤 다시 해 주세요."),
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

    /**
     * 2.0.0 [RVL-32] (검토 U07b-01·I07ba-01) The one thing to do about a failed reading, as a button beside its words: the network,
     * room on the phone, Play services, or the same photo read again. Null when the words are all there is to it.
     */
    enum class Fix(val label: String) {
        WIFI("와이파이 켜기"),
        STORAGE("저장 공간 보기"),
        PLAY("Play 서비스 업데이트"),
        AGAIN("다시 읽기"),
    }

    fun fix(e: Throwable): Fix? = when (e) {
        is NotReady -> when (e.wait) {
            Wait.NETWORK -> Fix.WIFI
            Wait.STORAGE -> Fix.STORAGE
            Wait.FAILED -> Fix.PLAY
            Wait.STOPPED, Wait.SLOW -> Fix.AGAIN
        }
        else -> when (why(e)) {
            Why.NETWORK -> Fix.WIFI
            Why.PLAY_OLD, Why.MODEL -> Fix.PLAY
            else -> null
        }
    }

    /**
     * 2.0.0 [RVL-32] (검토 SCAN-05·I07ba-01) A failed reading in words, the same for 근무표·명세서: why and what to do, then the cause
     * chain's codes for a 문의 (no words of the photo). The event log keeps `ocr_fail <code> …` (`not_ready <wait>` for the model).
     */
    fun said(context: Context, e: Throwable): String {
        val log = { line: String -> runCatching { context.appContainer.telemetry.event("ocr_fail", line.take(120)) } }
        if (e is NotReady) {
            log("not_ready ${e.wait.code}")
            return e.wait.text
        }
        val why = why(e)
        val detail = detail(e)
        log("${why.code} $detail")
        return why.text + " (오류: $detail)"
    }

    private suspend fun <T> await(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resumeWithException(it) }
    }
}
