package com.bamnat.app.money

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bamnat.app.NoticeLinks

/**
 * 1.37 [LEDH-14] '돈 적기' in the phone's quick settings: one tap opens 가계부 with the add sheet, from any screen.
 * The user adds it by editing the quick settings panel; nothing is added for them.
 */
class MoneyAddTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "돈 적기"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "밤낮 가계부"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = NoticeLinks.intent(this, NoticeLinks.Target.MONEY_ADD)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3701, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
