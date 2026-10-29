package com.bamnat.app.company

import android.content.Context
import com.bamnat.core.plaza.ShareAsk
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [J133-17] 1.33 (순번 697 (실사용 테스트 "부탁 반복": 회사 나누기를 거절해도 시트·급여 카드·달력 알림으로 세 번 더 물어요)): one rule for
 * every ask to share with coworkers (나누기·길잡이·광장에 올리기). A no to any of them quiets all of them for
 * [ShareAsk.waitDays] (14 days the first time), on top of each card's own 다음에·그만 볼게요.
 */
object ShareAskGate {
    private const val FILE = "share_ask_gate"
    private const val K_UNTIL = "until"
    private val until = MutableStateFlow<Long?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The time asks may come again (epoch ms), read once from this phone. */
    fun until(context: Context): StateFlow<Long?> {
        if (until.value == null) until.value = prefs(context).getLong(K_UNTIL, 0)
        return until.asStateFlow()
    }

    fun open(context: Context, now: Long = Today.millis()): Boolean = now >= (until(context).value ?: 0)

    /** A no (다음에, 그만 볼게요, 내 달력에만) to one ask. */
    fun declined(context: Context, now: Long = Today.millis()) {
        val next = ShareAsk.nextDue(1, now)
        until.value = next
        prefs(context).edit().putLong(K_UNTIL, next).apply()
    }
}
