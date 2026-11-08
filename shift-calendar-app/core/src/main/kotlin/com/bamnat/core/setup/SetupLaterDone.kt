package com.bamnat.core.setup

/**
 * 1.39 [SETUP-22] 7차 PC 리더 폰(3병동 3조 2교대): a first setup put off with '나중에' kept '근무 설정을 마저 해 주세요 · 6단계 중
 * 2단계까지' over the calendar after the leader had made a team and put in a 근무 주기 by other ways. The calendar it was for is
 * there by then, so the setup counts as done: the kind of work is chosen and the calendar has its own order, or the leader's team.
 */
object SetupLaterDone {
    fun done(later: Boolean, styleChosen: Boolean, hasPattern: Boolean, hasTeam: Boolean): Boolean =
        later && styleChosen && (hasPattern || hasTeam)
}
