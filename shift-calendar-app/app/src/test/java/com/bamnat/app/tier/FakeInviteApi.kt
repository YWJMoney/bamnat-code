package com.bamnat.app.tier

import com.bamnat.app.company.CompanyException
import com.bamnat.core.tier.InviteTerms
import java.time.Instant

/** [INVITE-02][INVITE-03][INVITE-08] The company server's invites for screenshots and tests: what was sent, and set answers. */
class FakeInviteApi(
    var viewAnswer: InviteView = InviteView("ABCD2345", "https://company.example/i/ABCD2345", InviteTerms.DEFAULT, 0, 0, 0, 0, false, null,
        Instant.parse("2026-10-25T00:00:00Z")),
    var rulesAnswer: InviteTerms = InviteTerms.DEFAULT,
    var useAnswer: InviteAnswer = InviteAnswer("waiting", daysLeft = 7),
    var seenAnswer: InviteAnswer = InviteAnswer("waiting"),
    /** The codes the fake knows; another code is refused as the server does. */
    var known: Set<String> = setOf("ABCD2345", "FRND2345"),
    override val enabled: Boolean = true,
) : InviteApi {
    val calls = mutableListOf<String>()
    val phones = mutableListOf<String>()
    val uses = mutableListOf<Pair<String, String>>()

    override suspend fun rules(): InviteTerms {
        calls += "rules"
        return rulesAnswer
    }

    override suspend fun view(device: String): InviteView {
        calls += "view"
        return viewAnswer
    }

    override suspend fun makeCode(device: String, phone: String): InviteView {
        calls += "code"
        phones += phone
        return viewAnswer
    }

    override suspend fun use(device: String, code: String, source: String, phone: String): InviteAnswer {
        calls += "use"
        phones += phone
        if (code !in known) throw CompanyException("code", "code")
        uses += code to source
        return useAnswer
    }

    override suspend fun seen(device: String): InviteAnswer {
        calls += "seen"
        return seenAnswer
    }
}
