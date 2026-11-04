package com.bamnat.app.ui.village

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.VillageReplies
import com.bamnat.app.ui.company.SoftCard

/**
 * [TOWN-02] 1.37: at the top of 마을 › 광장, "💬 내 글에 새 답 N개" for answers in any plaza I wrote in, except the room on
 * screen ([except], whose own card says it and opens its talk). Read again each time 광장 comes back into view; nothing at all
 * on a phone that never agreed to the plaza's rules (it cannot have written) or with no company server.
 */
@Composable
fun NewAnswersLine(c: AppContainer, except: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (!c.companyApi.enabled || c.settingsRepository.current.plazaRulesAgreed <= 0) return
    var fresh by remember { mutableIntStateOf(0) }
    var looks by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { looks++ }
    androidx.compose.runtime.LaunchedEffect(looks, except) {
        val posts = runCatching { c.companyApi.myPosts(c.companyStore.device) }.getOrNull() ?: return@LaunchedEffect
        val rooms = posts.associate { it.id to it.room }
        fresh = VillageReplies.fresh(c.appContext, posts).filterKeys { rooms[it] != except }.values.sum()
    }
    if (fresh <= 0) return
    SoftCard(modifier.fillMaxWidth(), highlighted = true) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "내 글 보기", onClick = onOpen).heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("💬 내 글에 새 답 ${fresh}개", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("보기") }
        }
    }
}
