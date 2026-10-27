package com.bamnat.core

import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.RuleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * [PHOTO-07] 1.29 AQ: 규칙 문서 34개([RuleCorpus])를 그대로, 그리고 사진 잡음을 넣어 두 번씩 읽어 정답 급여와 견줘요.
 * - 깨끗한 문서는 34개 모두 정답 금액과 같고, 읽은 규칙의 종류가 정답과 같고, '직접 넣기' 칸 수가 맞아요.
 * - 정답 규칙은 모두 급여를 바꿔요(아무것도 안 읽어도 맞는 문서는 없어요). 휴게 무급처럼 법과 같은 규칙은 종류로만 봐요.
 * - 잡음 문서도 90% 이상 정답 금액이에요.
 */
class RulePhotoCorpusTest {
    private val calc = PayCalculator()
    private fun total(p: PayProfile) = RuleCorpus.months.sumOf { calc.calculate(p, it, RuleCorpus.resolver::resolve).total }
    private fun applied(changes: List<RuleChange>) = RuleText.apply(RuleCorpus.base, changes, RuleCorpus.from)
    private fun read(text: String) = RuleText.read(text, RuleCorpus.kinds, RuleCorpus.shiftTypes).map { it.change }
    private fun kinds(changes: List<RuleChange>) = changes.map { it::class.simpleName }.sortedBy { it }

    @Test fun rulesCorpus() {
        val baseTotal = total(RuleCorpus.base)
        var clean = 0
        var noisyHit = 0
        var noisyAll = 0
        val dump = StringBuilder()
        val fails = mutableListOf<String>()
        val truthFails = mutableListOf<String>()
        for (d in RuleCorpus.DOCS) {
            val gold = total(applied(d.truth))
            if (d.truth.any { it !is RuleChange.Breaks || it.mode != com.bamnat.core.pay.BreakPay.UNPAID } && gold == baseTotal) truthFails += "${d.id} 정답이 급여를 안 바꿈"
            d.hand?.let { if (it != gold - baseTotal) truthFails += "${d.id} 손셈 $it, 정답 ${gold - baseTotal}" }
            val got = read(d.text)
            val payOk = total(applied(got)) == gold
            val kindOk = kinds(got) == kinds(d.truth)
            val unread = RuleText.unread(d.text, RuleCorpus.kinds, RuleCorpus.shiftTypes)
            val unreadOk = unread.size == d.unread
            if (payOk && kindOk && unreadOk) clean++
            else fails += "${d.id} pay=$payOk kind=$kindOk unread=${unread} got=${got.map { it.summary }} want=${d.truth.map { it.summary }}"
            dump.append(d.id).append('\t').append(gold).append('\t').append(d.text.replace("\n", "\\n")).append('\n')
            for (seed in 0 until 2) {
                val text = RuleCorpus.noisy(d.text, 1 + seed, Random(d.id.hashCode() * 31 + seed))
                val ok = total(applied(read(text))) == gold
                noisyAll++
                if (ok) noisyHit++ else println("NOISY MISS ${d.id}#$seed: ${text.replace("\n", " / ")} -> ${read(text).map { it.summary }}")
                dump.append(d.id).append('#').append(seed).append('\t').append(gold).append('\t').append(text.replace("\n", "\\n")).append('\n')
            }
        }
        fails.forEach { println("CLEAN MISS $it") }
        truthFails.forEach { println("TRUTH $it") }
        println("PHOTO-07 규칙 문서 ${RuleCorpus.DOCS.size}개: 깨끗한 문서 정답 $clean/${RuleCorpus.DOCS.size}, 잡음 문서 정답 금액 $noisyHit/$noisyAll")
        System.getenv("BAMNAT_RULE_DUMP")?.let { File(it).writeText(dump.toString()) }
        assertEquals("정답 규칙은 모두 급여를 바꾸고 손셈과 같아요", emptyList<String>(), truthFails)
        assertEquals("깨끗한 문서는 모두 맞아요: $fails", RuleCorpus.DOCS.size, clean)
        assertTrue("잡음 문서 90% 이상: $noisyHit/$noisyAll", noisyHit * 10 >= noisyAll * 9)
    }
}
