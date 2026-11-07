package com.bamnat.app.memo

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 1.39 [TASK-33] '할 일 적기' in the phone's quick settings, beside '돈 적기' ([com.bamnat.app.money.MoneyAddTile]): one tap opens the
 * 한 줄 window ([QuickTaskActivity]) over any screen. The user adds it by editing the quick settings panel; nothing is added for them.
 */
class TaskAddTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "할 일 적기"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "밤낮 할 일"
            updateTile()
        }
    }

    override fun onClick() {
        val intent = QuickTaskActivity.intent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3901, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
