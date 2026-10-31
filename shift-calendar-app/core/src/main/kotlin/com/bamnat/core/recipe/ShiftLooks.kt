package com.bamnat.core.recipe

/**
 * [STU-37] 1.36 근무에 따라 바뀌는 모양 (스튜디오 극상 조사 S8, 사용자 2026-10-31 "극상으로"): the app's colours change by themselves with
 * the day's work (which mode a moment calls for: [com.bamnat.core.schedule.ShiftLookMode]) — one look while a night shift is on (22~06시에 걸친 근무 중, as 야간 근무 중 어둡게 decides it) and one on a day set as a
 * day off. Each is a whole-app 화면 색 ([ScreenLooks]) and a 달력 색 ([CalendarTheme]); the rest of the look (칸 디자인, 배치, 그림) stays.
 * Idea from public descriptions of looks that follow a schedule (iOS 집중 모드 홈 화면, Widgetsmith 시간표); written from scratch for 밤낮,
 * which alone knows the user's 근무표.
 *
 * Written as lines "night\t<screens>\t<cal>" and "off\t<screens>\t<cal>" ([encode]); a line with both empty is left out.
 */
data class ShiftLooks(val night: Look? = null, val off: Look? = null) {
    data class Look(val screens: String = "", val cal: String = "") {
        val isEmpty: Boolean get() = ScreenLooks.decode(screens).isDefault && CalendarTheme.decode(cal).isDefault
    }

    enum class Mode(val label: String) { NORMAL("평소"), NIGHT("야간 근무 중"), OFF("쉬는 날") }

    val isDefault: Boolean get() = (night?.isEmpty ?: true) && (off?.isEmpty ?: true)

    fun of(mode: Mode): Look? = when (mode) { Mode.NIGHT -> night; Mode.OFF -> off; Mode.NORMAL -> null }?.takeIf { !it.isEmpty }

    fun with(mode: Mode, look: Look?): ShiftLooks = when (mode) {
        Mode.NIGHT -> copy(night = look?.takeIf { !it.isEmpty })
        Mode.OFF -> copy(off = look?.takeIf { !it.isEmpty })
        Mode.NORMAL -> this
    }

    /**
     * The screens and 달력 색 to draw with in [mode]: the mode's own over the usual ([screens], [cal]). A look's 화면 색 replaces the usual
     * colours of each screen it names (its pictures stay unless it has its own); its 달력 색 replaces the usual one where it says.
     */
    fun apply(mode: Mode, screens: String, cal: String): Pair<String, String> {
        val look = of(mode) ?: return screens to cal
        val usual = ScreenLooks.decode(screens)
        val over = ScreenLooks.decode(look.screens)
        val keys = (usual.looks.keys + over.looks.keys)
        // PC 확인 S6 (야간 모양을 입혀도 낮과 거의 같음): the look's 앱 전체 colours also reach screens that have their own, so the whole
        // app turns, not only the screens that follow 앱 전체.
        val overAll = over.looks[ScreenLooks.ALL]
        val merged = ScreenLooks(keys.associateWith { k ->
            val u = usual.looks[k] ?: ScreenLook()
            val o = over.looks[k] ?: overAll?.takeIf { k != ScreenLooks.ALL }?.let { a ->
                if (k == ScreenLooks.PLAZA) ScreenLook(bg = a.bg) else ScreenLook(bg = a.bg, card = a.card, ink = a.ink, accent = a.accent)
            } ?: return@associateWith u
            u.copy(bg = o.bg ?: u.bg, card = o.card ?: u.card, ink = o.ink ?: u.ink, accent = o.accent ?: u.accent,
                photo = o.photo ?: u.photo, dim = o.dim ?: u.dim)
        })
        val uc = CalendarTheme.decode(cal)
        val oc = CalendarTheme.decode(look.cal)
        val mc = uc.copy(today = oc.today ?: uc.today, sat = oc.sat ?: uc.sat, sun = oc.sun ?: uc.sun, holiday = oc.holiday ?: uc.holiday,
            cell = oc.cell ?: uc.cell)
        return merged.encode() to mc.encode()
    }

    fun encode(): String = listOfNotNull(
        night?.takeIf { !it.isEmpty }?.let { "night\t${it.screens}\t${it.cal}" },
        off?.takeIf { !it.isEmpty }?.let { "off\t${it.screens}\t${it.cal}" },
    ).joinToString("\n")

    companion object {
        fun decode(s: String?): ShiftLooks {
            var r = ShiftLooks()
            s.orEmpty().split('\n').forEach { line ->
                val p = line.split('\t')
                if (p.size != 3) return@forEach
                // Only what reads back as a look is kept; anything else is dropped (a bad line never breaks the app's colours).
                val look = Look(ScreenLooks.decode(p[1]).encode(), CalendarTheme.decode(p[2]).encode())
                r = when (p[0]) { "night" -> r.with(Mode.NIGHT, look); "off" -> r.with(Mode.OFF, look); else -> r }
            }
            return r
        }

    }
}
