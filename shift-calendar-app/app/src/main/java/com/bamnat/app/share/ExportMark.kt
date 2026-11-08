package com.bamnat.app.share

import android.content.Context
import com.bamnat.app.appContainer

/**
 * 1.39 [SPREAD-05] (앱 안 퍼짐 설계, 사용자 2026-11-07): a picture that leaves the phone (근무표, 견적서, 거래내역서) carries a
 * small '밤낮' so whoever gets it can find the app. On by default, for free and 프로 alike; 설정 › 데이터 › '보내는 사진에
 * 밤낮 표시' and the 근무표 사진 sheet turn it off, and it stays off.
 */
object ExportMark {
    fun on(context: Context): Boolean = runCatching { context.appContainer.settingsRepository.current.exportMark }.getOrDefault(true)
}
