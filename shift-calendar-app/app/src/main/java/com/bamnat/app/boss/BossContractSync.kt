package com.bamnat.app.boss

import com.bamnat.app.AppContainer
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today

/**
 * 1.38 [P3-X-06] 근로계약서 링크 서명: the contract filled from the staff row goes to the share server only while it waits for the
 * signature (14 days, 30 at most). Once signed the phone keeps the name and time, and takes the page down; the signed PDF is made
 * here from the items as they were sent.
 */
object BossContractSync {
    const val DAYS = 14

    suspend fun send(c: AppContainer, place: Workplace, s: StaffMember): StaffContract.Sent {
        val contract = StaffContract.of(place, s)
        val title = StaffContract.title(contract)
        // A link still waiting is taken down first: one contract out at a time.
        s.contract?.takeIf { !it.signed && it.linkId != null && it.token != null }?.let { old -> runCatching { c.shareApi.signDelete(old.linkId!!, old.token!!) } }
        val made = c.shareApi.signCreate(place.name.take(30), s.name.take(20), title, contract.items, DAYS)
        val sent = StaffContract.Sent(title, contract.items, Today.millis(), made.id, made.token, made.url, made.expiresAt.toEpochMilli())
        return c.phaseThree.updateContract(s.id) { cur -> cur?.replacedBy(sent) ?: sent } ?: sent
    }

    /** Reads the link; once signed, keeps the name and time and takes the page down. */
    suspend fun pull(c: AppContainer, staffId: String): StaffContract.Sent? {
        val now = c.phaseThree.allStaff().firstOrNull { it.id == staffId }?.contract ?: return null
        val id = now.linkId ?: return now
        val token = now.token ?: return now
        if (now.signed) return now
        val got = c.shareApi.signRead(id, token)
        val next = c.phaseThree.updateContract(staffId) { cur ->
            if (cur?.linkId != id) cur
            else cur.copy(seenAt = got.seenAt?.toEpochMilli() ?: cur.seenAt, signedName = got.signedName ?: cur.signedName, signedAt = got.signedAt?.toEpochMilli() ?: cur.signedAt)
        }
        if (next?.signed == true) {
            runCatching { c.shareApi.signDelete(id, token) }.onSuccess {
                c.phaseThree.updateContract(staffId) { cur -> if (cur?.linkId == id) cur.copy(linkId = null, token = null, url = null) else cur }
            }
        }
        return next
    }

    /** Pulls every contract still waiting in [staff]; offline is fine (the next open tries again). */
    suspend fun pullWaiting(c: AppContainer, staff: List<StaffMember>) {
        val now = Today.millis()
        staff.filter { it.contract?.waiting(now) == true }.forEach { runCatching { pull(c, it.id) } }
    }

    /** 거두기: the page goes, the contract on the phone with it (nothing was signed). */
    suspend fun withdraw(c: AppContainer, s: StaffMember) {
        val now = s.contract ?: return
        if (now.linkId != null && now.token != null) c.shareApi.signDelete(now.linkId!!, now.token!!)
        // The signed ones it would have replaced stay: the newest of them is the contract again.
        c.phaseThree.updateContract(s.id) { cur -> if (cur == null || cur.signed) cur else cur.past.lastOrNull()?.copy(past = cur.past.dropLast(1)) }
    }

    /** 종이 계약서로 받았어요: the items as they are now, marked signed on paper. */
    suspend fun paper(c: AppContainer, place: Workplace, s: StaffMember): StaffContract.Sent? {
        s.contract?.takeIf { !it.signed && it.linkId != null && it.token != null }?.let { old -> runCatching { c.shareApi.signDelete(old.linkId!!, old.token!!) } }
        val contract = StaffContract.of(place, s)
        val at = Today.millis()
        val sent = StaffContract.Sent(StaffContract.title(contract), contract.items, at, signedAt = at, paper = true)
        return c.phaseThree.updateContract(s.id) { cur -> cur?.replacedBy(sent) ?: sent }
    }

    /**
     * 2.0.0 [RVJ-17] 검토 BOSS-55 '직원에게 줬는지도 안 남아요': the day the signed contract was handed over (근로기준법 제17조 제2항),
     * kept on the contract and printed on its PDF; [given] false (the switch turned off) takes a mistaken press back.
     */
    suspend fun given(c: AppContainer, s: StaffMember, given: Boolean = true): StaffContract.Sent? =
        c.phaseThree.updateContract(s.id) { cur -> cur?.takeIf { it.signed }?.copy(givenAt = if (given) cur.givenAt ?: Today.millis() else null) ?: cur }

    fun message(place: Workplace, s: StaffMember, sent: StaffContract.Sent): String =
        "${s.name.ifBlank { "직원" }}님, ${place.name} ${sent.title}예요. 링크에서 내용을 읽고 이름을 써서 동의해 주세요. 서명하면 계약서 PDF를 보내 드려요." +
            "\n${sent.url.orEmpty()}"
}
