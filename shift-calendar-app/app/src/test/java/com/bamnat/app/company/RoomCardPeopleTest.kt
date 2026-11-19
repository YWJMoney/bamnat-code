package com.bamnat.app.company

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.0.0 [RVN-24] 검토 PC-21: '고친 점: 데이를 뺀 사람 2명' showed for a version one person sent and another said 맞아요 to,
 * beside '쓰는 사람 1명'. A card's people are the phones that sent a version with that change.
 */
class RoomCardPeopleTest {
    private fun v(setup: String, persons: Int, senders: Int) =
        RoomVersion(setup = setup, adopted = false, dept = "품질팀", version = null, text = "{}", persons = persons, senders = senders, uses = 1, support = 0)

    @Test fun aCardCountsTheSendersNotThe맞아요() {
        val page = RoomPage("c1", "가나다화학", base = v("base", persons = 2, senders = 1), mine = false, first = true,
            others = listOf(v("other", persons = 5, senders = 3)), stable = false)
        assertEquals(listOf("other" to 3, "base" to 1), page.cardVersions.map { it.id to it.persons })
    }

    @Test fun myOwnBaseIsNotACardAndAnOldServerWithNoSendersStillSaysOne() {
        val page = RoomPage("c1", "가나다화학", base = v("base", persons = 2, senders = 1), mine = true, first = false,
            others = listOf(v("other", persons = 1, senders = 0)), stable = false)
        assertEquals(listOf("other" to 1), page.cardVersions.map { it.id to it.persons })
    }
}
