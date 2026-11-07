package com.bamnat.app.company

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.BuildConfig
import com.bamnat.app.appContainer
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 1.36 [PUSH-01] 문의 답 바로 알림 (사용자 2026-11-01 "계획해서 개발 진행해줘", 계획/1.36-문의답-바로알림.md).
 *
 * Only a phone that has asked something, with 설정 › 알림 › 밤낮 소식 on, gives the server its FCM token. When an answer is
 * written the server sends one data message {kind: inquiry} with no words in it; [PushService] then runs the same check
 * as [InquiryReplyJob] (asks the server, shows "문의에 답이 왔어요"). Without the build's ids (or Play services) nothing
 * here runs and the job keeps checking by itself. 1.39 [PLZC-41]: a phone with 광장 답 바로 알림 on keeps its token there too.
 */
object PushPhone {
    private const val PREFS = "push_phone"
    private const val K_SENT = "sent"

    /** The build carries the Firebase app's ids. */
    val configured: Boolean get() = BuildConfig.FCM_APP_ID.isNotBlank() && BuildConfig.FCM_API_KEY.isNotBlank() &&
        BuildConfig.FCM_PROJECT_ID.isNotBlank() && BuildConfig.FCM_SENDER_ID.isNotBlank()

    private fun app(context: Context): FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FCM_APP_ID).setApiKey(BuildConfig.FCM_API_KEY)
            .setProjectId(BuildConfig.FCM_PROJECT_ID).setGcmSenderId(BuildConfig.FCM_SENDER_ID).build())
    }.getOrNull()

    private fun wanted(c: AppContainer): Boolean = configured && (
        (c.inquiryApi.enabled && c.inquiryStore.asked && c.settingsRepository.current.plazaNews) || PlazaPush.wanted(c))

    /** After a question went, the app opened, or 밤낮 소식 changed: the token goes to the server, or is taken back. */
    suspend fun sync(c: AppContainer, token: String? = null) {
        val prefs = c.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getString(K_SENT, null)
        if (!wanted(c)) {
            if (sent != null) {
                runCatching { c.inquiryApi.pushPhone(c.companyStore.device, null) }
                    .onSuccess { prefs.edit().remove(K_SENT).apply() }
                if (configured && app(c.appContext) != null) runCatching { FirebaseMessaging.getInstance().deleteToken() }
            }
            return
        }
        if (app(c.appContext) == null) return
        val now = token ?: runCatching {
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            suspendCancellableCoroutine<String?> { k ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> k.resume(if (t.isSuccessful) t.result else null) }
            }
        }.getOrNull() ?: return
        // PC 확인용 (디버그 빌드만): Firebase 콘솔 '테스트 메시지'에 넣을 토큰. adb logcat -s BamnatPush
        if (BuildConfig.DEBUG) android.util.Log.i("BamnatPush", "token $now")
        if (now == sent) return
        runCatching { c.inquiryApi.pushPhone(c.companyStore.device, now) }.onSuccess { prefs.edit().putString(K_SENT, now).apply() }
    }
}

/** 1.36 [PUSH-01] A new token, or "this phone has something new": the answer is fetched and told as the job tells it. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val c = applicationContext.appContainer
        c.appScope.launch { runCatching { PushPhone.sync(c, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (BuildConfig.DEBUG) android.util.Log.i("BamnatPush", "received ${message.data["kind"]}")
        // Only this project's server (or the operator's Firebase console test) can reach the token, so a message without a
        // kind is taken as "look now" too: the check itself asks our server and tells only what is really new.
        val kind = message.data["kind"]
        val c = applicationContext.appContainer
        // 1.39 [PLZC-41] 광장 답 바로 알림 / 같은 근무 새 질문.
        if (kind == PlazaPush.ANSWER || kind == PlazaPush.QUESTION) {
            c.appScope.launch { PlazaPush.handle(c, kind) }
            return
        }
        if (kind != null && kind != "inquiry") return
        c.appScope.launch { runCatching { InquiryReplyJob.check(c, fromPush = true) } }
    }
}
