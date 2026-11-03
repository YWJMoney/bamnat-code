package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.theme.AppFonts
import com.bamnat.app.ui.theme.UserFonts

/**
 * [MAT-06] 1.36 PC 확인 7차 (사용성 제안 "토스트 대신 달력 알림 한 줄, 되돌리기와 함께"): shared fonts a worn theme was waiting for that
 * came down by themselves while the app opened. Kept until the user closes the line or takes them back; this run of the app only.
 */
object FontArrivals {
    val fonts = mutableStateListOf<Pair<String, String>>()

    fun add(got: List<UserFonts.Mine>) {
        got.forEach { m -> if (fonts.none { it.first == m.id }) fonts += m.id to m.name }
    }
}

/** 기다리던 글꼴이 들어왔어요: ‘Gaegu’ · 되돌리기 (비슷한 글꼴로 돌아가요) · ✕. */
@Composable
internal fun FontArrivedLine(modifier: Modifier = Modifier) {
    val got = FontArrivals.fonts.toList()
    if (got.isEmpty()) return
    val ctx = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("기다리던 글꼴이 들어왔어요: ‘${got.joinToString("’, ‘") { it.second }}’", style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Back to the stand-in: the font leaves this phone (the theme draws its 비슷한 글꼴 again) and is not asked for again.
            TextButton(onClick = {
                got.forEach { (id, _) -> UserFonts.remove(ctx, id); AppFonts.loads.remove(id) }
                FontArrivals.fonts.clear()
            }) { Text("되돌리기") }
            IconButton(onClick = { FontArrivals.fonts.clear() }) { Icon(Icons.Filled.Close, "글꼴 알림 닫기", Modifier.size(18.dp)) }
        }
    }
}
