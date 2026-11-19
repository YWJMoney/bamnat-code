package com.bamnat.core.guide

/**
 * 2.0.0 [RVP-22] 앱을 열 때 뜨는 창은 한 번에 하나 (검토 UIX-43·I13ba-01·U13b-02·U13b-08·UIX-62, 디자인 품질 조건 1·3·10).
 *
 * Before 2.0.0 eight windows each looked at their own condition only: on the day of an update a phone could have to close two to
 * four of them before it saw its calendar, and '새 버전을 받았어요' came up over a sheet being filled in. Every such window now asks
 * [pick] for its turn. The rules, from the review's table (13b '앱을 열 때 뜰 수 있는 것들의 순서'):
 *
 * - One at a time: a window on screen keeps its turn until it is answered or closed.
 * - One per visit (opening the app, or coming back after 30 minutes or a new day): once one has been on screen, the rest wait for the
 *   next visit, so the calendar is never under a chain of windows. A window the user asked for ([Want.asked]: 새 기능 보기 from a
 *   notice) comes even after another, but never over one.
 * - Not while something is being written or a sheet is open ([busy]): the windows wait until it is closed.
 * - By weight: the law and consent first, then what the update brought, then the rest.
 */
enum class OpenAsk {
    /** '새로 생긴 기능이 있어요': a consent item added since the phone was set up (ConsentPlan). */
    NEW_CONSENT,

    /** 체험 끝 7일 전·마지막 날 계속 쓸지 (전자상거래법 제13조 6항). */
    TRIAL_CONSENT,

    /** 새로 생긴 것 (업데이트 뒤 한 번, 서버 스위치로 열린 것). */
    WHATS_NEW,

    /** 입은 테마의 기간이 끝났어요. */
    THEME_EXPIRY,

    /** '새 버전을 받았어요' (Play 유연 업데이트를 다 받음). */
    UPDATE_READY,

    /** 입은 테마의 새 판. */
    THEME_UPDATE,

    /** 1.32 전에 깐 폰에 한 번: 글자 크기. */
    TEXT_SIZE,

    /** 정식 출시 전부터 쓴 사람에게 한 번: 고마워요. */
    LAUNCH_THANKS,

    /**
     * 2.0.0 [RVH-61] 검토 PC-131: Play 별점·리뷰 창. 가장 가벼워 맨 뒤: 다른 창이 이번 방문에 떴으면 다음 방문으로 (판 올림 뒤 첫 화면이
     * 리뷰 창이던 것).
     */
    REVIEW;

    /** A window that wants to show: [asked] when the user asked for it (a notice's 새 기능 보기). */
    data class Want(val ask: OpenAsk, val asked: Boolean = false)

    companion object {
        /**
         * The window to show now, or none.
         * [wanting]: every window whose own condition holds; [current]: the one on screen (kept while it still wants);
         * [shownThisVisit]: windows already shown since the app was opened (or came back); [busy]: a sheet, another window or
         * unsaved writing is open.
         */
        fun pick(wanting: Collection<Want>, current: OpenAsk?, shownThisVisit: Set<OpenAsk>, busy: Boolean): OpenAsk? {
            if (current != null && wanting.any { it.ask == current }) return current
            if (busy) return null
            val asked = wanting.filter { it.asked }.minByOrNull { it.ask.ordinal }
            if (asked != null) return asked.ask
            if (shownThisVisit.isNotEmpty()) return null
            return wanting.minByOrNull { it.ask.ordinal }?.ask
        }
    }
}
