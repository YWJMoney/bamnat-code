package com.bamnat.app.tier

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [MON-13] 1.36 (사용자 결정 2026-10-31: '해당 기능을 사용한 기간부터 일주일 제공'): a 프로 feature in [TRIAL] opens for
 * [DAYS] days from the first time it is used on this phone, then asks for 프로 as before. Kept on the phone (a reinstall starts
 * again, as an ad day would); nothing goes to the server.
 */
class FeatureTrials(context: Context?) {
    private val prefs = context?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val started = MutableStateFlow(
        prefs?.all.orEmpty().mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap())

    /** Feature name to the millis its week began. */
    val all: StateFlow<Map<String, Long>> = started.asStateFlow()

    /** Starts [feature]'s week at [at] unless it began before; true when it starts now. */
    fun start(feature: Feature, at: Long): Boolean {
        if (feature !in TRIAL || started.value.containsKey(feature.name)) return false
        started.value = started.value + (feature.name to at)
        prefs?.edit()?.putLong(feature.name, at)?.apply()
        return true
    }

    /** For testing on a debug build: every trial back to never used. */
    fun clear() {
        started.value = emptyMap()
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        const val FILE = "pro_trials"
        const val DAYS = 7
        const val MS = DAYS * 86_400_000L
        /** 명세서 '금액 비교하기' and '세후 예상'. */
        val TRIAL = setOf(Feature.PAYSLIP_MATCH, Feature.TAX_FORECAST)
    }
}
