package com.bamnat.app.data

import android.content.Context
import com.bamnat.app.tier.ReferrerSource
import com.bamnat.core.setup.WebHandoff
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 2.0.0 [RVH-68] 검토 I50c-01·I50c-03 (RVR-31, 기존 계획 SPREAD-30): what bamnat.app put in its Play link ([WebHandoff]), taken up by
 * the first setup once. Read on the phone from Play's referrer, the same read as 친구 초대 ([com.bamnat.app.tier.ReferrerOnce]), and
 * kept on the phone only: nothing of it goes to a server, and nothing about the person is in it (the order's letters, its first day,
 * a 시급, where to land).
 *
 * The first setup asks it as one card on the question after the kind of work, only where it fits that kind ([fits]): a 교대 order
 * for 교대, a 시급 for 알바. One tap fills the order and today ([com.bamnat.core.setup.WebStartDraft]) or keeps the 시급 for the pay
 * setup; X turns it down for good. A link from the site's 명세서 pages opens '내 월급 맞나요?' after the setup, once ([slipDue]).
 */
class WebStart(private val context: Context, private val referrer: ReferrerSource) {
    private val prefs by lazy { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _handoff = MutableStateFlow<WebHandoff?>(null)
    /** What the link carried; null before [look], or for an install that did not come from the site. */
    val handoff: StateFlow<WebHandoff?> = _handoff.asStateFlow()

    private val _answer = MutableStateFlow<String?>(null)
    /** [USED], [DECLINED], or null while the card has not been answered. */
    val answer: StateFlow<String?> = _answer.asStateFlow()

    @Volatile private var looked = false

    /** Reads what the link carried, once a run (Play itself is asked once an install). */
    suspend fun look() {
        if (looked) return
        _answer.value = prefs.getString(K_ANSWER, null)
        _handoff.value = runCatching { WebHandoff.fromReferrer(referrer.read(context)) }.getOrNull()
        looked = true
    }

    /**
     * What of it the first setup asks for [style]: the order for 교대, the 시급 for 알바 (a 시급 typed into the 알바 calculator is not
     * a monthly wage's), nothing for other kinds or once answered.
     */
    fun fits(style: WorkStyle?): WebHandoff? {
        val h = _handoff.value ?: return null
        if (_answer.value != null) return null
        return when (style) {
            WorkStyle.SHIFT -> h.codes?.let { h.copy(hourly = null) }
            WorkStyle.HOURLY -> h.hourly?.let { WebHandoff(hourly = it, target = h.target, campaign = h.campaign) }
            else -> null
        }
    }

    fun used() = answer(USED)
    fun declined() = answer(DECLINED)

    private fun answer(a: String) {
        prefs.edit().putString(K_ANSWER, a).apply()
        _answer.value = a
    }

    /** The 시급 the pay setup starts from: taken on the card. */
    val wage: Long? get() = _handoff.value?.hourly?.takeIf { _answer.value == USED }

    /** The site's 명세서 맞춰 보기 sent this install: '내 월급 맞나요?' after the setup, once, unless its card was turned down. */
    fun slipDue(): Boolean =
        _handoff.value?.target == WebHandoff.SLIP && _answer.value != DECLINED && !prefs.getBoolean(K_SLIP, false)

    fun slipOpened() = prefs.edit().putBoolean(K_SLIP, true).apply()

    /** Screenshots and tests: as if the link carried [h]. */
    internal fun putForTest(h: WebHandoff?) {
        _handoff.value = h
        _answer.value = null
        looked = true
    }

    companion object {
        const val USED = "used"
        const val DECLINED = "declined"
        private const val PREFS = "web_start"
        private const val K_ANSWER = "answer"
        private const val K_SLIP = "slip_opened"
    }
}
