package com.bamnat.app.ui.company

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.work.WorkStyle

/**
 * 2.0.0 [RVN-21] (갈래 I가 찾음, 총괄 11-13: 광장 알림 창이 '설정 › 알림 › 밤낮 소식'이라 했는데 그 쪽은 '알림과 잠'이에요) The way into
 * settings said on a plaza or company screen, read from 기능 찾기's own list ([FeatureRegistry.whereFor]) so it names the page as it
 * stands for [style] ('급여와 연차' is an 알바's '시급과 주휴수당'), and a renamed page renames the line too.
 */
internal fun settingsWay(feature: String, style: WorkStyle?): String =
    FeatureRegistry.get(feature)?.let { FeatureRegistry.whereFor(it, style) } ?: "설정"

/** 2.0.0 [RVN-21] 회사 찾기 in settings: the 같은 회사 사람들과 row on the pay page (no 기능 찾기 entry of its own). */
internal fun companyFindWay(style: WorkStyle?): String =
    "설정 › ${FeatureRegistry.settingsPage("급여와 연차", style)} › 회사 찾기"
