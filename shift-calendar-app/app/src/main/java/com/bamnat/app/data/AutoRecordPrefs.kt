package com.bamnat.app.data

import android.content.Context
import android.content.SharedPreferences
import com.bamnat.core.money.CaptureFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.bamnat.core.time.Today

/**
 * [LEDG-22] 결제 알림으로 적기 as the user tuned it: apps left out, words that drop a text, the
 * smallest amount written, and the apps that sent a payment notification lately (to choose from).
 * [LEDG-24] Also the places the user said are not a monthly payment. All of it stays on the phone.
 */
class AutoRecordPrefs(context: Context) {
    data class State(
        val filter: CaptureFilter = CaptureFilter(),
        /** Package → the app's name, apps that sent a notification read as a payment. */
        val seenApps: Map<String, String> = emptyMap(),
        val notRepeating: Set<String> = emptySet(),
        /** [CALS-04] The calendar payment notifications go to when no card, account or app says otherwise. */
        val calendar: String = PersonalCalendar.MAIN,
        /** [CALS-04] Package → calendar: an app's notifications always go to that calendar. */
        val appCalendars: Map<String, String> = emptyMap(),
        /** [COMN-04] 확인하고 적기: payment lines wait in 확인 대기함 and count only once confirmed; off is 바로 적기. */
        val confirmFirst: Boolean = false,
        /** [COMN-04] The day 확인하고 적기 was turned on; older lines were already counted and stay counted. */
        val confirmSince: java.time.LocalDate? = null,
        /** E 1.32 [LEDX-09] Place key → the day the user said they still use that subscription. */
        val lookedAt: Map<String, java.time.LocalDate> = emptyMap(),
        /** 1.32 [LEDX-15] The user's own names as banks print them: a deposit from one is a move between their own accounts. */
        val myNames: List<String> = emptyList(),
        /** 1.32 [LEDX-18] The card's 해외 이용 수수료 (0.0125 = 1.25%), set by the user. */
        val abroadFee: Double = com.bamnat.core.money.LedgerSmarts.ABROAD_FEE,
        /** 1.32 [LEDX-18] Currency → won per unit, typed or learned from a line fixed to the card's bill. */
        val rates: Map<String, Double> = emptyMap(),
        /** 1.32 [LEDX-19] Card last-four digits already asked about ("체크카드 맞나요?"), so each is asked once. */
        val askedCards: Set<String> = emptySet(),
    ) {
        /** [COMN-04] From when unconfirmed lines wait, or null for 바로 적기. */
        val holdFrom: java.time.LocalDate? get() = if (confirmFirst) confirmSince ?: java.time.LocalDate.MIN else null
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auto_record", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()
    val current: State get() = _state.value

    /** [BAK-01] After a restore wrote the file anew. */
    fun reload() { _state.value = read() }

    fun setFilter(filter: CaptureFilter) = edit {
        putStringSet(K_BLOCKED, filter.blockedApps)
        putString(K_WORDS, filter.words.map { CaptureFilter.clean(it) }.filter { it.isNotEmpty() }.distinct().take(CaptureFilter.MAX_WORDS).joinToString("\n"))
        putLong(K_MIN, filter.minAmount.coerceAtLeast(0))
    }

    /** Remembered when a payment notification comes; only new apps are written. */
    fun sawApp(pkg: String, label: String) {
        if (current.seenApps[pkg] == label) return
        val next = (current.seenApps + (pkg to label.replace('\t', ' ').replace('\n', ' '))).entries.toList().takeLast(MAX_APPS)
        edit { putStringSet(K_SEEN, next.map { "${it.key}\t${it.value}" }.toSet()) }
    }

    fun notRepeating(key: String) = edit { putStringSet(K_NOT_REPEATING, current.notRepeating + key) }

    /** E 1.32 [LEDX-09] "아직 써요": not asked again for 90 days. */
    fun lookedAt(key: String, on: java.time.LocalDate = Today.date()) = edit {
        val next = (current.lookedAt + (key to on)).entries.toList().takeLast(MAX_APPS)
        putStringSet(K_LOOKED, next.map { "${it.key}\t${it.value.toEpochDay()}" }.toSet())
    }

    /** 1.32 [LEDX-15] At most [MAX_NAMES] names of 2~10 letters, spaces taken out. */
    fun setMyNames(names: List<String>) = edit {
        putString(K_MY_NAMES, cleanNames(names).joinToString("\n"))
    }

    /** 1.32 [LEDX-18] 해외 이용 수수료 in percent (1.25), held to 0~5%. */
    fun setAbroadFeePercent(percent: Double) = edit {
        putFloat(K_ABROAD_FEE, (percent / 100).coerceIn(0.0, com.bamnat.core.money.LedgerSmarts.MAX_ABROAD_FEE).toFloat())
    }

    /** 1.32 [LEDX-18] The user's rate for [currency]; null forgets it (the rough table again). */
    fun setRate(currency: String, rate: Double?) = edit {
        val cur = currency.trim().uppercase().take(3)
        val next = if (rate == null || rate <= 0 || !rate.isFinite()) current.rates - cur else current.rates + (cur to rate)
        putStringSet(K_RATES, next.entries.take(MAX_RATES).map { "${it.key}\t${it.value}" }.toSet())
    }

    /** 1.32 [LEDX-19] The card [last4] was asked about once. */
    fun askedCard(last4: String) = edit { putStringSet(K_ASKED_CARDS, (current.askedCards + last4).toList().takeLast(MAX_APPS).toSet()) }

    /** [CALS-04] Where payment notifications are written down. */
    fun setCalendar(id: String) = edit { putString(K_CALENDAR, id) }

    /** [CALS-04] Sends one app's notifications to calendar [id]; null lets it follow [State.calendar]. */
    fun setAppCalendar(pkg: String, id: String?) = edit {
        val next = if (id == null) current.appCalendars - pkg else current.appCalendars + (pkg to id)
        putStringSet(K_APP_CALENDARS, next.map { "${it.key}\t${it.value}" }.toSet())
    }

    /** [COMN-04] 확인하고 적기 on, or 바로 적기. */
    fun setConfirmFirst(on: Boolean) = edit {
        putBoolean(K_CONFIRM_FIRST, on)
        if (on && !current.confirmFirst) putLong(K_CONFIRM_SINCE, Today.date().toEpochDay())
    }

    /** [CALS-04] A calendar that goes away hands its notifications back to 내 달력. */
    fun forgetCalendar(id: String) {
        if (current.calendar == id) setCalendar(PersonalCalendar.MAIN)
        current.appCalendars.filterValues { it == id }.keys.forEach { setAppCalendar(it, null) }
    }

    private fun cleanNames(names: List<String>): List<String> =
        names.map { it.replace(Regex("\\s+"), "") }.filter { it.length in 2..10 }.distinct().take(MAX_NAMES)

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        synchronized(this) {
            prefs.edit().apply(block).apply()
            _state.value = read()
        }
    }

    private fun read(): State = State(
        filter = CaptureFilter(
            blockedApps = prefs.getStringSet(K_BLOCKED, null).orEmpty().toSet(),
            words = prefs.getString(K_WORDS, null).orEmpty().split("\n").filter { it.isNotBlank() },
            minAmount = prefs.getLong(K_MIN, 0),
        ),
        seenApps = prefs.getStringSet(K_SEEN, null).orEmpty().mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap(),
        notRepeating = prefs.getStringSet(K_NOT_REPEATING, null).orEmpty().toSet(),
        calendar = prefs.getString(K_CALENDAR, null) ?: PersonalCalendar.MAIN,
        appCalendars = prefs.getStringSet(K_APP_CALENDARS, null).orEmpty().mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap(),
        confirmFirst = prefs.getBoolean(K_CONFIRM_FIRST, false),
        confirmSince = if (prefs.contains(K_CONFIRM_SINCE)) java.time.LocalDate.ofEpochDay(prefs.getLong(K_CONFIRM_SINCE, 0)) else null,
        lookedAt = prefs.getStringSet(K_LOOKED, null).orEmpty().mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { p -> p[1].toLongOrNull()?.let { p[0] to java.time.LocalDate.ofEpochDay(it) } }
        }.toMap(),
        myNames = cleanNames(prefs.getString(K_MY_NAMES, null).orEmpty().split("\n")),
        abroadFee = if (prefs.contains(K_ABROAD_FEE)) prefs.getFloat(K_ABROAD_FEE, 0f).toDouble().coerceIn(0.0, com.bamnat.core.money.LedgerSmarts.MAX_ABROAD_FEE)
            else com.bamnat.core.money.LedgerSmarts.ABROAD_FEE,
        rates = prefs.getStringSet(K_RATES, null).orEmpty().mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { p -> p[1].toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { p[0] to it } }
        }.toMap(),
        askedCards = prefs.getStringSet(K_ASKED_CARDS, null).orEmpty().toSet(),
    )

    private companion object {
        const val K_BLOCKED = "blocked_apps"
        const val K_WORDS = "drop_words"
        const val K_MIN = "min_amount"
        const val K_SEEN = "seen_apps"
        const val K_NOT_REPEATING = "not_repeating"
        const val K_CALENDAR = "calendar"
        const val K_APP_CALENDARS = "app_calendars"
        const val K_CONFIRM_FIRST = "confirm_first"
        const val K_CONFIRM_SINCE = "confirm_since"
        const val K_LOOKED = "subs_looked"
        const val MAX_APPS = 30
        const val K_MY_NAMES = "my_names"
        const val K_ABROAD_FEE = "abroad_fee"
        const val K_RATES = "my_rates"
        const val K_ASKED_CARDS = "asked_cards"
        const val MAX_RATES = 20
        const val MAX_NAMES = 3
    }
}
