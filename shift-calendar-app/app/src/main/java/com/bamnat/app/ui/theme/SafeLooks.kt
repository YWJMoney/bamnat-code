package com.bamnat.app.ui.theme

import com.bamnat.app.data.AppSettings

/**
 * 1.36 [SEC-06] 보안 점검 M2: what 안전 모드 draws without. When the app stopped three times in a row before its month settled
 * ([com.bamnat.app.diag.StartGuard]), the cause may be a look the user put on: a theme from the 장터, a picture, a font, parts.
 * Before, 안전 모드 still drew them, so a look that stopped the app stopped it again. Now the screen is drawn with the app's own
 * looks until "평소대로"; nothing is changed in the settings, so the user's looks come back as they were.
 *
 * Each look a 1.36 branch adds (글꼴, 부품, 꾸밈 층, 탭 판) is put back to its default here too.
 */
fun AppSettings.safeLooks(): AppSettings = AppSettings().let { d ->
    copy(
        calTheme = d.calTheme, photoLook = d.photoLook, calView = d.calView, screenLooks = d.screenLooks,
        memoLook = d.memoLook, moneyLook = d.moneyLook, payLooks = d.payLooks,
    )
}
