package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.core.pay.PayslipCheck
import com.bamnat.core.pay.SlipProof
import com.bamnat.core.pay.WayPick
import com.bamnat.core.time.Today

/**
 * 2.0.0 [RVN-24] 검토 I10ac-01 명세서 한 장으로 회사와 판을 저절로 맞추기: '맞아요'까지 한 번에. The payslip, read whole, came to the
 * version's month on this calendar ([WayPick.sure]: the total within the match's tolerance and every 수당·가산 alike), so it is a
 * payslip check like 명세서로 맞춰 보기: the month is kept on the pay as checked ([SlipProof.MATCH]), and '맞아요' goes with the
 * 명세서 확인 it now rests on. What leaves the phone is the vote alone (the month's yes, 결정 D68), never an amount; without a
 * connection it waits ([SmallSends]).
 */
object PickAgree {
    /** What became of it: sent, waiting for a connection, or not sent (the line to say). */
    sealed interface Sent {
        data object Done : Sent
        data object Waits : Sent
        data class Failed(val why: String) : Sent
    }

    suspend fun send(c: AppContainer, scope: CompanyScope, companyId: String, setup: String, slip: WayPick.Slip, appTotal: Long): Sent {
        // The month checked against the payslip, as 명세서로 맞춰 보기 keeps it (it joins the months matched before, PAYA-01).
        scope.pay.update("${slip.month.monthValue}월 명세서와 맞춰 봤어요") {
            it.copy(verified = PayslipCheck(slip.month, slip.gross, appTotal, Today.millis(), SlipProof.MATCH))
        }
        val verified = scope.slipProof()?.counts == true
        return try {
            c.companyApi.vote(scope.store.device, companyId, setup, true, verified, null)
            mark(scope, setup)
            c.telemetry.event("pay_pick", "agree")
            Sent.Done
        } catch (e: CompanyException) {
            if (SmallSends.waits(e)) {
                SmallSends.later(c, scope.store, SmallSends.vote(companyId, setup, true, verified, null))
                mark(scope, setup)
                Sent.Waits
            } else Sent.Failed(com.bamnat.app.ui.company.companyProblem(e))
        }
    }

    private fun mark(scope: CompanyScope, setup: String) {
        scope.payWays.voted(true, null)
        if (scope.store.applied.value?.setup == setup) scope.store.voted(true)
    }
}
