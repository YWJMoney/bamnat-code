package com.bamnat.app.ui.onboarding

import com.bamnat.app.AppContainer
import com.bamnat.app.data.ConsentChecks
import com.bamnat.app.data.RecommendedSettings
import com.bamnat.core.consent.RecommendedPlan
import com.bamnat.core.work.WorkStyle

/**
 * [EASE-05] 1.38 (오늘근무 분석 2026-11-05, 사용자 승인): a new phone no longer goes through '밤낮을 제대로 쓰는 설정' with its phone
 * questions before (or after) the first setup. What that page turned on for the kind of work is on quietly; every permission is
 * asked where it is first needed, after the screen has said why: notices by the alarm (설정 마지막 화면의 알람 줄, 달력의 알람 줄),
 * the phone's calendar on 캘린더에서 가져오기, the watch's sleep on 설정 › 알림과 잠, usage statistics on the setup's last screen
 * (its own yes, never ticked beforehand, D42). Everything stays where 설정 › 알림과 잠 · 개인정보 turns it off or on.
 */
object QuietStart {
    /**
     * Turns on [style]'s recommended notices and marks the consent pages as seen, so neither they nor '새로 생긴 기능이 있어요' come.
     * [keepRestored]: a backup that came with its own choices keeps them.
     */
    fun apply(container: AppContainer, style: WorkStyle?, keepRestored: Boolean = false) {
        val ctx = container.appContext
        val available = ConsentChecks.available(ctx, container.healthSleep, container.consentStore)
        val start = RecommendedPlan.forStyle(style).start
        container.settingsRepository.update { s ->
            (if (keepRestored && RecommendedSettings.on(s).isNotEmpty()) s else RecommendedSettings.apply(s, start)).let { if (keepRestored) it else newView(it) }
        }
        container.consentStore.markFirstStartDone(available)
        container.consentStore.markRecommendOffered()
    }

    /**
     * 1.39 [CALX-07] (사용자 2026-11-08 "달력만 보기가 기본값, 카드 보기는 선택값, 처음 달력 칸을 볼 때 고르게"): a phone set up fresh opens
     * on 달력만 보기 and is asked once over its first month ([com.bamnat.app.ui.calendar.CalendarViewAsk]). A backup keeps its own view.
     */
    fun newView(s: com.bamnat.app.data.AppSettings): com.bamnat.app.data.AppSettings = s.copy(calendarOnly = true, viewAsked = false)
}
