package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.AppliedSetup
import com.bamnat.core.plaza.ProposalDraft
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.core.plaza.SetupEdit

/*
 * [J133-85] 1.33 광장 급여 써 보기 (사용자 2026-10-29 15:48 "급여체계는 첫 제공자가 나올 때까지 존재하지 않고 첫 제공자의 입력이 어렵다"):
 * the user took a company's shifts from the 광장, that version has no pay way, and the user has just set their pay by hand. That is
 * the moment to be the first: one card on the pay screen, and one sheet with the company's version and the user's pay way already
 * in it (no amounts), so 올리기 is a single press. It goes as a proposal on that version, where coworkers say 맞아요.
 */

/**
 * [J133-94] 1.33 빌드 7 확인 (첫 설정의 '내 달력에만 쓸게요' 뒤로 첫 급여 방식 카드가 2주 동안 안 떠요): being a version's first pay way
 * is not the share ask a no quiets ([com.bamnat.app.company.ShareAskGate], J133-17). It is offered once for each version, with its
 * own 다음에, and that 다음에 still quiets the other asks.
 */
internal object FirstPayAsked {
    private const val FILE = "first_pay_asked"

    private fun key(a: AppliedSetup) = "${a.companyId}/${a.setup}"
    private fun prefs(c: android.content.Context) = c.applicationContext.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)

    fun was(c: android.content.Context, a: AppliedSetup): Boolean = prefs(c).getBoolean(key(a), false)
    fun mark(c: android.content.Context, a: AppliedSetup) = prefs(c).edit().putBoolean(key(a), true).apply()
}

/** The version the calendar uses, from the 광장, that has no pay way at all (not one hidden from this phone). */
data class FirstPay(val applied: AppliedSetup, val text: String)

/**
 * [J133-85] [applied]'s version when it has no pay way for anyone; null when it has one, when this phone may not see it, or offline.
 * The page kept on the phone first, read once otherwise.
 */
internal suspend fun firstPayOf(c: AppContainer, applied: AppliedSetup): FirstPay? {
    val kept = c.companySearch.page(applied.companyId)?.setups?.firstOrNull { it.id == applied.setup }
    val setup = kept ?: runCatching { c.companyApi.company(applied.companyId).also(c.companySearch::keepPage) }.getOrNull()
        ?.setups?.firstOrNull { it.id == applied.setup } ?: return null
    if (setup.hasPay) return null
    return FirstPay(applied, setup.text)
}

/** [J133-85] The card on the pay screen; [onClosed] when it is sent or put off. */
@Composable
fun FirstPayCard(first: FirstPay, calendar: String, modifier: Modifier = Modifier, onClosed: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    SoftCard(highlighted = true, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("‘${first.applied.company}’ 첫 급여 방식이 되어 주세요", style = MaterialTheme.typography.titleMedium)
            Text("광장의 이 회사 근무표에는 아직 급여 계산 방식이 없어요. 방금 맞춘 내 방식을 올리면 같은 회사 분들이 월급을 바로 계산해요.",
                style = MaterialTheme.typography.bodyMedium)
            Text("금액은 빠지고 가산율·수당 이름 같은 방식만 올라가요. 올리기 전에 한 번 보여 드려요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open = true }, modifier = Modifier.weight(1f)) { Text("보고 올리기") }
                TextButton(onClick = {
                    FirstPayAsked.mark(context, first.applied)
                    com.bamnat.app.company.ShareAskGate.declined(context)
                    onClosed()
                }) { Text("다음에") }
            }
        }
    }
    if (open) FirstPaySheet(first, calendar, onDismiss = { open = false }, onSent = { open = false; FirstPayAsked.mark(context, first.applied); onClosed() })
}

/** [J133-85] 다른 점 말하기 opened on the version with the user's pay way in it and the reason written: one press sends it. */
@Composable
private fun FirstPaySheet(first: FirstPay, calendar: String, onDismiss: () -> Unit, onSent: () -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val holder = remember(calendar) { TalkHolder(c, scope, calendar) }
    var draft by remember { mutableStateOf<ProposalDraft?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(first.text) {
        val mine = runCatching { c.companyOf(calendar).share.exportForServer().toString() }.getOrNull()?.let(SetupEdit::of)
        val base = SetupEdit.of(first.text) ?: return@LaunchedEffect onDismiss()
        val text = (mine?.takeIf { it.hasPay }?.let(base::withPayOf) ?: base.withLawPay()).text
        draft = ProposalDraft(first.text, text, SetupCompare.proposalLines(SetupCompare.rows(first.text, text)), "우리 회사 급여 계산 방식이에요 (처음 올려요)")
    }
    val d = draft ?: return
    val title = "${first.applied.company} · ${first.applied.dept.ifEmpty { "회사 전체" }}"
    DiffEditorSheet(
        title = title, base = first.text, mine = null, busy = busy, error = error, draft = d,
        onSend = { lines, setup, why ->
            busy = true
            holder.show(first.applied.companyId, first.applied.setup, title)
            holder.send(proposalBody(SetupCompare.rows(first.text, setup), why), kind = "proposal", change = lines, setupText = setup) { e ->
                busy = false
                holder.close()
                if (e == null) {
                    c.telemetry.event("plaza_diff", "first_pay")
                    onSent()
                } else error = e
            }
        },
        onDismiss = onDismiss,
    )
}
