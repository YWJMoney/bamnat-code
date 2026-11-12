package com.bamnat.core.setup

import com.bamnat.core.pay.particle
import com.bamnat.core.plaza.CycleOrders
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 2.0.0 [RVR-31] (검토 I50c-01·I50c-03, 기존 계획 SPREAD-30·31): what the public site bamnat.app put in its Play link, so the
 * first run carries on from what the person already did there (누르는 횟수 0). Only settings, never anything about the person:
 * the order's codes, its first day, whether it repeats, an hourly wage typed into a calculator, and where to land ('명세서
 * 맞추기'). It rides in the install referrer's `utm_content` (utm_source=bamnat_web or bamnat_share) and is read on the phone
 * only, once, as the invite code is ([com.bamnat.core.tier.InviteRules.fromReferrer]); nothing of it goes to a server.
 *
 * The format is the web's `Bamnat.handoff` (tools/site/static/calc/calc.js): `p-DNBO_s-20261110_o-1_w-10320_t-slip`, each part
 * optional, in any order. A bare order ("DNBO", the shift page's links from 2026-11-10 to 2.0.0) is read as `p-` alone.
 */
data class WebHandoff(
    /** The order in the site's letters: D 주간, E 오후, N 야간, O 휴무, X 당번, B 비번. */
    val codes: String? = null,
    /** The day the order's first letter is worked. */
    val first: LocalDate? = null,
    /** A month's roster written once (간호사 듀티), not a repeating order. */
    val once: Boolean = false,
    /** 원 an hour, as typed into a wage calculator. */
    val hourly: Long? = null,
    /** Where to go after the setup: [SLIP] for 명세서 맞추기. */
    val target: String? = null,
    /** The page's campaign (shift_cal, alba, salary …), for the words and for counting. */
    val campaign: String? = null,
) {
    val empty: Boolean get() = codes == null && hourly == null && target == null

    /**
     * The one question the first run asks: "웹에서 보던 주야비휴, 11월 10일부터 채울까요?" ([name] is the order's name when the
     * app knows it, else the order is said in letters), "웹에서 넣은 시급 10,320원으로 시작할까요?", or both in one line.
     */
    fun question(name: String? = null): String? {
        val order = codes?.let { c ->
            val said = name?.takeIf { it.isNotBlank() } ?: CycleOrders.spoken(c)
            if (first != null) "$said, ${first.monthValue}월 ${first.dayOfMonth}일부터" else particle(said, "으로", "로")
        }
        val wage = hourly?.let { "시급 ${"%,d".format(it)}원" }
        return when {
            order != null && wage != null -> "웹에서 보던 $order 채우고, ${wage}으로 시작할까요?"
            order != null -> "웹에서 보던 $order 채울까요?"
            wage != null -> "웹에서 넣은 ${wage}으로 시작할까요?"
            else -> null
        }
    }

    companion object {
        /** utm_source of the site's search pages and of a received calendar page. */
        val SOURCES = setOf("bamnat_web", "bamnat_share")
        const val SLIP = "slip"
        /** The letters an order may have, and how long (the site's 직접 적기 keeps 62). */
        private val ORDER = Regex("[DENOXB]{1,62}")
        private val DAY = DateTimeFormatter.BASIC_ISO_DATE
        /** The years a first day can be in; anything else is a typing slip on the site. */
        private val YEARS = 2000..2100
        /** A calculator's hourly wage is more than nothing and less than a million won. */
        private val WAGE = 1L..999_999L

        /** What the referrer carried, or null for a referrer from anywhere else (a store search, an invite) or carrying nothing. */
        fun fromReferrer(referrer: String?): WebHandoff? {
            if (referrer.isNullOrBlank()) return null
            val params = referrer.split('&').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to runCatching { URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrDefault("")
            }.toMap()
            if (params["utm_source"] !in SOURCES) return null
            return read(params["utm_content"], params["utm_campaign"]?.substringAfterLast('-'))
        }

        /** [content] alone (`p-DNBO_s-20261110…` or a bare order), or null when it carries nothing to use. */
        fun read(content: String?, campaign: String? = null): WebHandoff? {
            val text = content?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (ORDER.matches(text)) return WebHandoff(codes = text, campaign = campaign)
            var h = WebHandoff(campaign = campaign)
            for (part in text.split('_')) {
                val key = part.substringBefore('-', "")
                val value = part.substringAfter('-', "")
                h = when (key) {
                    "p" -> if (ORDER.matches(value)) h.copy(codes = value) else h
                    "s" -> runCatching { LocalDate.parse(value, DAY) }.getOrNull()?.takeIf { it.year in YEARS }?.let { h.copy(first = it) } ?: h
                    "o" -> h.copy(once = value == "1")
                    "w" -> value.toLongOrNull()?.takeIf { it in WAGE }?.let { h.copy(hourly = it) } ?: h
                    "t" -> if (value == SLIP) h.copy(target = SLIP) else h
                    else -> h
                }
            }
            // A first day or 'once' means nothing without an order.
            if (h.codes == null) h = h.copy(first = null, once = false)
            return h.takeUnless { it.empty }
        }

        /** The web's encoding, for tests and for the app's own '같은 조 동료에게 보내기' (SPREAD-31) links to the site. */
        fun content(codes: String? = null, first: LocalDate? = null, once: Boolean = false, hourly: Long? = null, target: String? = null): String =
            listOfNotNull(
                codes?.takeIf { ORDER.matches(it) }?.let { "p-$it" },
                first?.takeIf { codes != null }?.let { "s-" + it.format(DAY) },
                "o-1".takeIf { once && codes != null },
                hourly?.takeIf { it in WAGE }?.let { "w-$it" },
                target?.takeIf { it == SLIP }?.let { "t-$it" },
            ).joinToString("_")

        /** SPREAD-31: the site's calendar for the same order and first day, to send to coworkers (no name, no token). */
        fun shiftPage(codes: String, first: LocalDate, once: Boolean = false): String =
            "https://bamnat.app/shift/?p=$codes&s=$first" + if (once) "&o=1" else ""
    }
}
