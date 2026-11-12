package com.bamnat.app.ui.village

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyText
import com.bamnat.app.ui.company.SoftCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * [TOWN-03] 1.37 (후보 2, PC 확인: 팔로잉 줄에 올린 분이 '이름 없는 분'으로만 보여 누군지 몰라요): the one 마을 이름 ([com.bamnat.app.company.CompanyApi.nick],
 * shown on 광장 posts and on the maker's 프로필) is asked where it first shows — before the first 장터 upload and the first post in
 * a kind of work's 광장 — instead of only in 프로필 고치기. Asked once: '이대로 둘게요' is kept on this phone.
 */
object VillageName {
    /**
     * 1.38 PC 9차 LOW (새로 깐 폰은 예전 마을 이름을 되찾을 길이 없어요): a name is this phone's, so on a new phone the old one reads as taken;
     * the words say how to free it, everywhere a name can be refused.
     */
    const val TAKEN = "다른 분이 쓰는 이름이에요. 예전 폰에서 쓰던 이름이면 그 폰의 마을 › 프로필 고치기에서 이름을 지운 뒤 여기서 다시 넣어 주세요. 예전 폰이 없으면 이름 끝에 숫자나 글자를 더해 주세요"

    private const val PREFS = "village_ui"
    private const val K_SKIP = "name_skip"

    /** null: not asked yet this run; "": no name; else the name. */
    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name

    fun skipped(c: AppContainer): Boolean = runCatching {
        c.appContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(K_SKIP, false)
    }.getOrDefault(false)

    fun skip(c: AppContainer) {
        runCatching { c.appContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(K_SKIP, true).apply() }
        _name.value = _name.value ?: ""
    }

    /** Whether this phone has no 마을 이름; null when the server could not say (then nothing is asked). */
    suspend fun missing(c: AppContainer): Boolean? {
        _name.value?.let { return it.isEmpty() }
        val n = runCatching { c.companyApi.activity(c.companyStore.device).nick }.getOrElse { return null }
        _name.value = n.orEmpty()
        return n.isNullOrBlank()
    }

    /** Sets the name; the problem to show back, or null when it is set. */
    suspend fun set(c: AppContainer, nick: String): String? {
        val t = nick.trim()
        if (t.length !in 2..12) return "이름은 2~12자로 정해 주세요"
        CompanyText.personal(t)?.let { return "이름에 연락처·개인 정보는 넣지 않아요" }
        return try {
            _name.value = c.companyApi.nick(c.companyStore.device, t) ?: t
            null
        } catch (e: CompanyException) {
            when (e.code) {
                "taken" -> VillageName.TAKEN
                "nick" -> "쓸 수 없는 이름이에요. 관리자·개발자·운영자 같은 말은 빼 주세요"
                "offline", "network" -> "인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 정해 주세요"
                else -> "지금은 이름을 정하지 못했어요. 마을 › 내 프로필 › 프로필 고치기에서도 정할 수 있어요"
            }
        }
    }
}

/**
 * [TOWN-03] A small card: "다른 분께 ‘[unnamed]’로 보여요" with a name box, 정하기 and 이대로 둘게요. Shows nothing while the server
 * is asked, when a name is set, when it was put off on this phone, or when the server cannot be reached.
 */
@Composable
fun VillageNameCard(c: AppContainer, unnamed: String, modifier: Modifier = Modifier) {
    if (!c.companyApi.enabled) return
    val known by VillageName.name.collectAsStateWithLifecycle()
    var missing by remember { mutableStateOf<Boolean?>(null) }
    var skipped by remember { mutableStateOf(VillageName.skipped(c)) }
    androidx.compose.runtime.LaunchedEffect(Unit) { missing = VillageName.missing(c) }
    var done by remember { mutableStateOf<String?>(null) }
    if (skipped) return
    done?.let { n ->
        Text("이제 ‘$n’${com.bamnat.app.ui.Fmt.ro(n)} 보여요. 바꾸려면 마을 › 내 프로필 › 프로필 고치기", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary, modifier = modifier)
        return
    }
    if (missing != true || !known.isNullOrEmpty()) return
    var nick by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SoftCard(modifier.fillMaxWidth(), highlighted = true) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("마을 이름을 먼저 정할까요?", style = MaterialTheme.typography.titleSmall)
            Text("지금은 다른 분께 ‘$unnamed’${com.bamnat.app.ui.Fmt.ro(unnamed)} 보여요. 이름이 있으면 팔로우하거나 답할 때 누군지 알아봐요. 광장과 장터에 같은 이름이 쓰여요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(nick, { nick = it.replace("\n", "").take(12); problem = null }, singleLine = true,
                label = { Text("마을 이름 (2~12자)") }, isError = problem != null,
                supportingText = problem?.let { p -> { Text(p) } }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { VillageName.skip(c); skipped = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이대로 둘게요") }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                com.bamnat.app.ui.components.FilledTonalButton(enabled = !busy && nick.trim().length >= 2, onClick = {
                    busy = true
                    scope.launch {
                        val p = VillageName.set(c, nick)
                        busy = false
                        if (p == null) done = nick.trim() else problem = p
                    }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) "정하는 중" else "이 이름으로") }
            }
        }
    }
}
