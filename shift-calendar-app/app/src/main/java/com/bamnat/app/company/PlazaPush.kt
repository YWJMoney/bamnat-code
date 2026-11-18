package com.bamnat.app.company

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.core.plaza.StylePlaza

/**
 * 1.39 [PLZC-41] 광장 답 바로 알림 (사용자 결정 12번, 2026-11-07 19:21; 처리방침 3.12 ⑥).
 *
 * Two switches in 광장's 내 태그 창, kept on this phone ("plaza_tag", backed up with the tag scope):
 * - 내 질문에 답이 오면 바로 알림 (처음엔 켜짐): another's answer to my question in a kind of work's 광장 rings at once.
 * - 나와 같은 근무에 새 질문이 오면 알림 (처음엔 꺼짐): a new question with my 근무형태 in my own 광장 (교대·일근).
 *
 * The server's message carries only its kind ({kind: plaza_answer | plaza_question}); this phone asks the server and words the
 * notice, which is the same one 소식 notice as [PlazaNewsJob] (one channel, held through sleep and work). Only while
 * plaza.people is on, 밤낮 소식 is on, and the plaza's rules were agreed.
 */
object PlazaPush {
    const val ANSWER = "plaza_answer"
    const val QUESTION = "plaza_question"

    private const val PREFS = "plaza_tag"
    private const val K_ANSWERS = "push_answers"
    private const val K_SAME = "push_same"
    private const val K_WATCHED = "push_watched"
    private const val ANSWER_LINE = "내 질문에 새 답"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun answers(context: Context): Boolean = runCatching { prefs(context).getBoolean(K_ANSWERS, true) }.getOrDefault(true)
    fun same(context: Context): Boolean = runCatching { prefs(context).getBoolean(K_SAME, false) }.getOrDefault(false)
    fun setAnswers(context: Context, on: Boolean) = prefs(context).edit().putBoolean(K_ANSWERS, on).apply()
    fun setSame(context: Context, on: Boolean) = prefs(context).edit().putBoolean(K_SAME, on).apply()

    /** Whether the plaza is open to this phone's news at all. */
    private fun open(c: AppContainer): Boolean {
        val s = c.settingsRepository.current
        return c.plazaTags.on && c.companyApi.enabled && s.plazaNews && s.plazaRulesAgreed > 0
    }

    /** Whether this phone wants a push token kept on the server for the 광장. */
    fun wanted(c: AppContainer): Boolean = open(c) && (answers(c.appContext) || same(c.appContext))

    /** The 광장 whose new questions [same] watches: this phone's own kind of work's, 교대·일근 only. */
    fun watchRoom(c: AppContainer): String? =
        StylePlaza.styleRoom(c.settingsRepository.current.workStyle)?.takeIf { it == StylePlaza.SHIFT || it == StylePlaza.DAY }

    /**
     * Tells the server what to watch: my 근무형태 in my own 광장 while [same] is on and the tag shows a 근무형태, nothing otherwise.
     * Asks the server only when that changed. Never throws.
     */
    suspend fun syncWatch(c: AppContainer) {
        runCatching {
            val room = watchRoom(c)
            val form = if (room != null && open(c) && same(c.appContext)) c.plazaTags.tagFor(room)?.form?.name else null
            val device = c.companyStore.device
            // The install's id is part of what was told, so a backup brought to a new phone tells the server again.
            val want = if (room != null && form != null) "$device|$room|$form" else ""
            val was = prefs(c.appContext).getString(K_WATCHED, "") ?: ""
            if (want == was) return
            val wasRoom = was.split('|').getOrNull(1)
            if (want.isEmpty()) {
                if (wasRoom != null && was.startsWith("$device|")) c.companyApi.plazaWatch(device, wasRoom, null)
            } else c.companyApi.plazaWatch(device, room!!, form)
            prefs(c.appContext).edit().putString(K_WATCHED, want).apply()
        }
    }

    /**
     * 2.0.0 [RVN-09] COMP-42 After 모두 지우기 (the server forgot what this phone watched and its token): 광장 바로 알림 goes off on
     * the phone too, so nothing is given to the server again by itself. True when it was on; 설정 › 알림 turns it back on.
     */
    fun forget(c: AppContainer): Boolean {
        val was = wanted(c)
        prefs(c.appContext).edit().apply { if (was) putBoolean(K_ANSWERS, false).putBoolean(K_SAME, false) }.remove(K_WATCHED).apply()
        return was
    }

    /** Whether a 근무형태 can be watched now (the tag shows one in my own 광장); the switch says why not otherwise. */
    suspend fun watchable(c: AppContainer): Boolean = watchRoom(c)?.let { runCatching { c.plazaTags.tagFor(it)?.form }.getOrNull() } != null

    /** The line a push becomes, or null when there is nothing (left) to tell. */
    internal fun answerLine(fresh: Int): String? = if (fresh <= 0) null else "$ANSWER_LINE ${fresh}개가 왔어요"
    internal const val QUESTION_LINE = "나와 같은 근무의 새 질문이 올라왔어요"

    /** A push came: the server is asked what is new and the 소식 notice tells it. Never throws. */
    suspend fun handle(c: AppContainer, kind: String) {
        runCatching {
            if (!open(c)) return
            when (kind) {
                ANSWER -> {
                    if (!answers(c.appContext)) return
                    val fresh = VillageReplies.fresh(c.appContext, c.companyApi.myPosts(c.companyStore.device)).values.sum()
                    VillageReplies.publish(fresh)
                    val line = answerLine(fresh) ?: return
                    PlazaNewsJob.tell(c, "광장 소식", listOf(line), NoticeLinks.Target.VILLAGE_POSTS, replacing = ANSWER_LINE)
                }
                QUESTION -> {
                    if (!same(c.appContext) || watchRoom(c) == null) return
                    PlazaNewsJob.tell(c, "광장 소식", listOf(QUESTION_LINE), NoticeLinks.Target.PLAZA)
                }
            }
        }
    }
}
