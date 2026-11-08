package com.bamnat.core.design

import java.time.LocalDateTime

/**
 * 1.39 [FLOW-14][DSG-07] 밤 색 (흐름·디자인 계획 3-6, 4장): when the app turns warm and dark so it does not glare in a dark
 * room, a ward or a control room at 3 a.m. Chosen in 설정 › 화면과 글자 › 밤 색 (and on the sleep screen).
 *
 * 1.39 (사용자 11-07, 기본값 다시 따져 봄): the default is [SCREENS], 1.38's own behaviour (the sleep and night screens dark at
 * night). Turning the whole app dark by itself surprised people at 01~05 or in a planned day sleep and hid the 스튜디오 색 they
 * chose or bought, so it is a choice ([SLEEP_TIME]) offered on the sleep screen, not something the app does unasked.
 */
enum class NightLook(val key: String, val label: String, val detail: String) {
    OFF("off", "끔", "밤 색을 쓰지 않아요. 수면 화면도 앱 색 그대로예요"),
    SCREENS("screens", "수면 화면만", "밤 10시~아침 6시와 자는 동안 수면 화면·밤 화면만 어둡게 해요. 다른 화면은 앱 색(스튜디오 색) 그대로예요"),
    SLEEP_TIME("sleep", "잘 시간엔 앱 전체", "수면 화면에 더해, 자는 중·근무에 맞춘 잘 시간·새벽 1~5시에는 앱 전체를 붉은 기 도는 어두운 색으로 바꿔요(그동안 스튜디오 색은 쉬어요)"),
    PHONE("phone", "휴대폰 따름", "휴대폰이 어두운 모드일 때 늘 밤 색으로 보여 줘요");

    companion object {
        val DEFAULT = SCREENS
        fun of(key: String?): NightLook = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** 1.39 [FLOW-14] Whether the night colours are on now, for the whole app and for the sleep and night screens. */
object NightRule {
    /** 새벽: a night shift's darkest hours, and the hours almost everyone opens the app from bed (01:00~04:59). */
    fun dawn(now: LocalDateTime): Boolean = now.hour in 1..4

    /** The sleep screen and the night screen go dark earlier, from 22:00 to 06:00 (they are opened in bed). */
    fun bedHours(now: LocalDateTime): Boolean = now.hour >= 22 || now.hour < 6

    /**
     * The whole app: [NightLook.SLEEP_TIME] while a sleep is open ([sleeping]), inside a sleep planned around the work
     * ([planned], from SleepSchedule), and at [dawn]; [NightLook.PHONE] whenever the phone is dark; never when [NightLook.OFF]
     * or [NightLook.SCREENS] (only the sleep and night screens, [screen]).
     */
    fun app(
        look: NightLook,
        now: LocalDateTime,
        sleeping: Boolean,
        planned: List<ClosedRange<LocalDateTime>>,
        systemDark: Boolean,
    ): Boolean = when (look) {
        NightLook.OFF, NightLook.SCREENS -> false
        NightLook.PHONE -> systemDark
        NightLook.SLEEP_TIME -> sleeping || dawn(now) || planned.any { now in it }
    }

    /** The sleep screen and the night screen: as [app], and from 22:00 to 06:00 as well (not when [NightLook.OFF]). */
    fun screen(look: NightLook, now: LocalDateTime, sleeping: Boolean, systemDark: Boolean): Boolean = when (look) {
        NightLook.OFF -> false
        NightLook.PHONE -> systemDark || sleeping || bedHours(now)
        NightLook.SCREENS, NightLook.SLEEP_TIME -> sleeping || bedHours(now)
    }
}
