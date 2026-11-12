package com.bamnat.app.ui.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 2.0.0 [RVK-43] (검토 MONEY-31) The words of a field whose value lives in a view model ('기록 찾기'). The field showed the
 * view model's copy, which comes back a frame later: a 한글 letter being put together ('ㄱ' → '가') was replaced by the
 * older copy and fell apart, or a fast letter went missing. The field now shows what was typed at once; the view model's
 * copy only moves it when it is not one of the field's own words coming back (모두 지우기, 가게를 눌러 찾기).
 */
class EchoText(start: String, private val max: Int) {
    var text by mutableStateOf(start)
        private set
    private val sent = ArrayDeque<String>()

    /** The field's own change; what to hand to the view model (cut to [max]). */
    fun typed(value: String): String {
        val words = value.take(max)
        text = words
        sent.addLast(words)
        while (sent.size > KEEP) sent.removeFirst()
        return words
    }

    /** The view model's copy came: an echo of a word typed here is let go, anything else is shown. */
    fun outside(value: String) {
        val at = sent.indexOf(value)
        if (at >= 0) {
            repeat(at + 1) { sent.removeFirst() }
            return
        }
        sent.clear()
        text = value
    }

    /**
     * Typing stopped a while ago, so every echo has come back: a word left in the queue (a copy the view model skipped) must not
     * later hide a real outside change that happens to say the same thing.
     */
    fun settle() = sent.clear()

    private companion object {
        const val KEEP = 16
    }
}
