package com.bamnat.core.recipe

/**
 * 2.0.0 [RVM-22] RCP-05 (알람 묶음이 진동·점점 크게·울림 길이를 바꾸는데 넣기 전 요약에 없고, 받은 작품 하나로 알람이 약해져 못 깨는 사고):
 * what an 알람 묶음 would change that makes the phone's alarm weaker than it is now. Someone else's work (장터, 근무표 링크의 근무 설정)
 * goes in without these unless the person says so; what makes the alarm stronger or only adds alarms goes in as before.
 */
object AlarmStrength {
    /** The phone's alarm now: 울림 길이 (minutes), 진동, 점점 크게. */
    data class Now(val ringMinutes: Int, val vibrate: Boolean, val gradual: Boolean)

    /** One way it would be weaker: the piece's field and a few words for it ("진동 끔", "울림 5분 → 1분", "작게 시작"). */
    data class Weak(val key: String, val words: String)

    /** The ways [values] (an [PieceKind.ALARMS] piece) would make the alarm weaker than [now]; empty when none. */
    fun weaker(values: Map<String, Any?>, now: Now): List<Weak> = listOfNotNull(
        (values["vibrate"] as? Boolean)?.takeIf { !it && now.vibrate }?.let { Weak("vibrate", "진동 끔") },
        (values["ring"] as? Number)?.toInt()?.takeIf { it < now.ringMinutes }?.let { Weak("ring", "울림 ${now.ringMinutes}분 → ${it}분") },
        (values["gradual"] as? Boolean)?.takeIf { it && !now.gradual }?.let { Weak("gradual", "작게 시작") },
    )

    /** [values] without what would make the alarm weaker than [now] (those stay as the phone has them). */
    fun keepStrong(values: Map<String, Any?>, now: Now): Map<String, Any?> {
        val weak = weaker(values, now).map { it.key }.toSet()
        return if (weak.isEmpty()) values else values - weak
    }

    /** [r] with its 알람 묶음 kept from making the alarm weaker than [now]. */
    fun keepStrong(r: Recipe, now: Now): Recipe =
        if (r.pieces.none { it.kind == PieceKind.ALARMS }) r
        else r.copy(pieces = r.pieces.map { p -> if (p.kind == PieceKind.ALARMS) p.copy(values = keepStrong(p.values, now)) else p })

    /** The ways [r]'s 알람 묶음 would make the alarm weaker than [now]. */
    fun weaker(r: Recipe, now: Now): List<Weak> = r.pieces.firstOrNull { it.kind == PieceKind.ALARMS }?.let { weaker(it.values, now) }.orEmpty()
}
